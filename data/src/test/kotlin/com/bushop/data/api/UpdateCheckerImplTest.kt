package com.bushop.data.api

import android.content.Context
import com.bushop.domain.model.NetworkResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateCheckerImplTest {
    private lateinit var context: Context
    private lateinit var client: OkHttpClient
    private lateinit var checker: UpdateCheckerImpl

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        client = mockk()
        checker = UpdateCheckerImpl(context, "1.0.0", client = client)
    }

    @Test
    fun `checkForUpdate returns Success with UpdateInfo when newer release with APK exists`() = runTest {
        // GitHub API returns a release with a newer version and APK asset
        val json =
            """
                {
                  "tag_name": "v1.1.0",
                  "body": "Bug fixes and improvements",
                  "assets": [
                    {
                      "name": "bus-hop-v1.1.0.apk",
                      "browser_download_url": "https://github.com/icemedica341/Bus-Hop/releases/download/v1.1.0/bus-hop-v1.1.0.apk"
                    }
                  ]
                }
            """.trimIndent()

        val body = mockk<ResponseBody>(relaxed = true)
        every { body.string() } returns json

        val call = mockk<Call>(relaxed = true)
        every { call.execute() } returns buildResponse(body)
        every { client.newCall(any()) } returns call

        val result = checker.checkForUpdate()

        assertTrue("Expected Success, got $result", result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertTrue(info.hasUpdate)
        assertTrue(info.latestVersion == "1.1.0")
        assertTrue(info.downloadUrl.contains("bus-hop-v1.1.0.apk"))
    }

    @Test
    fun `checkForUpdate returns Error when no APK asset in release`() = runTest {
        val json =
            """
                {
                  "tag_name": "v1.1.0",
                  "body": "No APK in this release",
                  "assets": []
                }
            """.trimIndent()

        val body = mockk<ResponseBody>(relaxed = true)
        every { body.string() } returns json

        val call = mockk<Call>(relaxed = true)
        every { call.execute() } returns buildResponse(body)
        every { client.newCall(any()) } returns call

        val result = checker.checkForUpdate()

        assertTrue("Expected Error, got $result", result is NetworkResult.Error)
        val msg = (result as NetworkResult.Error).message
        assertTrue(msg.contains("No update available"))
    }

    @Test
    fun `checkForUpdate returns Error when current version is already latest`() = runTest {
        val json =
            """
                {
                  "tag_name": "v1.0.0",
                  "body": "Same version",
                  "assets": [
                    {
                      "name": "bus-hop-v1.0.0.apk",
                      "browser_download_url": "https://github.com/icemedica341/Bus-Hop/releases/download/v1.0.0/bus-hop-v1.0.0.apk"
                    }
                  ]
                }
            """.trimIndent()

        val body = mockk<ResponseBody>(relaxed = true)
        every { body.string() } returns json

        val call = mockk<Call>(relaxed = true)
        every { call.execute() } returns buildResponse(body)
        every { client.newCall(any()) } returns call

        val result = checker.checkForUpdate()

        assertTrue("Expected Error, got $result", result is NetworkResult.Error)
        val msg = (result as NetworkResult.Error).message
        assertTrue(msg.contains("No update available"))
    }

    @Test
    fun `checkForUpdate returns Error when response is empty`() = runTest {
        val body = mockk<ResponseBody>(relaxed = true)
        every { body.string() } returns ""

        val call = mockk<Call>(relaxed = true)
        every { call.execute() } returns buildResponse(body)
        every { client.newCall(any()) } returns call

        val result = checker.checkForUpdate()

        assertTrue("Expected Error, got $result", result is NetworkResult.Error)
    }

    @Test
    fun `checkForUpdate returns Error on network exception`() = runTest {
        val call = mockk<Call>(relaxed = true)
        every { call.execute() } throws java.io.IOException("Network error")
        every { client.newCall(any()) } returns call

        val result = checker.checkForUpdate()

        assertTrue("Expected Error, got $result", result is NetworkResult.Error)
        val msg = (result as NetworkResult.Error).message
        assertTrue(msg.contains("Network error"))
    }

    @Test
    fun `downloadAndUpdateInstall returns Error when no prior checkForUpdate`() = runTest {
        val result = checker.downloadAndUpdateInstall()

        assertTrue("Expected Error, got $result", result is NetworkResult.Error)
        val msg = (result as NetworkResult.Error).message
        assertTrue(msg.contains("No update info"))
    }

    private fun buildResponse(body: ResponseBody): Response = Response
        .Builder()
        .request(Request.Builder().url("https://api.github.com/repos/icemedica341/Bus-Hop/releases/latest").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body)
        .build()
}
