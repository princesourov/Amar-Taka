package com.hisab.app.presentation.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.databinding.ItemAccountBalanceBinding
import com.hisab.app.utils.formatMinorAsCurrency

class AccountBalanceAdapter : ListAdapter<AccountBalanceItem, AccountBalanceAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAccountBalanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemAccountBalanceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AccountBalanceItem) {
            binding.accountName.text = item.name
            binding.accountBalance.text = formatMinorAsCurrency(item.balanceMinor)
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AccountBalanceItem>() {
            override fun areItemsTheSame(oldItem: AccountBalanceItem, newItem: AccountBalanceItem) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: AccountBalanceItem, newItem: AccountBalanceItem) =
                oldItem == newItem
        }
    }
}
