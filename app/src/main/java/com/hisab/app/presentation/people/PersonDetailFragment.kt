package com.hisab.app.presentation.people

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
import com.hisab.app.databinding.FragmentPersonDetailBinding
import com.hisab.app.databinding.ItemDebtRecordBinding
import com.hisab.app.domain.debt.DebtDirection
import com.hisab.app.domain.debt.DebtStatus
import com.hisab.app.domain.debt.PersonDebt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.presentation.transactions.TransactionAdapter
import com.hisab.app.utils.formatMinorAsCurrency
import kotlinx.coroutines.launch

class PersonDetailFragment : Fragment() {

    private var _binding: FragmentPersonDetailBinding? = null
    private val binding get() = _binding!!

    private val personId: String get() = requireArguments().getString("personId")!!

    private val viewModel: PersonDetailViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container, personId = personId)
    }

    private val historyAdapter = TransactionAdapter(
        onEdit = { item ->
            val args = Bundle().apply { putString("transactionId", item.id) }
            findNavController().navigate(R.id.action_personDetail_to_editTransaction, args)
        },
        onDelete = { item ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_transaction_title)
                .setMessage(R.string.delete_transaction_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteTransaction(item.id) }
                .show()
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = historyAdapter

        binding.debtFilterGroup.setOnCheckedStateChangeListener { _, ids ->
            filter = when (ids.firstOrNull()) {
                R.id.filterOpen -> RecordFilter.OPEN
                R.id.filterSettled -> RecordFilter.SETTLED
                else -> RecordFilter.ALL
            }
            renderRecords()
        }

        binding.giveButton.setOnClickListener { openAction(PersonMoneyAction.GIVE) }
        binding.takeButton.setOnClickListener { openAction(PersonMoneyAction.TAKE) }
        binding.receiveRepaymentButton.setOnClickListener { openAction(PersonMoneyAction.RECEIVE_REPAYMENT) }
        binding.makeRepaymentButton.setOnClickListener { openAction(PersonMoneyAction.MAKE_REPAYMENT) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.person.collect { person ->
                        binding.personName.text = person?.name.orEmpty()
                        val contact = listOfNotNull(person?.phone, person?.email).joinToString("  ·  ")
                        binding.personContact.text = contact
                        binding.personContact.visibility = if (contact.isBlank()) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.debt.collect { d ->
                        lastDebt = d
                        binding.personNet.text = formatMinorAsCurrency(d.netMinor)
                        binding.personNetHint.text = getString(
                            when {
                                d.netMinor > 0 -> R.string.label_you_will_receive
                                d.netMinor < 0 -> R.string.label_you_will_pay
                                else -> R.string.person_settled
                            }
                        )
                        binding.personReceivable.text = formatMinorAsCurrency(d.receivableMinor)
                        binding.personPayable.text = formatMinorAsCurrency(d.payableMinor)
                        // Each direction has its own repay button; they are independent.
                        binding.receiveRepaymentButton.visibility = if (d.receivableMinor > 0) View.VISIBLE else View.GONE
                        binding.makeRepaymentButton.visibility = if (d.payableMinor > 0) View.VISIBLE else View.GONE
                        val surplus = d.surplusReceivedMinor + d.surplusMadeMinor
                        binding.surplusNote.visibility = if (surplus > 0) View.VISIBLE else View.GONE
                        binding.surplusNote.text = getString(R.string.debt_surplus_note, formatMinorAsCurrency(surplus))
                        renderRecords()
                    }
                }
                launch {
                    viewModel.transactions.collect { list ->
                        historyAdapter.submitList(list)
                        binding.historyEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private enum class RecordFilter { ALL, OPEN, SETTLED }
    private var filter = RecordFilter.ALL
    private var lastDebt: PersonDebt? = null
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

    private fun fmtDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFmt)

    private fun renderRecords() {
        val debt = lastDebt ?: return
        binding.debtRecordList.removeAllViews()
        val shown = debt.records
            .filter {
                when (filter) {
                    RecordFilter.ALL -> true
                    RecordFilter.OPEN -> it.status != DebtStatus.SETTLED
                    RecordFilter.SETTLED -> it.status == DebtStatus.SETTLED
                }
            }
            .sortedByDescending { it.dateMillis }
        binding.debtRecordsEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        shown.forEach { r ->
            val row = ItemDebtRecordBinding.inflate(layoutInflater, binding.debtRecordList, false)
            row.recordTitle.text = getString(
                R.string.trash_type_line,
                getString(if (r.direction == DebtDirection.RECEIVABLE) R.string.debt_direction_receivable else R.string.debt_direction_payable),
                formatMinorAsCurrency(r.principalMinor)
            )
            row.recordSubtitle.text = r.note?.takeIf { it.isNotBlank() }?.let { "${fmtDate(r.dateMillis)} · $it" } ?: fmtDate(r.dateMillis)
            row.recordStatus.text = getString(
                when (r.status) {
                    DebtStatus.ACTIVE -> R.string.debt_status_active
                    DebtStatus.PARTIALLY_PAID -> R.string.debt_status_partial
                    DebtStatus.SETTLED -> R.string.debt_status_settled
                }
            )
            row.recordProgress.progress = if (r.principalMinor <= 0) 0 else (r.paidMinor * 100 / r.principalMinor).toInt().coerceIn(0, 100)
            row.recordAmounts.text = if (r.status == DebtStatus.SETTLED) getString(R.string.debt_record_amounts_settled, formatMinorAsCurrency(r.principalMinor))
            else getString(R.string.debt_record_amounts, formatMinorAsCurrency(r.paidMinor), formatMinorAsCurrency(r.remainingMinor), formatMinorAsCurrency(r.principalMinor))
            if (r.payments.isEmpty()) row.recordPayments.visibility = View.GONE
            else {
                row.recordPayments.visibility = View.VISIBLE
                row.recordPayments.text = r.payments.joinToString("\n") {
                    getString(R.string.debt_payment_line, formatMinorAsCurrency(it.amountMinor), fmtDate(it.dateMillis))
                }
            }
            binding.debtRecordList.addView(row.root)
        }
    }

    private fun openAction(action: PersonMoneyAction) {
        val args = Bundle().apply {
            putString("personId", personId)
            putString("personName", binding.personName.text.toString())
            putString("action", action.name)
        }
        findNavController().navigate(R.id.action_personDetail_to_personAction, args)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
