package com.hisab.app.presentation.savings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentSavingsGoalsBinding
import com.hisab.app.di.ViewModelFactory
import kotlinx.coroutines.launch

class SavingsGoalsFragment : Fragment() {

    private var _binding: FragmentSavingsGoalsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SavingsGoalsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    private val adapter = SavingsGoalAdapter { goal ->
        findNavController().navigate(R.id.action_savingsGoals_to_goalDetail, Bundle().apply { putString("goalId", goal.goal.id) })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSavingsGoalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.goalList.layoutManager = LinearLayoutManager(requireContext())
        binding.goalList.adapter = adapter
        binding.fabCreateGoal.setOnClickListener {
            findNavController().navigate(R.id.action_savingsGoals_to_createGoal)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.overview.collect { o ->
                    val items = o?.toListItems().orEmpty()
                    adapter.submitList(items)
                    binding.emptyState.visibility = if (o != null && items.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.goalList.adapter = null
        _binding = null
    }
}
