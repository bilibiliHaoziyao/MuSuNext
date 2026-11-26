package org.musu.next.ui.screen.dirtyfrag

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.musu.next.R

/**
 * All DirtyFrag wording lives here so Material and Miuix differ only in containers (spec §7.3).
 * Machine tokens from §5 (`verdict`, `risks`, `stages`, `error.code`, reason codes) are translated
 * once, in one place. No placeholder filler: every failure carries a code or a concrete cause.
 */

@Composable
fun dfVerdictLabel(verdict: String): String = stringResource(
    when (verdict) {
        "ready" -> R.string.dirtyfrag_verdict_ready
        "already_rooted" -> R.string.dirtyfrag_verdict_rooted
        "unsupported" -> R.string.dirtyfrag_verdict_unsupported
        else -> R.string.dirtyfrag_verdict_unknown
    },
)

@Composable
fun dfReasonLabel(code: String): String = stringResource(
    when (code) {
        "KMI_UNKNOWN" -> R.string.dirtyfrag_reason_kmi_unknown
        "KMI_NO_PAYLOAD" -> R.string.dirtyfrag_reason_kmi_no_payload
        "ABI_UNSUPPORTED" -> R.string.dirtyfrag_reason_abi
        "PAYLOAD_VERIFY_FAILED" -> R.string.dirtyfrag_reason_verify
        "VENDOR_PATCH_SUSPECTED" -> R.string.dirtyfrag_reason_vendor_patch
        "SELINUX_ENFORCING" -> R.string.dirtyfrag_reason_selinux
        else -> R.string.dirtyfrag_reason_other
    },
)

/** The three risk tokens are contract keys (§5.3); never localise the machine value itself. */
@Composable
fun dfRiskLabel(token: String): String = stringResource(
    when (token) {
        "root_cleared_on_reboot" -> R.string.dirtyfrag_risk_reboot
        "setenforce_permissive" -> R.string.dirtyfrag_risk_setenforce
        "kernel_conflict_on_repeat" -> R.string.dirtyfrag_risk_repeat
        else -> R.string.dirtyfrag_risk_unknown
    },
)

@Composable
fun dfStageLabel(name: String): String = stringResource(
    when (name) {
        "detect" -> R.string.dirtyfrag_stage_detect
        "stage_ksud" -> R.string.dirtyfrag_stage_stage
        "engine_exec" -> R.string.dirtyfrag_stage_exec
        "verify_exposed" -> R.string.dirtyfrag_stage_verify
        else -> R.string.dirtyfrag_stage_other
    },
)

/**
 * Failure copy: a contract error always shows its number (§5.6); a transport/parse failure shows
 * the concrete cause or a fixed sentence — never a fabricated "-1" code or a blank line.
 */
@Composable
fun dfFailureMessage(state: DirtyFragUiState): String {
    val code = state.errorCode
    if (code != null) return stringResource(dfErrorRes(code), code)
    val cause = state.errorMessage
    if (!cause.isNullOrBlank()) return cause
    return stringResource(R.string.dirtyfrag_read_failed)
}

/** Error-code copy per §5.6. Unknown codes still show their number, never a blank string. */
@StringRes
fun dfErrorRes(code: Int?): Int = when (code) {
    null -> R.string.dirtyfrag_error_unknown
    2000 -> R.string.dirtyfrag_error_2000
    2002 -> R.string.dirtyfrag_error_2002
    2003 -> R.string.dirtyfrag_error_2003
    2100 -> R.string.dirtyfrag_error_2100
    2101 -> R.string.dirtyfrag_error_2101
    2102 -> R.string.dirtyfrag_error_2102
    2103 -> R.string.dirtyfrag_error_2103
    2104 -> R.string.dirtyfrag_error_2104
    2105 -> R.string.dirtyfrag_error_2105
    DF_ALREADY_ROOTED -> R.string.dirtyfrag_error_2200
    DF_BUSY -> R.string.dirtyfrag_error_2201
    2202 -> R.string.dirtyfrag_error_2202
    2203 -> R.string.dirtyfrag_error_2203
    2204 -> R.string.dirtyfrag_error_2204
    DF_EXPLOIT_FAILED -> R.string.dirtyfrag_error_2300
    DF_TIMEOUT -> R.string.dirtyfrag_error_2301
    2302 -> R.string.dirtyfrag_error_2302
    2400 -> R.string.dirtyfrag_error_2400
    else -> R.string.dirtyfrag_error_unknown
}
