package com.hisab.app.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.PersonRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.model.TransactionDisplayItem
import com.hisab.app.domain.usecase.BorrowMoneyUseCase
import com.hisab.app.domain.usecase.DeleteTransactionUseCase
import com.hisab.app.domain.usecase.LendMoneyUseCase
import com.hisab.app.domain.usecase.RecordRepaymentMadeUseCase
import com.hisab.app.domain.usecase.RecordRepaymentReceivedUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PersonMoneyAction { GIVE, TAKE, RECEIVE_REPAYMENT, MAKE_REPAYMENT }

sealed interface PersonActionEvent {
    data object Saved : PersonActionEvent
    data object ValidationError : PersonActionEvent
    data class ExceedsOutstanding(val outstandingMinor: Long) : PersonActionEvent
    data class DuplicateWarning(
        val action: PersonMoneyAction, val amount: String, val accountId: String, val note: String?
    ) : PersonActionEvent
}

class PersonDetailViewModel(
    private val userId: String,
    private val personId: String,
    personRepository: PersonRepository,
    accountRepository: AccountRepository,
    categoryDao: CategoryDao,
    private val lendMoneyUseCase: LendMoneyUseCase,
    private val borrowMoneyUseCase: BorrowMoneyUseCase,
    private val recordRepaymentReceivedUseCase: RecordRepaymentReceivedUseCase,
    private val recordRepaymentMadeUseCase: RecordRepaymentMadeUseCase,
    private val deleteTransactionUseCase: DeleteTransactionUseCase
) : ViewModel() {

    val person: StateFlow<PersonEntity?> =
        personRepository.observePerson(personId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val netBalanceMinor: StateFlow<Long> =
        personRepository.getNetBalance(userId, personId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val transactions: StateFlow<List<TransactionDisplayItem>> = combine(
        personRepository.getTransactionsForPerson(userId, personId),
        accountRepository.getActiveAccounts(userId),
        categoryDao.getActiveCategories(userId),
        person
    ) { txns, accs, cats, currentPerson ->
        txns.map { t ->
            TransactionDisplayItem(
                transaction = t,
                categoryName = t.categoryId?.let { id -> cats.find { it.id == id }?.name },
                sourceAccountName = t.sourceAccountId?.let { id -> accs.find { it.id == id }?.name },
                destinationAccountName = t.destinationAccountId?.let { id -> accs.find { it.id == id }?.name },
                personName = currentPerson?.name
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> =
        accountRepository.getActiveAccounts(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<PersonActionEvent>()
    val events = _events.asSharedFlow()

    fun performAction(
        action: PersonMoneyAction, amountMajor: String, accountId: String?, note: String?, force: Boolean = false
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val amountMinor = amountMajor.trim().toDoubleOrNull()?.let { Math.round(it * 100) }
                if (amountMinor == null || amountMinor <= 0 || accountId == null) {
                    _events.emit(PersonActionEvent.ValidationError); return@launch
                }
                val trimmedNote = note?.ifBlank { null }
                val result = when (action) {
                    PersonMoneyAction.GIVE ->
                        lendMoneyUseCase(userId, amountMinor, personId, accountId, trimmedNote, force = force)
                    PersonMoneyAction.TAKE ->
                        borrowMoneyUseCase(userId, amountMinor, personId, accountId, trimmedNote, force = force)
                    PersonMoneyAction.RECEIVE_REPAYMENT ->
                        recordRepaymentReceivedUseCase(userId, amountMinor, personId, accountId, trimmedNote, force = force)
                    PersonMoneyAction.MAKE_REPAYMENT ->
                        recordRepaymentMadeUseCase(userId, amountMinor, personId, accountId, trimmedNote, force = force)
                }
                when (result) {
                    is TransactionRepository.RecordResult.Success ->
                        _events.emit(PersonActionEvent.Saved)
                    is TransactionRepository.RecordResult.ExceedsOutstanding ->
                        _events.emit(PersonActionEvent.ExceedsOutstanding(result.outstandingMinor))
                    is TransactionRepository.RecordResult.PossibleDuplicate ->
                        _events.emit(PersonActionEvent.DuplicateWarning(action, amountMajor, accountId, note))
                    is TransactionRepository.RecordResult.InvalidInput ->
                        Unit // not reachable for a person-linked transaction
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch { deleteTransactionUseCase(transactionId) }
    }
}
