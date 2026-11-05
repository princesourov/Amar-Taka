package com.hisab.app.presentation.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.R
import com.hisab.app.databinding.ItemDailySummaryBinding
import com.hisab.app.domain.dashboard.DailySummary
import com.hisab.app.utils.formatMinorAsCurrency
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One card per day: date, income, expense, net cash flow, savings rate. Nothing else. */
class DailySummaryAdapter : ListAdapter<DailySummary, DailySummaryAdapter.ViewHolder>(Diff) {

    class ViewHolder(private val binding: ItemDailySummaryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DailySummary) {
            val context = binding.root.context
            binding.dayDate.text = item.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))
            binding.dayIncomeValue.text = formatMinorAsCurrency(item.incomeMinor)
            binding.dayExpenseValue.text = formatMinorAsCurrency(item.expenseMinor)
            binding.dayNetValue.text = formatMinorAsCurrency(item.netCashFlowMinor)
            // Same colouring rule as the Dashboard's net cash flow figure.
            binding.dayNetValue.setTextColor(
                ContextCompat.getColor(context, if (item.netCashFlowMinor < 0) R.color.color_negative else R.color.color_positive)
            )
            binding.dayRateValue.text = item.savingsRatePercent
                ?.let { context.getString(R.string.dash_savings_rate_value, it) }
                ?: context.getString(R.string.dash_savings_rate_none)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemDailySummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    private object Diff : DiffUtil.ItemCallback<DailySummary>() {
        override fun areItemsTheSame(oldItem: DailySummary, newItem: DailySummary) = oldItem.date == newItem.date
        override fun areContentsTheSame(oldItem: DailySummary, newItem: DailySummary) = oldItem == newItem
    }
}
