package com.azimulkabir.actua.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.azimulkabir.actua"
private const val TIMEOUT_MS = 5_000L

/**
 * Drives the app's main journeys (launch, tab switches, list scrolling) so
 * `./gradlew :baselineprofile:pixel8Api34BenchmarkAndroidTest` (or the module's
 * default connected-device task) can record a Human Readable Profile. The
 * result is copied into app/src/release/generated/baselineProfiles/baseline-prof.txt
 * and consumed by profileinstaller/R8 to AOT-compile these paths on install,
 * instead of relying on the JIT to warm them up during real use.
 *
 * This module only *generates* the profile; regenerate it after any change
 * to the screens exercised below (or new hot paths, e.g. a new heavy list).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        startActivityAndWait()

        // Budget tab is the default landing screen; give it a moment to settle
        // before switching tabs, mirroring a real cold start.
        device.waitForIdle()

        navigateToTab("Accounts")
        navigateToTab("Transactions")
        scrollMainList()

        navigateToTab("Home")
        scrollMainList()

        navigateToTab("Manage")
        navigateToTab("Budget")
        scrollMainList()
    }

    private fun MacrobenchmarkScope.navigateToTab(label: String) {
        // The bottom nav item's Icon carries `contentDescription = item.label`, but when labels
        // are visible (the default; see AppNavigation.kt's `showBottomNavigationLabels`)
        // Compose's semantics merging drops that description in favor of the sibling Text, so
        // the merged node ends up with an empty content-desc and the label only reachable as
        // text. Try text first since that's the common case, and fall back to desc for
        // icon-only mode.
        val tab = device.wait(Until.findObject(By.text(label)), TIMEOUT_MS)
            ?: checkNotNull(device.wait(Until.findObject(By.desc(label)), TIMEOUT_MS)) {
                "Bottom nav tab \"$label\" was not found; the generated profile would silently " +
                    "skip this journey"
            }
        tab.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollMainList() {
        val scrollable = checkNotNull(device.wait(Until.findObject(By.scrollable(true)), TIMEOUT_MS)) {
            "No scrollable container was found; the generated profile would silently skip " +
                "this journey"
        }
        repeat(3) {
            scrollable.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(3) {
            scrollable.fling(Direction.UP)
            device.waitForIdle()
        }
    }
}
