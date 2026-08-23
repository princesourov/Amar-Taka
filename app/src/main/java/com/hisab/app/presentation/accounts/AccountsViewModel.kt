package com.hisab.app.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.domain.model.AccountType
import com.hisab.app.domain.model.AccountWithBalance
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AccountEvent {
    data object Saved : AccountEvent
    data object ValidationError : AccountEvent
}

class AccountsViewModel(
    private val userId: String,
    private val accountRepository: AccountRepository
) : ViewModel() {

    val accounts: StateFlow<List<AccountWithBalance>> =
        accountRepository.getAccountsWithBalances(userId)
            .map { pairs -> pairs.map { (account, balance) -> AccountWithBalance(account, balance) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<AccountEvent>()
    val events = _events.asSharedFlow()

    suspend fun loadForEdit(accountId: String): AccountEntity? = accountRepository.getAccountByIdOnce(accountId)

    fun createAccount(name: String, type: AccountType, openingBalanceMajor: String, notes: String?) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val openingMinor = parseAmount(openingBalanceMajor)
                if (name.isBlank() || openingMinor == null) {
                    _events.emit(AccountEvent.ValidationError); return@launch
                }
                accountRepository.createAccount(
                    userId, name.trim(), type, type.name.lowercase(), openingMinor, notes?.ifBlank { null }
                )
                _events.emit(AccountEvent.Saved)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateAccount(account: AccountEntity, name: String, openingBalanceMajor: String, notes: String?) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val openingMinor = parseAmount(openingBalanceMajor)
                if (name.isBlank() || openingMinor == null) {
                    _events.emit(AccountEvent.ValidationError); return@launch
                }
                accountRepository.updateAccount(account, name.trim(), openingMinor, notes?.ifBlank { null })
                _events.emit(AccountEvent.Saved)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deactivateAccount(id: String) {
        viewModelScope.launch { accountRepository.deactivateAccount(id) }
    }

    /** Opening balance may legitimately be blank (defaults to 0) or negative (a starting debt). */
    private fun parseAmount(input: String): Long? =
        input.trim().ifBlank { "0" }.toDoubleOrNull()?.let { Math.round(it * 100) }
}
