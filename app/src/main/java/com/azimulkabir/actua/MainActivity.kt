package com.azimulkabir.actua

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.azimulkabir.actua.ui.navigation.AppNavigation
import com.azimulkabir.actua.ui.theme.ActuaTheme
import com.azimulkabir.actua.data.sync.ActualSyncScheduler
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.notifications.CreditCardDueNotificationScheduler
import com.azimulkabir.actua.widget.WidgetActions
import com.azimulkabir.actua.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class AppLaunchRequest(val action: String, val target: String?, val nonce: Long = System.nanoTime())
const val SHARED_IMPORT_ACTION = "com.azimulkabir.actua.IMPORT_SHARED_TEXT"

class MainActivity : AppCompatActivity() {
    private var foregroundGeneration by mutableIntStateOf(0)
    private var launchRequest by mutableStateOf<AppLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchRequest = intent.toLaunchRequest()
        enableEdgeToEdge()
        setContent {
            var appearance by remember { mutableStateOf(DisplayPreferences(this).appearance) }
            var useDynamicColor by remember { mutableStateOf(DisplayPreferences(this).useDynamicColor) }
            ActuaTheme(appearance = appearance, dynamicColor = useDynamicColor) {
                AppNavigation(
                    modifier = Modifier.fillMaxSize(),
                    foregroundGeneration = foregroundGeneration,
                    launchRequest = launchRequest,
                    onLaunchRequestConsumed = { launchRequest = null },
                    onAppearanceChange = { appearance = it },
                    onUseDynamicColorChange = { useDynamicColor = it },
                )
            }
        }
        // Both operations can initialise WorkManager, and refreshing card reminders opens the
        // selected SQLite budget. They maintain background work only, so never make the first
        // Compose frame wait for them. The app-open sync in AppNavigation refreshes reminders
        // again after it has incorporated server changes.
        lifecycleScope.launch(Dispatchers.IO) {
            ActualSyncScheduler.schedulePeriodic(applicationContext)
            CreditCardDueNotificationScheduler.refresh(applicationContext)
        }
    }

    override fun onStart() {
        super.onStart()
        foregroundGeneration += 1
        // Looking up widget instances crosses Binder. Keep it off the input/render thread just
        // like the per-widget database reads triggered by the resulting broadcast.
        lifecycleScope.launch(Dispatchers.IO) {
            WidgetUpdater.requestAll(applicationContext)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = intent.toLaunchRequest()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) ActualSyncScheduler.scheduleLocalBackup(this)
    }

    private fun Intent.toLaunchRequest(): AppLaunchRequest? = action?.takeIf {
        it.startsWith("com.azimulkabir.actua.widget.")
    }?.let { AppLaunchRequest(it, getStringExtra(WidgetActions.EXTRA_TARGET)) }
        ?: takeIf { action == Intent.ACTION_SEND && type?.startsWith("text/") == true }
            ?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf(String::isNotBlank)
            ?.let { AppLaunchRequest(SHARED_IMPORT_ACTION, it) }
}
