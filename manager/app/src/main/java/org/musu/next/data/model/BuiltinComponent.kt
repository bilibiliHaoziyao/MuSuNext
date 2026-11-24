package org.musu.next.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The two components the manager ships itself. The wire id is what `ksud builtin <cmd> <id>` expects.
 */
enum class BuiltinComponentId(val wireId: String) {
    Zygisk("zygisk"),
    Metamodule("metamodule");

    companion object {
        fun fromWire(value: String?): BuiltinComponentId? =
            entries.firstOrNull { it.wireId == value }
    }
}

/**
 * `ui_state` is computed by ksud (`builtin-components.md` §4.3). The manager only maps it to a state
 * of the UI matrix (`builtin-ui.md` §5) and never re-derives it from the raw fields.
 */
enum class BuiltinPhase {
    /** S0: first frame, ksud has not answered yet. */
    Probing,

    /** S1 */
    Unsupported,

    /** S2 */
    Off,

    /** S3 */
    PendingEnable,

    /** S4 */
    Active,

    /** S5 */
    Failed,

    /** S6 */
    BlockedByOther,

    /** S8 */
    PendingDisable,

    /** S9 */
    ReadError;

    companion object {
        fun fromWire(value: String?): BuiltinPhase = when (value) {
            "unsupported" -> Unsupported
            "blocked" -> BlockedByOther
            "failed" -> Failed
            "active" -> Active
            "pending_disable" -> PendingDisable
            "pending_enable" -> PendingEnable
            "read_error" -> ReadError
            else -> Off
        }
    }
}

enum class BuiltinPendingReboot {
    None,
    Soft,
    Full;

    companion object {
        fun fromWire(value: String?): BuiltinPendingReboot = when (value) {
            "full" -> Full
            "soft" -> Soft
            else -> None
        }
    }
}

@Immutable
data class BuiltinDetail(
    val version: String? = null,
    val upstream: String? = null,
    val upstreamUrl: String? = null,
    val license: String? = null,
    val diskBytes: Long = 0L,
)

/**
 * One component as the pages and the settings entry show it. Built from [BuiltinComponentDto] only;
 * no field is inferred from another one.
 */
@Immutable
data class BuiltinComponent(
    val id: BuiltinComponentId,
    val phase: BuiltinPhase = BuiltinPhase.Probing,
    val enabled: Boolean = false,
    val pendingReboot: BuiltinPendingReboot = BuiltinPendingReboot.None,
    /** S6: the other implementation that owns the slot, shown as "name version". */
    val conflictName: String? = null,
    val conflictVersion: String? = null,
    val errorCode: Int? = null,
    /** zygisk: `metrics.zygote64_injected`, null when ksud did not report it. */
    val zygote64Injected: Boolean? = null,
    val modulesLoaded: Int = 0,
    val modulesTotal: Int = 0,
    val mountedModules: Int = 0,
    val failedModules: Int = 0,
    val scope: List<String> = emptyList(),
    /** S7 overlay: modules that cannot be enabled while this component is running. */
    val suppressed: List<String> = emptyList(),
    /** §9.5: the switch stays disabled until ksud answers, there is no optimistic update. */
    val toggling: Boolean = false,
    val detail: BuiltinDetail? = null,
) {
    /** S7 is S4 plus "someone else is blocked by us", not a state of its own (§5.1). */
    val blockingOthers: Boolean
        get() = phase == BuiltinPhase.Active && suppressed.isNotEmpty()

    /** Main matrix (§5.2) `checked`: the switch position. */
    val checked: Boolean
        get() = when (phase) {
            BuiltinPhase.Unsupported, BuiltinPhase.BlockedByOther -> false
            else -> enabled
        }

    /** Main matrix (§5.2) `enabled`: S1 / S6 / S9 and the in-flight window are not clickable. */
    val canToggle: Boolean
        get() = !toggling && phase in TOGGLEABLE_PHASES

    val needsReboot: Boolean
        get() = phase == BuiltinPhase.PendingEnable || phase == BuiltinPhase.PendingDisable

    private companion object {
        val TOGGLEABLE_PHASES = setOf(
            BuiltinPhase.Off,
            BuiltinPhase.PendingEnable,
            BuiltinPhase.Active,
            BuiltinPhase.Failed,
            BuiltinPhase.PendingDisable,
        )
    }
}

