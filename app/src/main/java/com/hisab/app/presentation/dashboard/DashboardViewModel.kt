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
import com.hisab.app.utils.DateRangeType
import com.hisab.app.utils.DateRanges
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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
    val netBalanceMinor: Long
        get() = totalReceivableMinor - totalPayableMinor
}

private data class DashboardDateRange(
    val startMillis: Long,
    val endMillis: Long
)

class DashboardViewModel(
    userId: String,
    accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    personRepository: PersonRepository,
    budgetRepository: BudgetRepository,
    savingsGoalRepository: SavingsGoalRepository,
    categoryDao: CategoryDao
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val selectedRangeType =
        MutableStateFlow(DateRangeType.TODAY)

    val rangeType: StateFlow<DateRangeType> =
        selectedRangeType

    private val dateFormatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

    val dayIndicator: StateFlow<String> =
        selectedDate
            .map { date ->
                when {
                    date == LocalDate.now() -> "Today"
                    date == LocalDate.now().minusDays(1) -> "Yesterday"
                    date == LocalDate.now().plusDays(1) -> "Tomorrow"
                    else -> date.format(dateFormatter)
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                "Today"
            )

    private val selectedRange: StateFlow<DashboardDateRange> =
        combine(
            selectedRangeType,
            selectedDate
        ) { type, date ->
            if (type == DateRangeType.TODAY) {
                createDayRange(date)
            } else {
                val range = DateRanges.resolve(type)

                DashboardDateRange(
                    startMillis = range.startMillis,
                    endMillis = range.endMillis
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            createDayRange(LocalDate.now())
        )

    val budgetsSummary: StateFlow<BudgetsSummary> =
        selectedDate
            .map { YearMonth.from(it) }
            .flatMapLatest { month ->
                budgetRepository
                    .getBudgetsWithSpentForMonth(userId, month)
                    .map { list ->
                        BudgetsSummary(
                            list.size,
                            list.count { budgetAndSpent ->
                                val budget = budgetAndSpent.first
                                val spent = budgetAndSpent.second
                                spent > budget.amountMinor
                            }
                        )
                    }
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

    private val accountsWithBalances: Flow<Pair<List<AccountBalanceItem>, Long>> =
        accountRepository
            .getAccountsWithBalances(userId)
            .map { pairs ->
                val items = pairs.map { accountAndBalance ->
                    val account = accountAndBalance.first
                    val balance = accountAndBalance.second

                    AccountBalanceItem(
                        id = account.id,
                        name = account.name,
                        type = AccountType.valueOf(account.type),
                        balanceMinor = balance
                    )
                }

                items to items.sumOf { it.balanceMinor }
            }

    val uiState: StateFlow<DashboardUiState> =
        selectedRange
            .flatMapLatest { range ->
                combine(
                    accountsWithBalances,
                    personRepository.getTotalReceivable(userId),
                    personRepository.getTotalPayable(userId),
                    transactionRepository.getTotalExpenseBetween(
                        userId,
                        range.startMillis,
                        range.endMillis
                    ),
                    transactionRepository.getTotalIncomeBetween(
                        userId,
                        range.startMillis,
                        range.endMillis
                    )
                ) { accountsAndTotal, receivable, payable, expense, income ->

                    val accounts = accountsAndTotal.first
                    val total = accountsAndTotal.second

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
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                DashboardUiState()
            )

    val recentTransactions: StateFlow<List<TransactionDisplayItem>> =
        selectedRange
            .flatMapLatest { range ->
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

                    txns.take(5).map { transaction ->
                        TransactionDisplayItem(
                            transaction = transaction,
                            categoryName = transaction.categoryId?.let { id ->
                                cats.find { category ->
                                    category.id == id
                                }?.name
                            },
                            sourceAccountName = transaction.sourceAccountId?.let { id ->
                                accs.find { account ->
                                    account.id == id
                                }?.name
                            },
                            destinationAccountName = transaction.destinationAccountId?.let { id ->
                                accs.find { account ->
                                    account.id == id
                                }?.name
                            },
                            personName = transaction.personId?.let { id ->
                                ppl.find { person ->
                                    person.id == id
                                }?.name
                            }
                        )
                    }
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    fun setRangeType(type: DateRangeType) {
        selectedRangeType.value = type

        if (type == DateRangeType.TODAY) {
            selectedDate.value = LocalDate.now()
        }
    }

    fun previousDay() {
        selectedDate.value = selectedDate.value.minusDays(1)
        selectedRangeType.value = DateRangeType.TODAY
    }

    fun nextDay() {
        selectedDate.value = selectedDate.value.plusDays(1)
        selectedRangeType.value = DateRangeType.TODAY
    }

    fun resetToToday() {
        selectedDate.value = LocalDate.now()
        selectedRangeType.value = DateRangeType.TODAY
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            transactionRepository.softDeleteTransaction(transactionId)
        }
    }

    private fun createDayRange(date: LocalDate): DashboardDateRange {
        val zone = ZoneId.systemDefault()

        val startMillis = date
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        val endMillis = date
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli() - 1

        return DashboardDateRange(
            startMillis = startMillis,
            endMillis = endMillis
        )
    }
}