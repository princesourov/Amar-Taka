package com.hisab.app.presentation.budgets

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.R
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.domain.budget.BudgetRules
import com.hisab.app.domain.budget.MoneyParser
import com.hisab.app.domain.model.BudgetScope
import com.hisab.app.utils.DateRanges
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Everything the form holds. Kept in the ViewModel so a rotation never loses what was typed. */
data class BudgetDraft(
    val name: String = "",
    val amountText: String = "",
    val scope: BudgetScope = BudgetScope.MONTHLY,
    val categoryId: String? = null,
    val startEpochDay: Long = LocalDate.now(DateRanges.zone()).toEpochDay(),
    val isActive: Boolean = true
)

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data class FieldError(val field: Field, @StringRes val messageRes: Int) : EditorEvent
    data class Error(@StringRes val messageRes: Int) : EditorEvent
    enum class Field { NAME, AMOUNT }
}

class BudgetEditorViewModel(
    private val userId: String,
    private val budgetId: String?,
    private val budgetRepository: BudgetRepository,
    categoryDao: CategoryDao
) : ViewModel() {

    val isEditing: Boolean = budgetId != null

    /** Expense categories only — a budget limits spending, so income categories make no sense here. */
    val categories: StateFlow<List<CategoryEntity>> =
        categoryDao.getActiveCategoriesByKinds(userId, listOf("EXPENSE", "BOTH"))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _draft = MutableStateFlow(BudgetDraft())
    val draft: StateFlow<BudgetDraft> = _draft.asStateFlow()

    private val _loaded = MutableStateFlow(budgetId == null)
    /** False only while an existing budget is being read from the database. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        if (budgetId != null) {
            viewModelScope.launch {
                val b = budgetRepository.getBudgetById(budgetId)
                if (b == null) {
                    _events.emit(EditorEvent.Error(R.string.error_generic))
                } else {
                    _draft.value = BudgetDraft(
                        name = b.name.orEmpty(),
                        amountText = MoneyParser.toPlainDecimal(b.amountMinor),
                        scope = BudgetScope.valueOf(b.scope),
                        categoryId = b.categoryId,
                        startEpochDay = b.startDateEpochDay ?: LocalDate.now(DateRanges.zone()).toEpochDay(),
                        isActive = b.isActive
                    )
                    _loaded.value = true
                }
            }
        }
    }

    fun update(change: (BudgetDraft) -> BudgetDraft) { _draft.value = change(_draft.value) }

    fun save() {
        if (_saving.value) return // blocks double taps
        val d = _draft.value
        when (val v = BudgetRules.validate(d.amountText, d.name)) {
            BudgetRules.Validation.AmountRequired -> fieldError(EditorEvent.Field.AMOUNT, R.string.error_amount_required)
            BudgetRules.Validation.AmountInvalid -> fieldError(EditorEvent.Field.AMOUNT, R.string.error_budget_amount_invalid)
            BudgetRules.Validation.AmountTooManyDecimals -> fieldError(EditorEvent.Field.AMOUNT, R.string.error_budget_amount_decimals)
            BudgetRules.Validation.AmountNotPositive -> fieldError(EditorEvent.Field.AMOUNT, R.string.error_budget_amount_positive)
            BudgetRules.Validation.AmountTooLarge -> fieldError(EditorEvent.Field.AMOUNT, R.string.error_budget_amount_large)
            BudgetRules.Validation.NameTooLong -> fieldError(EditorEvent.Field.NAME, R.string.error_budget_name_long)
            is BudgetRules.Validation.Valid -> {
                _saving.value = true
                viewModelScope.launch {
                    try {
                        val result = if (budgetId == null) {
                            budgetRepository.createBudget(userId, v.name, d.scope, d.categoryId, v.amountMinor, d.startEpochDay, d.isActive)
                        } else {
                            budgetRepository.updateBudget(budgetId, v.name, d.scope, d.categoryId, v.amountMinor, d.startEpochDay, d.isActive)
                        }
                        when (result) {
                            is BudgetRepository.SaveResult.Saved -> _events.emit(EditorEvent.Saved)
                            BudgetRepository.SaveResult.Conflict -> _events.emit(EditorEvent.Error(R.string.error_budget_duplicate))
                            BudgetRepository.SaveResult.NotFound -> _events.emit(EditorEvent.Error(R.string.error_generic))
                        }
                    } catch (e: Exception) {
                        _events.emit(EditorEvent.Error(R.string.error_generic))
                    } finally {
                        _saving.value = false
                    }
                }
            }
        }
    }

    private fun fieldError(field: EditorEvent.Field, @StringRes res: Int) {
        _events.tryEmit(EditorEvent.FieldError(field, res))
    }
}
