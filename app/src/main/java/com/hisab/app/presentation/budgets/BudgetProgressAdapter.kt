package com.hisab.app.presentation.budgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.R
import com.hisab.app.databinding.ItemBudgetProgressBinding
import com.hisab.app.domain.model.BudgetProgressItem
import com.hisab.app.utils.formatMinorAsCurrency

class BudgetProgressAdapter(
    private val onMore: (BudgetProgressItem, View) -> Unit
) : ListAdapter<BudgetProgressItem, BudgetProgressAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBudgetProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onMore)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(
        private val binding: ItemBudgetProgressBinding,
        private val onMore: (BudgetProgressItem, View) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BudgetProgressItem) {
            val context = binding.root.context
            binding.budgetLabel.text = item.categoryName ?: context.getString(R.string.budget_overall_option)
            binding.budgetPeriod.text = item.periodLabel
            binding.budgetProgress.progress = (item.percentUsed * 100).toInt().coerceIn(0, 100)
            binding.budgetSpent.text = context.getString(
                R.string.budget_spent_of,
                formatMinorAsCurrency(item.spentMinor),
                formatMinorAsCurrency(item.budget.amountMinor)
            )
            if (item.isOverBudget) {
                binding.budgetStatus.text = context.getString(R.string.budget_over_by, formatMinorAsCurrency(-item.remainingMinor))
                binding.budgetStatus.setTextColor(context.getColor(R.color.color_negative))
            } else {
                binding.budgetStatus.text = context.getString(R.string.budget_remaining, formatMinorAsCurrency(item.remainingMinor))
                binding.budgetStatus.setTextColor(context.getColor(R.color.color_positive))
            }
            binding.root.alpha = if (item.isActive) 1f else 0.55f
            binding.budgetMore.setOnClickListener { onMore(item, it) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BudgetProgressItem>() {
            override fun areItemsTheSame(oldItem: BudgetProgressItem, newItem: BudgetProgressItem) =
                oldItem.budget.id == newItem.budget.id

            override fun areContentsTheSame(oldItem: BudgetProgressItem, newItem: BudgetProgressItem) =
                oldItem == newItem
        }
    }
}
