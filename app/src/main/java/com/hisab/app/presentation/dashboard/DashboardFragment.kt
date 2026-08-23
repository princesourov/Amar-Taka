package com.hisab.app.presentation.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentDashboardBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.model.BudgetsSummary
import com.hisab.app.domain.model.GoalsSummary
import com.hisab.app.presentation.transactions.TransactionAdapter
import com.hisab.app.utils.formatMinorAsCurrency
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val accountAdapter = AccountBalanceAdapter()

    private val recentTransactionsAdapter = TransactionAdapter(
        onEdit = { item ->
            val args = Bundle().apply {
                putString("transactionId", item.id)
            }

            findNavController().navigate(
                R.id.action_dashboard_to_editTransaction,
                args
            )
        },

        onDelete = { item ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_transaction_title)
                .setMessage(R.string.delete_transaction_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    viewModel.deleteTransaction(item.id)
                }
                .show()
        }
    )

    private val viewModel: DashboardViewModel by viewModels {
        ViewModelFactory(
            (requireActivity().application as HisabApplication).container
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentDashboardBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Account list
        binding.accountList.layoutManager =
            LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )

        binding.accountList.adapter = accountAdapter

        // Recent transactions
        binding.recentTransactionsList.layoutManager =
            LinearLayoutManager(requireContext())

        binding.recentTransactionsList.adapter =
            recentTransactionsAdapter

        // Greeting
        binding.greeting.text = greetingForNow()

        // Add Expense
        binding.fabAddExpense.setOnClickListener {
            findNavController().navigate(
                R.id.action_dashboard_to_addExpense
            )
        }

        // Add Money
        binding.fabAddMoney.setOnClickListener {
            findNavController().navigate(
                R.id.action_dashboard_to_addMoney
            )
        }

        // Budgets
        binding.seeAllBudgets.setOnClickListener {
            findNavController().navigate(
                R.id.action_dashboard_to_budgets
            )
        }

        binding.budgetsCard.setOnClickListener {
            findNavController().navigate(
                R.id.action_dashboard_to_budgets
            )
        }

        // Savings Goals
        binding.seeAllGoals.setOnClickListener {
            findNavController().navigate(
                R.id.action_dashboard_to_savingsGoals
            )
        }

        binding.goalsCard.setOnClickListener {
            findNavController().navigate(
                R.id.action_dashboard_to_savingsGoals
            )
        }

        // Recurring
        binding.recurringLink.setOnClickListener {
            findNavController().navigate(
                R.id.action_dashboard_to_recurring
            )
        }

        // All Transactions
        binding.seeAllTransactions.setOnClickListener {
            findNavController().navigate(
                R.id.action_dashboard_to_transactions
            )
        }

        // Observe data
        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                launch {
                    viewModel.uiState.collect { state ->
                        render(state)
                    }
                }

                launch {
                    viewModel.budgetsSummary.collect { summary ->
                        renderBudgetsSummary(summary)
                    }
                }

                launch {
                    viewModel.goalsSummary.collect { summary ->
                        renderGoalsSummary(summary)
                    }
                }

                launch {
                    viewModel.recentTransactions.collect { list ->

                        recentTransactionsAdapter.submitList(list)

                        binding.recentTransactionsEmpty.visibility =
                            if (list.isEmpty()) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }
                    }
                }
            }
        }
    }

    private fun renderBudgetsSummary(
        summary: BudgetsSummary
    ) {

        binding.budgetsSummaryText.text =
            if (summary.totalBudgets == 0) {

                getString(
                    R.string.dashboard_budgets_empty
                )

            } else {

                getString(
                    R.string.dashboard_budgets_summary,
                    summary.totalBudgets,
                    summary.overBudgetCount
                )
            }
    }

    private fun renderGoalsSummary(
        summary: GoalsSummary
    ) {

        binding.goalsSummaryText.text =
            if (summary.totalGoals == 0) {

                getString(
                    R.string.dashboard_goals_empty
                )

            } else {

                getString(
                    R.string.dashboard_goals_summary,
                    formatMinorAsCurrency(
                        summary.totalSavedMinor
                    ),
                    formatMinorAsCurrency(
                        summary.totalTargetMinor
                    )
                )
            }
    }

    private fun render(
        state: DashboardUiState
    ) {

        binding.totalBalance.text =
            formatMinorAsCurrency(
                state.totalBalanceMinor
            )

        accountAdapter.submitList(
            state.accounts
        )

        binding.receiveAmount.text =
            formatMinorAsCurrency(
                state.totalReceivableMinor
            )

        binding.payAmount.text =
            formatMinorAsCurrency(
                state.totalPayableMinor
            )

        binding.netAmount.text =
            formatMinorAsCurrency(
                state.netBalanceMinor
            )

        binding.todayIncome.text =
            formatMinorAsCurrency(
                state.todayIncomeMinor
            )

        binding.todayExpense.text =
            formatMinorAsCurrency(
                state.todayExpenseMinor
            )
    }

    private fun greetingForNow(): String {

        val hour =
            Calendar.getInstance()
                .get(Calendar.HOUR_OF_DAY)

        return when {

            hour < 12 ->
                getString(
                    R.string.greeting_morning
                )

            hour < 17 ->
                getString(
                    R.string.greeting_afternoon
                )

            else ->
                getString(
                    R.string.greeting_evening
                )
        }
    }

    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }
}