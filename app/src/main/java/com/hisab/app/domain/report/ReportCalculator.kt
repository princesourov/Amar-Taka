package com.hisab.app.domain.report

import com.hisab.app.domain.model.TransactionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * A ledger row reduced to the fields reporting needs, decoupled from Room so the calculator below
 * is plain Kotlin and unit-testable on the JVM.
 */
data class LedgerRow(
    val id: String,
    val type: String, // TransactionType enum name
    val amountMinor: Long,
    val categoryId: String? = null,
    val sourceAccountId: String? = null,
    val destinationAccountId: String? = null,
    val note: String? = null,
    val dateMillis: Long
)

/** How a ledger row is treated by income/expense reporting. */
enum class FlowKind { INCOME, EXPENSE, REFUND, EXCLUDED_TRANSFER, EXCLUDED_LOAN, EXCLUDED_OTHER }

/**
 * THE single definition of what counts as income and spending. Reports, budgets and the dashboard
 * all follow these rules (the SQL queries mirror them — see TransactionDao.getTotalExpenseBetween
 * and getSpentBetween):
 *
 *  - INCOME            -> income
 *  - EXPENSE           -> spending
 *  - REFUND            -> reduces spending (in its own category when it has one); NOT income
 *  - TRANSFER          -> excluded: it only moves money between the user's own accounts
 *  - LENDING / BORROWING / REPAYMENT_RECEIVED / REPAYMENT_MADE -> excluded: these move principal
 *    to/from people; counting them would double-count the original loan as income or expense
 *  - GIFT / OTHER      -> excluded (no screen creates them; they are never silently counted)
 */
object LedgerRules {
    fun kindOf(type: String): FlowKind = when (type) {
        TransactionType.INCOME.name -> FlowKind.INCOME
        TransactionType.EXPENSE.name -> FlowKind.EXPENSE
        TransactionType.REFUND.name -> FlowKind.REFUND
        TransactionType.TRANSFER.name -> FlowKind.EXCLUDED_TRANSFER
        TransactionType.LENDING.name, TransactionType.BORROWING.name,
        TransactionType.REPAYMENT_RECEIVED.name, TransactionType.REPAYMENT_MADE.name -> FlowKind.EXCLUDED_LOAN
        else -> FlowKind.EXCLUDED_OTHER
    }
}

enum class TypeFilter { ALL, INCOME, EXPENSE }

data class ReportFilter(
    val type: TypeFilter = TypeFilter.ALL,
    val categoryId: String? = null,
    val accountId: String? = null,
    val keyword: String = ""
) {
    val isActive: Boolean
        get() = type != TypeFilter.ALL || categoryId != null || accountId != null || keyword.isNotBlank()
}

interface NameLookup {
    fun categoryName(id: String?): String?
    fun accountName(id: String?): String?

    object Empty : NameLookup {
        override fun categoryName(id: String?): String? = null
        override fun accountName(id: String?): String? = null
    }
}

data class CategoryTotal(
    val categoryId: String?, // null = uncategorized
    val amountMinor: Long,
    val count: Int,
    val sharePercent: Double
)

enum class Granularity { DAY, WEEK, MONTH }

data class BucketPoint(
    val start: LocalDate,
    val end: LocalDate,
    val incomeMinor: Long,
    val expenseMinor: Long
)

data class ReportSummary(
    val span: DateSpan,
    val incomeMinor: Long,
    /** Spending before refunds. */
    val grossExpenseMinor: Long,
    val refundMinor: Long,
    /** Net spending = gross - refunds. This is the figure used everywhere as "expenses". */
    val expenseMinor: Long,
    /** income - expenses. Never an account balance and never "savings" unless positive. */
    val netCashFlowMinor: Long,
    /** net cash flow / income; null when there is no income (not calculable, never invented). */
    val savingsRate: Double?,
    /** expenses / income; null when there is no income. */
    val expenseToIncomeRatio: Double?,
    val expenseByCategory: List<CategoryTotal>,
    val incomeByCategory: List<CategoryTotal>,
    val granularity: Granularity,
    val series: List<BucketPoint>,
    /** Ids of the rows included in the totals, newest first — lists can be reconciled against totals. */
    val countedRowIds: List<String>,
    val incomeCount: Int,
    val expenseCount: Int,
    val excludedTransfers: Int,
    val excludedLoanMovements: Int,
    val excludedOther: Int,
    val dailyAverageExpenseMinor: Long,
    val largestExpenseId: String?,
    val largestExpenseMinor: Long
) {
    val hasActivity: Boolean get() = incomeCount + expenseCount > 0
}

