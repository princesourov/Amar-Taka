package com.hisab.app.presentation.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.domain.model.BudgetProgressItem
import com.hisab.app.domain.model.BudgetScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BudgetEditorState(
    val budgetId: String? = null,
    val scope: BudgetScope = BudgetScope.MONTHLY,
    val categoryId: String? = null,
    val amountText: String = "",
    val month: YearMonth = YearMonth.now(),
    val isActive: Boolean = true
)

sealed interface BudgetEvent {
    data object Saved : BudgetEvent
    data object ValidationError : BudgetEvent
    data class EditReady(val state: BudgetEditorState) : BudgetEvent
}

class BudgetsViewModel(
    private val userId: String,
    private val budgetRepository: BudgetRepository,
    categoryDao: CategoryDao
) : ViewModel() {

    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryDao.getActiveCategories(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentMonthLabel: StateFlow<String> =
        selectedMonth
            .map { month -> month.format(monthFormat) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YearMonth.now().format(monthFormat))

    val budgetProgress: StateFlow<List<BudgetProgressItem>> = selectedMonth.flatMapLatest { month ->
        combine(
            budgetRepository.getBudgetsWithSpentForMonth(userId, month),
            categories
        ) { budgetsWithSpent, categoryList ->
            budgetsWithSpent.map { (budget, spent) ->
                val categoryName = budget.categoryId?.let { id -> categoryList.find { it.id == id }?.name }
                val periodMonth = budget.startDateEpochDay?.let { YearMonth.from(LocalDate.ofEpochDay(it)) } ?: month
                BudgetProgressItem(
                    budget = budget,
                    categoryName = categoryName,
                    spentMinor = spent,
                    periodLabel = periodMonth.format(monthFormat)
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<BudgetProgressItem>> = combine(
        budgetRepository.getAllBudgets(userId),
        categories
    ) { budgets, categoryList ->
        budgets to categoryList
    }.flatMapLatest { (budgets, categoryList) ->
        if (budgets.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(budgets.map { b -> budgetRepository.getSpent(userId, b).map { spent -> b to spent } }) { spentPairs ->
                spentPairs.map { (budget, spent) ->
                    val categoryName = budget.categoryId?.let { id -> categoryList.find { it.id == id }?.name }
                    val periodMonth = budget.startDateEpochDay?.let { YearMonth.from(LocalDate.ofEpochDay(it)) } ?: YearMonth.now()
                    BudgetProgressItem(
                        budget = budget,
                        categoryName = categoryName,
                        spentMinor = spent,
                        periodLabel = periodMonth.format(monthFormat)
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<BudgetEvent>()
    val events = _events.asSharedFlow()

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun currentMonth() {
        selectedMonth.value = YearMonth.now()
    }

    fun createBudget(scope: BudgetScope, categoryId: String?, amountMajor: String, month: YearMonth = selectedMonth.value) {
        saveBudget(
            BudgetEditorState(
                scope = scope,
                categoryId = categoryId,
                amountText = amountMajor,
                month = month,
                isActive = true
            )
        )
    }

    fun saveBudget(state: BudgetEditorState) {
        viewModelScope.launch {
            val amountMinor = state.amountText.trim().toDoubleOrNull()?.let { Math.round(it * 100) }
            if (amountMinor == null || amountMinor <= 0) {
                _events.emit(BudgetEvent.ValidationError)
                return@launch
            }
            val startEpoch = state.month.atDay(1).toEpochDay()
            if (state.budgetId == null) {
                budgetRepository.createBudget(userId, state.scope, state.categoryId, amountMinor, startEpoch)
            } else {
                budgetRepository.updateBudget(
                    budgetId = state.budgetId,
                    scope = state.scope,
                    categoryId = state.categoryId,
                    amountMinor = amountMinor,
                    startDateEpochDay = startEpoch,
                    isActive = state.isActive
                )
            }
            _events.emit(BudgetEvent.Saved)
        }
    }

    fun loadBudgetForEdit(id: String) {
        viewModelScope.launch {
            val budget = budgetRepository.getBudgetById(id) ?: return@launch
            _events.emit(BudgetEvent.EditReady(budget.toEditorState()))
        }
    }

    fun toggleBudget(id: String, enabled: Boolean) {
        viewModelScope.launch { budgetRepository.setBudgetActive(id, enabled) }
    }

    fun deleteBudget(id: String) {
        viewModelScope.launch { budgetRepository.deleteBudget(id) }
    }

    private fun BudgetEntity.toEditorState(): BudgetEditorState {
        val month = startDateEpochDay?.let { YearMonth.from(LocalDate.ofEpochDay(it)) } ?: YearMonth.now()
        return BudgetEditorState(
            budgetId = id,
            scope = BudgetScope.valueOf(scope),
            categoryId = categoryId,
            amountText = (amountMinor.toDouble() / 100.0).toString(),
            month = month,
            isActive = isActive
        )
    }
}
