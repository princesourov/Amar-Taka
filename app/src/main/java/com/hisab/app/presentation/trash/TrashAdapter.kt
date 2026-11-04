package com.hisab.app.presentation.trash

import android.content.Context
import android.view.LayoutInflater
import android.view.View
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

        private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
        private fun date(ctx: Context, millis: Long?): String =
            millis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(fmt) }
                ?: ctx.getString(R.string.trash_date_unknown)

        private fun txnLabel(ctx: Context, type: String): String = when (runCatching { TransactionType.valueOf(type) }.getOrNull()) {
            TransactionType.EXPENSE -> ctx.getString(R.string.type_expense)
            TransactionType.INCOME -> ctx.getString(R.string.type_income)
            TransactionType.TRANSFER -> ctx.getString(R.string.type_transfer)
            TransactionType.LENDING -> ctx.getString(R.string.txn_gave)
            TransactionType.BORROWING -> ctx.getString(R.string.txn_took)
            TransactionType.REPAYMENT_RECEIVED -> ctx.getString(R.string.txn_repaid_by_them)
            TransactionType.REPAYMENT_MADE -> ctx.getString(R.string.txn_repaid_by_you)
            else -> type
        }

        fun bind(e: TrashEntry, onRestore: (TrashEntry) -> Unit, onPurge: (TrashEntry) -> Unit) {
            val ctx = b.root.context
            var original: String? = null
            var amount: String? = null
            when (e) {
                is TrashEntry.Txn -> {
                    val t = e.transaction
                    b.trashTitle.text = e.categoryName ?: e.personName ?: txnLabel(ctx, t.type)
                    b.trashMeta.text = ctx.getString(
                        R.string.trash_type_line, ctx.getString(R.string.trash_type_transaction),
                        listOfNotNull(txnLabel(ctx, t.type), e.accountName).joinToString(" · ")
                    )
                    original = ctx.getString(R.string.trash_original_date, date(ctx, t.transactionDateMillis))
                    amount = formatMinorAsCurrency(t.amountMinor)
                }
                is TrashEntry.Budget -> {
                    val bd = e.budget
                    b.trashTitle.text = bd.name?.takeIf { it.isNotBlank() } ?: e.categoryName ?: ctx.getString(R.string.budget_overall_option)
                    b.trashMeta.text = ctx.getString(R.string.trash_type_line, ctx.getString(R.string.trash_type_budget), bd.scope)
                    amount = formatMinorAsCurrency(bd.amountMinor)
                }
                is TrashEntry.Goal -> {
                    b.trashTitle.text = e.goal.name
                    b.trashMeta.text = ctx.getString(R.string.trash_type_goal)
                    original = ctx.getString(R.string.trash_original_date, date(ctx, e.goal.createdAtMillis))
                    amount = formatMinorAsCurrency(e.goal.targetAmountMinor)
                }
                is TrashEntry.Category -> {
                    b.trashTitle.text = e.category.name
                    b.trashMeta.text = ctx.getString(
                        R.string.trash_category_kind_line,
                        when (e.category.kind) {
                            "EXPENSE" -> ctx.getString(R.string.type_expense)
                            "INCOME" -> ctx.getString(R.string.type_income)
                            else -> ctx.getString(R.string.category_kind_both)
                        }
                    )
                }
                is TrashEntry.Account -> {
                    b.trashTitle.text = e.account.name
                    b.trashMeta.text = ctx.getString(R.string.trash_type_line, ctx.getString(R.string.trash_type_account), e.account.type)
                    original = ctx.getString(R.string.trash_original_date, date(ctx, e.account.createdAtMillis))
                }
                is TrashEntry.Person -> {
                    b.trashTitle.text = e.person.name
                    b.trashMeta.text = ctx.getString(R.string.trash_type_person)
                    original = ctx.getString(R.string.trash_original_date, date(ctx, e.person.createdAtMillis))
                }
            }
            val deleted = ctx.getString(R.string.trash_deleted_date, date(ctx, e.deletedAtMillis))
            b.trashSubtitle.text = listOfNotNull(original, deleted).joinToString(" · ")
            b.trashAmount.text = amount.orEmpty()
            b.trashAmount.visibility = if (amount == null) View.GONE else View.VISIBLE
            b.trashRestore.setOnClickListener { onRestore(e) }
            b.trashPurge.setOnClickListener { onPurge(e) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<TrashEntry>() {
            override fun areItemsTheSame(a: TrashEntry, b: TrashEntry) = a.javaClass == b.javaClass && a.id == b.id
            override fun areContentsTheSame(a: TrashEntry, b: TrashEntry) = a == b
        }
    }
}
