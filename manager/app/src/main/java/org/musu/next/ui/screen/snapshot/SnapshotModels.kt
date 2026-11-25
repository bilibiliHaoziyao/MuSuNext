package org.musu.next.ui.screen.snapshot

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
data class SnapshotInfo(
    val path: String,
    val created: String?,
    val modules: Int,
    val profiles: Int,
    val size: Long,
)

@Immutable
data class SnapshotCreateInfo(
    val path: String,
    val modules: Int,
    val profiles: Int,
    val builtins: Int,
    val bytes: Long,
)

enum class SnapshotBusy { None, Creating, Restoring, Deleting }

@Immutable
data class SnapshotUiState(
    val isLoading: Boolean = false,
    val snapshots: List<SnapshotInfo> = emptyList(),
    val errorMessage: String? = null,
    val busy: SnapshotBusy = SnapshotBusy.None,
    /** Shown as a non-modal line after a successful create. */
    val createdPath: String? = null,
    /** Non-null means "show the restore result dialog"; the value carries the reboot requirement. */
    val restoreNeedReboot: Boolean? = null,
)

// region ksud `snapshot` wire format

@Serializable
internal data class SnapshotCreateResponse(
    val ok: Boolean = false,
    val path: String? = null,
    val modules: Int = 0,
    val profiles: Int = 0,
    val builtins: Int = 0,
    val bytes: Long = 0L,
    val error: String? = null,
)

@Serializable
internal data class SnapshotListResponse(
    val ok: Boolean = false,
    val snapshots: List<SnapshotDto> = emptyList(),
    val error: String? = null,
)

@Serializable
internal data class SnapshotDto(
    val path: String = "",
    val created: String? = null,
    val modules: Int = 0,
    val profiles: Int = 0,
    val size: Long = 0L,
)

@Serializable
internal data class SnapshotRestoreResponse(
    val ok: Boolean = false,
    val restored: SnapshotDto? = null,
    @SerialName("need_reboot") val needReboot: Boolean = false,
    val error: String? = null,
)

@Serializable
internal data class SnapshotOkResponse(
    val ok: Boolean = false,
    val error: String? = null,
)

internal fun SnapshotDto.toInfo(): SnapshotInfo = SnapshotInfo(
    path = path,
    created = created?.takeIf { it.isNotBlank() },
    modules = modules,
    profiles = profiles,
    size = size,
)

// endregion
