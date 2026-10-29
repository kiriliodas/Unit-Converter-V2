package app.unitconverter.feature.update

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException

/** Minimal blocking HTTP abstraction so GitHub response handling is JVM-testable. */
fun interface UpdateHttpTransport {
    fun get(url: String, accept: String, maxBytes: Int): HttpTextResponse
}

data class HttpTextResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap()
)

class UrlConnectionTransport(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000
) : UpdateHttpTransport {

    override fun get(url: String, accept: String, maxBytes: Int): HttpTextResponse {
        val parsed = try {
            URL(url)
        } catch (error: Exception) {
            throw UpdateException(UpdateErrorKind.MALFORMED_METADATA, cause = error)
        }
        requireHttps(parsed)

        var connection: HttpURLConnection? = null
        return try {
            connection = (parsed.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readLimited(it, maxBytes) } ?: ""
            val headers = buildMap<String, String> {
                for ((key, values) in connection.headerFields) {
                    if (key != null) {
                        put(key.lowercase(), values.joinToString(separator = ","))
                    }
                }
            }
            HttpTextResponse(status, body, headers)
        } catch (error: UpdateException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw UpdateException(UpdateErrorKind.NETWORK, cause = error)
        } catch (error: UnknownHostException) {
            throw UpdateException(UpdateErrorKind.NETWORK, cause = error)
        } catch (error: SSLException) {
            throw UpdateException(UpdateErrorKind.NETWORK, cause = error)
        } catch (error: Exception) {
            throw UpdateException(UpdateErrorKind.NETWORK, cause = error)
        } finally {
            connection?.disconnect()
        }
    }

    private fun requireHttps(url: URL) {
        if (!url.protocol.equals("https", ignoreCase = true)) {
            throw UpdateException(UpdateErrorKind.MALFORMED_METADATA)
        }
    }

    private fun readLimited(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw UpdateException(UpdateErrorKind.MALFORMED_METADATA)
            }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    companion object {
        const val USER_AGENT: String = "UnitConverterV2-Android"
        const val GITHUB_API_VERSION: String = "2022-11-28"
    }
}

class GitHubUpdateClient(
    private val transport: UpdateHttpTransport,
    private val latestReleaseUrl: String = UpdatePolicy.LATEST_RELEASE_API_URL
) {
    fun fetchLatestRelease(): GitHubRelease {
        val response = transport.get(
            url = latestReleaseUrl,
            accept = "application/vnd.github+json",
            maxBytes = MAX_RELEASE_JSON_BYTES
        )
        throwForStatus(response)
        return GitHubReleaseParser.parse(response.body)
    }

    fun fetchText(url: String, maxBytes: Int = UpdatePolicy.MAX_CHECKSUM_BYTES.toInt()): String {
        val response = transport.get(url, accept = "text/plain, application/octet-stream", maxBytes = maxBytes)
        throwForStatus(response)
        return response.body
    }

    private fun throwForStatus(response: HttpTextResponse) {
        if (response.statusCode in 200..299) return
        val remaining = response.headers["x-ratelimit-remaining"]?.trim()
        val kind = when {
            response.statusCode == 404 -> UpdateErrorKind.NO_RELEASE
            response.statusCode == 429 || (response.statusCode == 403 && remaining == "0") ->
                UpdateErrorKind.RATE_LIMIT
            else -> UpdateErrorKind.HTTP
        }
        throw UpdateException(kind, httpStatus = response.statusCode)
    }

    companion object {
        const val MAX_RELEASE_JSON_BYTES: Int = 2_097_152
    }
}

object GitHubReleaseParser {
    fun parse(json: String): GitHubRelease {
        val root = try {
            MiniJson.parse(json) as? JsonValue.Obj
        } catch (error: Exception) {
            throw UpdateException(UpdateErrorKind.MALFORMED_METADATA, cause = error)
        } ?: throw UpdateException(UpdateErrorKind.MALFORMED_METADATA)

        val tag = root.string("tag_name")?.takeIf { it.isNotBlank() }
            ?: throw UpdateException(UpdateErrorKind.MALFORMED_METADATA)
        val htmlUrl = root.string("html_url")?.takeIf {
            it.startsWith(UpdatePolicy.RELEASE_PAGE_PREFIX, ignoreCase = true)
        }
            ?: throw UpdateException(UpdateErrorKind.MALFORMED_METADATA)

        val assets = (root["assets"] as? JsonValue.Arr)?.items.orEmpty().mapNotNull { item ->
            val obj = item as? JsonValue.Obj ?: return@mapNotNull null
            val name = obj.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = obj.string("browser_download_url")?.takeIf { it.startsWith("https://") }
                ?: return@mapNotNull null
            ReleaseAsset(
                id = obj.long("id") ?: 0L,
                name = name,
                downloadUrl = url,
                sizeBytes = obj.long("size") ?: -1L,
                contentType = obj.string("content_type").orEmpty()
            )
        }

        return GitHubRelease(
            tagName = tag,
            name = root.string("name").orEmpty(),
            body = root.string("body").orEmpty(),
            htmlUrl = htmlUrl,
            publishedAt = root.string("published_at").orEmpty(),
            draft = root.boolean("draft") ?: false,
            prerelease = root.boolean("prerelease") ?: false,
            assets = assets
        )
    }
}

