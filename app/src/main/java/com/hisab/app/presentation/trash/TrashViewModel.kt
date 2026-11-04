package com.hisab.app.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.R
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.BudgetEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.data.local.entity.SavingsGoalEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.data.repository.PersonRepository
import com.hisab.app.data.repository.SavingsGoalRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.data.repository.TrashRepository
import com.hisab.app.data.repository.TrashResult
import com.hisab.app.domain.trash.PurgeBlock
import com.hisab.app.presentation.common.UiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TrashTab { TRANSACTIONS, BUDGETS, GOALS, CATEGORIES, ACCOUNTS, PEOPLE }

/** One row of the Trash screen. [deletedAtMillis] is null for old rows whose deletion time was never recorded. */
sealed interface TrashEntry {
    val id: String
    val deletedAtMillis: Long?

    data class Txn(val transaction: TransactionEntity, val categoryName: String?, val accountName: String?, val personName: String?) : TrashEntry {
        override val id get() = transaction.id
        override val deletedAtMillis: Long? get() = transaction.updatedAtMillis
    }

    data class Budget(val budget: BudgetEntity, val categoryName: String?) : TrashEntry {
        override val id get() = budget.id
        override val deletedAtMillis: Long? get() = budget.updatedAtMillis
    }

    data class Goal(val goal: SavingsGoalEntity) : TrashEntry {
        override val id get() = goal.id
        override val deletedAtMillis get() = goal.deletedAtMillis
    }

    data class Category(val category: CategoryEntity) : TrashEntry {
        override val id get() = category.id
        override val deletedAtMillis get() = category.deletedAtMillis
    }

    data class Account(val account: AccountEntity) : TrashEntry {
        override val id get() = account.id
        override val deletedAtMillis get() = account.deletedAtMillis
    }

    data class Person(val person: PersonEntity) : TrashEntry {
        override val id get() = person.id
        override val deletedAtMillis get() = person.deletedAtMillis
    }
}

data class TrashUiState(
    val tab: TrashTab = TrashTab.TRANSACTIONS,
    val entries: List<TrashEntry> = emptyList(),
    val counts: Map<TrashTab, Int> = TrashTab.values().associateWith { 0 }
) {
    val totalCount: Int get() = counts.values.sum()
    val isEmptyOverall: Boolean get() = totalCount == 0
}

