package com.hisab.app.presentation.budgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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

    private val viewModel: BudgetsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    private val adapter = BudgetProgressAdapter(
        onEdit = { openEditor(it.budget.id) },
        onToggle = { viewModel.toggleBudget(it.budget.id, !it.isActive) },
        onDelete = { confirmDelete(it) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBudgetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.budgetList.layoutManager = LinearLayoutManager(requireContext())
        binding.budgetList.adapter = adapter

        binding.fabCreateBudget.setOnClickListener { openEditor(null) }
        binding.openTrashButton.setOnClickListener { findNavController().navigate(R.id.action_budgets_to_trash) }
        binding.previousMonthButton.setOnClickListener { viewModel.previousMonth() }
        binding.nextMonthButton.setOnClickListener { viewModel.nextMonth() }
        binding.currentMonthButton.setOnClickListener { viewModel.currentMonth() }

        binding.statusChips.setOnCheckedStateChangeListener { _, ids ->
            when (ids.firstOrNull()) {
                R.id.chipActive -> viewModel.setStatusFilter(StatusFilter.ACTIVE)
                R.id.chipPaused -> viewModel.setStatusFilter(StatusFilter.PAUSED)
                R.id.chipStatusAll -> viewModel.setStatusFilter(StatusFilter.ALL)
            }
        }
        binding.kindChips.setOnCheckedStateChangeListener { _, ids ->
            when (ids.firstOrNull()) {
                R.id.chipKindAll -> viewModel.setKindFilter(KindFilter.ALL)
                R.id.chipKindOverall -> viewModel.setKindFilter(KindFilter.OVERALL)
                R.id.chipKindCategory -> viewModel.setKindFilter(KindFilter.CATEGORY)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    private fun render(state: BudgetsUiState) {
        binding.currentMonthLabel.text = state.monthLabel
        binding.currentMonthButton.visibility = if (state.isCurrentMonth) View.GONE else View.VISIBLE
        adapter.submitList(state.items)

        binding.statusChips.check(
            when (state.status) {
                StatusFilter.ACTIVE -> R.id.chipActive
                StatusFilter.PAUSED -> R.id.chipPaused
                StatusFilter.ALL -> R.id.chipStatusAll
            }
        )
        binding.kindChips.check(
            when (state.kind) {
                KindFilter.ALL -> R.id.chipKindAll
                KindFilter.OVERALL -> R.id.chipKindOverall
                KindFilter.CATEGORY -> R.id.chipKindCategory
            }
        )

        // Summary: ENABLED monthly budgets only (paused ones never count).
        binding.summaryCard.visibility = if (state.totalLimitMinor > 0) View.VISIBLE else View.GONE
        binding.summaryAmounts.text = getString(
            R.string.budget_summary_amounts,
            formatMinorAsCurrency(state.totalSpentMinor), formatMinorAsCurrency(state.totalLimitMinor)
        )
        binding.summaryProgress.progress =
            if (state.totalLimitMinor > 0) (state.totalSpentMinor * 100 / state.totalLimitMinor).coerceIn(0, 100).toInt() else 0
        binding.summaryDetail.text = getString(R.string.budget_summary_detail, state.activeCount, state.pausedCount, state.overCount)

        val empty = state.items.isEmpty() && !state.isLoading
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.emptyState.setText(if (state.hiddenByFilters) R.string.budgets_empty_filtered else R.string.budgets_empty_state)
    }

    private fun onEvent(event: BudgetsEvent) {
        when (event) {
            is BudgetsEvent.Message -> Snackbar.make(binding.root, event.resId, Snackbar.LENGTH_SHORT).show()
            is BudgetsEvent.Deleted ->
                Snackbar.make(binding.root, R.string.budget_moved_to_trash, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo) { viewModel.restoreBudget(event.budgetId) }
                    .show()
        }
    }

    private fun openEditor(budgetId: String?) {
        findNavController().navigate(R.id.action_budgets_to_createBudget, bundleOf("budgetId" to budgetId))
    }

    private fun confirmDelete(item: BudgetProgressItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_budget_title)
            .setMessage(R.string.delete_budget_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteBudget(item) }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.budgetList.adapter = null
        _binding = null
    }
}
