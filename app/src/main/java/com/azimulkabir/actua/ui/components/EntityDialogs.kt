package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R

@Composable
fun NewCategoryDialog(groups: List<String>, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var group by remember(groups) { mutableStateOf(groups.firstOrNull().orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.entity_new_category)) }, text = {
        Column {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.entity_name)) }, singleLine = true)
            TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(group.ifBlank { stringResource(R.string.entity_select_group) })
            }
            DropdownMenu(expanded, { expanded = false }) { groups.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { group = option; expanded = false })
            } }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }, confirmButton = {
        TextButton(enabled = name.trim().isNotEmpty() && group.isNotEmpty(), onClick = { onSave(group, name.trim()) }) {
            Text(stringResource(R.string.action_add))
        }
    })
}

@Composable
fun MoveCategoryDialog(
    categoryName: String,
    groups: List<String>,
    currentGroup: String,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
) {
    var selected by remember(groups) { mutableStateOf(groups.firstOrNull { it != currentGroup }.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = {
        Text(stringResource(R.string.entity_move_category, categoryName))
    }, text = {
        Column {
            groups.forEach { option ->
                val enabled = option != currentGroup
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { selected = option },
                ) {
                    RadioButton(selected = option == selected, onClick = { selected = option }, enabled = enabled)
                    Text(option, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }, confirmButton = {
        TextButton(enabled = selected.isNotEmpty() && selected != currentGroup, onClick = { onMove(selected) }) {
            Text(stringResource(R.string.action_move))
        }
    })
}

val AccountTypeOptions = listOf("Checking", "Savings", "Credit", "Investment", "Mortgage", "Debt", "Other")

@Composable
fun NewAccountDialog(onDismiss: () -> Unit, onSave: (String, Boolean, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var balance by remember { mutableStateOf("") }
    var offBudget by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(AccountTypeOptions.first()) }
    var typeExpanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.accounts_add)) }, text = {
        Column {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.entity_name)) }, singleLine = true)
            OutlinedTextField(balance, { balance = it.filter { char -> char.isDigit() || char in ".-" } },
                label = { Text(stringResource(R.string.entity_starting_balance)) }, singleLine = true)
            TextButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.entity_type_value, localizedAccountType(type)))
            }
            DropdownMenu(typeExpanded, { typeExpanded = false }) { AccountTypeOptions.forEach { option ->
                DropdownMenuItem(text = { Text(localizedAccountType(option)) }, onClick = { type = option; typeExpanded = false })
            } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.entity_off_budget), modifier = Modifier.weight(1f)); Switch(offBudget, { offBudget = it })
            }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }, confirmButton = {
        TextButton(enabled = name.trim().isNotEmpty(), onClick = { onSave(name.trim(), offBudget, balance, type) }) {
            Text(stringResource(R.string.action_add))
        }
    })
}

@Composable
fun ChangeAccountTypeDialog(
    accountName: String,
    currentType: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var selected by remember(currentType) { mutableStateOf(currentType) }
    AlertDialog(onDismissRequest = onDismiss, title = {
        Text(stringResource(R.string.entity_change_account_type, accountName))
    }, text = {
        Column {
            AccountTypeOptions.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { selected = option },
                ) {
                    RadioButton(selected = option == selected, onClick = { selected = option })
                    Text(localizedAccountType(option), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }, confirmButton = {
        TextButton(enabled = selected != currentType, onClick = { onSave(selected) }) {
            Text(stringResource(R.string.action_save))
        }
    })
}

@Composable
private fun localizedAccountType(type: String): String = stringResource(
    when (type) {
        "Checking" -> R.string.account_type_checking
        "Savings" -> R.string.account_type_savings
        "Credit" -> R.string.account_type_credit
        "Investment" -> R.string.account_type_investment
        "Mortgage" -> R.string.account_type_mortgage
        "Debt" -> R.string.account_type_debt
        else -> R.string.account_type_other
    },
)
