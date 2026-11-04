package com.hisab.app.presentation.savings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.data.local.entity.GoalContributionEntity
import com.hisab.app.databinding.FragmentGoalDetailBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.utils.formatMinorAsCurrency
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GoalDetailFragment : Fragment() {

    private var _binding: FragmentGoalDetailBinding? = null
    private val binding get() = _binding!!

    private val goalId by lazy { requireArguments().getString("goalId").orEmpty() }

    private val viewModel: GoalDetailViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container, goalId = goalId)
    }

    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGoalDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.addMoneyButton.setOnClickListener { openMoneySheet(ContributeToGoalFragment.MODE_ADD) }
        binding.withdrawButton.setOnClickListener { openMoneySheet(ContributeToGoalFragment.MODE_WITHDRAW) }
        binding.editGoalButton.setOnClickListener {
            findNavController().navigate(R.id.action_goalDetail_to_createGoal, Bundle().apply { putString("goalId", goalId) })
        }
        binding.deleteGoalButton.setOnClickListener {
            val g = viewModel.goal.value ?: return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.goal_delete_title)
                .setMessage(getString(R.string.goal_delete_message, g.goal.name))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteGoal() }
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.goal.collect { g ->
                        if (g == null) return@collect
                        binding.detailIcon.text = goalIconOrDefault(g.goal.iconKey)
                        binding.detailName.text = g.goal.name
                        binding.detailStatus.text = goalStatusLine(requireContext(), g)
                        binding.detailNote.text = g.goal.note
                        binding.detailNote.visibility = if (g.goal.note.isNullOrBlank()) View.GONE else View.VISIBLE
                        binding.detailSaved.text = formatMinorAsCurrency(g.savedMinor)
                        binding.detailOfTarget.text = getString(
                            R.string.goal_of_target_line,
                            formatMinorAsCurrency(g.goal.targetAmountMinor), formatMinorAsCurrency(g.remainingMinor)
                        )
                        binding.detailProgress.progress = g.percent
                        binding.detailPercent.text = getString(R.string.goal_percent, g.percent)
                        binding.withdrawButton.isEnabled = g.savedMinor > 0
                    }
                }
                launch {
                    kotlinx.coroutines.flow.combine(viewModel.contributions, viewModel.accounts) { c, a -> c to a }.collect { (rows, accounts) ->
                        renderAllocations(rows, accounts.associate { it.account.id to it.account.name })
                        renderHistory(rows, accounts.associate { it.account.id to it.account.name })
                    }
                }
                launch {
                    viewModel.events.collect { e ->
                        if (e is SavingsGoalEvent.Deleted) {
                            Snackbar.make(requireActivity().findViewById(android.R.id.content), getString(R.string.goal_deleted_message, e.name), Snackbar.LENGTH_LONG)
                                .setAction(R.string.action_undo) { viewModel.undoDelete() }.show()
                            findNavController().popBackStack()
                        }
                    }
                }
            }
        }
    }

    private fun openMoneySheet(mode: String) {
        val name = viewModel.goal.value?.goal?.name ?: return
        findNavController().navigate(
            R.id.action_goalDetail_to_contributeGoal,
            Bundle().apply { putString("goalId", goalId); putString("goalName", name); putString("mode", mode) }
        )
    }

    private fun row(text: String, bold: Boolean = false): TextView = TextView(requireContext()).apply {
        this.text = text
        setPadding(0, 8, 0, 8)
        setTextAppearance(if (bold) com.google.android.material.R.style.TextAppearance_Material3_BodyLarge else com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
    }

    private fun renderAllocations(rows: List<GoalContributionEntity>, names: Map<String, String>) {
        binding.allocationList.removeAllViews()
        viewModel.allocationsByAccount(rows).forEach { (accountId, amount) ->
            val name = accountId?.let { names[it] } ?: getString(R.string.goal_unassigned_account)
            binding.allocationList.addView(row(getString(R.string.goal_history_line, name, formatMinorAsCurrency(amount))))
        }
    }

    private fun renderHistory(rows: List<GoalContributionEntity>, names: Map<String, String>) {
        binding.historyList.removeAllViews()
        binding.historyEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        rows.sortedByDescending { it.dateMillis }.forEach { c ->
            val amount = formatMinorAsCurrency(kotlin.math.abs(c.amountMinor))
            val label = getString(if (c.amountMinor >= 0) R.string.goal_history_added else R.string.goal_history_released, amount)
            val date = Instant.ofEpochMilli(c.dateMillis).atZone(ZoneId.systemDefault()).format(dateFmt)
            val where = c.accountId?.let { names[it] } ?: getString(R.string.goal_unassigned_account)
            binding.historyList.addView(row(getString(R.string.goal_history_line, label, "$where · $date")))
            c.note?.takeIf { it.isNotBlank() }?.let { binding.historyList.addView(row(it).apply { alpha = 0.7f }) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
