package org.musu.next.ui.screen.safety

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import org.musu.next.R
import org.musu.next.ui.LocalUiMode
import org.musu.next.ui.UiMode
import org.musu.next.ui.navigation3.LocalNavigator
import org.musu.next.ui.navigation3.Route
import org.musu.next.ui.screen.envcheck.EnvCheckUiState
import org.musu.next.ui.util.SulogEntry
import org.musu.next.ui.util.SulogEventType
import org.musu.next.ui.viewmodel.EnvCheckViewModel
import org.musu.next.ui.viewmodel.SulogUiState
import org.musu.next.ui.viewmodel.SulogViewModel

/** Aggregated audit statistics for one app (identified by comm / exe name). */
data class AuditAppStat(
    val name: String,
    val total: Int,
    val rootExecve: Int,
    val sucompat: Int,
    val ioctlGrantRoot: Int,
    val lastTimestampText: String?,
)

data class SafetyActions(
    val onBack: () -> Unit,
    val onEnableSulog: () -> Unit,
    val onRefresh: () -> Unit,
    val onOpenFullLog: () -> Unit,
)

data class SafetyEntryActions(
    val onOpenAudit: () -> Unit,
    val onOpenEnvCheck: () -> Unit,
)

/**
 * Aggregate sulog entries into per-app audit stats.
 * Daemon events / dropped counters are global entries without an app identity, skip them.
 */
fun aggregateAuditStats(entries: List<SulogEntry>): List<AuditAppStat> {
    val grouped = LinkedHashMap<String, MutableList<SulogEntry>>()
    for (entry in entries) {
        when (entry.eventType) {
            SulogEventType.RootExecve,
            SulogEventType.SuCompat,
            SulogEventType.IoctlGrantRoot -> Unit

            else -> continue
        }
        val name = entry.fields["comm"]
            ?: entry.fields["exe"]?.substringAfterLast('/')
            ?: continue
        grouped.getOrPut(name) { mutableListOf() }.add(entry)
    }
    return grouped.map { (name, list) ->
        AuditAppStat(
            name = name,
            total = list.size,
            rootExecve = list.count { it.eventType == SulogEventType.RootExecve },
            sucompat = list.count { it.eventType == SulogEventType.SuCompat },
            ioctlGrantRoot = list.count { it.eventType == SulogEventType.IoctlGrantRoot },
            lastTimestampText = list.lastOrNull()?.timestampText,
        )
    }.sortedByDescending { it.total }
}

/** Security tab: an entry page with two destinations, not the audit list itself. */
@Composable
fun SafetyPager(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val sulogViewModel = viewModel<SulogViewModel>()
    val sulogState by sulogViewModel.uiState.collectAsStateWithLifecycle()
    val envViewModel = viewModel<EnvCheckViewModel>()
    val envState by envViewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)

    LaunchedEffect(isCurrentPage) {
        if (latestIsCurrentPage) {
            sulogViewModel.refreshLatest()
            envViewModel.requestPreview()
        }
    }

    val auditCalls = remember(sulogState.entries) {
        aggregateAuditStats(sulogState.entries).sumOf { it.total }
    }
    val auditStatus = safetyAuditStatusText(sulogState.isSulogEnabled, auditCalls)
    val envStatus = safetyEnvStatusText(envState)

    val actions = SafetyEntryActions(
        onOpenAudit = { navigator.push(Route.SafetyAudit) },
        onOpenEnvCheck = { navigator.push(Route.EnvCheck) },
    )

    when (uiMode) {
        UiMode.Material -> SafetyEntryMaterial(
            auditStatus = auditStatus,
            envStatus = envStatus,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Miuix -> SafetyEntryMiuix(
            auditStatus = auditStatus,
            envStatus = envStatus,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}

/** Audit sub-page, reached through [Route.SafetyAudit]. */
@Composable
fun SafetyAuditScreen() {
    val viewModel = viewModel<SulogViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) {
        viewModel.refreshLatest()
    }

    val actions = SafetyActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onEnableSulog = viewModel::enableSulog,
        onRefresh = viewModel::refreshLatest,
        onOpenFullLog = { navigator.push(Route.Sulog) },
    )

    when (LocalUiMode.current) {
        UiMode.Material -> SafetyAuditMaterial(uiState, actions)
        UiMode.Miuix -> SafetyAuditMiuix(uiState, actions)
    }
}

@Composable
fun safetyAuditStatusText(isSulogEnabled: Boolean, calls: Int): String {
    return when {
        !isSulogEnabled -> stringResource(R.string.safety_audit_disabled)
        calls > 0 -> stringResource(R.string.safety_entry_audit_status, calls)
        else -> stringResource(R.string.safety_entry_audit_status_empty)
    }
}

@Composable
fun safetyEnvStatusText(state: EnvCheckUiState): String {
    val result = state.result
    return when {
        state.isChecking -> stringResource(R.string.envcheck_checking)
        result?.ok == true -> {
            val count = result.effectiveExposedCount
            if (count > 0) {
                stringResource(R.string.safety_entry_env_status_exposed, count)
            } else {
                stringResource(R.string.safety_entry_env_status_safe)
            }
        }

        result != null && !result.ok -> {
            stringResource(
                R.string.safety_entry_env_status_error,
                result.error ?: stringResource(R.string.envcheck_error_unknown),
            )
        }

        state.errorMessage != null -> {
            stringResource(R.string.safety_entry_env_status_error, state.errorMessage.orEmpty())
        }

        else -> stringResource(R.string.safety_entry_env_status_pending)
    }
}

@Composable
fun auditTypeSummary(stat: AuditAppStat): String {
    return stringResource(
        if (stat.lastTimestampText != null) {
            R.string.safety_audit_app_calls
        } else {
            R.string.safety_audit_no_time
        },
        stat.total,
        stat.lastTimestampText ?: "",
    )
}