class TrashViewModel(
    private val userId: String,
    private val transactionRepository: TransactionRepository,
    private val trashRepository: TrashRepository,
    private val goalRepository: SavingsGoalRepository,
    private val accountRepository: AccountRepository,
    private val personRepository: PersonRepository,
    budgetRepository: BudgetRepository,
    categoryDao: CategoryDao
) : ViewModel() {

    private val tab = MutableStateFlow(TrashTab.TRANSACTIONS)

    private class Sources(
        val txns: List<TransactionEntity>, val budgets: List<BudgetEntity>, val goals: List<SavingsGoalEntity>,
        val categories: List<CategoryEntity>, val accounts: List<AccountEntity>, val people: List<PersonEntity>
    )

    private val sources: Flow<Sources> = combine(
        transactionRepository.getDeletedTransactions(userId),
        budgetRepository.getDeletedBudgets(userId),
        goalRepository.getDeletedGoals(userId),
        categoryDao.getDeletedCategories(userId),
        combine(accountRepository.getDeletedAccounts(userId), personRepository.getDeletedPeople(userId)) { a, p -> a to p }
    ) { txns, budgets, goals, cats, ap -> Sources(txns, budgets, goals, cats, ap.first, ap.second) }

    // Names for transactions/budgets come from ALL categories/accounts/people, including archived ones.
    private val names: Flow<Triple<Map<String, String>, Map<String, String>, Map<String, String>>> = combine(
        categoryDao.getAllCategories(userId),
        accountRepository.getAllAccounts(userId),
        personRepository.getAllPeople(userId)
    ) { cats, accs, ppl -> Triple(cats.associate { it.id to it.name }, accs.associate { it.id to it.name }, ppl.associate { it.id to it.name }) }

    val uiState: StateFlow<TrashUiState> = combine(tab, sources, names) { selected, src, nm ->
        val (catName, accName, personName) = nm
        val all: Map<TrashTab, List<TrashEntry>> = mapOf(
            TrashTab.TRANSACTIONS to src.txns.map {
                TrashEntry.Txn(it, it.categoryId?.let(catName::get), (it.sourceAccountId ?: it.destinationAccountId)?.let(accName::get), it.personId?.let(personName::get))
            },
            TrashTab.BUDGETS to src.budgets.map { TrashEntry.Budget(it, it.categoryId?.let(catName::get)) },
            TrashTab.GOALS to src.goals.map { TrashEntry.Goal(it) },
            TrashTab.CATEGORIES to src.categories.map { TrashEntry.Category(it) },
            TrashTab.ACCOUNTS to src.accounts.map { TrashEntry.Account(it) },
            TrashTab.PEOPLE to src.people.map { TrashEntry.Person(it) }
        )
        TrashUiState(
            tab = selected,
            entries = all.getValue(selected).sortedByDescending { it.deletedAtMillis ?: Long.MIN_VALUE },
            counts = all.mapValues { it.value.size }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrashUiState())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun selectTab(t: TrashTab) { tab.value = t }

    fun restore(entry: TrashEntry) {
        viewModelScope.launch {
            val result = when (entry) {
                is TrashEntry.Txn -> trashRepository.restoreTransaction(entry.id)
                is TrashEntry.Budget -> trashRepository.restoreBudget(entry.id)
                is TrashEntry.Goal -> trashRepository.restoreGoal(entry.id)
                is TrashEntry.Category -> trashRepository.restoreCategory(entry.id)
                is TrashEntry.Account -> trashRepository.restoreAccount(entry.id)
                is TrashEntry.Person -> trashRepository.restorePerson(entry.id)
            }
            _messages.emit(
                when (result) {
                    TrashResult.Done -> UiMessage(R.string.trash_restored)
                    TrashResult.NotFound -> UiMessage(R.string.error_generic)
                    TrashResult.BudgetConflict -> UiMessage(R.string.budget_restore_conflict)
                    is TrashResult.NeedsRestoreFirst ->
                        UiMessage(if (result.isAccount) R.string.trash_restore_account_first else R.string.trash_restore_person_first, listOf(result.name))
                    is TrashResult.KeptBecauseInUse -> UiMessage(R.string.error_generic)
                }
            )
        }
    }

    fun purge(entry: TrashEntry) {
        viewModelScope.launch {
            val result = when (entry) {
                is TrashEntry.Txn -> { trashRepository.purgeTransaction(entry.id); TrashResult.Done }
                is TrashEntry.Budget -> { trashRepository.purgeBudget(entry.id); TrashResult.Done }
                is TrashEntry.Goal -> { trashRepository.purgeGoal(entry.id); TrashResult.Done }
                is TrashEntry.Category -> trashRepository.purgeCategory(entry.id)
                is TrashEntry.Account -> trashRepository.purgeAccount(entry.id)
                is TrashEntry.Person -> trashRepository.purgePerson(entry.id)
            }
            _messages.emit(
                when (result) {
                    is TrashResult.KeptBecauseInUse -> UiMessage(
                        when (result.reason) {
                            PurgeBlock.HAS_TRANSACTIONS -> R.string.trash_kept_has_transactions
                            PurgeBlock.HAS_BUDGETS -> R.string.trash_kept_has_budgets
                            PurgeBlock.HAS_RECURRING -> R.string.trash_kept_has_recurring
                            PurgeBlock.HAS_GOALS -> R.string.trash_kept_has_goals
                        }
                    )
                    else -> UiMessage(R.string.trash_deleted_forever)
                }
            )
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val r = trashRepository.emptyTrash(userId)
            _messages.emit(
                if (r.keptInUse > 0) UiMessage(R.string.trash_emptied_some_kept, listOf(r.keptInUse))
                else UiMessage(R.string.trash_emptied)
            )
        }
    }
}
