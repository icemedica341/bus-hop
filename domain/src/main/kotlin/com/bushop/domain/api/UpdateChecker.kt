package com.bushop.domain.api

import com.bushop.domain.model.NetworkResult
import com.bushop.domain.model.UpdateInfo

/** Abstraction over the update checker. Enables DI and test mocking. */
interface UpdateChecker {
    /** Check GitHub for a newer release. Returns [UpdateInfo] if an update is available. */
    suspend fun checkForUpdate(): NetworkResult<UpdateInfo>

    /** Download the latest APK and launch the install intent. */
    suspend fun downloadAndUpdateInstall(): NetworkResult<Unit>
}
