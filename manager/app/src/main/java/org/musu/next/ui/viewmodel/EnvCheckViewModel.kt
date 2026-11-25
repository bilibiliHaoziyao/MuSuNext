package org.musu.next.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.musu.next.data.repository.EnvCheckRepository
import org.musu.next.data.repository.EnvCheckRepositoryImpl
import org.musu.next.ui.screen.envcheck.EnvCheckUiState

class EnvCheckViewModel(
    private val repo: EnvCheckRepository = EnvCheckRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnvCheckUiState())
    val uiState: StateFlow<EnvCheckUiState> = _uiState.asStateFlow()

    private var previewRequested = false

    fun check() {
        if (_uiState.value.isChecking) return
        _uiState.update { it.copy(isChecking = true, errorMessage = null) }
        viewModelScope.launch {
            repo.check().fold(
                onSuccess = { result ->
                    _uiState.update { it.copy(isChecking = false, result = result) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isChecking = false, errorMessage = error.message ?: "envcheck failed")
                    }
                },
            )
        }
    }

    /** The Security entry page probes once when it first becomes visible; manual checks still work. */
    fun requestPreview() {
        if (previewRequested) return
        previewRequested = true
        if (_uiState.value.result == null && !_uiState.value.isChecking) check()
    }
}
