package com.hisab.app.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.data.local.entity.PersonEntity
import com.hisab.app.data.repository.PersonRepository
import com.hisab.app.domain.model.PersonWithBalance
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CreatePersonEvent {
    data object Saved : CreatePersonEvent
    data object ValidationError : CreatePersonEvent
}

class PeopleViewModel(
    private val userId: String,
    private val personRepository: PersonRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val allPeople: StateFlow<List<PersonWithBalance>> =
        personRepository.getPeopleWithBalances(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val people: StateFlow<List<PersonWithBalance>> = combine(allPeople, _searchQuery) { people, query ->
        if (query.isBlank()) people
        else people.filter { it.person.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<CreatePersonEvent>()
    val events = _events.asSharedFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun loadForEdit(personId: String): PersonEntity? = personRepository.getPersonByIdOnce(personId)

    fun createPerson(name: String, phone: String?, email: String?, notes: String?) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                if (name.isBlank()) {
                    _events.emit(CreatePersonEvent.ValidationError); return@launch
                }
                personRepository.createPerson(
                    userId, name.trim(), phone?.ifBlank { null }, email?.ifBlank { null }, notes?.ifBlank { null }
                )
                _events.emit(CreatePersonEvent.Saved)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updatePerson(existing: PersonEntity, name: String, phone: String?, email: String?, notes: String?) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                if (name.isBlank()) {
                    _events.emit(CreatePersonEvent.ValidationError); return@launch
                }
                personRepository.updatePerson(existing, name.trim(), phone?.ifBlank { null }, email?.ifBlank { null }, notes?.ifBlank { null })
                _events.emit(CreatePersonEvent.Saved)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deactivatePerson(id: String) {
        viewModelScope.launch { personRepository.deactivatePerson(id) }
    }
}
