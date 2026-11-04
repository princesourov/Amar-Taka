package com.hisab.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.data.repository.GoalOverview
import com.hisab.app.data.repository.PersonRepository
import com.hisab.app.data.repository.SavingsGoalRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.budget.BudgetEvaluator
import com.hisab.app.domain.budget.BudgetState
import com.hisab.app.domain.dashboard.DashboardMath
import com.hisab.app.domain.debt.DebtTotals
import com.hisab.app.domain.model.AccountType
import com.hisab.app.domain.model.TransactionDisplayItem
import com.hisab.app.presentation.savings.goalIconOrDefault
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

data class AccountBalanceItem(
    val id: String,
    val name: String,
    val type: AccountType,
    val balanceMinor: Long,
    /** Earmarked for goals; the card shows balance and, when non-zero, how much of it is still free. */
    val allocatedMinor: Long = 0L
) {
    val availableMinor: Long get() = balanceMinor - allocatedMinor
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    /** Sum of every active account's balance. Goals do NOT reduce it (they only earmark money). */
    val totalBalanceMinor: Long = 0,
    val allocatedToGoalsMinor: Long = 0,
    val accounts: List<AccountBalanceItem> = emptyList(),
    /** Money others owe me / money I owe others / net, from DebtLedger (settled debts are zero). */
    val debt: DebtTotals = DebtTotals.ZERO,
    /** Income and expense for the selected period (transfers, loans and repayments excluded). */
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0
) {
    val availableMinor: Long get() = DashboardMath.availableMinor(totalBalanceMinor, allocatedToGoalsMinor)
    val netCashFlowMinor: Long get() = DashboardMath.netCashFlow(incomeMinor, expenseMinor)
    val savingsRatePercent: Int? get() = DashboardMath.savingsRatePercent(incomeMinor, expenseMinor)
}

data class DashboardBudgetItem(
    val name: String, val spentMinor: Long, val limitMinor: Long, val progressPercent: Int, val state: BudgetState
)

data class DashboardBudgets(
    val items: List<DashboardBudgetItem> = emptyList(),
    val totalBudgets: Int = 0,
    val overCount: Int = 0
)

data class DashboardGoalItem(
    val id: String, val name: String, val icon: String, val savedMinor: Long, val targetMinor: Long, val percent: Int
)

data class DashboardGoals(
    val items: List<DashboardGoalItem> = emptyList(),
    val totalGoals: Int = 0,
    val totalSavedMinor: Long = 0,
    val totalTargetMinor: Long = 0
)

private data class DashboardDateRange(val startMillis: Long, val endMillis: Long)

class DashboardViewModel(
    private val userId: String,
    accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    personRepository: PersonRepository,
    budgetRepository: BudgetRepository,
    savingsGoalRepository: SavingsGoalRepository,
    categoryDao: CategoryDao
) : ViewModel() {

    private val selectedRangeType = MutableStateFlow(DateRangeType.THIS_MONTH)
    val rangeType: StateFlow<DateRangeType> = selectedRangeType

    private val selectedRange: Flow<DashboardDateRange> = selectedRangeType.map { type ->
        if (type == DateRangeType.TODAY) {
            createDayRange(LocalDate.now())
        } else {
            val range = DateRanges.resolve(type)
            DashboardDateRange(range.startMillis, range.endMillis)
        }
    }

    private val goalOverview: Flow<GoalOverview> = savingsGoalRepository.observeOverview(userId)

    val budgets: StateFlow<DashboardBudgets> =
        combine(budgetRepository.observeBudgetsForMonth(userId, YearMonth.now(), activeOnly = true), categoryDao.getAllCategories(userId)) { list, cats ->
            val catName = cats.associate { it.id to it.name }
            val items = list.map { bs ->
                val eval = BudgetEvaluator.evaluate(bs.budget.amountMinor, bs.spentMinor)
                val name = bs.budget.name?.takeIf { it.isNotBlank() } ?: bs.budget.categoryId?.let(catName::get) ?: ""
                DashboardBudgetItem(name, eval.spentMinor, eval.limitMinor, eval.progressPercent, eval.state)
            }
            DashboardBudgets(
                items = items.sortedByDescending { if (it.limitMinor > 0) it.spentMinor * 1000 / it.limitMinor else 0 }.take(3),
                totalBudgets = items.size,
                overCount = items.count { it.state == BudgetState.OVER }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardBudgets())

    val goals: StateFlow<DashboardGoals> = goalOverview.map { o ->
        DashboardGoals(
            items = o.goals.take(3).map {
                DashboardGoalItem(it.goal.id, it.goal.name, goalIconOrDefault(it.goal.iconKey), it.savedMinor, it.goal.targetAmountMinor, it.percent)
            },
            totalGoals = o.goals.size,
            totalSavedMinor = o.goals.sumOf { it.savedMinor },
            totalTargetMinor = o.goals.sumOf { it.goal.targetAmountMinor }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardGoals())

    private val accountsWithBalances: Flow<List<AccountBalanceItem>> =
        savingsGoalRepository.observeAccountAvailability(userId).map { list ->
            list.map {
                AccountBalanceItem(it.account.id, it.account.name, AccountType.valueOf(it.account.type), it.balanceMinor, it.allocatedMinor)
            }
        }

    val uiState: StateFlow<DashboardUiState> = selectedRange.flatMapLatest { range ->
        combine(
            accountsWithBalances,
            goalOverview,
            personRepository.observeDebtTotals(userId),
            transactionRepository.getTotalExpenseBetween(userId, range.startMillis, range.endMillis),
            transactionRepository.getTotalIncomeBetween(userId, range.startMillis, range.endMillis)
        ) { accounts, goalOv, debt, expense, income ->
            DashboardUiState(
                isLoading = false,
                totalBalanceMinor = accounts.sumOf { it.balanceMinor },
                allocatedToGoalsMinor = goalOv.totalAllocatedMinor,
                accounts = accounts,
                debt = debt,
                incomeMinor = income,
                expenseMinor = expense
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    /** The five newest transactions overall (not limited to the selected period). */
    val recentTransactions: StateFlow<List<TransactionDisplayItem>> =
        combine(
            transactionRepository.getRecentTransactions(userId),
            accountRepository.getAllAccounts(userId),
            categoryDao.getAllCategories(userId),
            personRepository.getAllPeople(userId)
        ) { txns, accs, cats, ppl ->
            val accName = accs.associate { it.id to it.name }
            val catName = cats.associate { it.id to it.name }
            val personName = ppl.associate { it.id to it.name }
            txns.take(5).map { t ->
                TransactionDisplayItem(
                    transaction = t,
                    categoryName = t.categoryId?.let(catName::get),
                    sourceAccountName = t.sourceAccountId?.let(accName::get),
                    destinationAccountName = t.destinationAccountId?.let(accName::get),
                    personName = t.personId?.let(personName::get)
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setRangeType(type: DateRangeType) { selectedRangeType.value = type }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch { transactionRepository.softDeleteTransaction(transactionId) }
    }

    private fun createDayRange(date: LocalDate): DashboardDateRange {
        val zone = ZoneId.systemDefault()
        return DashboardDateRange(
            date.atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        )
    }
}
