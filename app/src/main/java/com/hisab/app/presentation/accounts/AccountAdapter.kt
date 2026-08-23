package com.hisab.app.presentation.accounts

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.R
import com.hisab.app.databinding.ItemAccountBinding
import com.hisab.app.domain.model.AccountWithBalance
import com.hisab.app.utils.formatMinorAsCurrency

class AccountAdapter(
    private val onEdit: (AccountWithBalance) -> Unit,
    private val onDeactivate: (AccountWithBalance) -> Unit
) : ListAdapter<AccountWithBalance, AccountAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), onEdit, onDeactivate)

    class ViewHolder(private val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: AccountWithBalance,
            onEdit: (AccountWithBalance) -> Unit,
            onDeactivate: (AccountWithBalance) -> Unit
        ) {
            val context = binding.root.context
            binding.accountName.text = item.account.name
            binding.accountType.text = item.account.type.lowercase().replaceFirstChar { it.uppercase() }
            binding.accountBalance.text = formatMinorAsCurrency(item.balanceMinor)
            binding.accountBalance.setTextColor(
                context.getColor(if (item.balanceMinor < 0) R.color.color_negative else R.color.color_on_surface)
            )

            binding.accountMenuButton.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    menuInflater.inflate(R.menu.account_item_menu, menu)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.menu_edit_account -> { onEdit(item); true }
                            R.id.menu_delete_account -> { onDeactivate(item); true }
                            else -> false
                        }
                    }
                }.show()
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AccountWithBalance>() {
            override fun areItemsTheSame(oldItem: AccountWithBalance, newItem: AccountWithBalance) =
                oldItem.account.id == newItem.account.id
            override fun areContentsTheSame(oldItem: AccountWithBalance, newItem: AccountWithBalance) =
                oldItem == newItem
        }
    }
}
