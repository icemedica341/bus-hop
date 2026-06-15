package com.bushop.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bushop.domain.api.UpdateChecker
import com.bushop.domain.model.NetworkResult
import com.bushop.domain.model.UpdateInfo
import com.bushop.domain.repository.BusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Handles update checking, APK download, and install.
 *
 * Separated from MainViewModel for SRP. Instantiated by MainViewModel which
 * provides a [onSnackbar] callback for user-facing messages.
 */
class UpdateManager(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val updateChecker: UpdateChecker,
    private val repository: BusRepository,
    private val onSnackbar: (String) -> Unit = {},
) {
    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        internal set
    var isCheckingUpdate by mutableStateOf(false)
        internal set
    var isDownloadingUpdate by mutableStateOf(false)
    var hasSeenDragHint by mutableStateOf(false)
        internal set

    fun dismissHint() {
        hasSeenDragHint = true
        scope.launch { repository.saveHintSeen(true) }
    }

    // updateChecker is injected via constructor — enables DI and test mocking

    fun checkForUpdate() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        scope.launch {
            try {
                when (val result = updateChecker.checkForUpdate()) {
                    is NetworkResult.Success -> {
                        updateInfo = result.data
                        if (result.data.hasUpdate) {
                            onSnackbar("Update v${result.data.latestVersion} available")
                        }
                    }

                    is NetworkResult.Error -> {
                        onSnackbar("Update check failed: ${result.message}")
                    }
                }
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    /** Download the latest APK and launch the install intent via FileProvider. */
    fun downloadAndInstallUpdate() {
        if (isDownloadingUpdate) return
        isDownloadingUpdate = true
        scope.launch {
            try {
                when (val result = updateChecker.downloadAndUpdateInstall()) {
                    is NetworkResult.Success -> {
                        onSnackbar("Installation launched\u2026")
                    }

                    is NetworkResult.Error -> {
                        onSnackbar("Download failed: ${result.message}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onSnackbar("Install failed. Check storage space and try again.")
            } finally {
                isDownloadingUpdate = false
            }
        }
    }

    /** Collect persisted hint-seen state. */
    fun observePersisted() {
        scope.launch {
            try {
                repository.hasSeenHintFlow.collect { seen -> hasSeenDragHint = seen }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // flow ended
            }
        }
    }
}
