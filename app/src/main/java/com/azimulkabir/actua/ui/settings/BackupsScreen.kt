package com.azimulkabir.actua.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.BackupDestinationError
import com.azimulkabir.actua.data.budget.BackupDestinationManager
import com.azimulkabir.actua.data.budget.BackupItem
import com.azimulkabir.actua.data.budget.BackupService
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private class SelectedBackupUnreadableException : Exception()

@Composable
fun BackupsScreen(
    budgetId: String,
    onBack: () -> Unit,
    onBeforeRestore: () -> Unit,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val service = remember { BackupService(context) }
    val destinations = remember { BackupDestinationManager(context) }
    val scope = rememberCoroutineScope()
    var backups by remember { mutableStateOf<List<BackupItem>>(emptyList()) }
    var destination by remember { mutableStateOf(destinations.read()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupItem?>(null) }
    var pendingExport by remember { mutableStateOf<BackupItem.Archive?>(null) }
    var confirmBackup by remember { mutableStateOf(false) }

    fun refresh() { backups = runCatching { service.availableBackups(budgetId) }.getOrDefault(emptyList()) }
    fun makeBackup() {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { service.makeBackup(budgetId) } }
                .onSuccess { message = resources.getString(R.string.backup_created) }
                .onFailure { message = resources.getString(R.string.could_not_create_backup) }
            destination = destinations.read(); busy = false; refresh()
        }
    }

    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                destinations.select(uri, null)
                service.mirrorExisting(budgetId)
            } }.onSuccess { message = resources.getString(R.string.existing_backups_mirrored) }
                .onFailure { message = resources.getString(R.string.could_not_use_folder) }
            destination = destinations.read(); busy = false
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        val backup = pendingExport
        pendingExport = null
        if (uri != null && backup != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                    service.archiveFile(budgetId, backup.id).inputStream().use { it.copyTo(output) }
                }
            } }.onSuccess { message = resources.getString(R.string.backup_exported) }
                .onFailure { message = resources.getString(R.string.could_not_export_backup) }
            busy = false
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { service.importArchive(budgetId, it) }
                    ?: throw SelectedBackupUnreadableException()
            } }.onSuccess { message = resources.getString(R.string.backup_imported) }
                .onFailure {
                    message = resources.getString(
                        if (it is SelectedBackupUnreadableException) {
                            R.string.could_not_read_selected_file
                        } else {
                            R.string.could_not_import_backup
                        },
                    )
                }
            busy = false; refresh()
        }
    }

    LaunchedEffect(budgetId) { refresh() }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestore = null },
            title = { Text(stringResource(if (backup is BackupItem.Latest) R.string.revert_budget_title else R.string.restore_backup_title)) },
            text = { Text(if (backup is BackupItem.Latest) {
                stringResource(R.string.revert_budget_description)
            } else {
                stringResource(R.string.restore_backup_description)
            }) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; onBeforeRestore()
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) {
                        service.restore(budgetId, when (backup) {
                            BackupItem.Latest -> BackupService.LATEST_ID
                            is BackupItem.Archive -> backup.id
                        })
                    } }.onSuccess { message = resources.getString(R.string.backup_restored) }
                        .onFailure { message = resources.getString(R.string.could_not_restore_backup) }
                    pendingRestore = null; busy = false; refresh(); onRestored()
                }
            }) { Text(stringResource(if (backup is BackupItem.Latest) R.string.revert else R.string.restore)) } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmBackup) AlertDialog(
        onDismissRequest = { confirmBackup = false },
        title = { Text(stringResource(R.string.create_new_backup_title)) },
        text = { Text(stringResource(R.string.create_new_backup_description)) },
        confirmButton = { TextButton(onClick = { confirmBackup = false; makeBackup() }) { Text(stringResource(R.string.back_up)) } },
        dismissButton = { TextButton(onClick = { confirmBackup = false }) { Text(stringResource(R.string.cancel)) } },
    )

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.backups_title), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.destination), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.backup_location), fontWeight = FontWeight.Medium)
                            Text(
                                destination.name ?: stringResource(
                                    if (destination.uri == null) R.string.private_app_storage else R.string.selected_folder
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (destination.lastMirroredMillis > 0) Text(
                                stringResource(R.string.mirrored_time, relativeTime(destination.lastMirroredMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(enabled = !busy, onClick = { chooseFolder.launch(null) }) { Text(stringResource(R.string.change)) }
                    }
                    if (destination.uri != null) {
                        HorizontalDivider()
                        TextButton(enabled = !busy, onClick = {
                            destinations.reset(); destination = destinations.read(); message = resources.getString(R.string.using_private_app_storage)
                        }) { Text(stringResource(R.string.reset_to_default), color = MaterialTheme.colorScheme.error) }
                    }
                    destination.error?.let {
                        Text(backupDestinationErrorMessage(it), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(
                stringResource(R.string.backups_storage_description, budgetId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                if (backups.any { it is BackupItem.Latest }) confirmBackup = true else makeBackup()
            }) {
                if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.back_up_now))
            }
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                importBackup.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }) { Text(stringResource(R.string.import_backup)) }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text(stringResource(R.string.available_backups), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            backups.forEachIndexed { index, backup ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) { pendingRestore = backup }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(when (backup) {
                            BackupItem.Latest -> stringResource(R.string.pre_restore_version)
                            is BackupItem.Archive -> DateFormat.getDateTimeInstance().format(Date.from(backup.modifiedAt))
                        })
                        Text(stringResource(if (backup is BackupItem.Latest) R.string.tap_to_revert else R.string.tap_to_restore), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (backup is BackupItem.Archive) OutlinedButton(enabled = !busy, onClick = {
                        pendingExport = backup; export.launch(backup.id)
                    }) { Text(stringResource(R.string.export)) }
                }
                if (index != backups.lastIndex) HorizontalDivider()
            }
            if (backups.isEmpty()) Text(stringResource(R.string.no_backups_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun backupDestinationErrorMessage(error: String): String = stringResource(when (error) {
    BackupDestinationError.CANNOT_CREATE_DIRECTORY.persistedValue,
    "The selected folder does not allow creating directories" ->
        R.string.backup_folder_cannot_create_directory
    BackupDestinationError.CANNOT_CREATE_BACKUP_FILE.persistedValue,
    "The selected folder did not create the backup file" ->
        R.string.backup_folder_cannot_create_file
    else -> R.string.backup_folder_cannot_write
})

@Composable
internal fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - timestamp).coerceAtLeast(0) / 1000)
    return when {
        seconds < 10 -> stringResource(R.string.just_now)
        seconds < 60 -> pluralStringResource(R.plurals.seconds_ago, seconds.toInt(), seconds)
        seconds < 3600 -> pluralStringResource(R.plurals.minutes_ago, (seconds / 60).toInt(), seconds / 60)
        seconds < 86400 -> pluralStringResource(R.plurals.hours_ago, (seconds / 3600).toInt(), seconds / 3600)
        else -> pluralStringResource(R.plurals.days_ago, (seconds / 86400).toInt(), seconds / 86400)
    }
}
