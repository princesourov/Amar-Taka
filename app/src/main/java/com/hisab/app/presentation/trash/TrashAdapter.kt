package com.hisab.app.presentation.trash

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.R
import com.hisab.app.databinding.ItemTrashBinding
import com.hisab.app.domain.model.TransactionType
import com.hisab.app.utils.formatMinorAsCurrency
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TrashAdapter(
    private val onRestore: (TrashEntry) -> Unit,
    private val onPurge: (TrashEntry) -> Unit
) : ListAdapter<TrashEntry, TrashAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTrashBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position), onRestore, onPurge)

    class VH(private val b: ItemTrashBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: TrashEntry, onRestore: (TrashEntry) -> Unit, onPurge: (TrashEntry) -> Unit) {
            val ctx = b.root.context
            val deleted = ctx.getString(
                R.string.trash_deleted_on,
                Instant.ofEpochMilli(e.deletedAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
            )
            when (e) {
                is TrashEntry.Txn -> {
                    val t = e.transaction
                    val type = runCatching { TransactionType.valueOf(t.type) }.getOrNull()
                    val typeLabel = when (type) {
                        TransactionType.EXPENSE -> ctx.getString(R.string.type_expense)
                        TransactionType.INCOME -> ctx.getString(R.string.type_income)
                        TransactionType.TRANSFER -> ctx.getString(R.string.type_transfer)
                        TransactionType.LENDING -> ctx.getString(R.string.txn_gave)
                        TransactionType.BORROWING -> ctx.getString(R.string.txn_took)
                        TransactionType.REPAYMENT_RECEIVED -> ctx.getString(R.string.txn_repaid_by_them)
                        TransactionType.REPAYMENT_MADE -> ctx.getString(R.string.txn_repaid_by_you)
                        else -> t.type
                    }
                    b.trashTitle.text = e.categoryName?.let { "$it · $typeLabel" } ?: typeLabel
                    b.trashSubtitle.text = listOfNotNull(e.accountName, deleted).joinToString(" · ")
                    b.trashAmount.text = formatMinorAsCurrency(t.amountMinor)
                }
                is TrashEntry.Budget -> {
                    val bd = e.budget
                    b.trashTitle.text = bd.name?.takeIf { it.isNotBlank() } ?: e.categoryName ?: ctx.getString(R.string.budget_overall_option)
                    b.trashSubtitle.text = ctx.getString(R.string.trash_budget_subtitle, deleted)
                    b.trashAmount.text = formatMinorAsCurrency(bd.amountMinor)
                }
            }
            b.trashRestore.setOnClickListener { onRestore(e) }
            b.trashPurge.setOnClickListener { onPurge(e) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<TrashEntry>() {
            override fun areItemsTheSame(a: TrashEntry, b: TrashEntry) = a.id == b.id
            override fun areContentsTheSame(a: TrashEntry, b: TrashEntry) = a == b
        }
    }
}
