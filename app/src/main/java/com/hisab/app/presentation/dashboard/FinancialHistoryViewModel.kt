package com.hisab.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.entity.toLedgerRow
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.dashboard.DailySummary
import com.hisab.app.domain.dashboard.DailySummaryCalculator
import com.hisab.app.utils.DateRangeType
import com.hisab.app.utils.DateRanges
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class FinancialHistoryUiState(
    val isLoading: Boolean = true,
    /** Newest first; always 30 entries once loaded, including days with no transactions. */
    val days: List<DailySummary> = emptyList(),
    val hasError: Boolean = false
) {
    /** True when at least one of the 30 days has income or expense (drives the "nothing recorded" note). */
    val hasActivity: Boolean get() = days.any { it.hasActivity }
}

/**
 * Backs the Last 30 Days history. It reads the same non-deleted rows, over the same
 * "Last 30 Days" range, as the Dashboard (via [TransactionRepository.getTransactionsBetween]) and
 * only splits them per day; the money rules live in LedgerRules / DashboardMath.
 */
class FinancialHistoryViewModel(
    private val userId: String,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())
    private val retryTick = MutableStateFlow(0)

    val uiState: StateFlow<FinancialHistoryUiState> = combine(today, retryTick) { day, _ -> day }
        .flatMapLatest { day ->
            val range = DateRanges.resolve(DateRangeType.LAST_30_DAYS, now = day)
            val zone = ZoneId.systemDefault()
            transactionRepository.getTransactionsBetween(userId, range.startMillis, range.endMillis)
                .map { rows ->
                    FinancialHistoryUiState(
                        isLoading = false,
                        days = DailySummaryCalculator.compute(rows.map { it.toLedgerRow() }, day, zone)
                    )
                }
                .onStart { emit(FinancialHistoryUiState()) }
                .catch { emit(FinancialHistoryUiState(isLoading = false, hasError = true)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialHistoryUiState())

    /** Re-reads the date so a screen left open past midnight starts with the new "today". */
    fun refreshToday() { today.value = LocalDate.now() }

    fun retry() { retryTick.value = retryTick.value + 1 }
}
