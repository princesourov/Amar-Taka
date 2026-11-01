package com.hisab.app.presentation.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.SheetReportTransactionsBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.model.TransactionDisplayItem
import com.hisab.app.domain.report.FlowKind
import com.hisab.app.domain.report.LedgerRules
import com.hisab.app.presentation.transactions.TransactionAdapter
import com.hisab.app.utils.formatMinorAsCurrency
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Drill-down: the transactions behind a report figure — one category, or everything counted in the
 * report. It reads the SAME state as the report (shared ReportsViewModel of the parent fragment),
 * so the rows here always add up to the totals on the screen behind it, and follow its period and
 * filters. Tapping a row opens it for editing.
 */
class ReportTransactionsSheet : BottomSheetDialogFragment() {

    private var _binding: SheetReportTransactionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportsViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { ViewModelFactory((requireActivity().application as HisabApplication).container) }
    )

    private val reports get() = requireParentFragment() as ReportsFragment

    private val adapter = TransactionAdapter(
        onEdit = { item -> dismiss(); reports.openTransaction(item.id) },
        onDelete = { item -> reports.confirmDelete(item.id) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetReportTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val key = requireArguments().getString(ARG_KEY, KEY_ALL)
        binding.sheetTitle.text = requireArguments().getString(ARG_TITLE).orEmpty()
        binding.sheetList.layoutManager = LinearLayoutManager(requireContext())
        binding.sheetList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val items: List<TransactionDisplayItem> = when (key) {
                        KEY_ALL -> state.items
                        KEY_UNCATEGORIZED -> state.items.filter { it.transaction.categoryId == null }
                        else -> state.items.filter { it.transaction.categoryId == key }
                    }
                    adapter.submitList(items)
                    binding.sheetEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    // Net of refunds, exactly like the report totals.
                    val net = items.sumOf {
                        when (LedgerRules.kindOf(it.transaction.type)) {
                            FlowKind.EXPENSE, FlowKind.INCOME -> it.transaction.amountMinor
                            FlowKind.REFUND -> -it.transaction.amountMinor
                            else -> 0L
                        }
                    }
                    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                    val span = state.span
                    binding.sheetSubtitle.text = listOfNotNull(
                        resources.getQuantityString(R.plurals.report_transactions, items.size, items.size),
                        if (key == KEY_ALL) null else formatMinorAsCurrency(net),
                        span?.let { "${it.start.format(fmt)} – ${it.end.format(fmt)}" }
                    ).joinToString(" · ")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.sheetList.adapter = null
        _binding = null
    }

    companion object {
        const val KEY_ALL = "__all__"
        const val KEY_UNCATEGORIZED = "__none__"
        private const val ARG_KEY = "key"
        private const val ARG_TITLE = "title"

        fun show(fm: FragmentManager, key: String, title: String) {
            ReportTransactionsSheet().apply {
                arguments = Bundle().apply { putString(ARG_KEY, key); putString(ARG_TITLE, title) }
            }.show(fm, "report_transactions")
        }
    }
}