private sealed interface JsonValue {
    data class Obj(val values: Map<String, JsonValue>) : JsonValue {
        operator fun get(key: String): JsonValue? = values[key]
        fun string(key: String): String? = (values[key] as? Str)?.value
        fun boolean(key: String): Boolean? = (values[key] as? Bool)?.value
        fun long(key: String): Long? = (values[key] as? Num)?.raw?.let { raw ->
            raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
        }
    }

    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val raw: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

/**
 * Small strict JSON reader for the GitHub Release object. It avoids adding a
 * serialization dependency while keeping malformed metadata fail-safe.
 */
private object MiniJson {
    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.parseValue(depth = 0)
        parser.skipWhitespace()
        if (!parser.isAtEnd()) throw IllegalArgumentException("Trailing JSON content")
        return value
    }

    private class Parser(private val text: String) {
        private var index: Int = 0

        fun isAtEnd(): Boolean = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun parseValue(depth: Int): JsonValue {
            if (depth > 64) throw IllegalArgumentException("JSON nesting too deep")
            skipWhitespace()
            if (isAtEnd()) throw IllegalArgumentException("Unexpected end of JSON")
            return when (text[index]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> JsonValue.Str(parseString())
                't' -> parseLiteral("true", JsonValue.Bool(true))
                'f' -> parseLiteral("false", JsonValue.Bool(false))
                'n' -> parseLiteral("null", JsonValue.Null)
                else -> parseNumber()
            }
        }

        private fun parseObject(depth: Int): JsonValue.Obj {
            expect('{')
            skipWhitespace()
            val values = linkedMapOf<String, JsonValue>()
            if (consume('}')) return JsonValue.Obj(values)
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                values[key] = parseValue(depth + 1)
                skipWhitespace()
                if (consume('}')) break
                expect(',')
            }
            return JsonValue.Obj(values)
        }

        private fun parseArray(depth: Int): JsonValue.Arr {
            expect('[')
            skipWhitespace()
            val items = mutableListOf<JsonValue>()
            if (consume(']')) return JsonValue.Arr(items)
            while (true) {
                items += parseValue(depth + 1)
                skipWhitespace()
                if (consume(']')) break
                expect(',')
            }
            return JsonValue.Arr(items)
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (index < text.length) {
                val ch = text[index++]
                when (ch) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (isAtEnd()) throw IllegalArgumentException("Unterminated escape")
                        when (val esc = text[index++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) throw IllegalArgumentException("Bad unicode escape")
                                val hex = text.substring(index, index + 4)
                                index += 4
                                out.append(hex.toIntOrNull(16)?.toChar() ?: throw IllegalArgumentException("Bad unicode escape"))
                            }
                            else -> throw IllegalArgumentException("Bad escape: $esc")
                        }
                    }
                    else -> {
                        if (ch.code < 0x20) throw IllegalArgumentException("Control character in string")
                        out.append(ch)
                    }
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseNumber(): JsonValue.Num {
            val start = index
            if (!isAtEnd() && text[index] == '-') index++
            if (isAtEnd() || !text[index].isDigit()) throw IllegalArgumentException("Bad number")
            if (text[index] == '0') {
                index++
            } else {
                while (!isAtEnd() && text[index].isDigit()) index++
            }
            if (!isAtEnd() && text[index] == '.') {
                index++
                if (isAtEnd() || !text[index].isDigit()) throw IllegalArgumentException("Bad fraction")
                while (!isAtEnd() && text[index].isDigit()) index++
            }
            if (!isAtEnd() && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (!isAtEnd() && (text[index] == '+' || text[index] == '-')) index++
                if (isAtEnd() || !text[index].isDigit()) throw IllegalArgumentException("Bad exponent")
                while (!isAtEnd() && text[index].isDigit()) index++
            }
            return JsonValue.Num(text.substring(start, index))
        }

        private fun parseLiteral(word: String, value: JsonValue): JsonValue {
            if (!text.regionMatches(index, word, 0, word.length)) {
                throw IllegalArgumentException("Bad literal")
            }
            index += word.length
            return value
        }

        private fun expect(ch: Char) {
            skipWhitespace()
            if (isAtEnd() || text[index] != ch) throw IllegalArgumentException("Expected $ch")
            index++
        }

        private fun consume(ch: Char): Boolean {
            skipWhitespace()
            if (isAtEnd() || text[index] != ch) return false
            index++
            return true
        }
    }
}
