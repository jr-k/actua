package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardCycle
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.theme.warning
import com.azimulkabir.actua.ui.components.formatMoneyCents
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun CreditCardsScreen(
    cards: List<CreditCardStatus>,
    accounts: List<Account>,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onSave: (String, Int, CreditCardCycle.PaymentDue, Long?) -> Unit,
    onRemove: (String) -> Unit,
    notificationsEnabled: Boolean = false,
    onNotificationsEnabledChange: (Boolean) -> Unit = {},
    onViewStatements: (CreditCardStatus) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<CreditCardStatus?>(null) }
    var adding by remember { mutableStateOf(false) }
    val configured = cards.mapTo(mutableSetOf()) { it.accountId }
    val availableAccounts = accounts.filter { !it.closed && it.id !in configured }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.fs_credit_cards), onBack = onBack) {
            IconButton(onClick = { adding = true }, enabled = availableAccounts.isNotEmpty()) {
                Icon(Icons.Outlined.Add, stringResource(R.string.fs_add_credit_card))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.fs_payment_reminders), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.fs_payment_reminders_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsEnabledChange)
                }
            }
            if (cards.isEmpty()) item {
                Text(stringResource(R.string.fs_no_credit_cards),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp))
            }
            items(cards, key = { it.accountId }) { card ->
                CreditCardRow(card, hideDecimalPlaces,
                    Modifier.padding(horizontal = 16.dp).clickable { editing = card },
                    onViewStatements = { onViewStatements(card) })
            }
        }
    }

    if (adding) CardEditorDialog(null, availableAccounts, onDismiss = { adding = false }, onSave = { id, day, paymentDue, limit ->
        onSave(id, day, paymentDue, limit); adding = false
    })
    editing?.let { card -> CardEditorDialog(card, accounts, onDismiss = { editing = null }, onSave = { id, day, paymentDue, limit ->
        onSave(id, day, paymentDue, limit); editing = null
    }, onRemove = { onRemove(card.accountId); editing = null }) }
}

@Composable
private fun CreditCardRow(
    card: CreditCardStatus, hideDecimals: Boolean, modifier: Modifier = Modifier,
    onViewStatements: () -> Unit = {},
) {
    val days = card.cycle.daysUntilDue()
    val urgency = when { days <= 3 -> MaterialTheme.colorScheme.error; days <= 7 -> MaterialTheme.colorScheme.warning; else -> Color(0xFFF9A825) }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(vertical = 0.dp).align(Alignment.CenterVertically)) {
                Surface(color = urgency, modifier = Modifier.padding(0.dp)) { Box(Modifier.padding(horizontal = 2.dp, vertical = 34.dp)) }
            }
            Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(card.accountName.ifBlank { stringResource(R.string.fs_unknown_account) },
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(formatMoneyCents(card.balanceCents, hideDecimals), fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val daysRemaining = card.cycle.daysRemainingInCycle()
                    Text(pluralStringResource(R.plurals.fs_card_spend_days_left, daysRemaining,
                        formatMoneyCents(card.cycleSpendCents, hideDecimals), daysRemaining),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    val daysUntilDue = card.cycle.daysUntilDue()
                    Text(when (daysUntilDue) {
                        0 -> stringResource(R.string.fs_due_today)
                        1 -> stringResource(R.string.fs_due_tomorrow)
                        else -> pluralStringResource(R.plurals.fs_due_in_days, daysUntilDue, daysUntilDue)
                    }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp))
                }
                card.availableCreditCents?.let {
                    Text(stringResource(R.string.fs_available_credit, formatMoneyCents(it, hideDecimals)), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onViewStatements) {
                Icon(Icons.Outlined.History, stringResource(R.string.fs_view_recent_statements))
            }
        }
    }
}

