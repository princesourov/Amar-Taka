package com.hisab.app.presentation.people

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
import com.hisab.app.databinding.FragmentPersonActionBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.utils.formatMinorAsCurrency
import kotlinx.coroutines.launch

class PersonActionFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentPersonActionBinding? = null
    private val binding get() = _binding!!

    private val personId: String get() = requireArguments().getString("personId")!!
    private val personName: String get() = requireArguments().getString("personName")!!
    private val action: PersonMoneyAction get() = PersonMoneyAction.valueOf(requireArguments().getString("action")!!)

    private var selectedAccountId: String? = null

    private val viewModel: PersonDetailViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container, personId = personId)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonActionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.actionTitle.text = titleFor(action)
        binding.personNameLabel.text = personName
        val accountRoleIsSource = action == PersonMoneyAction.GIVE || action == PersonMoneyAction.MAKE_REPAYMENT
        binding.accountLayout.hint = getString(if (accountRoleIsSource) R.string.hint_paid_from else R.string.hint_into_account)

        binding.saveButton.setOnClickListener {
            viewModel.performAction(
                action, binding.amountInput.text.toString(), selectedAccountId, binding.noteInput.text.toString()
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                            is PersonActionEvent.Saved -> dismiss()
                            is PersonActionEvent.ValidationError ->
                                Snackbar.make(binding.root, R.string.error_amount_required, Snackbar.LENGTH_SHORT).show()
                            is PersonActionEvent.ExceedsOutstanding ->
                                Snackbar.make(
                                    binding.root,
                                    getString(R.string.error_exceeds_outstanding, formatMinorAsCurrency(event.outstandingMinor)),
                                    Snackbar.LENGTH_LONG
                                ).show()
                            is PersonActionEvent.DuplicateWarning ->
                                Snackbar.make(binding.root, R.string.duplicate_warning_message, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.action_yes_save) {
                                        viewModel.performAction(event.action, event.amount, event.accountId, event.note, force = true)
                                    }.show()
                        }
                    }
                }
            }
        }
    }

    private fun titleFor(action: PersonMoneyAction): String = when (action) {
        PersonMoneyAction.GIVE -> getString(R.string.action_give_money)
        PersonMoneyAction.TAKE -> getString(R.string.action_take_money)
        PersonMoneyAction.RECEIVE_REPAYMENT -> getString(R.string.action_receive_repayment)
        PersonMoneyAction.MAKE_REPAYMENT -> getString(R.string.action_make_repayment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
