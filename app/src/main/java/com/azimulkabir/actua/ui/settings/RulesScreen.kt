package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleChoice
import com.azimulkabir.actua.data.rules.RuleEditorData
import com.azimulkabir.actua.data.rules.RuleFieldType
import com.azimulkabir.actua.data.rules.RuleSchema
import com.azimulkabir.actua.data.rules.RuleValue
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import java.time.LocalDate

@Composable
fun RulesScreen(
    rules: List<Rule>,
    supported: Boolean,
    scheduleOwnedRuleIds: Set<String>,
    editorData: RuleEditorData,
    onBack: () -> Unit,
    onSave: (Rule) -> Boolean,
    onDelete: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Rule?>(null) }
    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.fs_rules_title), onBack = onBack) {
            if (supported) IconButton(onClick = { editing = Rule.empty() }) { Icon(Icons.Outlined.Add, stringResource(R.string.fs_add_rule)) }
        }
        if (!supported) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.fs_rules_unavailable), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.fs_rules_unavailable_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.fs_search_rules)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        val names = editorData.names
        val filtered = mutableListOf<Rule>()
        for (rule in rules) {
            if (ruleSummary(rule, names).contains(search, true)) filtered += rule
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            filtered.forEach { rule ->
                Surface(onClick = { editing = rule }, color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stageLabel(rule.stage), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            if (rule.id in scheduleOwnedRuleIds) Text("  •  ${stringResource(R.string.fs_schedule_badge)}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(R.string.fs_if), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        rule.conditions.forEachIndexed { index, condition ->
                            Text((if (index > 0) "${conditionsOpLabel(rule.conditionsOp)} " else "") + conditionSummary(condition, names),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(stringResource(R.string.fs_then), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        rule.actions.forEach { Text(actionSummary(it, names), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (filtered.isEmpty()) Text(stringResource(if (search.isBlank()) R.string.fs_no_rules else R.string.fs_no_matching_rules),
                modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    }
    editing?.let { rule ->
        RuleEditor(rule, editorData, rule.id in scheduleOwnedRuleIds, onDismiss = { editing = null },
            onSave = { if (onSave(it)) editing = null },
            onDelete = { if (onDelete(rule.id)) editing = null })
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RuleEditor(rule: Rule, data: RuleEditorData, scheduleOwned: Boolean,
    onDismiss: () -> Unit, onSave: (Rule) -> Unit, onDelete: () -> Unit) {
    var draft by remember(rule.id) { mutableStateOf(rule) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (rule.conditions.isEmpty() && rule.actions.isEmpty()) R.string.fs_new_rule else R.string.fs_edit_rule),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.fs_stage), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Rule.Stage.entries.forEach { stage -> FilterChip(selected = draft.stage == stage,
                    onClick = { draft = draft.copy(stage = stage) }, label = { Text(stageLabel(stage)) }) }
            }
            Text(stringResource(R.string.fs_match), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(draft.conditionsOp == Rule.ConditionsOp.AND,
                    { draft = draft.copy(conditionsOp = Rule.ConditionsOp.AND) }, { Text(stringResource(R.string.fs_all_conditions)) })
                FilterChip(draft.conditionsOp == Rule.ConditionsOp.OR,
                    { draft = draft.copy(conditionsOp = Rule.ConditionsOp.OR) }, { Text(stringResource(R.string.fs_any_condition)) })
            }
            Text(stringResource(R.string.fs_if_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            draft.conditions.forEachIndexed { index, condition ->
                ConditionEditor(condition, data, onChange = { changed ->
                    draft = draft.copy(conditions = draft.conditions.toMutableList().also { it[index] = changed })
                }, onRemove = { draft = draft.copy(conditions = draft.conditions.toMutableList().also { it.removeAt(index) }) })
            }
            TextButton(onClick = { draft = draft.copy(conditions = draft.conditions +
                Rule.Condition("is", "imported_payee", RuleValue.Text(""))) }) {
                Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.fs_add_condition))
            }
            Text(stringResource(R.string.fs_then_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            draft.actions.forEachIndexed { index, action ->
                ActionEditor(action, data, onChange = { changed ->
                    draft = draft.copy(actions = draft.actions.toMutableList().also { it[index] = changed })
                }, onRemove = { draft = draft.copy(actions = draft.actions.toMutableList().also { it.removeAt(index) }) })
            }
            TextButton(onClick = { draft = draft.copy(actions = draft.actions +
                Rule.Action("set", "category", RuleValue.Null)) }) {
                Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.fs_add_action))
            }
            HorizontalDivider()
            Button(onClick = { onSave(draft) }, enabled = draft.conditions.isNotEmpty() && draft.actions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fs_save_rule)) }
            if (!scheduleOwned && rule.conditions.isNotEmpty()) TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Delete, null); Text(stringResource(R.string.fs_delete_rule))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fs_cancel)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConditionEditor(condition: Rule.Condition, data: RuleEditorData,
    onChange: (Rule.Condition) -> Unit, onRemove: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectField(ruleFieldLabel(condition.field), RuleSchema.conditionFields.map { it to ruleFieldLabel(it) }) { field ->
                    val op = RuleSchema.validOps(field).firstOrNull() ?: "is"
                    onChange(condition.copy(field = field, op = op, value = defaultValue(field, op), options = emptyMap()))
                }
                Spacer(Modifier.weight(1f)); IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, stringResource(R.string.fs_remove_condition)) }
            }
            SelectField(ruleOpLabel(condition.op), RuleSchema.validOps(condition.field).map { it to ruleOpLabel(it) }) { op ->
                onChange(condition.copy(op = op, value = defaultValue(condition.field, op)))
            }
            if (condition.op !in setOf("onBudget", "offBudget")) RuleValueEditor(condition.field, condition.op,
                condition.value, condition.options, data) { value, options -> onChange(condition.copy(value = value, options = options)) }
        }
    }
}

