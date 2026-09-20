package com.azimulkabir.actua.ui.settings

import android.net.Uri
import android.content.Intent
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.importing.CsvTransactionCandidateSource
import com.azimulkabir.actua.data.importing.ImportCandidate
import com.azimulkabir.actua.data.importing.ImportColumnMapping
import com.azimulkabir.actua.data.importing.ImportColumnRole
import com.azimulkabir.actua.data.importing.ImportDuplicateDetector
import com.azimulkabir.actua.data.importing.ImportHistoryEntry
import com.azimulkabir.actua.data.importing.ImportPreferences
import com.azimulkabir.actua.data.importing.ImportProblem
import com.azimulkabir.actua.data.importing.ImportProblemCode
import com.azimulkabir.actua.data.importing.ImportTable
import com.azimulkabir.actua.data.importing.ImportConfidence
import com.azimulkabir.actua.data.importing.FinancialMessageParser
import com.azimulkabir.actua.data.importing.NotificationImportPreferences
import com.azimulkabir.actua.data.importing.FinancialMessageProfile
import com.azimulkabir.actua.data.importing.StatementFormat
import com.azimulkabir.actua.data.importing.StatementDocumentReader
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate

private class SelectedStatementUnreadableException : Exception()
private class StatementTooLargeException : Exception()

private data class ReviewRow(
    val sourceRow: Int,
    val date: String,
    val payee: String,
    val amount: String,
    val notes: String,
    val reference: String?,
    val selected: Boolean,
    val confidence: ImportConfidence = ImportConfidence.HIGH,
    val sourceLabel: String,
)

