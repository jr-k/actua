package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.azimulkabir.actua.R
import com.azimulkabir.actua.ui.theme.AmountTypography
import com.azimulkabir.actua.ui.theme.Spacing

/**
 * Shared screen-title header used by top-level and drilled-in screens alike,
 * so the app bar spacing/typography/back-button treatment is one thing
 * rather than a slightly different Row on every screen.
 */
@Composable
fun ActuaScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) Spacing.xs else Spacing.screenHorizontal,
                end = Spacing.sm,
                top = Spacing.sm,
                bottom = Spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack != null) Spacing.xs else Spacing.none),
        )
        actions()
    }
}

/**
 * Shared section-heading style for grouped content (settings pages, budget
 * groups, manage screens, etc.) so equivalent groupings look the same.
 */
@Composable
fun ActuaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    showDividerAbove: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDividerAbove) HorizontalDivider()
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = Spacing.md,
                bottom = Spacing.xs,
            ),
        )
    }
}

/**
 * Shared list-row layout for transactions, accounts, categories, settings
 * and Manage screens: a fixed horizontal margin, an optional leading area,
 * a title/subtitle column and a trailing area, so row height, insets and
 * click targets stay consistent wherever this appears.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActuaListRow(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val clickable = onClick != null || onLongClick != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let {
                if (clickable) {
                    it.combinedClickable(enabled = enabled, onClick = onClick ?: {}, onLongClick = onLongClick)
                } else it
            }
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(modifier = Modifier.padding(end = Spacing.md)) { leading() }
        }
        Column(modifier = Modifier.weight(1f)) {
            title()
            subtitle?.invoke()
        }
        trailing?.invoke(this)
    }
}

/**
 * Shared title for bottom sheets and similar transient surfaces (action
 * sheets, pickers), so every sheet's heading uses the same type/padding
 * instead of a one-off Text() at each call site.
 */
@Composable
fun ActuaSheetTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
    )
}

/**
 * Consistent monetary text: tabular figures so amounts don't jitter in
 * lists, and the same positive/negative/zero coloring used everywhere an
 * amount is shown.
 */
@Composable
fun MonetaryText(
    amountCents: Long,
    hideDecimalPlaces: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = AmountTypography.rowAmount,
    color: Color? = null,
) {
    val resolvedColor = color ?: when {
        amountCents > 0 -> MaterialTheme.colorScheme.primary
        amountCents < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(formatMoneyCents(amountCents, hideDecimalPlaces), style = style, color = resolvedColor, modifier = modifier)
}
