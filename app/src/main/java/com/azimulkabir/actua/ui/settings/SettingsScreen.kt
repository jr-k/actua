package com.azimulkabir.actua.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.BuildConfig
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.budget.ActiveTagRepository
import com.azimulkabir.actua.data.location.ForegroundLocationPermission
import com.azimulkabir.actua.data.preferences.LocationPreferences
import com.azimulkabir.actua.ui.components.ActuaListRow
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.ActuaSectionHeader

internal enum class SettingsPage(@StringRes val titleRes: Int, val depth: Int) {
    Manage(R.string.settings_page_manage, 0),
    Tags(R.string.settings_page_tags, 1),
    General(R.string.settings_page_settings, 1),
    Transactions(R.string.settings_page_transactions_accounts, 2),
    Display(R.string.settings_page_display, 2),
    Privacy(R.string.settings_page_privacy, 2),
    About(R.string.settings_page_about, 2),
}

internal fun isForwardSettingsNavigation(from: SettingsPage, to: SettingsPage): Boolean =
    to.depth > from.depth

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onConnectionClick: () -> Unit = {},
    hideDecimalPlaces: Boolean = true,
    onHideDecimalPlacesChange: (Boolean) -> Unit = {},
    showNotes: Boolean = true,
    onShowNotesChange: (Boolean) -> Unit = {},
    currencyCode: String = "",
    onCurrencyCodeChange: (String) -> Unit = {},
    currencySymbolOnly: Boolean = false,
    onCurrencySymbolOnlyChange: (Boolean) -> Unit = {},
    languageTag: String = "",
    onLanguageChange: (String) -> Unit = {},
    dateFormat: String = "System default",
    onDateFormatChange: (String) -> Unit = {},
    numberFormat: String = "System default",
    onNumberFormatChange: (String) -> Unit = {},
    hideBalances: Boolean = false,
    onHideBalancesChange: (Boolean) -> Unit = {},
    appearance: String = "System",
    onAppearanceChange: (String) -> Unit = {},
    useDynamicColor: Boolean = false,
    onUseDynamicColorChange: (Boolean) -> Unit = {},
    startPage: String = "Budget",
    onStartPageChange: (String) -> Unit = {},
    accountOptions: List<String> = emptyList(),
    defaultAccount: String? = null,
    onDefaultAccountChange: (String?) -> Unit = {},
    groupTransactionsByDate: Boolean = true,
    onGroupTransactionsByDateChange: (Boolean) -> Unit = {},
    showAccountsMonthlySummary: Boolean = true,
    onShowAccountsMonthlySummaryChange: (Boolean) -> Unit = {},
    onCreditCardsClick: () -> Unit = {},
    onBillsCalendarClick: () -> Unit = {},
    onRulesClick: () -> Unit = {},
    onSchedulesClick: () -> Unit = {},
    onImportTransactionsClick: () -> Unit = {},
    onPayeeLocationsClick: () -> Unit = {},
    onReportsClick: () -> Unit = {},
    onCustomizeHomeClick: () -> Unit = {},
    conventionalAmountEntry: Boolean = true,
    onConventionalAmountEntryChange: (Boolean) -> Unit = {},
    showBottomNavigationLabels: Boolean = true,
    onShowBottomNavigationLabelsChange: (Boolean) -> Unit = {},
    showCurrentBalanceSummary: Boolean = true,
    onShowCurrentBalanceSummaryChange: (Boolean) -> Unit = {},
    returnToRootRequest: Int = 0,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val locationPreferences = remember { LocationPreferences(context) }
    val tagRepository = remember { ActiveTagRepository(context) }
    var tagVersion by remember { mutableStateOf(0L) }
    val managedTags = remember(tagVersion) { tagRepository.tags(tagVersion) }
    val tagCapabilities = remember(tagVersion) { tagRepository.capabilities(tagVersion) }
    val initialLocationPermissionGranted = remember {
        ForegroundLocationPermission.isGranted(context)
    }
    var locationPermissionGranted by remember {
        mutableStateOf(initialLocationPermissionGranted)
    }
    var recordPayeeLocations by remember {
        mutableStateOf(
            locationPreferences.recordPayeeLocations && initialLocationPermissionGranted,
        )
    }
    LaunchedEffect(initialLocationPermissionGranted) {
        if (!initialLocationPermissionGranted && locationPreferences.recordPayeeLocations) {
            locationPreferences.recordPayeeLocations = false
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants.values.any { it } || ForegroundLocationPermission.isGranted(context)
        locationPermissionGranted = granted
        locationPreferences.recordPayeeLocations = granted
        recordPayeeLocations = granted
    }

    fun setRecordPayeeLocations(enabled: Boolean) {
        if (!enabled) {
            locationPreferences.recordPayeeLocations = false
            recordPayeeLocations = false
            return
        }
        if (ForegroundLocationPermission.isGranted(context)) {
            locationPermissionGranted = true
            locationPreferences.recordPayeeLocations = true
            recordPayeeLocations = true
        } else {
            locationPermissionLauncher.launch(ForegroundLocationPermission.permissions)
        }
    }

    var page by rememberSaveable { mutableStateOf(SettingsPage.Manage) }
    val scrollState = rememberScrollState()
    fun parentPage(current: SettingsPage): SettingsPage = when (current) {
        SettingsPage.Tags -> SettingsPage.Manage
        SettingsPage.Transactions, SettingsPage.Display, SettingsPage.Privacy, SettingsPage.About ->
            SettingsPage.General
        SettingsPage.General -> SettingsPage.Manage
        SettingsPage.Manage -> SettingsPage.Manage
    }
    fun navigateBack() {
        page = parentPage(page)
    }
    LaunchedEffect(returnToRootRequest) {
        if (returnToRootRequest > 0) {
            if (page != SettingsPage.Manage) page = SettingsPage.Manage else scrollState.animateScrollTo(0)
        }
    }
    BackHandler(enabled = page != SettingsPage.Manage, onBack = ::navigateBack)
    fun openFullScreen(action: () -> Unit) {
        page = SettingsPage.Manage
        action()
    }
    AnimatedContent(
        targetState = page,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val opening = isForwardSettingsNavigation(initialState, targetState)
            if (opening) {
                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 5 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -it / 10 })
            } else {
                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { -it / 5 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 10 })
            // Settings pages may have very different scrollable heights. Preserve directional
            // motion without animating the layout bounds of two complete page trees.
            }.using(SizeTransform(sizeAnimationSpec = { _, _ -> snap() }, clip = false))
        },
        label = "Settings navigation motion",
    ) { shownPage ->
        if (shownPage == SettingsPage.Tags) {
            ManageTagsScreen(
                tags = managedTags,
                hiddenSupported = tagCapabilities.hidden,
                onBack = ::navigateBack,
                onCreate = { name, color, description, hidden ->
                    runCatching { tagRepository.create(name, color, description, hidden) }
                        .onSuccess { tagVersion += 1 }
                },
                onUpdate = { tag, name, color, description, hidden ->
                    runCatching { tagRepository.update(tag, name, color, description, hidden) }
                        .onSuccess { if (it) tagVersion += 1 }
                },
                onDelete = { tag ->
                    runCatching { tagRepository.delete(tag) }
                        .onSuccess { if (it) tagVersion += 1 }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            ActuaScreenHeader(
                title = stringResource(shownPage.titleRes),
                onBack = if (shownPage != SettingsPage.Manage) ::navigateBack else null,
            ) {
                if (shownPage == SettingsPage.Manage) {
                    IconButton(onClick = { page = SettingsPage.General }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_open_settings),
                        )
                    }
                }
            }
            when (shownPage) {
                SettingsPage.Manage -> {
                    SettingsSection(stringResource(R.string.settings_section_insights))
                    SettingsRow(
                        stringResource(R.string.settings_reports),
                        stringResource(R.string.settings_reports_description),
                        true,
                    ) {
                        openFullScreen(onReportsClick)
                    }
                    SettingsSection(stringResource(R.string.settings_section_automation))
                    SettingsRow(
                        stringResource(R.string.settings_bills_calendar),
                        stringResource(R.string.settings_bills_calendar_description),
                        true,
                    ) {
                        openFullScreen(onBillsCalendarClick)
                    }
                    SettingsRow(
                        stringResource(R.string.settings_scheduled_transactions),
                        stringResource(R.string.settings_scheduled_transactions_description),
                        true,
                    ) {
                        openFullScreen(onSchedulesClick)
                    }
                    SettingsRow(
                        stringResource(R.string.settings_rules),
                        stringResource(R.string.settings_rules_description),
                        true,
                    ) {
                        openFullScreen(onRulesClick)
                    }
                    SettingsSection(stringResource(R.string.settings_section_transactions_data))
                    SettingsRow(
                        stringResource(R.string.settings_tags),
                        stringResource(R.string.settings_tags_description),
                        true,
                    ) {
                        page = SettingsPage.Tags
                    }
                    SettingsRow(
                        stringResource(R.string.settings_import_transactions),
                        stringResource(R.string.settings_import_transactions_description),
                        true,
                    ) {
                        openFullScreen(onImportTransactionsClick)
                    }
                    SettingsRow(
                        stringResource(R.string.settings_connection_data),
                        stringResource(R.string.settings_connection_data_description),
                        true,
                    ) {
                        openFullScreen(onConnectionClick)
                    }
                    SettingsSection(stringResource(R.string.settings_section_financial_setup))
                    SettingsRow(
                        stringResource(R.string.settings_credit_cards),
                        stringResource(R.string.settings_credit_cards_description),
                        true,
                    ) {
                        openFullScreen(onCreditCardsClick)
                    }
                }
                SettingsPage.General -> {
                    SettingsSection(stringResource(R.string.settings_section_preferences))
                    SettingsRow(
                        stringResource(R.string.settings_home),
                        stringResource(R.string.settings_home_description),
                        true,
                    ) {
                        openFullScreen(onCustomizeHomeClick)
                    }
                    SettingsRow(
                        stringResource(R.string.settings_page_transactions_accounts),
                        stringResource(R.string.settings_transactions_accounts_description),
                        true,
                    ) {
                        page = SettingsPage.Transactions
                    }
                    SettingsRow(
                        stringResource(R.string.settings_page_display),
                        stringResource(R.string.settings_display_description),
                        true,
                    ) {
                        page = SettingsPage.Display
                    }
                    SettingsRow(
                        stringResource(R.string.settings_page_privacy),
                        stringResource(R.string.settings_privacy_description),
                        true,
                    ) {
                        page = SettingsPage.Privacy
                    }
                    SettingsSection(stringResource(R.string.settings_section_about))
                    SettingsRow(
                        stringResource(R.string.settings_about_actua),
                        stringResource(R.string.settings_about_actua_description),
                        true,
                    ) {
                        page = SettingsPage.About
                    }
                }
                SettingsPage.Transactions -> {
                    SettingsChoice(
                        stringResource(R.string.settings_default_account),
                        defaultAccount ?: DEFAULT_ACCOUNT_NONE,
                        listOf(
                            SettingsChoiceOption(
                                DEFAULT_ACCOUNT_NONE,
                                stringResource(R.string.settings_none),
                            ),
                        ) + accountOptions.map { SettingsChoiceOption(it, it) },
                    ) {
                        onDefaultAccountChange(it.takeUnless { value -> value == DEFAULT_ACCOUNT_NONE })
                    }
                    SettingsToggle(
                        stringResource(R.string.settings_group_transactions),
                        stringResource(R.string.settings_group_transactions_description),
                        groupTransactionsByDate,
                        onGroupTransactionsByDateChange,
                    )
                    SettingsToggle(
                        stringResource(R.string.settings_conventional_amount_entry),
                        stringResource(R.string.settings_conventional_amount_entry_description),
                        conventionalAmountEntry,
                        onConventionalAmountEntryChange,
                    )
                    SettingsToggle(
                        stringResource(R.string.settings_account_monthly_summary),
                        stringResource(R.string.settings_account_monthly_summary_description),
                        showAccountsMonthlySummary,
                        onShowAccountsMonthlySummaryChange,
                    )
                    SettingsToggle(
                        stringResource(R.string.settings_current_balance_summary),
                        stringResource(R.string.settings_current_balance_summary_description),
                        showCurrentBalanceSummary,
                        onShowCurrentBalanceSummaryChange,
                    )
                    SettingsRow(
                        stringResource(R.string.settings_credit_cards),
                        stringResource(R.string.settings_credit_cards_description),
                        true,
                    ) {
                        openFullScreen(onCreditCardsClick)
                    }
                }
                SettingsPage.Display -> {
                    SettingsChoice(
                        stringResource(R.string.settings_language),
                        languageTag,
                        listOf(
                            SettingsChoiceOption("", stringResource(R.string.settings_language_system)),
                            SettingsChoiceOption("en", stringResource(R.string.settings_language_english)),
                            SettingsChoiceOption("fr", stringResource(R.string.settings_language_french)),
                        ),
                        onLanguageChange,
                    )
                    SettingsChoice(
                        stringResource(R.string.settings_currency),
                        currencyCode,
                        currencyOptions.map {
                            SettingsChoiceOption(
                                it.code,
                                stringResource(it.labelRes),
                            )
                        },
                        onCurrencyCodeChange,
                    )
                    if (currencyCode.isNotBlank()) {
                        SettingsToggle(
                            stringResource(R.string.settings_symbol_only),
                            stringResource(R.string.settings_symbol_only_description),
                            currencySymbolOnly,
                            onCurrencySymbolOnlyChange,
                        )
                    }
                    SettingsChoice(
                        stringResource(R.string.settings_date_format),
                        dateFormat,
                        listOf(
                            SettingsChoiceOption(
                                "System default",
                                stringResource(R.string.settings_system_default),
                            ),
                            SettingsChoiceOption(
                                "DD/MM/YYYY",
                                stringResource(R.string.settings_date_dd_mm_yyyy),
                            ),
                            SettingsChoiceOption(
                                "MM/DD/YYYY",
                                stringResource(R.string.settings_date_mm_dd_yyyy),
                            ),
                            SettingsChoiceOption(
                                "YYYY-MM-DD",
                                stringResource(R.string.settings_date_yyyy_mm_dd),
                            ),
                        ),
                        onDateFormatChange,
                    )
                    Text(
                        stringResource(R.string.settings_preview, datePreview(dateFormat)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    SettingsChoice(
                        stringResource(R.string.settings_number_format),
                        numberFormat,
                        listOf(
                            SettingsChoiceOption(
                                "System default",
                                stringResource(R.string.settings_system_default),
                            ),
                            SettingsChoiceOption(
                                "1,234.56",
                                stringResource(R.string.settings_number_comma_decimal),
                            ),
                            SettingsChoiceOption(
                                "1.234,56",
                                stringResource(R.string.settings_number_period_decimal_comma),
                            ),
                            SettingsChoiceOption(
                                "1 234,56",
                                stringResource(R.string.settings_number_space_decimal_comma),
                            ),
                            SettingsChoiceOption(
                                "1234.56",
                                stringResource(R.string.settings_number_plain_decimal),
                            ),
                            SettingsChoiceOption(
                                "1,23,456.78",
                                stringResource(R.string.settings_number_indian_grouping),
                            ),
                        ),
                        onNumberFormatChange,
                    )
                    Text(
                        stringResource(R.string.settings_preview, numberPreview(numberFormat)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    SettingsChoice(
                        stringResource(R.string.settings_appearance),
                        appearance,
                        listOf(
                            SettingsChoiceOption(
                                "System",
                                stringResource(R.string.settings_appearance_system),
                            ),
                            SettingsChoiceOption(
                                "Light",
                                stringResource(R.string.settings_appearance_light),
                            ),
                            SettingsChoiceOption(
                                "Dark",
                                stringResource(R.string.settings_appearance_dark),
                            ),
                        ),
                        onAppearanceChange,
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        SettingsToggle(
                            stringResource(R.string.settings_material_you_colors),
                            stringResource(R.string.settings_material_you_colors_description),
                            useDynamicColor,
                            onUseDynamicColorChange,
                        )
                    }
                    SettingsChoice(
                        stringResource(R.string.settings_start_page),
                        startPage,
                        listOf(
                            SettingsChoiceOption(
                                "Home",
                                stringResource(R.string.settings_start_page_home),
                            ),
                            SettingsChoiceOption(
                                "Budget",
                                stringResource(R.string.settings_start_page_budget),
                            ),
                            SettingsChoiceOption(
                                "Transactions",
                                stringResource(R.string.settings_start_page_transactions),
                            ),
                            SettingsChoiceOption(
                                "Accounts",
                                stringResource(R.string.settings_start_page_accounts),
                            ),
                            SettingsChoiceOption(
                                "Manage",
                                stringResource(R.string.settings_start_page_manage),
                            ),
                        ),
                        onStartPageChange,
                    )
                    SettingsChoice(
                        stringResource(R.string.settings_bottom_navigation_labels),
                        if (showBottomNavigationLabels) "Icons and names" else "Icons only",
                        listOf(
                            SettingsChoiceOption(
                                "Icons and names",
                                stringResource(R.string.settings_navigation_icons_names),
                            ),
                            SettingsChoiceOption(
                                "Icons only",
                                stringResource(R.string.settings_navigation_icons_only),
                            ),
                        ),
                    ) { onShowBottomNavigationLabelsChange(it == "Icons and names") }
                    SettingsToggle(
                        stringResource(R.string.settings_hide_decimal_places),
                        stringResource(R.string.settings_hide_decimal_places_description),
                        hideDecimalPlaces,
                        onHideDecimalPlacesChange,
                    )
                    SettingsToggle(
                        stringResource(R.string.settings_notes),
                        stringResource(R.string.settings_notes_description),
                        showNotes,
                        onShowNotesChange,
                    )
                }
                SettingsPage.Privacy -> {
                    SettingsToggle(
                        stringResource(R.string.settings_hide_balances),
                        stringResource(R.string.settings_hide_balances_description),
                        hideBalances,
                        onHideBalancesChange,
                    )
                    SettingsSection(stringResource(R.string.settings_section_location_payees))
                    SettingsToggle(
                        stringResource(R.string.settings_record_payee_locations),
                        if (recordPayeeLocations && locationPermissionGranted) {
                            stringResource(
                                R.string.settings_record_payee_locations_enabled_description,
                            )
                        } else {
                            stringResource(
                                R.string.settings_record_payee_locations_disabled_description,
                            )
                        },
                        recordPayeeLocations,
                        ::setRecordPayeeLocations,
                    )
                    SettingsRow(
                        stringResource(R.string.settings_payee_locations),
                        stringResource(R.string.settings_payee_locations_description),
                        true,
                    ) { openFullScreen(onPayeeLocationsClick) }
                    Text(
                        if (locationPermissionGranted) {
                            stringResource(R.string.settings_location_permission_allowed)
                        } else {
                            stringResource(R.string.settings_location_permission_denied)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                SettingsPage.About -> {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.app_name)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.settings_about_summary,
                                    BuildConfig.VERSION_NAME,
                                ),
                            )
                        },
                    )
                    SettingsSection(stringResource(R.string.settings_section_project))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_actua_github)) },
                        supportingContent = {
                            Text(stringResource(R.string.settings_actua_github_url))
                        },
                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/azimul-kabir/actua")
                        },
                    )
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.settings_independent_project))
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.settings_independent_project_description,
                                ),
                            )
                        },
                    )
                    SettingsSection(stringResource(R.string.settings_section_credits))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_actuali_ios)) },
                        supportingContent = {
                            Text(stringResource(R.string.settings_actuali_ios_description))
                        },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_actual_budget)) },
                        supportingContent = {
                            Text(stringResource(R.string.settings_actual_budget_description))
                        },
                    )
                    SettingsSection(stringResource(R.string.settings_section_compatibility))
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.settings_android_requirement))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.settings_server_requirement))
                        },
                    )
                    SettingsSection(stringResource(R.string.settings_section_license))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_mit_license)) },
                        supportingContent = {
                            Text(stringResource(R.string.settings_license_description))
                        },
                    )
                }
                SettingsPage.Tags -> Unit
            }
        }
    }
}

