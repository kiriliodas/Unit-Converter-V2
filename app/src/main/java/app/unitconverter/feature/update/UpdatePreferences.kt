package app.unitconverter.feature.update

import app.unitconverter.feature.convert.StringStorage

/** Update-only persisted state. It is intentionally separate from library/app preferences. */
data class UpdatePersistedState(
    val lastSuccessfulCheckEpochMillis: Long = 0L,
    val dismissedTag: String = "",
    val lastKnownTag: String = "",
    val downloadedUpdate: DownloadedUpdate? = null
)

interface UpdateStateStore {
    fun load(): UpdatePersistedState
    fun save(state: UpdatePersistedState)
}

class SerializedUpdateStateStore(
    private val storage: StringStorage,
    private val key: String = STORAGE_KEY
) : UpdateStateStore {

    override fun load(): UpdatePersistedState = runCatching {
        UpdateStateSerializer.decode(storage.read(key))
    }.getOrDefault(UpdatePersistedState())

    override fun save(state: UpdatePersistedState) {
        storage.write(key, UpdateStateSerializer.encode(state))
    }

    companion object {
        const val STORAGE_KEY: String = "update_state_v1"
    }
}

/**
 * Tiny tab-separated format with its own header/key:
 *
 * `UC_UPDATE_STATE_V1` in SharedPreferences key `update_state_v1`.
 *
 * Unknown/malformed optional records are ignored. This never reads or writes
 * `UC_LIBRARY_V1` or `UC_APP_PREFERENCES_V1`.
 */
object UpdateStateSerializer {
    private const val HEADER = "UC_UPDATE_STATE_V1"
    private const val LAST_CHECK = "LAST_SUCCESSFUL_CHECK"
    private const val DISMISSED_TAG = "DISMISSED_TAG"
    private const val LAST_KNOWN_TAG = "LAST_KNOWN_TAG"
    private const val DOWNLOADED = "DOWNLOADED"
    private const val SEPARATOR = "\t"

    fun encode(state: UpdatePersistedState): String = buildString {
        appendLine(HEADER)
        appendRecord(LAST_CHECK, state.lastSuccessfulCheckEpochMillis.toString())
        if (state.dismissedTag.isNotBlank()) appendRecord(DISMISSED_TAG, sanitize(state.dismissedTag))
        if (state.lastKnownTag.isNotBlank()) appendRecord(LAST_KNOWN_TAG, sanitize(state.lastKnownTag))
        state.downloadedUpdate?.let { downloaded ->
            append(DOWNLOADED)
            append(SEPARATOR); append(sanitize(downloaded.tagName))
            append(SEPARATOR); append(sanitize(downloaded.fileName))
            append(SEPARATOR); append(downloaded.sizeBytes)
            append(SEPARATOR); append(sanitize(downloaded.sha256.lowercase()))
            append(SEPARATOR); append(if (downloaded.checksumVerified) "1" else "0")
            appendLine()
        }
    }

    fun decode(serialized: String?): UpdatePersistedState {
        if (serialized.isNullOrBlank()) return UpdatePersistedState()
        var state = UpdatePersistedState()
        serialized.lineSequence()
            .filter { it.isNotBlank() && it != HEADER }
            .forEach { line ->
                val parts = line.split(SEPARATOR)
                when (parts.firstOrNull()) {
                    LAST_CHECK -> if (parts.size == 2) {
                        state = state.copy(
                            lastSuccessfulCheckEpochMillis = parts[1].toLongOrNull()?.coerceAtLeast(0L)
                                ?: state.lastSuccessfulCheckEpochMillis
                        )
                    }

                    DISMISSED_TAG -> if (parts.size == 2) {
                        state = state.copy(dismissedTag = parts[1].take(MAX_FIELD_CHARS))
                    }

                    LAST_KNOWN_TAG -> if (parts.size == 2) {
                        state = state.copy(lastKnownTag = parts[1].take(MAX_FIELD_CHARS))
                    }

                    DOWNLOADED -> if (parts.size == 6) {
                        val size = parts[3].toLongOrNull()
                        val sha = parts[4].lowercase()
                        val fileName = parts[2]
                        if (size != null && size > 0L && Sha256.isValid(sha) && isSafeFileName(fileName)) {
                            state = state.copy(
                                downloadedUpdate = DownloadedUpdate(
                                    tagName = parts[1].take(MAX_FIELD_CHARS),
                                    fileName = fileName,
                                    sizeBytes = size,
                                    sha256 = sha,
                                    checksumVerified = parts[5] == "1"
                                )
                            )
                        }
                    }
                }
            }
        return state
    }

    private fun StringBuilder.appendRecord(name: String, value: String) {
        append(name)
        append(SEPARATOR)
        appendLine(value)
    }

    private fun sanitize(value: String): String = value
        .replace('\t', '_')
        .replace('\n', '_')
        .replace('\r', '_')
        .take(MAX_FIELD_CHARS)

    private fun isSafeFileName(name: String): Boolean =
        name.isNotBlank() && name.length <= MAX_FIELD_CHARS &&
            !name.contains('/') && !name.contains('\\') && name != "." && name != ".." &&
            name.endsWith(".apk", ignoreCase = true)

    private const val MAX_FIELD_CHARS: Int = 256
}
