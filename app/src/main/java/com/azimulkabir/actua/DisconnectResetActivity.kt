package com.azimulkabir.actua

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.DemoBudgetManager
import com.azimulkabir.actua.data.preferences.DisplayPreferences
import com.azimulkabir.actua.data.security.DisconnectResetManager
import com.azimulkabir.actua.data.sync.ActualSyncRunner
import com.azimulkabir.actua.data.sync.SyncRunResult
import com.azimulkabir.actua.ui.theme.ActuaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class DisconnectUserException(val userMessage: String) : Exception()

/**
 * Confirmation surface for a deliberate server disconnect.
 *
 * It lives outside ConnectionScreen so the existing repository can be torn down by
 * recreating the application task after the reset. A final sync protects normal
 * Actual data plus Actua-specific CRDT preferences such as credit-card metadata.
 */
class DisconnectResetActivity : AppCompatActivity() {
    private enum class Stage { Confirm, Syncing, SyncFailed }

    private var stage by mutableStateOf(Stage.Confirm)
    private var syncFailure by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ActuaTheme(
                appearance = DisplayPreferences(this).appearance,
                dynamicColor = DisplayPreferences(this).useDynamicColor,
            ) {
                when (stage) {
                    Stage.Confirm -> AlertDialog(
                        onDismissRequest = ::cancelAndRestore,
                        title = { Text(stringResource(R.string.disconnect_reset_title)) },
                        text = {
                            Text(stringResource(R.string.disconnect_reset_message))
                        },
                        confirmButton = {
                            TextButton(onClick = ::syncThenReset) {
                                Text(stringResource(R.string.disconnect_reset_action))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = ::cancelAndRestore) {
                                Text(stringResource(R.string.widget_action_cancel))
                            }
                        },
                    )

                    Stage.Syncing -> AlertDialog(
                        onDismissRequest = {},
                        title = { Text(stringResource(R.string.disconnect_syncing_title)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.disconnect_syncing_message))
                            }
                        },
                        confirmButton = {},
                    )

                    Stage.SyncFailed -> AlertDialog(
                        onDismissRequest = ::cancelAndRestore,
                        title = { Text(stringResource(R.string.disconnect_sync_failed_title)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.disconnect_sync_failed_message))
                                syncFailure?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = ::forceReset) {
                                Text(
                                    stringResource(R.string.disconnect_anyway),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = ::syncThenReset) {
                                Text(stringResource(R.string.disconnect_try_again))
                            }
                        },
                    )
                }
            }
        }
    }

    private fun syncThenReset() {
        if (stage == Stage.Syncing) return
        stage = Stage.Syncing
        syncFailure = null
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { syncAllDownloadedBudgets() }
            }
            result.onSuccess { resetAndRestart() }
                .onFailure { error ->
                    syncFailure = (error as? DisconnectUserException)?.userMessage
                        ?: getString(R.string.disconnect_error_sync_failed)
                    stage = Stage.SyncFailed
                }
        }
    }

    private fun syncAllDownloadedBudgets() {
        val files = BudgetFileManager(this)
        val activeStore = ActiveBudgetStore(this)
        val originalActive = activeStore.budgetId
        val budgets = files.listLocalBudgets().filter { it.id != DemoBudgetManager.BUDGET_ID }

        try {
            budgets.forEach { budget ->
                if (budget.cloudFileId.isNullOrBlank()) {
                    throw DisconnectUserException(getString(
                        R.string.disconnect_error_missing_server_identity,
                        budget.budgetName ?: budget.id,
                    ))
                }
                activeStore.budgetId = budget.id
                when (ActualSyncRunner.run(this)) {
                    is SyncRunResult.Success -> Unit
                    SyncRunResult.NotConfigured -> throw DisconnectUserException(getString(
                        R.string.disconnect_error_not_configured,
                        budget.budgetName ?: budget.id,
                    ))
                    SyncRunResult.EncryptionKeyUnavailable -> throw DisconnectUserException(getString(
                        R.string.disconnect_error_encryption_locked,
                        budget.budgetName ?: budget.id,
                    ))
                }
            }
        } finally {
            activeStore.budgetId = originalActive
        }
    }

    private fun forceReset() {
        resetAndRestart()
    }

    private fun resetAndRestart() {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    DisconnectResetManager(this@DisconnectResetActivity).resetLocalServerData()
                }
            }.onSuccess {
                DisconnectResetManager(this@DisconnectResetActivity).restartIntoFreshConnectionState()
                finish()
            }.onFailure {
                syncFailure = getString(R.string.disconnect_error_reset_failed)
                stage = Stage.SyncFailed
            }
        }
    }

    private fun cancelAndRestore() {
        DisconnectResetManager(this).restartIntoFreshConnectionState()
        finish()
    }
}
