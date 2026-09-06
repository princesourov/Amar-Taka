package com.hisab.app.presentation.transactions

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentTransactionsBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.model.TransactionType
import com.hisab.app.utils.DateRangeType
import kotlinx.coroutines.launch

class TransactionsFragment : Fragment() {

    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransactionsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    private val adapter = TransactionAdapter(
        onEdit = { item ->
            val args = Bundle().apply { putString("transactionId", item.id) }
            findNavController().navigate(R.id.action_transactions_to_editTransaction, args)
        },
        onDelete = { item -> confirmDelete(item.id) }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.transactionList.layoutManager = LinearLayoutManager(requireContext())
        binding.transactionList.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString())
            }
        })

        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val type = when (checkedIds.firstOrNull()) {
                binding.chipIncome.id -> TransactionType.INCOME
                binding.chipExpense.id -> TransactionType.EXPENSE
                binding.chipTransfer.id -> TransactionType.TRANSFER
                binding.chipLending.id -> TransactionType.LENDING
                binding.chipBorrowing.id -> TransactionType.BORROWING
                else -> null
            }
            viewModel.setTypeFilter(type)
        }

        val dateRanges = listOf(
            null,
            DateRangeType.TODAY,
            DateRangeType.LAST_3_DAYS,
            DateRangeType.LAST_7_DAYS,
            DateRangeType.LAST_15_DAYS,
            DateRangeType.LAST_30_DAYS,
            DateRangeType.LAST_90_DAYS,
            DateRangeType.LAST_6_MONTHS,
            DateRangeType.THIS_MONTH,
            DateRangeType.PREVIOUS_MONTH,
            DateRangeType.THIS_YEAR,
            DateRangeType.LAST_YEAR
        )
        val dateLabels = listOf(getString(R.string.filter_all)) + dateRanges.drop(1).map { displayName(it!!) }
        binding.dateRangeDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, dateLabels))
        binding.dateRangeDropdown.setText(dateLabels.first(), false)
        binding.dateRangeDropdown.setOnItemClickListener { _, _, position, _ ->
            viewModel.setDateRangeType(dateRanges[position])
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.accounts.collect { list ->
                        val labels = listOf(getString(R.string.filter_all)) + list.map { it.name }
                        binding.accountDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
                        binding.accountDropdown.setText(labels.first(), false)
                        binding.accountDropdown.setOnItemClickListener { _, _, position, _ ->
                            viewModel.setAccountFilter(if (position == 0) null else list[position - 1].id)
                        }
                    }
                }
                launch {
                    viewModel.people.collect { list ->
                        val labels = listOf(getString(R.string.filter_all)) + list.map { it.name }
                        binding.personDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
                        binding.personDropdown.setText(labels.first(), false)
                        binding.personDropdown.setOnItemClickListener { _, _, position, _ ->
                            viewModel.setPersonFilter(if (position == 0) null else list[position - 1].id)
                        }
                    }
                }
                launch {
                    viewModel.categories.collect { list ->
                        val labels = listOf(getString(R.string.filter_all)) + list.map { it.name }
                        binding.categoryDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
                        binding.categoryDropdown.setText(labels.first(), false)
                        binding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
                            viewModel.setCategoryFilter(if (position == 0) null else list[position - 1].id)
                        }
                    }
                }
                launch {
                    viewModel.items.collect { list ->
                        adapter.submitList(list)
                        binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun displayName(type: DateRangeType): String = when (type) {
        DateRangeType.TODAY -> getString(R.string.range_today)
        DateRangeType.LAST_3_DAYS -> getString(R.string.range_last_3_days)
        DateRangeType.LAST_7_DAYS -> getString(R.string.range_last_7_days)
        DateRangeType.LAST_15_DAYS -> getString(R.string.range_last_15_days)
        DateRangeType.LAST_30_DAYS -> getString(R.string.range_last_30_days)
        DateRangeType.LAST_90_DAYS -> getString(R.string.range_last_90_days)
        DateRangeType.LAST_6_MONTHS -> getString(R.string.range_last_6_months)
        DateRangeType.THIS_MONTH -> getString(R.string.range_this_month)
        DateRangeType.PREVIOUS_MONTH -> getString(R.string.range_previous_month)
        DateRangeType.THIS_YEAR -> getString(R.string.range_this_year)
        DateRangeType.LAST_YEAR -> getString(R.string.range_last_year)
        DateRangeType.CUSTOM -> getString(R.string.range_custom)
    }

    private fun confirmDelete(transactionId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_transaction_title)
            .setMessage(R.string.delete_transaction_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteTransaction(transactionId) }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
