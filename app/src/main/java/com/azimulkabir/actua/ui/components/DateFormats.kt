package com.azimulkabir.actua.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val legacyDateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH)

// DateTimeFormatter/Pattern construction isn't free, and this path runs once per transaction
// row, per recomposition, while scrolling. Both are immutable/thread-safe, so hoisting them to
// module-level vals (instead of rebuilding on every formatDate/parseStoredDate call) is safe —
// same fix as MoneyFormatter's NumberFormat caching (#323).
private val ddMmYyyyFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val mmDdYyyyFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
private val mediumDateFormatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
private val basicIsoDatePattern = Regex("\\d{8}")
private val isoLocalDatePattern = Regex("\\d{4}-\\d{2}-\\d{2}")

object DateDisplay {
    @Volatile var format: String = "System default"
}

fun formatDate(date: LocalDate): String = formatDate(date, Locale.getDefault())

fun formatDate(date: LocalDate, locale: Locale): String = date.format(when (DateDisplay.format) {
    "DD/MM/YYYY" -> ddMmYyyyFormatter
    "MM/DD/YYYY" -> mmDdYyyyFormatter
    "YYYY-MM-DD" -> DateTimeFormatter.ISO_LOCAL_DATE
    else -> mediumDateFormatter.withLocale(locale)
})

fun parseStoredDate(value: String): LocalDate? = runCatching {
    when {
        value.matches(basicIsoDatePattern) -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
        value.matches(isoLocalDatePattern) -> LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        else -> LocalDate.parse(value, legacyDateFormatter)
    }
}.getOrNull()

fun formatStoredDate(value: String): String = parseStoredDate(value)?.let(::formatDate) ?: value

fun formatStoredDate(value: String, locale: Locale): String =
    parseStoredDate(value)?.let { formatDate(it, locale) } ?: value

fun storageDate(date: LocalDate): String = date.format(DateTimeFormatter.BASIC_ISO_DATE)
