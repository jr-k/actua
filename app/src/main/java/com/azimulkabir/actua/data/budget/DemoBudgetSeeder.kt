package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import com.azimulkabir.actua.model.BudgetTarget
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal data class DemoBudgetLabels(
    val budgetName: String,
    val checkingAccount: String,
    val savingsAccount: String,
    val creditAccount: String,
    val investmentAccount: String,
    val incomeGroup: String,
    val essentialsGroup: String,
    val lifestyleGroup: String,
    val goalsGroup: String,
    val salaryCategory: String,
    val startingBalancesCategory: String,
    val rentCategory: String,
    val groceriesCategory: String,
    val utilitiesCategory: String,
    val transportCategory: String,
    val diningOutCategory: String,
    val entertainmentCategory: String,
    val emergencyFundCategory: String,
    val vacationCategory: String,
    val employerPayee: String,
    val landlordPayee: String,
    val supermarketPayee: String,
    val utilitiesPayee: String,
    val fuelPayee: String,
    val restaurantPayee: String,
    val streamingPayee: String,
    val cardPaymentPayee: String,
    val groceriesNote: String,
    val checkingNote: String,
    val salarySchedule: String,
    val rentSchedule: String,
    val streamingSchedule: String,
    val cardPaymentSchedule: String,
    val overviewReport: String,
    val welcomeReport: String,
    val thisMonthReport: String,
)

/** Seeds a local-only budget with realistic data that exercises Actua's main workflows. */
internal object DemoBudgetSeeder {
    const val BUDGET_ID = "demo"

