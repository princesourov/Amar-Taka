package com.hisab.app.domain

import com.hisab.app.domain.report.CsvExporter
import com.hisab.app.domain.report.LedgerRow
import com.hisab.app.domain.report.NameLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CsvExporterTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val names = object : NameLookup {
        override fun categoryName(id: String?) = if (id == "f") "Food, Dining" else null
        override fun accountName(id: String?) = if (id == "a") "Cash" else null
    }
    private fun at(h: Int) = LocalDateTime.of(2026, 3, 5, h, 30).atZone(zone).toInstant().toEpochMilli()

    @Test fun header_and_rows() {
        val csv = CsvExporter.build(listOf(
            LedgerRow("1", "EXPENSE", 123_450, "f", sourceAccountId = "a", note = "Lunch", dateMillis = at(13)),
            LedgerRow("2", "INCOME", 5_000, null, destinationAccountId = "a", dateMillis = at(9)),
            LedgerRow("3", "REFUND", 100, "f", dateMillis = at(10))
        ), zone, names)
        val lines = csv.trim().split("\r\n")
        assertEquals("Date,Type,Category,Account,Note,Amount,Effect on spending", lines[0])
        assertEquals("2026-03-05 13:30,EXPENSE,\"Food, Dining\",Cash,Lunch,1234.50,-1234.50", lines[1])
        assertEquals("2026-03-05 09:30,INCOME,,Cash,,50.00,50.00", lines[2])
        assertEquals("2026-03-05 10:30,REFUND,\"Food, Dining\",,,1.00,1.00", lines[3])
    }

    @Test fun quotes_commas_and_newlines_are_escaped() {
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""))
        assertEquals("\"a\nb\"", CsvExporter.escape("a\nb"))
    }

    @Test fun formula_injection_is_neutralised_but_numbers_are_not() {
        assertEquals("'=HYPERLINK(\"x\")".let { "\"" + it.replace("\"", "\"\"") + "\"" }, CsvExporter.escape("=HYPERLINK(\"x\")"))
        assertEquals("'@cmd", CsvExporter.escape("@cmd"))
        assertEquals("'+1 call", CsvExporter.escape("+1 call"))
        assertEquals("-12.50", CsvExporter.escape("-12.50"))
    }

    @Test fun empty_report_is_just_the_header() {
        assertTrue(CsvExporter.build(emptyList(), zone, NameLookup.Empty).trim() == CsvExporter.HEADER.joinToString(","))
    }
}
