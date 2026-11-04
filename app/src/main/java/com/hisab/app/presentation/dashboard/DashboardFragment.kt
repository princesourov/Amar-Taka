package com.hisab.app.presentation.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentDashboardBinding
import com.hisab.app.databinding.ItemDashboardProgressBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.presentation.transactions.TransactionAdapter
import com.hisab.app.utils.DateRangeType
import com.hisab.app.utils.formatMinorAsCurrency
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val accountAdapter = AccountBalanceAdapter()

    private val viewModel: DashboardViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    private val recentTransactionsAdapter = TransactionAdapter(
        onEdit = { item ->
            findNavController().navigate(
                R.id.action_dashboard_to_editTransaction,
                Bundle().apply { putString("transactionId", item.id) }
            )
        },
        onDelete = { item ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_transaction_title)
                .setMessage(R.string.delete_transaction_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteTransaction(item.id) }
                .show()
        }
    )

    private val dateRanges = listOf(
        DateRangeType.TODAY, DateRangeType.LAST_3_DAYS, DateRangeType.LAST_7_DAYS, DateRangeType.LAST_15_DAYS,
        DateRangeType.LAST_30_DAYS, DateRangeType.LAST_90_DAYS, DateRangeType.LAST_6_MONTHS,
        DateRangeType.THIS_MONTH, DateRangeType.PREVIOUS_MONTH, DateRangeType.THIS_YEAR, DateRangeType.LAST_YEAR
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val nav = findNavController()

        binding.accountList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.accountList.adapter = accountAdapter
        binding.recentTransactionsList.layoutManager = LinearLayoutManager(requireContext())
        binding.recentTransactionsList.adapter = recentTransactionsAdapter
        binding.greeting.text = greetingForNow()

        binding.dateRangeDropdown.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                dateRanges.map(::displayName)
            )
        )

        viewModel.setRangeType(DateRangeType.TODAY)
        binding.dateRangeDropdown.setText(displayName(DateRangeType.TODAY), false)

        binding.dateRangeDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.setRangeType(dateRanges[position])
        }

        // Every card goes somewhere.
        binding.heroCard.setOnClickListener { nav.navigate(R.id.action_dashboard_to_accounts) }
        binding.seeAllAccounts.setOnClickListener { nav.navigate(R.id.action_dashboard_to_accounts) }
        binding.summaryCard.setOnClickListener { nav.navigate(R.id.action_dashboard_to_transactions) }
        binding.debtCard.setOnClickListener { nav.navigate(R.id.action_dashboard_to_people) }
        binding.budgetsCard.setOnClickListener { nav.navigate(R.id.action_dashboard_to_budgets) }
        binding.seeAllBudgets.setOnClickListener { nav.navigate(R.id.action_dashboard_to_budgets) }
        binding.goalsCard.setOnClickListener { nav.navigate(R.id.action_dashboard_to_savingsGoals) }
        binding.seeAllGoals.setOnClickListener { nav.navigate(R.id.action_dashboard_to_savingsGoals) }
        binding.seeAllTransactions.setOnClickListener { nav.navigate(R.id.action_dashboard_to_transactions) }

        binding.qaIncome.setOnClickListener { nav.navigate(R.id.action_dashboard_to_addMoney) }
        binding.qaExpense.setOnClickListener { nav.navigate(R.id.action_dashboard_to_addExpense) }
        binding.qaTransfer.setOnClickListener { nav.navigate(R.id.action_dashboard_to_transfer) }
        binding.qaLend.setOnClickListener { nav.navigate(R.id.action_dashboard_to_people) }
        binding.qaGoal.setOnClickListener { nav.navigate(R.id.action_dashboard_to_createGoal) }
        binding.qaReports.setOnClickListener { nav.navigate(R.id.action_dashboard_to_reports) }
        binding.qaBudgets.setOnClickListener { nav.navigate(R.id.action_dashboard_to_budgets) }

        binding.moreButton.setOnClickListener { anchor ->
            PopupMenu(requireContext(), anchor).apply {
                menu.add(0, R.id.action_dashboard_to_categories, 0, R.string.dash_more_categories)
                menu.add(0, R.id.action_dashboard_to_recurring, 1, R.string.dash_more_recurring)
                menu.add(0, R.id.action_dashboard_to_trash, 2, R.string.dash_more_trash)
                menu.add(0, R.id.action_dashboard_to_backup, 3, R.string.dash_more_backup)
                setOnMenuItemClickListener { item -> nav.navigate(item.itemId); true }
            }.show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch { viewModel.budgets.collect { renderBudgets(it) } }
                launch { viewModel.goals.collect { renderGoals(it) } }
                launch {
                    viewModel.recentTransactions.collect { list ->
                        recentTransactionsAdapter.submitList(list)
                        binding.recentTransactionsEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun render(state: DashboardUiState) {
        binding.totalBalance.text = formatMinorAsCurrency(state.totalBalanceMinor)
        binding.availableAmount.text = formatMinorAsCurrency(state.availableMinor)
        binding.inGoalsAmount.text = formatMinorAsCurrency(state.allocatedToGoalsMinor)

        accountAdapter.submitList(state.accounts)
        binding.accountsEmpty.visibility = if (!state.isLoading && state.accounts.isEmpty()) View.VISIBLE else View.GONE
        binding.accountList.visibility = if (state.accounts.isEmpty()) View.GONE else View.VISIBLE

        binding.summaryIncomeValue.text = formatMinorAsCurrency(state.incomeMinor)
        binding.summaryExpenseValue.text = formatMinorAsCurrency(state.expenseMinor)
        binding.summaryNetValue.text = formatMinorAsCurrency(state.netCashFlowMinor)
        binding.summaryNetValue.setTextColor(signColor(state.netCashFlowMinor))
        binding.summaryRateValue.text = state.savingsRatePercent
            ?.let { getString(R.string.dash_savings_rate_value, it) } ?: getString(R.string.dash_savings_rate_none)

        binding.debtReceivableValue.text = formatMinorAsCurrency(state.debt.receivableMinor)
        binding.debtPayableValue.text = formatMinorAsCurrency(state.debt.payableMinor)
        binding.debtNetValue.text = formatMinorAsCurrency(state.debt.netMinor)
        binding.debtNetValue.setTextColor(signColor(state.debt.netMinor))
    }

    private fun renderBudgets(b: DashboardBudgets) {
        binding.budgetsSummaryText.text = when {
            b.totalBudgets == 0 -> getString(R.string.dashboard_budgets_empty)
            b.overCount > 0 -> getString(R.string.dash_budgets_over_note, b.overCount)
            else -> getString(R.string.dash_budgets_on_track)
        }
        binding.budgetRows.removeAllViews()
        b.items.forEach { item ->
            val row = ItemDashboardProgressBinding.inflate(layoutInflater, binding.budgetRows, false)
            row.rowLabel.text = item.name.ifBlank { getString(R.string.budget_overall_option) }
            row.rowValue.text = getString(R.string.dash_budget_progress, formatMinorAsCurrency(item.spentMinor), formatMinorAsCurrency(item.limitMinor))
            row.rowProgress.progress = item.progressPercent
            binding.budgetRows.addView(row.root)
        }
    }

    private fun renderGoals(g: DashboardGoals) {
        binding.goalsSummaryText.text = if (g.totalGoals == 0) getString(R.string.dashboard_goals_empty)
        else getString(R.string.dash_goals_total, formatMinorAsCurrency(g.totalSavedMinor), formatMinorAsCurrency(g.totalTargetMinor))
        binding.goalRows.removeAllViews()
        g.items.forEach { item ->
            val row = ItemDashboardProgressBinding.inflate(layoutInflater, binding.goalRows, false)
            row.rowLabel.text = "${item.icon}  ${item.name}"
            row.rowValue.text = getString(R.string.goal_saved_target, formatMinorAsCurrency(item.savedMinor), formatMinorAsCurrency(item.targetMinor))
            row.rowProgress.progress = item.percent
            binding.goalRows.addView(row.root)
        }
    }

    private fun signColor(minor: Long): Int = ContextCompat.getColor(
        requireContext(), if (minor < 0) R.color.color_negative else R.color.color_positive
    )

    private fun displayName(type: DateRangeType): String = getString(
        when (type) {
            DateRangeType.TODAY -> R.string.range_today
            DateRangeType.LAST_3_DAYS -> R.string.range_last_3_days
            DateRangeType.LAST_7_DAYS -> R.string.range_last_7_days
            DateRangeType.LAST_15_DAYS -> R.string.range_last_15_days
            DateRangeType.LAST_30_DAYS -> R.string.range_last_30_days
            DateRangeType.LAST_90_DAYS -> R.string.range_last_90_days
            DateRangeType.LAST_6_MONTHS -> R.string.range_last_6_months
            DateRangeType.THIS_MONTH -> R.string.range_this_month
            DateRangeType.PREVIOUS_MONTH -> R.string.range_previous_month
            DateRangeType.THIS_YEAR -> R.string.range_this_year
            DateRangeType.LAST_YEAR -> R.string.range_last_year
            DateRangeType.CUSTOM -> R.string.range_custom
        }
    )

    private fun greetingForNow(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return getString(
            when {
                hour < 12 -> R.string.greeting_morning
                hour < 17 -> R.string.greeting_afternoon
                else -> R.string.greeting_evening
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.accountList.adapter = null
        binding.recentTransactionsList.adapter = null
        _binding = null
    }
}
