package com.hisab.app.presentation.savings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.R
import com.hisab.app.data.local.entity.SavingsGoalEntity
import com.hisab.app.data.repository.GoalOverview
import com.hisab.app.data.repository.GoalResult
import com.hisab.app.data.repository.SavingsGoalRepository
import com.hisab.app.domain.budget.MoneyParser
import com.hisab.app.domain.goal.GoalCheck
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SavingsGoalEvent {
    data object Saved : SavingsGoalEvent
    data class Invalid(@StringRes val res: Int) : SavingsGoalEvent
    /** The chosen account has less unallocated money than requested. */
    data class NotEnoughAvailable(val availableMinor: Long) : SavingsGoalEvent
    /** Trying to withdraw more than the goal holds. */
    data class NotEnoughSaved(val savedMinor: Long) : SavingsGoalEvent
    data class Deleted(val goalId: String, val name: String) : SavingsGoalEvent
}

/** Icons offered for a goal. Stored as the emoji itself (iconKey) - no drawable per icon needed. */
val GOAL_ICONS = listOf("🎯", "📱", "🏠", "🚗", "✈️", "🎓", "💍", "🏥", "💻", "🎁", "🛡️", "💰")

fun goalIconOrDefault(key: String?): String = key?.takeIf { it in GOAL_ICONS } ?: "🎯"

internal fun GoalCheck.toEvent(): SavingsGoalEvent = when (this) {
    GoalCheck.Ok -> SavingsGoalEvent.Saved
    GoalCheck.NotPositive -> SavingsGoalEvent.Invalid(R.string.error_amount_required)
    is GoalCheck.ExceedsAvailable -> SavingsGoalEvent.NotEnoughAvailable(availableMinor)
    is GoalCheck.ExceedsAllocated -> SavingsGoalEvent.NotEnoughSaved(allocatedMinor)
}

/** The goals list + create / edit / delete. Money movement (allocate / withdraw) lives in [GoalDetailViewModel]. */
class SavingsGoalsViewModel(
    private val userId: String,
    private val repository: SavingsGoalRepository
) : ViewModel() {

    val overview: StateFlow<GoalOverview?> =
        repository.observeOverview(userId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _events = MutableSharedFlow<SavingsGoalEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    suspend fun loadGoal(goalId: String): SavingsGoalEntity? = repository.getGoalOnce(goalId)

    /** Creates a goal ([editingId] == null) or edits one. Editing never changes any allocation. */
    fun saveGoal(
        editingId: String?, name: String, targetText: String, targetDateEpochDay: Long?,
        accountId: String?, iconKey: String, note: String?
    ) {
        viewModelScope.launch {
            val target = MoneyParser.parsePositiveMinor(targetText)
            if (name.isBlank() || name.trim().length > 60 || target == null) {
                _events.emit(SavingsGoalEvent.Invalid(R.string.error_goal_invalid)); return@launch
            }
            val cleanNote = note?.trim()?.ifBlank { null }
            if (editingId == null) {
                repository.createGoal(userId, name.trim(), target, targetDateEpochDay, accountId, iconKey, cleanNote)
            } else {
                when (repository.updateGoal(editingId, name.trim(), target, targetDateEpochDay, accountId, iconKey, cleanNote)) {
                    is GoalResult.NotFound -> { _events.emit(SavingsGoalEvent.Invalid(R.string.error_generic)); return@launch }
                    else -> Unit
                }
            }
            _events.emit(SavingsGoalEvent.Saved)
        }
    }

    fun deleteGoal(goal: SavingsGoalEntity) {
        viewModelScope.launch {
            repository.deleteGoal(goal.id)
            _events.emit(SavingsGoalEvent.Deleted(goal.id, goal.name))
        }
    }

    fun undoDelete(goalId: String) {
        viewModelScope.launch { repository.restoreGoal(goalId) }
    }
}
