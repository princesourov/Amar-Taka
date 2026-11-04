package com.hisab.app.presentation.trash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.hisab.app.HisabApplication
import com.hisab.app.R
import com.hisab.app.databinding.FragmentTrashBinding
import com.hisab.app.di.ViewModelFactory
import kotlinx.coroutines.launch

class TrashFragment : Fragment() {

    private var _binding: FragmentTrashBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrashViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    private val adapter = TrashAdapter(
        onRestore = { viewModel.restore(it) },
        onPurge = { entry ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.trash_confirm_purge_title)
                .setMessage(R.string.trash_confirm_purge_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.trash_delete_forever) { _, _ -> viewModel.purge(entry) }
                .show()
        }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.trashList.layoutManager = LinearLayoutManager(requireContext())
        binding.trashList.adapter = adapter

        val tabs = TrashTab.values()
        val labels = mapOf(
            TrashTab.TRANSACTIONS to R.string.trash_tab_transactions, TrashTab.BUDGETS to R.string.trash_tab_budgets,
            TrashTab.GOALS to R.string.trash_tab_goals, TrashTab.CATEGORIES to R.string.trash_tab_categories,
            TrashTab.ACCOUNTS to R.string.trash_tab_accounts, TrashTab.PEOPLE to R.string.trash_tab_people
        )
        tabs.forEach { binding.trashTabs.addTab(binding.trashTabs.newTab().setText(labels.getValue(it))) }
        binding.trashTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { viewModel.selectTab(tabs[tab.position]) }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        binding.emptyTrashButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.trash_confirm_empty_title)
                .setMessage(R.string.trash_confirm_empty_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.trash_empty_action) { _, _ -> viewModel.emptyTrash() }
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { s ->
                        adapter.submitList(s.entries)
                        binding.emptyState.visibility = if (s.entries.isEmpty()) View.VISIBLE else View.GONE
                        binding.emptyState.setText(R.string.trash_empty_tab_state)
                        binding.emptyTrashButton.isEnabled = !s.isEmptyOverall
                        tabs.forEachIndexed { i, t ->
                            val n = s.counts[t] ?: 0
                            binding.trashTabs.getTabAt(i)?.text = getString(labels.getValue(t)) + if (n > 0) " ($n)" else ""
                        }
                        if (binding.trashTabs.selectedTabPosition != s.tab.ordinal) binding.trashTabs.getTabAt(s.tab.ordinal)?.select()
                    }
                }
                launch { viewModel.messages.collect { Snackbar.make(binding.root, getString(it.res, *it.args.toTypedArray()), Snackbar.LENGTH_SHORT).show() } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.trashList.adapter = null
        _binding = null
    }
}
