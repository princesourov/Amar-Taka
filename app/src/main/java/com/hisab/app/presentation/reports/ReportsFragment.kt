package com.hisab.app.presentation.reports

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentReportsBinding
import com.hisab.app.databinding.ItemReportAccountBinding
import com.hisab.app.databinding.ItemReportBudgetBinding
import com.hisab.app.databinding.ItemReportCategoryBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.budget.BudgetEvaluation
import com.hisab.app.domain.budget.BudgetState
import com.hisab.app.domain.model.BudgetScope
import com.hisab.app.domain.model.MoneyFormat
import com.hisab.app.domain.report.BucketPoint
import com.hisab.app.domain.report.Change
import com.hisab.app.domain.report.DateSpan
import com.hisab.app.domain.report.Granularity
import com.hisab.app.domain.report.Insight
import com.hisab.app.domain.report.ReportPeriodType
import com.hisab.app.domain.report.TypeFilter
import com.hisab.app.presentation.transactions.TransactionAdapter
import com.hisab.app.presentation.widget.BarChartView
import com.hisab.app.presentation.widget.ChartPalette
import com.hisab.app.presentation.widget.DonutChartView
import com.hisab.app.utils.formatMinorAsCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    private val adapter = TransactionAdapter(
        onEdit = { item -> openTransaction(item.id) },
        onDelete = { item -> confirmDelete(item.id) }
    )

    private var latest: ReportsUiState = ReportsUiState()
    private var shownAccountOptions: List<Pair<String, String>>? = null
    private var shownCategoryOptions: List<Pair<String, String>>? = null
    private var syncingChips = false

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwnerLiveScope {
            val csv = withContext(Dispatchers.Default) { viewModel.buildCsv() }
            val ok = withContext(Dispatchers.IO) {
                try {
                    requireContext().contentResolver.openOutputStream(uri)?.use {
                        // UTF-8 BOM so Excel shows the Taka sign and Bangla text correctly.
                        it.write("﻿".toByteArray(Charsets.UTF_8))
                        it.write(csv.toByteArray(Charsets.UTF_8))
                    } != null
                } catch (e: Exception) {
                    false
                }
            }
            Toast.makeText(requireContext(), if (ok) R.string.report_export_done else R.string.report_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun viewLifecycleOwnerLiveScope(block: suspend () -> Unit) {
        // The result callback can arrive while the view is being recreated; use the fragment scope then.
        (if (_binding != null) viewLifecycleOwner.lifecycleScope else lifecycleScope).launch { block() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recentList.layoutManager = LinearLayoutManager(requireContext())
        binding.recentList.adapter = adapter

        setupPeriodChips()
        setupFilters()

        binding.openTrashButton.setOnClickListener { findNavController().navigate(R.id.action_reports_to_trash) }
        binding.manageBudgetsButton.setOnClickListener { findNavController().navigate(R.id.action_reports_to_budgets) }
        binding.exportButton.setOnClickListener { showExportMenu(it) }
        binding.seeAllButton.setOnClickListener { ReportTransactionsSheet.show(childFragmentManager, ReportTransactionsSheet.KEY_ALL, getString(R.string.report_all_transactions_title)) }
        binding.clearFiltersButton.setOnClickListener { viewModel.clearFilters() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    // ---------------------------------------------------------------- controls

    private val chipToType: Map<Int, ReportPeriodType> by lazy {
        mapOf(
            R.id.chipToday to ReportPeriodType.TODAY,
            R.id.chipYesterday to ReportPeriodType.YESTERDAY,
            R.id.chipThisWeek to ReportPeriodType.THIS_WEEK,
            R.id.chipLastWeek to ReportPeriodType.LAST_WEEK,
            R.id.chipThisMonth to ReportPeriodType.THIS_MONTH,
            R.id.chipLastMonth to ReportPeriodType.LAST_MONTH,
            R.id.chipLast3Months to ReportPeriodType.LAST_3_MONTHS,
            R.id.chipThisYear to ReportPeriodType.THIS_YEAR,
            R.id.chipCustom to ReportPeriodType.CUSTOM
        )
    }

    private fun setupPeriodChips() {
        binding.periodChips.setOnCheckedStateChangeListener { _, checkedIds ->
            if (syncingChips) return@setOnCheckedStateChangeListener
            val type = chipToType[checkedIds.firstOrNull()] ?: return@setOnCheckedStateChangeListener
            if (type == ReportPeriodType.CUSTOM) showCustomRangePicker() else viewModel.setPeriod(type)
        }
        // Tapping "Custom" while it is already selected should let the user pick a new range.
        binding.chipCustom.setOnClickListener {
            if (latest.periodType == ReportPeriodType.CUSTOM) showCustomRangePicker()
        }
    }

    private fun showCustomRangePicker() {
        val current = latest.span
        val builder = MaterialDatePicker.Builder.dateRangePicker().setTitleText(R.string.period_custom_title)
        if (latest.periodType == ReportPeriodType.CUSTOM && current != null) {
            builder.setSelection(androidx.core.util.Pair(current.start.toEpochDay() * DAY_MILLIS, current.end.toEpochDay() * DAY_MILLIS))
        }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { sel ->
            val start = Math.floorDiv(sel.first, DAY_MILLIS)
            val end = Math.floorDiv(sel.second, DAY_MILLIS)
            viewModel.setCustomRange(start, end)
        }
        picker.addOnNegativeButtonClickListener { revertPeriodChip() }
        picker.addOnCancelListener { revertPeriodChip() }
        picker.show(childFragmentManager, "custom_range")
    }

    private fun revertPeriodChip() {
        if (_binding == null) return
        syncChip(latest.periodType)
    }

    private fun syncChip(type: ReportPeriodType) {
        val id = chipToType.entries.firstOrNull { it.value == type }?.key ?: return
        if (binding.periodChips.checkedChipId != id) {
            syncingChips = true
            binding.periodChips.check(id)
            syncingChips = false
        }
    }

    private fun setupFilters() {
        binding.typeChips.setOnCheckedStateChangeListener { _, checkedIds ->
            if (syncingChips) return@setOnCheckedStateChangeListener
            viewModel.setTypeFilter(
                when (checkedIds.firstOrNull()) {
                    R.id.chipTypeIncome -> TypeFilter.INCOME
                    R.id.chipTypeExpense -> TypeFilter.EXPENSE
                    else -> TypeFilter.ALL
                }
            )
        }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = viewModel.setKeyword(s?.toString().orEmpty())
        })
    }

    private fun showExportMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 0, R.string.report_export_csv)
            menu.add(0, 2, 1, R.string.report_share_summary)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        val span = latest.span
                        val name = "hisab-report-" + (span?.start ?: LocalDate.now()) + "_" + (span?.end ?: LocalDate.now()) + ".csv"
                        exportLauncher.launch(name)
                    }
                    2 -> shareSummary()
                }
                true
            }
        }.show()
    }

    private fun shareSummary() {
        val s = latest.summary ?: return
        val text = buildString {
            appendLine(getString(R.string.report_share_title, periodText(latest.span)))
            appendLine(getString(R.string.report_income_line, formatMinorAsCurrency(s.incomeMinor)))
            appendLine(getString(R.string.report_expense_line, formatMinorAsCurrency(s.expenseMinor)))
            appendLine(getString(R.string.report_net_line, formatMinorAsCurrency(s.netCashFlowMinor)))
            latest.expenseCategories.take(5).forEach {
                appendLine("• ${categoryName(it)}: ${formatMinorAsCurrency(it.amountMinor)} (${percent(it.sharePercent)})")
            }
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, getString(R.string.report_share_summary)))
    }

    // ---------------------------------------------------------------- navigation helpers (also used by the sheet)

    fun openTransaction(transactionId: String) {
        findNavController().navigate(R.id.action_reports_to_editTransaction, Bundle().apply { putString("transactionId", transactionId) })
    }

    fun confirmDelete(transactionId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_transaction_title)
            .setMessage(R.string.delete_transaction_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteTransaction(transactionId) }
            .show()
    }

    // ---------------------------------------------------------------- rendering

    private fun render(state: ReportsUiState) {
        latest = state
        val summary = state.summary
        binding.loadingIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        if (summary == null) {
            binding.reportContent.visibility = View.GONE
            binding.emptyState.visibility = View.GONE
            return
        }

        syncChip(state.periodType)
        syncFilterControls(state)

        binding.periodLabel.text = periodText(state.span)
        binding.comparisonLabel.text = state.previousSpan?.let { getString(R.string.report_compared_with, periodText(it)) }.orEmpty()

        if (!summary.hasActivity) {
            binding.reportContent.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            binding.emptyState.text = getString(if (state.filter.isActive) R.string.report_empty_filtered else R.string.report_empty)
            return
        }
        binding.emptyState.visibility = View.GONE
        binding.reportContent.visibility = View.VISIBLE

        renderSummary(state)
        renderInsights(state)
        renderTrend(state)
        renderCategories(state)
        renderComparison(state)
        renderAccounts(state)
        renderBudgets(state)
        adapter.submitList(state.items.take(RECENT_COUNT))
        binding.seeAllButton.visibility = if (state.items.size > RECENT_COUNT) View.VISIBLE else View.GONE
    }

    private fun syncFilterControls(state: ReportsUiState) {
        val f = state.filter
        syncingChips = true
        binding.typeChips.check(
            when (f.type) {
                TypeFilter.INCOME -> R.id.chipTypeIncome
                TypeFilter.EXPENSE -> R.id.chipTypeExpense
                TypeFilter.ALL -> R.id.chipTypeAll
            }
        )
        syncingChips = false
        binding.clearFiltersButton.visibility = if (f.isActive) View.VISIBLE else View.GONE
        if (binding.searchInput.text.toString() != f.keyword) binding.searchInput.setText(f.keyword)

        val allLabel = getString(R.string.filter_all)
        if (shownAccountOptions != state.accountOptions) {
            shownAccountOptions = state.accountOptions
            val labels = listOf(allLabel) + state.accountOptions.map { it.second }
            binding.accountDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
            binding.accountDropdown.setOnItemClickListener { _, _, position, _ ->
                viewModel.setAccountFilter(if (position == 0) null else state.accountOptions.getOrNull(position - 1)?.first)
            }
        }
        if (shownCategoryOptions != state.categoryOptions) {
            shownCategoryOptions = state.categoryOptions
            val labels = listOf(allLabel) + state.categoryOptions.map { it.second }
            binding.categoryDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
            binding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
                viewModel.setCategoryFilter(if (position == 0) null else state.categoryOptions.getOrNull(position - 1)?.first)
            }
        }
        binding.accountDropdown.setText(state.accountOptions.firstOrNull { it.first == f.accountId }?.second ?: allLabel, false)
        binding.categoryDropdown.setText(state.categoryOptions.firstOrNull { it.first == f.categoryId }?.second ?: allLabel, false)
    }

    private fun renderSummary(state: ReportsUiState) {
        val s = state.summary ?: return
        binding.netValue.text = formatMinorAsCurrency(s.netCashFlowMinor)
        binding.netValue.setTextColor(
            ContextCompat.getColor(requireContext(), if (s.netCashFlowMinor >= 0) R.color.color_positive else R.color.color_negative)
        )
        binding.netCaption.text = getString(
            when {
                s.netCashFlowMinor > 0 -> R.string.report_net_positive
                s.netCashFlowMinor < 0 -> R.string.report_net_negative
                else -> R.string.report_net_zero
            }
        ) + state.netChange?.let { " · " + deltaText(it, higherIsGood = true) }.orEmpty()

        binding.incomeValue.text = formatMinorAsCurrency(s.incomeMinor)
        binding.incomeDelta.text = state.incomeChange?.let { deltaText(it, higherIsGood = true) }.orEmpty()
        binding.expenseValue.text = formatMinorAsCurrency(s.expenseMinor)
        binding.expenseDelta.text = state.expenseChange?.let { deltaText(it, higherIsGood = false) }.orEmpty()

        // Ratios are only shown when they can be calculated (income > 0) — otherwise an honest dash.
        val rate = s.savingsRate
        binding.savingsRateValue.text = rate?.let { percent(it) } ?: getString(R.string.report_not_available)
        binding.savingsRateNote.text = getString(if (rate == null) R.string.report_needs_income else R.string.report_savings_rate_note)
        val ratio = s.expenseToIncomeRatio
        binding.expenseRatioValue.text = ratio?.let { percent(it) } ?: getString(R.string.report_not_available)
        binding.expenseRatioNote.text = getString(if (ratio == null) R.string.report_needs_income else R.string.report_expense_ratio_note)

        binding.expenseRefundNote.visibility = if (s.refundMinor > 0) View.VISIBLE else View.GONE
        binding.expenseRefundNote.text = getString(R.string.report_refund_note, formatMinorAsCurrency(s.refundMinor), formatMinorAsCurrency(s.grossExpenseMinor))
        binding.dailyAverageText.text = getString(R.string.report_daily_average, formatMinorAsCurrency(s.dailyAverageExpenseMinor))

        val excluded = ArrayList<String>()
        if (s.excludedTransfers > 0) excluded += resources.getQuantityString(R.plurals.report_transfers, s.excludedTransfers, s.excludedTransfers)
        if (s.excludedLoanMovements > 0) excluded += resources.getQuantityString(R.plurals.report_loan_movements, s.excludedLoanMovements, s.excludedLoanMovements)
        binding.excludedText.visibility = if (excluded.isEmpty()) View.GONE else View.VISIBLE
        binding.excludedText.text = getString(R.string.report_not_counted, excluded.joinToString(", "))
    }

    private fun renderInsights(state: ReportsUiState) {
        val container = binding.insightsContainer
        container.removeAllViews()
        binding.insightsCard.visibility = if (state.insights.isEmpty()) View.GONE else View.VISIBLE
        state.insights.forEach { insight ->
            container.addView(TextView(requireContext()).apply {
                text = insightText(state, insight)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, dp(4), 0, dp(4))
            })
        }
    }

    private fun insightText(state: ReportsUiState, insight: Insight): String = when (insight) {
        is Insight.TopCategory -> getString(R.string.insight_top_category, categoryNameFor(state, insight.categoryId), percent(insight.sharePercent), formatMinorAsCurrency(insight.amountMinor))
        is Insight.SpendingChange -> getString(
            if (insight.percent >= 0) R.string.insight_spending_up else R.string.insight_spending_down, percent(abs(insight.percent))
        )
        is Insight.IncomeChange -> getString(
            if (insight.percent >= 0) R.string.insight_income_up else R.string.insight_income_down, percent(abs(insight.percent))
        )
        is Insight.CategoryIncrease -> getString(R.string.insight_category_increase, categoryNameFor(state, insight.categoryId), percent(insight.percent), formatMinorAsCurrency(insight.deltaMinor))
        is Insight.NegativeCashFlow -> getString(R.string.insight_negative_cash_flow, formatMinorAsCurrency(insight.shortfallMinor))
        is Insight.SavingsRate -> getString(R.string.insight_savings_rate, percent(insight.percent))
        is Insight.SpentWithoutIncome -> getString(R.string.insight_spent_without_income, formatMinorAsCurrency(insight.expenseMinor))
        is Insight.BudgetsOver -> resources.getQuantityString(R.plurals.insight_budgets_over, insight.count, insight.count)
        is Insight.BudgetsNearLimit -> resources.getQuantityString(R.plurals.insight_budgets_near, insight.count, insight.count)
    }

    private fun categoryNameFor(state: ReportsUiState, id: String?): String =
        state.expenseCategories.firstOrNull { it.categoryId == id }?.let { categoryName(it) }
            ?: state.categoryOptions.firstOrNull { it.first == id }?.second
            ?: getString(R.string.report_uncategorized)

    private fun categoryName(row: CategoryRow): String = row.name ?: getString(R.string.report_uncategorized)

    private fun renderTrend(state: ReportsUiState) {
        val s = state.summary ?: return
        val incomeColor = ContextCompat.getColor(requireContext(), R.color.color_positive)
        val expenseColor = ContextCompat.getColor(requireContext(), R.color.color_negative)
        val points = s.series
        val labels = points.map { bucketLabel(it, s.granularity) }
        binding.trendChart.setData(
            labels,
            listOf(
                BarChartView.Series(getString(R.string.report_legend_income), incomeColor, points.map { it.incomeMinor }),
                BarChartView.Series(getString(R.string.report_legend_expense), expenseColor, points.map { it.expenseMinor })
            ),
            { MoneyFormat.compactMajor(it) },
            getString(R.string.report_trend_description, formatMinorAsCurrency(s.incomeMinor), formatMinorAsCurrency(s.expenseMinor))
        )
        binding.trendDetail.text = getString(R.string.report_trend_hint)
        binding.trendChart.onGroupSelected = { i ->
            points.getOrNull(i)?.let { p ->
                binding.trendDetail.text = getString(
                    R.string.report_trend_detail, bucketRange(p, s.granularity),
                    formatMinorAsCurrency(p.incomeMinor), formatMinorAsCurrency(p.expenseMinor)
                )
            }
        }
    }

    private fun renderCategories(state: ReportsUiState) {
        // ---- expenses: donut + rows
        val rows = state.expenseCategories
        binding.expenseCategoryCard.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        val shown = rows.take(MAX_CATEGORY_ROWS)
        val rest = rows.drop(MAX_CATEGORY_ROWS)
        val ctx = requireContext()
        val slices = shown.map {
            DonutChartView.Slice(categoryName(it), it.amountMinor, ChartPalette.colorFor(ctx, it.categoryId, it.colorHex), percent(it.sharePercent))
        } + if (rest.isNotEmpty()) listOf(
            DonutChartView.Slice(getString(R.string.report_other_categories), rest.sumOf { it.amountMinor }, ContextCompat.getColor(ctx, R.color.chart_10),
                percent(rest.sumOf { it.sharePercent }))
        ) else emptyList()
        val total = state.summary?.expenseMinor ?: 0L
        binding.expenseDonut.setData(
            slices, formatMinorAsCurrency(total), getString(R.string.report_total_spent),
            getString(R.string.report_donut_description, formatMinorAsCurrency(total), rows.size)
        )
        binding.expenseCategoryList.removeAllViews()
        shown.forEach { row -> binding.expenseCategoryList.addView(categoryRowView(row, spending = true)) }
        if (rest.isNotEmpty()) {
            binding.expenseCategoryList.addView(TextView(ctx).apply {
                text = getString(R.string.report_more_categories, rest.size, formatMinorAsCurrency(rest.sumOf { it.amountMinor }))
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setPadding(0, dp(8), 0, 0)
            })
        }
        binding.expenseCategoryHint.text = getString(R.string.report_category_hint)

        // ---- income
        val income = state.incomeCategories
        binding.incomeCategoryCard.visibility = if (income.isEmpty()) View.GONE else View.VISIBLE
        binding.incomeCategoryList.removeAllViews()
        income.take(MAX_CATEGORY_ROWS).forEach { binding.incomeCategoryList.addView(categoryRowView(it, spending = false)) }
    }

    private fun categoryRowView(row: CategoryRow, spending: Boolean): View {
        val ctx = requireContext()
        val b = ItemReportCategoryBinding.inflate(LayoutInflater.from(ctx), null, false)
        val color = ChartPalette.colorFor(ctx, row.categoryId, row.colorHex)
        b.categoryDot.backgroundTintList = ColorStateList.valueOf(color)
        b.categoryName.text = categoryName(row)
        b.categoryAmount.text = formatMinorAsCurrency(row.amountMinor)
        b.categoryBar.setIndicatorColor(color)
        b.categoryBar.progress = row.sharePercent.roundToInt().coerceIn(0, 100)

        val meta = ArrayList<String>()
        meta += getString(if (spending) R.string.report_share_of_spending else R.string.report_share_of_income, percent(row.sharePercent))
        meta += resources.getQuantityString(R.plurals.report_transactions, row.count, row.count)
        if (spending) {
            meta += when {
                row.changePercent != null -> deltaText(Change(row.amountMinor, row.previousMinor ?: 0L), higherIsGood = false)
                else -> getString(R.string.report_new_category)
            }
        }
        b.categoryMeta.text = meta.joinToString(" · ")

        val budget = row.budget
        if (budget != null) {
            b.categoryBudget.visibility = View.VISIBLE
            b.categoryBudget.text = budgetStatusText(budget)
        }
        b.root.setOnClickListener {
            ReportTransactionsSheet.show(childFragmentManager, row.categoryId ?: ReportTransactionsSheet.KEY_UNCATEGORIZED, categoryName(row))
        }
        b.root.contentDescription = "${categoryName(row)}, ${formatMinorAsCurrency(row.amountMinor)}, ${b.categoryMeta.text}"
        return b.root
    }

    private fun renderComparison(state: ReportsUiState) {
        val cur = state.summary ?: return
        val prev = state.previous
        val show = prev != null && prev.hasActivity
        binding.comparisonCard.visibility = View.VISIBLE
        if (!show || prev == null) {
            binding.comparisonChart.visibility = View.GONE
            binding.comparisonDetail.text = getString(R.string.report_no_previous_data)
            return
        }
        binding.comparisonChart.visibility = View.VISIBLE
        binding.comparisonChart.setData(
            listOf(getString(R.string.report_legend_income), getString(R.string.report_legend_expense)),
            listOf(
                BarChartView.Series(getString(R.string.report_legend_previous), ContextCompat.getColor(requireContext(), R.color.chart_10), listOf(prev.incomeMinor, prev.expenseMinor)),
                BarChartView.Series(getString(R.string.report_legend_current), ContextCompat.getColor(requireContext(), R.color.color_primary), listOf(cur.incomeMinor, cur.expenseMinor))
            ),
            { MoneyFormat.compactMajor(it) },
            getString(R.string.report_comparison_description)
        )
        binding.comparisonDetail.text = getString(
            R.string.report_comparison_detail,
            formatMinorAsCurrency(prev.expenseMinor), formatMinorAsCurrency(cur.expenseMinor),
            state.expenseChange?.let { deltaText(it, higherIsGood = false) } ?: getString(R.string.report_new_category)
        )
    }

    private fun renderAccounts(state: ReportsUiState) {
        val ctx = requireContext()
        val accounts = state.accountBalances
        binding.accountCard.visibility = if (accounts.isEmpty()) View.GONE else View.VISIBLE
        val positives = accounts.filter { it.balanceMinor > 0 }
        val positiveTotal = positives.sumOf { it.balanceMinor }
        binding.accountDonut.visibility = if (positives.isEmpty()) View.GONE else View.VISIBLE
        binding.accountDonut.setData(
            positives.map { DonutChartView.Slice(it.name, it.balanceMinor, ChartPalette.colorFor(ctx, it.id, null), percent(it.balanceMinor * 100.0 / positiveTotal)) },
            formatMinorAsCurrency(accounts.sumOf { it.balanceMinor }), getString(R.string.report_total_balance),
            getString(R.string.report_accounts_description, accounts.size)
        )
        binding.accountList.removeAllViews()
        accounts.sortedByDescending { it.balanceMinor }.forEach { a ->
            val b = ItemReportAccountBinding.inflate(LayoutInflater.from(ctx), null, false)
            b.accountDot.backgroundTintList = ColorStateList.valueOf(ChartPalette.colorFor(ctx, a.id, null))
            b.accountName.text = a.name
            b.accountBalance.text = formatMinorAsCurrency(a.balanceMinor)
            b.accountShare.text = if (a.balanceMinor > 0 && positiveTotal > 0) percent(a.balanceMinor * 100.0 / positiveTotal) else ""
            binding.accountList.addView(b.root)
        }
        binding.accountNote.text = getString(R.string.report_accounts_note)
    }

    private fun renderBudgets(state: ReportsUiState) {
        val ctx = requireContext()
        binding.budgetCard.visibility = if (state.budgets.isEmpty()) View.GONE else View.VISIBLE
        binding.budgetList.removeAllViews()
        state.budgets.forEach { bu ->
            val b = ItemReportBudgetBinding.inflate(LayoutInflater.from(ctx), null, false)
            val label = bu.label ?: bu.categoryName ?: getString(R.string.budget_overall_option)
            b.budgetName.text = getString(R.string.budget_card_title, label, scopeLabel(bu.scope))
            b.budgetPercent.text = percent(bu.evaluation.usedPercent)
            b.budgetBar.progress = bu.evaluation.progressPercent
            b.budgetBar.setIndicatorColor(budgetColor(bu.evaluation.state))
            b.budgetDetail.text = budgetStatusText(bu.evaluation)
            binding.budgetList.addView(b.root)
        }
    }

    // ---------------------------------------------------------------- formatting helpers

    private fun budgetColor(state: BudgetState): Int = ContextCompat.getColor(
        requireContext(),
        when (state) {
            BudgetState.OVER -> R.color.color_negative
            BudgetState.NEAR_LIMIT, BudgetState.LIMIT_REACHED -> R.color.color_warning
            else -> R.color.color_positive
        }
    )

    /** Status is always spelled out in words, so it never depends on colour alone. */
    private fun budgetStatusText(e: BudgetEvaluation): String {
        val spent = formatMinorAsCurrency(e.spentMinor)
        val limit = formatMinorAsCurrency(e.limitMinor)
        return when (e.state) {
            BudgetState.OVER -> getString(R.string.budget_status_over, spent, limit, formatMinorAsCurrency(e.overspentMinor))
            BudgetState.LIMIT_REACHED -> getString(R.string.budget_status_reached, spent, limit)
            BudgetState.NEAR_LIMIT -> getString(R.string.budget_status_near, spent, limit, formatMinorAsCurrency(e.remainingMinor))
            BudgetState.INVALID -> getString(R.string.budget_status_invalid)
            BudgetState.ON_TRACK -> getString(R.string.budget_status_ok, spent, limit, formatMinorAsCurrency(e.remainingMinor))
        }
    }

    private fun scopeLabel(scope: BudgetScope): String = getString(
        when (scope) {
            BudgetScope.DAILY -> R.string.scope_daily
            BudgetScope.WEEKLY -> R.string.scope_weekly
            BudgetScope.MONTHLY -> R.string.scope_monthly
        }
    )

    /** "▲ 12% vs previous" — direction is in the glyph AND the wording; colour is never the only signal. */
    private fun deltaText(change: Change, higherIsGood: Boolean): String {
        val pct = change.percent ?: return getString(R.string.report_no_comparison)
        if (change.delta == 0L) return getString(R.string.report_unchanged)
        val up = change.delta > 0
        return getString(if (up) R.string.report_delta_up else R.string.report_delta_down, percent(abs(pct)))
    }

    private fun percent(value: Double): String = if (abs(value) >= 100 || value == Math.floor(value)) "${value.roundToInt()}%" else String.format(Locale.getDefault(), "%.1f%%", value)

    private fun periodText(span: DateSpan?): String {
        if (span == null) return ""
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        return if (span.start == span.end) span.start.format(fmt) else "${span.start.format(fmt)} – ${span.end.format(fmt)}"
    }

    private fun bucketLabel(p: BucketPoint, g: Granularity): String = when (g) {
        Granularity.DAY -> p.start.format(DateTimeFormatter.ofPattern("d", Locale.getDefault()))
        Granularity.WEEK -> p.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        Granularity.MONTH -> p.start.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
    }

    private fun bucketRange(p: BucketPoint, g: Granularity): String {
        val day = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        return when (g) {
            Granularity.DAY -> p.start.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
            Granularity.WEEK -> "${p.start.format(day)} – ${p.end.format(day)}"
            Granularity.MONTH -> p.start.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recentList.adapter = null
        _binding = null
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val RECENT_COUNT = 5
        const val MAX_CATEGORY_ROWS = 8
    }
}
