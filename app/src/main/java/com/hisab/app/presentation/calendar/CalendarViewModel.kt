package com.hisab.app.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.PersonRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.model.TransactionDisplayItem
import com.hisab.app.domain.model.TransactionType
import com.hisab.app.utils.DateRanges
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CalendarUiState(
    val monthLabel: String,
    val selectedDate: LocalDate,
    val monthIncomeMinor: Long = 0,
    val monthExpenseMinor: Long = 0,
    val monthTransactionCount: Int = 0,
    val selectedDayIncomeMinor: Long = 0,
    val selectedDayExpenseMinor: Long = 0,
    val selectedDayTransactions: List<TransactionDisplayItem> = emptyList()
)

class CalendarViewModel(
    userId: String,
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryDao: com.hisab.app.data.local.dao.CategoryDao,
    personRepository: PersonRepository
) : ViewModel() {
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<CalendarUiState> = combine(selectedMonth, selectedDate) { month, date -> month to date }
        .flatMapLatest { (month, date) ->
            val range = DateRanges.forMonth(month)
            combine(
                transactionRepository.searchTransactions(userId = userId, startMillis = range.startMillis, endMillis = range.endMillis),
                accountRepository.getActiveAccounts(userId),
                categoryDao.getActiveCategories(userId),
                personRepository.getActivePeople(userId)
            ) { txns, accs, cats, ppl ->
                val dayItems = txns.filter {
                    Instant.ofEpochMilli(it.transactionDateMillis).atZone(ZoneId.systemDefault()).toLocalDate() == date
                }.map { t ->
                    TransactionDisplayItem(
                        transaction = t,
                        categoryName = t.categoryId?.let { id -> cats.find { it.id == id }?.name },
                        sourceAccountName = t.sourceAccountId?.let { id -> accs.find { it.id == id }?.name },
                        destinationAccountName = t.destinationAccountId?.let { id -> accs.find { it.id == id }?.name },
                        personName = t.personId?.let { id -> ppl.find { it.id == id }?.name }
                    )
                }
                CalendarUiState(
                    monthLabel = month.format(monthFormatter),
                    selectedDate = date,
                    monthIncomeMinor = txns.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amountMinor },
                    monthExpenseMinor = txns.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amountMinor },
                    monthTransactionCount = txns.size,
                    selectedDayIncomeMinor = dayItems.filter { it.transaction.type == TransactionType.INCOME.name }.sumOf { it.transaction.amountMinor },
                    selectedDayExpenseMinor = dayItems.filter { it.transaction.type == TransactionType.EXPENSE.name }.sumOf { it.transaction.amountMinor },
                    selectedDayTransactions = dayItems
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            CalendarUiState(monthLabel = YearMonth.now().format(monthFormatter), selectedDate = LocalDate.now())
        )

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
        selectedDate.value = selectedMonth.value.atDay(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
        selectedDate.value = selectedMonth.value.atDay(1)
    }

    fun currentMonth() {
        selectedMonth.value = YearMonth.now()
        selectedDate.value = LocalDate.now()
    }

    fun onDateSelected(date: LocalDate) {
        selectedDate.value = date
        selectedMonth.value = YearMonth.from(date)
    }
}
