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

        binding.trashTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(if (tab.position == 0) TrashTab.TRANSACTIONS else TrashTab.BUDGETS)
            }
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
                        binding.emptyState.setText(R.string.trash_empty_state)
                        binding.emptyTrashButton.isEnabled = !s.isEmptyOverall
                        val want = if (s.tab == TrashTab.TRANSACTIONS) 0 else 1
                        if (binding.trashTabs.selectedTabPosition != want) binding.trashTabs.getTabAt(want)?.select()
                    }
                }
                launch { viewModel.messages.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show() } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.trashList.adapter = null
        _binding = null
    }
}
