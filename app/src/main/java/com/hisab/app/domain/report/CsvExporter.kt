package com.hisab.app.domain.report

import com.hisab.app.domain.budget.MoneyParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds the CSV export of the report's transactions. Amounts are written as plain decimals
 * ("1234.50") so spreadsheets parse them as numbers, and text cells that start with a formula
 * character are neutralised so opening the file in Excel/Sheets can't execute a note as a formula.
 */
object CsvExporter {

    private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    val HEADER = listOf("Date", "Type", "Category", "Account", "Note", "Amount", "Effect on spending")

    fun build(rows: List<LedgerRow>, zone: ZoneId, names: NameLookup): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",") { escape(it) }).append("\r\n")
        for (row in rows) {
            val kind = LedgerRules.kindOf(row.type)
            val signed = when (kind) {
                FlowKind.INCOME -> row.amountMinor
                FlowKind.EXPENSE -> -row.amountMinor
                FlowKind.REFUND -> row.amountMinor
                else -> 0L
            }
            val account = names.accountName(row.sourceAccountId) ?: names.accountName(row.destinationAccountId) ?: ""
            val cells = listOf(
                Instant.ofEpochMilli(row.dateMillis).atZone(zone).format(dateTime),
                row.type,
                names.categoryName(row.categoryId) ?: "",
                account,
                row.note ?: "",
                MoneyParser.toFixedDecimal(row.amountMinor),
                if (kind == FlowKind.INCOME || kind == FlowKind.EXPENSE || kind == FlowKind.REFUND)
                    MoneyParser.toFixedDecimal(signed) else ""
            )
            sb.append(cells.joinToString(",") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    fun escape(value: String): String {
        var v = value
        if (v.isNotEmpty() && v[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
            // Numbers like "-12.50" are legitimate; only neutralise cells that aren't plain numbers.
            if (!Regex("""^-?\d+(\.\d+)?$""").matches(v)) v = "'$v"
        }
        val needsQuotes = v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }
}
