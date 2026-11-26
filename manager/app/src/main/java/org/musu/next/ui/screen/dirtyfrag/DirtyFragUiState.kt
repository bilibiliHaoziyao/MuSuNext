package org.musu.next.ui.screen.dirtyfrag

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * UI-facing state machine (spec §7.2 S0–S9). The Repository only parses §5 JSON; it never
 * decides a phase — every phase transition lives in [DirtyFragViewModel].
 */
enum class DfPhase {
    Idle,          // S0 first frame, skeleton, button disabled
    Probing,       // S1 detect in flight (<=2s)
    Ready,         // S2 verdict=ready
    Unsupported,   // S3 verdict=unsupported / unknown
    ConfirmRisk,   // S4 risk confirmation — mandatory, cannot be skipped
    Running,       // S5 run in flight (70s window), button disabled, no percentage
    Succeeded,     // S6 exposed=true after run
    Failed,        // S7 ok=false or state=failed/timeout
    AlreadyRooted, // S8 detect says exposed=true, short circuit
    ReadError,     // S9 read failure / 2201 BUSY / 2200 ALREADY_ROOTED
}

/** A `reasons[]` entry. Severity is `blocker` (red) or `warning` (amber) per §5.3. */
@Immutable
data class DfReason(
    val code: String,
    val severity: String,
    val message: String,
) {
    val isBlocker: Boolean get() = severity == "blocker"
}

/** `device` from detect (§5.3). Every field is optional: blank values are hidden, never shown as "-". */
@Immutable
data class DfDevice(
    val kmi: String?,
    val kernelRelease: String?,
    val androidRelease: String?,
    val abi: String?,
    val selinux: String?,
)

/** `engine` + `payload` identity from detect (§5.3 / §5.2), used for the source line. */
@Immutable
data class DfInfo(
    val payloadVersion: String?,
    val payloadLicense: String?,
    val enginePackage: String?,
    val engineMode: String?,
    val engineManager: String?,
)

/** Parsed `detect` answer. `ok=false` is a credible "read failed", not "unsupported". */
@Immutable
data class DfDetect(
    val ok: Boolean,
    val error: DfError?,
    val exposed: Boolean,
    val supported: Boolean,
    val verdict: String,
    val device: DfDevice,
    val reasons: List<DfReason>,
    /** Machine codes: root_cleared_on_reboot / setenforce_permissive / kernel_conflict_on_repeat. */
    val risks: List<String>,
    val info: DfInfo,
)

/** One `stages[]` entry from run (§5.4). Names are machine tokens, localised in the UI layer. */
@Immutable
data class DfStage(
    val name: String,
    val ok: Boolean,
)

/** Parsed `run` answer (§5.4). */
@Immutable
data class DfRun(
    val ok: Boolean,
    val error: DfError?,
    val exposed: Boolean,
    val state: String,
    val requestId: String?,
    val elapsedMs: Long,
    val stages: List<DfStage>,
    val next: String?,
)

/** Parsed `status` answer (§5.5). */
@Immutable
data class DfStatus(
    val ok: Boolean,
    val error: DfError?,
    val exposed: Boolean,
    val state: String,
    val bootId: String?,
    val activeBoot: String?,
    val next: String?,
    val riskNote: String?,
)

/** `{code,message,retryable}` from §5.2 — never a plain string. */
@Immutable
data class DfError(
    val code: Int,
    val message: String?,
    val retryable: Boolean,
)

@Immutable
data class DirtyFragUiState(
    val phase: DfPhase = DfPhase.Idle,
    val detect: DfDetect? = null,
    val status: DfStatus? = null,
    val run: DfRun? = null,
    /** Transport / parse failure. Distinct from a well-formed `ok=false` answer held in detect/run. */
    val errorMessage: String? = null,
    /** The error code to render (from error.code), or null. */
    val errorCode: Int? = null,
    /** True when the last failure was 2201 BUSY — drives the "do not tap again" copy. */
    val busy: Boolean = false,
)

// region ksud `dirtyfrag` wire format (spec §5) — field names are the contract, do not rename.

@Serializable
internal data class DfErrorDto(
    val code: Int = 0,
    val message: String? = null,
    val retryable: Boolean = false,
)

@Serializable
internal data class DfDeviceDto(
    val kmi: String? = null,
    @SerialName("kernel_release") val kernelRelease: String? = null,
    @SerialName("android_release") val androidRelease: String? = null,
    val abi: String? = null,
    val selinux: String? = null,
)

@Serializable
internal data class DfMatchDto(
    val payload: String? = null,
    @SerialName("matched_by") val matchedBy: String? = null,
    @SerialName("payload_sha256") val payloadSha256: String? = null,
    val verified: Boolean = false,
)

