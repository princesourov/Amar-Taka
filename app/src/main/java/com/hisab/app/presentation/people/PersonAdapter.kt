package com.hisab.app.presentation.people

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.R
import com.hisab.app.databinding.ItemPersonBinding
import com.hisab.app.domain.model.PersonStatus
import com.hisab.app.domain.model.PersonWithBalance
import com.hisab.app.utils.formatMinorAsCurrency

class PersonAdapter(
    private val onClick: (PersonWithBalance) -> Unit,
    private val onEdit: (PersonWithBalance) -> Unit,
    private val onDeactivate: (PersonWithBalance) -> Unit
) : ListAdapter<PersonWithBalance, PersonAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPersonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), onClick, onEdit, onDeactivate)

    class ViewHolder(private val binding: ItemPersonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: PersonWithBalance,
            onClick: (PersonWithBalance) -> Unit,
            onEdit: (PersonWithBalance) -> Unit,
            onDeactivate: (PersonWithBalance) -> Unit
        ) {
            val context = binding.root.context
            binding.personName.text = item.person.name
            binding.personPhone.text = item.person.phone.orEmpty()
            binding.personPhone.visibility = if (item.person.phone.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.personStatus.text = when (item.status) {
                PersonStatus.YOU_WILL_RECEIVE -> context.getString(R.string.person_will_receive, formatMinorAsCurrency(item.netBalanceMinor))
                PersonStatus.YOU_WILL_PAY -> context.getString(R.string.person_will_pay, formatMinorAsCurrency(-item.netBalanceMinor))
                PersonStatus.SETTLED -> context.getString(R.string.person_settled)
            }
            binding.personStatus.setTextColor(
                context.getColor(
                    when (item.status) {
                        PersonStatus.YOU_WILL_RECEIVE -> R.color.color_positive
                        PersonStatus.YOU_WILL_PAY -> R.color.color_negative
                        PersonStatus.SETTLED -> R.color.color_on_surface
                    }
                )
            )

            binding.root.setOnClickListener { onClick(item) }
            binding.personMenuButton.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    menuInflater.inflate(R.menu.person_item_menu, menu)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.menu_edit_person -> { onEdit(item); true }
                            R.id.menu_delete_person -> { onDeactivate(item); true }
                            else -> false
                        }
                    }
                }.show()
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PersonWithBalance>() {
            override fun areItemsTheSame(oldItem: PersonWithBalance, newItem: PersonWithBalance) =
                oldItem.person.id == newItem.person.id
            override fun areContentsTheSame(oldItem: PersonWithBalance, newItem: PersonWithBalance) =
                oldItem == newItem
        }
    }
}
