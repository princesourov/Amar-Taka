package com.hisab.app.presentation.recurring

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
import com.hisab.app.databinding.FragmentCreateRecurringBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.model.RecurrenceFrequency
import com.hisab.app.domain.model.TransactionType
import kotlinx.coroutines.launch

class CreateRecurringFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCreateRecurringBinding? = null
    private val binding get() = _binding!!

    private val frequencies = listOf(
        RecurrenceFrequency.DAILY, RecurrenceFrequency.WEEKLY,
        RecurrenceFrequency.MONTHLY, RecurrenceFrequency.YEARLY
    )
    private var selectedFrequency = RecurrenceFrequency.MONTHLY
    private var selectedType = TransactionType.EXPENSE
    private var selectedCategoryId: String? = null
    private var selectedAccountId: String? = null

    private val viewModel: RecurringViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateRecurringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.typeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedType = if (checkedId == binding.typeIncome.id) TransactionType.INCOME else TransactionType.EXPENSE
            binding.categoryDropdownLayout.visibility = if (selectedType == TransactionType.EXPENSE) View.VISIBLE else View.GONE
        }

        val frequencyLabels = listOf(
            getString(R.string.scope_daily), getString(R.string.scope_weekly),
            getString(R.string.scope_monthly), getString(R.string.scope_yearly)
        )
        binding.frequencyDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, frequencyLabels))
        binding.frequencyDropdown.setText(frequencyLabels[2], false)
        binding.frequencyDropdown.setOnItemClickListener { _, _, position, _ -> selectedFrequency = frequencies[position] }

        binding.saveButton.setOnClickListener {
            viewModel.create(
                selectedType, binding.amountInput.text.toString(), selectedCategoryId,
                selectedAccountId, selectedFrequency, binding.noteInput.text.toString()
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { list ->
                        binding.categoryDropdown.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, list.map { it.name })
                        )
                        binding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
                            selectedCategoryId = list[position].id
                        }
                    }
                }
                launch {
                    viewModel.accounts.collect { list ->
                        binding.accountDropdown.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, list.map { it.name })
                        )
                        binding.accountDropdown.setOnItemClickListener { _, _, position, _ ->
                            selectedAccountId = list[position].id
                        }
                        if (selectedAccountId == null && list.isNotEmpty()) {
                            selectedAccountId = list.first().id
                            binding.accountDropdown.setText(list.first().name, false)
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is RecurringEvent.Saved -> dismiss()
                            is RecurringEvent.ValidationError ->
                                Snackbar.make(binding.root, R.string.error_recurring_invalid, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
