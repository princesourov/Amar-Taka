package com.hisab.app.presentation.accounts

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
import com.hisab.app.data.local.entity.AccountEntity
import com.hisab.app.databinding.FragmentCreateAccountBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.model.AccountType
import kotlinx.coroutines.launch

class CreateAccountFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCreateAccountBinding? = null
    private val binding get() = _binding!!

    private val accountId: String? get() = arguments?.getString("accountId")
    private var editingAccount: AccountEntity? = null
    private var selectedType: AccountType = AccountType.CUSTOM

    private val viewModel: AccountsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    private val types = listOf(
        AccountType.POCKET, AccountType.BANK, AccountType.BKASH, AccountType.NAGAD,
        AccountType.ROCKET, AccountType.CUSTOM
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val typeLabels = listOf(
            getString(R.string.account_type_cash), getString(R.string.account_type_bank),
            getString(R.string.account_type_bkash), getString(R.string.account_type_nagad),
            getString(R.string.account_type_rocket), getString(R.string.account_type_other)
        )
        binding.typeDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, typeLabels))
        binding.typeDropdown.setOnItemClickListener { _, _, position, _ -> selectedType = types[position] }

        val editId = accountId
        if (editId != null) {
            binding.formTitle.text = getString(R.string.edit_account_title)
            // Type is fixed once an account exists — a deliberate scope decision, see AccountRepository.
            binding.typeDropdownLayout.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val account = viewModel.loadForEdit(editId)
                editingAccount = account
                account?.let { a ->
                    binding.nameInput.setText(a.name)
                    binding.openingBalanceInput.setText(formatAmountForInput(a.openingBalanceMinor))
                    binding.notesInput.setText(a.notes.orEmpty())
                    selectedType = AccountType.valueOf(a.type)
                    val idx = types.indexOf(selectedType)
                    if (idx >= 0) binding.typeDropdown.setText(typeLabels[idx], false)
                }
            }
        } else {
            binding.formTitle.text = getString(R.string.create_account_title)
            binding.typeDropdown.setText(typeLabels[0], false)
            selectedType = types[0]
        }

        binding.saveButton.setOnClickListener {
            val name = binding.nameInput.text.toString()
            val opening = binding.openingBalanceInput.text.toString()
            val notes = binding.notesInput.text.toString()
            val existing = editingAccount
            if (existing != null) {
                viewModel.updateAccount(existing, name, opening, notes)
            } else {
                viewModel.createAccount(name, selectedType, opening, notes)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isSaving.collect { saving ->
                        binding.saveButton.isEnabled = !saving
                        binding.saveButton.text = getString(if (saving) R.string.action_saving else R.string.action_save)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AccountEvent.Saved -> dismiss()
                            is AccountEvent.ValidationError ->
                                Snackbar.make(binding.root, R.string.error_account_name_required, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun formatAmountForInput(amountMinor: Long): String =
        if (amountMinor % 100 == 0L) (amountMinor / 100).toString() else "%.2f".format(amountMinor / 100.0)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
