package com.hisab.app.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.PersonRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.model.TransactionDisplayItem
import com.hisab.app.domain.model.TransactionType
import com.hisab.app.domain.usecase.DeleteTransactionUseCase
import com.hisab.app.utils.DateRangeType
import com.hisab.app.utils.DateRanges
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransactionFilter(
    val type: TransactionType? = null,
    val query: String? = null,
    val accountId: String? = null,
    val personId: String? = null,
    val categoryId: String? = null,
    val dateRangeType: DateRangeType? = null
)

class TransactionsViewModel(
    private val userId: String,
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryDao: CategoryDao,
    personRepository: PersonRepository,
    private val deleteTransactionUseCase: DeleteTransactionUseCase
) : ViewModel() {

    private val _filter = MutableStateFlow(TransactionFilter())
    val filter: StateFlow<TransactionFilter> = _filter

    val accounts = accountRepository.getActiveAccounts(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = categoryDao.getActiveCategories(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val people = personRepository.getActivePeople(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items: StateFlow<List<TransactionDisplayItem>> = _filter.flatMapLatest { f ->
        val range = f.dateRangeType?.let { DateRanges.resolve(it) }
        val transactionsFlow = transactionRepository.searchTransactions(
            userId = userId,
            type = f.type?.name,
            accountId = f.accountId,
            categoryId = f.categoryId,
            personId = f.personId,
            startMillis = range?.startMillis,
            endMillis = range?.endMillis,
            noteQuery = f.query
        )
        combine(transactionsFlow, accounts, categories, people) { txns, accs, cats, ppl ->
            txns.map { t ->
                TransactionDisplayItem(
                    transaction = t,
                    categoryName = t.categoryId?.let { id -> cats.find { it.id == id }?.name },
                    sourceAccountName = t.sourceAccountId?.let { id -> accs.find { it.id == id }?.name },
                    destinationAccountName = t.destinationAccountId?.let { id -> accs.find { it.id == id }?.name },
                    personName = t.personId?.let { id -> ppl.find { it.id == id }?.name }
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTypeFilter(type: TransactionType?) {
        _filter.value = _filter.value.copy(type = type)
    }

    fun setSearchQuery(query: String?) {
        _filter.value = _filter.value.copy(query = query)
    }

    fun setAccountFilter(accountId: String?) {
        _filter.value = _filter.value.copy(accountId = accountId)
    }

    fun setPersonFilter(personId: String?) {
        _filter.value = _filter.value.copy(personId = personId)
    }

    fun setCategoryFilter(categoryId: String?) {
        _filter.value = _filter.value.copy(categoryId = categoryId)
    }

    fun setDateRangeType(type: DateRangeType?) {
        _filter.value = _filter.value.copy(dateRangeType = type)
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch { deleteTransactionUseCase(id) }
    }
}
