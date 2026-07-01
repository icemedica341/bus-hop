package com.bushop.data.local

/**
 * ┌─ BusStopUpdater ──────────────────────────────────┐
 * │  data/ layer · Remote bus stop data updater       │
 * │                                                     │
 * │  Reads current version from bundled assets         │
 * │  Checks a remote version URL for a newer version   │
 * │  Downloads updated bus_stops.json to internal      │
 * │  storage when a newer version is found             │
 * │  Graceful fallback on network failures             │
 * └─────────────────────────────────────────────────────┘
 */

import android.content.Context
import android.util.Log
import com.bushop.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Checks for and downloads updated bus stop data from a remote source.
 *
 * Uses the app's bundled assets as the version baseline and downloads
 * newer data to internal storage so [BusStopIndex] can pick it up
 * without requiring an app store release.
 */
class BusStopUpdater(
    private val context: Context,
    private val versionUrl: String = DEFAULT_VERSION_URL,
    private val dataUrl: String = DEFAULT_DATA_URL,
) {
    private val client = ApiClient.okHttpClient
    private val tag = "BusStopUpdater"

    /**
     * Check for a newer version of bus_stops.json and download if available.
     *
     * @return `true` if a new version was successfully downloaded, `false` otherwise
     *         (no update needed, or network/parse failure — logged but not thrown).
     */
    suspend fun checkForUpdate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val localVersion = readLocalVersion()
            val remoteVersion = fetchRemoteVersion()

            if (remoteVersion > localVersion) {
                downloadFile(dataUrl, File(context.filesDir, FILE_NAME))
                File(context.filesDir, VERSION_FILE_NAME).writeText(remoteVersion.toString())
                Log.i(tag, "Updated bus_stops from v$localVersion to v$remoteVersion")
                return@withContext true
            }

            Log.i(tag, "No update needed: local v$localVersion, remote v$remoteVersion")
            return@withContext false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "Failed to check for update", e)
            return@withContext false
        }
    }

    /** Read the current version from the bundled assets. Returns 0 if unavailable. */
    private fun readLocalVersion(): Int = try {
        context.assets
            .open(VERSION_FILE_NAME)
            .bufferedReader()
            .use { it.readText().trim().toInt() }
    } catch (e: Exception) {
        Log.w(tag, "Could not read local version, assuming 0", e)
        0
    }

    /** Fetch the remote version string and parse it as an integer. Returns 0 on failure. */
    private fun fetchRemoteVersion(): Int {
        val request = Request.Builder().url(versionUrl).get().build()
        val response = client.newCall(request).execute()
        val body = response.body.string()
        return body.trim().toIntOrNull() ?: 0
    }

    /** Download a file from [url] and save it to [target]. */
    private fun downloadFile(url: String, target: File) {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body
        target.outputStream().use { output ->
            body.byteStream().use { input ->
                input.copyTo(output)
            }
        }
    }

    companion object {
        private const val FILE_NAME = "bus_stops.json"
        private const val VERSION_FILE_NAME = "bus_stops_version.txt"

        private const val DEFAULT_VERSION_URL =
            "https://raw.githubusercontent.com/icemedica341/Bus-Hop/main/app/src/main/assets/bus_stops_version.txt"
        private const val DEFAULT_DATA_URL =
            "https://raw.githubusercontent.com/icemedica341/Bus-Hop/main/app/src/main/assets/bus_stops.json"
    }
}
