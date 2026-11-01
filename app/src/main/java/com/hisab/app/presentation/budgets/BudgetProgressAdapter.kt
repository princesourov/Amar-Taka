package com.hisab.app.presentation.budgets

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.R
import com.hisab.app.databinding.ItemBudgetProgressBinding
import com.hisab.app.domain.budget.BudgetState
import com.hisab.app.domain.model.BudgetProgressItem
import com.hisab.app.domain.model.BudgetScope
import com.hisab.app.utils.formatMinorAsCurrency

/** One card per budget. Every action is a visible button, so nothing is hidden behind a long-press. */
class BudgetProgressAdapter(
    private val onEdit: (BudgetProgressItem) -> Unit,
    private val onToggle: (BudgetProgressItem) -> Unit,
    private val onDelete: (BudgetProgressItem) -> Unit
) : ListAdapter<BudgetProgressItem, BudgetProgressAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemBudgetProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), onEdit, onToggle, onDelete)

    class ViewHolder(private val b: ItemBudgetProgressBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(
            item: BudgetProgressItem,
            onEdit: (BudgetProgressItem) -> Unit,
            onToggle: (BudgetProgressItem) -> Unit,
            onDelete: (BudgetProgressItem) -> Unit
        ) {
            val ctx = b.root.context
            val e = item.evaluation
            val scopeLabel = ctx.getString(
                when (item.scope) {
                    BudgetScope.DAILY -> R.string.scope_daily
                    BudgetScope.WEEKLY -> R.string.scope_weekly
                    BudgetScope.MONTHLY -> R.string.scope_monthly
                }
            )
            val name = item.displayName ?: ctx.getString(R.string.budget_overall_option)
            b.budgetLabel.text = ctx.getString(R.string.budget_card_title, name, scopeLabel)
            b.budgetPeriod.text = item.periodLabel
            b.budgetSpent.text = ctx.getString(R.string.budget_spent_of, formatMinorAsCurrency(e.spentMinor), formatMinorAsCurrency(e.limitMinor))
            b.budgetProgress.progress = e.progressPercent

            val colorRes = when (e.state) {
                BudgetState.OVER -> R.color.color_negative
                BudgetState.NEAR_LIMIT, BudgetState.LIMIT_REACHED -> R.color.color_warning
                else -> R.color.color_positive
            }
            b.budgetProgress.setIndicatorColor(ContextCompat.getColor(ctx, if (item.isActive) colorRes else R.color.color_warning))
            b.budgetProgress.alpha = if (item.isActive) 1f else 0.4f

            b.budgetStatus.text = when (e.state) {
                BudgetState.OVER -> ctx.getString(R.string.budget_over_by, formatMinorAsCurrency(e.overspentMinor))
                BudgetState.INVALID -> ctx.getString(R.string.budget_status_invalid)
                else -> ctx.getString(R.string.budget_remaining, formatMinorAsCurrency(e.remainingMinor))
            }

            // The badge repeats the state in words so colour is never the only signal.
            val badge = when {
                !item.isActive -> R.string.budget_badge_paused
                e.state == BudgetState.OVER -> R.string.budget_badge_over
                e.state == BudgetState.NEAR_LIMIT || e.state == BudgetState.LIMIT_REACHED -> R.string.budget_badge_near
                else -> null
            }
            b.budgetBadge.visibility = if (badge == null) android.view.View.GONE else android.view.View.VISIBLE
            badge?.let { b.budgetBadge.setText(it) }

            b.budgetToggle.setText(if (item.isActive) R.string.budget_action_pause else R.string.budget_action_enable)
            b.budgetEdit.setOnClickListener { onEdit(item) }
            b.budgetToggle.setOnClickListener { onToggle(item) }
            b.budgetDelete.setOnClickListener { onDelete(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<BudgetProgressItem>() {
            override fun areItemsTheSame(a: BudgetProgressItem, b: BudgetProgressItem) = a.budget.id == b.budget.id
            override fun areContentsTheSame(a: BudgetProgressItem, b: BudgetProgressItem) = a == b
        }
    }
}