data class Change(val current: Long, val previous: Long) {
    val delta: Long get() = current - previous

    /** Percent change vs previous; null when there is no previous value to compare against. */
    val percent: Double? get() = if (previous == 0L) null else delta * 100.0 / abs(previous)
}

object ReportCalculator {

    fun granularityFor(span: DateSpan): Granularity = when {
        span.dayCount <= 14 -> Granularity.DAY
        span.dayCount <= 92 -> Granularity.WEEK
        else -> Granularity.MONTH
    }

    fun compute(
        rows: List<LedgerRow>,
        span: DateSpan,
        zone: ZoneId,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        filter: ReportFilter = ReportFilter(),
        names: NameLookup = NameLookup.Empty
    ): ReportSummary {
        val startMillis = span.startMillis(zone)
        val endMillis = span.endMillis(zone)
        val keyword = filter.keyword.trim().lowercase()

        var income = 0L
        var gross = 0L
        var refunds = 0L
        var incomeCount = 0
        var expenseCount = 0
        var excludedTransfers = 0
        var excludedLoans = 0
        var excludedOther = 0

        val expenseByCat = LinkedHashMap<String?, LongArray>() // [net, count]
        val incomeByCat = LinkedHashMap<String?, LongArray>()
        val counted = ArrayList<LedgerRow>()

        for (row in rows) {
            if (row.dateMillis < startMillis || row.dateMillis > endMillis) continue
            if (filter.accountId != null &&
                row.sourceAccountId != filter.accountId && row.destinationAccountId != filter.accountId
            ) continue
            if (keyword.isNotEmpty() && !matchesKeyword(row, keyword, names)) continue

            val kind = LedgerRules.kindOf(row.type)
            when (kind) {
                FlowKind.EXCLUDED_TRANSFER -> { excludedTransfers++; continue }
                FlowKind.EXCLUDED_LOAN -> { excludedLoans++; continue }
                FlowKind.EXCLUDED_OTHER -> { excludedOther++; continue }
                else -> Unit
            }
            if (filter.type == TypeFilter.INCOME && kind != FlowKind.INCOME) continue
            if (filter.type == TypeFilter.EXPENSE && kind == FlowKind.INCOME) continue
            if (filter.categoryId != null && row.categoryId != filter.categoryId) continue

            counted += row
            when (kind) {
                FlowKind.INCOME -> {
                    income += row.amountMinor
                    incomeCount++
                    bump(incomeByCat, row.categoryId, row.amountMinor)
                }
                FlowKind.EXPENSE -> {
                    gross += row.amountMinor
                    expenseCount++
                    bump(expenseByCat, row.categoryId, row.amountMinor)
                }
                FlowKind.REFUND -> {
                    refunds += row.amountMinor
                    expenseCount++
                    bump(expenseByCat, row.categoryId, -row.amountMinor)
                }
                else -> Unit
            }
        }

        val expense = gross - refunds
        val net = income - expense
        val granularity = granularityFor(span)

        val effectiveEnd = when {
            today.isBefore(span.start) -> span.start
            today.isBefore(span.end) -> today
            else -> span.end
        }
        val elapsedDays = ChronoUnit.DAYS.between(span.start, effectiveEnd) + 1
        val largest = counted
            .filter { LedgerRules.kindOf(it.type) == FlowKind.EXPENSE }
            .maxByOrNull { it.amountMinor }

        return ReportSummary(
            span = span,
            incomeMinor = income,
            grossExpenseMinor = gross,
            refundMinor = refunds,
            expenseMinor = expense,
            netCashFlowMinor = net,
            savingsRate = if (income > 0) net * 100.0 / income else null,
            expenseToIncomeRatio = if (income > 0) expense * 100.0 / income else null,
            expenseByCategory = toTotals(expenseByCat, positiveOnly = true),
            incomeByCategory = toTotals(incomeByCat, positiveOnly = true),
            granularity = granularity,
            series = buildSeries(counted, span, zone, today, firstDayOfWeek, granularity),
            countedRowIds = counted.sortedByDescending { it.dateMillis }.map { it.id },
            incomeCount = incomeCount,
            expenseCount = expenseCount,
            excludedTransfers = excludedTransfers,
            excludedLoanMovements = excludedLoans,
            excludedOther = excludedOther,
            dailyAverageExpenseMinor = if (expense > 0) expense / elapsedDays else 0L,
            largestExpenseId = largest?.id,
            largestExpenseMinor = largest?.amountMinor ?: 0L
        )
    }

