package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.navigationBarsPadding
import com.azimulkabir.actua.R

@Composable
fun CalculatorAmountSheet(
    title: String,
    initialCents: Long,
    conventionalAmountEntry: Boolean = false,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit,
    onExpressionChange: (String) -> Unit = {},
) {
    val calculator = remember(conventionalAmountEntry) {
        CalculatorAmountState(initialCents, conventionalAmountEntry = conventionalAmountEntry)
    }
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
            CompactCalculatorPad(
                calculator = calculator,
                conventionalAmountEntry = conventionalAmountEntry,
                showDisplay = false,
                onValueChange = onApply,
                onExpressionChange = onExpressionChange,
                onDone = { onApply(calculator.finish()); onDismiss() },
            )
        }
    }
}

@Composable
fun CompactCalculatorPad(
    calculator: CalculatorAmountState,
    conventionalAmountEntry: Boolean = false,
    allowSign: Boolean = false,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    moveMoneyMode: Boolean = false,
    displayLabel: String? = null,
    showDisplay: Boolean = true,
    onValueChange: (Long) -> Unit = {},
    onExpressionChange: (String) -> Unit = {},
    canFinish: (Long) -> Boolean = { true },
    onClose: (() -> Unit)? = null,
    onDone: () -> Unit,
) {
    var revision by remember { mutableIntStateOf(0) }
    fun press(key: String) {
        when (key) {
            "C" -> calculator.clear()
            "±" -> calculator.toggleSign()
            "⌫" -> calculator.backspace()
            "00" -> { calculator.digit(0); calculator.digit(0) }
            "." -> calculator.decimalPoint()
            "+" -> calculator.operator(CalculatorAmountState.Operator.ADD)
            "−" -> calculator.operator(CalculatorAmountState.Operator.SUBTRACT)
            "×" -> calculator.operator(CalculatorAmountState.Operator.MULTIPLY)
            "÷" -> calculator.operator(CalculatorAmountState.Operator.DIVIDE)
            "✓" -> if (canFinish(calculator.cents)) onDone()
            else -> calculator.digit(key.toInt())
        }
        revision++
        if (key != "✓") {
            onValueChange(calculator.cents)
            onExpressionChange(calculator.expressionDisplay)
        }
    }
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf(".", "0", "±", "✓"),
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showDisplay) {
            Row(
                modifier = Modifier.fillMaxWidth().height(42.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onClose?.let {
                    IconButton(onClick = it, modifier = Modifier.height(38.dp).width(38.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.calculator_close))
                    }
                }
                Spacer(Modifier.weight(1f))
                displayLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                }
                Text(
                    calculator.display,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                Box(
                    Modifier.padding(start = 3.dp).height(26.dp).width(2.dp)
                        .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        rows.forEach { keys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                keys.forEachIndexed { columnIndex, key ->
                    CompactCalculatorKey(
                        label = key,
                        operator = key in setOf("÷", "×", "−", "+"),
                        confirm = key == "✓",
                        enabled = key != "✓" || canFinish(calculator.cents),
                        modifier = Modifier.weight(if (columnIndex < 3) 1.1f else 0.9f),
                        onClick = { press(key) },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    @Suppress("UNUSED_EXPRESSION") revision
}

@Composable
private fun CompactCalculatorKey(
    label: String,
    operator: Boolean,
    confirm: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = MaterialTheme.shapes.large,
        color = when {
            confirm -> MaterialTheme.colorScheme.primaryContainer
            operator -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    confirm -> MaterialTheme.colorScheme.onPrimaryContainer
                    operator -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