@Composable
private fun ActionEditor(action: Rule.Action, data: RuleEditorData,
    onChange: (Rule.Action) -> Unit, onRemove: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectField(ruleOpLabel(action.op), listOf("set", "prepend-notes", "append-notes", "delete-transaction")
                    .map { it to ruleOpLabel(it) }) { op ->
                    onChange(when (op) {
                        "set" -> Rule.Action(op, "category", RuleValue.Null)
                        "delete-transaction" -> Rule.Action(op, null, RuleValue.Null)
                        else -> Rule.Action(op, "notes", RuleValue.Text(""))
                    })
                }
                Spacer(Modifier.weight(1f)); IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, stringResource(R.string.fs_remove_action)) }
            }
            if (action.op == "set") {
                val field = action.field ?: "category"
                SelectField(ruleFieldLabel(field), RuleSchema.actionFields.map { it to ruleFieldLabel(it) }) {
                    onChange(action.copy(field = it, value = defaultValue(it, "is"), options = emptyMap()))
                }
                RuleValueEditor(field, "is", action.value, action.options, data) { value, options ->
                    onChange(action.copy(value = value, options = options))
                }
            } else if (action.op != "delete-transaction") {
                OutlinedTextField(action.value.text.orEmpty(), { onChange(action.copy(value = RuleValue.Text(it))) },
                    label = { Text(stringResource(R.string.fs_text)) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RuleValueEditor(field: String, op: String, value: RuleValue, options: Map<String, RuleValue>,
    data: RuleEditorData, onChange: (RuleValue, Map<String, RuleValue>) -> Unit) {
    val choices = when (field) { "account" -> data.accounts; "payee" -> data.payees; "category" -> data.categories;
        "category_group" -> data.categoryGroups; else -> emptyList() }
    val unknownName = stringResource(when (field) {
        "account" -> R.string.fs_unknown_account
        "payee" -> R.string.fs_unknown_payee
        else -> R.string.common_unknown
    })
    val displayChoices = choices.map { choice ->
        choice.copy(name = choice.name.ifBlank { unknownName })
    }
    when (RuleSchema.type(field)) {
        RuleFieldType.ID -> {
            if (op in setOf("oneOf", "notOneOf")) MultiChoice(value.list.orEmpty(), displayChoices) { onChange(RuleValue.ListValue(it), options) }
            else {
                val selectedName = data.names[value.text]
                    ?: choices.firstOrNull { it.id == value.text }?.name
                SelectField(
                    selectedName?.ifBlank { unknownName } ?: stringResource(R.string.fs_select_value),
                    displayChoices.map { it.id to it.name },
                ) { onChange(RuleValue.Text(it), options) }
            }
        }
        RuleFieldType.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fs_value), Modifier.weight(1f)); Switch(value.flag == true, { onChange(RuleValue.Flag(it), options) })
        }
        RuleFieldType.NUMBER -> {
            if (op == "isbetween") {
                val map = (value as? RuleValue.ObjectValue)?.value.orEmpty()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput(stringResource(R.string.fs_from), map["num1"]?.number, Modifier.weight(1f)) { a -> onChange(RuleValue.ObjectValue(map + ("num1" to RuleValue.Number(a))), options) }
                    NumberInput(stringResource(R.string.fs_to), map["num2"]?.number, Modifier.weight(1f)) { b -> onChange(RuleValue.ObjectValue(map + ("num2" to RuleValue.Number(b))), options) }
                }
            } else NumberInput(stringResource(R.string.fs_amount), value.number, Modifier.fillMaxWidth()) { onChange(RuleValue.Number(it), options) }
            if (field == "amount" && op != "isbetween") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(stringResource(R.string.fs_any) to null, stringResource(R.string.fs_outflow) to "outflow", stringResource(R.string.fs_inflow) to "inflow").forEach { (label, key) ->
                    val selected = if (key == null) options["outflow"]?.flag != true && options["inflow"]?.flag != true else options[key]?.flag == true
                    FilterChip(selected, { onChange(value, key?.let { mapOf(it to RuleValue.Flag(true)) }.orEmpty()) }, { Text(label) })
                }
            }
        }
        else -> OutlinedTextField(
            if (op in setOf("oneOf", "notOneOf")) value.list.orEmpty().mapNotNull { it.text }.joinToString(", ")
            else value.text.orEmpty(), { text ->
            onChange(if (op in setOf("oneOf", "notOneOf")) RuleValue.ListValue(text.split(',').map { RuleValue.Text(it.trim()) }.filter { it.value.isNotEmpty() })
                else RuleValue.Text(text), options)
        }, label = { Text(stringResource(if (field == "date") R.string.fs_date_iso_hint else if (op == "matches") R.string.fs_regular_expression else R.string.fs_value)) },
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NumberInput(label: String, cents: Double?, modifier: Modifier, onChange: (Double) -> Unit) {
    OutlinedTextField(if (cents == null) "" else "%.2f".format(cents / 100.0), { text ->
        text.toDoubleOrNull()?.let { onChange(it * 100.0) }
    }, label = { Text(label) }, modifier = modifier, singleLine = true)
}

@Composable
private fun MultiChoice(selected: List<RuleValue>, choices: List<RuleChoice>, onChange: (List<RuleValue>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val ids = selected.mapNotNull { it.text }.toSet()
    Box {
        TextButton(onClick = { open = true }) { Text(if (ids.isEmpty()) stringResource(R.string.fs_select_values)
            else pluralStringResource(R.plurals.fs_values_selected, ids.size, ids.size)) }
        DropdownMenu(open, { open = false }) { choices.forEach { choice ->
            DropdownMenuItem(text = { Text(choice.name) }, leadingIcon = { Checkbox(choice.id in ids, null) }, onClick = {
                val updated = if (choice.id in ids) ids - choice.id else ids + choice.id
                onChange(updated.map { RuleValue.Text(it) })
            })
        } }
    }
}

@Composable
private fun SelectField(label: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        DropdownMenu(open, { open = false }) { options.forEach { (value, title) ->
            DropdownMenuItem(text = { Text(title) }, onClick = { open = false; onSelect(value) })
        } }
    }
}

private fun defaultValue(field: String, op: String): RuleValue = when {
    op in setOf("oneOf", "notOneOf") -> RuleValue.ListValue(emptyList())
    op == "isbetween" -> RuleValue.ObjectValue(mapOf("num1" to RuleValue.Number(0.0), "num2" to RuleValue.Number(0.0)))
    RuleSchema.type(field) == RuleFieldType.NUMBER -> RuleValue.Number(0.0)
    RuleSchema.type(field) == RuleFieldType.BOOLEAN -> RuleValue.Flag(true)
    RuleSchema.type(field) in setOf(RuleFieldType.STRING, RuleFieldType.DATE) -> RuleValue.Text("")
    else -> RuleValue.Null
}

@Composable
private fun ruleSummary(rule: Rule, names: Map<String, String>): String {
    val summaries = mutableListOf<String>()
    for (condition in rule.conditions) summaries += conditionSummary(condition, names)
    for (action in rule.actions) summaries += actionSummary(action, names)
    return summaries.joinToString(" ")
}

@Composable
private fun conditionSummary(condition: Rule.Condition, names: Map<String, String>) =
    stringResource(R.string.fs_rule_condition_summary, ruleFieldLabel(condition.field),
        ruleOpLabel(condition.op), valueLabel(condition.value, names)).trim()

@Composable
private fun actionSummary(action: Rule.Action, names: Map<String, String>) = when (action.op) {
    "set" -> stringResource(R.string.fs_rule_action_set,
        action.field?.let { ruleFieldLabel(it) }.orEmpty(), valueLabel(action.value, names))
    "prepend-notes" -> stringResource(R.string.fs_rule_action_prepend, action.value.text.orEmpty())
    "append-notes" -> stringResource(R.string.fs_rule_action_append, action.value.text.orEmpty())
    "delete-transaction" -> stringResource(R.string.fs_rule_action_delete)
    else -> ruleOpLabel(action.op)
}

@Composable
private fun valueLabel(value: RuleValue, names: Map<String, String>): String = when (value) {
    is RuleValue.Text -> names[value.value] ?: value.value
    is RuleValue.Number -> "%.2f".format(value.value / 100.0)
    is RuleValue.Flag -> stringResource(if (value.value) R.string.fs_boolean_true else R.string.fs_boolean_false)
    is RuleValue.ListValue -> {
        val labels = mutableListOf<String>()
        for (item in value.value) labels += valueLabel(item, names)
        labels.joinToString(", ")
    }
    is RuleValue.ObjectValue -> {
        val labels = mutableListOf<String>()
        for (item in value.value.values) labels += valueLabel(item, names)
        labels.joinToString(" – ")
    }
    RuleValue.Null -> ""
}

@Composable
private fun stageLabel(stage: Rule.Stage) = stringResource(when (stage) {
    Rule.Stage.PRE -> R.string.fs_stage_pre
    Rule.Stage.DEFAULT -> R.string.fs_stage_default
    Rule.Stage.POST -> R.string.fs_stage_post
})

@Composable
private fun conditionsOpLabel(op: Rule.ConditionsOp) =
    stringResource(if (op == Rule.ConditionsOp.AND) R.string.fs_and else R.string.fs_or)

@Composable
private fun ruleFieldLabel(field: String) = stringResource(when (field) {
    "imported_payee" -> R.string.fs_rule_field_imported_payee
    "account" -> R.string.fs_rule_field_account
    "category" -> R.string.fs_rule_field_category
    "category_group" -> R.string.fs_rule_field_category_group
    "date" -> R.string.fs_rule_field_date
    "payee" -> R.string.fs_rule_field_payee
    "payee_name" -> R.string.fs_rule_field_payee_name
    "notes" -> R.string.fs_rule_field_notes
    "amount", "amount-inflow", "amount-outflow" -> R.string.fs_rule_field_amount
    "cleared" -> R.string.fs_rule_field_cleared
    else -> R.string.fs_value
})

@Composable
private fun ruleOpLabel(op: String) = stringResource(when (op) {
    "is" -> R.string.fs_rule_op_is
    "isNot" -> R.string.fs_rule_op_is_not
    "oneOf" -> R.string.fs_rule_op_one_of
    "notOneOf" -> R.string.fs_rule_op_not_one_of
    "isapprox" -> R.string.fs_rule_op_is_approx
    "isbetween" -> R.string.fs_rule_op_is_between
    "contains" -> R.string.fs_rule_op_contains
    "doesNotContain" -> R.string.fs_rule_op_does_not_contain
    "matches" -> R.string.fs_rule_op_matches
    "hasTags" -> R.string.fs_rule_op_has_all_tags
    "hasAnyTag" -> R.string.fs_rule_op_has_any_tag
    "onBudget" -> R.string.fs_rule_op_on_budget
    "offBudget" -> R.string.fs_rule_op_off_budget
    "gt" -> R.string.fs_rule_op_greater
    "gte" -> R.string.fs_rule_op_greater_equal
    "lt" -> R.string.fs_rule_op_less
    "lte" -> R.string.fs_rule_op_less_equal
    "set" -> R.string.fs_rule_op_set
    "prepend-notes" -> R.string.fs_rule_op_prepend_notes
    "append-notes" -> R.string.fs_rule_op_append_notes
    "delete-transaction" -> R.string.fs_rule_op_delete_transaction
    else -> R.string.fs_value
})
