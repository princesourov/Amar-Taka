package com.hisab.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.data.repository.PersonRepository
import com.hisab.app.data.repository.SavingsGoalRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.model.AccountType
import com.hisab.app.domain.model.BudgetsSummary
import com.hisab.app.domain.model.GoalsSummary
import com.hisab.app.domain.model.TransactionDisplayItem
import com.hisab.app.utils.DateRanges
import com.hisab.app.utils.DateRangeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AccountBalanceItem(
    val id: String,
    val name: String,
    val type: AccountType,
    val balanceMinor: Long
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val totalBalanceMinor: Long = 0,
    val accounts: List<AccountBalanceItem> = emptyList(),
    val totalReceivableMinor: Long = 0,
    val totalPayableMinor: Long = 0,
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0
) {
    val netBalanceMinor: Long get() = totalReceivableMinor - totalPayableMinor
}

class DashboardViewModel(
    userId: String,
    accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    personRepository: PersonRepository,
    budgetRepository: BudgetRepository,
    savingsGoalRepository: SavingsGoalRepository,
    categoryDao: CategoryDao
) : ViewModel() {

    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val selectedRangeType = MutableStateFlow(DateRangeType.THIS_MONTH)
    private val selectedMonth = MutableStateFlow(YearMonth.now())

    val rangeType: StateFlow<DateRangeType> = selectedRangeType
    val monthIndicator: StateFlow<String> = selectedMonth.map { it.format(monthFormatter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YearMonth.now().format(monthFormatter))

    private val selectedRange = combine(selectedRangeType, selectedMonth) { type, month ->
        if (type == DateRangeType.THIS_MONTH) DateRanges.forMonth(month)
        else DateRanges.resolve(type)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DateRanges.resolve(DateRangeType.THIS_MONTH))

    val budgetsSummary: StateFlow<BudgetsSummary> =
        selectedMonth.flatMapLatest { month ->
            budgetRepository.getBudgetsWithSpentForMonth(userId, month)
                .map { list ->
                    BudgetsSummary(list.size, list.count { (budget, spent) -> spent > budget.amountMinor })
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetsSummary(0, 0))

    val goalsSummary: StateFlow<GoalsSummary> =
        savingsGoalRepository.getActiveGoals(userId).map { goals ->
            GoalsSummary(goals.size, goals.sumOf { it.savedAmountMinor }, goals.sumOf { it.targetAmountMinor })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalsSummary(0, 0, 0))

    private val accountsWithBalances: Flow<Pair<List<AccountBalanceItem>, Long>> =
        accountRepository.getAccountsWithBalances(userId).map { pairs ->
            val items = pairs.map { (account, balance) ->
                AccountBalanceItem(account.id, account.name, AccountType.valueOf(account.type), balance)
            }
            items to items.sumOf { it.balanceMinor }
        }

    val uiState: StateFlow<DashboardUiState> =
        selectedRange.flatMapLatest { range ->
            combine(
                accountsWithBalances,
                personRepository.getTotalReceivable(userId),
                personRepository.getTotalPayable(userId),
                transactionRepository.getTotalExpenseBetween(userId, range.startMillis, range.endMillis),
                transactionRepository.getTotalIncomeBetween(userId, range.startMillis, range.endMillis)
            ) { accountsAndTotal, receivable, payable, expense, income ->
                val (accounts, total) = accountsAndTotal
                DashboardUiState(
                    isLoading = false,
                    totalBalanceMinor = total,
                    accounts = accounts,
                    totalReceivableMinor = receivable,
                    totalPayableMinor = payable,
                    incomeMinor = income,
                    expenseMinor = expense
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    val recentTransactions: StateFlow<List<TransactionDisplayItem>> =
        selectedRange.flatMapLatest { range ->
            combine(
                transactionRepository.searchTransactions(
                    userId = userId,
                    startMillis = range.startMillis,
                    endMillis = range.endMillis
                ),
                accountRepository.getActiveAccounts(userId),
                categoryDao.getActiveCategories(userId),
                personRepository.getActivePeople(userId)
            ) { txns, accs, cats, ppl ->
                txns.take(5).map { t ->
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

    fun setRangeType(type: DateRangeType) {
        selectedRangeType.value = type
    }

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
        selectedRangeType.value = DateRangeType.THIS_MONTH
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
        selectedRangeType.value = DateRangeType.THIS_MONTH
    }

    fun resetToCurrentMonth() {
        selectedMonth.value = YearMonth.now()
        selectedRangeType.value = DateRangeType.THIS_MONTH
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch { transactionRepository.softDeleteTransaction(transactionId) }
    }
}
