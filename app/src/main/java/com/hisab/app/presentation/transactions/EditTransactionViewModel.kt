package com.hisab.app.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.usecase.EditTransactionUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface EditTransactionEvent {
    data object Saved : EditTransactionEvent
    data object ValidationError : EditTransactionEvent
    data class ExceedsOutstanding(val outstandingMinor: Long) : EditTransactionEvent
    data object InvalidAccounts : EditTransactionEvent
}

class EditTransactionViewModel(
    private val userId: String,
    private val transactionId: String,
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryDao: CategoryDao,
    private val editTransactionUseCase: EditTransactionUseCase
) : ViewModel() {

    private val _original = MutableStateFlow<TransactionEntity?>(null)
    val original: StateFlow<TransactionEntity?> = _original.asStateFlow()

    val accounts: StateFlow<List<AccountEntity>> =
        accountRepository.getActiveAccounts(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryDao.getActiveCategories(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<EditTransactionEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            _original.value = transactionRepository.getTransactionByIdOnce(transactionId)
        }
    }

    fun save(
        amountMajor: String,
        sourceAccountId: String?,
        destinationAccountId: String?,
        categoryId: String?,
        note: String?,
        dateMillis: Long,
        force: Boolean = false
    ) {
        if (_isSaving.value) return
        val current = _original.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val amountMinor = amountMajor.trim().toDoubleOrNull()?.let { Math.round(it * 100) }
                if (amountMinor == null || amountMinor <= 0) {
                    _events.emit(EditTransactionEvent.ValidationError); return@launch
                }
                when (
                    val result = editTransactionUseCase(
                        userId, current, amountMinor, sourceAccountId, destinationAccountId,
                        categoryId, note?.ifBlank { null }, dateMillis, force
                    )
                ) {
                    is TransactionRepository.RecordResult.Success -> {
                        _original.value = current.copy(
                            amountMinor = amountMinor,
                            sourceAccountId = sourceAccountId,
                            destinationAccountId = destinationAccountId,
                            categoryId = categoryId,
                            note = note,
                            transactionDateMillis = dateMillis
                        )
                        _events.emit(EditTransactionEvent.Saved)
                    }
                    is TransactionRepository.RecordResult.ExceedsOutstanding ->
                        _events.emit(EditTransactionEvent.ExceedsOutstanding(result.outstandingMinor))
                    is TransactionRepository.RecordResult.InvalidInput ->
                        _events.emit(EditTransactionEvent.InvalidAccounts)
                    is TransactionRepository.RecordResult.PossibleDuplicate ->
                        _events.emit(EditTransactionEvent.Saved) // edits never trigger this check
                }
            } finally {
                _isSaving.value = false
            }
        }
    }
}
