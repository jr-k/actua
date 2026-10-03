package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualPayee
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HybridLogicalClock
import com.azimulkabir.actua.data.rules.RuleChangeGuard
import com.azimulkabir.actua.data.rules.RulesEngine
import java.util.UUID

/** Offline-first transaction mutations matching Actual's row/message shapes. */
class ActualTransactionWriter(
    private val database: ActualBudgetDatabase,
    nodeId: String = HybridLogicalClock.generateNodeId(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    nowMillis: () -> Long = System::currentTimeMillis,
    private val onWrite: () -> Unit = {},
) {
    private val clock = HybridLogicalClock(nodeId, nowMillis = nowMillis)

    init {
        database.maxMessageTimestamp()?.let(com.azimulkabir.actua.data.sync.HlcTimestamp::parse)?.let(clock::advance)
    }

    fun resolveOrCreatePayee(name: String): ActualPayee {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Payee name cannot be empty" }
        database.findPayeeByName(clean)?.let { return it }
        val payee = ActualPayee(idFactory(), clean, null)
        val messages = fields("payees", payee.id, linkedMapOf(
            "name" to payee.name, "transfer_acct" to null, "tombstone" to 0,
        )) + fields("payee_mapping", payee.id, linkedMapOf("targetId" to payee.id))
        database.insertPayee(payee, messages)
        saveClock()
        return payee
    }

    fun planPayee(name: String, planned: MutableMap<String, ActualPayee>): ActualPayee {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Payee name cannot be empty" }
        val key = clean.lowercase()
        database.findPayeeByName(clean)?.let { return it }
        return planned.getOrPut(key) { ActualPayee(idFactory(), clean, null) }
    }

    fun createTransaction(
        transaction: ActualTransaction,
        applyRules: Boolean = true,
        preserveCategory: Boolean = false,
    ): ActualTransaction? {
        val final = prepareForCreate(transaction, applyRules, preserveCategory) ?: return null
        validateBase(final)
        require(!final.isParent && final.parentId == null) { "Use createSplit for split rows" }
        database.insertTransactions(listOf(final), fieldsForInsert(final))
        saveClock()
        return final
    }

    fun prepareImportedTransaction(
        transaction: ActualTransaction,
        plannedPayees: MutableMap<String, ActualPayee>,
    ): ActualTransaction? = prepareForCreate(
        transaction,
        applyRules = true,
        preserveCategory = false,
        plannedPayees = plannedPayees,
    )

    fun createTransfer(source: ActualTransaction, target: ActualTransaction) {
        validateBase(source)
        validateBase(target)
        require(source.accountId != target.accountId) { "Transfer accounts must be different" }
        require(source.transferId == target.id && target.transferId == source.id) { "Transfer legs must reference each other" }
        require(source.amountCents == -target.amountCents) { "Transfer amounts must balance" }
        require(!source.isParent && !target.isParent && source.parentId == null && target.parentId == null)
        val messages = fieldsForInsert(source) + fieldsForInsert(target)
        database.insertTransactions(listOf(source, target), messages)
        saveClock()
    }

    fun createSplit(parent: ActualTransaction, children: List<ActualTransaction>) {
        validateBase(parent)
        require(parent.isParent && parent.parentId == null && parent.categoryId == null) { "Invalid split parent" }
        require(children.size >= 2) { "A split needs at least two lines" }
        require(children.all { it.parentId == parent.id && !it.isParent && it.accountId == parent.accountId }) {
            "Every split child must reference its parent and account"
        }
        require(children.sumOf(ActualTransaction::amountCents) == parent.amountCents) { "Split amount does not match parent" }
        children.forEach(::validateBase)
        val rows = listOf(parent) + children
        database.insertTransactions(rows, rows.flatMap(::fieldsForInsert))
        saveClock()
    }

    fun updateTransaction(transaction: ActualTransaction, changedFields: Set<String>) {
        validateBase(transaction)
        val unknown = changedFields - mutableTransactionFields
        require(unknown.isEmpty()) { "Unknown transaction fields: ${unknown.sorted().joinToString()}" }
        database.updateTransaction(transaction, fields("transactions", transaction.id,
            transactionFields(transaction).filterKeys { it in changedFields }))
        saveClock()
    }

    fun setScheduleLink(transaction: ActualTransaction, scheduleId: String?) {
        if (transaction.scheduleId == scheduleId) return
        updateTransaction(transaction.copy(scheduleId = scheduleId), setOf("schedule"))
    }

    /** Change cleared state while keeping split children aligned with their parent. */
    @Synchronized
    fun setCleared(transaction: ActualTransaction, cleared: Boolean) {
        require(!transaction.reconciled || transaction.cleared == cleared) {
            "Reconciled transactions are locked"
        }
        val originals = if (transaction.isParent) {
            listOf(transaction) + database.fetchChildTransactions(transaction.id)
        } else listOf(transaction)
        val updates = originals
            .filter { !it.reconciled && it.cleared != cleared }
            .map { it to it.copy(cleared = cleared) }
        if (updates.isNotEmpty()) mutate(updates = updates)
    }

    /** Lock all cleared rows for one account in a single CRDT/database transaction. */
    @Synchronized
    fun reconcileClearedTransactions(accountId: String): Int {
        val originals = database.fetchClearedUnreconciledTransactions(accountId)
        if (originals.isEmpty()) return 0
        mutate(updates = originals.map { it to it.copy(reconciled = true) })
        return originals.size
    }

    fun deleteTransaction(transaction: ActualTransaction) {
        val ids = if (transaction.isParent) {
            database.fetchChildTransactions(transaction.id).map(ActualTransaction::id) + transaction.id
        } else listOf(transaction.id)
        database.tombstoneTransactions(ids, ids.map { message("transactions", it, "tombstone", 1) })
        saveClock()
    }

    fun mutate(
        updates: List<Pair<ActualTransaction, ActualTransaction>> = emptyList(),
        inserts: List<ActualTransaction> = emptyList(),
        tombstoneIds: List<String> = emptyList(),
        applyRulesToInserts: Boolean = false,
        newPayees: List<ActualPayee> = emptyList(),
        accountBalances: Map<String, Long> = emptyMap(),
        accountLastSync: Map<String, String> = emptyMap(),
        accountSyncStatuses: Map<String, String> = emptyMap(),
    ) {
        val finalInserts = if (applyRulesToInserts) {
            inserts.mapNotNull { prepareForCreate(it, applyRules = true, preserveCategory = false) }
        } else {
            inserts
        }
        updates.forEach { (_, updated) -> validateBase(updated) }
        finalInserts.forEach(::validateBase)
        val messages = updates.flatMap { (original, updated) ->
            val changed = changedFields(original, updated)
            fields("transactions", updated.id, transactionFields(updated).filterKeys { it in changed })
        } + finalInserts.flatMap(::fieldsForInsert) +
            tombstoneIds.map { message("transactions", it, "tombstone", 1) } +
            newPayees.flatMap { payee ->
                fields("payees", payee.id, linkedMapOf(
                    "name" to payee.name,
                    "transfer_acct" to null,
                    "tombstone" to 0,
                )) + fields("payee_mapping", payee.id, mapOf("targetId" to payee.id))
            } +
            accountBalances.flatMap { (accountId, balance) ->
                fields("accounts", accountId, mapOf("balance_current" to balance))
            } + accountLastSync.flatMap { (accountId, lastSync) ->
                fields("accounts", accountId, mapOf("last_sync" to lastSync))
            } + accountSyncStatuses.flatMap { (accountId, status) ->
                fields("accounts", accountId, mapOf("bank_sync_status" to status))
            }
        database.mutateTransactions(
            updates.map { it.second },
            finalInserts,
            tombstoneIds,
            newPayees,
            accountBalances,
            accountLastSync,
            accountSyncStatuses,
            messages,
        )
        saveClock()
    }

    private fun prepareForCreate(
        transaction: ActualTransaction,
        applyRules: Boolean,
        preserveCategory: Boolean,
        plannedPayees: MutableMap<String, ActualPayee>? = null,
    ): ActualTransaction? {
        var final = transaction
        if (applyRules && transaction.transferId == null && !transaction.startingBalance) {
            val result = RulesEngine.apply(transaction, database.fetchRules(), database.ruleContext())
            if (result.isDeleted) return null
            final = result.transaction
            result.pendingPayeeName?.let { payeeName ->
                val payee = plannedPayees?.let { planPayee(payeeName, it) }
                    ?: resolveOrCreatePayee(payeeName)
                final = final.copy(payeeId = payee.id)
            }
            if (preserveCategory &&
                !RuleChangeGuard.shouldApplyRuleChange("category", transaction.categoryId, final.categoryId)
            ) {
                final = final.copy(categoryId = transaction.categoryId)
            }
        }
        if (database.fetchAccounts().any { it.id == final.accountId && it.offBudget }) {
            final = final.copy(categoryId = null)
        }
        return final
    }

    private fun validateBase(transaction: ActualTransaction) {
        require(transaction.id.isNotBlank() && transaction.accountId.isNotBlank())
        require(transaction.date in 19000101..29991231) { "Invalid Actual YYYYMMDD date" }
        // Actual permits zero-valued imported/scheduled rows. Interactive forms
        // reject zero at their own validation boundary, matching iOS.
    }

    private fun fieldsForInsert(transaction: ActualTransaction) =
        fields("transactions", transaction.id, transactionFields(transaction))

    private fun transactionFields(transaction: ActualTransaction): LinkedHashMap<String, Any?> = linkedMapOf(
        "acct" to transaction.accountId,
        "date" to transaction.date,
        "description" to transaction.payeeId,
        "category" to transaction.categoryId,
        "amount" to transaction.amountCents,
        "notes" to transaction.notes,
        "cleared" to if (transaction.cleared) 1 else 0,
        "reconciled" to if (transaction.reconciled) 1 else 0,
        "transferred_id" to transaction.transferId,
        "isParent" to if (transaction.isParent) 1 else 0,
        "isChild" to if (transaction.parentId != null) 1 else 0,
        "parent_id" to transaction.parentId,
        "tombstone" to if (transaction.tombstone) 1 else 0,
        "sort_order" to (transaction.sortOrder ?: System.currentTimeMillis().toDouble()),
        "imported_description" to transaction.importedPayee,
        "financial_id" to transaction.importedId,
        "raw_synced_data" to transaction.rawSyncedData,
        "schedule" to transaction.scheduleId,
        "starting_balance_flag" to if (transaction.startingBalance) 1 else 0,
    )

    private fun fields(dataset: String, row: String, values: Map<String, Any?>): List<CrdtMessage> =
        values.map { (column, value) -> message(dataset, row, column, value) }

    private fun message(dataset: String, row: String, column: String, value: Any?) =
        CrdtMessage(clock.send(), dataset, row, column, CrdtValue.serialize(value))

    private fun saveClock() {
        database.saveClock(ActualBudgetDatabase.ClockRecord(clock.current().toString(), database.deriveMerkleFromMessageLog().root))
        onWrite()
    }

    companion object {
        private val mutableTransactionFields = setOf(
            "acct", "date", "description", "category", "amount", "notes", "cleared",
            "reconciled", "transferred_id", "isParent", "parent_id", "tombstone", "schedule",
            "imported_description", "financial_id", "raw_synced_data",
        )

        fun changedFields(original: ActualTransaction, updated: ActualTransaction): Set<String> = buildSet {
            if (original.accountId != updated.accountId) add("acct")
            if (original.date != updated.date) add("date")
            if (original.payeeId != updated.payeeId) add("description")
            if (original.categoryId != updated.categoryId) add("category")
            if (original.amountCents != updated.amountCents) add("amount")
            if (original.notes != updated.notes) add("notes")
            if (original.cleared != updated.cleared) add("cleared")
            if (original.reconciled != updated.reconciled) add("reconciled")
            if (original.transferId != updated.transferId) add("transferred_id")
            if (original.scheduleId != updated.scheduleId) add("schedule")
            if (original.importedPayee != updated.importedPayee) add("imported_description")
            if (original.importedId != updated.importedId) add("financial_id")
            if (original.rawSyncedData != updated.rawSyncedData) add("raw_synced_data")
            if (original.isParent != updated.isParent) add("isParent")
            if (original.parentId != updated.parentId) add("parent_id")
            if (original.tombstone != updated.tombstone) add("tombstone")
        }
    }
}
