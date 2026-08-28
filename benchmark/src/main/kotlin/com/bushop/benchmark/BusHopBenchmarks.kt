package com.bushop.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BusHopBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    companion object {
        private const val PACKAGE_NAME = "com.bushop"
    }

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
            measureBlock = {
                startActivityAndWait(
                    android.content.Intent().apply {
                        setPackage(PACKAGE_NAME)
                        setAction(android.content.Intent.ACTION_MAIN)
                        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    },
                )
                device.wait(Until.hasObject(By.res("$PACKAGE_NAME:id/mainContent")), 5_000)
            },
        )
    }

    @Test
    fun scrollBusStopList() {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = {
                pressHome()
                startActivityAndWait(
                    android.content.Intent().apply {
                        setPackage(PACKAGE_NAME)
                        setAction(android.content.Intent.ACTION_MAIN)
                        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    },
                )
                device.waitForIdle()
            },
            measureBlock = {
                val listView = device.findObject(By.res(PACKAGE_NAME, "bus_stop_list"))
                if (listView.exists()) {
                    listView.setGestureMargin(device.displayWidth / 4)
                    repeat(2) { listView.flingForward(); device.waitForIdle() }
                }
            },
        )
    }
}
