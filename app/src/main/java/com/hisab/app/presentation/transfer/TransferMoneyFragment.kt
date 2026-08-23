package com.hisab.app.presentation.transfer

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
import com.hisab.app.databinding.FragmentTransferMoneyBinding
import com.hisab.app.di.ViewModelFactory
import kotlinx.coroutines.launch

class TransferMoneyFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentTransferMoneyBinding? = null
    private val binding get() = _binding!!

    private var fromAccountId: String? = null
    private var toAccountId: String? = null

    private val viewModel: TransferMoneyViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferMoneyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.saveButton.setOnClickListener {
            viewModel.submit(
                binding.amountInput.text.toString(), fromAccountId, toAccountId, binding.noteInput.text.toString()
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.accounts.collect { list ->
                        val names = list.map { it.name }
                        binding.fromAccountDropdown.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
                        )
                        binding.fromAccountDropdown.setOnItemClickListener { _, _, position, _ ->
                            fromAccountId = list[position].id
                        }
                        binding.toAccountDropdown.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
                        )
                        binding.toAccountDropdown.setOnItemClickListener { _, _, position, _ ->
                            toAccountId = list[position].id
                        }
                        if (fromAccountId == null && list.isNotEmpty()) {
                            fromAccountId = list.first().id
                            binding.fromAccountDropdown.setText(list.first().name, false)
                        }
                        if (toAccountId == null && list.size > 1) {
                            toAccountId = list[1].id
                            binding.toAccountDropdown.setText(list[1].name, false)
                        }
                    }
                }
                launch {
                    viewModel.isSaving.collect { saving ->
                        binding.saveButton.isEnabled = !saving
                        binding.saveButton.text = getString(if (saving) R.string.action_saving else R.string.action_save)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is TransferEvent.Saved -> dismiss()
                            is TransferEvent.ValidationError ->
                                Snackbar.make(binding.root, R.string.error_amount_required, Snackbar.LENGTH_SHORT).show()
                            is TransferEvent.SameAccount ->
                                Snackbar.make(binding.root, R.string.error_same_account_transfer, Snackbar.LENGTH_SHORT).show()
                            is TransferEvent.DuplicateWarning ->
                                Snackbar.make(binding.root, R.string.duplicate_warning_message, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.action_yes_save) {
                                        viewModel.submit(event.amount, event.fromAccountId, event.toAccountId, event.note, force = true)
                                    }.show()
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