private fun datePreview(format: String): String = when (format) {
    "DD/MM/YYYY" -> "31/12/2026"
    "MM/DD/YYYY" -> "12/31/2026"
    "YYYY-MM-DD" -> "2026-12-31"
    else -> java.time.LocalDate.of(2026, 12, 31)
        .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
}

private fun numberPreview(format: String): String = when (format) {
    "1,234.56" -> "1,234.56"
    "1.234,56" -> "1.234,56"
    "1 234,56" -> "1 234,56"
    "1234.56" -> "1234.56"
    "1,23,456.78" -> "1,23,456.78"
    else -> java.text.NumberFormat.getNumberInstance().format(1234.56)
}

private data class CurrencyOption(
    @StringRes val labelRes: Int,
    val code: String,
)

private val currencyOptions = listOf(
    CurrencyOption(R.string.settings_none, ""),
    CurrencyOption(R.string.settings_currency_bdt, "BDT"),
    CurrencyOption(R.string.settings_currency_usd, "USD"),
    CurrencyOption(R.string.settings_currency_eur, "EUR"),
    CurrencyOption(R.string.settings_currency_gbp, "GBP"),
    CurrencyOption(R.string.settings_currency_cad, "CAD"),
    CurrencyOption(R.string.settings_currency_aud, "AUD"),
    CurrencyOption(R.string.settings_currency_jpy, "JPY"),
    CurrencyOption(R.string.settings_currency_inr, "INR"),
    CurrencyOption(R.string.settings_currency_cny, "CNY"),
    CurrencyOption(R.string.settings_currency_sgd, "SGD"),
    CurrencyOption(R.string.settings_currency_aed, "AED"),
    CurrencyOption(R.string.settings_currency_sar, "SAR"),
)

private const val DEFAULT_ACCOUNT_NONE = "\u0000"

private data class SettingsChoiceOption(
    val value: String,
    val label: String,
)

@Composable
private fun SettingsChoice(
    label: String,
    value: String,
    options: List<SettingsChoiceOption>,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == value }?.label ?: value
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) { Text(selectedLabel) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.distinctBy { it.value }.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = {
                            expanded = false
                            onChange(option.value)
                        })
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingsToggle(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ActuaListRow(
        title = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        subtitle = { Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
private fun SettingsSection(label: String) {
    ActuaSectionHeader(label)
}

@Composable
private fun SettingsRow(label: String, detail: String, enabled: Boolean = false, onClick: () -> Unit = {}) {
    ActuaListRow(
        title = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        subtitle = {
            Text(
                if (enabled) detail else {
                    stringResource(R.string.settings_coming_with_backend, detail)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = { if (enabled) Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}
