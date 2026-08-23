package com.hisab.app.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.entity.SavingsGoalEntity
import com.hisab.app.data.repository.SavingsGoalRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SavingsGoalEvent {
    data object Saved : SavingsGoalEvent
    data object ValidationError : SavingsGoalEvent
}

class SavingsGoalsViewModel(
    private val userId: String,
    private val savingsGoalRepository: SavingsGoalRepository
) : ViewModel() {

    val goals: StateFlow<List<SavingsGoalEntity>> =
        savingsGoalRepository.getActiveGoals(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<SavingsGoalEvent>()
    val events = _events.asSharedFlow()

    fun createGoal(name: String, targetAmountMajor: String) {
        viewModelScope.launch {
            val targetMinor = targetAmountMajor.trim().toDoubleOrNull()?.let { Math.round(it * 100) }
            if (name.isBlank() || targetMinor == null || targetMinor <= 0) {
                _events.emit(SavingsGoalEvent.ValidationError); return@launch
            }
            savingsGoalRepository.createGoal(userId, name.trim(), targetMinor)
            _events.emit(SavingsGoalEvent.Saved)
        }
    }

    fun contribute(goalId: String, amountMajor: String) {
        viewModelScope.launch {
            val amountMinor = amountMajor.trim().toDoubleOrNull()?.let { Math.round(it * 100) }
            if (amountMinor == null || amountMinor <= 0) {
                _events.emit(SavingsGoalEvent.ValidationError); return@launch
            }
            savingsGoalRepository.contribute(goalId, amountMinor)
            _events.emit(SavingsGoalEvent.Saved)
        }
    }
}
