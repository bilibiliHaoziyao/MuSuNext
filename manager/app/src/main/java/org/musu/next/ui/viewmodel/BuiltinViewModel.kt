package org.musu.next.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.musu.next.data.model.BuiltinComponentId
import org.musu.next.data.repository.BuiltinRepository
import org.musu.next.data.repository.BuiltinRepositoryImpl
import org.musu.next.ui.screen.builtin.BuiltinUiState
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reads and switches the two built-in components. Every write goes through `ksud builtin enable|disable`
 * and is followed by a fresh [BuiltinRepository.list]: the manager never guesses `ui_state`.
 */
class BuiltinViewModel(
    private val repo: BuiltinRepository = BuiltinRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuiltinUiState())
    val uiState: StateFlow<BuiltinUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Reloads both components. A previous snapshot is kept when the probe fails (no white flash). */
    fun refresh() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(retrying = it.snapshot != null) }
        loadJob = viewModelScope.launch {
            val result = repo.list()
            _uiState.update { state ->
                result.fold(
                    onSuccess = { state.copy(snapshot = it, loadFailed = false, retrying = false) },
                    onFailure = { state.copy(loadFailed = true, retrying = false) },
                )
            }
        }
    }

    fun setEnabled(id: BuiltinComponentId, enabled: Boolean) {
        if (id in _uiState.value.toggling) return
        _uiState.update { it.copy(toggling = it.toggling + id) }
        viewModelScope.launch {
            // §9.5: the switch stays disabled until ksud answers, and it is never flipped optimistically.
            // A call that hangs gives up after 3 s, then the reload decides between the real state and S9.
            withTimeoutOrNull(ACTION_TIMEOUT) { repo.setEnabled(id, enabled) }
                ?.onFailure { Log.w(TAG, "builtin ${id.wireId} -> $enabled failed", it) }

            val fresh = repo.list()
            _uiState.update { state ->
                val toggling = state.toggling - id
                fresh.fold(
                    onSuccess = { state.copy(snapshot = it, loadFailed = false, toggling = toggling) },
                    onFailure = { state.copy(loadFailed = true, toggling = toggling) },
                )
            }
        }
    }

    private companion object {
        const val TAG = "BuiltinViewModel"
        val ACTION_TIMEOUT = 3_000L.milliseconds
    }
}
