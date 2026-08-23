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

class RecurringTransactionAdapter : ListAdapter<RecurringTransactionEntity, RecurringTransactionAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecurringTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemRecurringTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecurringTransactionEntity) {
            val context = binding.root.context
            val frequencyLabel = frequencyLabelFor(context, item.frequency)
            binding.recurringTitle.text = item.note?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.recurring_untitled)
            binding.recurringAmount.text = formatMinorAsCurrency(item.amountMinor)
            binding.recurringFrequency.text = frequencyLabel
            binding.recurringNextRun.text = context.getString(
                R.string.recurring_next_run,
                LocalDate.ofEpochDay(item.nextRunEpochDay).format(DateTimeFormatter.ofPattern("d MMM"))
            )
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
