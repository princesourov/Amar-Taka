package com.hisab.app.presentation.savings

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
import com.hisab.app.databinding.FragmentContributeGoalBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.utils.formatMinorAsCurrency
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Add money to a goal (from an account's free balance) or withdraw it (back to the account it was set aside in). */
class ContributeToGoalFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentContributeGoalBinding? = null
    private val binding get() = _binding!!

    private val goalId by lazy { requireArguments().getString("goalId").orEmpty() }
    private val isWithdraw get() = arguments?.getString("mode") == MODE_WITHDRAW

    private val viewModel: GoalDetailViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container, goalId = goalId)
    }

    private class Choice(val accountId: String?, val label: String)
    private var choices: List<Choice> = emptyList()
    private var selected = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentContributeGoalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val name = arguments?.getString("goalName").orEmpty()
        binding.goalNameLabel.text = getString(if (isWithdraw) R.string.goal_withdraw_title else R.string.goal_add_title, name)

        binding.accountInput.setOnItemClickListener { _, _, pos, _ -> selected = pos }
        binding.saveButton.setOnClickListener {
            val accountId = choices.getOrNull(selected)?.accountId
            val amount = binding.amountInput.text.toString()
            val note = binding.noteInput.text?.toString()
            if (isWithdraw) viewModel.withdraw(accountId, amount, note)
            else if (accountId == null) Snackbar.make(binding.root, R.string.goal_error_no_account, Snackbar.LENGTH_SHORT).show()
            else viewModel.allocate(accountId, amount, note)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.accounts, viewModel.contributions) { a, c -> a to c }.collect { (accounts, rows) ->
                        val previousId = choices.getOrNull(selected)?.accountId
                        choices = if (isWithdraw) {
                            viewModel.allocationsByAccount(rows).filterValues { it > 0 }.map { (id, amt) ->
                                val label = accounts.firstOrNull { it.account.id == id }?.account?.name ?: getString(R.string.goal_unassigned_account)
                                Choice(id, getString(R.string.goal_account_set_aside, label, formatMinorAsCurrency(amt)))
                            }
                        } else {
                            accounts.map { Choice(it.account.id, getString(R.string.goal_account_available, it.account.name, formatMinorAsCurrency(it.availableMinor))) }
                        }
                        selected = choices.indexOfFirst { it.accountId == previousId }.takeIf { it >= 0 } ?: 0
                        binding.accountInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, choices.map { it.label }))
                        binding.accountInput.setText(choices.getOrNull(selected)?.label.orEmpty(), false)
                    }
                }
                launch { viewModel.isSaving.collect { binding.saveButton.isEnabled = !it } }
                launch {
                    viewModel.events.collect { event ->
                        val msg: String? = when (event) {
                            is SavingsGoalEvent.Saved -> { dismiss(); null }
                            is SavingsGoalEvent.Invalid -> getString(event.res)
                            is SavingsGoalEvent.NotEnoughAvailable -> getString(R.string.goal_error_not_enough_available, formatMinorAsCurrency(event.availableMinor))
                            is SavingsGoalEvent.NotEnoughSaved -> getString(R.string.goal_error_not_enough_saved, formatMinorAsCurrency(event.savedMinor))
                            is SavingsGoalEvent.Deleted -> null
                        }
                        msg?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val MODE_ADD = "add"
        const val MODE_WITHDRAW = "withdraw"
    }
}
