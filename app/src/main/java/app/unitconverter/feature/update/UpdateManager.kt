package app.unitconverter.feature.update

import android.content.Context
import app.unitconverter.feature.convert.SharedPreferencesStringStorage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-shell update coordinator. It is deliberately not a ViewModel: this C8
 * app has a single Activity and no DI/navigation architecture. The manager
 * owns only update state and leaves all converter state untouched.
 */
class UpdateManager(
    context: Context,
    private val client: GitHubUpdateClient = GitHubUpdateClient(UrlConnectionTransport()),
    private val stateStore: UpdateStateStore = SerializedUpdateStateStore(
        SharedPreferencesStringStorage(context.applicationContext)
    ),
    private val downloadManager: UpdateDownloadManager = UpdateDownloadManager(context.applicationContext),
    private val apkValidator: ApkValidator = ApkValidator(context.applicationContext),
    private val installer: UpdateInstaller = UpdateInstaller(context.applicationContext),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var persisted: UpdatePersistedState = stateStore.load()
    private val installedVersionName: String = apkValidator.installedVersionName().orEmpty()
    private val installedVersionCode: Long = apkValidator.installedVersionCode()

    private val _state = MutableStateFlow(
        UpdateUiState(
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            phase = if (persisted.downloadedUpdate != null) {
                UpdatePhase.READY_TO_INSTALL
            } else {
                UpdatePhase.IDLE
            },
            downloadedUpdate = persisted.downloadedUpdate,
            lastSuccessfulCheckEpochMillis = persisted.lastSuccessfulCheckEpochMillis,
            lastKnownTag = persisted.lastKnownTag
        )
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val startupRequested = AtomicBoolean(false)
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var installJob: Job? = null

    init {
        scope.launch { cleanupPersistedDownload() }
    }

    fun checkOnStartup() {
        if (!startupRequested.compareAndSet(false, true)) return
        if (!UpdatePolicy.shouldCheck(clock(), persisted.lastSuccessfulCheckEpochMillis)) return
        checkInternal(manual = false)
    }

    fun checkManually() = checkInternal(manual = true)

    fun showPrompt() {
        val current = _state.value
        if (current.releaseInfo == null && current.downloadedUpdate == null) return
        _state.update { it.copy(promptVisible = true) }
    }

    fun dismissPrompt() {
        val current = _state.value
        current.releaseInfo?.let { info ->
            if (!persisted.dismissedTag.equals(info.tagName, ignoreCase = true)) {
                persisted = persisted.copy(dismissedTag = info.tagName)
                savePersisted()
            }
        }
        _state.update { state ->
            state.copy(
                promptVisible = false,
                phase = when {
                    state.phase == UpdatePhase.ERROR && state.releaseInfo != null -> {
                        if (state.downloadedUpdate != null) UpdatePhase.READY_TO_INSTALL else UpdatePhase.AVAILABLE
                    }
                    state.phase == UpdatePhase.ERROR -> UpdatePhase.IDLE
                    else -> state.phase
                }
            )
        }
    }

    fun downloadUpdate() {
        val info = _state.value.releaseInfo ?: return
        if (downloadJob?.isActive == true) return

        downloadJob = scope.launch {
            _state.update {
                it.copy(
                    phase = UpdatePhase.DOWNLOADING,
                    promptVisible = true,
                    progress = DownloadProgress(0L, info.apkAsset.sizeBytes.takeIf { size -> size > 0L }),
                    errorKind = null,
                    errorHttpStatus = null
                )
            }
            try {
                val file = downloadManager.download(info) { progress ->
                    _state.update { state ->
                        if (state.phase == UpdatePhase.DOWNLOADING) state.copy(progress = progress) else state
                    }
                }
                _state.update { it.copy(phase = UpdatePhase.VALIDATING) }
                val downloaded = validateDownloaded(info, file)
                persisted = persisted.copy(
                    downloadedUpdate = downloaded,
                    lastKnownTag = info.tagName
                )
                savePersisted()
                _state.update {
                    it.copy(
                        phase = UpdatePhase.READY_TO_INSTALL,
                        downloadedUpdate = downloaded,
                        progress = DownloadProgress(downloaded.sizeBytes, downloaded.sizeBytes),
                        errorKind = null,
                        errorHttpStatus = null
                    )
                }
            } catch (error: CancellationException) {
                _state.update {
                    it.copy(
                        phase = if (it.releaseInfo != null) UpdatePhase.AVAILABLE else UpdatePhase.IDLE,
                        progress = null,
                        errorKind = null,
                        errorHttpStatus = null
                    )
                }
            } catch (error: UpdateException) {
                clearPersistedDownloadIfTag(info.tagName)
                _state.update {
                    it.copy(
                        phase = UpdatePhase.ERROR,
                        promptVisible = true,
                        progress = null,
                        downloadedUpdate = null,
                        errorKind = error.kind,
                        errorHttpStatus = error.httpStatus
                    )
                }
            } catch (error: Exception) {
                clearPersistedDownloadIfTag(info.tagName)
                _state.update {
                    it.copy(
                        phase = UpdatePhase.ERROR,
                        promptVisible = true,
                        progress = null,
                        downloadedUpdate = null,
                        errorKind = UpdateErrorKind.UNKNOWN,
                        errorHttpStatus = null
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadManager.cancelActiveDownload()
        downloadJob?.cancel()
    }

    fun deleteDownloadedUpdate() {
        if (downloadJob?.isActive == true) return
        val downloaded = _state.value.downloadedUpdate ?: persisted.downloadedUpdate ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                downloadManager.fileForPersistedName(downloaded.fileName)?.let { file ->
                    downloadManager.deleteDownloaded(file)
                }
            }
            persisted = persisted.copy(downloadedUpdate = null)
            savePersisted()
            _state.update {
                it.copy(
                    phase = if (it.releaseInfo != null) UpdatePhase.AVAILABLE else UpdatePhase.IDLE,
                    downloadedUpdate = null,
                    progress = null,
                    errorKind = null,
                    errorHttpStatus = null
                )
            }
        }
    }

    fun installDownloadedUpdate() {
        val downloaded = _state.value.downloadedUpdate ?: return
        if (installJob?.isActive == true) return

        installJob = scope.launch {
            _state.update { it.copy(phase = UpdatePhase.VALIDATING, errorKind = null, errorHttpStatus = null) }
            try {
                val file = withContext(Dispatchers.IO) {
                    val candidate = downloadManager.fileForPersistedName(downloaded.fileName)
                        ?: throw UpdateException(UpdateErrorKind.INVALID_APK)
                    if (!candidate.isFile || candidate.length() != downloaded.sizeBytes) {
                        throw UpdateException(UpdateErrorKind.INVALID_APK)
                    }
                    val actualSha = downloadManager.sha256(candidate)
                    if (!Sha256.verify(downloaded.sha256, actualSha)) {
                        downloadManager.deleteDownloaded(candidate)
                        throw UpdateException(UpdateErrorKind.CHECKSUM_MISMATCH)
                    }
                    val expectedVersion = SemanticVersion.parseReleaseTag(downloaded.tagName)
                        ?: throw UpdateException(UpdateErrorKind.INVALID_VERSION)
                    apkValidator.validate(candidate, expectedVersion)
                    candidate
                }

                when (val launch = installer.createInstallLaunch(file)) {
                    is InstallLaunch.Ready -> {
                        withContext(Dispatchers.Main) {
                            appContext.startActivity(launch.intent)
                        }
                        _state.update {
                            it.copy(
                                phase = UpdatePhase.READY_TO_INSTALL,
                                errorKind = null,
                                errorHttpStatus = null
                            )
                        }
                    }

                    is InstallLaunch.NeedsInstallPermission -> {
                        withContext(Dispatchers.Main) {
                            appContext.startActivity(launch.intent)
                        }
                        _state.update {
                            it.copy(
                                phase = UpdatePhase.READY_TO_INSTALL,
                                promptVisible = true,
                                errorKind = UpdateErrorKind.INSTALL_PERMISSION,
                                errorHttpStatus = null
                            )
                        }
                    }

                    is InstallLaunch.Failed -> {
                        _state.update {
                            it.copy(
                                phase = UpdatePhase.READY_TO_INSTALL,
                                promptVisible = true,
                                errorKind = launch.kind,
                                errorHttpStatus = null
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(phase = UpdatePhase.READY_TO_INSTALL) }
            } catch (error: UpdateException) {
                if (error.kind in INVALID_DOWNLOAD_KINDS) {
                    clearPersistedDownloadIfTag(downloaded.tagName)
                }
                _state.update {
                    it.copy(
                        phase = if (error.kind in INVALID_DOWNLOAD_KINDS) UpdatePhase.ERROR else UpdatePhase.READY_TO_INSTALL,
                        promptVisible = true,
                        downloadedUpdate = if (error.kind in INVALID_DOWNLOAD_KINDS) null else it.downloadedUpdate,
                        errorKind = error.kind,
                        errorHttpStatus = error.httpStatus
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        phase = UpdatePhase.READY_TO_INSTALL,
                        promptVisible = true,
                        errorKind = UpdateErrorKind.INSTALLER_FAILED,
                        errorHttpStatus = null
                    )
                }
            }
        }
    }

    fun retryLastAction() {
        val current = _state.value
        if (current.releaseInfo != null && current.downloadedUpdate == null) {
            downloadUpdate()
        } else {
            checkManually()
        }
    }

    private fun checkInternal(manual: Boolean) {
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return

        checkJob = scope.launch {
            _state.update {
                it.copy(
                    phase = UpdatePhase.CHECKING,
                    promptVisible = false,
                    manualCheck = manual,
                    errorKind = null,
                    errorHttpStatus = null
                )
            }

            try {
                val release = withContext(Dispatchers.IO) { client.fetchLatestRelease() }
                val now = clock()
                persisted = persisted.copy(
                    lastSuccessfulCheckEpochMillis = now,
                    lastKnownTag = release.tagName
                )
                savePersisted()

                when (val decision = UpdatePolicy.decide(installedVersionName, release)) {
                    is UpdateCheckDecision.Available -> handleAvailable(decision.info, manual, now)
                    UpdateCheckDecision.UpToDate -> {
                        clearDownloadedIfNotNewerThanInstalled()
                        _state.update {
                            it.copy(
                                phase = if (manual) UpdatePhase.UP_TO_DATE else UpdatePhase.IDLE,
                                promptVisible = false,
                                manualCheck = manual,
                                lastSuccessfulCheckEpochMillis = now,
                                lastKnownTag = release.tagName,
                                errorKind = null,
                                errorHttpStatus = null
                            )
                        }
                    }

                    is UpdateCheckDecision.Rejected -> {
                        _state.update {
                            it.copy(
                                phase = if (manual) UpdatePhase.ERROR else UpdatePhase.IDLE,
                                promptVisible = false,
                                manualCheck = manual,
                                lastSuccessfulCheckEpochMillis = now,
                                lastKnownTag = release.tagName,
                                errorKind = decision.kind,
                                errorHttpStatus = null
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(phase = idlePhaseFor(it), promptVisible = false) }
            } catch (error: UpdateException) {
                _state.update {
                    it.copy(
                        phase = if (manual) UpdatePhase.ERROR else UpdatePhase.IDLE,
                        promptVisible = false,
                        manualCheck = manual,
                        errorKind = error.kind,
                        errorHttpStatus = error.httpStatus
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        phase = if (manual) UpdatePhase.ERROR else UpdatePhase.IDLE,
                        promptVisible = false,
                        manualCheck = manual,
                        errorKind = UpdateErrorKind.UNKNOWN,
                        errorHttpStatus = null
                    )
                }
            }
        }
    }

    private suspend fun handleAvailable(info: UpdateReleaseInfo, manual: Boolean, now: Long) {
        val currentDownloaded = _state.value.downloadedUpdate ?: persisted.downloadedUpdate
        val matchingDownloaded = currentDownloaded?.takeIf {
            it.tagName.equals(info.tagName, ignoreCase = true)
        }
        if (currentDownloaded != null && matchingDownloaded == null) {
            withContext(Dispatchers.IO) {
                downloadManager.fileForPersistedName(currentDownloaded.fileName)?.let { file ->
                    downloadManager.deleteDownloaded(file)
                }
            }
            persisted = persisted.copy(downloadedUpdate = null)
            savePersisted()
        }

        _state.update {
            it.copy(
                phase = if (matchingDownloaded != null) {
                    UpdatePhase.READY_TO_INSTALL
                } else {
                    UpdatePhase.AVAILABLE
                },
                releaseInfo = info,
                promptVisible = UpdatePolicy.shouldPrompt(info, persisted.dismissedTag, manual),
                downloadedUpdate = matchingDownloaded,
                manualCheck = manual,
                lastSuccessfulCheckEpochMillis = now,
                lastKnownTag = info.tagName,
                errorKind = null,
                errorHttpStatus = null
            )
        }
    }

    private suspend fun validateDownloaded(info: UpdateReleaseInfo, file: File): DownloadedUpdate {
        val sha256 = downloadManager.sha256(file)
        var checksumVerified = false

        val checksumAsset = info.checksumAsset
        if (checksumAsset != null) {
            val alreadyVerified = persisted.downloadedUpdate?.let { stored ->
                stored.tagName.equals(info.tagName, ignoreCase = true) &&
                    stored.fileName == file.name &&
                    stored.sizeBytes == file.length() &&
                    Sha256.verify(stored.sha256, sha256) &&
                    stored.checksumVerified
            } ?: false

            if (alreadyVerified) {
                checksumVerified = true
            } else {
                val checksumText = withContext(Dispatchers.IO) {
                    client.fetchText(checksumAsset.downloadUrl)
                }
                val expected = Sha256.parseExpected(checksumText, info.apkAsset.name)
                if (expected == null || !Sha256.verify(expected, sha256)) {
                    downloadManager.deleteDownloaded(file)
                    throw UpdateException(UpdateErrorKind.CHECKSUM_MISMATCH)
                }
                checksumVerified = true
            }
        }

        try {
            withContext(Dispatchers.IO) {
                apkValidator.validate(file, info.version)
            }
        } catch (error: UpdateException) {
            if (error.kind in INVALID_DOWNLOAD_KINDS) {
                downloadManager.deleteDownloaded(file)
            }
            throw error
        }

        return DownloadedUpdate(
            tagName = info.tagName,
            fileName = file.name,
            sizeBytes = file.length(),
            sha256 = sha256,
            checksumVerified = checksumVerified
        )
    }

    private suspend fun cleanupPersistedDownload() {
        val downloaded = persisted.downloadedUpdate ?: return
        val shouldClear = withContext(Dispatchers.IO) {
            val file = downloadManager.fileForPersistedName(downloaded.fileName)
            val parsed = SemanticVersion.parseReleaseTag(downloaded.tagName)
            val installed = SemanticVersion.parseInstalledVersionName(installedVersionName)
            val stale = file == null || !file.isFile || file.length() != downloaded.sizeBytes ||
                parsed == null || (installed != null && parsed <= installed)
            if (stale && file != null) downloadManager.deleteDownloaded(file)
            stale
        }
        if (shouldClear) {
            persisted = persisted.copy(downloadedUpdate = null)
            savePersisted()
            _state.update {
                it.copy(
                    phase = if (it.phase == UpdatePhase.READY_TO_INSTALL) UpdatePhase.IDLE else it.phase,
                    downloadedUpdate = null
                )
            }
        }
    }

    private suspend fun clearDownloadedIfNotNewerThanInstalled() {
        val downloaded = persisted.downloadedUpdate ?: return
        val parsed = SemanticVersion.parseReleaseTag(downloaded.tagName)
        val installed = SemanticVersion.parseInstalledVersionName(installedVersionName)
        if (parsed == null || (installed != null && parsed <= installed)) {
            withContext(Dispatchers.IO) {
                downloadManager.fileForPersistedName(downloaded.fileName)?.let { file ->
                    downloadManager.deleteDownloaded(file)
                }
            }
            persisted = persisted.copy(downloadedUpdate = null)
            savePersisted()
            _state.update { it.copy(downloadedUpdate = null) }
        }
    }

    private fun clearPersistedDownloadIfTag(tag: String) {
        if (persisted.downloadedUpdate?.tagName.equals(tag, ignoreCase = true)) {
            persisted = persisted.copy(downloadedUpdate = null)
            savePersisted()
        }
    }

    private fun idlePhaseFor(state: UpdateUiState): UpdatePhase = when {
        state.downloadedUpdate != null -> UpdatePhase.READY_TO_INSTALL
        state.releaseInfo != null -> UpdatePhase.AVAILABLE
        else -> UpdatePhase.IDLE
    }

    private fun savePersisted() {
        runCatching { stateStore.save(persisted) }
    }

    companion object {
        private val INVALID_DOWNLOAD_KINDS = setOf(
            UpdateErrorKind.CHECKSUM_MISMATCH,
            UpdateErrorKind.INVALID_APK,
            UpdateErrorKind.WRONG_PACKAGE,
            UpdateErrorKind.SIGNATURE_MISMATCH,
            UpdateErrorKind.NOT_NEWER,
            UpdateErrorKind.INVALID_VERSION
        )
    }
}
