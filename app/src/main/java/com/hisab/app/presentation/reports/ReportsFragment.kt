package com.hisab.app.presentation.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentReportsBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.utils.formatMinorAsCurrency
import kotlinx.coroutines.launch

class ReportsFragment : Fragment() {
    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rangeDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, viewModel.rangeLabelList))
        binding.rangeDropdown.setText(viewModel.rangeLabelList.firstOrNull().orEmpty(), false)
        binding.rangeDropdown.setOnItemClickListener { _, _, position, _ -> viewModel.setRange(position) }

//        binding.openCalendarButton.setOnClickListener { findNavController().navigate(R.id.action_reports_to_calendar) }
//        binding.openTrashButton.setOnClickListener { findNavController().navigate(R.id.action_reports_to_trash) }
//        binding.openSettingsButton.setOnClickListener { findNavController().navigate(R.id.action_reports_to_settings) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.incomeExpenseText.text = getString(
                        R.string.report_income_expense,
                        formatMinorAsCurrency(state.incomeMinor),
                        formatMinorAsCurrency(state.expenseMinor)
                    )
                    binding.netCashFlowText.text = getString(R.string.report_net_cash_flow, formatMinorAsCurrency(state.netCashFlowMinor))
                    binding.expenseByCategoryText.text = getString(R.string.report_expense_by_category, state.expenseByCategory)
                    binding.incomeByCategoryText.text = getString(R.string.report_income_by_category, state.incomeByCategory)
                    binding.accountActivityText.text = getString(R.string.report_account_activity, state.accountActivity)
                    binding.monthlyTrendText.text = getString(R.string.report_monthly_trend, state.monthlyTrend)
                    binding.budgetPerformanceText.text = getString(R.string.report_budget_performance, state.budgetPerformance)
                    binding.lendingBorrowingText.text = getString(R.string.report_lending_borrowing, state.lendingBorrowing)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
