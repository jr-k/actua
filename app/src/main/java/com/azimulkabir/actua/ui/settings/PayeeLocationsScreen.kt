package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.PayeeLocationSummary
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import java.text.DateFormat
import java.util.Date

private sealed interface LocationDeletion {
    data class One(val location: PayeeLocationSummary) : LocationDeletion
    data class All(val payeeId: String, val payeeName: String) : LocationDeletion
}

@Composable
fun PayeeLocationsScreen(
    locations: List<PayeeLocationSummary>,
    writesSupported: Boolean,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onClearPayee: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeletion by remember { mutableStateOf<LocationDeletion?>(null) }
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.payee_locations_title), onBack = onBack)
        Text(
            stringResource(R.string.payee_locations_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (!writesSupported) {
            Text(
                stringResource(R.string.payee_locations_read_only),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (locations.isEmpty()) {
            Text(
                stringResource(R.string.payee_locations_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                locations.groupBy { it.payeeId to it.payeeName }.forEach { (payee, entries) ->
                    item(key = "heading-" + payee.first) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                payee.second,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { pendingDeletion = LocationDeletion.All(payee.first, payee.second) },
                                enabled = writesSupported,
                            ) { Text(stringResource(R.string.clear_all)) }
                        }
                    }
                    items(entries, key = PayeeLocationSummary::id) { location ->
                        ListItem(
                            headlineContent = {
                                Text("%.5f, %.5f".format(location.latitude, location.longitude))
                            },
                            supportingContent = {
                                Text(DateFormat.getDateTimeInstance().format(Date(location.createdAt)))
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { pendingDeletion = LocationDeletion.One(location) },
                                    enabled = writesSupported,
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_location))
                                }
                            },
                        )
                    }
                    item(key = "divider-" + payee.first) { HorizontalDivider() }
                }
            }
        }
    }

    pendingDeletion?.let { deletion ->
        val description = when (deletion) {
            is LocationDeletion.One -> stringResource(R.string.delete_one_payee_location, deletion.location.payeeName)
            is LocationDeletion.All -> stringResource(R.string.delete_all_payee_locations, deletion.payeeName)
        }
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.delete_payee_location_title)) },
            text = { Text(description) },
            confirmButton = {
                TextButton(onClick = {
                    when (deletion) {
                        is LocationDeletion.One -> onDelete(deletion.location.id)
                        is LocationDeletion.All -> onClearPayee(deletion.payeeId)
                    }
                    pendingDeletion = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
