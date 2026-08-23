package com.hisab.app.presentation.expense

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.R
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.usecase.RecordExpenseUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AddExpenseEvent {
    data object Saved : AddExpenseEvent
    data class ValidationError(@StringRes val messageRes: Int) : AddExpenseEvent
    data class DuplicateWarning(
        val amount: String, val categoryId: String, val accountId: String, val note: String?
    ) : AddExpenseEvent
}

class AddExpenseViewModel(
    private val userId: String,
    accountRepository: AccountRepository,
    categoryDao: CategoryDao,
    private val recordExpenseUseCase: RecordExpenseUseCase
) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> =
        accountRepository.getActiveAccounts(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryDao.getActiveCategories(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<AddExpenseEvent>()
    val events = _events.asSharedFlow()

    fun submit(amountMajor: String, categoryId: String?, accountId: String?, note: String?, force: Boolean = false) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val amountMinor = parseAmountToMinor(amountMajor)
                if (amountMinor == null || amountMinor <= 0) {
                    _events.emit(AddExpenseEvent.ValidationError(R.string.error_amount_required)); return@launch
                }
                if (categoryId == null) {
                    _events.emit(AddExpenseEvent.ValidationError(R.string.error_category_required)); return@launch
                }
                if (accountId == null) {
                    _events.emit(AddExpenseEvent.ValidationError(R.string.error_account_required)); return@launch
                }

                when (recordExpenseUseCase(userId, amountMinor, categoryId, accountId, note?.ifBlank { null }, force = force)) {
                    is TransactionRepository.RecordResult.Success ->
                        _events.emit(AddExpenseEvent.Saved)
                    is TransactionRepository.RecordResult.PossibleDuplicate ->
                        _events.emit(AddExpenseEvent.DuplicateWarning(amountMajor, categoryId, accountId, note))
                    is TransactionRepository.RecordResult.ExceedsOutstanding,
                    is TransactionRepository.RecordResult.InvalidInput ->
                        Unit // not reachable for a plain expense
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun parseAmountToMinor(input: String): Long? =
        input.trim().toDoubleOrNull()?.let { Math.round(it * 100) }
}
