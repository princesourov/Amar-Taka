package com.hisab.app.presentation.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.RecurringTransactionRepository
import com.hisab.app.domain.model.RecurrenceFrequency
import com.hisab.app.domain.model.TransactionType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface RecurringEvent {
    data object Saved : RecurringEvent
    data object ValidationError : RecurringEvent
}

class RecurringViewModel(
    private val userId: String,
    private val recurringRepository: RecurringTransactionRepository,
    accountRepository: AccountRepository,
    categoryDao: CategoryDao
) : ViewModel() {

    val recurringTransactions: StateFlow<List<RecurringTransactionEntity>> =
        recurringRepository.getActiveRecurring(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> =
        accountRepository.getActiveAccounts(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryDao.getActiveCategories(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<RecurringEvent>()
    val events = _events.asSharedFlow()

    fun create(
        type: TransactionType,
        amountMajor: String,
        categoryId: String?,
        accountId: String?,
        frequency: RecurrenceFrequency,
        note: String?
    ) {
        viewModelScope.launch {
            val amountMinor = amountMajor.trim().toDoubleOrNull()?.let { Math.round(it * 100) }
            val invalid = amountMinor == null || amountMinor <= 0 || accountId == null ||
                (type == TransactionType.EXPENSE && categoryId == null)
            if (invalid) {
                _events.emit(RecurringEvent.ValidationError); return@launch
            }
            recurringRepository.createRecurring(
                userId, type, amountMinor!!, categoryId, accountId!!, frequency,
                startDateEpochDay = LocalDate.now().toEpochDay(), note = note?.ifBlank { null }
            )
            _events.emit(RecurringEvent.Saved)
        }
    }
}
