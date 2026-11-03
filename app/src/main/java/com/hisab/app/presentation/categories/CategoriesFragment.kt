package com.hisab.app.presentation.categories

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
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.databinding.DialogCategoryEditorBinding
import com.hisab.app.databinding.FragmentCategoriesBinding
import com.hisab.app.di.ViewModelFactory
import com.hisab.app.domain.category.CategoryKind
import kotlinx.coroutines.launch

class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!
    private var editorDialog: androidx.appcompat.app.AlertDialog? = null
    private var editorBinding: DialogCategoryEditorBinding? = null

    private val viewModel: CategoriesViewModel by viewModels {
        ViewModelFactory((requireActivity().application as HisabApplication).container)
    }

    private val adapter = CategoryAdapter(
        onEdit = { showEditor(it) },
        onToggle = { c -> if (c.isActive) confirmHide(c) else viewModel.restore(c) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.categoryList.layoutManager = LinearLayoutManager(requireContext())
        binding.categoryList.adapter = adapter
        binding.fabAddCategory.setOnClickListener { showEditor(null) }
        binding.categoryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(CategoryTab.entries[tab.position])
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { s ->
                        adapter.submitList(s.items)
                        binding.emptyState.visibility = if (s.items.isEmpty()) View.VISIBLE else View.GONE
                        val want = s.tab.ordinal
                        if (binding.categoryTabs.selectedTabPosition != want) binding.categoryTabs.getTabAt(want)?.select()
                    }
                }
                launch { viewModel.events.collect(::onEvent) }
            }
        }
    }

    private fun onEvent(e: CategoryEvent) {
        when (e) {
            CategoryEvent.Saved -> editorDialog?.dismiss()
            is CategoryEvent.Message -> Snackbar.make(binding.root, e.res, Snackbar.LENGTH_SHORT).show()
            is CategoryEvent.NameError -> editorBinding?.categoryNameLayout?.error = getString(e.res)
        }
    }

    private fun showEditor(existing: CategoryEntity?) {
        val eb = DialogCategoryEditorBinding.inflate(layoutInflater)
        editorBinding = eb
        eb.categoryNameInput.setText(existing?.name.orEmpty())
        eb.categoryKindGroup.check(
            when (existing?.kind?.let { runCatching { CategoryKind.valueOf(it) }.getOrNull() } ?: CategoryKind.EXPENSE) {
                CategoryKind.EXPENSE -> R.id.kindExpense
                CategoryKind.INCOME -> R.id.kindIncome
                CategoryKind.BOTH -> R.id.kindBoth
            }
        )
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.categories_add else R.string.categories_edit)
            .setView(eb.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null) // set below so errors keep the dialog open
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                eb.categoryNameLayout.error = null
                val kind = when (eb.categoryKindGroup.checkedButtonId) {
                    R.id.kindIncome -> CategoryKind.INCOME
                    R.id.kindBoth -> CategoryKind.BOTH
                    else -> CategoryKind.EXPENSE
                }
                viewModel.save(existing, eb.categoryNameInput.text.toString(), kind)
            }
        }
        dialog.setOnDismissListener { editorDialog = null; editorBinding = null }
        editorDialog = dialog
        dialog.show()
    }

    private fun confirmHide(c: CategoryEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val used = viewModel.usageCount(c)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.category_hide_title, c.name))
                .setMessage(resources.getQuantityString(R.plurals.category_hide_message, used, used))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.category_hide) { _, _ -> viewModel.hide(c) }
                .show()
        }
    }

    override fun onDestroyView() {
        editorDialog?.dismiss()
        super.onDestroyView()
        binding.categoryList.adapter = null
        _binding = null
    }
}
