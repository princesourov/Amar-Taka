package com.hisab.app.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.R
import com.hisab.app.data.local.entity.GoalContributionEntity
import com.hisab.app.data.repository.AccountAvailability
import com.hisab.app.data.repository.GoalResult
import com.hisab.app.data.repository.GoalWithProgress
import com.hisab.app.data.repository.SavingsGoalRepository
import com.hisab.app.domain.budget.MoneyParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One goal: its progress, its allocation history, and the allocate / withdraw actions. */
class GoalDetailViewModel(
    userId: String,
    private val goalId: String,
    private val repository: SavingsGoalRepository
) : ViewModel() {

    val goal: StateFlow<GoalWithProgress?> =
        repository.observeGoal(goalId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val contributions: StateFlow<List<GoalContributionEntity>> =
        repository.observeContributions(goalId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountAvailability>> =
        repository.observeAccountAvailability(userId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<SavingsGoalEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    /** Allocation per account for this goal (accountId null = carried over from before accounts were tracked). */
    fun allocationsByAccount(rows: List<GoalContributionEntity>): Map<String?, Long> =
        rows.groupBy { it.accountId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }.filterValues { it != 0L }

    fun allocate(accountId: String?, amountText: String, note: String?) = submit(amountText) { amount ->
        if (accountId == null) return@submit GoalResult.NotFound
        repository.allocate(goalId, accountId, amount, note)
    }

    fun withdraw(accountId: String?, amountText: String, note: String?) = submit(amountText) { amount ->
        repository.release(goalId, accountId, amount, note)
    }

    private fun submit(amountText: String, block: suspend (Long) -> GoalResult) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val amount = MoneyParser.parsePositiveMinor(amountText)
                if (amount == null) { _events.emit(SavingsGoalEvent.Invalid(R.string.error_amount_required)); return@launch }
                when (val r = block(amount)) {
                    is GoalResult.Done -> _events.emit(SavingsGoalEvent.Saved)
                    is GoalResult.Rejected -> _events.emit(r.check.toEvent())
                    GoalResult.NotFound -> _events.emit(SavingsGoalEvent.Invalid(R.string.error_generic))
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteGoal() {
        viewModelScope.launch {
            val g = goal.value?.goal ?: return@launch
            repository.deleteGoal(g.id)
            _events.emit(SavingsGoalEvent.Deleted(g.id, g.name))
        }
    }

    fun undoDelete() {
        viewModelScope.launch { repository.restoreGoal(goalId) }
    }
}
