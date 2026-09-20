package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.schedules.RecurConfig
import com.azimulkabir.actua.data.schedules.ScheduleDiscovery
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.formatMoneyCents

@Composable
fun FindSchedulesScreen(
    proposals: List<ScheduleDiscovery.DisplayProposal>?,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onCreate: (List<ScheduleDiscovery.Proposal>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedIds by remember(proposals) { mutableStateOf(emptySet<String>()) }
    val selected = proposals.orEmpty().filter { it.proposal.id in selectedIds }.map { it.proposal }
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.fs_find_schedules), onBack = onBack) {
            TextButton(onClick = { onCreate(selected) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.fs_create))
            }
        }
        if (proposals == null) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.fs_looking_for_repeating), modifier = Modifier.padding(top = 16.dp))
            }
        } else if (proposals.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.fs_nothing_found), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.fs_no_repeating_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                stringResource(R.string.fs_select_repeating),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(proposals, key = { it.proposal.id }) { item ->
                    val checked = item.proposal.id in selectedIds
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedIds = if (checked) selectedIds - item.proposal.id
                            else selectedIds + item.proposal.id
                        }.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    item.payeeName.ifBlank { stringResource(R.string.fs_unknown_payee) },
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(formatMoneyCents(item.proposal.amount, hideDecimalPlaces))
                            }
                            Text(
                                recurrenceLabel(item.proposal.config),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                item.accountName.ifBlank { stringResource(R.string.fs_unknown_account) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}

@Composable
private fun recurrenceLabel(config: RecurConfig): String {
    val base = when (config.frequency) {
        RecurConfig.Frequency.DAILY -> if (config.interval == 1) {
            stringResource(R.string.fs_every_day)
        } else {
            pluralStringResource(R.plurals.fs_every_days, config.interval, config.interval)
        }
        RecurConfig.Frequency.WEEKLY -> if (config.interval == 1) {
            stringResource(R.string.fs_every_week)
        } else {
            pluralStringResource(R.plurals.fs_every_weeks, config.interval, config.interval)
        }
        RecurConfig.Frequency.MONTHLY -> if (config.interval == 1) {
            stringResource(R.string.fs_every_month)
        } else {
            pluralStringResource(R.plurals.fs_every_months, config.interval, config.interval)
        }
        RecurConfig.Frequency.YEARLY -> if (config.interval == 1) {
            stringResource(R.string.fs_every_year)
        } else {
            pluralStringResource(R.plurals.fs_every_years, config.interval, config.interval)
        }
    }
    return if (config.exactPatternLabel().isEmpty()) base else "$base · ${config.exactPatternLabel()}"
}

@Composable
private fun RecurConfig.exactPatternLabel(): String {
    val labels = mutableListOf<String>()
    for (pattern in patterns) {
        labels += when {
        pattern.type == "day" && pattern.value == -1 -> stringResource(R.string.fs_last_day)
        pattern.type == "day" -> stringResource(R.string.fs_day_number, pattern.value)
        pattern.value == -1 -> stringResource(R.string.fs_last_weekday, weekdayLabel(pattern.type))
        else -> stringResource(R.string.fs_ordinal_weekday, ordinal(pattern.value), weekdayLabel(pattern.type))
        }
    }
    return labels.joinToString(", ")
}

@Composable
private fun weekdayLabel(code: String) = stringResource(when (code) {
    "SU" -> R.string.fs_sunday
    "MO" -> R.string.fs_monday
    "TU" -> R.string.fs_tuesday
    "WE" -> R.string.fs_wednesday
    "TH" -> R.string.fs_thursday
    "FR" -> R.string.fs_friday
    "SA" -> R.string.fs_saturday
    else -> R.string.fs_day
})

@Composable
private fun ordinal(value: Int): String = if (value == 1) stringResource(R.string.fs_ordinal_first) else stringResource(
    if (value % 100 in 11..13) R.string.fs_ordinal_other else when (value % 10) {
        1 -> R.string.fs_ordinal_st
        2 -> R.string.fs_ordinal_nd
        3 -> R.string.fs_ordinal_rd
        else -> R.string.fs_ordinal_other
    },
    value,
)
