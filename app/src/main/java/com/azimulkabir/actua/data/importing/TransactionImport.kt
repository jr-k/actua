package com.azimulkabir.actua.data.importing

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class ImportCandidate(
    val sourceRow: Int,
    val date: Int,
    val payee: String,
    val notes: String,
    val amountCents: Long,
    val reference: String? = null,
    val confidence: ImportConfidence = ImportConfidence.HIGH,
    val sourceLabel: String = "Statement",
    val accountHint: String? = null,
)

enum class ImportProblemCode {
    FILE_EMPTY,
    MISSING_REQUIRED_COLUMNS,
    PAYEE_BLANK,
    AMOUNT_BLANK,
    AMOUNT_BLANK_OR_ZERO,
    INVALID_AMOUNT,
    UNSUPPORTED_DATE,
    UNCLOSED_QUOTED_FIELD,
    DEBIT_OR_CREDIT_NOT_RECOGNIZED,
    TRANSACTION_AMOUNT_NOT_RECOGNIZED,
    ROW_COULD_NOT_BE_PARSED,
}

data class ImportProblem(
    val sourceRow: Int,
    val code: ImportProblemCode,
    val detail: String? = null,
) {
    constructor(sourceRow: Int, legacyMessage: String) : this(
        sourceRow = sourceRow,
        code = when (legacyMessage) {
            "No debit or credit wording was recognized" -> ImportProblemCode.DEBIT_OR_CREDIT_NOT_RECOGNIZED
            "No transaction amount was recognized" -> ImportProblemCode.TRANSACTION_AMOUNT_NOT_RECOGNIZED
            else -> ImportProblemCode.ROW_COULD_NOT_BE_PARSED
        },
    )
}

data class ImportParseResult(
    val candidates: List<ImportCandidate>,
    val problems: List<ImportProblem>,
)

enum class ImportColumnRole { IGNORE, DATE, PAYEE, NOTES, REFERENCE, AMOUNT, DEBIT, CREDIT }
enum class StatementFormat { CSV, XLSX, PDF, SHARED_TEXT, NOTIFICATION }
enum class ImportConfidence { HIGH, MEDIUM, LOW }

data class ImportColumnMapping(
    val roles: List<ImportColumnRole>,
    val datePattern: String = "Auto",
    val expensesArePositive: Boolean = false,
)

data class ImportTable(val headers: List<String>, val rows: List<List<String>>)

/** Source-independent boundary used by CSV now and notification/statement sources later. */
fun interface TransactionCandidateSource {
    fun parse(content: String): ImportParseResult
}

object CsvTransactionCandidateSource : TransactionCandidateSource {
    private val dateHeaders = setOf("date", "transaction date", "posted date", "posting date")
    private val payeeHeaders = setOf("payee", "description", "merchant", "narration", "details")
    private val notesHeaders = setOf("notes", "memo")
    private val referenceHeaders = setOf("reference", "transaction id", "id")

    override fun parse(content: String): ImportParseResult = parse(inspect(content))

    fun inspect(content: String): ImportTable {
        val rows = parseDelimited(content.removePrefix("\uFEFF"))
        return ImportTable(rows.firstOrNull().orEmpty(), rows.drop(1))
    }

    fun suggestedMapping(headers: List<String>): ImportColumnMapping {
        val normalized = headers.map { it.trim().lowercase(Locale.ROOT) }
        fun role(index: Int, names: Set<String>, role: ImportColumnRole) =
            if (normalized[index] in names) role else null
        return ImportColumnMapping(headers.indices.map { index ->
            role(index, dateHeaders, ImportColumnRole.DATE)
                ?: role(index, payeeHeaders, ImportColumnRole.PAYEE)
                ?: role(index, notesHeaders, ImportColumnRole.NOTES)
                ?: role(index, referenceHeaders, ImportColumnRole.REFERENCE)
                ?: role(index, setOf("amount", "value"), ImportColumnRole.AMOUNT)
                ?: role(index, setOf("debit", "withdrawal", "outflow"), ImportColumnRole.DEBIT)
                ?: role(index, setOf("credit", "deposit", "inflow"), ImportColumnRole.CREDIT)
                ?: ImportColumnRole.IGNORE
        })
    }

