package org.musu.next.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.musu.next.data.repository.SnapshotRepository
import org.musu.next.data.repository.SnapshotRepositoryImpl
import org.musu.next.ui.screen.snapshot.SnapshotBusy
import org.musu.next.ui.screen.snapshot.SnapshotUiState

class SnapshotViewModel(
    private val repo: SnapshotRepository = SnapshotRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnapshotUiState())
    val uiState: StateFlow<SnapshotUiState> = _uiState.asStateFlow()

    fun refresh() {
        if (_uiState.value.busy != SnapshotBusy.None) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repo.list().fold(
                onSuccess = { list -> _uiState.update { it.copy(isLoading = false, snapshots = list) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "snapshot list failed")
                    }
                },
            )
        }
    }

    fun create() {
        if (_uiState.value.busy != SnapshotBusy.None) return
        _uiState.update { it.copy(busy = SnapshotBusy.Creating, errorMessage = null, createdPath = null) }
        viewModelScope.launch {
            repo.create().fold(
                onSuccess = { created ->
                    _uiState.update { it.copy(busy = SnapshotBusy.None, createdPath = created.path) }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(busy = SnapshotBusy.None, errorMessage = error.message ?: "snapshot create failed")
                    }
                },
            )
        }
    }

    fun restore(path: String) {
        if (_uiState.value.busy != SnapshotBusy.None) return
        _uiState.update { it.copy(busy = SnapshotBusy.Restoring, errorMessage = null, restoreNeedReboot = null) }
        viewModelScope.launch {
            repo.restore(path).fold(
                onSuccess = { needReboot ->
                    _uiState.update { it.copy(busy = SnapshotBusy.None, restoreNeedReboot = needReboot) }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(busy = SnapshotBusy.None, errorMessage = error.message ?: "snapshot restore failed")
                    }
                },
            )
        }
    }

    fun delete(path: String) {
        if (_uiState.value.busy != SnapshotBusy.None) return
        _uiState.update { it.copy(busy = SnapshotBusy.Deleting, errorMessage = null) }
        viewModelScope.launch {
            repo.delete(path).fold(
                onSuccess = {
                    _uiState.update { it.copy(busy = SnapshotBusy.None) }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(busy = SnapshotBusy.None, errorMessage = error.message ?: "snapshot delete failed")
                    }
                },
            )
        }
    }

    fun dismissRestoreResult() = _uiState.update { it.copy(restoreNeedReboot = null) }

    fun clearCreatedPath() = _uiState.update { it.copy(createdPath = null) }
}
