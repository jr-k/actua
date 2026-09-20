package com.azimulkabir.actua.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.azimulkabir.actua.data.budget.model.ActualTag
import com.azimulkabir.actua.R

@Composable
internal fun TagAutocompleteField(
    value: String,
    tags: List<ActualTag>,
    onValueChange: (String) -> Unit,
    onCreateTag: (String) -> ActualTag?,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var dismissedToken by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(value) {
        if (value != fieldValue.text) fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
    }
    val token = activeTagToken(fieldValue.text, fieldValue.selection.end)
    val tokenKey = token?.let { "${it.start}:${it.endExclusive}:${it.name}" }
    if (tokenKey != dismissedToken && dismissedToken != null) dismissedToken = null
    val matches = token?.let { matchingTags(tags, it.name).take(6) }.orEmpty()
    val showCreate = token?.let { canCreateTag(it.name, tags) } == true
    val expanded = focused && token != null && tokenKey != dismissedToken && (matches.isNotEmpty() || showCreate)

    Column(modifier) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { next -> fieldValue = next; onValueChange(next.text) },
            label = { Text(label ?: stringResource(R.string.transaction_notes)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    val cursor = fieldValue.selection.end
                    val text = fieldValue.text.replaceRange(cursor, cursor, "#")
                    fieldValue = TextFieldValue(text, TextRange(cursor + 1))
                    onValueChange(text)
                    focusRequester.requestFocus()
                }) { Text("#", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { dismissedToken = tokenKey },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            matches.forEach { tag ->
                DropdownMenuItem(
                    leadingIcon = { Box(Modifier.size(14.dp).background(parseTagSuggestionColor(tag.color), CircleShape)) },
                    text = { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("#${tag.tag}", modifier = Modifier.weight(1f))
                        tag.description?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    } },
                    onClick = {
                        val current = activeTagToken(fieldValue.text, fieldValue.selection.end) ?: return@DropdownMenuItem
                        val (text, cursor) = replaceActiveTag(fieldValue.text, current, tag.tag)
                        fieldValue = TextFieldValue(text, TextRange(cursor)); onValueChange(text)
                    },
                )
            }
            if (showCreate && token != null) DropdownMenuItem(text = {
                Text(stringResource(R.string.transaction_create_tag, token.name))
            }, onClick = {
                val current = activeTagToken(fieldValue.text, fieldValue.selection.end) ?: return@DropdownMenuItem
                val created = onCreateTag(current.name) ?: return@DropdownMenuItem
                val (text, cursor) = replaceActiveTag(fieldValue.text, current, created.tag)
                fieldValue = TextFieldValue(text, TextRange(cursor)); onValueChange(text)
            })
        }
    }
}

private fun parseTagSuggestionColor(value: String?): Color = runCatching {
    Color(android.graphics.Color.parseColor(value ?: "#808080"))
}.getOrDefault(Color.Gray)