    fun parse(table: ImportTable, mapping: ImportColumnMapping = suggestedMapping(table.headers)): ImportParseResult {
        if (table.headers.isEmpty()) {
            return ImportParseResult(emptyList(), listOf(ImportProblem(1, ImportProblemCode.FILE_EMPTY)))
        }
        fun column(role: ImportColumnRole) = mapping.roles.indexOf(role).takeIf { it >= 0 }
        val date = column(ImportColumnRole.DATE)
        val payee = column(ImportColumnRole.PAYEE)
        val notes = column(ImportColumnRole.NOTES)
        val reference = column(ImportColumnRole.REFERENCE)
        val amount = column(ImportColumnRole.AMOUNT)
        val debit = column(ImportColumnRole.DEBIT)
        val credit = column(ImportColumnRole.CREDIT)
        val missing = buildList {
            if (date == null) add("date")
            if (payee == null) add("payee")
            if (amount == null && debit == null && credit == null) add("amount")
        }
        if (missing.isNotEmpty()) return ImportParseResult(emptyList(), listOf(
            ImportProblem(1, ImportProblemCode.MISSING_REQUIRED_COLUMNS, missing.joinToString(","))
        ))

        val candidates = mutableListOf<ImportCandidate>()
        val problems = mutableListOf<ImportProblem>()
        table.rows.forEachIndexed { index, row ->
            val sourceRow = index + 2
            if (row.all(String::isBlank)) return@forEachIndexed
            try {
                val parsedDate = parseDate(row.value(date!!), mapping.datePattern)
                val parsedPayee = row.value(payee!!).trim().ifBlank {
                    throw ImportParseException(ImportProblemCode.PAYEE_BLANK)
                }
                val cents = if (amount != null) {
                    parseMoney(row.value(amount)).let { if (mapping.expensesArePositive) -it else it }
                } else {
                    val creditCents = credit?.let { parseOptionalMoney(row.value(it)) } ?: 0
                    val debitCents = debit?.let { parseOptionalMoney(row.value(it)) } ?: 0
                    creditCents - debitCents
                }
                if (cents == 0L) throw ImportParseException(ImportProblemCode.AMOUNT_BLANK_OR_ZERO)
                candidates += ImportCandidate(
                    sourceRow, parsedDate, parsedPayee, notes?.let { row.value(it).trim() }.orEmpty(), cents,
                    reference?.let { row.value(it).trim().takeIf(String::isNotEmpty) },
                )
            } catch (error: ImportParseException) {
                problems += ImportProblem(sourceRow, error.code, error.detail)
            } catch (_: Exception) {
                problems += ImportProblem(sourceRow, ImportProblemCode.ROW_COULD_NOT_BE_PARSED)
            }
        }
        return ImportParseResult(candidates, problems)
    }

    private fun List<String>.value(index: Int) = getOrElse(index) { "" }

    private fun parseMoney(value: String): Long {
        var clean = value.trim().replace(" ", "").replace(",", "")
        val negative = clean.startsWith("(") && clean.endsWith(")")
        clean = clean.trim('(', ')').replace(Regex("[^0-9.+-]"), "")
        if (clean.isBlank()) throw ImportParseException(ImportProblemCode.AMOUNT_BLANK)
        return try {
            BigDecimal(clean).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
                .let { if (negative) -it else it }
        } catch (_: ArithmeticException) {
            throw ImportParseException(ImportProblemCode.INVALID_AMOUNT)
        } catch (_: NumberFormatException) {
            throw ImportParseException(ImportProblemCode.INVALID_AMOUNT)
        }
    }

    private fun parseOptionalMoney(value: String) = if (value.isBlank()) 0L else parseMoney(value)

    private fun parseDate(value: String, requestedPattern: String): Int {
        val clean = value.trim()
        clean.toDoubleOrNull()?.takeIf { it >= 1_000 }?.let { serial ->
            val parsed = LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
            return parsed.year * 10_000 + parsed.monthValue * 100 + parsed.dayOfMonth
        }
        val formats = if (requestedPattern == "Auto") {
            listOf("yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "M/d/yyyy", "d/M/yyyy", "dd-MM-yyyy", "dd MMM yyyy")
        } else listOf(requestedPattern)
        val parsed = formats.firstNotNullOfOrNull { pattern ->
            try { LocalDate.parse(clean, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)) }
            catch (_: DateTimeParseException) { null }
        } ?: throw ImportParseException(ImportProblemCode.UNSUPPORTED_DATE, value.trim())
        return parsed.year * 10_000 + parsed.monthValue * 100 + parsed.dayOfMonth
    }

    internal fun parseDelimited(content: String): List<List<String>> {
        val firstLine = content.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(',', ';', '\t').maxByOrNull { candidate -> firstLine.count { it == candidate } } ?: ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < content.length) {
            val char = content[i]
            when {
                char == '"' && quoted && i + 1 < content.length && content[i + 1] == '"' -> { cell.append('"'); i++ }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> { row += cell.toString(); cell.clear() }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear(); rows += row; row = mutableListOf()
                }
                else -> cell.append(char)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        if (quoted) throw ImportParseException(ImportProblemCode.UNCLOSED_QUOTED_FIELD)
        return rows
    }

    private class ImportParseException(
        val code: ImportProblemCode,
        val detail: String? = null,
    ) : Exception()
}

object ImportDuplicateDetector {
    fun key(date: Int, amountCents: Long, payee: String): String =
        "$date|$amountCents|${payee.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")}"
}
