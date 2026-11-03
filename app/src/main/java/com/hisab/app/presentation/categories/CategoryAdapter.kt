package com.hisab.app.presentation.categories

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.R
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.databinding.ItemCategoryBinding
import com.hisab.app.domain.category.CategoryKind

class CategoryAdapter(
    private val onEdit: (CategoryEntity) -> Unit,
    private val onToggle: (CategoryEntity) -> Unit
) : ListAdapter<CategoryEntity, CategoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position), onEdit, onToggle)

    class VH(private val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: CategoryEntity, onEdit: (CategoryEntity) -> Unit, onToggle: (CategoryEntity) -> Unit) {
            val ctx = b.root.context
            val color = runCatching { Color.parseColor(c.colorHex) }.getOrDefault(Color.GRAY)
            b.categoryDot.background.mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN)
            b.categoryName.text = c.name
            val kind = when (runCatching { CategoryKind.valueOf(c.kind) }.getOrDefault(CategoryKind.BOTH)) {
                CategoryKind.EXPENSE -> ctx.getString(R.string.type_expense)
                CategoryKind.INCOME -> ctx.getString(R.string.type_income)
                CategoryKind.BOTH -> ctx.getString(R.string.category_kind_both)
            }
            b.categoryMeta.text = ctx.getString(
                R.string.category_meta, kind,
                ctx.getString(if (c.isCustom) R.string.category_custom else R.string.category_default)
            )
            b.categoryToggle.setText(if (c.isActive) R.string.category_hide else R.string.trash_restore)
            b.categoryEdit.setOnClickListener { onEdit(c) }
            b.categoryToggle.setOnClickListener { onToggle(c) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<CategoryEntity>() {
            override fun areItemsTheSame(a: CategoryEntity, b: CategoryEntity) = a.id == b.id
            override fun areContentsTheSame(a: CategoryEntity, b: CategoryEntity) = a == b
        }
    }
}
