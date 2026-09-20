package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import com.azimulkabir.actua.R
import org.json.JSONObject

/** Owns creation, compatibility repair, and reset of Actua's local-only demonstration budget. */
object DemoBudgetManager {
    const val BUDGET_ID = "demo"

    fun isDemoBudget(budgetId: String?): Boolean = budgetId == BUDGET_ID

    /**
     * Beta.6 could create a demo with a schedules table that lacked sort_order,
     * while the schedule read model queried that column during app startup. Repair
     * only that incompatible demo shape so existing fixed demo edits are preserved.
     */
    fun repairIfNeeded(files: BudgetFileManager) {
        val databaseFile = files.databaseFile(BUDGET_ID)
        if (!databaseFile.isFile) return
        val compatible = runCatching {
            SQLiteDatabase.openDatabase(
                databaseFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { database ->
                database.rawQuery("PRAGMA table_info(schedules)", null).use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    var hasSortOrder = false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == "sort_order") {
                            hasSortOrder = true
                            break
                        }
                    }
                    hasSortOrder
                }
            }
        }.getOrDefault(false)
        if (!compatible) recreate(files)
    }

    /**
     * Recreates the demo from the current blank-budget schema, then layers curated
     * sample data on top. The metadata intentionally contains no cloud identity,
     * so the normal sync runner cannot upload or merge this budget with a server.
     */
    fun recreate(files: BudgetFileManager): BudgetMetadata {
        val labels = labels(files)
        val directory = files.budgetDirectory(BUDGET_ID)
        if (directory.exists()) {
            check(directory.deleteRecursively()) { "Unable to reset the demo budget" }
        }
        check(directory.mkdirs()) { "Unable to create the demo budget directory" }

        try {
            BlankBudgetFactory.create(files.databaseFile(BUDGET_ID))
            SQLiteDatabase.openDatabase(
                files.databaseFile(BUDGET_ID).path,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { database ->
                DemoBudgetSeeder.seed(database, labels)
                // Actual resolves transaction/schedule payees through payee_mapping.
                database.execSQL(
                    "INSERT OR REPLACE INTO payee_mapping(id,targetId) SELECT id,id FROM payees WHERE tombstone=0",
                )
                // Keep the demo card preference in the same shape as normal Actua writes.
                database.execSQL(
                    "UPDATE preferences SET value=? WHERE id=?",
                    arrayOf(
                        JSONObject()
                            .put("statementDay", 20)
                            .put("dueOffsetDays", 15)
                            .put("limit", 500000)
                            .toString(),
                        "actuali:credit_card:demo-account-credit",
                    ),
                )
            }

            val metadataJson = JSONObject()
                .put("id", BUDGET_ID)
                .put("budgetName", labels.budgetName)
                .put("demo", true)
            files.metadataFile(BUDGET_ID).writeText(metadataJson.toString())
            return BudgetMetadata.fromJson(metadataJson)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }

    private fun labels(files: BudgetFileManager) = DemoBudgetLabels(
        budgetName = files.localizedString(R.string.demo_budget_name),
        checkingAccount = files.localizedString(R.string.demo_account_checking),
        savingsAccount = files.localizedString(R.string.demo_account_savings),
        creditAccount = files.localizedString(R.string.demo_account_credit),
        investmentAccount = files.localizedString(R.string.demo_account_investment),
        incomeGroup = files.localizedString(R.string.demo_group_income),
        essentialsGroup = files.localizedString(R.string.demo_group_essentials),
        lifestyleGroup = files.localizedString(R.string.demo_group_lifestyle),
        goalsGroup = files.localizedString(R.string.demo_group_goals),
        salaryCategory = files.localizedString(R.string.demo_category_salary),
        startingBalancesCategory = files.localizedString(R.string.demo_category_starting_balances),
        rentCategory = files.localizedString(R.string.demo_category_rent),
        groceriesCategory = files.localizedString(R.string.demo_category_groceries),
        utilitiesCategory = files.localizedString(R.string.demo_category_utilities),
        transportCategory = files.localizedString(R.string.demo_category_transport),
        diningOutCategory = files.localizedString(R.string.demo_category_dining_out),
        entertainmentCategory = files.localizedString(R.string.demo_category_entertainment),
        emergencyFundCategory = files.localizedString(R.string.demo_category_emergency_fund),
        vacationCategory = files.localizedString(R.string.demo_category_vacation),
        employerPayee = files.localizedString(R.string.demo_payee_employer),
        landlordPayee = files.localizedString(R.string.demo_payee_landlord),
        supermarketPayee = files.localizedString(R.string.demo_payee_supermarket),
        utilitiesPayee = files.localizedString(R.string.demo_payee_utilities),
        fuelPayee = files.localizedString(R.string.demo_payee_fuel),
        restaurantPayee = files.localizedString(R.string.demo_payee_restaurant),
        streamingPayee = files.localizedString(R.string.demo_payee_streaming),
        cardPaymentPayee = files.localizedString(R.string.demo_payee_card_payment),
        groceriesNote = files.localizedString(R.string.demo_note_groceries),
        checkingNote = files.localizedString(R.string.demo_note_checking),
        salarySchedule = files.localizedString(R.string.demo_schedule_salary),
        rentSchedule = files.localizedString(R.string.demo_schedule_rent),
        streamingSchedule = files.localizedString(R.string.demo_schedule_streaming),
        cardPaymentSchedule = files.localizedString(R.string.demo_schedule_card_payment),
        overviewReport = files.localizedString(R.string.demo_report_overview),
        welcomeReport = files.localizedString(R.string.demo_report_welcome),
        thisMonthReport = files.localizedString(R.string.demo_report_this_month),
    )
}
