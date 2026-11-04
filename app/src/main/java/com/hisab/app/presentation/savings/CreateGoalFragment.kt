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
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentCreateGoalBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.utils.formatAmountForInput
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/** Create a goal, or edit one when a "goalId" argument is present. Editing never moves any money. */
class CreateGoalFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCreateGoalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SavingsGoalsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    private val editingId: String? get() = arguments?.getString("goalId")
    private var targetDay: Long? = null
    private var selectedIcon = GOAL_ICONS.first()
    private var accountIds: List<String?> = listOf(null)
    private var accountIndex = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateGoalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        targetDay = savedInstanceState?.getLong(KEY_DAY, Long.MIN_VALUE)?.takeIf { it != Long.MIN_VALUE }
        savedInstanceState?.getString(KEY_ICON)?.let { selectedIcon = it }

        GOAL_ICONS.forEach { icon ->
            val chip = Chip(requireContext()).apply {
                text = icon; isCheckable = true; id = View.generateViewId(); isChecked = icon == selectedIcon
                setOnClickListener { selectedIcon = icon }
            }
            binding.iconGroup.addView(chip)
        }
        updateDateButton()
        binding.dateButton.setOnClickListener {
            val initial = targetDay?.let { it * 86_400_000L } ?: MaterialDatePicker.todayInUtcMilliseconds()
            val picker = MaterialDatePicker.Builder.datePicker().setSelection(initial).build()
            picker.addOnPositiveButtonClickListener { utcMillis ->
                targetDay = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                updateDateButton()
            }
            picker.show(childFragmentManager, "goal_date")
        }
        binding.clearDateButton.setOnClickListener { targetDay = null; updateDateButton() }

        viewLifecycleOwner.lifecycleScope.launch {
            val accounts = viewModel.overview.value?.accounts.orEmpty().map { it.account }
            accountIds = listOf<String?>(null) + accounts.map { it.id }
            val labels = listOf(getString(R.string.goal_default_account_none)) + accounts.map { it.name }
            binding.accountInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
            binding.accountInput.setOnItemClickListener { _, _, pos, _ -> accountIndex = pos }
            binding.accountInput.setText(labels[0], false)

            editingId?.let { id ->
                binding.sheetTitle.setText(R.string.goal_edit_title)
                if (savedInstanceState == null) {
                    viewModel.loadGoal(id)?.let { g ->
                        binding.nameInput.setText(g.name)
                        binding.targetInput.setText(formatAmountForInput(g.targetAmountMinor))
                        binding.noteInput.setText(g.note)
                        targetDay = g.targetDateEpochDay
                        selectedIcon = goalIconOrDefault(g.iconKey)
                        accountIndex = accountIds.indexOf(g.accountId).coerceAtLeast(0)
                        binding.accountInput.setText(labels[accountIndex], false)
                        (0 until binding.iconGroup.childCount).forEach { i ->
                            (binding.iconGroup.getChildAt(i) as Chip).isChecked = (binding.iconGroup.getChildAt(i) as Chip).text == selectedIcon
                        }
                        updateDateButton()
                    }
                }
            }
        }

        binding.saveButton.setOnClickListener {
            viewModel.saveGoal(
                editingId, binding.nameInput.text.toString(), binding.targetInput.text.toString(),
                targetDay, accountIds.getOrNull(accountIndex), selectedIcon, binding.noteInput.text?.toString()
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SavingsGoalEvent.Saved -> dismiss()
                        is SavingsGoalEvent.Invalid -> Snackbar.make(binding.root, event.res, Snackbar.LENGTH_SHORT).show()
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun updateDateButton() {
        val d = targetDay
        binding.dateButton.text = if (d == null) getString(R.string.goal_no_target_date) else getString(R.string.goal_target_date_line, formatEpochDay(d))
        binding.clearDateButton.visibility = if (d == null) View.GONE else View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_DAY, targetDay ?: Long.MIN_VALUE)
        outState.putString(KEY_ICON, selectedIcon)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val KEY_DAY = "day"
        const val KEY_ICON = "icon"
    }
}
