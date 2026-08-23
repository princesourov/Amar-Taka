package com.hisab.app.presentation.savings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.launch

class ContributeToGoalFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentContributeGoalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SavingsGoalsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContributeGoalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val goalId = arguments?.getString("goalId").orEmpty()
        val goalName = arguments?.getString("goalName").orEmpty()
        binding.goalNameLabel.text = getString(R.string.contribute_to_goal_title, goalName)

        binding.saveButton.setOnClickListener {
            viewModel.contribute(goalId, binding.amountInput.text.toString())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SavingsGoalEvent.Saved -> dismiss()
                        is SavingsGoalEvent.ValidationError ->
                            Snackbar.make(binding.root, R.string.error_amount_required, Snackbar.LENGTH_SHORT).show()
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
