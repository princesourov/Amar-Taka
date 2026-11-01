package com.hisab.app.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.toLedgerRow
import com.hisab.app.data.repository.AccountRepository
import com.hisab.app.data.repository.BudgetRepository
import com.hisab.app.data.repository.TransactionRepository
import com.hisab.app.domain.budget.BudgetEvaluation
import com.hisab.app.domain.budget.BudgetEvaluator
import com.hisab.app.domain.budget.BudgetState
import com.hisab.app.domain.model.BudgetScope
import com.hisab.app.domain.model.TransactionDisplayItem
import com.hisab.app.domain.report.Change
import com.hisab.app.domain.report.CsvExporter
import com.hisab.app.domain.report.DateSpan
import com.hisab.app.domain.report.Insight
import com.hisab.app.domain.report.InsightEngine
import com.hisab.app.domain.report.NameLookup
import com.hisab.app.domain.report.ReportCalculator
import com.hisab.app.domain.report.ReportFilter
import com.hisab.app.domain.report.ReportPeriodType
import com.hisab.app.domain.report.ReportPeriods
import com.hisab.app.domain.report.ReportSummary
import com.hisab.app.domain.report.TypeFilter
import com.hisab.app.domain.usecase.DeleteTransactionUseCase
import com.hisab.app.utils.DateRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** One row of the category breakdown (spending or income). */
data class CategoryRow(
    val categoryId: String?,
    val name: String?,           // null = uncategorized (the UI supplies the localized label)
    val colorHex: String?,
    val amountMinor: Long,
    val sharePercent: Double,
    val count: Int,
    /** Same category in the previous period; null when it had none (shown as "New", never as a fake %). */
    val previousMinor: Long?,
    val changePercent: Double?,
    /** Budget for this category, if one exists — shown as utilization on the row. */
    val budget: BudgetEvaluation?
)

data class AccountShare(val id: String, val name: String, val balanceMinor: Long)

data class BudgetUtilization(val label: String?, val categoryName: String?, val scope: BudgetScope, val evaluation: BudgetEvaluation)

data class ReportsUiState(
    val isLoading: Boolean = true,
    val periodType: ReportPeriodType = ReportPeriodType.THIS_MONTH,
    val span: DateSpan? = null,
    val previousSpan: DateSpan? = null,
    val filter: ReportFilter = ReportFilter(),
    val summary: ReportSummary? = null,
    val previous: ReportSummary? = null,
    val expenseCategories: List<CategoryRow> = emptyList(),
    val incomeCategories: List<CategoryRow> = emptyList(),
    val accountBalances: List<AccountShare> = emptyList(),
    val budgets: List<BudgetUtilization> = emptyList(),
    /** Every transaction counted in the totals, newest first — the list always reconciles with the figures. */
    val items: List<TransactionDisplayItem> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val categoryOptions: List<Pair<String, String>> = emptyList(), // id to name
    val accountOptions: List<Pair<String, String>> = emptyList()
) {
    val incomeChange: Change? get() = if (summary != null && previous != null) Change(summary.incomeMinor, previous.incomeMinor) else null
    val expenseChange: Change? get() = if (summary != null && previous != null) Change(summary.expenseMinor, previous.expenseMinor) else null
    val netChange: Change? get() = if (summary != null && previous != null) Change(summary.netCashFlowMinor, previous.netCashFlowMinor) else null
}

