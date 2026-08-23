package com.hisab.app.presentation.savings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.R
import com.hisab.app.data.local.entity.SavingsGoalEntity
import com.hisab.app.databinding.ItemSavingsGoalBinding
import com.hisab.app.utils.formatMinorAsCurrency

class SavingsGoalAdapter(
    private val onContributeClick: (SavingsGoalEntity) -> Unit
) : ListAdapter<SavingsGoalEntity, SavingsGoalAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavingsGoalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), onContributeClick)

    class ViewHolder(private val binding: ItemSavingsGoalBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(goal: SavingsGoalEntity, onContributeClick: (SavingsGoalEntity) -> Unit) {
            binding.goalName.text = goal.name
            val percent = if (goal.targetAmountMinor <= 0) 0 else
                ((goal.savedAmountMinor.toFloat() / goal.targetAmountMinor.toFloat()) * 100).toInt().coerceIn(0, 100)
            binding.goalProgress.progress = percent
            binding.goalAmounts.text = binding.root.context.getString(
                R.string.goal_saved_of,
                formatMinorAsCurrency(goal.savedAmountMinor),
                formatMinorAsCurrency(goal.targetAmountMinor)
            )
            binding.contributeButton.setOnClickListener { onContributeClick(goal) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SavingsGoalEntity>() {
            override fun areItemsTheSame(oldItem: SavingsGoalEntity, newItem: SavingsGoalEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: SavingsGoalEntity, newItem: SavingsGoalEntity) = oldItem == newItem
        }
    }
}
