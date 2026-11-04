package com.hisab.app.presentation.recurring

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentRecurringBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.usecase.SkipReason
import kotlinx.coroutines.launch

class RecurringFragment : Fragment() {

    private var _binding: FragmentRecurringBinding? = null
    private val binding get() = _binding!!
    private val adapter = RecurringTransactionAdapter(
        onToggle = { viewModel.setActive(it.id, !it.isActive) },
        onDelete = { item ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.recurring_delete_title)
                .setMessage(R.string.recurring_delete_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(item.id) }
                .show()
        },
        onEdit = { item ->
            findNavController().navigate(R.id.action_recurring_to_createRecurring, Bundle().apply { putString("recurringId", item.id) })
        },
        onHistory = { item -> showHistory(item.id) }
    )

    private val viewModel: RecurringViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecurringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recurringList.layoutManager = LinearLayoutManager(requireContext())
        binding.recurringList.adapter = adapter

        binding.fabCreateRecurring.setOnClickListener {
            findNavController().navigate(R.id.action_recurring_to_createRecurring)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refreshAttention()
                launch {
                    viewModel.recurringTransactions.collect { list ->
                        adapter.submitList(list)
                        binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.needsAttention.collect { map ->
                        adapter.attention = map.mapValues { (_, reason) ->
                            getString(
                                R.string.recurring_needs_attention,
                                getString(
                                    when (reason) {
                                        SkipReason.ACCOUNT_MISSING_OR_ARCHIVED -> R.string.recurring_skip_account
                                        SkipReason.INVALID_AMOUNT -> R.string.recurring_skip_amount
                                        SkipReason.UNSUPPORTED_TYPE -> R.string.recurring_skip_type
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showHistory(scheduleId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val rows = viewModel.history(scheduleId)
            val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
            val text = if (rows.isEmpty()) getString(R.string.recurring_history_empty) else rows.joinToString("\n") { t ->
                val date = java.time.Instant.ofEpochMilli(t.transactionDateMillis).atZone(java.time.ZoneId.systemDefault()).format(fmt)
                getString(if (t.isDeleted) R.string.recurring_history_trashed else R.string.recurring_history_line, date, com.hisab.app.utils.formatMinorAsCurrency(t.amountMinor))
            }
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.recurring_history_title).setMessage(text)
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
