package com.hisab.app.presentation.budgets

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.R
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.domain.budget.BudgetEvaluator
import com.hisab.app.domain.budget.BudgetState
import com.hisab.app.domain.model.BudgetProgressItem
import com.hisab.app.domain.model.BudgetScope
import com.hisab.app.utils.DateRanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class StatusFilter { ACTIVE, PAUSED, ALL }
enum class KindFilter { ALL, OVERALL, CATEGORY }

data class BudgetsUiState(
    val isLoading: Boolean = true,
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    val items: List<BudgetProgressItem> = emptyList(),
    val status: StatusFilter = StatusFilter.ACTIVE,
    val kind: KindFilter = KindFilter.ALL,
    /** True when there are budgets in this month but the filters hide all of them. */
    val hiddenByFilters: Boolean = false,
    /** Totals over the ENABLED MONTHLY budgets in view — paused budgets never count toward them. */
    val activeCount: Int = 0,
    val pausedCount: Int = 0,
    val overCount: Int = 0,
    val totalLimitMinor: Long = 0,
    val totalSpentMinor: Long = 0
)

sealed interface BudgetsEvent {
    /** Show "moved to Trash" with an Undo that restores [budgetId]. */
    data class Deleted(val budgetId: String, val label: String?) : BudgetsEvent
    data class Message(@StringRes val resId: Int) : BudgetsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModel(
    private val userId: String,
    private val budgetRepository: BudgetRepository,
    categoryDao: CategoryDao
) : ViewModel() {

    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val selectedMonth = MutableStateFlow(YearMonth.now(DateRanges.zone()))
    private val status = MutableStateFlow(StatusFilter.ACTIVE)
    private val kind = MutableStateFlow(KindFilter.ALL)

    private val categories = categoryDao.getAllCategories(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<BudgetsUiState> = selectedMonth.flatMapLatest { month ->
        combine(
            budgetRepository.observeBudgetsForMonth(userId, month),
            categories, status, kind
        ) { budgets, cats, statusFilter, kindFilter ->
            val all = budgets.map { bs ->
                val b = bs.budget
                val scope = BudgetScope.valueOf(b.scope)
                BudgetProgressItem(
                    budget = b,
                    categoryName = b.categoryId?.let { id -> cats.firstOrNull { it.id == id }?.name },
                    spentMinor = bs.spentMinor,
                    periodLabel = periodLabel(scope, bs.period.start, bs.period.end),
                    scope = scope,
                    evaluation = BudgetEvaluator.evaluate(b.amountMinor, bs.spentMinor)
                )
            }.sortedWith(
                compareByDescending<BudgetProgressItem> { it.isActive }
                    .thenByDescending { it.evaluation.usedPercent }
                    .thenBy { it.displayName ?: "" }
            )
            val shown = all.filter {
                (when (statusFilter) {
                    StatusFilter.ACTIVE -> it.isActive
                    StatusFilter.PAUSED -> !it.isActive
                    StatusFilter.ALL -> true
                }) && (when (kindFilter) {
                    KindFilter.ALL -> true
                    KindFilter.OVERALL -> it.budget.categoryId == null
                    KindFilter.CATEGORY -> it.budget.categoryId != null
                })
            }
            val active = all.filter { it.isActive && it.evaluation.state != BudgetState.INVALID }
            // Summary totals use MONTHLY budgets only: adding a daily limit to a monthly one would be meaningless.
            val monthly = active.filter { it.scope == BudgetScope.MONTHLY }
            BudgetsUiState(
                isLoading = false,
                monthLabel = month.format(monthFormat),
                isCurrentMonth = month == YearMonth.now(DateRanges.zone()),
                items = shown,
                status = statusFilter,
                kind = kindFilter,
                hiddenByFilters = all.isNotEmpty() && shown.isEmpty(),
                activeCount = all.count { it.isActive },
                pausedCount = all.count { !it.isActive },
                overCount = active.count { it.isOverBudget },
                totalLimitMinor = monthly.sumOf { it.budget.amountMinor },
                totalSpentMinor = monthly.sumOf { it.evaluation.spentMinor }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetsUiState())

    private val _events = MutableSharedFlow<BudgetsEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun previousMonth() { selectedMonth.value = selectedMonth.value.minusMonths(1) }
    fun nextMonth() { selectedMonth.value = selectedMonth.value.plusMonths(1) }
    fun currentMonth() { selectedMonth.value = YearMonth.now(DateRanges.zone()) }
    fun setStatusFilter(f: StatusFilter) { status.value = f }
    fun setKindFilter(f: KindFilter) { kind.value = f }

    fun toggleBudget(id: String, enabled: Boolean) {
        viewModelScope.launch {
            budgetRepository.setBudgetActive(id, enabled)
            _events.emit(BudgetsEvent.Message(if (enabled) R.string.budget_enabled_message else R.string.budget_paused_message))
        }
    }

    fun deleteBudget(item: BudgetProgressItem) {
        viewModelScope.launch {
            budgetRepository.deleteBudget(item.budget.id)
            _events.emit(BudgetsEvent.Deleted(item.budget.id, item.displayName))
        }
    }

    fun restoreBudget(id: String) {
        viewModelScope.launch {
            when (budgetRepository.restoreBudget(id)) {
                is BudgetRepository.SaveResult.Saved -> _events.emit(BudgetsEvent.Message(R.string.budget_restored_message))
                BudgetRepository.SaveResult.Conflict -> _events.emit(BudgetsEvent.Message(R.string.budget_restore_conflict))
                BudgetRepository.SaveResult.NotFound -> _events.emit(BudgetsEvent.Message(R.string.error_generic))
            }
        }
    }

    private fun periodLabel(scope: BudgetScope, start: LocalDate, end: LocalDate): String {
        val day = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        return when (scope) {
            BudgetScope.DAILY -> start.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))
            BudgetScope.WEEKLY -> "${start.format(day)} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))}"
            BudgetScope.MONTHLY -> start.format(monthFormat)
        }
    }
}