    fun seed(database: SQLiteDatabase, labels: DemoBudgetLabels, now: LocalDate = LocalDate.now()) {
        database.beginTransaction()
        try {
            // BlankBudgetFactory intentionally ships starter categories for newly-created budgets.
            // The demo is curated, so replace only those starter rows while preserving schema/migrations.
            database.execSQL("DELETE FROM category_mapping")
            database.execSQL("DELETE FROM categories")
            database.execSQL("DELETE FROM category_groups")

            val checking = "demo-account-checking"
            val savings = "demo-account-savings"
            val credit = "demo-account-credit"
            val investment = "demo-account-investment"

            insertAccount(database, checking, labels.checkingAccount, "checking", 0, 1.0)
            insertAccount(database, savings, labels.savingsAccount, "savings", 0, 2.0)
            insertAccount(database, credit, labels.creditAccount, "credit", 0, 3.0)
            insertAccount(database, investment, labels.investmentAccount, "investment", 1, 4.0)

            val incomeGroup = group(database, labels.incomeGroup, true, 1.0)
            val essentialsGroup = group(database, labels.essentialsGroup, false, 2.0)
            val lifestyleGroup = group(database, labels.lifestyleGroup, false, 3.0)
            val goalsGroup = group(database, labels.goalsGroup, false, 4.0)

            val salary = category(database, labels.salaryCategory, incomeGroup, true, 1.0)
            val starting = category(database, labels.startingBalancesCategory, incomeGroup, true, 2.0)
            val rent = category(database, labels.rentCategory, essentialsGroup, false, 1.0)
            val groceries = category(database, labels.groceriesCategory, essentialsGroup, false, 2.0)
            val utilities = category(database, labels.utilitiesCategory, essentialsGroup, false, 3.0)
            val transport = category(database, labels.transportCategory, essentialsGroup, false, 4.0)
            val dining = category(database, labels.diningOutCategory, lifestyleGroup, false, 1.0)
            val entertainment = category(database, labels.entertainmentCategory, lifestyleGroup, false, 2.0)
            val emergency = category(database, labels.emergencyFundCategory, goalsGroup, false, 1.0)
            val vacation = category(database, labels.vacationCategory, goalsGroup, false, 2.0)

            setTarget(database, groceries, BudgetTarget(BudgetTarget.Type.FIXED, 60000, startingDate = now.withDayOfMonth(1).toString()))
            setTarget(database, emergency, BudgetTarget(BudgetTarget.Type.FIXED, 25000, startingDate = now.withDayOfMonth(1).toString()))
            setTarget(database, vacation, BudgetTarget(BudgetTarget.Type.BY_DATE, 180000, YearMonth.from(now).plusMonths(6).toString()))
            setTargets(
                database, dining,
                listOf(
                    BudgetTarget(BudgetTarget.Type.LIMIT, 25000, limitPeriod = BudgetTarget.LimitPeriod.MONTHLY),
                    BudgetTarget(BudgetTarget.Type.REFILL),
                ),
            )
            setTarget(
                database, transport,
                BudgetTarget(
                    BudgetTarget.Type.FIXED, 5000, startingDate = now.withDayOfMonth(1).toString(),
                    period = BudgetTarget.Period.WEEK,
                ),
            )
            setTarget(
                database, entertainment,
                BudgetTarget(
                    BudgetTarget.Type.HISTORICAL,
                    historicalMode = BudgetTarget.HistoricalMode.AVERAGE, historicalMonths = 3,
                ),
            )

            val paycheck = payee(database, labels.employerPayee)
            val landlord = payee(database, labels.landlordPayee)
            val supermarket = payee(database, labels.supermarketPayee)
            val utilityCo = payee(database, labels.utilitiesPayee)
            val fuel = payee(database, labels.fuelPayee)
            val restaurant = payee(database, labels.restaurantPayee)
            val streaming = payee(database, labels.streamingPayee)
            val cardPayment = payee(database, labels.cardPaymentPayee)
            val transferPayees = listOf(checking, savings, credit, investment).associateWith { transferPayee(database, it) }

            opening(database, checking, starting, now.minusMonths(6).withDayOfMonth(1), 450000)
            opening(database, savings, starting, now.minusMonths(6).withDayOfMonth(1), 150000)
            opening(database, investment, starting, now.minusMonths(6).withDayOfMonth(1), 300000)

            repeat(6) { monthsAgo ->
                val month = YearMonth.from(now).minusMonths(monthsAgo.toLong())
                val base = month.atDay(1)
                txn(database, checking, salary, paycheck, safeDay(base, 2), 220000, true, true)
                txn(database, checking, rent, landlord, safeDay(base, 3), -80000, true, true)
                txn(database, checking, groceries, supermarket, safeDay(base, 6), -22000 - monthsAgo * 800L, true, monthsAgo > 0)
                txn(database, credit, dining, restaurant, safeDay(base, 10), -6500 - monthsAgo * 300L, true, monthsAgo > 0)
                txn(database, credit, entertainment, streaming, safeDay(base, 12), -1200, true, monthsAgo > 0)
                txn(database, checking, utilities, utilityCo, safeDay(base, 15), -9000 - monthsAgo * 250L, true, monthsAgo > 0)
                txn(database, credit, transport, fuel, safeDay(base, 18), -7500 - monthsAgo * 200L, true, monthsAgo > 0)
                if (monthsAgo > 0) transfer(
                    database,
                    fromAccount = checking,
                    toAccount = credit,
                    fromPayee = requireNotNull(transferPayees[credit]),
                    toPayee = requireNotNull(transferPayees[checking]),
                    date = safeDay(base, 25),
                    amount = 25000,
                    reconciled = true,
                )
            }

            // Current-month examples for uncleared/reconciliation states.
            txn(database, checking, groceries, supermarket, now.minusDays(1), -8200, false, false)
            txn(database, credit, dining, restaurant, now, -3400, false, false)

            // Budget allocations for current and previous months.
            repeat(4) { i ->
                val ym = YearMonth.from(now).minusMonths(i.toLong())
                budget(database, ym, rent, 80000)
                budget(database, ym, groceries, 60000)
                budget(database, ym, utilities, 12000)
                budget(database, ym, transport, 22000)
                budget(database, ym, dining, 25000)
                budget(database, ym, entertainment, 12000)
                budget(database, ym, emergency, 25000)
                budget(database, ym, vacation, 30000)
            }

            note(database, groceries, labels.groceriesNote)
            note(database, "account-$checking", labels.checkingNote)

            // Credit-card configuration uses the same preference key read by Actua.
            database.execSQL(
                "INSERT OR REPLACE INTO preferences(id,value) VALUES(?,?)",
                arrayOf<Any?>("actuali:credit_card:$credit", JSONObject().put("statementDay", 20).put("dueOffsetDays", 15).put("limitCents", 500000).toString()),
            )

            seedRules(database, groceries, supermarket, entertainment, streaming, transport, fuel)
            seedSchedules(database, labels, now, checking, credit, paycheck, landlord, streaming, cardPayment)
            seedDashboard(database, labels)

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun insertAccount(db: SQLiteDatabase, id: String, name: String, type: String, offBudget: Int, order: Double) =
        db.execSQL("INSERT OR REPLACE INTO accounts(id,name,type,offbudget,closed,tombstone,sort_order) VALUES(?,?,?,?,0,0,?)", arrayOf<Any?>(id, name, type, offBudget, order))

    private fun group(db: SQLiteDatabase, name: String, income: Boolean, order: Double): String = id().also {
        db.execSQL("INSERT INTO category_groups(id,name,is_income,sort_order,tombstone,hidden) VALUES(?,?,?,?,0,0)", arrayOf<Any?>(it, name, if (income) 1 else 0, order))
    }

    private fun category(db: SQLiteDatabase, name: String, group: String, income: Boolean, order: Double): String = id().also {
        db.execSQL("INSERT INTO categories(id,name,is_income,cat_group,sort_order,tombstone,hidden,template_settings) VALUES(?,?,?,?,?,0,0,?)", arrayOf<Any?>(it, name, if (income) 1 else 0, group, order, "{\"source\":\"notes\"}"))
        db.execSQL("INSERT OR REPLACE INTO category_mapping(id,transferId) VALUES(?,?)", arrayOf(it, it))
    }

    private fun setTarget(db: SQLiteDatabase, categoryId: String, target: BudgetTarget) {
        db.execSQL("UPDATE categories SET goal_def=?, template_settings=? WHERE id=?", arrayOf(target.toGoalDef(), "{\"source\":\"ui\"}", categoryId))
    }

    private fun setTargets(db: SQLiteDatabase, categoryId: String, targets: List<BudgetTarget>) {
        val goalDef = com.azimulkabir.actua.model.BudgetAutomationDocument.encode(targets)
        db.execSQL("UPDATE categories SET goal_def=?, template_settings=? WHERE id=?", arrayOf(goalDef, "{\"source\":\"ui\"}", categoryId))
    }

    private fun payee(db: SQLiteDatabase, name: String): String = id().also {
        db.execSQL("INSERT INTO payees(id,name,tombstone) VALUES(?,?,0)", arrayOf(it, name))
    }

    private fun transferPayee(db: SQLiteDatabase, accountId: String): String = id().also {
        db.execSQL("INSERT INTO payees(id,name,tombstone,transfer_acct) VALUES(?,NULL,0,?)", arrayOf(it, accountId))
    }

    private fun opening(db: SQLiteDatabase, account: String, category: String, date: LocalDate, amount: Long) =
        txn(db, account, category, null, date, amount, true, true, starting = true)

    private fun txn(db: SQLiteDatabase, account: String, category: String?, payee: String?, date: LocalDate, amount: Long,
                    cleared: Boolean, reconciled: Boolean, starting: Boolean = false) {
        db.execSQL(
            "INSERT INTO transactions(id,acct,category,amount,description,date,starting_balance_flag,tombstone,cleared,reconciled,sort_order) VALUES(?,?,?,?,?,?,?,0,?,?,?)",
            arrayOf<Any?>(id(), account, category, amount, payee, date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE).toInt(), if (starting) 1 else 0, if (cleared) 1 else 0, if (reconciled) 1 else 0, System.nanoTime().toDouble()),
        )
    }

    private fun transfer(
        db: SQLiteDatabase,
        fromAccount: String,
        toAccount: String,
        fromPayee: String,
        toPayee: String,
        date: LocalDate,
        amount: Long,
        reconciled: Boolean,
    ) {
        val sourceId = id()
        val targetId = id()
        val ymd = date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE).toInt()
        val sortOrder = System.nanoTime().toDouble()
        db.execSQL(
            "INSERT INTO transactions(id,acct,category,amount,description,date,transferred_id,tombstone,cleared,reconciled,sort_order) VALUES(?,?,?,?,?,?,?,0,1,?,?)",
            arrayOf<Any?>(sourceId, fromAccount, null, -amount, fromPayee, ymd, targetId, if (reconciled) 1 else 0, sortOrder),
        )
        db.execSQL(
            "INSERT INTO transactions(id,acct,category,amount,description,date,transferred_id,tombstone,cleared,reconciled,sort_order) VALUES(?,?,?,?,?,?,?,0,1,?,?)",
            arrayOf<Any?>(targetId, toAccount, null, amount, toPayee, ymd, sourceId, if (reconciled) 1 else 0, sortOrder + 1),
        )
    }

