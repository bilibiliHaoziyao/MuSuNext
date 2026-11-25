package org.musu.next.ui.screen.envcheck

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One check item. [exposed] is intentionally nullable: null means "not verified", not "safe". */
@Immutable
data class EnvCheckItem(
    val id: String,
    val label: String,
    val exposed: Boolean?,
    val detail: String?,
)

/**
 * A parsed `ksud envcheck` answer.
 *
 * `ok = false` is a valid answer: the probe may complete only part of its items. The UI must show
 * [error] and [partialItems] instead of turning the page green.
 */
@Immutable
data class EnvCheckResult(
    val ok: Boolean,
    val view: String?,
    val uid: Int?,
    val items: List<EnvCheckItem>,
    val partialItems: List<EnvCheckItem>,
    val exposedCount: Int?,
    val checkedAt: String?,
    val error: String?,
) {
    /** Count real exposures; fall back to the item list when the backend omits the counter. */
    val effectiveExposedCount: Int
        get() = exposedCount ?: items.count { it.exposed == true }
}

@Immutable
data class EnvCheckUiState(
    val isChecking: Boolean = false,
    val result: EnvCheckResult? = null,
    /** Shell / parse failure. Distinct from a well-formed `ok:false` answer in [result]. */
    val errorMessage: String? = null,
)

// region ksud `envcheck` wire format

@Serializable
internal data class EnvCheckResponse(
    val ok: Boolean = false,
    val view: String? = null,
    val uid: Int? = null,
    val items: List<EnvCheckItemDto> = emptyList(),
    @SerialName("exposed_count") val exposedCount: Int? = null,
    @SerialName("checked_at") val checkedAt: String? = null,
    val error: String? = null,
    @SerialName("partial_items") val partialItems: List<EnvCheckItemDto> = emptyList(),
)

@Serializable
internal data class EnvCheckItemDto(
    val id: String = "",
    val label: String = "",
    val exposed: Boolean? = null,
    val detail: String? = null,
)

internal fun EnvCheckResponse.toResult(): EnvCheckResult = EnvCheckResult(
    ok = ok,
    view = view,
    uid = uid,
    items = items.map { it.toItem() },
    partialItems = partialItems.map { it.toItem() },
    exposedCount = exposedCount,
    checkedAt = checkedAt,
    error = error?.takeIf { it.isNotBlank() },
)

private fun EnvCheckItemDto.toItem(): EnvCheckItem = EnvCheckItem(
    id = id,
    label = label.ifBlank { id },
    exposed = exposed,
    detail = detail?.takeIf { it.isNotBlank() },
)

// endregion
