package com.azimulkabir.actua.data.budget

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import com.azimulkabir.actua.data.budget.model.ActualCategory
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import com.azimulkabir.actua.data.budget.model.ActualCleanupGroup
import com.azimulkabir.actua.data.budget.model.ActualPayee
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.model.TransactionStatusFilter
import com.azimulkabir.actua.data.budget.model.ActualBudgetMonth
import com.azimulkabir.actua.data.budget.model.ActualCategoryBudget
import com.azimulkabir.actua.data.budget.model.ActualIncomeBudget
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.location.Coordinates
import com.azimulkabir.actua.data.location.LocationUtils
import com.azimulkabir.actua.data.location.NearbyPayee
import com.azimulkabir.actua.data.location.PayeeLocation
import com.azimulkabir.actua.data.reports.DashboardPageRow
import com.azimulkabir.actua.data.reports.DashboardWidgetRow
import com.azimulkabir.actua.data.rules.RuleContext
import com.azimulkabir.actua.data.schedules.ActualSchedule
import com.azimulkabir.actua.data.schedules.ActualScheduleSummary
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.schedules.ScheduleConditions
import com.azimulkabir.actua.data.schedules.ScheduleDateCondition
import com.azimulkabir.actua.data.schedules.ScheduledAmount
import com.azimulkabir.actua.data.schedules.ScheduleAmountOp
import com.azimulkabir.actua.data.schedules.ScheduleStatusCalculator
import com.azimulkabir.actua.data.schedules.ScheduleDiscovery
import org.json.JSONArray
import com.azimulkabir.actua.data.sync.MerkleTree
import com.azimulkabir.actua.data.sync.MerkleJson
import com.azimulkabir.actua.data.sync.MerkleNode
import com.azimulkabir.actua.data.sync.MurmurHash3
import org.json.JSONObject
import com.azimulkabir.actua.model.CreditCardConfig
import com.azimulkabir.actua.model.CreditCardCycle
import java.io.Closeable
import java.io.File

