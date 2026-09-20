package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.model.ActualTag

private val tagPalette = listOf("#E57373", "#FFB74D", "#FFF176", "#81C784", "#4DB6AC", "#64B5F6", "#7986CB", "#BA68C8", "#A1887F", "#90A4AE")
internal fun isValidManagedTagName(name: String): Boolean = name.isNotBlank() && name.none { it == '#' || it.isWhitespace() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTagsScreen(
    tags: List<ActualTag>, hiddenSupported: Boolean, onBack: () -> Unit,
    onCreate: (String, String?, String?, Boolean) -> Unit,
    onUpdate: (ActualTag, String, String?, String?, Boolean) -> Unit,
    onDelete: (ActualTag) -> Unit, modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }; var editing by remember { mutableStateOf<ActualTag?>(null) }
    var creating by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf<ActualTag?>(null) }
    var viewing by remember { mutableStateOf<ActualTag?>(null) }; var actionsFor by remember { mutableStateOf<ActualTag?>(null) }
    val filtered = remember(tags, query) { tags.filter { query.isBlank() || it.tag.contains(query, true) || it.description.orEmpty().contains(query, true) } }

    viewing?.let { tag ->
        ManagedTagTransactionsScreen(tag = tag, onBack = { viewing = null }, modifier = modifier)
        return
    }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text(stringResource(R.string.fs_manage_tags)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.fs_back)) } }) }, floatingActionButton = { FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.fs_create_tag)) } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.fs_search_tags)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(if (tags.isEmpty()) R.string.fs_no_managed_tags else R.string.fs_no_matching_tags)) }
            else LazyColumn { items(filtered, key = { it.id }) { tag ->
                Box {
                    ListItem(
                        headlineContent = { Text("#${tag.tag}") },
                        supportingContent = { val detail = listOfNotNull(tag.description?.takeIf(String::isNotBlank), if (tag.hidden) stringResource(R.string.fs_hidden) else null); if (detail.isNotEmpty()) Text(detail.joinToString(" · ")) },
                        leadingContent = { TagColorDot(tag.color) },
                        trailingContent = { IconButton(onClick = { actionsFor = tag }) { Icon(Icons.Outlined.Edit, stringResource(R.string.fs_tag_actions, tag.tag)) } },
                        modifier = Modifier.clickable { viewing = tag },
                    )
                    DropdownMenu(expanded = actionsFor?.id == tag.id, onDismissRequest = { actionsFor = null }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.fs_view_transactions)) }, leadingIcon = { Icon(Icons.Outlined.ReceiptLong, null) }, onClick = { actionsFor = null; viewing = tag })
                        DropdownMenuItem(text = { Text(stringResource(R.string.fs_edit)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { actionsFor = null; editing = tag })
                        DropdownMenuItem(text = { Text(stringResource(R.string.fs_delete)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { actionsFor = null; deleting = tag })
                    }
                }
            } }
        }
    }
    if (creating) TagEditorDialog(stringResource(R.string.fs_create_tag_title), null, hiddenSupported, { creating = false }) { n,c,d,h -> onCreate(n,c,d,h); creating = false }
    editing?.let { tag -> TagEditorDialog(stringResource(R.string.fs_edit_tag_title, tag.tag), tag, hiddenSupported, { editing = null }) { n,c,d,h -> onUpdate(tag,n,c,d,h); editing = null } }
    deleting?.let { tag -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.fs_delete_tag_title, tag.tag)) }, text = { Text(stringResource(R.string.fs_delete_tag_message, tag.tag)) }, confirmButton = { TextButton(onClick = { onDelete(tag); deleting = null }) { Text(stringResource(R.string.fs_delete)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.fs_cancel)) } }) }
}

@Composable private fun TagEditorDialog(title: String, existing: ActualTag?, hiddenSupported: Boolean, onDismiss: () -> Unit, onSave: (String, String?, String?, Boolean) -> Unit) {
    var name by remember(existing) { mutableStateOf(existing?.tag.orEmpty()) }; var color by remember(existing) { mutableStateOf(existing?.color ?: tagPalette.first()) }
    var description by remember(existing) { mutableStateOf(existing?.description.orEmpty()) }; var hidden by remember(existing) { mutableStateOf(existing?.hidden ?: false) }
    val valid = isValidManagedTagName(name)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.fs_name)) }, singleLine = true, isError = name.isNotEmpty() && !valid)
        OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.fs_description)) }); Text(stringResource(R.string.fs_color), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tagPalette.take(5).forEach { option -> Box(Modifier.size(if (color == option) 36.dp else 32.dp).background(parseTagColor(option), CircleShape).clickable { color = option }) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tagPalette.drop(5).forEach { option -> Box(Modifier.size(if (color == option) 36.dp else 32.dp).background(parseTagColor(option), CircleShape).clickable { color = option }) } }
        if (hiddenSupported) Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.fs_hidden), modifier = Modifier.weight(1f)); Switch(hidden, { hidden = it }) }
    } }, confirmButton = { Button(onClick = { onSave(name.trim(), color, description.trim().ifBlank { null }, hidden) }, enabled = valid) { Text(stringResource(R.string.fs_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fs_cancel)) } })
}

@Composable private fun TagColorDot(value: String?) { Box(Modifier.size(18.dp).background(parseTagColor(value), CircleShape)) }
internal fun parseManagedTagColor(value: String?): Color = runCatching { Color(android.graphics.Color.parseColor(value ?: "#808080")) }.getOrDefault(Color.Gray)
private fun parseTagColor(value: String?): Color = parseManagedTagColor(value)
