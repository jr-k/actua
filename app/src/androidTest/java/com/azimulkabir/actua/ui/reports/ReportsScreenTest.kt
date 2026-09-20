package com.azimulkabir.actua.ui.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azimulkabir.actua.model.ReportDashboardPage
import com.azimulkabir.actua.model.ReportSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home Slice 5 (#346) follow-up: regression coverage for two state-restoration bugs a review
 * caught in the initial favorite-report-shortcut change (PR #403) — stale routing requests
 * replaying on a later plain Reports visit, and the dashboard-picker reset wiping a
 * restored/requested selection while the report snapshot is still loading.
 */
@RunWith(AndroidJUnit4::class)
class ReportsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val netWorth = ReportDashboardPage("net-worth", "Net Worth", emptyList())
    private val spending = ReportDashboardPage("spending", "Spending", emptyList())

    @Test fun initialPageRequestSelectsTheRequestedDashboard() {
        val snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(netWorth, spending))

        compose.setContent {
            MaterialTheme {
                ReportsScreen(
                    snapshot = snapshot,
                    hideDecimalPlaces = false,
                    initialPageId = "spending",
                    initialPageRequest = 1,
                )
            }
        }

        compose.onNodeWithText("Spending").assertExists()
    }

    @Test fun selectedDashboardSurvivesRecreationWhileTheSnapshotIsStillLoading() {
        var snapshot by mutableStateOf(ReportSnapshot(emptyList(), emptyList(), 0, listOf(netWorth, spending)))
        val restorationTester = StateRestorationTester(compose)

        restorationTester.setContent {
            MaterialTheme {
                ReportsScreen(snapshot = snapshot, hideDecimalPlaces = false)
            }
        }

        compose.onNodeWithContentDescription("Switch dashboard").performClick()
        compose.onNodeWithText("Spending").performClick()
        compose.onNodeWithText("Spending").assertExists()

        // Real launch order: the UI (and its rememberSaveable state) restores before the async
        // report fetch resolves, so the snapshot AppNavigation hands ReportsScreen is briefly
        // empty again right after recreation.
        snapshot = ReportSnapshot(emptyList(), emptyList(), 0)
        restorationTester.emulateSavedInstanceStateRestore()

        snapshot = ReportSnapshot(emptyList(), emptyList(), 0, listOf(netWorth, spending))
        compose.waitForIdle()

        compose.onNodeWithText("Spending").assertExists()
    }
}
