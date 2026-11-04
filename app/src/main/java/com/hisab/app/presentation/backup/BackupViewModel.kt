package com.hisab.app.presentation.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisab.app.R
import com.hisab.app.data.backup.BackupCodec
import com.hisab.app.data.repository.BackupRepository
import com.hisab.app.data.repository.InspectResult
import com.hisab.app.data.repository.RestoreResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the screen is currently showing/doing. */
sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState
}

sealed interface BackupEvent {
    /** A backup file's text is ready; the screen writes it to the Uri the user picked (or shares it). */
    data class BackupReady(val text: String, val fileName: String) : BackupEvent
    /** The chosen file needs a password, or the one given was wrong. */
    data class PasswordRequired(val wrong: Boolean) : BackupEvent
    /** The file is valid: show the preview + confirmation before anything is replaced. */
    data class RestorePreview(
        val parsed: BackupCodec.Parsed, val warnings: List<String>, val currentCounts: Map<String, Int>
    ) : BackupEvent
    data class Restored(val counts: Map<String, Int>) : BackupEvent
    data class Failure(val message: String?, val messageRes: Int = R.string.backup_error_generic) : BackupEvent
}

class BackupViewModel(
    private val repository: BackupRepository,
    private val appVersion: String,
    /** Writes the pre-restore safety copy to app-private storage (supplied by the screen's Context). */
    private val safetyCopyWriter: suspend (String) -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    /** A parsed backup waiting for the user's confirmation (kept here so rotating the screen cannot lose it). */
    private var pending: BackupCodec.Parsed? = null

    fun createBackup(password: CharArray?, fileName: String) = work {
        val text = repository.createBackup(appVersion, password)
        _events.emit(BackupEvent.BackupReady(text, fileName))
    }

    /** Step 1 of restore: validate the file and show what it would do. Changes nothing. */
    fun inspect(fileText: String, password: CharArray?) = work {
        when (val r = repository.inspect(fileText, password)) {
            InspectResult.NeedsPassword -> _events.emit(BackupEvent.PasswordRequired(wrong = false))
            InspectResult.WrongPassword -> _events.emit(BackupEvent.PasswordRequired(wrong = true))
            is InspectResult.Invalid -> _events.emit(BackupEvent.Failure(r.message))
            is InspectResult.Ready -> {
                pending = r.parsed
                _events.emit(BackupEvent.RestorePreview(r.parsed, r.warnings, r.currentCounts))
            }
        }
    }

    /** Step 2: the user confirmed. Replaces all data atomically (see BackupRepository.restore). */
    fun confirmRestore() = work {
        val parsed = pending ?: run { _events.emit(BackupEvent.Failure(null)); return@work }
        when (val r = repository.restore(parsed, appVersion, safetyCopyWriter)) {
            is RestoreResult.Restored -> { pending = null; _events.emit(BackupEvent.Restored(r.counts)) }
            is RestoreResult.Failed -> _events.emit(BackupEvent.Failure(r.message))
        }
    }

    fun cancelRestore() { pending = null }

    fun reportReadError() { viewModelScope.launch { _events.emit(BackupEvent.Failure(null, R.string.backup_error_read)) } }

    private fun work(block: suspend () -> Unit) {
        if (_state.value == BackupUiState.Working) return
        viewModelScope.launch {
            _state.value = BackupUiState.Working
            try { block() } catch (e: Exception) { _events.emit(BackupEvent.Failure(e.message)) } finally { _state.value = BackupUiState.Idle }
        }
    }
}