/** A thin boundary around an Actual budget's own db.sqlite file. */
class ActualBudgetDatabase private constructor(
    private val database: SQLiteDatabase,
) : Closeable {
    override fun close() = database.close()

    /** Live accounts and their Actual-compatible, split-aware balances. */
    @Synchronized
    fun fetchAccounts(): List<ActualAccount> {
        data class Balances(val total: Long, val cleared: Long, val reconciled: Long)
        val balances = mutableMapOf<String, Balances>()
        database.rawQuery(
            """
                SELECT t.acct, COALESCE(SUM(t.amount), 0),
                       COALESCE(SUM(CASE WHEN t.cleared = 1 THEN t.amount ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN t.reconciled = 1 THEN t.amount ELSE 0 END), 0)
                FROM transactions t
                LEFT JOIN transactions p ON p.id = t.parent_id
                WHERE t.acct IS NOT NULL AND t.date IS NOT NULL
                  AND (t.tombstone = 0 OR t.tombstone IS NULL)
                  AND (t.isChild = 0 OR t.isChild IS NULL OR
                       (p.id IS NOT NULL AND (p.tombstone = 0 OR p.tombstone IS NULL)))
                  AND (t.isParent = 0 OR t.isParent IS NULL)
                GROUP BY t.acct
            """.trimIndent(), null,
        ).use { cursor ->
            while (cursor.moveToNext()) balances[cursor.getString(0)] =
                Balances(cursor.getLong(1), cursor.getLong(2), cursor.getLong(3))
        }

        val result = mutableListOf<ActualAccount>()
        database.rawQuery(
            """
                SELECT id, name, type, offbudget, closed, sort_order
                FROM accounts
                WHERE tombstone = 0 OR tombstone IS NULL
                ORDER BY sort_order ASC
            """.trimIndent(), null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                result += ActualAccount(
                    id = id,
                    name = cursor.stringOrNull(1).orEmpty(),
                    type = ActualAccountType.fromDatabase(cursor.stringOrNull(2)),
                    offBudget = cursor.intOrZero(3) == 1,
                    closed = cursor.intOrZero(4) == 1,
                    sortOrder = cursor.doubleOrZero(5).toInt(),
                    balanceCents = balances[id]?.total ?: 0,
                    clearedCents = balances[id]?.cleared ?: 0,
                    unclearedCents = (balances[id]?.total ?: 0) - (balances[id]?.cleared ?: 0),
                    reconciledCents = balances[id]?.reconciled ?: 0,
                )
            }
        }
        return result
    }

    @Synchronized
    fun fetchNote(id: String): String {
        if (!hasTable("notes")) return ""
        return database.rawQuery("SELECT note FROM notes WHERE id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) cursor.stringOrNull(0).orEmpty() else ""
        }
    }

    /**
     * Batched [fetchNote]: a single query (and a single `hasTable` check) instead of one query
     * per id. Callers that need notes for many categories/accounts at once — e.g. building the
     * whole budget for a month — should use this rather than calling [fetchNote] in a loop,
     * which was previously N+1 round-trips run synchronously during Compose composition.
     */
    @Synchronized
    fun fetchNotes(ids: Collection<String>): Map<String, String> {
        if (ids.isEmpty() || !hasTable("notes")) return emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        return database.rawQuery(
            "SELECT id, note FROM notes WHERE id IN ($placeholders)",
            ids.toTypedArray(),
        ).use { cursor ->
            val result = mutableMapOf<String, String>()
            while (cursor.moveToNext()) {
                result[cursor.getString(0)] = cursor.stringOrNull(1).orEmpty()
            }
            result
        }
    }

    /** Actuali's cross-platform card metadata stored in Actual's synced preferences table. */
    @Synchronized
    fun fetchCreditCardConfigs(): Map<String, CreditCardConfig> {
        if (!hasTable("preferences")) return emptyMap()
        val prefix = CREDIT_CARD_PREFERENCE_PREFIX
        val result = linkedMapOf<String, CreditCardConfig>()
        database.rawQuery(
            "SELECT id, value FROM preferences WHERE id LIKE ? AND value IS NOT NULL",
            arrayOf("$prefix%"),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.stringOrNull(0) ?: continue
                val value = cursor.stringOrNull(1) ?: continue
                runCatching {
                    val json = JSONObject(value)
                    val day = json.getInt("statementDay")
                    val offset = json.optInt("dueOffsetDays", CreditCardCycle.DEFAULT_DUE_OFFSET_DAYS)
                    val dueDay = if (json.has("dueDay") && !json.isNull("dueDay")) {
                        json.getInt("dueDay").also { require(it in 1..31) }
                    } else null
                    val limit = if (json.has("limit") && !json.isNull("limit")) json.getLong("limit") else null
                    CreditCardConfig(day, offset, limit, dueDay)
                }.getOrNull()?.let { result[id.removePrefix(prefix)] = it }
            }
        }
        return result
    }

    /** Positive total of charges/debits in an inclusive billing-cycle window. */
    @Synchronized
    fun fetchAccountSpend(accountId: String, fromDate: Int, toDate: Int): Long = database.rawQuery(
        """
            SELECT COALESCE(SUM(CASE WHEN t.amount < 0 THEN -t.amount ELSE 0 END), 0)
            FROM transactions t
            LEFT JOIN transactions p ON p.id = t.parent_id
            WHERE t.acct = ? AND t.date >= ? AND t.date <= ?
              AND (t.tombstone = 0 OR t.tombstone IS NULL)
              AND (t.isChild = 0 OR t.isChild IS NULL OR
                   (p.id IS NOT NULL AND (p.tombstone = 0 OR p.tombstone IS NULL)))
              AND (t.isParent = 0 OR t.isParent IS NULL)
        """.trimIndent(), arrayOf(accountId, fromDate.toString(), toDate.toString()),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }

    /** Statement records for the given closed cycles on a credit card account, newest first;
     * cycles with no recorded transactions and zero statement balance are excluded. */
    @Synchronized
    fun fetchRecentStatements(
        accountId: String, cycles: List<CreditCardCycle.StatementCycle>, liveBalanceCents: Long,
    ): List<CreditCardCycle.StatementRecord> {
        val records = mutableListOf<CreditCardCycle.StatementRecord>()
        for (cycle in cycles) {
            val (statementRawBalance, paymentsSince, totalSpend, transactionCount) = database.rawQuery(
                """
                    SELECT
                        COALESCE(SUM(CASE WHEN t.date <= ? THEN t.amount ELSE 0 END), 0),
                        COALESCE(SUM(CASE WHEN t.date > ? AND t.amount > 0 THEN t.amount ELSE 0 END), 0),
                        COALESCE(SUM(CASE WHEN t.date >= ? AND t.date <= ? AND t.amount < 0 THEN -t.amount ELSE 0 END), 0),
                        COUNT(CASE WHEN t.date >= ? AND t.date <= ? THEN 1 ELSE NULL END)
                    FROM transactions t
                    LEFT JOIN transactions p ON p.id = t.parent_id
                    WHERE t.acct = ? AND t.date IS NOT NULL
                      AND (t.tombstone = 0 OR t.tombstone IS NULL)
                      AND (t.isChild = 0 OR t.isChild IS NULL OR
                           (p.id IS NOT NULL AND (p.tombstone = 0 OR p.tombstone IS NULL)))
                      AND (t.isParent = 0 OR t.isParent IS NULL)
                """.trimIndent(),
                arrayOf(
                    cycle.end.yyyymmdd.toString(), cycle.end.yyyymmdd.toString(),
                    cycle.start.yyyymmdd.toString(), cycle.end.yyyymmdd.toString(),
                    cycle.start.yyyymmdd.toString(), cycle.end.yyyymmdd.toString(),
                    accountId,
                ),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    listOf(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getLong(3))
                } else listOf(0L, 0L, 0L, 0L)
            }

            val due = CreditCardCycle.calculateStatementDue(statementRawBalance, paymentsSince, liveBalanceCents, cycle.dueDate)
            if (transactionCount > 0 || due.statementBalance > 0) {
                records += CreditCardCycle.StatementRecord(
                    startDate = cycle.start, endDate = cycle.end, dueDate = cycle.dueDate,
                    statementBalance = due.statementBalance, paymentsSince = due.paymentsSince,
                    remainingDue = due.remainingDue, totalSpend = totalSpend,
                )
            }
        }
        return records
    }

    @Synchronized
    fun fetchPayees(): List<ActualPayee> {
        val result = mutableListOf<ActualPayee>()
        database.rawQuery(
            """SELECT id, name, transfer_acct FROM payees
                WHERE tombstone = 0 OR tombstone IS NULL ORDER BY name COLLATE NOCASE ASC""",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) result += ActualPayee(
                cursor.getString(0), cursor.stringOrNull(1).orEmpty(), cursor.stringOrNull(2),
            )
        }
        return result
    }

    @Synchronized
    fun findPayeeByName(name: String): ActualPayee? = database.rawQuery(
        """SELECT id, name, transfer_acct FROM payees
            WHERE UPPER(name) = UPPER(?) AND (tombstone = 0 OR tombstone IS NULL)
            LIMIT 1""",
        arrayOf(name),
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else ActualPayee(
            cursor.getString(0), cursor.stringOrNull(1).orEmpty(), cursor.stringOrNull(2),
        )
    }

    /** Complete, non-tombstoned rows only; partial CRDT rows remain safely invisible. */
    @Synchronized
    fun payeeLocationWritesSupported(): Boolean {
        val required = setOf("id", "payee_id", "latitude", "longitude", "created_at", "tombstone")
        if (!hasTable("payee_locations") || !hasTable("messages_crdt")) return false
        val locationColumns = mutableSetOf<String>()
        database.rawQuery("PRAGMA table_info(payee_locations)", null).use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) locationColumns += cursor.getString(name)
        }
        val messageColumns = mutableSetOf<String>()
        database.rawQuery("PRAGMA table_info(messages_crdt)", null).use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) messageColumns += cursor.getString(name)
        }
        return required.all(locationColumns::contains) &&
            setOf("timestamp", "dataset", "row", "column", "value").all(messageColumns::contains)
    }

    fun fetchPayeeLocations(payeeId: String? = null): List<PayeeLocation> {
        if (!hasTable("payee_locations")) return emptyList()
        val where = if (payeeId == null) "" else " AND payee_id = ?"
        return database.rawQuery(
            """SELECT id, payee_id, latitude, longitude, created_at, tombstone
                FROM payee_locations
                WHERE (tombstone = 0 OR tombstone IS NULL)
                  AND payee_id IS NOT NULL AND latitude IS NOT NULL
                  AND longitude IS NOT NULL AND created_at IS NOT NULL$where
                ORDER BY created_at DESC""",
            payeeId?.let { arrayOf(it) },
        ).use { cursor -> buildList {
            while (cursor.moveToNext()) {
                val latitude = cursor.getDouble(2)
                val longitude = cursor.getDouble(3)
                if (!LocationUtils.isValid(latitude, longitude)) continue
                add(PayeeLocation(cursor.getString(0), cursor.getString(1), latitude, longitude,
                    cursor.getLong(4), cursor.intOrZero(5) == 1))
            }
        } }
    }

    /** Closest saved location per ordinary payee, ranked within Actual's default radius. */
    @Synchronized
    fun fetchNearbyPayees(
        coordinates: Coordinates,
        maxDistanceMeters: Double = LocationUtils.DEFAULT_MAX_DISTANCE_METERS,
    ): List<NearbyPayee> {
        require(maxDistanceMeters.isFinite() && maxDistanceMeters > 0) { "Maximum distance must be positive" }
        val payees = fetchPayees().filter { it.transferAccountId == null }.associateBy(ActualPayee::id)
        return fetchPayeeLocations().mapNotNull { location ->
            val payee = payees[location.payeeId] ?: return@mapNotNull null
            val distance = LocationUtils.distanceMeters(
                coordinates, Coordinates(location.latitude, location.longitude),
            )
            if (distance <= maxDistanceMeters) NearbyPayee(payee, location, distance) else null
        }.groupBy { it.payee.id }
            .mapNotNull { (_, matches) -> matches.minByOrNull(NearbyPayee::distanceMeters) }
            .sortedBy(NearbyPayee::distanceMeters)
            .take(10)
    }

    @Synchronized
    fun fetchRules(): List<Rule> {
        if (!hasTable("rules")) return emptyList()
        val result = mutableListOf<Rule>()
        database.rawQuery(
            """SELECT id, stage, conditions_op, conditions, actions FROM rules
                WHERE tombstone = 0 OR tombstone IS NULL""", null,
        ).use { cursor ->
            while (cursor.moveToNext()) runCatching {
                Rule.parse(cursor.getString(0), cursor.stringOrNull(1), cursor.stringOrNull(2),
                    cursor.stringOrNull(3), cursor.stringOrNull(4))
            }.getOrNull()?.let(result::add)
        }
        return result
    }

    @Synchronized
    fun rulesSupported(): Boolean = hasTable("rules")

    @Synchronized
    fun scheduleOwnedRuleIds(): Set<String> {
        if (!hasTable("schedules")) return emptySet()
        val result = mutableSetOf<String>()
        database.rawQuery(
            """SELECT rule FROM schedules WHERE rule IS NOT NULL
                AND (tombstone = 0 OR tombstone IS NULL)""", null,
        ).use { cursor -> while (cursor.moveToNext()) cursor.stringOrNull(0)?.let(result::add) }
        return result
    }

    @Synchronized
    fun ruleContext(): RuleContext {
        val accounts = fetchAccounts()
        val groups = fetchCategoryGroups()
        return RuleContext(
            offBudgetAccountIds = accounts.filter { it.offBudget }.mapTo(mutableSetOf()) { it.id },
            accountNames = accounts.associate { it.id to it.name },
            categoryNames = groups.flatMap { it.categories }.associate { it.id to it.name },
            categoryGroupIds = groups.flatMap { group -> group.categories.map { it.id to group.id } }.toMap(),
            categoryGroupNames = groups.associate { it.id to it.name },
            payeeNames = fetchPayees().associate { it.id to it.name },
        )
    }

    @Synchronized
    fun latestTransactionDate(accountId: String): DayDate? = database.rawQuery(
        """SELECT date FROM transactions WHERE acct=?
            AND (tombstone=0 OR tombstone IS NULL) AND parent_id IS NULL
            ORDER BY date DESC LIMIT 1""", arrayOf(accountId),
    ).use { cursor -> if (cursor.moveToFirst()) DayDate.fromYyyymmdd(cursor.getInt(0)) else null }

    @Synchronized
    fun fetchDiscoveryTransactions(accountId: String, notBefore: Int): List<ScheduleDiscovery.Candidate> {
        if (!hasTable("payee_mapping") || !hasTable("payees")) return emptyList()
        val result = mutableListOf<ScheduleDiscovery.Candidate>()
        database.rawQuery(
            """SELECT t.id,t.date,t.amount,pm.targetId
                FROM transactions t JOIN payee_mapping pm ON pm.id=t.description
                LEFT JOIN payees p ON p.id=pm.targetId
                WHERE t.acct=? AND t.date>=?
                  AND (t.tombstone=0 OR t.tombstone IS NULL)
                  AND t.schedule IS NULL
                  AND (t.isChild=0 OR t.isChild IS NULL)
                  AND t.transferred_id IS NULL AND p.transfer_acct IS NULL
                ORDER BY t.date ASC""", arrayOf(accountId, notBefore.toString()),
        ).use { cursor -> while (cursor.moveToNext()) {
            val date = DayDate.fromYyyymmdd(cursor.getInt(1)) ?: continue
            result += ScheduleDiscovery.Candidate(cursor.getString(0), date, cursor.getLong(2),
                cursor.getString(3), accountId)
        } }
        return result
    }

    fun discoverSchedules(): List<ScheduleDiscovery.Proposal> = ScheduleDiscovery.discover(
        fetchAccounts(), ::fetchDiscoveryTransactions, ::latestTransactionDate,
    )

    @Synchronized
    fun writeScheduleJsonPaths(scheduleId: String, conditions: JSONArray) {
        if (!hasTable("schedules_json_paths")) return
        val indices = ScheduleConditions.jsonPaths(conditions)
        fun path(index: Int?) = index?.let { "$$[$it]" }
        val values = ContentValues().apply {
            put("schedule_id", scheduleId)
            put("payee", path(indices.payee)); put("account", path(indices.account))
            put("amount", path(indices.amount)); put("date", path(indices.date))
        }
        database.insertWithOnConflict("schedules_json_paths", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Actual v_schedules-compatible projection used by automatic posting and forecasts. */
    @Synchronized
    fun fetchSchedules(postableOnly: Boolean = true): List<ActualSchedule> {
        if (!hasTable("schedules") || !hasTable("schedules_next_date") || !hasTable("rules")) return emptyList()
        val closed = fetchAccounts().filter { it.closed }.mapTo(mutableSetOf()) { it.id }
        val seen = mutableSetOf<String>(); val result = mutableListOf<ActualSchedule>()
        val postFilter = if (postableOnly) "AND (s.posts_transaction = 1)" else ""
        database.rawQuery(
            """SELECT s.id,s.name,nd.id,nd.local_next_date,nd.local_next_date_ts,
                       nd.base_next_date,nd.base_next_date_ts,r.conditions,r.actions
                FROM schedules s JOIN schedules_next_date nd ON nd.schedule_id=s.id
                JOIN rules r ON r.id=s.rule
                WHERE (s.tombstone=0 OR s.tombstone IS NULL)
                  AND (s.completed=0 OR s.completed IS NULL) $postFilter
                  AND (r.tombstone=0 OR r.tombstone IS NULL)
                ORDER BY s.id,nd.id""", null,
        ).use { cursor -> while (cursor.moveToNext()) {
            val id = cursor.getString(0); if (!seen.add(id)) continue
            val conditions = runCatching { JSONArray(cursor.stringOrNull(7)) }.getOrNull() ?: continue
            fun first(ops: Set<String>, fields: List<String>): org.json.JSONObject? {
                fields.forEach { field -> for (index in 0 until conditions.length()) {
                    val row = conditions.optJSONObject(index) ?: continue
                    if (row.optString("op") in ops && row.optString("field") == field) return row
                } }; return null
            }
            val account = first(setOf("is"), listOf("account", "acct"))?.optString("value")
                ?.takeIf(String::isNotBlank) ?: continue
            if (account in closed) continue
            val localTs = cursor.longOrNull(4); val baseTs = cursor.longOrNull(6)
            val effective = if (localTs != null && localTs == baseTs) cursor.intOrNull(3) else cursor.intOrNull(5)
            val next = effective?.let(DayDate::fromYyyymmdd) ?: continue
            val rawPayee = first(setOf("is"), listOf("payee", "description"))?.optString("value")?.takeIf(String::isNotBlank)
            val payee = rawPayee?.let { raw -> database.rawQuery("SELECT targetId FROM payee_mapping WHERE id=?", arrayOf(raw)).use { c -> if (c.moveToFirst()) c.stringOrNull(0) else null } }
            val amountValue = first(setOf("is", "isapprox", "isbetween"), listOf("amount"))?.opt("value")
            val amount = when (amountValue) {
                is Number -> ScheduledAmount.Fixed(amountValue.toLong())
                is org.json.JSONObject -> if (amountValue.has("num1") && amountValue.has("num2")) ScheduledAmount.Range(amountValue.getLong("num1"), amountValue.getLong("num2")) else null
                else -> null
            }
            val dateValue = first(setOf("is", "isapprox"), listOf("date"))?.opt("value")
            val actions = runCatching { JSONArray(cursor.stringOrNull(8)) }.getOrNull()
            var category: String? = null
            if (actions != null) for (index in 0 until actions.length()) actions.optJSONObject(index)?.let {
                if (category == null && it.optString("op") == "set" && it.optString("field") == "category") category = it.optString("value").takeIf(String::isNotBlank)
            }
            result += ActualSchedule(id, cursor.stringOrNull(1), next, cursor.getString(2), baseTs,
                account, payee, category, amount, ScheduleConditions.dateCondition(dateValue))
        } }
        return result
    }

    /**
     * Whether a posted transaction already exists for the schedule occurrence bounded by
     * [onOrAfter]..[onOrBefore] (inclusive), matching upstream's occurrence-scoped
     * `isScheduleOccurrencePosted` window rather than an unbounded "any transaction since" check.
     */
    @Synchronized
    fun hasScheduleTransaction(scheduleId: String, onOrAfter: Int, onOrBefore: Int = onOrAfter): Boolean = database.rawQuery(
        """SELECT EXISTS(SELECT 1 FROM transactions WHERE schedule=? AND date>=? AND date<=?
            AND (tombstone=0 OR tombstone IS NULL))""", arrayOf(scheduleId, onOrAfter.toString(), onOrBefore.toString()),
    ).use { it.moveToFirst() && it.getInt(0) != 0 }

    /** Inclusive schedule-list projection: malformed linked rows remain visible and repairable. */
    @Synchronized
    fun fetchScheduleSummaries(): List<ActualScheduleSummary> {
        if (!hasTable("schedules") || !hasTable("schedules_next_date") || !hasTable("rules")) return emptyList()
        val seen = mutableSetOf<String>(); val result = mutableListOf<ActualScheduleSummary>()
        database.rawQuery(
            """SELECT s.id,s.name,s.rule,s.posts_transaction,s.completed,s.custom_upcoming_length,s.sort_order,
                       nd.id,nd.local_next_date,nd.local_next_date_ts,nd.base_next_date,nd.base_next_date_ts,
                       r.id,r.conditions,r.actions
                FROM schedules s LEFT JOIN schedules_next_date nd ON nd.schedule_id=s.id
                LEFT JOIN rules r ON r.id=s.rule AND (r.tombstone=0 OR r.tombstone IS NULL)
                WHERE s.tombstone=0 OR s.tombstone IS NULL ORDER BY s.id,nd.id""", null,
        ).use { cursor -> while (cursor.moveToNext()) {
            val id = cursor.getString(0); if (!seen.add(id)) continue
            val conditionsJson = cursor.stringOrNull(13); val actionsJson = cursor.stringOrNull(14)
            val conditions = runCatching { JSONArray(conditionsJson ?: "[]") }.getOrElse { JSONArray() }
            val actions = runCatching { JSONArray(actionsJson ?: "[]") }.getOrElse { JSONArray() }
            fun first(array: JSONArray, ops: Set<String>, fields: List<String>): org.json.JSONObject? {
                fields.forEach { field -> for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index) ?: continue
                    if (row.optString("op") in ops && row.optString("field") == field) return row
                } }; return null
            }
            val account = first(conditions, setOf("is"), listOf("account", "acct"))
            val payeeCondition = first(conditions, setOf("is"), listOf("payee", "description"))
            val amountCondition = first(conditions, setOf("is", "isapprox", "isbetween"), listOf("amount"))
            val dateCondition = first(conditions, setOf("is", "isapprox"), listOf("date"))
            val localTs = cursor.longOrNull(9); val baseTs = cursor.longOrNull(11)
            val effective = if (localTs != null && localTs == baseTs) cursor.intOrNull(8) else cursor.intOrNull(10)
            val rawPayee = payeeCondition?.optString("value")?.takeIf(String::isNotBlank)
            val payee = rawPayee?.let { raw -> database.rawQuery("SELECT targetId FROM payee_mapping WHERE id=?", arrayOf(raw)).use { c -> if (c.moveToFirst()) c.stringOrNull(0) else null } }
            val amount = when (val value = amountCondition?.opt("value")) {
                is Number -> ScheduledAmount.Fixed(value.toLong())
                is org.json.JSONObject -> if (value.has("num1") && value.has("num2")) ScheduledAmount.Range(value.getLong("num1"), value.getLong("num2")) else null
                else -> null
            }
            var category: String? = null
            for (index in 0 until actions.length()) actions.optJSONObject(index)?.let {
                if (category == null && it.optString("op") == "set" && it.optString("field") == "category") category = it.optString("value").takeIf(String::isNotBlank)
            }
            val recognized = listOf(account, payeeCondition, amountCondition, dateCondition).count { it != null }
            val custom = conditions.length() > recognized || (0 until actions.length()).any { actions.optJSONObject(it)?.optString("op") != "link-schedule" }
            result += ActualScheduleSummary(id, cursor.stringOrNull(1), cursor.stringOrNull(12), effective?.let(DayDate::fromYyyymmdd),
                cursor.stringOrNull(7), baseTs, account?.optString("value")?.takeIf(String::isNotBlank), payee,
                amount, when (amountCondition?.optString("op")) { "is" -> ScheduleAmountOp.EXACT; "isbetween" -> ScheduleAmountOp.BETWEEN; else -> ScheduleAmountOp.APPROXIMATE },
                dateCondition?.optString("op"), dateCondition?.opt("value")?.let(ScheduleConditions::dateCondition),
                cursor.intOrZero(3) == 1, cursor.intOrZero(4) == 1, cursor.stringOrNull(5),
                if (cursor.isNull(6)) null else cursor.getDouble(6), custom, conditionsJson, actionsJson, category)
        } }
        return result
    }

    @Synchronized
    fun fetchPaidScheduleIds(schedules: List<ActualScheduleSummary>): Set<String> {
        val bounds = schedules.mapNotNull { schedule -> schedule.nextDate?.let {
            schedule.id to ScheduleStatusCalculator.occurrenceMatchStartDate(it, schedule.dateOp, schedule.postsTransaction).yyyymmdd
        } }
        if (bounds.isEmpty()) return emptySet()
        val placeholders = List(bounds.size) { "?" }.joinToString()
        val latest = mutableMapOf<String, Int>()
        database.rawQuery("""SELECT schedule,MAX(date) FROM transactions WHERE schedule IN ($placeholders)
            AND (tombstone=0 OR tombstone IS NULL) GROUP BY schedule""", bounds.map { it.first }.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) cursor.stringOrNull(0)?.let { latest[it] = cursor.intOrZero(1) }
        }
        return bounds.filter { (id, start) -> (latest[id] ?: Int.MIN_VALUE) >= start }.mapTo(mutableSetOf()) { it.first }
    }

    @Synchronized
    fun fetchSchedulePaymentDates(scheduleIds: List<String>): Map<String, Set<DayDate>> {
        if (scheduleIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, MutableSet<DayDate>>()
        scheduleIds.chunked(500).forEach { ids ->
            val placeholders = ids.joinToString { "?" }
            database.rawQuery(
                "SELECT schedule,date FROM transactions WHERE schedule IN ($placeholders) " +
                    "AND (tombstone=0 OR tombstone IS NULL)",
                ids.toTypedArray(),
            ).use { cursor -> while (cursor.moveToNext()) {
                val scheduleId = cursor.stringOrNull(0) ?: continue
                val date = DayDate.fromYyyymmdd(cursor.intOrZero(1)) ?: continue
                result.getOrPut(scheduleId) { mutableSetOf() } += date
            } }
        }
        return result
    }

    @Synchronized
    fun scheduleNameExists(name: String, excludingId: String? = null): Boolean {
        val clause = if (excludingId == null) "" else " AND id<>?"
        val args = if (excludingId == null) arrayOf(name) else arrayOf(name, excludingId)
        return database.rawQuery(
            "SELECT 1 FROM schedules WHERE (tombstone=0 OR tombstone IS NULL) AND name=?$clause LIMIT 1", args,
        ).use { it.moveToFirst() }
    }

    /** Insert a regular payee and its required self-mapping, with its CRDT messages. */
    @Synchronized
    fun insertPayee(payee: ActualPayee, messages: List<CrdtMessage>) = transaction {
        database.execSQL(
            "INSERT INTO payees (id, name, transfer_acct, tombstone) VALUES (?, ?, ?, 0)",
            arrayOf(payee.id, payee.name, payee.transferAccountId),
        )
        database.execSQL(
            "INSERT INTO payee_mapping (id, targetId) VALUES (?, ?)",
            arrayOf(payee.id, payee.id),
        )
        insertMessageRows(messages)
    }

    /** Persist one transaction or a complete transfer/split plus messages atomically. */
    @Synchronized
    fun insertTransactions(transactions: List<ActualTransaction>, messages: List<CrdtMessage>) = transaction {
        transactions.forEach(::insertTransactionRow)
        insertMessageRows(messages)
    }

    @Synchronized
    fun updateTransaction(transaction: ActualTransaction, messages: List<CrdtMessage>) = transaction {
        val values = transactionValues(transaction, includeCreationFields = false)
        check(database.update("transactions", values, "id = ?", arrayOf(transaction.id)) == 1) {
            "Transaction ${transaction.id} does not exist"
        }
        insertMessageRows(messages)
    }

    @Synchronized
    fun tombstoneTransactions(ids: List<String>, messages: List<CrdtMessage>) = transaction {
        ids.forEach { id ->
            val values = ContentValues().apply { put("tombstone", 1) }
            database.update("transactions", values, "id = ?", arrayOf(id))
        }
        insertMessageRows(messages)
    }

    @Synchronized
    fun mutateTransactions(
        updates: List<ActualTransaction>,
        inserts: List<ActualTransaction>,
        tombstoneIds: List<String>,
        messages: List<CrdtMessage>,
    ) = transaction {
        updates.forEach { item ->
            check(database.update("transactions", transactionValues(item, false), "id = ?", arrayOf(item.id)) == 1) {
                "Transaction ${item.id} does not exist"
            }
        }
        inserts.forEach(::insertTransactionRow)
        tombstoneIds.forEach { id ->
            database.update("transactions", ContentValues().apply { put("tombstone", 1) }, "id = ?", arrayOf(id))
        }
        insertMessageRows(messages)
    }

    private fun insertTransactionRow(transaction: ActualTransaction) {
        database.insertOrThrow("transactions", null, transactionValues(transaction, includeCreationFields = true))
    }

    private fun transactionValues(transaction: ActualTransaction, includeCreationFields: Boolean) = ContentValues().apply {
        if (includeCreationFields) put("id", transaction.id)
        put("acct", transaction.accountId)
        put("date", transaction.date)
        putOrNull("description", transaction.payeeId)
        putOrNull("category", transaction.categoryId)
        put("amount", transaction.amountCents)
        putOrNull("notes", transaction.notes)
        put("cleared", if (transaction.cleared) 1 else 0)
        put("reconciled", if (transaction.reconciled) 1 else 0)
        putOrNull("transferred_id", transaction.transferId)
        put("isParent", if (transaction.isParent) 1 else 0)
        put("isChild", if (transaction.parentId != null) 1 else 0)
        putOrNull("parent_id", transaction.parentId)
        put("tombstone", if (transaction.tombstone) 1 else 0)
        if (includeCreationFields) put("sort_order", transaction.sortOrder ?: System.currentTimeMillis().toDouble())
        putOrNull("imported_description", transaction.importedPayee)
        putOrNull("schedule", transaction.scheduleId)
        put("starting_balance_flag", if (transaction.startingBalance) 1 else 0)
    }

    private fun ContentValues.putOrNull(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    @Synchronized
    fun fetchCategoryGroups(): List<ActualCategoryGroup> {
        val categories = mutableListOf<ActualCategory>()
        database.rawQuery(
            """SELECT id, name, cat_group, is_income, hidden, sort_order, cleanup_def FROM categories
                WHERE tombstone = 0 OR tombstone IS NULL ORDER BY sort_order ASC""",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) categories += ActualCategory(
                id = cursor.getString(0),
                name = cursor.stringOrNull(1).orEmpty(),
                groupId = cursor.stringOrNull(2) ?: "",
                isIncome = cursor.intOrZero(3) == 1,
                hidden = cursor.intOrZero(4) == 1,
                sortOrder = cursor.doubleOrZero(5),
                cleanupDef = cursor.stringOrNull(6),
            )
        }
        val result = mutableListOf<ActualCategoryGroup>()
        database.rawQuery(
            """SELECT id, name, is_income, hidden, sort_order FROM category_groups
                WHERE tombstone = 0 OR tombstone IS NULL ORDER BY sort_order ASC""",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                result += ActualCategoryGroup(
                    id = id,
                    name = cursor.stringOrNull(1).orEmpty(),
                    isIncome = cursor.intOrZero(2) == 1,
                    hidden = cursor.intOrZero(3) == 1,
                    sortOrder = cursor.doubleOrZero(4),
                    categories = categories.filter { it.groupId == id },
                )
            }
        }
        return result
    }

    /** Actual's `cleanup_groups`: named pools referenced by category `cleanup_def` rows. */
    @Synchronized
    fun fetchCleanupGroups(includeTombstoned: Boolean = false): List<ActualCleanupGroup> {
        if (!hasTable("cleanup_groups")) return emptyList()
        val where = if (includeTombstoned) "" else "WHERE tombstone = 0 OR tombstone IS NULL"
        return database.rawQuery("SELECT id, name, tombstone FROM cleanup_groups $where", null).use { cursor ->
            val rows = mutableListOf<ActualCleanupGroup>()
            while (cursor.moveToNext()) rows += ActualCleanupGroup(
                id = cursor.getString(0), name = cursor.stringOrNull(1).orEmpty(), tombstone = cursor.intOrZero(2) == 1,
            )
            rows
        }
    }

    @Synchronized
    fun fetchTransactions(
        accountId: String? = null,
        limit: Int = 500,
        offset: Int = 0,
        query: String? = null,
        unclearedOnly: Boolean = false,
        hideReconciled: Boolean = false,
        statusFilter: TransactionStatusFilter? = null,
    ): List<ActualTransaction> {
        require(limit >= 0 && offset >= 0)
        val args = mutableListOf<String>()
        var accountClause = ""
        if (accountId != null) {
            accountClause = " AND t.acct = ?"
            args += accountId
        }
        val statusFilterActive = statusFilter != null && statusFilter != TransactionStatusFilter.ALL
        val stateClause = buildString {
            // statusFilter supersedes the legacy flags below; combining them (e.g. hideReconciled
            // with statusFilter = RECONCILED) would otherwise AND together contradictory clauses.
            if (!statusFilterActive) {
                if (unclearedOnly) append(" AND COALESCE(t.cleared, 0) = 0")
                if (hideReconciled) append(" AND COALESCE(t.reconciled, 0) = 0")
            }

            when (statusFilter) {
                TransactionStatusFilter.UNCATEGORIZED -> append(" AND (c.name IS NULL OR c.name = '') AND t.transferred_id IS NULL AND (t.isParent = 0 OR t.isParent IS NULL)")
                TransactionStatusFilter.UNCLEARED -> append(" AND COALESCE(t.cleared, 0) = 0")
                TransactionStatusFilter.CLEARED -> append(" AND COALESCE(t.cleared, 0) = 1 AND COALESCE(t.reconciled, 0) = 0")
                TransactionStatusFilter.RECONCILED -> append(" AND COALESCE(t.reconciled, 0) = 1")
                else -> {}
            }
        }
        val searchClause = if (query.isNullOrBlank()) "" else {
            // Bind a literal substring: %, _ and backslash are user text, not wildcards.
            val pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            repeat(10) { args += pattern }
            transactionSearchClause
        }
        args += limit.toString()
        args += offset.toString()
        val rows = mutableListOf<ActualTransaction>()
        database.rawQuery(transactionSelect + accountClause + stateClause + searchClause + " ORDER BY t.date DESC, t.sort_order DESC, t.id LIMIT ? OFFSET ?", args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) rows += cursor.toActualTransaction()
        }
        val parentIds = rows.filter(ActualTransaction::isParent).map(ActualTransaction::id)
        if (parentIds.isEmpty()) return rows
        val portions = mutableMapOf<String, MutableList<ActualTransaction.SplitPortion>>()
        // Stay below SQLite's bind limit even for complete-history results.
        parentIds.chunked(500).forEach { batch ->
            val placeholders = batch.joinToString { "?" }
            database.rawQuery(
                """
                    SELECT ct.parent_id, ct.id, ct.amount, c.name, ct.notes,
                           COALESCE(p.name, ct.imported_description), c.is_income
                    FROM transactions ct
                    LEFT JOIN category_mapping cm ON cm.id = ct.category
                    LEFT JOIN categories c ON c.id = COALESCE(cm.transferId, ct.category)
                    LEFT JOIN payee_mapping pm ON pm.id = ct.description
                    LEFT JOIN payees p ON p.id = pm.targetId
                    WHERE ct.parent_id IN ($placeholders)
                      AND (ct.tombstone = 0 OR ct.tombstone IS NULL)
                    ORDER BY ct.sort_order DESC
                """.trimIndent(), batch.toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) portions.getOrPut(cursor.getString(0)) { mutableListOf() } +=
                    ActualTransaction.SplitPortion(
                        id = cursor.getString(1),
                        amountCents = cursor.longOrZero(2),
                        categoryName = cursor.stringOrNull(3),
                        notes = cursor.stringOrNull(4),
                        payeeName = cursor.stringOrNull(5),
                        categoryIsIncome = cursor.intOrNull(6)?.let { it == 1 },
                    )
            }
        }
        return rows.map { it.copy(splitPortions = portions[it.id].orEmpty()) }
    }

    @Synchronized
    fun fetchTransaction(id: String): ActualTransaction? = database.rawQuery(
        transactionSelect + " AND t.id = ?", arrayOf(id),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toActualTransaction() else null }

    @Synchronized
    fun fetchScheduleTransactions(scheduleId: String): List<ActualTransaction> {
        val rows = mutableListOf<ActualTransaction>()
        database.rawQuery(
            transactionSelect + " AND t.schedule = ? ORDER BY t.date DESC, t.sort_order DESC, t.id",
            arrayOf(scheduleId),
        ).use { cursor -> while (cursor.moveToNext()) rows += cursor.toActualTransaction() }
        return rows
    }

    @Synchronized
    fun fetchChildTransactions(parentId: String): List<ActualTransaction> {
        val rows = mutableListOf<ActualTransaction>()
        database.rawQuery(
            transactionChildSelect + " AND t.parent_id = ? ORDER BY t.sort_order DESC",
            arrayOf(parentId),
        ).use { cursor -> while (cursor.moveToNext()) rows += cursor.toActualTransaction() }
        return rows
    }

    /** Every live cleared row that has not yet been locked by reconciliation. */
    @Synchronized
    fun fetchClearedUnreconciledTransactions(accountId: String): List<ActualTransaction> {
        val rows = mutableListOf<ActualTransaction>()
        database.rawQuery(
            transactionChildSelect + """
              AND t.acct = ?
              AND t.cleared = 1
              AND t.date IS NOT NULL
              AND COALESCE(t.reconciled, 0) = 0
              AND (t.isChild = 0 OR t.isChild IS NULL OR EXISTS (
                    SELECT 1 FROM transactions parent
                    WHERE parent.id = t.parent_id
                      AND (parent.tombstone = 0 OR parent.tombstone IS NULL)
                  ))
              ORDER BY t.date DESC, t.sort_order DESC, t.id
            """.trimIndent(),
            arrayOf(accountId),
        ).use { cursor -> while (cursor.moveToNext()) rows += cursor.toActualTransaction() }
        return rows
    }

    /** Report rows matching iOS fetchTransactionsForReports: no split parents or orphan children. */
    @Synchronized
    fun fetchTransactionsForReports(): List<ActualTransaction> {
        val rows = mutableListOf<ActualTransaction>()
        database.rawQuery(
            transactionChildSelect + """
              AND (t.isParent = 0 OR t.isParent IS NULL)
              AND (
                    t.isChild = 0 OR t.isChild IS NULL OR EXISTS (
                        SELECT 1 FROM transactions parent
                        WHERE parent.id = t.parent_id
                          AND (parent.tombstone = 0 OR parent.tombstone IS NULL)
                    )
                  )
              AND t.date IS NOT NULL AND t.acct IS NOT NULL
              ORDER BY t.date ASC, t.sort_order ASC
            """.trimIndent(),
            null,
        ).use { cursor -> while (cursor.moveToNext()) rows += cursor.toActualTransaction() }
        return rows
    }

    @Synchronized
    fun fetchDashboardPages(): List<DashboardPageRow> {
        if (!hasTable("dashboard_pages")) return emptyList()
        val rows = mutableListOf<DashboardPageRow>()
        database.rawQuery(
            "SELECT id, COALESCE(name, '') FROM dashboard_pages WHERE tombstone = 0 OR tombstone IS NULL ORDER BY rowid",
            null,
        ).use { cursor -> while (cursor.moveToNext()) rows += DashboardPageRow(cursor.getString(0), cursor.getString(1)) }
        return rows
    }

    @Synchronized
    fun fetchDashboardWidgets(pageId: String?): List<DashboardWidgetRow> {
        if (!hasTable("dashboard")) return emptyList()
        val columns = database.rawQuery("PRAGMA table_info(dashboard)", null).use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
        }
        val hasPage = "dashboard_page_id" in columns
        val where = if (!hasPage) "" else if (pageId == null) {
            " AND dashboard_page_id IS NULL"
        } else {
            " AND dashboard_page_id = ?"
        }
        val args = if (hasPage && pageId != null) arrayOf(pageId) else null
        val order = listOf("y", "x").filter(columns::contains).plus("rowid").joinToString()
        val rows = mutableListOf<DashboardWidgetRow>()
        database.rawQuery(
            "SELECT id, COALESCE(type, ''), meta FROM dashboard WHERE (tombstone = 0 OR tombstone IS NULL)$where ORDER BY $order",
            args,
        ).use { cursor -> while (cursor.moveToNext()) rows += DashboardWidgetRow(
            cursor.getString(0), cursor.getString(1), cursor.stringOrNull(2),
        ) }
        return rows
    }

    data class BudgetCell(
        val table: String,
        val rowId: String,
        val month: Int,
        val categoryId: String,
        val exists: Boolean,
        val amountCents: Long,
        val goalCents: Long?,
        val longGoal: Boolean,
    )

    data class BufferCell(
        val rowId: String,
        val exists: Boolean,
        val bufferedCents: Long,
    )

    /** Row in zero_budget_months holding the amount manually held for next month ("hold for next month"). */
    @Synchronized
    fun bufferCell(month: String): BufferCell? {
        parseMonth(month) ?: return null
        if (!hasTable("zero_budget_months")) return null
        return database.rawQuery(
            "SELECT id, buffered FROM zero_budget_months WHERE id = ?", arrayOf(month),
        ).use { cursor ->
            if (cursor.moveToFirst()) BufferCell(cursor.getString(0), true, cursor.longOrZero(1))
            else BufferCell(month, false, 0)
        }
    }

    @Synchronized
    fun budgetCell(month: String, categoryId: String): BudgetCell? {
        val monthInt = parseMonth(month) ?: return null
        val table = budgetTable() ?: return null
        return database.rawQuery(
            "SELECT id, amount, goal, long_goal FROM $table WHERE month = ? AND category = ?",
            arrayOf(monthInt.toString(), categoryId),
        ).use { cursor ->
            if (cursor.moveToFirst()) BudgetCell(
                table, cursor.getString(0), monthInt, categoryId, true, cursor.longOrZero(1),
                if (cursor.isNull(2)) null else cursor.getLong(2), cursor.intOrZero(3) == 1,
            ) else BudgetCell(table, "$monthInt-$categoryId", monthInt, categoryId, false, 0, null, false)
        }
    }

    /** Month walk matching Actuali iOS BudgetDatabase.budgetWalk. */
    @Synchronized
    fun fetchBudgetMonth(month: String): ActualBudgetMonth {
        val target = parseMonth(month) ?: throw IllegalArgumentException("Month must be yyyy-MM")
        val table = budgetTable()
        val envelope = table != "reflect_budgets"
        data class BudgetRow(val amount: Long, val flag: Boolean, val goal: Long?, val longGoal: Boolean)
        val budgets = mutableMapOf<Int, MutableMap<String, BudgetRow>>()
        if (table != null) database.rawQuery(
            "SELECT month, category, amount, carryover, goal, long_goal FROM $table WHERE month <= ?",
            arrayOf(target.toString()),
        ).use { cursor -> while (cursor.moveToNext()) {
            val m = cursor.intOrZero(0)
            val category = cursor.stringOrNull(1) ?: continue
            budgets.getOrPut(m) { mutableMapOf() }[category] = BudgetRow(
                cursor.longOrZero(2), cursor.intOrZero(3) == 1,
                if (cursor.isNull(4)) null else cursor.getLong(4), cursor.intOrZero(5) == 1,
            )
        } }

        val spent = mutableMapOf<Int, MutableMap<String, Long>>()
        database.rawQuery(
            """
                SELECT (t.date / 100), COALESCE(cm.transferId, t.category), SUM(t.amount)
                FROM transactions t
                LEFT JOIN category_mapping cm ON cm.id = t.category
                LEFT JOIN accounts a ON a.id = t.acct
                LEFT JOIN transactions p ON p.id = t.parent_id
                WHERE (t.tombstone = 0 OR t.tombstone IS NULL)
                  AND (t.isChild = 0 OR t.isChild IS NULL OR
                       (p.id IS NOT NULL AND (p.tombstone = 0 OR p.tombstone IS NULL)))
                  AND (t.isParent = 0 OR t.isParent IS NULL)
                  AND t.category IS NOT NULL AND a.offbudget = 0
                  AND (a.tombstone = 0 OR a.tombstone IS NULL)
                  AND (t.date / 100) <= ?
                GROUP BY (t.date / 100), COALESCE(cm.transferId, t.category)
            """.trimIndent(), arrayOf(target.toString()),
        ).use { cursor -> while (cursor.moveToNext()) {
            val category = cursor.stringOrNull(1) ?: continue
            spent.getOrPut(cursor.getInt(0)) { mutableMapOf() }[category] = cursor.longOrZero(2)
        } }

        val buffered = mutableMapOf<Int, Long>()
        if (envelope && hasTable("zero_budget_months")) database.rawQuery(
            "SELECT id, buffered FROM zero_budget_months", null,
        ).use { cursor -> while (cursor.moveToNext()) {
            val m = cursor.getString(0).filter(Char::isDigit).toIntOrNull() ?: continue
            if (m % 100 in 1..12 && m <= target) buffered[m] = cursor.longOrZero(1)
        } }

        data class Cat(val id: String, val name: String, val group: String, val income: Boolean, val hidden: Boolean, val sort: Double,
            val goalDef: String?, val templateSource: String?, val cleanupDef: String?)
        data class Group(val id: String, val name: String, val hidden: Boolean, val sort: Double)
        val categories = mutableListOf<Cat>()
        database.rawQuery("SELECT id,name,cat_group,is_income,hidden,sort_order,goal_def,template_settings,cleanup_def FROM categories WHERE tombstone = 0 OR tombstone IS NULL", null).use { c ->
            while (c.moveToNext()) categories += Cat(c.getString(0), c.stringOrNull(1).orEmpty(), c.stringOrNull(2).orEmpty(), c.intOrZero(3) == 1, c.intOrZero(4) == 1, c.doubleOrZero(5),
                c.stringOrNull(6), c.stringOrNull(7)?.let { raw -> runCatching { JSONObject(raw).optString("source") }.getOrNull() }, c.stringOrNull(8))
        }
        val groups = mutableMapOf<String, Group>()
        database.rawQuery("SELECT id,name,hidden,sort_order FROM category_groups WHERE tombstone = 0 OR tombstone IS NULL", null).use { c ->
            while (c.moveToNext()) groups[c.getString(0)] = Group(c.getString(0), c.stringOrNull(1).orEmpty(), c.intOrZero(2) == 1, c.doubleOrZero(3))
        }
        val incomeIds = categories.filter(Cat::income).mapTo(mutableSetOf(), Cat::id)
        val expenseIds = categories.filterNot(Cat::income).mapTo(mutableSetOf(), Cat::id)
        val earliest = (budgets.keys + spent.keys + buffered.keys).minOrNull() ?: target
        var running = mutableMapOf<String, Long>()
        var priorFlags = mutableMapOf<String, Boolean>()
        var toBudget = 0L
        var priorBuffered = 0L
        val leftovers = mutableMapOf<Int, Map<String, Long>>()
        var cursorMonth = earliest
        while (cursorMonth <= target) {
            val monthBudgets = budgets[cursorMonth].orEmpty()
            val monthSpent = spent[cursorMonth].orEmpty()
            if (envelope) {
                val income = incomeIds.sumOf { monthSpent[it] ?: 0 }
                val automaticBuffer = incomeIds.sumOf { if (monthBudgets[it]?.flag == true) monthSpent[it] ?: 0 else 0 }
                val assigned = expenseIds.sumOf { monthBudgets[it]?.amount ?: 0 }
                val overspent = expenseIds.sumOf { if (priorFlags[it] == true) 0 else minOf(0, running[it] ?: 0) }
                val held = (buffered[cursorMonth] ?: 0).takeIf { it != 0L } ?: automaticBuffer
                toBudget = income + toBudget + priorBuffered + overspent - assigned - held
                priorBuffered = held
            }
            val touched = monthBudgets.keys + monthSpent.keys + running.keys
            val next = mutableMapOf<String, Long>()
            val nextFlags = mutableMapOf<String, Boolean>()
            touched.forEach { id ->
                val prior = running[id] ?: 0
                val contribution = when {
                    priorFlags[id] == true -> prior
                    envelope -> maxOf(0, prior)
                    else -> 0
                }
                next[id] = (monthBudgets[id]?.amount ?: 0) + (monthSpent[id] ?: 0) + contribution
                nextFlags[id] = monthBudgets[id]?.flag == true
            }
            running = next
            priorFlags = nextFlags
            leftovers[cursorMonth] = next
            cursorMonth = nextMonth(cursorMonth)
        }
        val targetBudgets = budgets[target].orEmpty()
        val targetSpent = spent[target].orEmpty()
        val expenses = categories.filterNot(Cat::income).mapNotNull { cat ->
            val group = groups[cat.group] ?: return@mapNotNull null
            val budgeted = targetBudgets[cat.id]?.amount ?: 0
            val activity = targetSpent[cat.id] ?: 0
            val available = leftovers[target]?.get(cat.id) ?: (budgeted + activity)
            ActualCategoryBudget(month, cat.id, cat.name, cat.group, group.name, group.sort, cat.sort,
                budgeted, activity, available, available - budgeted - activity, cat.hidden, group.hidden,
                targetBudgets[cat.id]?.goal, targetBudgets[cat.id]?.longGoal == true, targetBudgets[cat.id]?.flag == true,
                cat.goalDef, cat.templateSource, cat.cleanupDef)
        }.sortedWith(compareBy(ActualCategoryBudget::groupSortOrder, ActualCategoryBudget::categorySortOrder))
        val incomes = categories.filter(Cat::income).mapNotNull { cat ->
            val group = groups[cat.group] ?: return@mapNotNull null
            ActualIncomeBudget(month, cat.id, cat.name, group.name, cat.sort,
                targetBudgets[cat.id]?.amount ?: 0, targetSpent[cat.id] ?: 0, cat.hidden, group.hidden)
        }.sortedBy(ActualIncomeBudget::sortOrder)
        return ActualBudgetMonth(
            month,
            expenses.filterNot { it.hidden || it.groupHidden },
            incomes.filterNot { it.hidden || it.groupHidden },
            if (envelope) toBudget else null,
            expenses.filter { it.hidden || it.groupHidden },
            incomes.filter { it.hidden || it.groupHidden },
            if (envelope) buffered[target] ?: 0 else 0,
        )
    }

    private fun budgetTable(): String? {
        val zero = hasTable("zero_budgets")
        val reflect = hasTable("reflect_budgets")
        if (!zero || !reflect) return when { zero -> "zero_budgets"; reflect -> "reflect_budgets"; else -> null }
        val tracking = if (hasTable("preferences")) database.rawQuery(
            "SELECT value FROM preferences WHERE id = 'budgetType'", null,
        ).use { it.moveToFirst() && it.getString(0) in setOf("tracking", "report") } else false
        return if (tracking) "reflect_budgets" else "zero_budgets"
    }

    private fun hasTable(name: String) = database.rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name),
    ).use { it.moveToFirst() }

    private fun parseMonth(value: String): Int? {
        val parts = value.split('-')
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return year * 100 + month
    }

    private fun nextMonth(value: Int): Int = if (value % 100 == 12) (value / 100 + 1) * 100 + 1 else value + 1

    private fun android.database.Cursor.toActualTransaction() = ActualTransaction(
        id = getString(getColumnIndexOrThrow("id")),
        accountId = string("acct") ?: "",
        date = int("date"),
        amountCents = long("amount"),
        payeeId = string("description"),
        payeeName = string("payee_name"),
        categoryId = string("category"),
        categoryName = string("category_name"),
        notes = string("notes"),
        cleared = int("cleared") == 1,
        reconciled = int("reconciled") == 1,
        transferId = string("transferred_id"),
        isParent = int("isParent") == 1,
        parentId = string("parent_id"),
        tombstone = int("tombstone") == 1,
        sortOrder = doubleOrNull("sort_order"),
        importedPayee = string("imported_description"),
        scheduleId = string("schedule"),
        transferAccountId = string("transfer_acct"),
        startingBalance = int("starting_balance_flag") == 1,
        categoryIsIncome = intOrNull(getColumnIndexOrThrow("category_is_income"))?.let { it == 1 },
    )

    private fun android.database.Cursor.string(name: String) = stringOrNull(getColumnIndexOrThrow(name))
    private fun android.database.Cursor.int(name: String) = intOrZero(getColumnIndexOrThrow(name))
    private fun android.database.Cursor.long(name: String) = longOrZero(getColumnIndexOrThrow(name))
    private fun android.database.Cursor.doubleOrNull(name: String): Double? =
        getColumnIndexOrThrow(name).let { if (isNull(it)) null else getDouble(it) }
    private fun android.database.Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
    private fun android.database.Cursor.intOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)
    private fun android.database.Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
    private fun android.database.Cursor.intOrZero(index: Int): Int = if (isNull(index)) 0 else getInt(index)
    private fun android.database.Cursor.longOrZero(index: Int): Long = if (isNull(index)) 0 else getLong(index)
    private fun android.database.Cursor.doubleOrZero(index: Int): Double = if (isNull(index)) 0.0 else getDouble(index)

    @Synchronized
    fun insertMessages(messages: List<CrdtMessage>): List<CrdtMessage> = transaction {
        insertMessageRows(messages)
    }

    @Synchronized
    fun getMessagesSince(since: String): List<CrdtMessage> {
        val messages = mutableListOf<CrdtMessage>()
        database.rawQuery(
            """
                SELECT timestamp, dataset, row, column, value
                FROM messages_crdt
                WHERE timestamp > ?
                ORDER BY timestamp
            """.trimIndent(),
            arrayOf(since),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val timestamp = HlcTimestamp.parse(cursor.getString(0)) ?: continue
                messages += CrdtMessage(
                    timestamp,
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    cursor.getString(4),
                )
            }
        }
        return messages
    }

    @Synchronized
    fun maxMessageTimestamp(): String? = database.rawQuery(
        "SELECT MAX(timestamp) FROM messages_crdt",
        null,
    ).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null }

    @Synchronized
    fun filterNewMessages(messages: List<CrdtMessage>): List<CrdtMessage> =
        messages.filterNot(::hasSameOrNewerCellMessage)

    @Synchronized
    fun applyMessages(messages: List<CrdtMessage>) = transaction {
        applyMessageRows(messages, syncableSchema())
    }

    @Synchronized
    fun applyLocalMessages(messages: List<CrdtMessage>): List<CrdtMessage> = transaction {
        applyMessageRows(messages, syncableSchema())
        insertMessageRows(messages)
    }

    /**
     * The receive-side operation: select per-cell winners, apply them, and
     * deduplicate the complete server batch in one SQLite transaction.
     */
    @Synchronized
    fun receiveMessages(messages: List<CrdtMessage>): ReceiveResult = transaction {
        val winners = messages.filterNot(::hasSameOrNewerCellMessage)
        applyMessageRows(winners, syncableSchema())
        ReceiveResult(winners, insertMessageRows(messages))
    }

    @Synchronized
    fun deriveMerkleFromMessageLog(): MerkleTree {
        val buckets = mutableMapOf<Long, Int>()
        database.rawQuery("SELECT timestamp FROM messages_crdt", null).use { cursor ->
            while (cursor.moveToNext()) {
                val timestamp = cursor.getString(0)
                val minute = HlcTimestamp.minutesSinceEpoch(timestamp) ?: continue
                val millis = minute * 60_000
                buckets[millis] = (buckets[millis] ?: 0) xor MurmurHash3.hash(timestamp).toInt()
            }
        }
        return MerkleTree.building(buckets).pruned()
    }

    @Synchronized
    fun loadClock(): ClockRecord? = database.rawQuery(
        "SELECT clock FROM messages_clock WHERE id = 1",
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst() || cursor.isNull(0)) return@use null
        val raw = cursor.getString(0)
        runCatching {
            val json = JSONObject(raw)
            ClockRecord(json.optString("timestamp"), MerkleJson.decode(json.getJSONObject("merkle").toString()))
        }.getOrElse {
            runCatching { ClockRecord("", MerkleJson.decode(raw)) }.getOrNull()
        }
    }

    @Synchronized
    fun saveClock(clock: ClockRecord) = transaction {
        database.execSQL("CREATE TABLE IF NOT EXISTS messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
        val json = "{\"timestamp\":\"${clock.timestamp}\",\"merkle\":${MerkleJson.encode(clock.merkle)}}"
        val values = ContentValues().apply {
            put("id", 1)
            put("clock", json)
        }
        database.insertWithOnConflict("messages_clock", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun hasSameOrNewerCellMessage(message: CrdtMessage): Boolean = database.rawQuery(
        """
            SELECT 1 FROM messages_crdt
            WHERE dataset = ? AND row = ? AND column = ? AND timestamp >= ?
            LIMIT 1
        """.trimIndent(),
        arrayOf(message.dataset, message.row, message.column, message.timestamp.toString()),
    ).use { it.moveToFirst() }

    private fun insertMessageRows(messages: List<CrdtMessage>): List<CrdtMessage> {
        val inserted = mutableListOf<CrdtMessage>()
        messages.forEach { message ->
            val values = ContentValues().apply {
                put("timestamp", message.timestamp.toString())
                put("dataset", message.dataset)
                put("row", message.row)
                put("column", message.column)
                put("value", message.value)
            }
            val result = database.insertWithOnConflict(
                "messages_crdt",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            if (result != -1L) inserted += message
        }
        return inserted
    }

    private fun applyMessageRows(messages: List<CrdtMessage>, schema: Map<String, Set<String>>) {
        messages.sortedBy(CrdtMessage::timestamp).forEach { message ->
            val columns = schema[message.dataset] ?: return@forEach
            if (message.column !in columns) return@forEach
            upsertValue(message)
        }
    }

    private fun upsertValue(message: CrdtMessage) {
        val values = ContentValues().apply { putCrdtValue(message.column, CrdtValue.deserialize(message.value)) }
        val updated = database.update(
            quoteIdentifier(message.dataset),
            values,
            "id = ?",
            arrayOf(message.row),
        )
        if (updated == 0) {
            values.put("id", message.row)
            try {
                database.insertOrThrow(quoteIdentifier(message.dataset), null, values)
            } catch (error: SQLiteConstraintException) {
                // Another cell in this batch may have created the row first.
                database.update(
                    quoteIdentifier(message.dataset),
                    values.apply { remove("id") },
                    "id = ?",
                    arrayOf(message.row),
                )
            }
        }
    }

    private fun ContentValues.putCrdtValue(column: String, value: CrdtValue) {
        when (value) {
            CrdtValue.Null -> putNull(column)
            is CrdtValue.Integer -> put(column, value.value)
            is CrdtValue.Decimal -> put(column, value.value)
            is CrdtValue.Text -> put(column, value.value)
        }
    }

    private fun syncableSchema(): Map<String, Set<String>> {
        val schema = mutableMapOf<String, Set<String>>()
        database.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { tables ->
            while (tables.moveToNext()) {
                val table = tables.getString(0)
                if (table in internalTables || table.startsWith("sqlite_")) continue
                val columns = mutableSetOf<String>()
                database.rawQuery("PRAGMA table_info(${quoteIdentifier(table)})", null).use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
                }
                if ("id" in columns) schema[table] = columns
            }
        }
        return schema
    }

    private fun quoteIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private inline fun <T> transaction(block: () -> T): T {
        database.beginTransaction()
        return try {
            block().also { database.setTransactionSuccessful() }
        } finally {
            database.endTransaction()
        }
    }

    data class ReceiveResult(
        val appliedMessages: List<CrdtMessage>,
        val insertedMessages: List<CrdtMessage>,
    )

    data class ClockRecord(val timestamp: String, val merkle: MerkleNode)

    companion object {
        const val CREDIT_CARD_PREFERENCE_PREFIX = "actuali:credit_card:"
        private const val transactionSearchClause = """
            AND (
                COALESCE(pa.name, p.name, cpa.name, cp.name,
                         CASE WHEN t.isParent = 1 THEN 'Split' ELSE '' END) LIKE ? ESCAPE '\'
                OR COALESCE(c.name, CASE WHEN t.isParent = 1 THEN 'Split' ELSE 'Uncategorized' END) LIKE ? ESCAPE '\'
                OR t.notes LIKE ? ESCAPE '\'
                OR t.imported_description LIKE ? ESCAPE '\'
                OR EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.acct AND a.name LIKE ? ESCAPE '\')
                OR (t.isParent = 1 AND EXISTS (
                    SELECT 1 FROM transactions child
                    LEFT JOIN payee_mapping spm ON spm.id = child.description
                    LEFT JOIN payees sp ON sp.id = spm.targetId
                    LEFT JOIN accounts sa ON sa.id = sp.transfer_acct
                    LEFT JOIN category_mapping scm ON scm.id = child.category
                    LEFT JOIN categories sc ON sc.id = COALESCE(scm.transferId, child.category)
                    WHERE child.parent_id = t.id AND child.isChild = 1
                      AND (child.tombstone = 0 OR child.tombstone IS NULL)
                      AND (sp.name LIKE ? ESCAPE '\' OR child.notes LIKE ? ESCAPE '\'
                           OR child.imported_description LIKE ? ESCAPE '\'
                           OR sa.name LIKE ? ESCAPE '\' OR sc.name LIKE ? ESCAPE '\')
                ))
            )
        """

        private const val transactionSelect = """
            SELECT t.id, t.isParent, t.isChild, t.acct, t.category, t.amount,
                   t.description, t.notes, t.date, t.imported_description, t.schedule,
                   t.transferred_id, t.cleared, t.reconciled, t.sort_order,
                   t.tombstone, t.parent_id,
                   COALESCE(pa.name, p.name, cpa.name, cp.name) AS payee_name,
                   c.name AS category_name, p.transfer_acct AS transfer_acct,
                   t.starting_balance_flag, c.is_income AS category_is_income
            FROM transactions t
            LEFT JOIN payee_mapping pm ON pm.id = t.description
            LEFT JOIN payees p ON p.id = pm.targetId
            LEFT JOIN accounts pa ON pa.id = p.transfer_acct
                AND (pa.tombstone = 0 OR pa.tombstone IS NULL)
            LEFT JOIN (
                SELECT ct.parent_id,
                       CASE WHEN COUNT(DISTINCT ct.description) = 1
                            THEN MIN(ct.description) END AS payee
                FROM transactions ct
                WHERE ct.isChild = 1
                  AND (ct.tombstone = 0 OR ct.tombstone IS NULL)
                  AND ct.description IS NOT NULL
                GROUP BY ct.parent_id
            ) child_payee ON t.isParent = 1 AND child_payee.parent_id = t.id
            LEFT JOIN payee_mapping cpm ON cpm.id = child_payee.payee
            LEFT JOIN payees cp ON cp.id = cpm.targetId
            LEFT JOIN accounts cpa ON cpa.id = cp.transfer_acct
                AND (cpa.tombstone = 0 OR cpa.tombstone IS NULL)
            LEFT JOIN category_mapping cm ON cm.id = t.category
            LEFT JOIN categories c ON c.id = COALESCE(cm.transferId, t.category)
            WHERE (t.tombstone = 0 OR t.tombstone IS NULL)
              AND (t.isChild = 0 OR t.isChild IS NULL)
              AND t.date IS NOT NULL AND t.acct IS NOT NULL
        """

        private const val transactionChildSelect = """
            SELECT t.id, t.isParent, t.isChild, t.acct, t.category, t.amount,
                   t.description, t.notes, t.date, t.imported_description, t.schedule,
                   t.transferred_id, t.cleared, t.reconciled, t.sort_order,
                   t.tombstone, t.parent_id, COALESCE(pa.name, p.name) AS payee_name,
                   c.name AS category_name, p.transfer_acct AS transfer_acct,
                   t.starting_balance_flag, c.is_income AS category_is_income
            FROM transactions t
            LEFT JOIN payee_mapping pm ON pm.id = t.description
            LEFT JOIN payees p ON p.id = pm.targetId
            LEFT JOIN accounts pa ON pa.id = p.transfer_acct
                AND (pa.tombstone = 0 OR pa.tombstone IS NULL)
            LEFT JOIN category_mapping cm ON cm.id = t.category
            LEFT JOIN categories c ON c.id = COALESCE(cm.transferId, t.category)
            WHERE (t.tombstone = 0 OR t.tombstone IS NULL)
        """

        private data class ColumnMigration(
            val id: Long,
            val table: String,
            val column: String,
            val declaration: String,
        )

        // IDs and declarations mirror Actuali iOS. The core read model needs
        // schedule now; the remaining columns ensure synced account/category
        // data is not discarded while later Android screens are being ported.
        private val columnMigrations = listOf(
            ColumnMigration(1694438752000, "zero_budgets", "goal", "INTEGER DEFAULT null"),
            ColumnMigration(1694438752001, "reflect_budgets", "goal", "INTEGER DEFAULT null"),
            ColumnMigration(1694438752002, "categories", "goal_def", "TEXT DEFAULT null"),
            ColumnMigration(1720665000000, "zero_budgets", "long_goal", "INTEGER DEFAULT null"),
            ColumnMigration(1720665000001, "reflect_budgets", "long_goal", "INTEGER DEFAULT null"),
            ColumnMigration(1754611200000, "categories", "template_settings", "JSON DEFAULT '{\"source\": \"notes\"}'"),
            ColumnMigration(1778510362741, "categories", "cleanup_def", "TEXT DEFAULT NULL"),
            ColumnMigration(1780606214999, "transactions", "schedule", "TEXT"),
            ColumnMigration(1780606215005, "transactions", "starting_balance_flag", "INTEGER DEFAULT 0"),
            ColumnMigration(1780606215000, "accounts", "bank_sync_status", "TEXT"),
            ColumnMigration(1780606215003, "accounts", "account_sync_source", "TEXT"),
            ColumnMigration(1780606215004, "accounts", "last_sync", "TEXT"),
            ColumnMigration(1783004650757, "schedules", "sort_order", "REAL DEFAULT 0"),
            ColumnMigration(1787013118115, "accounts", "account_group_id", "TEXT DEFAULT NULL"),
        )
        private val internalTables = setOf("messages_crdt", "messages_clock", "migrations", "__migrations__")
        private val requiredTables = setOf(
            "accounts",
            "categories",
            "category_groups",
            "messages_clock",
            "messages_crdt",
            "payee_mapping",
            "payees",
            "transactions",
            "zero_budgets",
        )

        fun open(file: File, readOnly: Boolean = false): ActualBudgetDatabase {
            validate(file)
            val flags = if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE
            val database = SQLiteDatabase.openDatabase(file.absolutePath, null, flags)
            // WAL lets a writer connection (e.g. the sync worker) commit without blocking readers on
            // another connection (e.g. the UI's repository), so concurrent access doesn't stall the UI.
            if (!readOnly) database.enableWriteAheadLogging()
            // PRAGMA busy_timeout returns the applied value as a result row, which execSQL()
            // (update/delete only) rejects; rawQuery consumes that row instead.
            database.rawQuery("PRAGMA busy_timeout=5000", null).use { it.moveToFirst() }
            if (!readOnly) runMigrations(database)
            return ActualBudgetDatabase(database)
        }

        private fun runMigrations(database: SQLiteDatabase) {
            database.beginTransaction()
            try {
                database.execSQL("CREATE TABLE IF NOT EXISTS __migrations__ (id INTEGER PRIMARY KEY)")
                val applied = mutableSetOf<Long>()
                database.rawQuery("SELECT id FROM __migrations__", null).use { cursor ->
                    while (cursor.moveToNext()) applied += cursor.getLong(0)
                }
                if (database.hasTable("payees")) {
                    database.execSQL(
                        """CREATE TABLE IF NOT EXISTS payee_locations (
                            id TEXT PRIMARY KEY, payee_id TEXT, latitude REAL, longitude REAL,
                            created_at INTEGER, tombstone INTEGER DEFAULT 0
                        )""".trimIndent(),
                    )
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_payee_locations_payee_id ON payee_locations(payee_id)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_payee_locations_tombstone_payee_created ON payee_locations(tombstone, payee_id, created_at)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_payee_locations_geo_tombstone ON payee_locations(tombstone, latitude, longitude)")
                    database.execSQL("INSERT OR IGNORE INTO __migrations__ (id) VALUES (1768872504000)")
                }
                if (database.hasTable("accounts")) {
                    database.execSQL(
                        """CREATE TABLE IF NOT EXISTS account_groups (
                            id TEXT PRIMARY KEY, name TEXT, sort_order REAL,
                            tombstone INTEGER DEFAULT 0
                        )""".trimIndent(),
                    )
                }
                // Older Actual servers pre-date the cleanup automation schema; ensure the
                // groups table exists locally so cleanup_def group references stay resolvable.
                if (database.hasTable("categories")) {
                    database.execSQL(
                        """CREATE TABLE IF NOT EXISTS cleanup_groups (
                            id TEXT PRIMARY KEY, name TEXT NOT NULL, tombstone INTEGER DEFAULT 0
                        )""".trimIndent(),
                    )
                }
                val added = mutableListOf<Pair<String, String>>()
                columnMigrations.filterNot { it.id in applied }.forEach { migration ->
                    if (!database.hasTable(migration.table)) return@forEach
                    if (!database.hasColumn(migration.table, migration.column)) {
                        database.execSQL(
                            "ALTER TABLE ${quote(migration.table)} ADD COLUMN ${quote(migration.column)} ${migration.declaration}",
                        )
                        added += migration.table to migration.column
                    }
                    database.execSQL("INSERT OR IGNORE INTO __migrations__ (id) VALUES (?)", arrayOf(migration.id))
                }
                if (database.hasTable("transactions") &&
                    database.hasColumn("transactions", "acct") && database.hasColumn("transactions", "tombstone")) {
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_acct_tombstone ON transactions(acct, tombstone)")
                }
                if (database.hasTable("transactions") && database.hasColumn("transactions", "schedule")) {
                    database.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_schedule ON transactions(schedule)")
                }
                replayStoredMessages(database, added)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }

        private fun replayStoredMessages(database: SQLiteDatabase, columns: List<Pair<String, String>>) {
            if (columns.isEmpty() || !database.hasTable("messages_crdt")) return
            columns.forEach { (table, column) ->
                database.rawQuery(
                    """
                        SELECT m.row, m.value FROM messages_crdt m
                        JOIN (SELECT row, MAX(timestamp) timestamp FROM messages_crdt
                              WHERE dataset = ? AND `column` = ? GROUP BY row) latest
                          ON latest.row = m.row AND latest.timestamp = m.timestamp
                        WHERE m.dataset = ? AND m.`column` = ?
                    """.trimIndent(), arrayOf(table, column, table, column),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val values = ContentValues().apply {
                            when (val value = CrdtValue.deserialize(cursor.getString(1))) {
                                CrdtValue.Null -> putNull(column)
                                is CrdtValue.Integer -> put(column, value.value)
                                is CrdtValue.Decimal -> put(column, value.value)
                                is CrdtValue.Text -> put(column, value.value)
                            }
                        }
                        database.update(quote(table), values, "id = ?", arrayOf(cursor.getString(0)))
                    }
                }
            }
        }

        private fun SQLiteDatabase.hasTable(table: String): Boolean = rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table),
        ).use { it.moveToFirst() }

        private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean =
            rawQuery("PRAGMA table_info(${quote(table)})", null).use { cursor ->
                val index = cursor.getColumnIndexOrThrow("name")
                var found = false
                while (cursor.moveToNext()) if (cursor.getString(index) == column) found = true
                found
            }

        private fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""

        fun validate(file: File) {
            if (!file.isFile) throw BudgetFileException.MissingDatabase
            val database = try {
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            } catch (_: Exception) {
                throw BudgetFileException.InvalidArchive
            }
            database.use { db ->
                val found = mutableSetOf<String>()
                db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
                    while (cursor.moveToNext()) found += cursor.getString(0)
                }
                val missing = requiredTables - found
                if (missing.isNotEmpty()) {
                    throw BudgetFileException.UnsafeArchive(
                        "database is missing Actual tables: ${missing.sorted().joinToString()}",
                    )
                }
            }
        }
    }
}
