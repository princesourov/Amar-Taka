package com.hisab.app.presentation.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.RecurringTransactionRepository
import com.hisab.app.domain.budget.MoneyParser
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
    categoryDao: CategoryDao,
    private val executeRecurring: com.hisab.app.domain.usecase.ExecuteRecurringTransactionsUseCase
) : ViewModel() {

    val recurringTransactions: StateFlow<List<RecurringTransactionEntity>> =
        recurringRepository.getAllRecurring(userId)
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
            val amountMinor = (MoneyParser.parsePositive(amountMajor) as? MoneyParser.Result.Ok)?.minorUnits
            val invalid = amountMinor == null || amountMinor <= 0 || accountId == null ||
                (type == TransactionType.EXPENSE && categoryId == null)
            if (invalid || amountMinor == null || accountId == null) {
                _events.emit(RecurringEvent.ValidationError); return@launch
            }
            recurringRepository.createRecurring(
                userId, type, amountMinor, categoryId, accountId, frequency,
                startDateEpochDay = LocalDate.now().toEpochDay(), note = note?.ifBlank { null }
            )
            // A schedule starting today should produce its first transaction now, not at the next app launch.
            executeRecurring(userId)
            _events.emit(RecurringEvent.Saved)
        }
    }

    /** Saves changes to an existing schedule. Past generated transactions are never rewritten. */
    fun update(
        id: String, type: TransactionType, amountMajor: String, categoryId: String?, accountId: String?,
        frequency: RecurrenceFrequency, note: String?
    ) {
        viewModelScope.launch {
            val amountMinor = MoneyParser.parsePositiveMinor(amountMajor)
            if (amountMinor == null || accountId == null || (type == TransactionType.EXPENSE && categoryId == null)) {
                _events.emit(RecurringEvent.ValidationError); return@launch
            }
            val existing = recurringRepository.getById(id)
            val ok = recurringRepository.update(
                id, type, amountMinor, categoryId, accountId, frequency, existing?.endDateEpochDay, note?.ifBlank { null }
            )
            if (ok) { executeRecurring(userId); _events.emit(RecurringEvent.Saved) } else _events.emit(RecurringEvent.ValidationError)
        }
    }

    suspend fun load(id: String): RecurringTransactionEntity? = recurringRepository.getById(id)

    /** Transactions this schedule has generated, newest first (trashed ones are flagged by isDeleted). */
    suspend fun history(id: String) = recurringRepository.history(id)

    /** Schedules the last run could not process (and why), so the screen can flag them instead of silently doing nothing. */
    private val _attention = kotlinx.coroutines.flow.MutableStateFlow<Map<String, com.hisab.app.domain.usecase.SkipReason>>(emptyMap())
    val needsAttention: StateFlow<Map<String, com.hisab.app.domain.usecase.SkipReason>> = _attention

    fun refreshAttention() {
        viewModelScope.launch {
            val result = executeRecurring.run(userId)
            _attention.value = result.skipped.associate { it.scheduleId to it.reason }
        }
    }

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch { recurringRepository.setActive(id, active) }
    }

    /** Removes the schedule; already generated transactions are kept. */
    fun delete(id: String) {
        viewModelScope.launch { recurringRepository.delete(id) }
    }
}
