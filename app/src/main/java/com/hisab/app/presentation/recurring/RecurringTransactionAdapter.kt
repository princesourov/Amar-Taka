package com.hisab.app.presentation.recurring

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.R
import com.hisab.app.data.local.entity.RecurringTransactionEntity
import com.hisab.app.databinding.ItemRecurringTransactionBinding
import com.hisab.app.domain.model.RecurrenceFrequency
import com.hisab.app.utils.formatMinorAsCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RecurringTransactionAdapter(
    private val onToggle: (RecurringTransactionEntity) -> Unit,
    private val onDelete: (RecurringTransactionEntity) -> Unit,
    private val onEdit: (RecurringTransactionEntity) -> Unit,
    private val onHistory: (RecurringTransactionEntity) -> Unit
) : ListAdapter<RecurringTransactionEntity, RecurringTransactionAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecurringTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onToggle, onDelete, onEdit, onHistory)
    }

    /** Schedules the last run could not process, with a ready-made explanation. */
    var attention: Map<String, String> = emptyMap()
        set(value) { field = value; notifyDataSetChanged() }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), attention[getItem(position).id])

    class ViewHolder(
        private val binding: ItemRecurringTransactionBinding,
        private val onToggle: (RecurringTransactionEntity) -> Unit,
        private val onDelete: (RecurringTransactionEntity) -> Unit,
        private val onEdit: (RecurringTransactionEntity) -> Unit,
        private val onHistory: (RecurringTransactionEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecurringTransactionEntity, attentionText: String?) {
            val context = binding.root.context
            val frequencyLabel = frequencyLabelFor(context, item.frequency)
            binding.recurringTitle.text = item.note?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.recurring_untitled)
            binding.recurringAmount.text = formatMinorAsCurrency(item.amountMinor)
            binding.recurringFrequency.text = frequencyLabel
            binding.recurringNextRun.text = if (item.isActive) context.getString(
                R.string.recurring_next_run,
                LocalDate.ofEpochDay(item.nextRunEpochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
            ) else context.getString(R.string.recurring_paused)
            binding.root.alpha = if (item.isActive) 1f else 0.6f
            binding.recurringToggle.setText(if (item.isActive) R.string.budget_action_pause else R.string.budget_action_enable)
            binding.recurringToggle.setOnClickListener { onToggle(item) }
            binding.recurringDelete.setOnClickListener { onDelete(item) }
            binding.recurringHistory.setOnClickListener { onHistory(item) }
            binding.root.setOnClickListener { onEdit(item) }
            binding.recurringAttention.text = attentionText
            binding.recurringAttention.visibility = if (attentionText == null) android.view.View.GONE else android.view.View.VISIBLE
        }

        private fun frequencyLabelFor(context: android.content.Context, frequency: String): String =
            when (RecurrenceFrequency.valueOf(frequency)) {
                RecurrenceFrequency.DAILY -> context.getString(R.string.scope_daily)
                RecurrenceFrequency.WEEKLY -> context.getString(R.string.scope_weekly)
                RecurrenceFrequency.MONTHLY -> context.getString(R.string.scope_monthly)
                RecurrenceFrequency.YEARLY -> context.getString(R.string.scope_yearly)
            }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RecurringTransactionEntity>() {
            override fun areItemsTheSame(oldItem: RecurringTransactionEntity, newItem: RecurringTransactionEntity) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: RecurringTransactionEntity, newItem: RecurringTransactionEntity) =
                oldItem == newItem
        }
    }
}
