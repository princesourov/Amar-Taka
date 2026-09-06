package com.hisab.app.presentation.budgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
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
import com.hisab.app.databinding.FragmentBudgetsBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.model.BudgetProgressItem
import com.hisab.app.utils.formatMinorAsCurrency
import kotlinx.coroutines.launch

class BudgetsFragment : Fragment() {

    private var _binding: FragmentBudgetsBinding? = null
    private val binding get() = _binding!!
    private var historySnapshot: List<BudgetProgressItem> = emptyList()

    private val adapter = BudgetProgressAdapter { item, anchor ->
        showBudgetMenu(item, anchor)
    }

    private val viewModel: BudgetsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.budgetList.layoutManager = LinearLayoutManager(requireContext())
        binding.budgetList.adapter = adapter

        binding.fabCreateBudget.setOnClickListener {
            findNavController().navigate(R.id.action_budgets_to_createBudget)
        }
        binding.previousMonthButton.setOnClickListener { viewModel.previousMonth() }
        binding.nextMonthButton.setOnClickListener { viewModel.nextMonth() }
        binding.currentMonthButton.setOnClickListener { viewModel.currentMonth() }
        binding.viewHistoryButton.setOnClickListener { showHistoryDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.budgetProgress.collect { list ->
                        adapter.submitList(list)
                        binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.currentMonthLabel.collect { label -> binding.currentMonthLabel.text = label }
                }
                launch {
                    viewModel.history.collect { historySnapshot = it }
                }
            }
        }
    }

    private fun showBudgetMenu(item: BudgetProgressItem, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, R.id.action_budgets_to_createBudget, 0, getString(R.string.action_edit))
        popup.menu.add(0, 1001, 1, if (item.budget.isActive) getString(R.string.action_disable_budget) else getString(R.string.action_enable_budget))
        popup.menu.add(0, 1002, 2, getString(R.string.action_delete))
        popup.setOnMenuItemClickListener { menuItem -> onBudgetAction(item, menuItem) }
        popup.show()
    }

    private fun onBudgetAction(item: BudgetProgressItem, menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_budgets_to_createBudget -> {
                val args = Bundle().apply { putString("budgetId", item.budget.id) }
                findNavController().navigate(R.id.action_budgets_to_createBudget, args)
                true
            }
            1001 -> {
                viewModel.toggleBudget(item.budget.id, !item.budget.isActive)
                true
            }
            1002 -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_budget_title)
                    .setMessage(R.string.delete_budget_message)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteBudget(item.budget.id) }
                    .show()
                true
            }
            else -> false
        }
    }

    private fun showHistoryDialog() {
        if (historySnapshot.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_view_budget_history)
                .setMessage(R.string.budgets_empty_state)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val message = historySnapshot.joinToString("\n\n") { item ->
            "${item.periodLabel}\n${item.categoryName ?: getString(R.string.budget_overall_option)}\n" +
                getString(
                    R.string.budget_history_line,
                    formatMinorAsCurrency(item.budget.amountMinor),
                    formatMinorAsCurrency(item.spentMinor),
                    formatMinorAsCurrency(item.remainingMinor)
                )
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_view_budget_history)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
