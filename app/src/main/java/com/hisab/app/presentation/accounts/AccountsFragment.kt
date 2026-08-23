package com.hisab.app.presentation.accounts

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
import com.hisab.app.databinding.FragmentAccountsBinding
import com.hisab.app.di.ViewModelFactory
import kotlinx.coroutines.launch

class AccountsFragment : Fragment() {

    private var _binding: FragmentAccountsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccountsViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    private val adapter = AccountAdapter(
        onEdit = { item ->
            val args = Bundle().apply { putString("accountId", item.account.id) }
            findNavController().navigate(R.id.action_accounts_to_createAccount, args)
        },
        onDeactivate = { item ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deactivateAccount(item.account.id) }
                .show()
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.accountList.layoutManager = LinearLayoutManager(requireContext())
        binding.accountList.adapter = adapter

        binding.fabCreateAccount.setOnClickListener {
            findNavController().navigate(R.id.action_accounts_to_createAccount)
        }
        binding.transferButton.setOnClickListener {
            findNavController().navigate(R.id.action_accounts_to_transfer)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.accounts.collect { list ->
                    adapter.submitList(list)
                    binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
