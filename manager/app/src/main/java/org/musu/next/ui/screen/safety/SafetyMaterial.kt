package org.musu.next.ui.screen.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.musu.next.R
import org.musu.next.ui.component.material.ExpressiveScaffold
import org.musu.next.ui.component.material.SegmentedColumn
import org.musu.next.ui.component.material.SegmentedListItem
import org.musu.next.ui.component.material.TonalCard
import org.musu.next.ui.component.material.TopBarBackButton
import org.musu.next.ui.component.material.expressiveTopAppBarColors
import org.musu.next.ui.component.statustag.StatusTag
import org.musu.next.ui.viewmodel.SulogUiState

@Composable
fun SafetyEntryMaterial(
    auditStatus: String,
    envStatus: String,
    actions: SafetyEntryActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.safety)) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + bottomInnerPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.safety_entry_desc),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            item(key = "audit") {
                TonalCard(onClick = actions.onOpenAudit) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.safety_entry_audit_title),
                                style = typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.safety_entry_audit_summary),
                                style = typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = auditStatus,
                                style = typography.labelMedium,
                                color = colorScheme.primary,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "envcheck") {
                TonalCard(onClick = actions.onOpenEnvCheck) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.safety_entry_env_title),
                                style = typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.safety_entry_env_summary),
                                style = typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = envStatus,
                                style = typography.labelMedium,
                                color = colorScheme.primary,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SafetyAuditMaterial(
    uiState: SulogUiState,
    actions: SafetyActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val stats = remember(uiState.entries) { aggregateAuditStats(uiState.entries) }

    ExpressiveScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.safety_audit_title)) },
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "audit_state") {
                    AuditStateCardMaterial(uiState, actions)
                }

                if (stats.isEmpty()) {
                    if (!uiState.isLoading) {
                        item(key = "empty") {
                            TonalCard {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = stringResource(R.string.safety_empty),
                                        style = typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item(key = "by_app_title") {
                        Text(
                            text = stringResource(R.string.safety_by_app),
                            style = typography.titleSmallEmphasized,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    items(stats, key = { it.name }) { stat ->
                        TonalCard {
                            SegmentedColumn(
                                content = listOf {
                                    SegmentedListItem(
                                        headlineContent = {
                                            Text(
                                                text = stat.name,
                                                style = typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        },
                                        supportingContent = {
                                            Column {
                                                Text(
                                                    text = auditTypeSummary(stat),
                                                    style = typography.bodySmall,
                                                    color = colorScheme.onSurfaceVariant,
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (stat.rootExecve > 0) {
                                                        StatusTag(
                                                            label = "${stringResource(R.string.sulog_filter_root_execve)} ${stat.rootExecve}",
                                                            backgroundColor = colorScheme.primaryContainer,
                                                            contentColor = colorScheme.onPrimaryContainer,
                                                        )
                                                    }
                                                    if (stat.sucompat > 0) {
                                                        StatusTag(
                                                            label = "${stringResource(R.string.sulog_filter_sucompat)} ${stat.sucompat}",
                                                            backgroundColor = colorScheme.secondaryContainer,
                                                            contentColor = colorScheme.onSecondaryContainer,
                                                        )
                                                    }
                                                    if (stat.ioctlGrantRoot > 0) {
                                                        StatusTag(
                                                            label = "${stringResource(R.string.sulog_filter_ioctl_grant_root)} ${stat.ioctlGrantRoot}",
                                                            backgroundColor = colorScheme.tertiaryContainer,
                                                            contentColor = colorScheme.onTertiaryContainer,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        leadingContent = {
                                            Icon(
                                                imageVector = Icons.Filled.History,
                                                contentDescription = null,
                                                tint = colorScheme.primary,
                                            )
                                        },
                                    )
                                }
                            )
                        }
                    }
                }

                item(key = "full_log") {
                    TonalCard(onClick = actions.onOpenFullLog) {
                        SegmentedColumn(
                            content = listOf {
                                SegmentedListItem(
                                    headlineContent = {
                                        Text(stringResource(R.string.safety_view_full_log))
                                    },
                                    leadingContent = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                        )
                                    },
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditStateCardMaterial(
    uiState: SulogUiState,
    actions: SafetyActions,
) {
    TonalCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.HealthAndSafety,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (uiState.isSulogEnabled) R.string.safety_audit_enabled
                            else R.string.safety_audit_disabled
                        ),
                        style = typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.safety_audit_title),
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
                StatusTag(
                    label = stringResource(
                        if (uiState.isSulogEnabled) R.string.safety_audit_enabled
                        else R.string.safety_audit_disabled
                    ),
                    backgroundColor = if (uiState.isSulogEnabled) colorScheme.primaryContainer
                    else colorScheme.errorContainer,
                    contentColor = if (uiState.isSulogEnabled) colorScheme.onPrimaryContainer
                    else colorScheme.onErrorContainer,
                )
            }
            Text(
                text = stringResource(R.string.safety_audit_summary),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!uiState.isSulogEnabled) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = actions.onEnableSulog) {
                    Text(stringResource(R.string.safety_audit_enable))
                }
            }
        }
    }
}
