package org.musu.next.ui.screen.builtin

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import org.musu.next.R
import org.musu.next.data.model.BuiltinComponent
import org.musu.next.data.model.BuiltinComponentId
import org.musu.next.data.model.BuiltinPhase
import org.musu.next.ui.component.WarningLevel
import java.text.NumberFormat

/**
 * All wording of the built-in component pages lives here, so that Material and Miuix only differ in
 * containers (§7) and the five subtitle prefixes of §3.3 stay in one place.
 */

/** StatusTag label per phase (§5.2). Null while probing: the page shows a skeleton instead. */
@StringRes
fun builtinTagLabelRes(phase: BuiltinPhase): Int? = when (phase) {
    BuiltinPhase.Probing -> null
    BuiltinPhase.Unsupported -> R.string.builtin_tag_unsupported
    BuiltinPhase.Off -> R.string.builtin_tag_off
    BuiltinPhase.PendingEnable, BuiltinPhase.PendingDisable -> R.string.builtin_tag_pending_reboot
    BuiltinPhase.Active -> R.string.builtin_tag_running
    BuiltinPhase.Failed -> R.string.builtin_tag_failed
    BuiltinPhase.BlockedByOther -> R.string.builtin_tag_blocked
    BuiltinPhase.ReadError -> R.string.builtin_tag_unknown
}

@Composable
fun builtinTitleText(id: BuiltinComponentId): String = when (id) {
    BuiltinComponentId.Zygisk -> stringResource(R.string.builtin_zygisk_title)
    BuiltinComponentId.Metamodule -> stringResource(R.string.builtin_meta_title)
}

/**
 * Setting-entry and switch-area subtitle. Only the five prefixes of §3.3 may start this string:
 * off / enabled-but-pending / running / occupied by someone / unreadable.
 */
@Composable
fun builtinSummaryText(component: BuiltinComponent): String = when (component.phase) {
    BuiltinPhase.Probing -> stringResource(R.string.builtin_probing)
    BuiltinPhase.Unsupported -> when (component.id) {
        BuiltinComponentId.Zygisk -> stringResource(R.string.builtin_zygisk_summary_unsupported)
        BuiltinComponentId.Metamodule -> stringResource(R.string.builtin_meta_summary_unsupported)
    }

    BuiltinPhase.Off -> when (component.id) {
        BuiltinComponentId.Zygisk -> stringResource(R.string.builtin_zygisk_summary_off)
        BuiltinComponentId.Metamodule -> stringResource(R.string.builtin_meta_summary_off)
    }

    BuiltinPhase.PendingEnable -> when (component.id) {
        BuiltinComponentId.Zygisk -> stringResource(R.string.builtin_zygisk_summary_pending)
        BuiltinComponentId.Metamodule -> stringResource(R.string.builtin_meta_summary_pending)
    }

    BuiltinPhase.PendingDisable -> when (component.id) {
        BuiltinComponentId.Zygisk -> stringResource(R.string.builtin_zygisk_summary_pending_disable)
        BuiltinComponentId.Metamodule -> stringResource(R.string.builtin_meta_summary_pending_disable)
    }

    BuiltinPhase.Active -> when (component.id) {
        BuiltinComponentId.Zygisk -> stringResource(
            R.string.builtin_zygisk_summary_active,
            component.modulesLoaded,
        )

        BuiltinComponentId.Metamodule -> stringResource(
            R.string.builtin_meta_summary_active,
            component.mountedModules,
            component.failedModules,
        )
    }

    BuiltinPhase.Failed -> stringResource(R.string.builtin_summary_failed)
    BuiltinPhase.ReadError -> stringResource(R.string.builtin_summary_unknown)
    BuiltinPhase.BlockedByOther -> conflictOwner(component)?.let { owner ->
        when (component.id) {
            BuiltinComponentId.Zygisk -> stringResource(R.string.builtin_zygisk_summary_blocked, owner)
            BuiltinComponentId.Metamodule -> stringResource(R.string.builtin_meta_summary_blocked, owner)
        }
    } ?: stringResource(R.string.builtin_tag_blocked)
}