// region ksud `builtin list` wire format (builtin-components.md §4.2)

@Serializable
internal data class BuiltinListResponse(
    val schema: Int = 1,
    @SerialName("boot_id") val bootId: String? = null,
    val components: List<BuiltinComponentDto> = emptyList(),
    @SerialName("blocked_module_ids") val blockedModuleIds: List<String> = emptyList(),
    @SerialName("blocked_metamodule_ids") val blockedMetamoduleIds: List<String> = emptyList(),
    @SerialName("blocked_reason_code") val blockedReasonCode: Int? = null,
)

@Serializable
internal data class BuiltinComponentDto(
    val id: String = "",
    val name: String = "",
    @SerialName("ui_state") val uiState: String? = null,
    val enabled: Boolean = false,
    @SerialName("pending_reboot") val pendingReboot: String? = null,
    val available: Boolean = true,
    val conflict: BuiltinConflictDto? = null,
    val error: BuiltinErrorDto? = null,
    val metrics: BuiltinMetricsDto? = null,
    val detail: BuiltinDetailDto? = null,
)

@Serializable
internal data class BuiltinConflictDto(
    val kind: String? = null,
    val id: String? = null,
    val name: String? = null,
    val version: String? = null,
    val evidence: String? = null,
)

@Serializable
internal data class BuiltinErrorDto(
    val code: Int = 0,
    val message: String? = null,
)

@Serializable
internal data class BuiltinMetricsDto(
    // zygisk
    @SerialName("zygote64_injected") val zygote64Injected: Boolean? = null,
    @SerialName("zygote32_injected") val zygote32Injected: Boolean? = null,
    @SerialName("zygote32_covered") val zygote32Covered: Boolean? = null,
    @SerialName("modules_loaded") val modulesLoaded: Int = 0,
    @SerialName("modules_total") val modulesTotal: Int = 0,
    // metamodule
    @SerialName("mounted_modules") val mountedModules: Int = 0,
    @SerialName("failed_modules") val failedModules: Int = 0,
    val scope: List<String> = emptyList(),
    val degraded: Boolean = false,
)

@Serializable
internal data class BuiltinDetailDto(
    val version: String? = null,
    val upstream: String? = null,
    @SerialName("upstream_url") val upstreamUrl: String? = null,
    @SerialName("upstream_commit") val upstreamCommit: String? = null,
    val license: String? = null,
    @SerialName("disk_bytes") val diskBytes: Long = 0L,
)

/**
 * Maps one component. [suppressed] is the module list ksud attributes to this component
 * (`blocked_module_ids` / `blocked_metamodule_ids`), already resolved to display names.
 */
internal fun BuiltinComponentDto.toComponent(
    id: BuiltinComponentId,
    suppressed: List<String>,
): BuiltinComponent = BuiltinComponent(
    id = id,
    phase = BuiltinPhase.fromWire(uiState),
    enabled = enabled,
    pendingReboot = BuiltinPendingReboot.fromWire(pendingReboot),
    conflictName = conflict?.name?.takeIf { it.isNotBlank() },
    conflictVersion = conflict?.version?.takeIf { it.isNotBlank() },
    errorCode = error?.code,
    zygote64Injected = metrics?.zygote64Injected,
    modulesLoaded = metrics?.modulesLoaded ?: 0,
    modulesTotal = metrics?.modulesTotal ?: 0,
    mountedModules = metrics?.mountedModules ?: 0,
    failedModules = metrics?.failedModules ?: 0,
    scope = metrics?.scope.orEmpty(),
    suppressed = suppressed,
    detail = detail?.let {
        BuiltinDetail(
            version = it.version?.takeIf { version -> version.isNotBlank() },
            upstream = it.upstream?.takeIf { name -> name.isNotBlank() },
            upstreamUrl = it.upstreamUrl?.takeIf { url -> url.isNotBlank() },
            license = it.license?.takeIf { spdx -> spdx.isNotBlank() },
            diskBytes = it.diskBytes,
        )
    },
)

// endregion
