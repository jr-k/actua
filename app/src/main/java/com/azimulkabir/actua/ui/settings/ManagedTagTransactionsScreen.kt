package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.model.ActualTag
import com.azimulkabir.actua.data.sync.SyncSignals
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.components.formatStoredDate
import com.azimulkabir.actua.ui.transactions.filterByTag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManagedTagTransactionsScreen(tag: ActualTag, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { ActuaRepository(context) }
    val syncGeneration by SyncSignals.dataGeneration.collectAsState()
    val transactions = remember(syncGeneration, tag.tag) { repository.transactions().filterByTag(tag.tag) }
    BackHandler(onBack = onBack)

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.tag_transactions_title)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } })
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = onBack,
                label = { Text("#${tag.tag}") },
                leadingIcon = { androidx.compose.foundation.layout.Box(Modifier.size(12.dp).background(parseManagedTagColor(tag.color), CircleShape)) },
                trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.clear_tag_filter), modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            )
            Spacer(Modifier.weight(1f))
            Text(pluralStringResource(R.plurals.transaction_count, transactions.size, transactions.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (transactions.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.no_transactions_use_tag, tag.tag), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.exact_hashtag_matching, tag.tag), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(transactions, key = { it.id }) { transaction ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(transaction.payee.ifBlank { stringResource(R.string.no_payee) }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(transaction.category, transaction.account).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatMoneyCents(transaction.amountCents, false), fontWeight = FontWeight.SemiBold)
                    }
                    transaction.notes.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                    Text(formatStoredDate(transaction.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                HorizontalDivider()
            }
        }
    }
}
