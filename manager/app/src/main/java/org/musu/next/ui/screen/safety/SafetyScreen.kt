package org.musu.next.ui.screen.safety

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.musu.next.R
import org.musu.next.ui.LocalUiMode
import org.musu.next.ui.UiMode
import org.musu.next.ui.navigation3.LocalNavigator
import org.musu.next.ui.navigation3.Route
import org.musu.next.ui.util.SulogEntry
import org.musu.next.ui.util.SulogEventType
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
    val onEnableSulog: () -> Unit,
    val onRefresh: () -> Unit,
    val onOpenFullLog: () -> Unit,
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

@Composable
fun SafetyPager(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<SulogViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)

    LaunchedEffect(isCurrentPage) {
        if (latestIsCurrentPage) {
            viewModel.refreshLatest()
        }
    }

    val actions = SafetyActions(
        onEnableSulog = viewModel::enableSulog,
        onRefresh = viewModel::refreshLatest,
        onOpenFullLog = { navigator.push(Route.Sulog) },
    )

    when (uiMode) {
        UiMode.Material -> SafetyPagerMaterial(uiState, actions, bottomInnerPadding)
        UiMode.Miuix -> SafetyPagerMiuix(uiState, actions, bottomInnerPadding)
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
