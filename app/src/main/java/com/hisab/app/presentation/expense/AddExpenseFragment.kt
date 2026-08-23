package com.hisab.app.presentation.expense

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
import com.hisab.app.databinding.FragmentAddExpenseBinding
import com.hisab.app.di.ViewModelFactory
import kotlinx.coroutines.launch

/**
 * A bottom sheet rather than a full screen, deliberately — spec Section 12
 * wants expense entry to take 3-5 seconds, and a sheet that appears over the
 * dashboard (no page transition) is faster to reach and dismiss than pushing
 * a whole new screen.
 */
class AddExpenseFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAddExpenseBinding? = null
    private val binding get() = _binding!!

    private var selectedAccountId: String? = null
    private var selectedCategoryId: String? = null

    private val viewModel: AddExpenseViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.saveButton.setOnClickListener {
            viewModel.submit(
                amountMajor = binding.amountInput.text.toString(),
                categoryId = selectedCategoryId,
                accountId = selectedAccountId,
                note = binding.noteInput.text.toString()
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
                    viewModel.isSaving.collect { saving ->
                        binding.saveButton.isEnabled = !saving
                        binding.saveButton.text = getString(if (saving) R.string.action_saving else R.string.action_save)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AddExpenseEvent.Saved -> dismiss()
                            is AddExpenseEvent.ValidationError ->
                                Snackbar.make(binding.root, event.messageRes, Snackbar.LENGTH_SHORT).show()
                            is AddExpenseEvent.DuplicateWarning ->
                                Snackbar.make(binding.root, R.string.duplicate_warning_message, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.action_yes_save) {
                                        viewModel.submit(event.amount, event.categoryId, event.accountId, event.note, force = true)
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
