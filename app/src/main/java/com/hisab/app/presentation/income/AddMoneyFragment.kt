package com.hisab.app.presentation.income

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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentAddMoneyBinding
import com.hisab.app.di.ViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

class AddMoneyFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAddMoneyBinding? = null
    private val binding get() = _binding!!

    private var selectedAccountId: String? = null
    private var selectedCategoryId: String? = null
    private var selectedDateMillis: Long = System.currentTimeMillis()

    private val viewModel: AddMoneyViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddMoneyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateDateDisplay()
        binding.dateInput.setOnClickListener { showDatePicker() }

        binding.saveButton.setOnClickListener {
            viewModel.submit(
                amountMajor = binding.amountInput.text.toString(),
                accountId = selectedAccountId,
                categoryId = selectedCategoryId,
                note = binding.noteInput.text.toString(),
                dateMillis = selectedDateMillis
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { list ->
                        val labels = listOf(getString(R.string.source_none_option)) + list.map { it.name }
                        binding.categoryDropdown.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
                        )
                        binding.categoryDropdown.setText(labels[0], false)
                        binding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
                            selectedCategoryId = if (position == 0) null else list[position - 1].id
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
                    viewModel.isSaving.collect { saving ->
                        binding.saveButton.isEnabled = !saving
                        binding.saveButton.text = getString(
                            if (saving) R.string.action_saving else R.string.action_save
                        )
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AddMoneyEvent.Saved -> dismiss()
                            is AddMoneyEvent.ValidationError ->
                                Snackbar.make(binding.root, event.messageRes, Snackbar.LENGTH_SHORT).show()
                            is AddMoneyEvent.DuplicateWarning ->
                                Snackbar.make(binding.root, R.string.duplicate_warning_message, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.action_yes_save) {
                                        viewModel.submit(
                                            event.amount, event.accountId, event.categoryId, event.note,
                                            event.dateMillis, force = true
                                        )
                                    }.show()
                        }
                    }
                }
            }
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(utcMillisForLocalMillis(selectedDateMillis))
            .build()
        picker.addOnPositiveButtonClickListener { utcSelection ->
            selectedDateMillis = localMillisForUtcMillis(utcSelection)
            updateDateDisplay()
        }
        picker.show(childFragmentManager, "add_money_date_picker")
    }

    private fun updateDateDisplay() {
        binding.dateInput.setText(SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis)))
    }

    /** MaterialDatePicker works in UTC-midnight selections — convert to/from local-zone midnight
     *  so the stored date matches how the rest of the app (DateRanges) reasons about "today". */
    private fun utcMillisForLocalMillis(localMillis: Long): Long {
        val localDate = Instant.ofEpochMilli(localMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    private fun localMillisForUtcMillis(utcMillis: Long): Long {
        val utcDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return utcDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
