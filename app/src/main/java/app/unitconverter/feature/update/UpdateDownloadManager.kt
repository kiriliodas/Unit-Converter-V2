package app.unitconverter.feature.update

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Downloads a release APK into `cacheDir/updates/` only. No external-storage
 * permission or `file://` URI is involved; [UpdateInstaller] later exposes the
 * completed file through FileProvider.
 */
class UpdateDownloadManager(context: Context) {

    private val appContext: Context = context.applicationContext

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    fun updatesDirectory(): File = File(appContext.cacheDir, UPDATES_DIRECTORY).apply {
        if (!exists()) mkdirs()
    }

    fun targetFile(info: UpdateReleaseInfo): File {
        val safeTag = info.tagName.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.')
            .take(48)
            .ifBlank { "release" }
        val assetPart = if (info.apkAsset.id > 0L) info.apkAsset.id.toString() else "asset"
        return File(updatesDirectory(), "unit-converter-$safeTag-$assetPart.apk")
    }

    fun fileForPersistedName(fileName: String): File? {
        if (fileName.contains('/') || fileName.contains('\\') || fileName == "." || fileName == "..") {
            return null
        }
        val directory = updatesDirectory()
        val candidate = File(directory, fileName)
        return candidate.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile }
    }

    suspend fun download(
        info: UpdateReleaseInfo,
        onProgress: (DownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val asset = info.apkAsset
        val target = targetFile(info)
        ensureInsideUpdatesDirectory(target)

        if (target.isFile && target.length() > 0L) {
            if (asset.sizeBytes <= 0L || target.length() == asset.sizeBytes) {
                onProgress(DownloadProgress(target.length(), asset.sizeBytes.takeIf { it > 0L } ?: target.length()))
                return@withContext target
            }
            target.delete()
        }

        checkStorage(asset.sizeBytes)
        val part = File(updatesDirectory(), target.name + PART_SUFFIX)
        if (part.exists()) part.delete()

        val url = try {
            URL(asset.downloadUrl)
        } catch (error: Exception) {
            throw UpdateException(UpdateErrorKind.MALFORMED_METADATA, cause = error)
        }
        if (!url.protocol.equals("https", ignoreCase = true)) {
            throw UpdateException(UpdateErrorKind.MALFORMED_METADATA)
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
                setRequestProperty("User-Agent", UrlConnectionTransport.USER_AGENT)
            }
            activeConnection = connection

            val status = connection.responseCode
            if (status !in 200..299) {
                val kind = if (status == 429) UpdateErrorKind.RATE_LIMIT else UpdateErrorKind.HTTP
                throw UpdateException(kind, httpStatus = status)
            }

            val headerTotal = connection.contentLengthLong.takeIf { it > 0L }
            val total = asset.sizeBytes.takeIf { it > 0L } ?: headerTotal
            var downloaded = 0L
            var lastReported = -1L
            onProgress(DownloadProgress(0L, total))

            connection.inputStream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (asset.sizeBytes > 0L && downloaded > asset.sizeBytes) {
                            throw UpdateException(UpdateErrorKind.DOWNLOAD_FAILED)
                        }
                        if (downloaded - lastReported >= PROGRESS_STEP_BYTES || downloaded == total) {
                            onProgress(DownloadProgress(downloaded, total))
                            lastReported = downloaded
                        }
                    }
                    output.flush()
                    runCatching { output.fd.sync() }
                }
            }

            currentCoroutineContext().ensureActive()
            if (downloaded <= 0L) throw UpdateException(UpdateErrorKind.DOWNLOAD_FAILED)
            if (headerTotal != null && downloaded != headerTotal) {
                throw UpdateException(UpdateErrorKind.DOWNLOAD_FAILED)
            }
            if (asset.sizeBytes > 0L && downloaded != asset.sizeBytes) {
                throw UpdateException(UpdateErrorKind.DOWNLOAD_FAILED)
            }

            if (!part.renameTo(target)) {
                if (target.isFile && target.length() == downloaded) {
                    part.delete()
                } else {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
            }
            onProgress(DownloadProgress(downloaded, total ?: downloaded))
            target
        } catch (error: CancellationException) {
            part.delete()
            throw error
        } catch (error: UpdateException) {
            part.delete()
            currentCoroutineContext().ensureActive()
            throw error
        } catch (error: SocketTimeoutException) {
            part.delete()
            currentCoroutineContext().ensureActive()
            throw UpdateException(UpdateErrorKind.NETWORK, cause = error)
        } catch (error: UnknownHostException) {
            part.delete()
            currentCoroutineContext().ensureActive()
            throw UpdateException(UpdateErrorKind.NETWORK, cause = error)
        } catch (error: SSLException) {
            part.delete()
            currentCoroutineContext().ensureActive()
            throw UpdateException(UpdateErrorKind.NETWORK, cause = error)
        } catch (error: Exception) {
            part.delete()
            currentCoroutineContext().ensureActive()
            val kind = if (isStorageError(error)) UpdateErrorKind.STORAGE else UpdateErrorKind.DOWNLOAD_FAILED
            throw UpdateException(kind, cause = error)
        } finally {
            if (activeConnection === connection) activeConnection = null
            connection?.disconnect()
        }
    }

    fun cancelActiveDownload() {
        activeConnection?.disconnect()
    }

    suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        FileInputStream(file).use { input -> Sha256.digest(input) }
    }

    fun deleteDownloaded(file: File?) {
        if (file == null) return
        runCatching {
            val directory = updatesDirectory().canonicalFile
            if (file.parentFile?.canonicalFile == directory && file.isFile) {
                file.delete()
            }
            val part = File(directory, file.name + PART_SUFFIX)
            if (part.isFile) part.delete()
        }
    }

    private fun checkStorage(expectedBytes: Long) {
        if (expectedBytes <= 0L) return
        val usable = updatesDirectory().usableSpace
        if (usable > 0L && usable < expectedBytes + STORAGE_MARGIN_BYTES) {
            throw UpdateException(UpdateErrorKind.STORAGE)
        }
    }

    private fun ensureInsideUpdatesDirectory(file: File) {
        val directory = updatesDirectory().canonicalFile
        if (file.parentFile?.canonicalFile != directory) {
            throw UpdateException(UpdateErrorKind.DOWNLOAD_FAILED)
        }
    }

    private fun isStorageError(error: Exception): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return "no space" in message || "not enough space" in message || "storage" in message
    }

    companion object {
        const val UPDATES_DIRECTORY: String = "updates"
        private const val PART_SUFFIX: String = ".part"
        private const val CONNECT_TIMEOUT_MILLIS: Int = 15_000
        private const val READ_TIMEOUT_MILLIS: Int = 30_000
        private const val BUFFER_SIZE: Int = 64 * 1024
        private const val PROGRESS_STEP_BYTES: Long = 128L * 1024L
        private const val STORAGE_MARGIN_BYTES: Long = 10L * 1024L * 1024L
    }
}