    private fun budget(db: SQLiteDatabase, month: YearMonth, category: String, amount: Long) {
        val key = month.year * 100 + month.monthValue
        db.execSQL("INSERT OR REPLACE INTO zero_budgets(id,month,category,amount,carryover) VALUES(?,?,?,?,0)", arrayOf<Any?>("$key-$category", key, category, amount))
        db.execSQL("INSERT OR IGNORE INTO created_budgets(month) VALUES(?)", arrayOf(month.toString()))
    }

    private fun note(db: SQLiteDatabase, id: String, text: String) = db.execSQL("INSERT OR REPLACE INTO notes(id,note) VALUES(?,?)", arrayOf(id, text))

    private fun seedRules(db: SQLiteDatabase, groceries: String, supermarket: String, entertainment: String, streaming: String, transport: String, fuel: String) {
        fun rule(payee: String, category: String) {
            val conditions = JSONArray().put(JSONObject().put("op", "is").put("field", "description").put("type", "id").put("value", payee))
            val actions = JSONArray().put(JSONObject().put("op", "set").put("field", "category").put("type", "id").put("value", category))
            db.execSQL("INSERT INTO rules(id,stage,conditions,actions,tombstone,conditions_op) VALUES(?,NULL,?,?,0,'and')", arrayOf(id(), conditions.toString(), actions.toString()))
        }
        rule(supermarket, groceries); rule(streaming, entertainment); rule(fuel, transport)
    }

