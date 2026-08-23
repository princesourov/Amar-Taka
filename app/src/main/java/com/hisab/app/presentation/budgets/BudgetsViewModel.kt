package com.hisab.app.presentation.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.domain.model.BudgetProgressItem
import com.hisab.app.domain.model.BudgetScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CreateBudgetEvent {
    data object Saved : CreateBudgetEvent
    data object ValidationError : CreateBudgetEvent
}

class BudgetsViewModel(
    private val userId: String,
    private val budgetRepository: BudgetRepository,
    categoryDao: CategoryDao
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> =
        categoryDao.getActiveCategories(userId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgetProgress: StateFlow<List<BudgetProgressItem>> = combine(
        budgetRepository.getBudgetsWithSpent(userId),
        categories
    ) { budgetsWithSpent, categoryList ->
        budgetsWithSpent.map { (budget, spent) ->
            val categoryName = budget.categoryId?.let { id -> categoryList.find { it.id == id }?.name }
            BudgetProgressItem(budget, categoryName, spent)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<CreateBudgetEvent>()
    val events = _events.asSharedFlow()

    fun createBudget(scope: BudgetScope, categoryId: String?, amountMajor: String) {
        viewModelScope.launch {
            val amountMinor = amountMajor.trim().toDoubleOrNull()?.let { Math.round(it * 100) }
            if (amountMinor == null || amountMinor <= 0) {
                _events.emit(CreateBudgetEvent.ValidationError); return@launch
            }
            budgetRepository.createBudget(userId, scope, categoryId, amountMinor)
            _events.emit(CreateBudgetEvent.Saved)
        }
    }
}
