package com.hisab.app.presentation.budgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentCreateBudgetBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.model.BudgetScope
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class CreateBudgetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCreateBudgetBinding? = null
    private val binding get() = _binding!!
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    private val scopes = listOf(BudgetScope.DAILY, BudgetScope.WEEKLY, BudgetScope.MONTHLY)
    private var selectedScope = BudgetScope.MONTHLY
    private var selectedCategoryId: String? = null
    private var selectedMonth = YearMonth.now()
    private var editingId: String? = null
    private var editingActive: Boolean = true

    private val viewModel: BudgetsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editingId = arguments?.getString("budgetId")
        binding.titleText.text = if (editingId == null) getString(R.string.create_budget_title) else getString(R.string.edit_budget_title)

        val scopeLabels = listOf(
            getString(R.string.scope_daily),
            getString(R.string.scope_weekly),
            getString(R.string.scope_monthly)
        )
        binding.scopeDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, scopeLabels))
        binding.scopeDropdown.setText(scopeLabels[2], false)
        binding.scopeDropdown.setOnItemClickListener { _, _, position, _ -> selectedScope = scopes[position] }
        renderMonth()
        binding.monthPrevButton.setOnClickListener {
            selectedMonth = selectedMonth.minusMonths(1)
            renderMonth()
        }
        binding.monthNextButton.setOnClickListener {
            selectedMonth = selectedMonth.plusMonths(1)
            renderMonth()
        }

        binding.saveButton.setOnClickListener {
            val state = BudgetEditorState(
                budgetId = editingId,
                scope = selectedScope,
                categoryId = selectedCategoryId,
                amountText = binding.amountInput.text.toString(),
                month = selectedMonth,
                isActive = editingActive
            )
            viewModel.saveBudget(state)
        }

        editingId?.let { viewModel.loadBudgetForEdit(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { list ->
                        val labels = listOf(getString(R.string.budget_overall_option)) + list.map { it.name }
                        binding.categoryDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
                        val selectedIndex = selectedCategoryId?.let { id -> list.indexOfFirst { it.id == id } + 1 } ?: 0
                        binding.categoryDropdown.setText(labels.getOrElse(selectedIndex) { labels[0] }, false)
                        binding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
                            selectedCategoryId = if (position == 0) null else list[position - 1].id
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is BudgetEvent.Saved -> dismiss()
                            is BudgetEvent.ValidationError ->
                                Snackbar.make(binding.root, R.string.error_amount_required, Snackbar.LENGTH_SHORT).show()
                            is BudgetEvent.EditReady -> bindEditorState(event.state)
                        }
                    }
                }
            }
        }
    }

    private fun bindEditorState(state: BudgetEditorState) {
        editingId = state.budgetId
        selectedScope = state.scope
        selectedCategoryId = state.categoryId
        selectedMonth = state.month
        editingActive = state.isActive
        binding.amountInput.setText(state.amountText)
        val scopeIndex = scopes.indexOf(selectedScope).coerceAtLeast(0)
        val scopeLabels = listOf(getString(R.string.scope_daily), getString(R.string.scope_weekly), getString(R.string.scope_monthly))
        binding.scopeDropdown.setText(scopeLabels[scopeIndex], false)
        renderMonth()
    }

    private fun renderMonth() {
        binding.monthLabel.text = selectedMonth.format(monthFormatter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