private data class ReportRequest(
    val periodType: ReportPeriodType = ReportPeriodType.THIS_MONTH,
    val custom: DateSpan? = null,
    val filter: ReportFilter = ReportFilter()
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ReportsViewModel(
    private val userId: String,
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    private val categoryDao: CategoryDao,
    budgetRepository: BudgetRepository,
    private val deleteTransactionUseCase: DeleteTransactionUseCase
) : ViewModel() {

    private val request = MutableStateFlow(ReportRequest())

    @Volatile private var names: NameLookup = NameLookup.Empty

    val uiState: StateFlow<ReportsUiState> = request
        .debounce(150) // typing in the keyword box shouldn't re-query on every character
        .flatMapLatest { req ->
            val zone = DateRanges.zone()
            val today = LocalDate.now(zone)
            val firstDay = DateRanges.firstDayOfWeek()
            val span = ReportPeriods.resolve(req.periodType, today, firstDay, req.custom)
            val prevSpan = ReportPeriods.previous(req.periodType, span, today)
            val budgetMonth = YearMonth.from(if (span.end.isAfter(today)) today else span.end)

            combine(
                transactionRepository.getTransactionsBetween(userId, span.startMillis(zone), span.endMillis(zone)),
                transactionRepository.getTransactionsBetween(userId, prevSpan.startMillis(zone), prevSpan.endMillis(zone)),
                categoryDao.getAllCategories(userId),
                accountRepository.getAccountsWithBalances(userId),
                budgetRepository.observeBudgetsForMonth(userId, budgetMonth, activeOnly = true)
            ) { rows, prevRows, categories, accounts, budgetsSpent ->
                val catById = categories.associateBy { it.id }
                val accById = accounts.associate { it.first.id to it.first }
                val lookup = object : NameLookup {
                    override fun categoryName(id: String?) = id?.let { catById[it]?.name }
                    override fun accountName(id: String?) = id?.let { accById[it]?.name }
                }
                names = lookup

                val ledger = rows.map { it.toLedgerRow() }
                val summary = ReportCalculator.compute(ledger, span, zone, today, firstDay, req.filter, lookup)
                val previous = ReportCalculator.compute(prevRows.map { it.toLedgerRow() }, prevSpan, zone, today, firstDay, req.filter, lookup)

                val budgetEvals = budgetsSpent.map { bs ->
                    Triple(bs, BudgetEvaluator.evaluate(bs.budget.amountMinor, bs.spentMinor), bs.budget.categoryId)
                }
                fun budgetFor(categoryId: String?): BudgetEvaluation? =
                    if (categoryId == null) null
                    else budgetEvals.filter { it.third == categoryId }
                        .minByOrNull { if (it.first.budget.scope == BudgetScope.MONTHLY.name) 0 else 1 }?.second

                val prevExpense = previous.expenseByCategory.associate { it.categoryId to it.amountMinor }
                val prevIncome = previous.incomeByCategory.associate { it.categoryId to it.amountMinor }
                fun rowsFor(totals: List<com.hisab.app.domain.report.CategoryTotal>, prev: Map<String?, Long>, withBudget: Boolean) =
                    totals.map { t ->
                        val p = prev[t.categoryId]
                        CategoryRow(
                            categoryId = t.categoryId,
                            name = t.categoryId?.let { catById[it]?.name },
                            colorHex = t.categoryId?.let { catById[it]?.colorHex },
                            amountMinor = t.amountMinor,
                            sharePercent = t.sharePercent,
                            count = t.count,
                            previousMinor = p,
                            changePercent = p?.let { Change(t.amountMinor, it).percent },
                            budget = if (withBudget) budgetFor(t.categoryId) else null
                        )
                    }

                val idSet = summary.countedRowIds.toHashSet()
                val items = rows.filter { it.id in idSet }.map { t ->
                    TransactionDisplayItem(
                        transaction = t,
                        categoryName = lookup.categoryName(t.categoryId),
                        sourceAccountName = lookup.accountName(t.sourceAccountId),
                        destinationAccountName = lookup.accountName(t.destinationAccountId),
                        personName = null
                    )
                } // `rows` is already newest-first from the DAO

                ReportsUiState(
                    isLoading = false,
                    periodType = req.periodType,
                    span = span,
                    previousSpan = prevSpan,
                    filter = req.filter,
                    summary = summary,
                    previous = previous,
                    expenseCategories = rowsFor(summary.expenseByCategory, prevExpense, withBudget = true),
                    incomeCategories = rowsFor(summary.incomeByCategory, prevIncome, withBudget = false),
                    accountBalances = accounts.map { (a, bal) -> AccountShare(a.id, a.name, bal) },
                    budgets = budgetEvals.map { (bs, eval, _) ->
                        BudgetUtilization(bs.budget.name, bs.budget.categoryId?.let { catById[it]?.name }, BudgetScope.valueOf(bs.budget.scope), eval)
                    }.sortedByDescending { it.evaluation.usedPercent },
                    items = items,
                    insights = InsightEngine.generate(
                        summary, if (previous.hasActivity) previous else null,
                        budgetsOver = budgetEvals.count { it.second.state == BudgetState.OVER },
                        budgetsNear = budgetEvals.count { it.second.state == BudgetState.NEAR_LIMIT || it.second.state == BudgetState.LIMIT_REACHED }
                    ),
                    categoryOptions = categories.filter { it.isActive }.map { it.id to it.name },
                    accountOptions = accounts.map { it.first.id to it.first.name }
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsUiState())

    val selectedPeriod: StateFlow<ReportPeriodType> =
        request.map { it.periodType }.stateIn(viewModelScope, SharingStarted.Eagerly, ReportPeriodType.THIS_MONTH)

    fun setPeriod(type: ReportPeriodType) {
        if (type != ReportPeriodType.CUSTOM) request.value = request.value.copy(periodType = type)
    }

    fun setCustomRange(startEpochDay: Long, endEpochDay: Long) {
        val a = LocalDate.ofEpochDay(minOf(startEpochDay, endEpochDay))
        val b = LocalDate.ofEpochDay(maxOf(startEpochDay, endEpochDay))
        request.value = request.value.copy(periodType = ReportPeriodType.CUSTOM, custom = DateSpan(a, b))
    }

    fun setTypeFilter(type: TypeFilter) = updateFilter { it.copy(type = type) }
    fun setCategoryFilter(categoryId: String?) = updateFilter { it.copy(categoryId = categoryId) }
    fun setAccountFilter(accountId: String?) = updateFilter { it.copy(accountId = accountId) }
    fun setKeyword(keyword: String) = updateFilter { it.copy(keyword = keyword) }
    fun clearFilters() = updateFilter { ReportFilter() }

    val currentCustomSpan: DateSpan? get() = request.value.custom

    private fun updateFilter(change: (ReportFilter) -> ReportFilter) {
        request.value = request.value.copy(filter = change(request.value.filter))
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch { deleteTransactionUseCase(id) }
    }

    /** CSV of exactly the rows behind the figures on screen (same period and filters). */
    fun buildCsv(): String {
        val state = uiState.value
        return CsvExporter.build(state.items.map { it.transaction.toLedgerRow() }, DateRanges.zone(), names)
    }
}