@Serializable
internal data class DfReasonDto(
    val code: String = "",
    val severity: String = "",
    val message: String = "",
)

@Serializable
internal data class DfPayloadDto(
    val version: String? = null,
    val license: String? = null,
    val source: String? = null,
)

@Serializable
internal data class DfEngineDto(
    // `package` is a Kotlin keyword; the wire field stays "package" (spec §5.3).
    @SerialName("package") val packageName: String? = null,
    val mode: String? = null,
    val manager: String? = null,
)

@Serializable
internal data class DfDetectResponse(
    val ok: Boolean = false,
    val error: DfErrorDto? = null,
    val exposed: Boolean = false,
    val supported: Boolean = false,
    val verdict: String = "",
    val device: DfDeviceDto = DfDeviceDto(),
    val match: DfMatchDto = DfMatchDto(),
    val reasons: List<DfReasonDto> = emptyList(),
    val risks: List<String> = emptyList(),
    val payload: DfPayloadDto = DfPayloadDto(),
    val engine: DfEngineDto = DfEngineDto(),
)

@Serializable
internal data class DfStageDto(
    val name: String = "",
    val ok: Boolean = false,
)

@Serializable
internal data class DfUsedDto(
    val kmi: String? = null,
    val ko: String? = null,
    val manager: String? = null,
    @SerialName("libksud_sha256") val libksudSha256: String? = null,
)

@Serializable
internal data class DfRunResponse(
    val ok: Boolean = false,
    val error: DfErrorDto? = null,
    val exposed: Boolean = false,
    val state: String = "",
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("elapsed_ms") val elapsedMs: Long = 0L,
    val used: DfUsedDto = DfUsedDto(),
    val stages: List<DfStageDto> = emptyList(),
    val next: String? = null,
)

@Serializable
internal data class DfAttemptDto(
    @SerialName("request_id") val requestId: String? = null,
    val result: String? = null,
    val code: Int = 0,
    @SerialName("started_ms") val startedMs: Long = 0L,
    @SerialName("finished_ms") val finishedMs: Long = 0L,
)

@Serializable
internal data class DfStatusResponse(
    val ok: Boolean = false,
    val error: DfErrorDto? = null,
    val exposed: Boolean = false,
    val state: String = "",
    @SerialName("boot_id") val bootId: String? = null,
    @SerialName("active_boot") val activeBoot: String? = null,
    @SerialName("last_attempt") val lastAttempt: DfAttemptDto? = null,
    val next: String? = null,
    @SerialName("risk_note") val riskNote: String? = null,
)

@Serializable
internal data class DfRunRequest(
    val confirm: Boolean = true,
    @SerialName("client_request_id") val clientRequestId: String,
)

// endregion

internal fun DfErrorDto.toDomain(): DfError = DfError(
    code = code,
    message = message?.takeIf { it.isNotBlank() },
    retryable = retryable,
)

private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }

internal fun DfDetectResponse.toDomain(): DfDetect = DfDetect(
    ok = ok,
    error = error?.toDomain(),
    exposed = exposed,
    supported = supported,
    verdict = verdict,
    device = DfDevice(
        kmi = device.kmi.blankToNull(),
        kernelRelease = device.kernelRelease.blankToNull(),
        androidRelease = device.androidRelease.blankToNull(),
        abi = device.abi.blankToNull(),
        selinux = device.selinux.blankToNull(),
    ),
    reasons = reasons.map { DfReason(it.code, it.severity, it.message) },
    risks = risks,
    info = DfInfo(
        payloadVersion = payload.version.blankToNull(),
        payloadLicense = payload.license.blankToNull(),
        enginePackage = engine.packageName.blankToNull(),
        engineMode = engine.mode.blankToNull(),
        engineManager = engine.manager.blankToNull(),
    ),
)

internal fun DfRunResponse.toDomain(): DfRun = DfRun(
    ok = ok,
    error = error?.toDomain(),
    exposed = exposed,
    state = state,
    requestId = requestId.blankToNull(),
    elapsedMs = elapsedMs,
    stages = stages.map { DfStage(it.name, it.ok) },
    next = next.blankToNull(),
)

internal fun DfStatusResponse.toDomain(): DfStatus = DfStatus(
    ok = ok,
    error = error?.toDomain(),
    exposed = exposed,
    state = state,
    bootId = bootId.blankToNull(),
    activeBoot = activeBoot.blankToNull(),
    next = next.blankToNull(),
    riskNote = riskNote.blankToNull(),
)