/**
 * Rows of the status section (§4.2). B1 is always present; B2/B3 disappear when ksud gave no data,
 * and the conditional reboot row only appears when something is actually waiting for a reboot.
 */
@Composable
fun builtinStatusRows(component: BuiltinComponent): List<BuiltinRow> = buildList {
    val tagLabel = builtinTagLabelRes(component.phase)?.let { stringResource(it) }
    add(
        BuiltinRow(
            key = "status",
            title = stringResource(R.string.builtin_status_label),
            showTag = tagLabel != null,
        )
    )

    // Nothing but the state itself is known while probing or after a failed probe.
    if (component.phase == BuiltinPhase.Probing || component.phase == BuiltinPhase.ReadError) {
        return@buildList
    }

    when (component.id) {
        BuiltinComponentId.Zygisk -> {
            add(
                BuiltinRow(
                    key = "inject",
                    title = stringResource(R.string.builtin_zygisk_inject),
                    value = stringResource(zygiskInjectionRes(component)),
                    valueIsError = component.hasInjectionError(),
                )
            )
            add(
                BuiltinRow(
                    key = "modules",
                    title = stringResource(R.string.builtin_zygisk_modules),
                    value = if (component.modulesTotal == 0) {
                        stringResource(R.string.builtin_zygisk_modules_empty)
                    } else {
                        stringResource(
                            R.string.builtin_zygisk_modules_value,
                            component.modulesLoaded,
                            component.modulesTotal,
                        )
                    },
                )
            )
        }

        BuiltinComponentId.Metamodule -> {
            add(
                BuiltinRow(
                    key = "mount",
                    title = stringResource(R.string.builtin_meta_mount),
                    value = stringResource(
                        R.string.builtin_meta_mount_value,
                        component.mountedModules,
                        component.failedModules,
                    ),
                    valueIsError = component.failedModules > 0,
                )
            )
            if (component.scope.isNotEmpty()) {
                add(
                    BuiltinRow(
                        key = "scope",
                        title = stringResource(R.string.builtin_meta_scope),
                        value = component.scope.joinToString(" · "),
                        hint = stringResource(R.string.builtin_meta_scope_hint),
                    )
                )
            }
        }
    }

    if (component.needsReboot) {
        add(
            BuiltinRow(
                key = "reboot",
                title = stringResource(
                    if (component.id == BuiltinComponentId.Metamodule) {
                        R.string.builtin_reboot_needed_full
                    } else {
                        R.string.builtin_reboot_needed
                    }
                ),
                value = stringResource(rebootReasonRes(component)),
                actionLabel = stringResource(R.string.builtin_reboot_now),
                action = BuiltinRowAction.Reboot,
            )
        )
    }
}

/** Rows of the detail section (§4.4). A row whose value is unknown is dropped, never shown as "-". */
@Composable
fun builtinDetailRows(component: BuiltinComponent): List<BuiltinRow> = buildList {
    add(
        BuiltinRow(
            key = "type",
            title = stringResource(R.string.builtin_detail_type),
            value = stringResource(R.string.builtin_detail_type_value),
        )
    )
    val detail = component.detail ?: return@buildList

    detail.version?.let {
        add(
            BuiltinRow(
                key = "version",
                title = stringResource(R.string.builtin_detail_version),
                value = stringResource(R.string.builtin_detail_version_value, it),
            )
        )
    }
    detail.upstream?.let {
        add(
            BuiltinRow(
                key = "upstream",
                title = stringResource(R.string.builtin_detail_upstream),
                value = it,
                linkUrl = detail.upstreamUrl,
            )
        )
    }
    detail.license?.let {
        add(
            BuiltinRow(
                key = "license",
                title = stringResource(R.string.builtin_detail_license),
                value = it,
            )
        )
    }
    if (detail.diskBytes > 0L) {
        add(
            BuiltinRow(
                key = "size",
                title = stringResource(R.string.builtin_detail_size),
                value = formatDiskUsage(detail.diskBytes),
                hint = stringResource(R.string.builtin_detail_size_hint),
            )
        )
    }
}