    private fun seedSchedules(db: SQLiteDatabase, labels: DemoBudgetLabels, now: LocalDate, checking: String, credit: String, paycheck: String, landlord: String, streaming: String, cardPayment: String) {
        schedule(db, labels.salarySchedule, checking, paycheck, 220000, now.plusMonths(1).withDayOfMonth(2), "monthly", true)
        schedule(db, labels.rentSchedule, checking, landlord, -80000, now.plusMonths(1).withDayOfMonth(3), "monthly", false)
        schedule(db, labels.streamingSchedule, credit, streaming, -1200, now.plusMonths(1).withDayOfMonth(12), "monthly", true)
        schedule(db, labels.cardPaymentSchedule, checking, cardPayment, -25000, now.plusMonths(1).withDayOfMonth(25), "monthly", false)
    }

    private fun schedule(db: SQLiteDatabase, name: String, account: String, payee: String, amount: Long, next: LocalDate, period: String, autoPost: Boolean) {
        val scheduleId = id(); val ruleId = id(); val nextId = id()
        val dateValue = JSONObject().put("start", next.toString()).put("frequency", period).put("interval", 1)
        val conditions = JSONArray()
            .put(JSONObject().put("op", "is").put("field", "payee").put("value", payee))
            .put(JSONObject().put("op", "is").put("field", "account").put("value", account))
            .put(JSONObject().put("op", "isapprox").put("field", "date").put("value", dateValue))
            .put(JSONObject().put("op", "isapprox").put("field", "amount").put("value", amount))
        val actions = JSONArray().put(JSONObject().put("op", "link-schedule").put("value", scheduleId))
        db.execSQL("INSERT INTO rules(id,stage,conditions,actions,tombstone,conditions_op) VALUES(?,NULL,?,?,0,'and')", arrayOf(ruleId, conditions.toString(), actions.toString()))
        db.execSQL("INSERT INTO schedules(id,rule,active,completed,posts_transaction,tombstone,name) VALUES(?,?,1,0,?,0,?)", arrayOf<Any?>(scheduleId, ruleId, if (autoPost) 1 else 0, name))
        val ymd = next.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE).toInt()
        val ts = System.currentTimeMillis()
        db.execSQL("INSERT INTO schedules_next_date(id,schedule_id,local_next_date,local_next_date_ts,base_next_date,base_next_date_ts,tombstone) VALUES(?,?,?,?,?,?,0)", arrayOf<Any?>(nextId, scheduleId, ymd, ts, ymd, ts))
    }

    private fun seedDashboard(db: SQLiteDatabase, labels: DemoBudgetLabels) {
        val page = "demo-dashboard-main"
        db.execSQL("INSERT OR REPLACE INTO dashboard_pages(id,name,tombstone) VALUES(?,?,0)", arrayOf(page, labels.overviewReport))
        val welcome = JSONObject().put("content", labels.welcomeReport)
        db.execSQL("INSERT INTO dashboard(id,type,width,height,x,y,meta,tombstone,dashboard_page_id) VALUES(?,?,?,?,?,?,?,0,?)", arrayOf<Any?>(id(), "markdown-card", 12, 2, 0, 0, welcome.toString(), page))
        val spending = JSONObject().put("name", labels.thisMonthReport).put("mode", "single-month")
        db.execSQL("INSERT INTO dashboard(id,type,width,height,x,y,meta,tombstone,dashboard_page_id) VALUES(?,?,?,?,?,?,?,0,?)", arrayOf<Any?>(id(), "spending-card", 12, 2, 0, 2, spending.toString(), page))
        db.execSQL("INSERT INTO dashboard(id,type,width,height,x,y,meta,tombstone,dashboard_page_id) VALUES(?,?,?,?,?,?,?,0,?)", arrayOf<Any?>(id(), "net-worth-card", 12, 2, 0, 4, "{}", page))
    }

    private fun safeDay(base: LocalDate, day: Int): LocalDate = base.withDayOfMonth(minOf(day, base.lengthOfMonth()))
    private fun id(): String = UUID.randomUUID().toString().lowercase()
}
