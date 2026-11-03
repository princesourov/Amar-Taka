package com.hisab.app.presentation.categories

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.R
import com.hisab.app.data.local.dao.CategoryDao
import com.hisab.app.data.local.entity.CategoryEntity
import com.hisab.app.domain.category.CategoryKind
import com.hisab.app.domain.category.CategoryRules
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class CategoryTab { EXPENSE, INCOME, HIDDEN }

data class CategoriesUiState(val tab: CategoryTab = CategoryTab.EXPENSE, val items: List<CategoryEntity> = emptyList())

sealed interface CategoryEvent {
    data object Saved : CategoryEvent
    data class Message(@StringRes val res: Int) : CategoryEvent
    data class NameError(@StringRes val res: Int) : CategoryEvent
}

class CategoriesViewModel(
    private val userId: String,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val tab = MutableStateFlow(CategoryTab.EXPENSE)
    private val all = categoryDao.getAllCategories(userId)

    val uiState: StateFlow<CategoriesUiState> = combine(tab, all) { t, list ->
        val shown = when (t) {
            CategoryTab.EXPENSE -> list.filter { it.isActive && it.kind != CategoryKind.INCOME.name }
            CategoryTab.INCOME -> list.filter { it.isActive && it.kind != CategoryKind.EXPENSE.name }
            CategoryTab.HIDDEN -> list.filter { !it.isActive }
        }
        CategoriesUiState(t, shown.sortedBy { it.name.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoriesUiState())

    private val _events = MutableSharedFlow<CategoryEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun selectTab(t: CategoryTab) { tab.value = t }

    /** Creates a category ([editing] == null) or renames/re-kinds an existing one. History is never touched. */
    fun save(editing: CategoryEntity?, rawName: String, kind: CategoryKind) {
        viewModelScope.launch {
            val existing = categoryDao.getAllCategoriesOnce(userId)
            val others = existing.filter { it.id != editing?.id }.map { it.id to it.name }
            when (val v = CategoryRules.validate(rawName, others)) {
                CategoryRules.Validation.Empty -> _events.emit(CategoryEvent.NameError(R.string.error_category_name_required))
                CategoryRules.Validation.TooLong -> _events.emit(CategoryEvent.NameError(R.string.error_category_name_long))
                is CategoryRules.Validation.Duplicate -> {
                    val dup = existing.first { it.id == v.existingId }
                    _events.emit(CategoryEvent.NameError(if (dup.isActive) R.string.error_category_duplicate else R.string.error_category_duplicate_hidden))
                }
                is CategoryRules.Validation.Valid -> {
                    if (editing == null) {
                        categoryDao.insert(
                            CategoryEntity(
                                id = UUID.randomUUID().toString(), userId = userId, name = v.name,
                                iconKey = "custom", colorHex = CategoryRules.nextColor(existing.count { it.isCustom }),
                                isCustom = true, isActive = true, kind = kind.name
                            )
                        )
                    } else {
                        categoryDao.update(editing.copy(name = v.name, kind = kind.name))
                    }
                    _events.emit(CategoryEvent.Saved)
                }
            }
        }
    }

    /** "Delete" hides the category: pickers stop offering it, but old transactions, reports and budgets keep resolving it. */
    fun hide(c: CategoryEntity) {
        viewModelScope.launch {
            categoryDao.setActive(c.id, false)
            _events.emit(CategoryEvent.Message(R.string.category_hidden))
        }
    }

    fun restore(c: CategoryEntity) {
        viewModelScope.launch {
            categoryDao.setActive(c.id, true)
            _events.emit(CategoryEvent.Message(R.string.category_restored))
        }
    }

    suspend fun usageCount(c: CategoryEntity): Int = categoryDao.countTransactionsUsing(c.id)
}
