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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val todayExpenseMinor: Long = 0,
    val todayIncomeMinor: Long = 0
) {
    val netBalanceMinor: Long
        get() = totalReceivableMinor - totalPayableMinor
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

    val budgetsSummary: StateFlow<BudgetsSummary> =
        budgetRepository
            .getBudgetsWithSpent(userId)
            .map { list ->
                BudgetsSummary(
                    list.size,
                    list.count { (budget, spent) ->
                        spent > budget.amountMinor
                    }
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                BudgetsSummary(0, 0)
            )

    val goalsSummary: StateFlow<GoalsSummary> =
        savingsGoalRepository
            .getActiveGoals(userId)
            .map { goals ->
                GoalsSummary(
                    goals.size,
                    goals.sumOf { it.savedAmountMinor },
                    goals.sumOf { it.targetAmountMinor }
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                GoalsSummary(0, 0, 0)
            )

    private val accountsWithBalances:
            Flow<Pair<List<AccountBalanceItem>, Long>> =

        accountRepository
            .getAccountsWithBalances(userId)
            .map { pairs ->

                val items = pairs.map { (account, balance) ->

                    AccountBalanceItem(
                        account.id,
                        account.name,
                        AccountType.valueOf(account.type),
                        balance
                    )
                }

                items to items.sumOf {
                    it.balanceMinor
                }
            }

    private val todayRange =
        DateRanges.todayRange()

    val uiState: StateFlow<DashboardUiState> =
        combine(
            accountsWithBalances,
            personRepository.getTotalReceivable(userId),
            personRepository.getTotalPayable(userId),
            transactionRepository.getTotalExpenseBetween(
                userId,
                todayRange.first,
                todayRange.second
            ),
            transactionRepository.getTotalIncomeBetween(
                userId,
                todayRange.first,
                todayRange.second
            )
        ) { accountsAndTotal,
            receivable,
            payable,
            todayExpense,
            todayIncome ->

            val (accounts, total) =
                accountsAndTotal

            DashboardUiState(
                isLoading = false,
                totalBalanceMinor = total,
                accounts = accounts,
                totalReceivableMinor = receivable,
                totalPayableMinor = payable,
                todayExpenseMinor = todayExpense,
                todayIncomeMinor = todayIncome
            )
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                DashboardUiState()
            )

    val recentTransactions:
            StateFlow<List<TransactionDisplayItem>> =

        combine(
            transactionRepository.getRecentTransactions(userId),
            accountRepository.getActiveAccounts(userId),
            categoryDao.getActiveCategories(userId),
            personRepository.getActivePeople(userId)
        ) { txns, accs, cats, ppl ->

            txns
                .take(5)
                .map { t ->

                    TransactionDisplayItem(
                        transaction = t,

                        categoryName =
                            t.categoryId?.let { id ->
                                cats.find {
                                    it.id == id
                                }?.name
                            },

                        sourceAccountName =
                            t.sourceAccountId?.let { id ->
                                accs.find {
                                    it.id == id
                                }?.name
                            },

                        destinationAccountName =
                            t.destinationAccountId?.let { id ->
                                accs.find {
                                    it.id == id
                                }?.name
                            },

                        personName =
                            t.personId?.let { id ->
                                ppl.find {
                                    it.id == id
                                }?.name
                            }
                    )
                }
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            transactionRepository.softDeleteTransaction(
                transactionId
            )
        }
    }
}