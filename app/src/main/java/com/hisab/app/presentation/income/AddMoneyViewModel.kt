package com.hisab.app.presentation.income

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.R
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.usecase.RecordIncomeUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AddMoneyEvent {
    data object Saved : AddMoneyEvent
    data class ValidationError(@StringRes val messageRes: Int) : AddMoneyEvent
    data class DuplicateWarning(
        val amount: String, val accountId: String, val categoryId: String?, val note: String?, val dateMillis: Long
    ) : AddMoneyEvent
}

class AddMoneyViewModel(
    private val userId: String,
    accountRepository: AccountRepository,
    categoryDao: CategoryDao,
    private val recordIncomeUseCase: RecordIncomeUseCase
) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> =
        accountRepository.getActiveAccounts(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryDao.getActiveCategories(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    /** The Fragment disables its Save button while this is true — the explicit fix for
     *  double-tap double-submission, since the duplicate-check itself is a check-then-insert
     *  that isn't atomic against two near-simultaneous calls. */
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<AddMoneyEvent>()
    val events = _events.asSharedFlow()

    fun submit(
        amountMajor: String,
        accountId: String?,
        categoryId: String?,
        note: String?,
        dateMillis: Long,
        force: Boolean = false
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val amountMinor = amountMajor.trim().toDoubleOrNull()?.let { Math.round(it * 100) }
                if (amountMinor == null || amountMinor <= 0) {
                    _events.emit(AddMoneyEvent.ValidationError(R.string.error_amount_required)); return@launch
                }
                if (accountId == null) {
                    _events.emit(AddMoneyEvent.ValidationError(R.string.error_account_required)); return@launch
                }

                when (
                    val result = recordIncomeUseCase(
                        userId, amountMinor, accountId, categoryId, note?.ifBlank { null }, dateMillis, force = force
                    )
                ) {
                    is TransactionRepository.RecordResult.Success ->
                        _events.emit(AddMoneyEvent.Saved)
                    is TransactionRepository.RecordResult.PossibleDuplicate ->
                        _events.emit(AddMoneyEvent.DuplicateWarning(amountMajor, accountId, categoryId, note, dateMillis))
                    is TransactionRepository.RecordResult.ExceedsOutstanding,
                    is TransactionRepository.RecordResult.InvalidInput ->
                        Unit // not reachable for a plain income entry
                }
            } finally {
                _isSaving.value = false
            }
        }
    }
}
