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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransactionFilter(val type: TransactionType? = null, val query: String? = null)

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

    private val accounts = accountRepository.getActiveAccounts(userId)
    private val categories = categoryDao.getActiveCategories(userId)
    private val people = personRepository.getActivePeople(userId)

    val items: StateFlow<List<TransactionDisplayItem>> = _filter.flatMapLatest { f ->
        val transactionsFlow = transactionRepository.searchTransactions(
            userId = userId,
            type = f.type?.name,
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

    fun deleteTransaction(id: String) {
        viewModelScope.launch { deleteTransactionUseCase(id) }
    }
}
