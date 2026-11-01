package com.hisab.app.presentation.trash

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.R
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.data.repository.TrashRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TrashTab { TRANSACTIONS, BUDGETS }

sealed interface TrashEntry {
    val id: String
    val deletedAtMillis: Long

    data class Txn(
        val transaction: TransactionEntity,
        val categoryName: String?,
        val accountName: String?
    ) : TrashEntry {
        override val id get() = transaction.id
        override val deletedAtMillis get() = transaction.updatedAtMillis
    }

    data class Budget(val budget: BudgetEntity, val categoryName: String?) : TrashEntry {
        override val id get() = budget.id
        override val deletedAtMillis get() = budget.updatedAtMillis
    }
}

data class TrashUiState(
    val tab: TrashTab = TrashTab.TRANSACTIONS,
    val entries: List<TrashEntry> = emptyList(),
    val transactionCount: Int = 0,
    val budgetCount: Int = 0
) {
    val isEmptyOverall: Boolean get() = transactionCount == 0 && budgetCount == 0
}

class TrashViewModel(
    private val userId: String,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val trashRepository: TrashRepository,
    accountRepository: AccountRepository,
    categoryDao: CategoryDao
) : ViewModel() {

    private val tab = MutableStateFlow(TrashTab.TRANSACTIONS)

    val uiState: StateFlow<TrashUiState> = combine(
        tab,
        transactionRepository.getDeletedTransactions(userId),
        budgetRepository.getDeletedBudgets(userId),
        categoryDao.getAllCategories(userId),
        accountRepository.getActiveAccounts(userId)
    ) { tab, txns, budgets, cats, accounts ->
        val catName = cats.associate { it.id to it.name }
        val accName = accounts.associate { it.id to it.name }
        val entries: List<TrashEntry> = when (tab) {
            TrashTab.TRANSACTIONS -> txns.map {
                TrashEntry.Txn(it, it.categoryId?.let(catName::get), (it.sourceAccountId ?: it.destinationAccountId)?.let(accName::get))
            }
            TrashTab.BUDGETS -> budgets.map { TrashEntry.Budget(it, it.categoryId?.let(catName::get)) }
        }
        TrashUiState(tab, entries.sortedByDescending { it.deletedAtMillis }, txns.size, budgets.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrashUiState())

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun selectTab(t: TrashTab) { tab.value = t }

    fun restore(entry: TrashEntry) {
        viewModelScope.launch {
            when (entry) {
                is TrashEntry.Txn -> {
                    transactionRepository.restoreTransaction(entry.id)
                    say(R.string.trash_restored)
                }
                is TrashEntry.Budget -> when (budgetRepository.restoreBudget(entry.id)) {
                    is BudgetRepository.SaveResult.Saved -> say(R.string.budget_restored_message)
                    BudgetRepository.SaveResult.Conflict -> say(R.string.budget_restore_conflict)
                    BudgetRepository.SaveResult.NotFound -> say(R.string.error_generic)
                }
            }
        }
    }

    fun purge(entry: TrashEntry) {
        viewModelScope.launch {
            when (entry) {
                is TrashEntry.Txn -> trashRepository.purgeTransaction(entry.id)
                is TrashEntry.Budget -> trashRepository.purgeBudget(entry.id)
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch { trashRepository.emptyTrash(userId) }
    }

    private fun say(@StringRes res: Int) { _messages.tryEmit(res) }
}
