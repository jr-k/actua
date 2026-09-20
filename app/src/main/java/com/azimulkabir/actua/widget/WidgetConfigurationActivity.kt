package com.azimulkabir.actua.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.preferences.FavoritePreferences
import com.azimulkabir.actua.ui.theme.ActuaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WidgetChoice(val id: String, val name: String, val subtitle: String)

class WidgetConfigurationActivity : AppCompatActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
        val kind = kindForWidget(widgetId) ?: run {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            ActuaTheme {
                WidgetConfigurationScreen(
                    kind = kind,
                    widgetId = widgetId,
                    onCancel = ::finish,
                    onSave = { selected -> save(kind, selected) },
                    loadChoices = { loadChoices(kind) },
                )
            }
        }
    }

    private fun kindForWidget(widgetId: Int): WidgetKind? {
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider ?: return null
        return when (provider) {
            ComponentName(this, FavouriteCategoriesWidgetProvider::class.java) -> WidgetKind.Categories
            ComponentName(this, AccountBalancesWidgetProvider::class.java) -> WidgetKind.Accounts
            else -> null
        }
    }

    private suspend fun loadChoices(kind: WidgetKind): List<WidgetChoice> = withContext(Dispatchers.IO) {
        val repository = ActuaRepository(this@WidgetConfigurationActivity)
        try {
            when (kind) {
                WidgetKind.Categories -> repository.budgetGroups()
                    .filterNot { it.hidden || it.isIncome }
                    .flatMap { group -> group.categories.filterNot { it.hidden }.mapNotNull { category ->
                        category.id?.let { WidgetChoice(it, category.name, group.name) }
                    } }
                WidgetKind.Accounts -> repository.accounts().filterNot { it.closed }.map {
                    WidgetChoice(
                        it.id,
                        it.name,
                        if (it.offBudget) getString(R.string.widget_off_budget) else it.type,
                    )
                }
            }
        } finally {
            repository.close()
        }
    }

    private fun save(kind: WidgetKind, selected: Set<String>) {
        if (kind == WidgetKind.Categories) {
            val budgetId = ActiveBudgetStore(this).budgetId ?: "no-budget"
            FavoritePreferences(this).replace(budgetId, FavoritePreferences.Type.CATEGORY, selected)
        }
        WidgetPreferences(this).save(kind, widgetId, selected)
        lifecycleScope.launch(Dispatchers.IO) {
            val manager = AppWidgetManager.getInstance(this@WidgetConfigurationActivity)
            val provider = when (kind) {
                WidgetKind.Categories -> FavouriteCategoriesWidgetProvider()
                WidgetKind.Accounts -> AccountBalancesWidgetProvider()
            }
            WidgetUpdater.update(this@WidgetConfigurationActivity, manager, widgetId, provider)
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigurationScreen(
    kind: WidgetKind,
    widgetId: Int,
    onCancel: () -> Unit,
    onSave: (Set<String>) -> Unit,
    loadChoices: suspend () -> List<WidgetChoice>,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var choices by remember { mutableStateOf<List<WidgetChoice>?>(null) }
    var selected by remember(kind, widgetId) {
        val existing = if (kind == WidgetKind.Categories) {
            val budgetId = ActiveBudgetStore(context).budgetId ?: "no-budget"
            FavoritePreferences(context).ids(budgetId, FavoritePreferences.Type.CATEGORY)
        } else {
            WidgetPreferences(context).selected(kind, widgetId)
        }
        mutableStateOf(existing)
    }
    LaunchedEffect(kind) { choices = loadChoices() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(
                        if (kind == WidgetKind.Categories) R.string.widget_favourite_categories
                        else R.string.widget_account_balances,
                    ))
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.widget_action_cancel))
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { onSave(selected) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(stringResource(R.string.widget_add)) }
        },
    ) { padding ->
        val loaded = choices
        if (loaded == null) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Text(
                        stringResource(
                            if (kind == WidgetKind.Categories) R.string.widget_choose_categories
                            else R.string.widget_choose_accounts,
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (loaded.isEmpty()) {
                    item {
                        Text(stringResource(R.string.widget_no_available_items), Modifier.padding(20.dp))
                    }
                }
                items(loaded, key = { it.id }) { choice ->
                    val checked = choice.id in selected
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            enabled = checked || selected.size < 4,
                            onCheckedChange = { enabled ->
                                selected = if (enabled) selected + choice.id else selected - choice.id
                            },
                        )
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(
                                choice.name.ifBlank { stringResource(R.string.common_unknown) },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                choice.subtitle.ifBlank { stringResource(R.string.common_unknown) },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
