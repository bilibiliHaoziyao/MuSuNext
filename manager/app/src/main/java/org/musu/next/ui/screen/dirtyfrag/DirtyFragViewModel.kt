package org.musu.next.ui.screen.dirtyfrag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

// Contract error codes (spec §5.6). Kept as constants so the UI can branch on meaning, not text.
internal const val DF_ALREADY_ROOTED = 2200
internal const val DF_BUSY = 2201
internal const val DF_EXPLOIT_FAILED = 2300
internal const val DF_TIMEOUT = 2301

private const val DETECT_TIMEOUT_MS = 2_000L
private const val RUN_TIMEOUT_MS = 90_000L

/**
 * S0–S9 state machine (spec §7.2). No optimistic update: the S5 button stays disabled until the
 * lower layer answers or the guard fires, so "on then off" is never shown. Feedback is in-place.
 */
class DirtyFragViewModel(
    private val repo: DirtyFragRepository = DirtyFragRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DirtyFragUiState())
    val uiState: StateFlow<DirtyFragUiState> = _uiState.asStateFlow()

    /** Guards against a late CLI answer overwriting a newer state after a timeout/retry. */
    private var generation = 0
    /** Hard anti-double-click latch, independent of Compose recomposition. */
    private var runInFlight = false

    init {
        probe()
    }

    /** S0 → S1 → S2/S3/S8/S9. Called on entry and by retry. */
    fun probe() {
        if (_uiState.value.phase == DfPhase.Probing) return
        val token = ++generation
        runInFlight = false
        _uiState.update {
            it.copy(
                phase = DfPhase.Probing,
                detect = null,
                status = null,
                run = null,
                errorCode = null,
                errorMessage = null,
                busy = false,
            )
        }
        viewModelScope.launch {
            val answer = withTimeoutOrNull(DETECT_TIMEOUT_MS) { repo.detect() }
            if (token != generation) return@launch
            if (answer == null) {
                _uiState.update { it.copy(phase = DfPhase.ReadError) }
                return@launch
            }
            answer.fold(
                onSuccess = { applyDetect(it) },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(phase = DfPhase.ReadError, errorMessage = error.message?.takeIf { m -> m.isNotBlank() })
                    }
                },
            )
        }
    }

    /** S2 → S4. The confirmation card is mandatory (§7.2); no code path runs from Ready directly. */
    fun requestJailbreak() {
        if (_uiState.value.phase != DfPhase.Ready) return
        _uiState.update { it.copy(phase = DfPhase.ConfirmRisk) }
    }

    /** S4 → S2. The user may back out before any kernel state is touched. */
    fun cancelRisk() {
        if (_uiState.value.phase != DfPhase.ConfirmRisk) return
        _uiState.update { it.copy(phase = DfPhase.Ready) }
    }

    /** S4 → S5 → S6/S7/S8/S9. Rejects every call that is not the confirmed S4 state. */
    fun run() {
        if (_uiState.value.phase != DfPhase.ConfirmRisk || runInFlight) return
        runInFlight = true
        val token = ++generation
        val requestId = UUID.randomUUID().toString().replace("-", "").take(8)
        _uiState.update {
            it.copy(phase = DfPhase.Running, run = null, errorCode = null, errorMessage = null, busy = false)
        }
        viewModelScope.launch {
            val answer = withTimeoutOrNull(RUN_TIMEOUT_MS) { repo.run(requestId) }
            runInFlight = false
            if (token != generation) return@launch
            if (answer == null) {
                _uiState.update { it.copy(phase = DfPhase.Failed, errorCode = DF_TIMEOUT) }
                return@launch
            }
            answer.fold(
                onSuccess = { run ->
                    applyRun(run)
                    if (_uiState.value.phase == DfPhase.Succeeded) refreshStatus(token)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(phase = DfPhase.Failed, errorMessage = error.message?.takeIf { m -> m.isNotBlank() })
                    }
                },
            )
        }
    }

    /** Refreshes the boot-scoped exposure/risk note after success (§5.5). Best effort only. */
    private fun refreshStatus(token: Int) {
        viewModelScope.launch {
            repo.status().fold(
                onSuccess = { status ->
                    if (token == generation && _uiState.value.phase == DfPhase.Succeeded) {
                        _uiState.update { it.copy(status = status) }
                    }
                },
                onFailure = { /* status is informational; a failed refresh must not fail the run */ },
            )
        }
    }

    private fun applyDetect(detect: DfDetect) {
        val code = detect.error?.code
        val phase = when {
            detect.exposed || code == DF_ALREADY_ROOTED -> DfPhase.AlreadyRooted
            detect.error != null && code != null -> DfPhase.ReadError
            detect.verdict == "ready" && detect.supported -> DfPhase.Ready
            // unsupported / unknown / unverified: button stays disabled, reasons are shown
            else -> DfPhase.Unsupported
        }
        _uiState.update {
            it.copy(
                phase = phase,
                detect = detect,
                errorCode = code,
                busy = code == DF_BUSY,
            )
        }
    }

    private fun applyRun(run: DfRun) {
        val code = run.error?.code
        val phase = when {
            run.exposed && run.state == "succeeded" -> DfPhase.Succeeded
            code == DF_ALREADY_ROOTED -> DfPhase.AlreadyRooted
            code == DF_BUSY || run.state == "running" -> DfPhase.ReadError
            run.state == "failed" || run.state == "timeout" || code != null -> DfPhase.Failed
            run.exposed -> DfPhase.Succeeded
            else -> DfPhase.Failed
        }
        _uiState.update {
            it.copy(
                phase = phase,
                run = run,
                errorCode = code,
                busy = code == DF_BUSY || run.state == "running",
            )
        }
    }

    /** S7/S9 → probe again. Also usable after S6 to re-check a fresh boot. */
    fun retry() = probe()
}
