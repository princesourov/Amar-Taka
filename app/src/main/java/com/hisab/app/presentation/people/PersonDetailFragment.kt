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
                    viewModel.netBalanceMinor.collect { balance ->
                        binding.personBalance.text = formatMinorAsCurrency(kotlin.math.abs(balance))
                        binding.personBalanceLabel.text = when {
                            balance > 0 -> getString(R.string.label_you_will_receive)
                            balance < 0 -> getString(R.string.label_you_will_pay)
                            else -> getString(R.string.person_settled)
                        }
                        binding.receiveRepaymentButton.visibility = if (balance > 0) View.VISIBLE else View.GONE
                        binding.makeRepaymentButton.visibility = if (balance < 0) View.VISIBLE else View.GONE
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