    private fun bump(map: MutableMap<String?, LongArray>, key: String?, amount: Long) {
        val slot = map.getOrPut(key) { LongArray(2) }
        slot[0] += amount
        slot[1] += 1
    }

    /**
     * Share is computed over the sum of the categories actually listed, so the donut always totals
     * 100%. A category whose refunds exceed its spending in the period (net <= 0) is left out of the
     * breakdown but still reduces the headline expense figure.
     */
    private fun toTotals(map: Map<String?, LongArray>, positiveOnly: Boolean): List<CategoryTotal> {
        val listed = map.entries.filter { !positiveOnly || it.value[0] > 0 }
        val denominator = listed.sumOf { it.value[0] }
        return listed
            .map { (key, v) ->
                CategoryTotal(
                    categoryId = key,
                    amountMinor = v[0],
                    count = v[1].toInt(),
                    sharePercent = if (denominator > 0) v[0] * 100.0 / denominator else 0.0
                )
            }
            // Ties: named categories first (by id for a stable order), "Uncategorized" last.
            .sortedWith(
                compareByDescending<CategoryTotal> { it.amountMinor }
                    .thenBy { it.categoryId == null }
                    .thenBy { it.categoryId ?: "" }
            )
    }

    private fun matchesKeyword(row: LedgerRow, keyword: String, names: NameLookup): Boolean =
        listOf(
            row.note,
            names.categoryName(row.categoryId),
            names.accountName(row.sourceAccountId),
            names.accountName(row.destinationAccountId)
        ).any { it != null && it.lowercase().contains(keyword) }

    private fun buildSeries(
        counted: List<LedgerRow>,
        span: DateSpan,
        zone: ZoneId,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        granularity: Granularity
    ): List<BucketPoint> {
        // Bucket boundaries covering the span, clipped to it.
        val buckets = ArrayList<DateSpan>()
        var cursor = span.start
        while (!cursor.isAfter(span.end)) {
            val bucketEnd = when (granularity) {
                Granularity.DAY -> cursor
                Granularity.WEEK -> ReportPeriods.weekContaining(cursor, firstDayOfWeek).end
                Granularity.MONTH -> YearMonth.from(cursor).atEndOfMonth()
            }
            val clipped = if (bucketEnd.isAfter(span.end)) span.end else bucketEnd
            buckets += DateSpan(cursor, clipped)
            cursor = clipped.plusDays(1)
        }

        val income = LongArray(buckets.size)
        val expense = LongArray(buckets.size)
        for (row in counted) {
            val date = Instant.ofEpochMilli(row.dateMillis).atZone(zone).toLocalDate()
            val idx = buckets.indexOfFirst { date in it }
            if (idx < 0) continue
            when (LedgerRules.kindOf(row.type)) {
                FlowKind.INCOME -> income[idx] += row.amountMinor
                FlowKind.EXPENSE -> expense[idx] += row.amountMinor
                FlowKind.REFUND -> expense[idx] -= row.amountMinor
                else -> Unit
            }
        }
        return buckets.indices
            // Don't draw empty future buckets, but never drop a bucket that has activity.
            .filter { !buckets[it].start.isAfter(today) || income[it] != 0L || expense[it] != 0L }
            .map { BucketPoint(buckets[it].start, buckets[it].end, income[it], expense[it]) }
    }
}