/** Conflict / error card (§4.3). Only S5, S6, S7 and S9 produce one. */
@Composable
fun builtinNotice(component: BuiltinComponent): BuiltinNotice? = when (component.phase) {
    BuiltinPhase.BlockedByOther -> {
        val owner = conflictOwner(component)
        BuiltinNotice(
            level = WarningLevel.Error,
            message = if (owner == null) {
                stringResource(R.string.builtin_read_failed)
            } else {
                stringResource(
                    R.string.builtin_conflict_blocked_by,
                    component.conflictName.orEmpty(),
                    component.conflictVersion.orEmpty(),
                ).collapseBlankArgument()
            },
            actionLabel = stringResource(R.string.builtin_conflict_goto_modules),
            action = BuiltinRowAction.OpenModuleList,
        )
    }

    BuiltinPhase.Active -> if (!component.blockingOthers) {
        null
    } else {
        // S7 is not an error: we are running, someone else is merely blocked by us.
        BuiltinNotice(
            level = WarningLevel.Notice,
            message = stringResource(
                R.string.builtin_conflict_blocking,
                component.suppressed.joinToString(", "),
            ),
        )
    }

    BuiltinPhase.Failed -> BuiltinNotice(
        level = WarningLevel.Error,
        message = component.errorCode?.let {
            stringResource(
                R.string.builtin_failed,
                builtinTitleText(component.id),
                it,
            )
        } ?: stringResource(R.string.builtin_summary_failed),
        actionLabel = stringResource(R.string.builtin_view_log),
        action = BuiltinRowAction.ViewLog,
    )

    BuiltinPhase.ReadError -> BuiltinNotice(
        level = WarningLevel.Error,
        message = stringResource(R.string.builtin_read_failed),
        actionLabel = stringResource(R.string.builtin_retry),
        action = BuiltinRowAction.Retry,
    )

    else -> null
}

/** `<1 GB` keeps one decimal in MB, `>=1 GB` switches to GB (§4.4). */
@Composable
fun formatDiskUsage(bytes: Long): String {
    val format = remember {
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    }
    val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) {
        "${format.format(gib)} GB"
    } else {
        "${format.format(bytes.toDouble() / (1024.0 * 1024.0))} MB"
    }
}

/** "Zygisk Next 4.0.1"; the version is optional in the wire format. */
private fun conflictOwner(component: BuiltinComponent): String? {
    val name = component.conflictName ?: return null
    val version = component.conflictVersion
    return if (version.isNullOrBlank()) name else "$name $version"
}

@StringRes
private fun zygiskInjectionRes(component: BuiltinComponent): Int = when {
    component.hasInjectionError() -> R.string.builtin_zygisk_inject_error
    component.zygote64Injected == true -> R.string.builtin_zygisk_inject_ok
    component.zygote64Injected == false -> R.string.builtin_zygisk_inject_none
    else -> R.string.builtin_value_missing
}

/** 1501 / 1502 are the only codes that belong to the injection line, not to a whole failed component. */
private fun BuiltinComponent.hasInjectionError(): Boolean {
    val code = errorCode ?: return false
    return code in INJECTION_ERROR_CODES
}

@StringRes
private fun rebootReasonRes(component: BuiltinComponent): Int = when (component.id) {
    BuiltinComponentId.Zygisk -> if (component.phase == BuiltinPhase.PendingEnable) {
        R.string.builtin_reboot_value_zygisk_enable
    } else {
        R.string.builtin_reboot_value_zygisk_disable
    }

    BuiltinComponentId.Metamodule -> if (component.phase == BuiltinPhase.PendingEnable) {
        R.string.builtin_reboot_value_meta_enable
    } else {
        R.string.builtin_reboot_value_meta_disable
    }
}

/** `builtin_failed` and the conflict card repeat the module name; keep a missing argument invisible. */
private fun String.collapseBlankArgument(): String =
    replace(Regex("\\s+"), " ").replace(" 」", "」").trim()

private val INJECTION_ERROR_CODES = setOf(1501, 1502)
