package com.azimulkabir.actua.data

import android.content.Context
import android.util.Log
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.ActualTransactionForm
import com.azimulkabir.actua.data.budget.ActualTransactionFormService
import com.azimulkabir.actua.data.budget.ActualTransactionType
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.ActualSplitLineForm
import com.azimulkabir.actua.data.budget.ActualEntityWriter
import com.azimulkabir.actua.data.budget.ActualBudgetWriter
import com.azimulkabir.actua.data.budget.CategoryReorderPlanner
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.data.budget.BudgetOpenProbe
import com.azimulkabir.actua.data.importing.ImportCandidate
import com.azimulkabir.actua.data.location.Coordinates
import com.azimulkabir.actua.data.location.PayeeLocationWriter
import com.azimulkabir.actua.data.importing.ImportDuplicateDetector
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.BudgetHistory
import com.azimulkabir.actua.model.BudgetTarget
import com.azimulkabir.actua.model.BudgetAutomationDocument
import com.azimulkabir.actua.model.BudgetNoteAutomationParser
import com.azimulkabir.actua.model.BudgetTemplatePreview
import com.azimulkabir.actua.model.CleanupGroup
import com.azimulkabir.actua.model.CleanupNoteParser
import com.azimulkabir.actua.model.CleanupPreview
import com.azimulkabir.actua.model.CleanupTarget
import com.azimulkabir.actua.model.CleanupTemplatePlanner
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.TransactionStatusFilter
import com.azimulkabir.actua.model.Type
import com.azimulkabir.actua.model.SplitLine
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportMonth
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.CreditCardConfig
import com.azimulkabir.actua.model.CreditCardCycle
import com.azimulkabir.actua.model.paymentDue
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.model.sortedForPaymentPriority
import com.azimulkabir.actua.data.sync.ActualSyncScheduler
import org.json.JSONObject
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleChoice
import com.azimulkabir.actua.data.rules.RuleEditorData
import com.azimulkabir.actua.data.rules.RulePreviewChoices
import com.azimulkabir.actua.data.rules.TransactionRulePreview
import com.azimulkabir.actua.data.schedules.ActualScheduleWriter
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleListItem
import com.azimulkabir.actua.data.schedules.ScheduleLinkedTransaction
import com.azimulkabir.actua.data.schedules.ScheduleRecurrence
import com.azimulkabir.actua.data.schedules.ScheduleStatusCalculator
import com.azimulkabir.actua.data.schedules.ScheduleWriteBuilder
import com.azimulkabir.actua.data.schedules.ScheduleFormFields
import com.azimulkabir.actua.data.schedules.ScheduleDiscovery
import com.azimulkabir.actua.data.schedules.BillCalendarItem
import com.azimulkabir.actua.data.schedules.BillsCalendarEngine
import com.azimulkabir.actua.data.schedules.sortedForDisplay
import com.azimulkabir.actua.model.BudgetScheduleFunding
import com.azimulkabir.actua.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException

data class PayeeLocationSummary(
    val id: String,
    val payeeId: String,
    val payeeName: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long,
)

data class NearbyPayeeSummary(
    val locationId: String,
    val payeeName: String,
    val distanceMeters: Double,
)

class ActuaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scheduleSync = {
        ActualSyncScheduler.scheduleMutation(appContext)
        WidgetUpdater.requestAll(appContext)
    }
    private val actualDatabase: ActualBudgetDatabase? = BudgetFileManager(context).let { files ->
        val activeBudgetStore = ActiveBudgetStore(context)
        val selectedId = activeBudgetStore.budgetId
        val candidates = files.listLocalBudgets().sortedWith(
            compareBy({ if (it.id == selectedId) 0 else 1 }, { it.budgetName?.lowercase() ?: it.id }, { it.id }),
        )
        val opened = candidates.firstNotNullOfOrNull { metadata ->
            openBudget(files, metadata.id)?.also {
                if (metadata.id != selectedId) activeBudgetStore.budgetId = metadata.id
            }
        }
        if (opened == null && selectedId != null) activeBudgetStore.budgetId = null
        opened
    }
    private val actualWriter = actualDatabase?.let { ActualTransactionWriter(it, onWrite = scheduleSync) }
    private val actualEntities = actualDatabase?.let { ActualEntityWriter(it, onWrite = scheduleSync) }
    private val actualBudgets = actualDatabase?.let { ActualBudgetWriter(it, onWrite = scheduleSync) }
    private val actualSchedules = actualDatabase?.let { ActualScheduleWriter(it, onWrite = scheduleSync) }
    private val payeeLocationWriter = actualDatabase?.let {
        PayeeLocationWriter(it, onWrite = scheduleSync)
    }
    private val actualForms = actualDatabase?.let { db ->
        ActualTransactionFormService(db, requireNotNull(actualWriter))
    }

    val isUsingActualBudget: Boolean get() = actualDatabase != null

    fun close() {
        actualDatabase?.close()
    }

    private fun openBudget(files: BudgetFileManager, budgetId: String): ActualBudgetDatabase? {
        val database = runCatching { ActualBudgetDatabase.open(files.databaseFile(budgetId)) }
            .onFailure {
                Log.e("ActuaRepository", "Could not open a local budget (${it.javaClass.simpleName})")
                discardUnreadableBudget(files, budgetId)
            }
            .getOrNull()
            ?: return null
        return try {
            BudgetOpenProbe.validate(database)
            database
        } catch (error: CancellationException) {
            database.close()
            throw error
        } catch (error: Exception) {
            Log.e("ActuaRepository", "Skipping an unusable local budget (${error.javaClass.simpleName})")
            database.close()
            discardUnreadableBudget(files, budgetId)
            null
        }
    }

    private fun discardUnreadableBudget(files: BudgetFileManager, budgetId: String) {
        runCatching { files.deleteBudget(budgetId) }
            .onFailure { Log.e("ActuaRepository", "Could not discard an unreadable local budget (${it.javaClass.simpleName})") }
    }

    fun categoryNames(): List<String> = actualDatabase?.fetchCategoryGroups()
        ?.flatMap { it.categories }
        ?.filterNot { it.hidden }
        ?.map { it.name }
        ?: emptyList()

    fun payeeNames(): List<String> = actualDatabase?.fetchPayees()
        ?.filter { it.transferAccountId == null && it.name.isNotBlank() }
        ?.map { it.name }
        ?: emptyList()

    fun payeeLocationWritesSupported(): Boolean =
        actualDatabase?.payeeLocationWritesSupported() == true

    fun payeeLocations(): List<PayeeLocationSummary> {
        val database = actualDatabase ?: return emptyList()
        val names = database.fetchPayees().associate { it.id to it.name }
        return database.fetchPayeeLocations().mapNotNull { location ->
            val name = names[location.payeeId]?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            PayeeLocationSummary(
                id = location.id,
                payeeId = location.payeeId,
                payeeName = name,
                latitude = location.latitude,
                longitude = location.longitude,
                createdAt = location.createdAt,
            )
        }
    }

    fun recordPayeeLocation(payeeName: String, coordinates: Coordinates): Boolean {
        val database = actualDatabase ?: return false
        if (!database.payeeLocationWritesSupported()) return false
        val payee = database.fetchPayees().firstOrNull {
            it.transferAccountId == null && it.name == payeeName
        } ?: return false
        return payeeLocationWriter?.record(payee.id, coordinates) != null
    }

    fun deletePayeeLocation(locationId: String): Boolean =
        payeeLocationWriter?.delete(locationId) == true

    fun clearPayeeLocations(payeeId: String): Int =
        payeeLocationWriter?.deleteAllForPayee(payeeId) ?: 0

    fun nearbyPayees(coordinates: Coordinates): List<NearbyPayeeSummary> =
        actualDatabase?.fetchNearbyPayees(coordinates)
            ?.mapNotNull { nearby ->
                nearby.payee.name.takeIf(String::isNotBlank)?.let { name ->
                    NearbyPayeeSummary(
                        locationId = nearby.location.id,
                        payeeName = name,
                        distanceMeters = nearby.distanceMeters,
                    )
                }
            }
            .orEmpty()

    fun rules(): List<Rule> = actualDatabase?.fetchRules().orEmpty()

    fun rulesSupported(): Boolean = actualDatabase?.rulesSupported() == true

    fun scheduleOwnedRuleIds(): Set<String> = actualDatabase?.scheduleOwnedRuleIds().orEmpty()

    fun schedules(today: DayDate = DayDate.today()): List<ScheduleListItem> {
        val db = actualDatabase ?: return emptyList()
        val schedules = db.fetchScheduleSummaries()
        val paid = db.fetchPaidScheduleIds(schedules)
        val accounts = db.fetchAccounts().associate { it.id to it.name }
        val payees = db.fetchPayees().associate { it.id to it.name }
        return schedules.map { schedule ->
            ScheduleListItem(
                schedule,
                ScheduleStatusCalculator.status(
                    schedule.nextDate, schedule.completed, schedule.id in paid,
                    schedule.customUpcomingLength, today,
                ),
                schedule.accountId?.let(accounts::get),
                schedule.payeeId?.let(payees::get),
            )
        }.sortedForDisplay()
    }

    fun scheduleTransactions(scheduleId: String): List<ScheduleLinkedTransaction> {
        val database = actualDatabase ?: return emptyList()
        val accounts = database.fetchAccounts().associate { it.id to it.name }
        return database.fetchScheduleTransactions(scheduleId).map { transaction ->
            ScheduleLinkedTransaction(
                id = transaction.id,
                date = DayDate.fromYyyymmdd(transaction.date),
                payeeName = transaction.payeeName.orEmpty(),
                accountName = accounts[transaction.accountId].orEmpty(),
                amountCents = transaction.amountCents,
            )
        }
    }

    fun discoverSchedules(): List<ScheduleDiscovery.DisplayProposal> {
        val database = actualDatabase ?: return emptyList()
        val accounts = database.fetchAccounts().associate { it.id to it.name }
        val payees = database.fetchPayees().associate { it.id to it.name }
        return database.discoverSchedules().map { proposal ->
            ScheduleDiscovery.DisplayProposal(
                proposal = proposal,
                payeeName = payees[proposal.payeeId].orEmpty(),
                accountName = accounts[proposal.accountId].orEmpty(),
            )
        }
    }

    fun billCalendarItems(year: Int, month: Int, cardBills: Boolean): List<BillCalendarItem> {
        if (cardBills) return BillsCalendarEngine.itemsForCreditCards(creditCards(), year, month)
        val database = actualDatabase ?: return emptyList()
        val schedules = schedules()
        val categoryNames = database.fetchCategoryGroups().flatMap { it.categories }.associate { it.id to it.name }
        return BillsCalendarEngine.itemsForSchedules(
            schedules = schedules,
            paymentDates = database.fetchSchedulePaymentDates(schedules.map { it.schedule.id }),
            categoryNames = categoryNames,
            year = year,
            month = month,
        )
    }

    fun createDiscoveredSchedules(proposals: List<ScheduleDiscovery.Proposal>): Boolean {
        if (proposals.isEmpty()) return false
        val writer = actualSchedules ?: return false
        proposals.forEach { proposal ->
            writer.apply(ScheduleWriteBuilder.create(
                fields = proposal.formFields,
                scheduleId = java.util.UUID.randomUUID().toString().lowercase(),
                ruleId = java.util.UUID.randomUUID().toString().lowercase(),
                nextDateRowId = java.util.UUID.randomUUID().toString().lowercase(),
                now = System.currentTimeMillis(),
                today = DayDate.today(),
            ))
        }
        return true
    }

    fun unlinkScheduleTransaction(scheduleId: String, transactionId: String): Boolean {
        val transaction = actualDatabase?.fetchTransaction(transactionId) ?: return false
        require(transaction.scheduleId == scheduleId) { "This transaction is not linked to the schedule." }
        actualWriter!!.setScheduleLink(transaction, null)
        return true
    }

    fun setScheduleCompleted(scheduleId: String, completed: Boolean): Boolean {
        val schedule = actualDatabase?.fetchScheduleSummaries()?.firstOrNull { it.id == scheduleId }
            ?: return false
        if (schedule.completed == completed) return true
        actualSchedules!!.apply(ScheduleWriteBuilder.columns(
            schedule.id, "completed" to if (completed) 1 else 0,
        ))
        if (!completed) {
            val date = schedule.dateCondition ?: return true
            val next = com.azimulkabir.actua.data.schedules.ScheduleConditions.nextDate(
                date, DayDate.today(),
            ) ?: return true
            ScheduleWriteBuilder.nextDate(
                schedule, next, reset = true, now = System.currentTimeMillis(),
            )?.let(actualSchedules::apply)
        }
        return true
    }

    fun updateSchedule(scheduleId: String, fields: ScheduleFormFields, payeeName: String): Boolean {
        val schedule = actualDatabase?.fetchScheduleSummaries()?.firstOrNull { it.id == scheduleId }
            ?: return false
        val payeeId = payeeName.trim().takeIf(String::isNotEmpty)?.let {
            actualWriter!!.resolveOrCreatePayee(it).id
        }
        val plan = ScheduleWriteBuilder.update(
            schedule = schedule,
            fields = fields.copy(payeeId = payeeId),
            now = System.currentTimeMillis(),
            today = DayDate.today(),
            newNextDateRowId = { java.util.UUID.randomUUID().toString() },
            newRuleId = { java.util.UUID.randomUUID().toString() },
        )
        actualSchedules!!.apply(plan)
        return true
    }

    fun createSchedule(fields: ScheduleFormFields, payeeName: String): Boolean {
        val database = actualDatabase ?: return false
        fields.normalizedName?.let { name ->
            require(!database.scheduleNameExists(name)) { "A schedule named $name already exists." }
        }
        val payeeId = payeeName.trim().takeIf(String::isNotEmpty)?.let {
            actualWriter!!.resolveOrCreatePayee(it).id
        }
        val plan = ScheduleWriteBuilder.create(
            fields = fields.copy(payeeId = payeeId),
            scheduleId = java.util.UUID.randomUUID().toString().lowercase(),
            ruleId = java.util.UUID.randomUUID().toString().lowercase(),
            nextDateRowId = java.util.UUID.randomUUID().toString().lowercase(),
            now = System.currentTimeMillis(),
            today = DayDate.today(),
        )
        actualSchedules!!.apply(plan)
        return true
    }

    /** Post one linked transaction without advancing the schedule, matching Actual/Actuali. */
    fun postScheduleTransaction(scheduleId: String, today: Boolean): Boolean {
        val schedule = actualDatabase?.fetchScheduleSummaries()?.firstOrNull { it.id == scheduleId }
            ?: return false
        require(!schedule.completed) { "Restart this schedule before posting a transaction" }
        val accountId = requireNotNull(schedule.accountId) { "This schedule has no account" }
        val date = if (today) DayDate.today() else schedule.nextDate ?: DayDate.today()
        actualWriter!!.createTransaction(
            ActualTransaction(
                id = java.util.UUID.randomUUID().toString().lowercase(),
                accountId = accountId,
                date = date.yyyymmdd,
                amountCents = schedule.postAmount,
                payeeId = schedule.payeeId,
                payeeName = null,
                categoryId = schedule.categoryId,
                categoryName = null,
                notes = null,
                cleared = false,
                reconciled = false,
                transferId = null,
                isParent = false,
                parentId = null,
                tombstone = false,
                sortOrder = null,
                importedPayee = null,
                scheduleId = schedule.id,
                transferAccountId = null,
            ),
            applyRules = true,
        )
        return true
    }

    fun deleteSchedule(scheduleId: String): Boolean {
        val schedule = actualDatabase?.fetchScheduleSummaries()?.firstOrNull { it.id == scheduleId }
            ?: return false
        actualSchedules!!.apply(ScheduleWriteBuilder.delete(schedule))
        return true
    }

    fun skipScheduleNextDate(scheduleId: String): Boolean {
        val schedule = actualDatabase?.fetchScheduleSummaries()?.firstOrNull { it.id == scheduleId }
            ?: return false
        val current = schedule.nextDate ?: return false
        val recurring = schedule.dateCondition as? com.azimulkabir.actua.data.schedules.ScheduleDateCondition.Recurring
            ?: return false
        val next = ScheduleRecurrence.nextOccurrence(
            recurring.config, ScheduleRecurrence.skipSearchStart(current, recurring.config),
        ) ?: return false
        if (next == current) return false
        val plan = ScheduleWriteBuilder.nextDate(schedule, next, reset = false,
            now = System.currentTimeMillis()) ?: return false
        actualSchedules!!.apply(plan)
        return true
    }

    fun ruleEditorData(): RuleEditorData {
        val db = actualDatabase ?: return RuleEditorData()
        val groups = db.fetchCategoryGroups()
        val allAccounts = db.fetchAccounts()
        val allPayees = db.fetchPayees()
        val allCategories = groups.flatMap { it.categories }
        val accountNames = allAccounts.associate { it.id to it.name }
        val allNames = (allAccounts.map { it.id to it.name } + allPayees.map { payee ->
            val name = payee.name.takeIf { it.isNotBlank() }
                ?: payee.transferAccountId?.let(accountNames::get)
                ?: ""
            payee.id to name
        } +
            allCategories.map { it.id to it.name } + groups.map { it.id to it.name }).toMap()
        return RuleEditorData(
            accounts = allAccounts.filterNot { it.closed }.map { RuleChoice(it.id, it.name) },
            payees = allPayees.filter { it.transferAccountId == null && it.name.isNotBlank() }
                .map { RuleChoice(it.id, it.name) },
            categories = groups.filterNot { it.hidden }.flatMap { it.categories }.filterNot { it.hidden }
                .map { RuleChoice(it.id, it.name) },
            categoryGroups = groups.filterNot { it.hidden }.map { RuleChoice(it.id, it.name) },
            names = allNames,
        )
    }

    fun saveRule(rule: Rule): Boolean {
        require(rule.conditions.isNotEmpty()) { "Add at least one condition" }
        require(rule.actions.isNotEmpty()) { "Add at least one action" }
        actualEntities?.saveRule(rule) ?: return false
        return true
    }

    fun deleteRule(ruleId: String): Boolean {
        val db = actualDatabase ?: return false
        require(ruleId !in db.scheduleOwnedRuleIds()) { "This rule belongs to a schedule" }
        actualEntities!!.deleteRule(ruleId)
        return true
    }

    fun budgetGroups(month: String = currentMonth()): List<BudgetGroup> {
        actualDatabase?.let { db ->
            val budget = db.fetchBudgetMonth(month)
            val selectedMonth = java.time.YearMonth.parse(month)
            val histories = (1L..6L).map { offset -> db.fetchBudgetMonth(selectedMonth.minusMonths(offset).toString()) }
            val percentageSources = setOf("available funds", "all income") +
                (budget.incomeCategories + budget.hiddenIncomeCategories).flatMap {
                    listOfNotNull(it.categoryId, it.categoryName)
                }
            // One query for every category's note instead of one query (plus a hasTable check)
            // per category — this ran synchronously during composition on every dataVersion or
            // month change, so an O(categories) round-trip count mattered.
            val allCategoryIds = (budget.categories + budget.hiddenCategories).map { it.categoryId } +
                (budget.incomeCategories + budget.hiddenIncomeCategories).map { it.categoryId }
            val notes = db.fetchNotes(allCategoryIds)
            val expenseGroups = (budget.categories + budget.hiddenCategories).groupBy { it.groupId }.values
                .sortedBy { it.first().groupSortOrder }
                .map { rows ->
                    BudgetGroup(rows.first().groupName, rows.sortedBy { it.categorySortOrder }.map {
                        val automationDocument = BudgetAutomationDocument.decode(
                            it.goalDef, it.templateSource, percentageSources,
                        )
                        val cleanupDefinition = CleanupTarget.decode(it.cleanupDef)
                        BudgetCategory(
                            it.categoryName,
                            centsToDisplayUnits(it.budgetedCents),
                            centsToDisplayUnits(-it.spentCents),
                            centsToDisplayUnits(it.availableCents),
                            it.budgetedCents,
                            it.categoryId,
                            it.availableCents,
                            it.hidden,
                            -it.spentCents,
                            it.carryoverEnabled,
                            notes[it.categoryId].orEmpty(),
                            histories.mapNotNull { historyMonth ->
                                (historyMonth.categories + historyMonth.hiddenCategories)
                                    .firstOrNull { row -> row.categoryId == it.categoryId }
                                    ?.let { row -> BudgetHistory(historyMonth.month, row.budgetedCents, row.spentCents) }
                            },
                            target = automationDocument.supported.singleOrNull(),
                            hasUnsupportedTarget = automationDocument.hasUnsupported,
                            automations = automationDocument.supported,
                            unsupportedAutomationTypes = automationDocument.unsupportedTypes,
                            automationReadOnly = !automationDocument.editable,
                            goalCents = it.goalCents,
                            longGoal = it.longGoal,
                            cleanupTargets = cleanupDefinition.targets,
                            cleanupInvalid = cleanupDefinition.invalid,
                        )
                    }, hidden = rows.first().groupHidden)
                }

            val incomeGroups = (budget.incomeCategories + budget.hiddenIncomeCategories)
                .groupBy { it.groupName }
                .map { (groupName, rows) ->
                    BudgetGroup(
                        name = groupName,
                        categories = rows.sortedBy { it.sortOrder }.map {
                            BudgetCategory(
                                name = it.categoryName,
                                assigned = centsToDisplayUnits(it.budgetedCents),
                                spent = centsToDisplayUnits(it.receivedCents),
                                actualAvailable = centsToDisplayUnits(it.receivedCents),
                                actualAssignedCents = it.budgetedCents,
                                id = it.categoryId,
                                availableCents = it.receivedCents,
                                hidden = it.hidden,
                                spentCents = -it.receivedCents,
                                note = notes[it.categoryId].orEmpty(),
                                isIncome = true,
                            )
                        },
                        hidden = rows.first().groupHidden,
                        isIncome = true,
                    )
                }
            return expenseGroups + incomeGroups
        }
        return emptyList()
    }

    fun budgetScheduleFunding(month: String = currentMonth()): List<BudgetScheduleFunding> {
        val db = actualDatabase ?: return emptyList()
        val selected = runCatching { java.time.YearMonth.parse(month) }.getOrNull() ?: return emptyList()
        val monthStart = DayDate(selected.year, selected.monthValue, 1)
        val monthEnd = DayDate(selected.year, selected.monthValue, selected.lengthOfMonth())
        return db.fetchScheduleSummaries().mapNotNull { schedule ->
            val amount = kotlin.math.abs(schedule.amount?.postAmount ?: return@mapNotNull null)
            if (schedule.completed || amount <= 0L) return@mapNotNull null
            val condition = schedule.dateCondition ?: return@mapNotNull null
            val dates = when (condition) {
                is com.azimulkabir.actua.data.schedules.ScheduleDateCondition.Fixed ->
                    listOf(condition.day)
                is com.azimulkabir.actua.data.schedules.ScheduleDateCondition.Recurring ->
                    com.azimulkabir.actua.data.schedules.ScheduleRecurrence
                        .upcomingDates(condition.config, 64, monthStart)
                com.azimulkabir.actua.data.schedules.ScheduleDateCondition.Unsupported ->
                    return@mapNotNull null
            }
            val inMonth = dates.count { it >= monthStart && it <= monthEnd }
            val next = dates.firstOrNull { it >= monthStart }
                ?: schedule.nextDate
                ?: return@mapNotNull null
            val monthsUntil = (next.year - selected.year) * 12 +
                next.month - selected.monthValue
            BudgetScheduleFunding(
                id = schedule.id,
                name = schedule.name,
                amountCents = amount,
                occurrencesInMonth = inMonth,
                monthsUntilNextOccurrence = monthsUntil,
                categoryId = schedule.categoryId,
                active = next >= monthStart,
            )
        }
    }

    fun budgetOverview(month: String = currentMonth()): BudgetOverview {
        actualDatabase?.let { db ->
            val budget = db.fetchBudgetMonth(month)
            return BudgetOverview(
                toBudgetCents = budget.toBudgetCents,
                budgetedCents = budget.categories.sumOf { it.budgetedCents },
                spentCents = budget.categories.sumOf { it.spentCents },
                availableCents = budget.categories.sumOf { it.availableCents },
                bufferedCents = budget.bufferedCents,
            )
        }
        return BudgetOverview(null, 0, 0, 0)
    }

    fun accounts(): List<Account> {
        actualDatabase?.let { db ->
            val fetched = db.fetchAccounts()
            val notes = db.fetchNotes(fetched.map { "account-${it.id}" })
            return fetched.map {
                Account(
                    name = it.name,
                    balance = centsToDisplayUnits(it.balanceCents),
                    type = it.type.name.lowercase().replaceFirstChar(Char::uppercase),
                    offBudget = it.offBudget,
                    closed = it.closed,
                    balanceCents = it.balanceCents,
                    id = it.id,
                    clearedCents = it.clearedCents,
                    unclearedCents = it.unclearedCents,
                    reconciledCents = it.reconciledCents,
                    note = notes["account-${it.id}"].orEmpty(),
                )
            }
        }
        return emptyList()
    }

    fun creditCards(includeClosed: Boolean = false): List<CreditCardStatus> {
        val db = actualDatabase ?: return emptyList()
        val configs = db.fetchCreditCardConfigs()
        return db.fetchAccounts().mapNotNull { account ->
            val config = configs[account.id] ?: return@mapNotNull null
            if (account.closed && !includeClosed) return@mapNotNull null
            val cycle = CreditCardCycle(config.statementDay, config.paymentDue)
            val range = cycle.cycleRange()
            CreditCardStatus(
                account.id, account.name, account.balanceCents, config,
                db.fetchAccountSpend(account.id, range.first.yyyymmdd, range.second.yyyymmdd),
                config.limitCents?.plus(account.balanceCents), account.closed,
            )
        }.sortedForPaymentPriority()
    }

    fun setCreditCard(accountId: String, statementDay: Int?,
        paymentDue: CreditCardCycle.PaymentDue = CreditCardCycle.PaymentDue.DaysAfter(CreditCardCycle.DEFAULT_DUE_OFFSET_DAYS),
        limitCents: Long? = null): Boolean {
        val db = actualDatabase ?: return false
        require(db.fetchAccounts().any { it.id == accountId }) { "That account no longer exists" }
        val value = statementDay?.let {
            require(it in 1..31) { "Statement day must be between 1 and 31" }
            val cycle = CreditCardCycle(it, paymentDue)
            val fallbackOffset = when (paymentDue) {
                is CreditCardCycle.PaymentDue.DaysAfter -> paymentDue.days
                is CreditCardCycle.PaymentDue.DayOfMonth -> {
                    val statement = cycle.previousStatementDate()
                    maxOf(1, statement.daysUntil(cycle.dueDate(statement)))
                }
            }
            JSONObject().put("statementDay", it).put("dueOffsetDays", fallbackOffset).apply {
                if (paymentDue is CreditCardCycle.PaymentDue.DayOfMonth) put("dueDay", paymentDue.day)
                if (limitCents != null && limitCents > 0) put("limit", limitCents)
            }.toString()
        }
        actualEntities!!.setPreference(ActualBudgetDatabase.CREDIT_CARD_PREFERENCE_PREFIX + accountId, value)
        return true
    }

    fun transactions(query: String? = null, limit: Int = Int.MAX_VALUE, offset: Int = 0,
        unclearedOnly: Boolean = false, hideReconciled: Boolean = false,
        statusFilter: TransactionStatusFilter = TransactionStatusFilter.ALL): List<Transaction> {
        actualDatabase?.let { db ->
            val accountNames = db.fetchAccounts().associate { it.id to it.name }
            // The database API defaults to a 500-row page. This repository currently backs
            // an in-memory Compose list, so explicitly load the complete history; otherwise
            // older synced transactions exist locally but silently disappear from Accounts.
            return db.fetchTransactions(limit = limit, offset = offset, query = query,
                unclearedOnly = unclearedOnly, hideReconciled = hideReconciled, statusFilter = statusFilter)
                .map { toTransaction(it, accountNames) }
        }
        return emptyList()
    }

    private fun toTransaction(it: ActualTransaction, accountNames: Map<String, String>): Transaction {
        val isTransfer = it.transferId != null
        return Transaction(
            id = it.id,
            date = it.date.toString(),
            payee = it.payeeName.orEmpty(),
            category = when {
                isTransfer -> ""
                else -> it.categoryName.orEmpty()
            },
            account = accountNames[it.accountId].orEmpty(),
            amount = centsToDisplayUnits(it.amountCents),
            cleared = it.cleared,
            reconciled = it.reconciled,
            amountCents = it.amountCents,
            type = when {
                isTransfer -> Type.TRANSFER
                it.amountCents >= 0 -> Type.INCOME
                else -> Type.EXPENSE
            },
            transferAccount = it.transferAccountId?.let(accountNames::get),
            notes = it.notes.orEmpty(),
            categoryIsIncome = it.categoryIsIncome,
            scheduleId = it.scheduleId,
            splits = it.splitPortions.map { part ->
                SplitLine(
                    category = part.categoryName.orEmpty(),
                    amountCents = kotlin.math.abs(part.amountCents),
                    notes = part.notes.orEmpty(),
                    payee = part.payeeName.takeUnless { name -> name == it.payeeName }.orEmpty(),
                    isOpposite = (part.amountCents < 0) != (it.amountCents < 0),
                    childId = part.id,
                    categoryIsIncome = part.categoryIsIncome,
                )
            },
        )
    }

    /** Closed statements for a credit card account (up to 3), newest first. */
    fun fetchRecentStatements(accountId: String): List<CreditCardCycle.StatementRecord> {
        val db = actualDatabase ?: return emptyList()
        val config = db.fetchCreditCardConfigs()[accountId] ?: return emptyList()
        val account = db.fetchAccounts().firstOrNull { it.id == accountId } ?: return emptyList()
        val cycle = CreditCardCycle(config.statementDay, config.paymentDue)
        return db.fetchRecentStatements(accountId, cycle.recentStatementCycles(), account.balanceCents)
    }

    /** Transactions within a credit card billing statement date range [startDate, endDate]. */
    fun fetchStatementTransactions(accountId: String, startDate: Int, endDate: Int): List<Transaction> {
        val db = actualDatabase ?: return emptyList()
        val accountNames = db.fetchAccounts().associate { it.id to it.name }
        return db.fetchTransactions(accountId = accountId, limit = Int.MAX_VALUE)
            .filter { it.date in startDate..endDate }
            .map { toTransaction(it, accountNames) }
    }

    fun importDuplicateKeys(accountId: String): Set<String> {
        val db = actualDatabase ?: return emptySet()
        return db.fetchTransactions(limit = Int.MAX_VALUE)
            .asSequence()
            .filter { it.accountId == accountId && !it.tombstone && !it.isParent }
            .map { ImportDuplicateDetector.key(it.date, it.amountCents, it.payeeName ?: it.importedPayee.orEmpty()) }
            .toSet()
    }

    /** Commits reviewed candidates together through the normal CRDT transaction writer. */
    fun importTransactions(accountId: String, candidates: List<ImportCandidate>): Int {
        if (candidates.isEmpty()) return 0
        val db = actualDatabase ?: return 0
        val writer = actualWriter ?: return 0
        require(db.fetchAccounts().any { it.id == accountId && !it.closed }) { "That account is unavailable" }
        val rows = candidates.map { candidate ->
            val payeeId = writer.resolveOrCreatePayee(candidate.payee).id
            ActualTransaction(
                id = java.util.UUID.randomUUID().toString().lowercase(),
                accountId = accountId,
                date = candidate.date,
                amountCents = candidate.amountCents,
                payeeId = payeeId,
                payeeName = null,
                categoryId = null,
                categoryName = null,
                notes = listOfNotNull(candidate.notes.takeIf(String::isNotBlank), candidate.reference?.let { "Reference: $it" })
                    .joinToString(" · ").takeIf(String::isNotBlank),
                cleared = false,
                reconciled = false,
                transferId = null,
                isParent = false,
                parentId = null,
                tombstone = false,
                sortOrder = null,
                importedPayee = candidate.payee,
                scheduleId = null,
                transferAccountId = null,
            )
        }
        writer.mutate(inserts = rows)
        return rows.size
    }

    fun reports(): ReportSnapshot {
        val db = actualDatabase ?: return ReportSnapshot(emptyList(), emptyList(), 0)
        val accounts = db.fetchAccounts()
        val groups = db.fetchCategoryGroups()
        val onBudgetIds = accounts.filter { !it.offBudget && !it.closed }.mapTo(mutableSetOf()) { it.id }
        val allRows = db.fetchTransactionsForReports()
        val rows = allRows.filter { it.accountId in onBudgetIds }
        val currentMonth = currentMonth()
        val monthRows = rows.filter { it.transferId == null }.groupBy { dateMonth(it.date) }
        val end = java.time.YearMonth.parse(currentMonth)
        val months = (5 downTo 0).map { offset ->
            val month = end.minusMonths(offset.toLong()).toString()
            val transactions = monthRows[month].orEmpty()
            ReportMonth(
                month,
                transactions.filter { it.amountCents >= 0 }.sumOf { it.amountCents },
                transactions.filter { it.amountCents < 0 }.sumOf { -it.amountCents },
            )
        }
        val categories = rows.asSequence()
            .filter { it.transferId == null && it.amountCents < 0 && dateMonth(it.date) == currentMonth }
            .groupBy { it.categoryName ?: "Uncategorized" }
            .map { (name, transactions) -> ReportCategory(name, transactions.sumOf { -it.amountCents }) }
            .sortedByDescending { it.spentCents }
        val dashboardPages = db.fetchDashboardPages()
        val reportBudgets = mutableMapOf<java.time.YearMonth, Map<String, Long>>()
        val dashboards = com.azimulkabir.actua.data.reports.CoreReportEngine.dashboards(
            dashboardPages,
            widgets = db::fetchDashboardWidgets,
            transactions = allRows,
            accounts = accounts,
            groups = groups,
            budgetedByCategory = { month ->
                reportBudgets.getOrPut(month) {
                    runCatching { db.fetchBudgetMonth(month.toString()) }.getOrNull()
                        ?.let { it.categories + it.hiddenCategories }
                        ?.associate { it.categoryId to it.budgetedCents }
                        .orEmpty()
                }
            },
        )
        return ReportSnapshot(
            months,
            categories,
            accounts.filterNot { it.closed }.sumOf { it.balanceCents },
            dashboards,
        )
    }

    fun saveTransaction(transaction: Transaction) {
        actualDatabase?.let { db ->
            val account = db.fetchAccounts().firstOrNull { it.name == transaction.account && !it.closed }
                ?: error("Select an account")
            val categoriesAllowed = !account.offBudget && transaction.type != Type.TRANSFER
            val categories = db.fetchCategoryGroups().flatMap { it.categories }
            val category = categories.firstOrNull { it.name == transaction.category && !it.hidden }
            if (categoriesAllowed && transaction.splits.isEmpty() && transaction.category.isNotBlank() &&
                category == null) {
                error("Select a category from the list")
            }
            val transferAccount = transaction.transferAccount?.let { name ->
                db.fetchAccounts().firstOrNull { it.name == name && !it.closed }
                    ?: error("Select a destination account")
            }
            val original = transaction.id.takeIf(String::isNotBlank)?.let(db::fetchTransaction)
            actualForms!!.save(
                ActualTransactionForm(
                    accountId = account.id,
                    type = when (transaction.type) {
                        Type.EXPENSE -> ActualTransactionType.EXPENSE
                        Type.INCOME -> ActualTransactionType.INCOME
                        Type.TRANSFER -> ActualTransactionType.TRANSFER
                    },
                    amount = com.azimulkabir.actua.ui.components.centsToInput(kotlin.math.abs(transaction.amountCents)),
                    payeeName = transaction.payee,
                    transferToAccountId = transferAccount?.id,
                    categoryId = category?.id,
                    notes = transaction.notes,
                    date = parseDate(transaction.date),
                    cleared = transaction.cleared,
                    splits = transaction.splits.map { line ->
                        val lineCategory = if (categoriesAllowed) {
                            categories.firstOrNull { it.name == line.category }
                                ?: error("Select a category for every split")
                        } else null
                        ActualSplitLineForm(
                            childId = line.childId,
                            categoryId = lineCategory?.id,
                            amount = com.azimulkabir.actua.ui.components.centsToInput(line.amountCents),
                            isOpposite = line.isOpposite,
                            notes = line.notes,
                            payeeName = line.payee,
                        )
                    },
                    collapseSplit = original?.isParent == true && transaction.splits.isEmpty(),
                    categoryIsExplicit = transaction.categoryIsExplicit,
                ),
                original = original,
                applyRules = !transaction.rulesApplied,
            )
            return
        }
        error("Connect to Actual and download a budget before adding transactions")
    }

    fun previewRules(transaction: Transaction): Transaction {
        val db = actualDatabase ?: return transaction
        if (transaction.splits.isNotEmpty()) return transaction
        val accounts = db.fetchAccounts().filterNot { it.closed }
        val account = accounts.firstOrNull { it.name == transaction.account } ?: return transaction
        val categories = db.fetchCategoryGroups().filterNot { it.hidden }
            .flatMap { group -> group.categories.filterNot { it.hidden } }
        val ruleTransaction = if (transaction.type == Type.TRANSFER) {
            val destination = accounts.firstOrNull { it.name == transaction.transferAccount } ?: return transaction
            // Actual represents a transfer's destination through the destination account's
            // canonical transfer payee, not a plain account id, so rules keyed to that
            // destination match on this payee rather than on `transferAccountId` directly.
            val transferPayee = db.fetchPayees().firstOrNull { it.transferAccountId == destination.id }
                ?: return transaction
            com.azimulkabir.actua.data.budget.model.ActualTransaction(
                id = "rule-preview",
                accountId = account.id,
                date = parseDate(transaction.date),
                amountCents = transaction.amountCents,
                payeeId = transferPayee.id,
                payeeName = transferPayee.name,
                categoryId = null,
                categoryName = null,
                notes = transaction.notes.takeIf(String::isNotEmpty),
                cleared = transaction.cleared,
                reconciled = false,
                transferId = null,
                isParent = false,
                parentId = null,
                tombstone = false,
                sortOrder = null,
                importedPayee = null,
                scheduleId = null,
                transferAccountId = destination.id,
            )
        } else {
            val payee = db.fetchPayees().firstOrNull {
                it.transferAccountId == null && it.name.equals(transaction.payee.trim(), ignoreCase = true)
            }
            val currentCategory = categories.firstOrNull { it.name == transaction.category }?.id
            val signedAmount = when (transaction.type) {
                Type.EXPENSE -> -kotlin.math.abs(transaction.amountCents)
                Type.INCOME -> kotlin.math.abs(transaction.amountCents)
                Type.TRANSFER -> transaction.amountCents
            }
            com.azimulkabir.actua.data.budget.model.ActualTransaction(
                id = "rule-preview",
                accountId = account.id,
                date = parseDate(transaction.date),
                amountCents = signedAmount,
                payeeId = payee?.id,
                payeeName = payee?.name ?: transaction.payee.trim().takeIf(String::isNotEmpty),
                categoryId = currentCategory,
                categoryName = transaction.category.takeIf(String::isNotEmpty),
                notes = transaction.notes.takeIf(String::isNotEmpty),
                cleared = transaction.cleared,
                reconciled = false,
                transferId = null,
                isParent = false,
                parentId = null,
                tombstone = false,
                sortOrder = null,
                importedPayee = transaction.payee.trim().takeIf(String::isNotEmpty),
                scheduleId = null,
                transferAccountId = null,
            )
        }
        val preview = com.azimulkabir.actua.data.rules.RulesEngine.apply(
            ruleTransaction,
            db.fetchRules(),
            db.ruleContext(),
        )
        return TransactionRulePreview.map(
            transaction,
            preview,
            RulePreviewChoices(
                accountNames = accounts.associate { it.id to it.name },
                offBudgetAccountIds = accounts.filter { it.offBudget }.mapTo(mutableSetOf()) { it.id },
                categoryNames = categories.associate { it.id to it.name },
                payeeNames = db.fetchPayees().filter { it.transferAccountId == null }
                    .associate { it.id to it.name },
            ),
        )
    }

    fun setAccountClosed(name: String, closed: Boolean): Boolean {
        val db = actualDatabase ?: return false
        val account = db.fetchAccounts().firstOrNull { it.name == name } ?: return false
        actualEntities!!.setAccountClosed(account.id, closed)
        return true
    }

    fun renameAccount(oldName: String, newName: String): Boolean {
        val account = actualDatabase?.fetchAccounts()?.firstOrNull { it.name == oldName } ?: return false
        actualEntities!!.renameAccount(account.id, newName); return true
    }

    fun setAccountType(name: String, type: String): Boolean {
        val db = actualDatabase ?: return false
        val account = db.fetchAccounts().firstOrNull { it.name == name } ?: return false
        val normalized = ActualAccountType.entries.firstOrNull { it.name.equals(type, ignoreCase = true) } ?: return false
        actualEntities!!.setAccountType(account.id, normalized.name.lowercase())
        return true
    }

    fun renameCategory(groupName: String, oldName: String, newName: String): Boolean {
        val category = actualDatabase?.fetchCategoryGroups()?.firstOrNull { it.name == groupName }
            ?.categories?.firstOrNull { it.name == oldName } ?: return false
        actualEntities!!.renameCategory(category.id, newName); return true
    }

    fun renameCategoryGroup(oldName: String, newName: String): Boolean {
        val group = actualDatabase?.fetchCategoryGroups()?.firstOrNull { it.name == oldName } ?: return false
        actualEntities!!.renameCategoryGroup(group.id, newName); return true
    }

    fun createAccount(name: String, offBudget: Boolean, startingBalance: String, type: String = "Checking"): Boolean {
        val cents = ActualTransactionFormService.cents(startingBalance.ifBlank { "0" }) ?: return false
        val normalized = ActualAccountType.entries.firstOrNull { it.name.equals(type, ignoreCase = true) } ?: return false
        actualEntities?.createAccount(name, offBudget, cents, normalized.name.lowercase()) ?: return false
        return true
    }

    fun createCategory(groupName: String, name: String): Boolean {
        val group = actualDatabase?.fetchCategoryGroups()?.firstOrNull { it.name == groupName } ?: return false
        actualEntities!!.createCategory(name, group.id); return true
    }

    fun createCategoryGroup(name: String): Boolean {
        actualEntities?.createCategoryGroup(name) ?: return false
        return true
    }

    fun setCategoryHidden(groupName: String, categoryName: String, hidden: Boolean): Boolean {
        val db = actualDatabase ?: return false
        val group = db.fetchCategoryGroups().firstOrNull { it.name == groupName } ?: return false
        val category = group.categories.firstOrNull { it.name == categoryName } ?: return false
        actualEntities!!.setCategoryHidden(category.id, hidden)
        return true
    }

    fun deleteCategory(groupName: String, categoryName: String): Boolean {
        val db = actualDatabase ?: return false
        val group = db.fetchCategoryGroups().firstOrNull { it.name == groupName } ?: return false
        val category = group.categories.firstOrNull { it.name == categoryName } ?: return false
        actualEntities!!.deleteCategory(category.id)
        return true
    }

    fun setCategoryGroupHidden(groupName: String, hidden: Boolean): Boolean {
        val group = actualDatabase?.fetchCategoryGroups()?.firstOrNull { it.name == groupName } ?: return false
        actualEntities!!.setCategoryGroupHidden(group.id, hidden)
        return true
    }

    /** Category groups and categories sorted for the drag-to-reorder management screen. */
    fun categoryGroupsForReorder(): List<ActualCategoryGroup> =
        actualDatabase?.fetchCategoryGroups()
            ?.sortedWith(compareBy({ it.sortOrder }, { it.id }))
            ?.map { it.copy(categories = it.categories.sortedWith(compareBy({ category -> category.sortOrder }, { category -> category.id }))) }
            .orEmpty()

    fun moveCategory(move: CategoryReorderPlanner.CategoryMove): Boolean {
        actualEntities?.moveCategory(move.categoryId, move.groupId, move.beforeCategoryId) ?: return false
        return true
    }

    fun moveCategoryGroup(move: CategoryReorderPlanner.GroupMove): Boolean {
        actualEntities?.moveCategoryGroup(move.groupId, move.beforeGroupId) ?: return false
        return true
    }

    fun setBudgetAmount(groupName: String, categoryName: String, amountCents: Long, month: String = currentMonth()): Boolean {
        val db = actualDatabase ?: return false
        val category = db.fetchCategoryGroups().firstOrNull { it.name == groupName }
            ?.categories?.firstOrNull { it.name == categoryName } ?: return false
        actualBudgets!!.setAmount(month, category.id, amountCents)
        return true
    }

    /** Copies visible categories' budgeted amounts from the month before [month] into [month]. Hidden categories are left unchanged. */
    fun copyPreviousMonthBudget(month: String): Boolean {
        val db = actualDatabase ?: return false
        val previousMonth = java.time.YearMonth.parse(month).minusMonths(1).toString()
        val previous = db.fetchBudgetMonth(previousMonth)
        val amounts = previous.categories.associate { it.categoryId to it.budgetedCents } +
            if (previous.isTracking) previous.incomeCategories.associate { it.categoryId to it.budgetedCents } else emptyMap()
        if (amounts.isEmpty()) return true
        actualBudgets!!.setAmounts(month, amounts)
        return true
    }

    fun setCategoryNote(categoryId: String, note: String): Boolean {
        val normalized = normalizeNote(note)
        actualEntities?.setNote(categoryId, normalized) ?: return false
        val parsed = BudgetNoteAutomationParser.parse(normalized)
        if (parsed.valid) {
            actualEntities?.setCategoryNoteTarget(categoryId, BudgetAutomationDocument.encode(parsed.targets))
        }
        refreshCleanupDefinitions()
        return true
    }

    fun setAccountNote(accountId: String, note: String): Boolean {
        actualEntities?.setNote("account-$accountId", normalizeNote(note)) ?: return false
        return true
    }

    fun setCategoryCarryover(categoryId: String, enabled: Boolean, month: String = currentMonth()): Boolean {
        val start = java.time.YearMonth.parse(month)
        val end = java.time.YearMonth.now().plusMonths(12)
        val months = generateSequence(start) { current -> current.plusMonths(1).takeIf { it <= end } }.toList()
            .ifEmpty { listOf(start) }.map(java.time.YearMonth::toString)
        actualBudgets?.setCarryover(months, categoryId, enabled) ?: return false
        return true
    }

    /** Holds part or all of [month]'s To Budget amount for next month. Envelope budgets only. */
    fun setBufferedAmount(month: String, amountCents: Long): Boolean {
        actualBudgets?.setBuffered(month, amountCents) ?: return false
        return true
    }

    /** Cancels a manual hold on [month], returning the full amount to this month's To Budget. */
    fun resetNextMonthBuffer(month: String): Boolean {
        actualBudgets?.resetBuffer(month) ?: return false
        return true
    }

    fun setCategoryTarget(categoryId: String, target: BudgetTarget?): Boolean {
        if (categoryId.isBlank()) return false
        actualEntities?.setCategoryTarget(categoryId, target?.toGoalDef()) ?: return false
        return true
    }

    fun setCategoryAutomations(categoryId: String, targets: List<BudgetTarget>): Boolean {
        if (categoryId.isBlank()) return false
        actualEntities?.setCategoryTarget(categoryId, BudgetAutomationDocument.encode(targets)) ?: return false
        return true
    }

    fun applyBudgetTemplate(preview: BudgetTemplatePreview): Boolean {
        if (preview.changes.isEmpty() && preview.goalChanges.isEmpty()) return true
        actualBudgets?.applyTemplate(
            preview.month,
            preview.changes.associate { it.categoryId to it.proposedCents },
            preview.changes.associate { it.categoryId to it.currentCents },
            preview.goalChanges.associate { it.categoryId to it.proposedCents },
            preview.goalChanges.associate { it.categoryId to it.currentCents },
        ) ?: return false
        return true
    }

    fun cleanupGroups(): List<CleanupGroup> = actualDatabase?.fetchCleanupGroups()?.map { CleanupGroup(it.id, it.name) } ?: emptyList()

    /**
     * Re-scans every category note for `#cleanup` directives and rewrites `cleanup_def` plus
     * `cleanup_groups` in one atomic batch, mirroring Actual's `storeNoteCleanups()`. Group
     * names are resolved case-insensitively; groups no longer referenced by any category are
     * tombstoned rather than deleted, matching upstream's `tombstoneOrphanCleanupGroups()`.
     */
    fun refreshCleanupDefinitions(): Boolean {
        val db = actualDatabase ?: return false
        val writer = actualEntities ?: return false
        val categories = db.fetchCategoryGroups().flatMap { it.categories }
        val parsedByCategory = categories.mapNotNull { cat ->
            val rows = CleanupNoteParser.parse(db.fetchNote(cat.id))
            if (rows.isEmpty()) null else cat.id to rows
        }.toMap()
        val existingGroups = db.fetchCleanupGroups(includeTombstoned = true).associateBy { it.name.lowercase() }
        val allNames = parsedByCategory.values.flatten().mapNotNull { it.groupName?.trim()?.ifBlank { null } }.distinct()
        val nameToId = mutableMapOf<String, String>()
        val groupUpserts = mutableMapOf<String, String>()
        allNames.forEach { name ->
            val key = name.lowercase()
            val existing = existingGroups[key]
            val id = existing?.id ?: java.util.UUID.randomUUID().toString()
            nameToId[key] = id
            if (existing == null || existing.tombstone) groupUpserts[id] = name
        }
        val cleanupDefs = categories.associate { cat ->
            val rows = parsedByCategory[cat.id]
            val targets = rows?.mapNotNull { row ->
                val groupId = row.groupName?.let { nameToId[it.trim().lowercase()] }
                if (row.role == CleanupTarget.Role.OVERSPEND && groupId == null) null
                else CleanupTarget(row.role, groupId, row.weight)
            }.orEmpty()
            cat.id to CleanupTarget.encode(targets)
        }
        val referencedGroupIds = cleanupDefs.values.flatMap { raw -> CleanupTarget.decode(raw).targets.mapNotNull(CleanupTarget::groupId) }.toSet()
        val orphanGroupIds = existingGroups.values.filterNot { it.tombstone }.map { it.id }.toSet() - referencedGroupIds
        val changedDefs = cleanupDefs.filter { (id, raw) -> raw != categories.first { it.id == id }.cleanupDef }
        if (changedDefs.isEmpty() && groupUpserts.isEmpty() && orphanGroupIds.isEmpty()) return true
        writer.refreshCleanupDefinitions(changedDefs, groupUpserts, orphanGroupIds)
        return true
    }

    /** Read-only dry run for Actual's month-end cleanup source/sink groups. */
    fun previewCleanup(month: String = currentMonth()): CleanupPreview {
        val toBudget = budgetOverview(month).toBudgetCents ?: return CleanupPreview(month)
        val groups = budgetGroups(month)
        val cleanupGroupNames = actualDatabase?.fetchCleanupGroups()?.associate { it.id to it.name } ?: emptyMap()
        return CleanupTemplatePlanner.preview(groups, cleanupGroupNames, month, toBudget)
    }

    /** Applies a confirmed [CleanupPreview] as one synchronized CRDT/database batch. */
    fun applyCleanup(preview: CleanupPreview): Boolean {
        if (preview.isUpToDate) return true
        actualBudgets?.applyTemplate(
            preview.month,
            preview.changes.associate { it.categoryId to it.proposedCents },
            preview.changes.associate { it.categoryId to it.currentCents },
            preview.goalChanges.associate { it.categoryId to it.proposedCents },
            preview.goalChanges.associate { it.categoryId to it.currentCents },
        ) ?: return false
        return true
    }

    fun transferBudget(fromGroup: String?, fromCategory: String?, toGroup: String?, toCategory: String?,
        amountCents: Long, month: String = currentMonth()): Boolean {
        val db = actualDatabase ?: return false
        val groups = db.fetchCategoryGroups()
        val from = if (fromGroup == null || fromCategory == null) null else
            groups.firstOrNull { it.name == fromGroup }?.categories?.firstOrNull { it.name == fromCategory } ?: return false
        val to = if (toGroup == null || toCategory == null) null else
            groups.firstOrNull { it.name == toGroup }?.categories?.firstOrNull { it.name == toCategory } ?: return false
        actualBudgets!!.transfer(month, from?.id, to?.id, amountCents)
        return true
    }

    fun setTransactionCleared(id: String, cleared: Boolean): Boolean {
        val transaction = actualDatabase?.fetchTransaction(id) ?: return false
        actualWriter!!.setCleared(transaction, cleared)
        return true
    }

    fun reconcileAccount(accountId: String): Boolean {
        val writer = actualWriter ?: return false
        writer.reconcileClearedTransactions(accountId)
        return true
    }

    fun createReconciliationAdjustment(accountId: String, amountCents: Long): Boolean {
        require(amountCents != 0L) { "The balances already match" }
        val db = actualDatabase ?: return false
        val writer = actualWriter ?: return false
        require(db.fetchAccounts().any { it.id == accountId && !it.closed }) { "That account is unavailable" }
        writer.createTransaction(
            ActualTransaction(
                id = java.util.UUID.randomUUID().toString().lowercase(),
                accountId = accountId,
                date = DayDate.today().yyyymmdd,
                amountCents = amountCents,
                payeeId = null,
                payeeName = null,
                categoryId = null,
                categoryName = null,
                notes = "Reconciliation balance adjustment",
                cleared = true,
                reconciled = false,
                transferId = null,
                isParent = false,
                parentId = null,
                tombstone = false,
                sortOrder = System.currentTimeMillis().toDouble(),
                importedPayee = null,
                scheduleId = null,
                transferAccountId = null,
            ),
            applyRules = false,
        )
        return true
    }

    fun deleteTransaction(id: String): Boolean {
        val transaction = actualDatabase?.fetchTransaction(id) ?: return false
        actualWriter!!.deleteTransaction(transaction)
        return true
    }

    fun deleteTransactions(ids: Collection<String>): Int {
        val db = actualDatabase ?: return 0
        val writer = actualWriter ?: return 0
        var count = 0
        for (id in ids) {
            val transaction = db.fetchTransaction(id) ?: continue
            writer.deleteTransaction(transaction)
            count++
        }
        return count
    }

    fun linkScheduleTransactions(scheduleId: String, transactionIds: Collection<String>): Int {
        val db = actualDatabase ?: return 0
        val writer = actualWriter ?: return 0
        require(db.fetchScheduleSummaries().any { it.id == scheduleId }) { "That schedule no longer exists" }
        var count = 0
        for (id in transactionIds) {
            val transaction = db.fetchTransaction(id) ?: continue
            writer.setScheduleLink(transaction, scheduleId)
            count++
        }
        return count
    }

    fun unlinkScheduleFromTransactions(transactionIds: Collection<String>): Int {
        val db = actualDatabase ?: return 0
        val writer = actualWriter ?: return 0
        var count = 0
        for (id in transactionIds) {
            val transaction = db.fetchTransaction(id) ?: continue
            if (transaction.scheduleId == null) continue
            writer.setScheduleLink(transaction, null)
            count++
        }
        return count
    }

    private fun parseDate(value: String): Int {
        value.filter(Char::isDigit).toIntOrNull()?.takeIf { it in 19000101..29991231 }?.let { return it }
        error("Enter a valid date as YYYY-MM-DD")
    }

    private fun centsToDisplayUnits(cents: Long): Int =
        (cents / 100L).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    private fun normalizeNote(note: String): String = if (note.isBlank()) "" else note

    private fun currentMonth(): String = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        .format(java.util.Date())

    private fun dateMonth(date: Int): String = "%04d-%02d".format(date / 10_000, date / 100 % 100)
}
