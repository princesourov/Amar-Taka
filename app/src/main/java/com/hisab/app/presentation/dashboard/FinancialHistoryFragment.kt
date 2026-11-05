package com.hisab.app.presentation.dashboard

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
import com.hisab.app.databinding.FragmentFinancialHistoryBinding
import com.hisab.app.di.ViewModelFactory
import kotlinx.coroutines.launch

/** Last 30 Days Financial History, opened from the Dashboard's Income / Expense / Net / Savings rate figures. */
class FinancialHistoryFragment : Fragment() {

    private var _binding: FragmentFinancialHistoryBinding? = null
    private val binding get() = _binding!!

    private val adapter = DailySummaryAdapter()

    private val viewModel: FinancialHistoryViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFinancialHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = adapter
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.retryButton.setOnClickListener { viewModel.retry() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refreshToday()
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: FinancialHistoryUiState) {
        binding.loading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.errorGroup.visibility = if (state.hasError) View.VISIBLE else View.GONE
        binding.historyList.visibility = if (state.isLoading || state.hasError) View.GONE else View.VISIBLE
        // All 30 days are always listed; with no activity at all we only add a note above them.
        binding.emptyNote.visibility =
            if (!state.isLoading && !state.hasError && !state.hasActivity) View.VISIBLE else View.GONE
        adapter.submitList(state.days)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.historyList.adapter = null
        _binding = null
    }
}
