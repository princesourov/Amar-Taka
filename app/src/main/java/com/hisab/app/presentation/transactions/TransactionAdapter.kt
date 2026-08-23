package com.hisab.app.presentation.transactions

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.R
import com.hisab.app.databinding.ItemTransactionBinding
import com.hisab.app.domain.model.TransactionDisplayItem
import com.hisab.app.domain.model.TransactionType
import com.hisab.app.utils.formatMinorAsCurrency
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TransactionAdapter(
    private val onEdit: (TransactionDisplayItem) -> Unit,
    private val onDelete: (TransactionDisplayItem) -> Unit
) : ListAdapter<TransactionDisplayItem, TransactionAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), onEdit, onDelete)

    class ViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: TransactionDisplayItem,
            onEdit: (TransactionDisplayItem) -> Unit,
            onDelete: (TransactionDisplayItem) -> Unit
        ) {
            val context = binding.root.context
            val typeLabel = typeLabelFor(context, item.type)

            binding.txnTitle.text = when (item.type) {
                TransactionType.EXPENSE, TransactionType.INCOME ->
                    item.categoryName?.let { "$it · $typeLabel" } ?: typeLabel
                TransactionType.LENDING, TransactionType.BORROWING,
                TransactionType.REPAYMENT_RECEIVED, TransactionType.REPAYMENT_MADE ->
                    item.personName?.let { "$typeLabel · $it" } ?: typeLabel
                else -> typeLabel
            }

            val accountText = when (item.type) {
                TransactionType.TRANSFER ->
                    "${item.sourceAccountName.orEmpty()} → ${item.destinationAccountName.orEmpty()}"
                TransactionType.EXPENSE, TransactionType.LENDING, TransactionType.REPAYMENT_MADE ->
                    item.sourceAccountName.orEmpty()
                else -> item.destinationAccountName.orEmpty()
            }
            val dateText = Instant.ofEpochMilli(item.transaction.transactionDateMillis)
                .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
            binding.txnSubtitle.text = "$accountText · $dateText"

            binding.txnNote.text = item.transaction.note.orEmpty()
            binding.txnNote.visibility = if (item.transaction.note.isNullOrBlank()) View.GONE else View.VISIBLE

            val isOutflow = item.type == TransactionType.EXPENSE || item.type == TransactionType.LENDING ||
                item.type == TransactionType.REPAYMENT_MADE
            val amountText = formatMinorAsCurrency(item.transaction.amountMinor)
            binding.txnAmount.text = if (isOutflow) "-$amountText" else "+$amountText"
            binding.txnAmount.setTextColor(
                context.getColor(if (isOutflow) R.color.color_negative else R.color.color_positive)
            )

            binding.txnMenuButton.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    menuInflater.inflate(R.menu.transaction_item_menu, menu)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.menu_edit_transaction -> { onEdit(item); true }
                            R.id.menu_delete_transaction -> { onDelete(item); true }
                            else -> false
                        }
                    }
                }.show()
            }
        }

        private fun typeLabelFor(context: Context, type: TransactionType): String = when (type) {
            TransactionType.EXPENSE -> context.getString(R.string.type_expense)
            TransactionType.INCOME -> context.getString(R.string.type_income)
            TransactionType.TRANSFER -> context.getString(R.string.type_transfer)
            TransactionType.LENDING -> context.getString(R.string.txn_gave)
            TransactionType.BORROWING -> context.getString(R.string.txn_took)
            TransactionType.REPAYMENT_RECEIVED -> context.getString(R.string.txn_repaid_by_them)
            TransactionType.REPAYMENT_MADE -> context.getString(R.string.txn_repaid_by_you)
            else -> type.name
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TransactionDisplayItem>() {
            override fun areItemsTheSame(oldItem: TransactionDisplayItem, newItem: TransactionDisplayItem) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: TransactionDisplayItem, newItem: TransactionDisplayItem) =
                oldItem == newItem
        }
    }
}
