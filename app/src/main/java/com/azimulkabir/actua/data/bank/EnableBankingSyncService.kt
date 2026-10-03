package com.azimulkabir.actua.data.bank

import android.content.Context
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.model.ActualBankSyncAccount
import com.azimulkabir.actua.data.budget.model.ActualPayee
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.network.ActualServerClient
import com.azimulkabir.actua.data.network.ActualServerException
import com.azimulkabir.actua.data.network.TrustedCertificateStore
import com.azimulkabir.actua.data.network.UrlConnectionTransport
import com.azimulkabir.actua.data.security.CredentialStore
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs

data class BankSyncResult(
    val accountsAttempted: Int,
    val accountsSucceeded: Int,
    val added: Int,
    val updated: Int,
    val failedAccounts: List<String>,
)

class BankSyncUnavailableException(message: String) : IllegalStateException(message)

/**
 * Ports Actual's existing-account Enable Banking download and reconciliation path.
 * Linking/re-authorizing accounts remains owned by the web app; credentials stay server-side.
 */
class EnableBankingSyncService(
    context: Context,
    private val database: ActualBudgetDatabase,
    private val writer: ActualTransactionWriter,
) {
    private val appContext = context.applicationContext
    private val credentials = CredentialStore(appContext)
    private val client = ActualServerClient(
        UrlConnectionTransport(TrustedCertificateStore(appContext)),
    ).apply {
        customHeaders = credentials.customHeaders
    }

    fun sync(): BankSyncResult {
        val accounts = database.fetchEnableBankingAccounts()
        if (accounts.isEmpty()) {
            throw BankSyncUnavailableException(
                "No Enable Banking account is linked in this budget.",
            )
        }
        val token = credentials.token()
            ?: throw BankSyncUnavailableException("Sign in to the Actual server before syncing banks.")
        val primaryUrl = credentials.serverUrl.takeIf(String::isNotBlank)
            ?: throw BankSyncUnavailableException("Configure an Actual server before syncing banks.")
        val fallbackUrl = credentials.fallbackServerUrl
            .takeIf { it.isNotBlank() && it != primaryUrl }

        val updates = mutableListOf<Pair<ActualTransaction, ActualTransaction>>()
        val inserts = mutableListOf<ActualTransaction>()
        val plannedPayees = linkedMapOf<String, ActualPayee>()
        val accountBalances = mutableMapOf<String, Long>()
        val accountLastSync = mutableMapOf<String, String>()
        val accountSyncStatuses = mutableMapOf<String, String>()
        val failures = mutableListOf<String>()
        var succeeded = 0

        accounts.forEach { account ->
            runCatching {
                val existing = database.fetchTransactions(
                    accountId = account.id,
                    limit = Int.MAX_VALUE,
                )
                val startDate = syncStartDate(existing)
                val response = try {
                    download(primaryUrl, token, account, startDate)
                } catch (primary: Exception) {
                    fallbackUrl?.let { download(it, token, account, startDate) } ?: throw primary
                }
                val download = parseTransactions(account, response)
                val accountUpdates = mutableListOf<Pair<ActualTransaction, ActualTransaction>>()
                val accountInserts = mutableListOf<ActualTransaction>()
                val accountPayees = plannedPayees.toMutableMap()
                reconcile(
                    account,
                    existing,
                    download.transactions,
                    download.currentBalance,
                    accountUpdates,
                    accountInserts,
                    accountPayees,
                )
                AccountPlan(
                    accountUpdates,
                    accountInserts,
                    accountPayees,
                    download.currentBalance,
                )
            }.onSuccess { plan ->
                updates += plan.updates
                inserts += plan.inserts
                plannedPayees.putAll(plan.payees)
                plan.currentBalance?.let { accountBalances[account.id] = it }
                accountLastSync[account.id] = System.currentTimeMillis().toString()
                accountSyncStatuses[account.id] = "ok"
                succeeded += 1
            }.onFailure { error ->
                failures += when (error) {
                    ActualServerException.Unauthorized ->
                        "${account.name}: server authentication failed"
                    is ActualServerException.BankSync ->
                        "${account.name}: ${error.category}/${error.code}"
                    is ActualServerException.Http ->
                        "${account.name}: server HTTP ${error.status}"
                    else -> "${account.name}: ${error.message ?: "bank sync failed"}"
                }
            }
        }

        val usedPayeeIds = (inserts.asSequence() + updates.asSequence().map { it.second })
            .mapNotNull { it.payeeId }
            .toSet()
        val newPayees = plannedPayees.values.filter { it.id in usedPayeeIds }
        if (updates.isNotEmpty() || inserts.isNotEmpty() ||
            newPayees.isNotEmpty() || accountBalances.isNotEmpty() ||
            accountLastSync.isNotEmpty() || accountSyncStatuses.isNotEmpty()
        ) {
            writer.mutate(
                updates = updates,
                inserts = inserts,
                newPayees = newPayees,
                accountBalances = accountBalances,
                accountLastSync = accountLastSync,
                accountSyncStatuses = accountSyncStatuses,
            )
        }
        if (succeeded == 0) {
            throw BankSyncUnavailableException(
                "Enable Banking could not synchronize ${failures.joinToString()}.",
            )
        }
        return BankSyncResult(
            accountsAttempted = accounts.size,
            accountsSucceeded = succeeded,
            added = inserts.size,
            updated = updates.size,
            failedAccounts = failures,
        )
    }

    private fun download(
        serverUrl: String,
        token: String,
        account: ActualBankSyncAccount,
        startDate: String,
    ): JSONObject = client.downloadEnableBankingTransactions(
        serverUrl = serverUrl,
        token = token,
        accountId = account.externalAccountId,
        startDate = startDate,
        bankName = account.bankName,
    )

    private fun parseTransactions(
        account: ActualBankSyncAccount,
        response: JSONObject,
    ): ParsedDownload {
        val rows = response.optJSONObject("transactions")?.optJSONArray("all")
            ?: throw IllegalStateException("Enable Banking returned no transaction list.")
        val importPending = preferenceFlag("sync-import-pending-${account.id}", default = true)
        val importNotes = preferenceFlag("sync-import-notes-${account.id}", default = true)
        val customMappings = database.fetchPreference("custom-sync-mappings-${account.id}")
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
        val transactions = rows.objects().mapNotNull { raw ->
            val booked = raw.optBoolean("booked", false)
            if (!booked && !importPending) return@mapNotNull null
            val amountValue = raw.opt("amount")
                ?: raw.optJSONObject("transactionAmount")?.opt("amount")
                ?: throw IllegalStateException("A bank transaction has no amount.")
            val amountCents = decimalCents(amountValue)
            val directionMappings = customMappings?.optJSONObject(
                if (amountCents <= 0L) "payment" else "deposit",
            )
            val date = parseDate(
                raw.optString(directionMappings?.optString("date", "date") ?: "date"),
            )
            val payeeName = raw.optString(
                directionMappings?.optString("payee", "payeeName") ?: "payeeName",
            ).trim()
                .takeIf(String::isNotEmpty)
                ?: throw IllegalStateException("A bank transaction has no payee.")
            val transactionId = raw.optString("transactionId").takeIf(String::isNotBlank)
            val importedId = transactionId ?: if (booked) {
                raw.optString("internalTransactionId").takeIf(String::isNotBlank)?.let { internal ->
                    "${raw.optString("account", account.externalAccountId)}-$internal"
                }
            } else null
            ImportedBankTransaction(
                date = date,
                amountCents = amountCents,
                payeeName = payeeName,
                notes = raw.optString(
                    directionMappings?.optString("notes", "notes") ?: "notes",
                ).trim().takeIf { importNotes && it.isNotEmpty() }
                    ?.replace("#", "##"),
                cleared = booked,
                importedId = importedId,
                rawData = raw.toString(),
            )
        }.toList()
        val currentBalance = response.opt("startingBalance")
            ?.takeUnless { it == JSONObject.NULL }
            ?.let(::integerAmount)
        return ParsedDownload(transactions, currentBalance)
    }

    private fun reconcile(
        account: ActualBankSyncAccount,
        existing: List<ActualTransaction>,
        imported: List<ImportedBankTransaction>,
        currentBalance: Long?,
        updates: MutableList<Pair<ActualTransaction, ActualTransaction>>,
        inserts: MutableList<ActualTransaction>,
        plannedPayees: MutableMap<String, ActualPayee>,
    ) {
        val available = existing.toMutableList()
        val matchedIds = mutableSetOf<String>()
        val seenImportedIds = mutableSetOf<String>()
        val updateDates = preferenceFlag("sync-update-dates-${account.id}", default = false)

        val today = LocalDate.now().format(ACTUAL_DATE).toInt()
        if (existing.none { it.date <= today } && currentBalance != null) {
            val startingBalance = currentBalance - imported.sumOf { it.amountCents }
            if (startingBalance != 0L) {
                val accountRow = database.fetchAccounts().first { it.id == account.id }
                val categoryId = if (accountRow.offBudget) null else {
                    database.fetchCategoryGroups()
                        .flatMap { it.categories }
                        .filter { it.isIncome }
                        .let { categories ->
                            categories.firstOrNull { it.name.equals("Starting Balances", true) }
                                ?: categories.firstOrNull()
                        }
                        ?.id
                }
                inserts += ActualTransaction(
                    id = UUID.randomUUID().toString().lowercase(),
                    accountId = account.id,
                    date = imported.minOfOrNull { it.date }
                        ?: today,
                    amountCents = startingBalance,
                    payeeId = writer.planPayee("Starting Balance", plannedPayees).id,
                    payeeName = null,
                    categoryId = categoryId,
                    categoryName = null,
                    notes = null,
                    cleared = true,
                    reconciled = false,
                    transferId = null,
                    isParent = false,
                    parentId = null,
                    tombstone = false,
                    sortOrder = System.currentTimeMillis().toDouble() + 1,
                    importedPayee = null,
                    scheduleId = null,
                    transferAccountId = null,
                    startingBalance = true,
                )
            }
        }

        imported.forEachIndexed { index, incoming ->
            if (incoming.importedId != null && !seenImportedIds.add(incoming.importedId)) {
                return@forEachIndexed
            }
            val knownPayeeId = database.findPayeeByName(incoming.payeeName)?.id
                ?: plannedPayees[incoming.payeeName.lowercase()]?.id
            val exact = incoming.importedId?.let { importedId ->
                available.firstOrNull { it.importedId == importedId && it.id !in matchedIds }
            }
            val fuzzy = if (exact == null) {
                available.asSequence()
                    .filter {
                        it.id !in matchedIds &&
                            it.amountCents == incoming.amountCents &&
                            dateDistance(it.date, incoming.date) <= 7
                    }
                    .sortedWith(
                        compareBy<ActualTransaction>(
                            { dateDistance(it.date, incoming.date) },
                            { if (it.importedId == null) 0 else 1 },
                        ),
                    )
                    .toList()
                    .let { candidates ->
                        candidates.firstOrNull { knownPayeeId != null && it.payeeId == knownPayeeId }
                            ?: candidates.firstOrNull()
                    }
            } else null
            val match = exact ?: fuzzy

            if (match != null) {
                matchedIds += match.id
                if (!match.reconciled) {
                    val payeeId = match.payeeId
                        ?: knownPayeeId
                        ?: writer.planPayee(incoming.payeeName, plannedPayees).id
                    val prepared = writer.prepareImportedTransaction(
                        incoming.toActualTransaction(
                            accountId = account.id,
                            id = match.id,
                            payeeId = payeeId,
                            sortOrder = match.sortOrder,
                        ),
                        plannedPayees,
                    ) ?: return@forEachIndexed
                    val updated = match.copy(
                        date = if (updateDates) incoming.date else match.date,
                        payeeId = match.payeeId ?: prepared.payeeId,
                        categoryId = match.categoryId ?: prepared.categoryId,
                        notes = match.notes ?: prepared.notes,
                        cleared = match.cleared || incoming.cleared,
                        importedPayee = incoming.payeeName,
                        importedId = incoming.importedId,
                        rawSyncedData = match.rawSyncedData ?: incoming.rawData,
                    )
                    if (updated != match) {
                        updates += match to updated
                        if (match.isParent &&
                            (updated.cleared != match.cleared || updated.date != match.date)
                        ) {
                            database.fetchChildTransactions(match.id).forEach { child ->
                                updates += child to child.copy(
                                    cleared = if (updated.cleared != match.cleared) {
                                        updated.cleared
                                    } else child.cleared,
                                    date = if (updated.date != match.date) updated.date else child.date,
                                )
                            }
                        }
                    }
                }
            } else {
                val payeeId = knownPayeeId
                    ?: writer.planPayee(incoming.payeeName, plannedPayees).id
                writer.prepareImportedTransaction(
                    incoming.toActualTransaction(
                        accountId = account.id,
                        id = UUID.randomUUID().toString().lowercase(),
                        payeeId = payeeId,
                        sortOrder = System.currentTimeMillis().toDouble() - index,
                    ),
                    plannedPayees,
                )?.let(inserts::add)
            }
        }
    }

    private fun preferenceFlag(id: String, default: Boolean): Boolean =
        database.fetchPreference(id)?.toBooleanStrictOrNull() ?: default

    private fun syncStartDate(existing: List<ActualTransaction>): String {
        val today = LocalDate.now()
        val earliestAllowed = today.minusDays(89)
        val oldest = existing.asSequence()
            .map { actualDate(it.date) }
            .filter { !it.isAfter(today) }
            .minOrNull()
        return maxOf(earliestAllowed, oldest ?: earliestAllowed).toString()
    }

    private fun parseDate(value: String): Int {
        val date = runCatching { LocalDate.parse(value.take(10)) }
            .getOrElse { throw IllegalStateException("Enable Banking returned an invalid date.") }
        return date.format(ACTUAL_DATE).toInt()
    }

    private fun actualDate(value: Int): LocalDate =
        LocalDate.parse(value.toString().padStart(8, '0'), ACTUAL_DATE)

    private fun dateDistance(left: Int, right: Int): Long =
        abs(ChronoUnit.DAYS.between(actualDate(left), actualDate(right)))

    private fun decimalCents(value: Any): Long = runCatching {
        BigDecimal(value.toString())
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrElse {
        throw IllegalStateException("Enable Banking returned an invalid amount.")
    }

    private fun integerAmount(value: Any): Long = runCatching {
        BigDecimal(value.toString()).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrElse {
        throw IllegalStateException("Enable Banking returned an invalid account balance.")
    }

    private fun JSONArray.objects(): Sequence<JSONObject> = sequence {
        for (index in 0 until length()) yield(getJSONObject(index))
    }

    private data class ImportedBankTransaction(
        val date: Int,
        val amountCents: Long,
        val payeeName: String,
        val notes: String?,
        val cleared: Boolean,
        val importedId: String?,
        val rawData: String,
    ) {
        fun toActualTransaction(
            accountId: String,
            id: String,
            payeeId: String,
            sortOrder: Double?,
        ) = ActualTransaction(
            id = id,
            accountId = accountId,
            date = date,
            amountCents = amountCents,
            payeeId = payeeId,
            payeeName = null,
            categoryId = null,
            categoryName = null,
            notes = notes,
            cleared = cleared,
            reconciled = false,
            transferId = null,
            isParent = false,
            parentId = null,
            tombstone = false,
            sortOrder = sortOrder,
            importedPayee = payeeName,
            scheduleId = null,
            transferAccountId = null,
            importedId = importedId,
            rawSyncedData = rawData,
        )
    }

    private data class ParsedDownload(
        val transactions: List<ImportedBankTransaction>,
        val currentBalance: Long?,
    )

    private data class AccountPlan(
        val updates: List<Pair<ActualTransaction, ActualTransaction>>,
        val inserts: List<ActualTransaction>,
        val payees: Map<String, ActualPayee>,
        val currentBalance: Long?,
    )

    private companion object {
        val ACTUAL_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }
}