@Composable
fun ImportTransactionsScreen(
    accounts: List<Account>,
    duplicateKeys: (String) -> Set<String>,
    onImport: (String, List<ImportCandidate>) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialSharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<ReviewRow>() }
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    var problems by remember { mutableStateOf<List<ImportProblem>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var accountMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val importPreferences = remember { ImportPreferences(context) }
    var table by remember { mutableStateOf<ImportTable?>(null) }
    var mapping by remember { mutableStateOf<ImportColumnMapping?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var sourceFormat by remember { mutableStateOf(StatementFormat.CSV) }
    var profileName by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(importPreferences.history()) }
    var mappingMenuIndex by remember { mutableStateOf<Int?>(null) }
    var datePatternMenu by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    val notificationPreferences = remember { NotificationImportPreferences(context) }
    var captureEnabled by remember { mutableStateOf(notificationPreferences.enabled) }
    var queued by remember { mutableStateOf(notificationPreferences.queued()) }
    var pastedText by remember { mutableStateOf(initialSharedText.orEmpty()) }
    var debitKeywords by remember { mutableStateOf(notificationPreferences.profile().debitKeywords.joinToString(", ")) }
    var creditKeywords by remember { mutableStateOf(notificationPreferences.profile().creditKeywords.joinToString(", ")) }
    var allowedPackages by remember { mutableStateOf(notificationPreferences.allowedPackages) }
    var appMenu by remember { mutableStateOf(false) }
    val notificationApps = remember {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(launcher, 0).map { info ->
            info.activityInfo.packageName to info.loadLabel(context.packageManager).toString()
        }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    val existingKeys = remember(account?.id, rows.size) { account?.id?.let(duplicateKeys).orEmpty() }

    fun reviewCandidates(candidates: List<ImportCandidate>, parseProblems: List<ImportProblem>) {
        candidates.mapNotNull(ImportCandidate::accountHint).distinct().singleOrNull()?.let { hint ->
            accounts.singleOrNull { hint in it.name.filter(Char::isDigit) }?.let { account = it }
        }
        rows.clear()
        val knownKeys = account?.id?.let(duplicateKeys).orEmpty().toMutableSet()
        rows += candidates.map { candidate ->
            val key = ImportDuplicateDetector.key(candidate.date, candidate.amountCents, candidate.payee)
            val duplicate = !knownKeys.add(key)
            ReviewRow(candidate.sourceRow, formatDate(candidate.date), candidate.payee,
                BigDecimal.valueOf(candidate.amountCents, 2).toPlainString(), candidate.notes,
                candidate.reference, selected = !duplicate, candidate.confidence, candidate.sourceLabel)
        }
        problems = parseProblems
        message = if (rows.isEmpty()) resources.getString(R.string.no_valid_transactions_found) else null
    }

    fun review(parsedTable: ImportTable, selectedMapping: ImportColumnMapping) {
        mapping = selectedMapping
        val result = CsvTransactionCandidateSource.parse(parsedTable, selectedMapping)
        reviewCandidates(result.candidates, result.problems)
    }

    fun reviewText(value: String, source: String, format: StatementFormat) {
        val result = FinancialMessageParser.parse(value, source, profile = notificationPreferences.profile())
        sourceName = source; sourceFormat = format; table = null; mapping = null
        reviewCandidates(result.candidates, result.problems)
    }

    LaunchedEffect(initialSharedText) {
        initialSharedText?.takeIf(String::isNotBlank)?.let {
            reviewText(it, resources.getString(R.string.shared_message), StatementFormat.SHARED_TEXT)
            onSharedTextConsumed()
        }
    }

    fun load(uri: Uri) {
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: resources.getString(R.string.default_statement_filename)
                val format = when (name.substringAfterLast('.', "").lowercase()) {
                    "xlsx" -> StatementFormat.XLSX
                    "pdf" -> StatementFormat.PDF
                    else -> StatementFormat.CSV
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use {
                    it.readLimitedStatement()
                } ?: throw SelectedStatementUnreadableException()
                Triple(name, format, StatementDocumentReader.read(context, bytes, format))
            } }.onSuccess { (name, format, parsedTable) ->
                    sourceName = name; sourceFormat = format; table = parsedTable
                    val suggested = CsvTransactionCandidateSource.suggestedMapping(parsedTable.headers)
                    profileName = ""
                    review(parsedTable, suggested)
                }.onFailure {
                    message = resources.getString(
                        when (it) {
                            is SelectedStatementUnreadableException -> R.string.could_not_read_selected_file
                            is StatementTooLargeException -> R.string.statement_too_large
                            else -> R.string.could_not_parse_statement
                        },
                    )
                }
            busy = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(::load)
    }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.import_transactions_title), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.import_transactions_privacy),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(pastedText, { pastedText = it }, label = { Text(stringResource(R.string.paste_financial_alert)) },
                minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = pastedText.isNotBlank(), onClick = {
                    reviewText(pastedText, resources.getString(R.string.pasted_message), StatementFormat.SHARED_TEXT)
                }) { Text(stringResource(R.string.review_text)) }
                if (queued.isNotEmpty()) TextButton(onClick = {
                    sourceName = resources.getString(R.string.captured_notifications); sourceFormat = StatementFormat.NOTIFICATION
                    table = null; mapping = null; reviewCandidates(queued, emptyList())
                }) { Text(pluralStringResource(R.plurals.review_captured, queued.size, queued.size)) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.capture_bank_notifications), Modifier.weight(1f))
                Switch(captureEnabled, { enabled ->
                    if (enabled && allowedPackages.isEmpty()) {
                        message = resources.getString(R.string.select_app_before_capture)
                        appMenu = true
                    } else {
                        captureEnabled = enabled; notificationPreferences.enabled = enabled
                        if (enabled) context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                })
            }
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { appMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (allowedPackages.isEmpty()) stringResource(R.string.select_notification_apps)
                    else pluralStringResource(R.plurals.selected_notification_apps, allowedPackages.size, allowedPackages.size))
                }
                DropdownMenu(appMenu, { appMenu = false }) {
                    notificationApps.forEach { (packageName, label) ->
                        DropdownMenuItem(
                            text = { Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(packageName in allowedPackages, null)
                                Text(label)
                            } },
                            onClick = {
                                allowedPackages = allowedPackages.toMutableSet().apply {
                                    if (!add(packageName)) remove(packageName)
                                }
                                notificationPreferences.allowedPackages = allowedPackages
                                if (allowedPackages.isEmpty()) {
                                    captureEnabled = false; notificationPreferences.enabled = false
                                }
                            },
                        )
                    }
                }
            }
            Text(stringResource(R.string.notification_access_description),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(debitKeywords, { debitKeywords = it }, label = { Text(stringResource(R.string.debit_keywords)) },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(creditKeywords, { creditKeywords = it }, label = { Text(stringResource(R.string.credit_keywords)) },
                modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    val debit = debitKeywords.split(',').map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
                    val credit = creditKeywords.split(',').map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
                    if (debit.isEmpty() || credit.isEmpty()) message = resources.getString(R.string.keep_debit_credit_keyword)
                    else {
                        notificationPreferences.saveProfile(FinancialMessageProfile(debit, credit))
                        message = resources.getString(R.string.saved_parser_keywords)
                    }
                }) { Text(stringResource(R.string.save_parser_words)) }
                TextButton(onClick = {
                    notificationPreferences.clearAll(); captureEnabled = false; queued = emptyList()
                    message = resources.getString(R.string.deleted_notification_data)
                }) { Text(stringResource(R.string.delete_notification_data)) }
            }
            Box(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                OutlinedButton(onClick = { accountMenu = true }, enabled = accounts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(account?.name?.ifBlank { stringResource(R.string.fs_unknown_account) }
                        ?: stringResource(R.string.no_open_account))
                }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    accounts.forEach { option -> DropdownMenuItem(
                        text = { Text(option.name.ifBlank { stringResource(R.string.fs_unknown_account) }) },
                        onClick = {
                        account = option; accountMenu = false
                    }) }
                }
            }
            Button(onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/pdf")) },
                enabled = !busy && account != null, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(if (busy) stringResource(R.string.reading) else stringResource(R.string.choose_statement_file))
            }
            val activeTable = table
            val activeMapping = mapping
            if (activeTable != null && activeMapping != null) {
                Text(stringResource(R.string.column_mapping), style = MaterialTheme.typography.titleMedium)
                activeTable.headers.forEachIndexed { index, header ->
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { mappingMenuIndex = index }, modifier = Modifier.fillMaxWidth()) {
                            val role = activeMapping.roles.getOrElse(index) { ImportColumnRole.IGNORE }
                            Text(stringResource(R.string.column_role,
                                header.ifBlank { stringResource(R.string.column_number, index + 1) },
                                role.displayName()))
                        }
                        DropdownMenu(expanded = mappingMenuIndex == index, onDismissRequest = { mappingMenuIndex = null }) {
                            ImportColumnRole.entries.forEach { role ->
                                DropdownMenuItem(text = { Text(role.displayName()) }, onClick = {
                                    val roles = activeMapping.roles.toMutableList().apply { this[index] = role }
                                    mappingMenuIndex = null
                                    review(activeTable, activeMapping.copy(roles = roles))
                                })
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.expenses_are_positive), Modifier.weight(1f))
                    Switch(checked = activeMapping.expensesArePositive, onCheckedChange = {
                        review(activeTable, activeMapping.copy(expensesArePositive = it))
                    })
                }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { datePatternMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.date_format,
                            if (activeMapping.datePattern == "Auto") stringResource(R.string.automatic) else activeMapping.datePattern))
                    }
                    DropdownMenu(datePatternMenu, { datePatternMenu = false }) {
                        listOf("Auto", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "dd MMM yyyy")
                            .forEach { pattern -> DropdownMenuItem(text = {
                                Text(if (pattern == "Auto") stringResource(R.string.automatic) else pattern)
                            }, onClick = {
                                datePatternMenu = false; review(activeTable, activeMapping.copy(datePattern = pattern))
                            }) }
                    }
                }
                OutlinedTextField(profileName, { profileName = it }, label = { Text(stringResource(R.string.mapping_profile_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    Box {
                        TextButton(enabled = importPreferences.profileNames().isNotEmpty(), onClick = { profileMenu = true }) {
                            Text(stringResource(R.string.load_profile))
                        }
                        DropdownMenu(profileMenu, { profileMenu = false }) {
                            importPreferences.profileNames().forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = {
                                val saved = importPreferences.profile(name)
                                profileMenu = false
                                if (saved?.roles?.size == activeTable.headers.size) {
                                    profileName = name; review(activeTable, saved)
                                } else message = resources.getString(R.string.profile_column_count_mismatch)
                            }) }
                        }
                    }
                    TextButton(enabled = profileName.isNotBlank(), onClick = {
                        importPreferences.saveProfile(profileName.trim(), activeMapping)
                        message = resources.getString(R.string.saved_mapping_profile, profileName.trim())
                    }) { Text(stringResource(R.string.save_profile)) }
                }
            }
            if (problems.isNotEmpty()) Text(
                pluralStringResource(R.plurals.malformed_rows_excluded, problems.size, problems.size) + " " +
                    problems.take(3).map {
                        stringResource(R.string.row_problem, it.sourceRow, it.displayMessage())
                    }.joinToString(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            message?.let { Text(it, modifier = Modifier.padding(bottom = 8.dp)) }
            if (history.isNotEmpty()) {
                Text(stringResource(R.string.recent_imports), style = MaterialTheme.typography.titleMedium)
                history.take(3).forEach { entry ->
                    Text(stringResource(R.string.import_history_entry, entry.sourceName, entry.imported, entry.skipped, entry.accountName),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            rows.forEachIndexed { index, row ->
                val candidate = row.toCandidateOrNull()
                val invalid = candidate == null
                val candidateKey = candidate?.let { ImportDuplicateDetector.key(it.date, it.amountCents, it.payee) }
                val duplicate = candidateKey != null && (candidateKey in existingKeys || rows.take(index).any {
                    it.toCandidateOrNull()?.let { prior ->
                        ImportDuplicateDetector.key(prior.date, prior.amountCents, prior.payee) == candidateKey
                    } == true
                })
                val duplicateReason = when {
                    candidateKey != null && candidateKey in existingKeys -> stringResource(R.string.already_in_account)
                    duplicate -> stringResource(R.string.repeated_in_file)
                    else -> null
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = row.selected, onCheckedChange = { rows[index] = row.copy(selected = it) })
                    Text(stringResource(R.string.format_row, sourceFormat.displayName(), row.sourceRow), style = MaterialTheme.typography.labelLarge)
                    if (duplicateReason != null) Text(stringResource(R.string.duplicate_reason, duplicateReason), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium)
                }
                Text(stringResource(
                    R.string.confidence_source,
                    row.confidence.displayName(),
                    if (row.sourceLabel == "Statement") stringResource(R.string.statement_source) else row.sourceLabel,
                ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(row.date, { rows[index] = row.copy(date = it) }, label = { Text(stringResource(R.string.date_iso_label)) },
                    isError = invalid, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(row.payee, { rows[index] = row.copy(payee = it) }, label = { Text(stringResource(R.string.payee)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(row.amount, { rows[index] = row.copy(amount = it) }, label = { Text(stringResource(R.string.signed_amount)) },
                    isError = invalid, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(row.notes, { rows[index] = row.copy(notes = it) }, label = { Text(stringResource(R.string.notes)) },
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { rows.removeAt(index) }) { Text(stringResource(R.string.reject)) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
        val selected = rows.filter(ReviewRow::selected)
        val ready = selected.mapNotNull(ReviewRow::toCandidateOrNull)
        Button(
            onClick = {
                val target = account ?: return@Button
                if (onImport(target.id, ready)) {
                    message = resources.getQuantityString(R.plurals.imported_transactions, ready.size, ready.size)
                    importPreferences.addHistory(ImportHistoryEntry(sourceName, sourceFormat, target.name,
                        ready.size, rows.size - ready.size, System.currentTimeMillis()))
                    history = importPreferences.history()
                    if (sourceFormat == StatementFormat.NOTIFICATION) {
                        notificationPreferences.clearQueue(); queued = emptyList()
                    }
                    rows.clear(); problems = emptyList()
                }
            },
            enabled = selected.isNotEmpty() && ready.size == selected.size,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text(pluralStringResource(R.plurals.approve_and_import, ready.size, ready.size)) }
        if (history.isNotEmpty()) {
            TextButton(onClick = { importPreferences.clearHistory(); history = emptyList() },
                modifier = Modifier.align(Alignment.End)) {
                Text(pluralStringResource(R.plurals.clear_import_history, history.size, history.size))
            }
        }
    }
}

@Composable
private fun ImportColumnRole.displayName() = stringResource(when (this) {
    ImportColumnRole.IGNORE -> R.string.column_role_ignore
    ImportColumnRole.DATE -> R.string.column_role_date
    ImportColumnRole.PAYEE -> R.string.column_role_payee
    ImportColumnRole.NOTES -> R.string.column_role_notes
    ImportColumnRole.REFERENCE -> R.string.column_role_reference
    ImportColumnRole.AMOUNT -> R.string.column_role_amount
    ImportColumnRole.DEBIT -> R.string.column_role_debit
    ImportColumnRole.CREDIT -> R.string.column_role_credit
})

@Composable
private fun ImportConfidence.displayName() = stringResource(when (this) {
    ImportConfidence.HIGH -> R.string.confidence_high
    ImportConfidence.MEDIUM -> R.string.confidence_medium
    ImportConfidence.LOW -> R.string.confidence_low
})

@Composable
private fun StatementFormat.displayName() = stringResource(when (this) {
    StatementFormat.CSV -> R.string.statement_format_csv
    StatementFormat.XLSX -> R.string.statement_format_xlsx
    StatementFormat.PDF -> R.string.statement_format_pdf
    StatementFormat.SHARED_TEXT -> R.string.statement_format_shared_text
    StatementFormat.NOTIFICATION -> R.string.statement_format_notification
})

@Composable
private fun ImportProblem.displayMessage(): String = when (code) {
    ImportProblemCode.FILE_EMPTY -> stringResource(R.string.import_problem_file_empty)
    ImportProblemCode.MISSING_REQUIRED_COLUMNS -> {
        val columns = detail.orEmpty().split(',').filter(String::isNotBlank).map { column ->
            stringResource(when (column) {
                "date" -> R.string.import_column_date
                "payee" -> R.string.import_column_payee
                else -> R.string.import_column_amount
            })
        }.joinToString()
        stringResource(R.string.import_problem_missing_columns, columns)
    }
    ImportProblemCode.PAYEE_BLANK -> stringResource(R.string.import_problem_payee_blank)
    ImportProblemCode.AMOUNT_BLANK -> stringResource(R.string.import_problem_amount_blank)
    ImportProblemCode.AMOUNT_BLANK_OR_ZERO -> stringResource(R.string.import_problem_amount_blank_or_zero)
    ImportProblemCode.INVALID_AMOUNT -> stringResource(R.string.import_problem_invalid_amount)
    ImportProblemCode.UNSUPPORTED_DATE -> stringResource(R.string.import_problem_unsupported_date, detail.orEmpty())
    ImportProblemCode.UNCLOSED_QUOTED_FIELD -> stringResource(R.string.import_problem_unclosed_quote)
    ImportProblemCode.DEBIT_OR_CREDIT_NOT_RECOGNIZED ->
        stringResource(R.string.import_problem_direction_not_recognized)
    ImportProblemCode.TRANSACTION_AMOUNT_NOT_RECOGNIZED ->
        stringResource(R.string.import_problem_transaction_amount_not_recognized)
    ImportProblemCode.ROW_COULD_NOT_BE_PARSED -> stringResource(R.string.import_problem_row_parse)
}

private fun ReviewRow.toCandidateOrNull(): ImportCandidate? = runCatching {
    val parsedDate = LocalDate.parse(date.trim())
    val cents = BigDecimal(amount.trim()).setScale(2).movePointRight(2).longValueExact()
    require(payee.isNotBlank() && cents != 0L)
    ImportCandidate(sourceRow, parsedDate.year * 10_000 + parsedDate.monthValue * 100 + parsedDate.dayOfMonth,
        payee.trim(), notes.trim(), cents, reference)
}.getOrNull()

private fun formatDate(value: Int): String = "%04d-%02d-%02d".format(value / 10_000, value / 100 % 100, value % 100)

private fun InputStream.readLimitedStatement(maxBytes: Int = 25 * 1024 * 1024): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > maxBytes) throw StatementTooLargeException()
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
