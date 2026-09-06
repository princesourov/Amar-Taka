package com.hisab.app.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.data.repository.PersonRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.model.TransactionType
import com.hisab.app.utils.DateRangeType
import com.hisab.app.utils.DateRanges
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReportsUiState(
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val netCashFlowMinor: Long = 0,
    val expenseByCategory: String = "",
    val incomeByCategory: String = "",
    val accountActivity: String = "",
    val monthlyTrend: String = "",
    val budgetPerformance: String = "",
    val lendingBorrowing: String = ""
)

class ReportsViewModel(
    userId: String,
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    personRepository: PersonRepository,
    budgetRepository: BudgetRepository
) : ViewModel() {
    private val rangeType = MutableStateFlow(DateRangeType.THIS_MONTH)
    val selectedRangeType: StateFlow<DateRangeType> = rangeType

    val uiState: StateFlow<ReportsUiState> = rangeType.flatMapLatest { type ->
        val range = DateRanges.resolve(type)
        combine(
            transactionRepository.searchTransactions(
                userId = userId,
                startMillis = range.startMillis,
                endMillis = range.endMillis
            ),
            accountRepository.getActiveAccounts(userId),
            personRepository.getTotalReceivable(userId),
            personRepository.getTotalPayable(userId),
            budgetRepository.getBudgetsWithSpentForMonth(userId, YearMonth.from(range.endDate))
        ) { txns, accounts, receivable, payable, budgets ->
            val income = txns.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amountMinor }
            val expense = txns.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amountMinor }
            val expenseCategory = txns.filter { it.type == TransactionType.EXPENSE.name }
                .groupBy { it.categoryId ?: "uncategorized" }
                .entries.sortedByDescending { it.value.sumOf { t -> t.amountMinor } }
                .take(4)
                .joinToString(" • ") { "${it.key}: ${it.value.sumOf { t -> t.amountMinor } / 100.0}" }
            val incomeCategory = txns.filter { it.type == TransactionType.INCOME.name }
                .groupBy { it.categoryId ?: "uncategorized" }
                .entries.sortedByDescending { it.value.sumOf { t -> t.amountMinor } }
                .take(4)
                .joinToString(" • ") { "${it.key}: ${it.value.sumOf { t -> t.amountMinor } / 100.0}" }
            val accountActivity = accounts.joinToString(" • ") { account ->
                val count = txns.count { it.sourceAccountId == account.id || it.destinationAccountId == account.id }
                "${account.name}: $count"
            }
            val monthFormat = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())
            val trend = txns.groupBy {
                Instant.ofEpochMilli(it.transactionDateMillis).atZone(ZoneId.systemDefault()).toLocalDate().let(YearMonth::from)
            }.toSortedMap().entries.joinToString(" • ") { (month, list) ->
                "${month.format(monthFormat)}: ${(list.sumOf { v -> v.amountMinor } / 100.0)}"
            }
            val budgetPerformance = if (budgets.isEmpty()) "No budgets" else {
                "${budgets.count { (budget, spent) -> spent > budget.amountMinor }} over, ${budgets.size} total"
            }
            ReportsUiState(
                incomeMinor = income,
                expenseMinor = expense,
                netCashFlowMinor = income - expense,
                expenseByCategory = expenseCategory.ifBlank { "No expense data" },
                incomeByCategory = incomeCategory.ifBlank { "No income data" },
                accountActivity = accountActivity.ifBlank { "No account activity" },
                monthlyTrend = trend.ifBlank { "No monthly trend data" },
                budgetPerformance = budgetPerformance,
                lendingBorrowing = "Receivable: ${receivable / 100.0}, Payable: ${payable / 100.0}"
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsUiState())

    val rangeLabelList = DateRangeType.entries.filter { it != DateRangeType.CUSTOM }
        .map { displayName(it) }

    fun setRange(index: Int) {
        rangeType.value = DateRangeType.entries.filter { it != DateRangeType.CUSTOM }[index]
    }

    private fun displayName(type: DateRangeType): String = when (type) {
        DateRangeType.TODAY -> "Today"
        DateRangeType.LAST_3_DAYS -> "Last 3 Days"
        DateRangeType.LAST_7_DAYS -> "Last 7 Days"
        DateRangeType.LAST_15_DAYS -> "Last 15 Days"
        DateRangeType.LAST_30_DAYS -> "Last 30 Days"
        DateRangeType.LAST_90_DAYS -> "Last 90 Days"
        DateRangeType.LAST_6_MONTHS -> "Last 6 Months"
        DateRangeType.THIS_MONTH -> "This Month"
        DateRangeType.PREVIOUS_MONTH -> "Previous Month"
        DateRangeType.THIS_YEAR -> "This Year"
        DateRangeType.LAST_YEAR -> "Last Year"
        DateRangeType.CUSTOM -> "Custom"
    }
}
