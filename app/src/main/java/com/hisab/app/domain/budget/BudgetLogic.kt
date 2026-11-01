package com.hisab.app.domain.budget

import com.hisab.app.domain.model.BudgetScope
import com.hisab.app.domain.report.DateSpan
import com.hisab.app.domain.report.ReportPeriods
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Budget rules as pure functions.
 *
 * Model: a budget is a RECURRING limit. A MONTHLY budget of 3,000 means 3,000 per calendar month,
 * every month from its start date onward, until it is paused or deleted. DAILY and WEEKLY work the
 * same way per calendar day / calendar week. The start date only says when the budget takes effect;
 * it does not shift period boundaries.
 */
object BudgetPeriods {

    /** The budget period (day / week / month) that contains [reference]. */
    fun periodContaining(scope: BudgetScope, reference: LocalDate, firstDayOfWeek: DayOfWeek): DateSpan =
        when (scope) {
            BudgetScope.DAILY -> DateSpan(reference, reference)
            BudgetScope.WEEKLY -> ReportPeriods.weekContaining(reference, firstDayOfWeek)
            BudgetScope.MONTHLY -> ReportPeriods.monthSpan(YearMonth.from(reference))
        }

    /** A budget with no start date is treated as always in effect. */
    fun isInEffect(startEpochDay: Long?, period: DateSpan): Boolean =
        startEpochDay == null || !LocalDate.ofEpochDay(startEpochDay).isAfter(period.end)

    /**
     * The date a month view should evaluate DAILY/WEEKLY budgets at: today when looking at the
     * current month, otherwise the last day of the viewed month. MONTHLY budgets don't care.
     */
    fun referenceDateForMonth(month: YearMonth, today: LocalDate): LocalDate = when {
        month == YearMonth.from(today) -> today
        month.isBefore(YearMonth.from(today)) -> month.atEndOfMonth()
        else -> month.atDay(1)
    }
}

enum class BudgetState {
    /** Under 80% used. */
    ON_TRACK,
    /** 80% up to (but not including) 100%. */
    NEAR_LIMIT,
    /** Spent exactly the limit. */
    LIMIT_REACHED,
    /** Spent more than the limit. */
    OVER,
    /** Limit is zero or negative — cannot be evaluated meaningfully. */
    INVALID
}

data class BudgetEvaluation(
    val limitMinor: Long,
    val spentMinor: Long,
    val state: BudgetState
) {
    /** limit - spent; negative means overspent. */
    val remainingMinor: Long get() = limitMinor - spentMinor
    val overspentMinor: Long get() = if (spentMinor > limitMinor) spentMinor - limitMinor else 0L

    /** spent / limit as a percentage (can exceed 100). 0 for an invalid budget. */
    val usedPercent: Double get() = if (limitMinor <= 0) 0.0 else spentMinor * 100.0 / limitMinor

    /** Progress bar fill, clamped to 0..100. */
    val progressPercent: Int get() = usedPercent.coerceIn(0.0, 100.0).toInt()
}

object BudgetEvaluator {
    const val NEAR_LIMIT_PERCENT = 80

    /** [spentMinor] is net spending (expenses minus refunds), see LedgerRules. A negative net is shown as 0 spent. */
    fun evaluate(limitMinor: Long, spentMinor: Long): BudgetEvaluation {
        val spent = spentMinor.coerceAtLeast(0L)
        val state = when {
            limitMinor <= 0L -> BudgetState.INVALID
            spent > limitMinor -> BudgetState.OVER
            spent == limitMinor -> BudgetState.LIMIT_REACHED
            spent * 100 >= limitMinor * NEAR_LIMIT_PERCENT -> BudgetState.NEAR_LIMIT
            else -> BudgetState.ON_TRACK
        }
        return BudgetEvaluation(limitMinor, spent, state)
    }
}

/** What the duplicate/conflict rule needs to know about an existing budget. */
data class BudgetKey(
    val id: String,
    val scope: BudgetScope,
    val categoryId: String?,
    val isDeleted: Boolean = false
)

object BudgetRules {
    const val MAX_NAME_LENGTH = 40

    /**
     * Only ONE budget may exist per (period, category-or-overall) pair — two monthly "Food" budgets
     * would double-report the same spending. Paused budgets count (re-enabling one must never create
     * a conflict); deleted ones don't (they're in Trash and can be restored or purged).
     */
    fun findConflict(existing: List<BudgetKey>, scope: BudgetScope, categoryId: String?, ignoreId: String?): BudgetKey? =
        existing.firstOrNull {
            !it.isDeleted && it.id != ignoreId && it.scope == scope && it.categoryId == categoryId
        }

    sealed interface Validation {
        data class Valid(val amountMinor: Long, val name: String?) : Validation
        data object AmountRequired : Validation
        data object AmountInvalid : Validation
        data object AmountTooManyDecimals : Validation
        data object AmountNotPositive : Validation
        data object AmountTooLarge : Validation
        data object NameTooLong : Validation
    }

    fun validate(amountText: String?, name: String?): Validation {
        val cleanName = name?.trim()?.ifEmpty { null }
        if (cleanName != null && cleanName.length > MAX_NAME_LENGTH) return Validation.NameTooLong
        return when (val parsed = MoneyParser.parsePositive(amountText)) {
            is MoneyParser.Result.Ok -> Validation.Valid(parsed.minorUnits, cleanName)
            MoneyParser.Result.Empty -> Validation.AmountRequired
            MoneyParser.Result.Invalid -> Validation.AmountInvalid
            MoneyParser.Result.TooManyDecimals -> Validation.AmountTooManyDecimals
            MoneyParser.Result.NotPositive -> Validation.AmountNotPositive
            MoneyParser.Result.TooLarge -> Validation.AmountTooLarge
        }
    }
}
