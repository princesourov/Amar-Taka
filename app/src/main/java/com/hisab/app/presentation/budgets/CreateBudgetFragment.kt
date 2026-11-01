package com.hisab.app.presentation.budgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentCreateBudgetBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.model.BudgetScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Add / edit sheet. All state lives in [BudgetEditorViewModel], so rotation never loses input. */
class CreateBudgetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCreateBudgetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BudgetEditorViewModel by viewModels {
        ViewModelFactory(
            (requireActivity().application as HisabApplication).container,
            budgetId = arguments?.getString("budgetId")
        )
    }

    private val scopes = listOf(BudgetScope.DAILY, BudgetScope.WEEKLY, BudgetScope.MONTHLY)
    private var populatedFromDraft = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleText.setText(if (viewModel.isEditing) R.string.edit_budget_title else R.string.create_budget_title)
        binding.recurrenceHint.setText(R.string.budget_recurrence_hint)

        val scopeLabels = listOf(R.string.scope_daily, R.string.scope_weekly, R.string.scope_monthly).map(::getString)
        binding.scopeDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, scopeLabels))
        binding.scopeDropdown.setOnItemClickListener { _, _, pos, _ -> viewModel.update { it.copy(scope = scopes[pos]) } }

        binding.nameInput.doAfterTextChanged { t ->
            binding.nameLayout.error = null
            val s = t?.toString().orEmpty()
            if (s != viewModel.draft.value.name) viewModel.update { it.copy(name = s) }
        }
        binding.amountInput.doAfterTextChanged { t ->
            binding.amountLayout.error = null
            val s = t?.toString().orEmpty()
            if (s != viewModel.draft.value.amountText) viewModel.update { it.copy(amountText = s) }
        }
        binding.activeSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != viewModel.draft.value.isActive) viewModel.update { it.copy(isActive = checked) }
        }
        binding.startDateButton.setOnClickListener { pickStartDate() }
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.saveButton.setOnClickListener { viewModel.save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { list ->
                        val labels = listOf(getString(R.string.budget_overall_option)) + list.map { it.name }
                        binding.categoryDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
                        binding.categoryDropdown.setOnItemClickListener { _, _, pos, _ ->
                            viewModel.update { it.copy(categoryId = if (pos == 0) null else list[pos - 1].id) }
                        }
                        val id = viewModel.draft.value.categoryId
                        val idx = id?.let { cid -> list.indexOfFirst { it.id == cid } + 1 } ?: 0
                        binding.categoryDropdown.setText(labels.getOrElse(idx) { labels[0] }, false)
                    }
                }
                launch { viewModel.loaded.collect { loaded ->
                    binding.loadingBar.visibility = if (loaded) View.GONE else View.VISIBLE
                    binding.saveButton.isEnabled = loaded
                    // Text fields are filled once the draft is ready; afterwards the user owns them.
                    if (loaded && !populatedFromDraft) {
                        val d = viewModel.draft.value
                        binding.nameInput.setText(d.name)
                        binding.amountInput.setText(d.amountText)
                        populatedFromDraft = true
                    }
                } }
                launch { viewModel.saving.collect { binding.saveButton.isEnabled = !it && viewModel.loaded.value } }
                launch { viewModel.draft.collect(::renderDraft) }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    private fun renderDraft(d: BudgetDraft) {
        binding.scopeDropdown.setText(getString(
            when (d.scope) {
                BudgetScope.DAILY -> R.string.scope_daily
                BudgetScope.WEEKLY -> R.string.scope_weekly
                BudgetScope.MONTHLY -> R.string.scope_monthly
            }
        ), false)
        if (binding.activeSwitch.isChecked != d.isActive) binding.activeSwitch.isChecked = d.isActive
        val date = LocalDate.ofEpochDay(d.startEpochDay)
        binding.startDateButton.text = getString(
            R.string.budget_start_date,
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
        )
    }

    private fun onEvent(event: EditorEvent) {
        when (event) {
            EditorEvent.Saved -> dismiss()
            is EditorEvent.FieldError -> {
                val layout = if (event.field == EditorEvent.Field.NAME) binding.nameLayout else binding.amountLayout
                layout.error = getString(event.messageRes)
            }
            is EditorEvent.Error -> Snackbar.make(binding.root, event.messageRes, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun pickStartDate() {
        val current = LocalDate.ofEpochDay(viewModel.draft.value.startEpochDay)
        val utcMillis = current.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = MaterialDatePicker.Builder.datePicker().setSelection(utcMillis).build()
        picker.addOnPositiveButtonClickListener { sel ->
            val day = java.time.Instant.ofEpochMilli(sel).atZone(java.time.ZoneOffset.UTC).toLocalDate().toEpochDay()
            viewModel.update { it.copy(startEpochDay = day) }
        }
        picker.show(childFragmentManager, "budget_start")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
