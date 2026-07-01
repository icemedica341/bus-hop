package com.bushop.data.api

/**
 * ┌─ UpdateCheckerImpl ──────────────────────────────┐
 * │  data/ layer · GitHub release checker            │
 * │                                                   │
 * │  checkForUpdate() ─→ GitHub Releases API call     │
 * │  Compare latest version with current versionName │
 * │  downloadUpdate() ─→ APK download via OkHttp     │
 * │  Implements domain UpdateChecker interface       │
 * └───────────────────────────────────────────────────┘
 */

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.bushop.domain.api.UpdateChecker
import com.bushop.domain.model.NetworkResult
import com.bushop.domain.model.UpdateInfo
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** Checks GitHub releases and downloads APK updates. */
class UpdateCheckerImpl(
    private val context: Context,
    private val currentVersion: String,
    private val githubRepo: String = "icemedica341/Bus-Hop",
    internal val client: okhttp3.OkHttpClient = ApiClient.okHttpClient,
) : UpdateChecker {
    private val gson = GsonProvider.gson
    private val apiUrl = "https://api.github.com/repos/$githubRepo/releases/latest"

    private var latestUpdateInfo: UpdateInfo? = null

    /** Fetch latest release info from GitHub. */
    override suspend fun checkForUpdate(): NetworkResult<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request =
                Request
                    .Builder()
                    .url(apiUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext NetworkResult.Error("Empty response")
            val release = gson.fromJson(body, GitHubRelease::class.java)
            val tag = release.tagName.removePrefix("v")
            val apkAsset = release.assets?.find { it.name.endsWith(".apk") }
            if (apkAsset == null || !isNewerVersion(tag, currentVersion)) {
                return@withContext NetworkResult.Error("No update available")
            }
            val info =
                UpdateInfo(
                    latestVersion = tag,
                    downloadUrl = apkAsset.browserDownloadUrl,
                    releaseNotes = release.body?.take(500) ?: "",
                    hasUpdate = true,
                )
            latestUpdateInfo = info
            NetworkResult.Success(info)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Check update failed", e)
        }
    }

    /** Download APK to cache and launch the install intent via FileProvider. */
    override suspend fun downloadAndUpdateInstall(): NetworkResult<Unit> {
        val info = latestUpdateInfo ?: return NetworkResult.Error("No update info. Call checkForUpdate() first.")
        return withContext(Dispatchers.IO) {
            try {
                // Validate the download URL is from GitHub to prevent MITM redirect attacks
                val uri = java.net.URI(info.downloadUrl)
                val host = uri.host ?: return@withContext NetworkResult.Error("Invalid download URL: no host")
                if (uri.scheme != "https" ||
                    !(host.endsWith("github.com") || host.endsWith("github-releases.githubusercontent.com"))
                ) {
                    return@withContext NetworkResult.Error("Invalid download URL: $host")
                }

                val updatesDir = File(context.cacheDir, "updates").also { it.mkdirs() }
                val targetFile = File(updatesDir, "bus-hop-update.apk")

                val downloadRequest = Request.Builder().url(info.downloadUrl).build()
                val response = client.newCall(downloadRequest).execute()
                val body = response.body ?: return@withContext NetworkResult.Error("Empty response body")
                FileOutputStream(targetFile).use { output -> body.byteStream().use { it.copyTo(output) } }

                val apkUri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        targetFile,
                    )
                // Check install permission on Android 8+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    return@withContext NetworkResult.Error("Install from unknown sources not enabled. Enable in Settings.")
                }

                val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                context.startActivity(intent)
                targetFile.delete()
                NetworkResult.Success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Download/install failed", e)
            }
        }
    }

    private fun isNewerVersion(
        tag: String,
        current: String,
    ): Boolean {
        // Strip pre-release suffix (e.g. "1.0.3-rc1" → "1.0.3")
        val normalize = { v: String -> v.split("-").first() }
        val tParts = normalize(tag).split(".").mapNotNull { it.toIntOrNull() }
        val cParts = normalize(current).split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(tParts.size, cParts.size)) {
            val t = tParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (t != c) return t > c
        }
        return false
    }

    internal data class GitHubRelease(
        @SerializedName("tag_name") val tagName: String,
        @SerializedName("body") val body: String?,
        @SerializedName("assets") val assets: List<GitHubAsset>?,
    )

    internal data class GitHubAsset(
        @SerializedName("name") val name: String,
        @SerializedName("browser_download_url") val browserDownloadUrl: String,
    )
}
