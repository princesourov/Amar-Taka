package com.hisab.app.presentation.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.usecase.TransferMoneyUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TransferEvent {
    data object Saved : TransferEvent
    data object ValidationError : TransferEvent
    data object SameAccount : TransferEvent
    data class DuplicateWarning(
        val amount: String, val fromAccountId: String, val toAccountId: String, val note: String?
    ) : TransferEvent
}

class TransferMoneyViewModel(
    private val userId: String,
    accountRepository: AccountRepository,
    private val transferMoneyUseCase: TransferMoneyUseCase
) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> =
        accountRepository.getActiveAccounts(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<TransferEvent>()
    val events = _events.asSharedFlow()

    fun submit(amountMajor: String, fromAccountId: String?, toAccountId: String?, note: String?, force: Boolean = false) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val amountMinor = amountMajor.trim().toDoubleOrNull()?.let { Math.round(it * 100) }
                if (amountMinor == null || amountMinor <= 0 || fromAccountId == null || toAccountId == null) {
                    _events.emit(TransferEvent.ValidationError); return@launch
                }
                when (
                    val result = transferMoneyUseCase(userId, amountMinor, fromAccountId, toAccountId, note?.ifBlank { null }, force = force)
                ) {
                    is TransactionRepository.RecordResult.Success ->
                        _events.emit(TransferEvent.Saved)
                    is TransactionRepository.RecordResult.InvalidInput ->
                        _events.emit(TransferEvent.SameAccount)
                    is TransactionRepository.RecordResult.PossibleDuplicate ->
                        _events.emit(TransferEvent.DuplicateWarning(amountMajor, fromAccountId, toAccountId, note))
                    is TransactionRepository.RecordResult.ExceedsOutstanding ->
                        Unit // not reachable for a transfer
                }
            } finally {
                _isSaving.value = false
            }
        }
    }
}