@Composable
private fun CardEditorDialog(
    card: CreditCardStatus?, accounts: List<Account>, onDismiss: () -> Unit,
    onSave: (String, Int, CreditCardCycle.PaymentDue, Long?) -> Unit, onRemove: (() -> Unit)? = null,
) {
    var accountId by remember { mutableStateOf(card?.accountId ?: accounts.firstOrNull()?.id.orEmpty()) }
    var day by remember { mutableStateOf((card?.config?.statementDay ?: 15).toString()) }
    var offset by remember { mutableStateOf((card?.config?.dueOffsetDays ?: CreditCardCycle.DEFAULT_DUE_OFFSET_DAYS).toString()) }
    var useFixedDueDay by remember { mutableStateOf(card?.config?.dueDay != null) }
    var dueDay by remember { mutableStateOf((card?.config?.dueDay ?: 1).toString()) }
    var limit by remember { mutableStateOf(card?.config?.limitCents?.let { BigDecimal(it).movePointLeft(2).toPlainString() }.orEmpty()) }
    var accountsExpanded by remember { mutableStateOf(false) }
    val account = accounts.firstOrNull { it.id == accountId }
    val validDay = day.toIntOrNull()?.takeIf { it in 1..31 }
    val validOffset = offset.toIntOrNull()?.takeIf { it in 1..CreditCardCycle.MAX_DUE_OFFSET_DAYS }
    val validDueDay = dueDay.toIntOrNull()?.takeIf { it in 1..31 }
    val limitCents = runCatching { limit.takeIf(String::isNotBlank)?.let {
        BigDecimal(it).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact().takeIf { cents -> cents > 0 }
    } }.getOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (card == null) R.string.fs_add_credit_card_title else R.string.fs_edit_card)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (card == null) Box {
                TextButton(onClick = { accountsExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(account?.name?.ifBlank { stringResource(R.string.fs_unknown_account) }
                        ?: stringResource(R.string.fs_select_account))
                }
                DropdownMenu(accountsExpanded, { accountsExpanded = false }) { accounts.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name.ifBlank { stringResource(R.string.fs_unknown_account) }) },
                        onClick = { accountId = option.id; accountsExpanded = false },
                    )
                } }
            } else Text(stringResource(
                R.string.fs_account_named,
                card.accountName.ifBlank { stringResource(R.string.fs_unknown_account) },
            ))
            OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.fs_statement_closing_day)) }, singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !useFixedDueDay,
                    onClick = { useFixedDueDay = false },
                    label = { Text(stringResource(R.string.fs_days_after)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = useFixedDueDay,
                    onClick = { useFixedDueDay = true },
                    label = { Text(stringResource(R.string.fs_day_of_month)) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (useFixedDueDay) {
                OutlinedTextField(dueDay, { dueDay = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.fs_payment_due_day)) }, singleLine = true)
            } else {
                OutlinedTextField(offset, { offset = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.fs_payment_due_after)) }, singleLine = true)
            }
            OutlinedTextField(limit, { value -> limit = value.filter { it.isDigit() || it == '.' } }, label = { Text(stringResource(R.string.fs_credit_limit_optional)) }, singleLine = true)
            Text(stringResource(if (useFixedDueDay) R.string.fs_fixed_due_explanation else R.string.fs_offset_due_explanation),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onRemove != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.fs_remove_credit_card_tracking), color = MaterialTheme.colorScheme.error) }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fs_cancel)) } }, confirmButton = {
        val validPaymentDue = if (useFixedDueDay) validDueDay != null else validOffset != null
        Button(enabled = accountId.isNotBlank() && validDay != null && validPaymentDue && (limit.isBlank() || limitCents != null),
            onClick = {
                val paymentDue = if (useFixedDueDay) CreditCardCycle.PaymentDue.DayOfMonth(validDueDay!!)
                else CreditCardCycle.PaymentDue.DaysAfter(validOffset!!)
                onSave(accountId, validDay!!, paymentDue, limitCents)
            }) { Text(stringResource(R.string.fs_save)) }
    })
}
