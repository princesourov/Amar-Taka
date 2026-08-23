package com.hisab.app.presentation.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.data.local.entity.TransactionEntity
import com.hisab.app.databinding.FragmentEditTransactionBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.model.TransactionType
import com.hisab.app.utils.formatMinorAsCurrency
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

class EditTransactionFragment : Fragment() {

    private var _binding: FragmentEditTransactionBinding? = null
    private val binding get() = _binding!!

    private val transactionId: String get() = requireArguments().getString("transactionId")!!

    private val viewModel: EditTransactionViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container, transactionId)
    }

    private var accounts: List<AccountEntity> = emptyList()
    private var categories: List<CategoryEntity> = emptyList()
    private var selectedPrimaryAccountId: String? = null
    private var selectedSecondaryAccountId: String? = null
    private var selectedCategoryId: String? = null
    private var selectedDateMillis: Long = System.currentTimeMillis()
    private var prefilled = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.dateInput.setOnClickListener { showDatePicker() }
        binding.saveButton.setOnClickListener {
            viewModel.save(
                amountMajor = binding.amountInput.text.toString(),
                sourceAccountId = resolveSourceAccountId(),
                destinationAccountId = resolveDestinationAccountId(),
                categoryId = selectedCategoryId,
                note = binding.noteInput.text.toString(),
                dateMillis = selectedDateMillis
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { list ->
                        categories = list
                        binding.categoryDropdown.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, list.map { it.name })
                        )
                        binding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
                            selectedCategoryId = list[position].id
                        }
                        prefillIfReady()
                    }
                }
                launch {
                    viewModel.accounts.collect { list ->
                        accounts = list
                        val names = list.map { it.name }
                        binding.primaryAccountDropdown.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
                        )
                        binding.primaryAccountDropdown.setOnItemClickListener { _, _, position, _ ->
                            selectedPrimaryAccountId = list[position].id
                        }
                        binding.secondaryAccountDropdown.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
                        )
                        binding.secondaryAccountDropdown.setOnItemClickListener { _, _, position, _ ->
                            selectedSecondaryAccountId = list[position].id
                        }
                        prefillIfReady()
                    }
                }
                launch {
                    viewModel.original.collect { prefillIfReady() }
                }
                launch {
                    viewModel.isSaving.collect { saving ->
                        binding.saveButton.isEnabled = !saving
                        binding.saveButton.text = getString(
                            if (saving) R.string.action_saving else R.string.action_save_changes
                        )
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is EditTransactionEvent.Saved -> findNavController().popBackStack()
                            is EditTransactionEvent.ValidationError ->
                                Snackbar.make(binding.root, R.string.error_amount_required, Snackbar.LENGTH_SHORT).show()
                            is EditTransactionEvent.InvalidAccounts ->
                                Snackbar.make(binding.root, R.string.error_same_account_transfer, Snackbar.LENGTH_SHORT).show()
                            is EditTransactionEvent.ExceedsOutstanding ->
                                Snackbar.make(
                                    binding.root,
                                    getString(R.string.error_exceeds_outstanding, formatMinorAsCurrency(event.outstandingMinor)),
                                    Snackbar.LENGTH_LONG
                                ).show()
                        }
                    }
                }
            }
        }
    }

    /** Runs once, only after the original transaction AND both dropdown lists have loaded,
     *  so pre-filled selections land on already-populated dropdowns. */
    private fun prefillIfReady() {
        if (prefilled) return
        val original = viewModel.original.value ?: return
        if (accounts.isEmpty() && (original.sourceAccountId != null || original.destinationAccountId != null)) return
        prefilled = true
        applyFieldVisibility(original)
        binding.amountInput.setText(formatAmountForInput(original.amountMinor))
        binding.noteInput.setText(original.note.orEmpty())
        selectedDateMillis = original.transactionDateMillis
        updateDateDisplay()

        val type = TransactionType.valueOf(original.type)
        val primaryId = if (isSourceRole(type)) original.sourceAccountId else original.destinationAccountId
        selectedPrimaryAccountId = primaryId
        accounts.find { it.id == primaryId }?.let { binding.primaryAccountDropdown.setText(it.name, false) }

        if (type == TransactionType.TRANSFER) {
            selectedSecondaryAccountId = original.destinationAccountId
            accounts.find { it.id == original.destinationAccountId }?.let {
                binding.secondaryAccountDropdown.setText(it.name, false)
            }
        }

        selectedCategoryId = original.categoryId
        categories.find { it.id == original.categoryId }?.let { binding.categoryDropdown.setText(it.name, false) }
    }

    private fun applyFieldVisibility(original: TransactionEntity) {
        val type = TransactionType.valueOf(original.type)
        binding.editTitle.text = getString(R.string.edit_transaction_title, typeLabel(type))

        val needsCategory = type == TransactionType.EXPENSE || type == TransactionType.INCOME
        binding.categoryLayout.visibility = if (needsCategory) View.VISIBLE else View.GONE

        binding.primaryAccountLayout.hint = getString(
            if (isSourceRole(type)) R.string.hint_paid_from else R.string.hint_into_account
        )

        binding.secondaryAccountLayout.visibility = if (type == TransactionType.TRANSFER) View.VISIBLE else View.GONE

        val hasPerson = type == TransactionType.LENDING || type == TransactionType.BORROWING ||
            type == TransactionType.REPAYMENT_RECEIVED || type == TransactionType.REPAYMENT_MADE
        if (hasPerson) {
            binding.personLabel.visibility = View.VISIBLE
            binding.personLabel.text = getString(R.string.edit_transaction_person_label)
        } else {
            binding.personLabel.visibility = View.GONE
        }
    }

    private fun isSourceRole(type: TransactionType): Boolean = when (type) {
        TransactionType.EXPENSE, TransactionType.TRANSFER, TransactionType.LENDING, TransactionType.REPAYMENT_MADE -> true
        else -> false
    }

    private fun resolveSourceAccountId(): String? {
        val type = viewModel.original.value?.type?.let { TransactionType.valueOf(it) } ?: return null
        return if (isSourceRole(type)) selectedPrimaryAccountId else null
    }

    private fun resolveDestinationAccountId(): String? {
        val type = viewModel.original.value?.type?.let { TransactionType.valueOf(it) } ?: return null
        return when {
            type == TransactionType.TRANSFER -> selectedSecondaryAccountId
            !isSourceRole(type) -> selectedPrimaryAccountId
            else -> null
        }
    }

    private fun typeLabel(type: TransactionType): String = when (type) {
        TransactionType.EXPENSE -> getString(R.string.type_expense)
        TransactionType.INCOME -> getString(R.string.type_income)
        TransactionType.TRANSFER -> getString(R.string.type_transfer)
        TransactionType.LENDING -> getString(R.string.txn_gave)
        TransactionType.BORROWING -> getString(R.string.txn_took)
        TransactionType.REPAYMENT_RECEIVED -> getString(R.string.txn_repaid_by_them)
        TransactionType.REPAYMENT_MADE -> getString(R.string.txn_repaid_by_you)
        else -> type.name
    }

    private fun formatAmountForInput(amountMinor: Long): String =
        if (amountMinor % 100 == 0L) (amountMinor / 100).toString() else "%.2f".format(amountMinor / 100.0)

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(utcMillisForLocalMillis(selectedDateMillis))
            .build()
        picker.addOnPositiveButtonClickListener { utcSelection ->
            selectedDateMillis = localMillisForUtcMillis(utcSelection)
            updateDateDisplay()
        }
        picker.show(childFragmentManager, "edit_transaction_date_picker")
    }

    private fun updateDateDisplay() {
        binding.dateInput.setText(SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis)))
    }

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
