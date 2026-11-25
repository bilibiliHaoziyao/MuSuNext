package org.musu.next.ui.screen.safety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.musu.next.R
import org.musu.next.ui.component.statustag.StatusTag
import org.musu.next.ui.theme.LocalEnableBlur
import org.musu.next.ui.util.BlurredBar
import org.musu.next.ui.util.rememberBlurBackdrop
import org.musu.next.ui.viewmodel.SulogUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun SafetyEntryMiuix(
    auditStatus: String,
    envStatus: String,
    actions: SafetyEntryActions,
    bottomInnerPadding: Dp,
) {
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.safety),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 12.dp,
                end = padding.calculateEndPadding(layoutDirection) + 12.dp,
                top = padding.calculateTopPadding() + dynamicTopPadding + 8.dp,
                bottom = padding.calculateBottomPadding() + bottomInnerPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.safety_entry_desc),
                    color = colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            item(key = "audit") {
                Card(onClick = actions.onOpenAudit) {
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
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.safety_entry_audit_summary),
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = auditStatus,
                                fontSize = 13.sp,
                                color = colorScheme.primary,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            item(key = "envcheck") {
                Card(onClick = actions.onOpenEnvCheck) {
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
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.safety_entry_env_summary),
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = envStatus,
                                fontSize = 13.sp,
                                color = colorScheme.primary,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SafetyAuditMiuix(
    uiState: SulogUiState,
    actions: SafetyActions,
) {
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val stats = remember(uiState.entries) { aggregateAuditStats(uiState.entries) }
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.safety_audit_title),
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            val direction = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (direction == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 12.dp,
                end = padding.calculateEndPadding(layoutDirection) + 12.dp,
                top = padding.calculateTopPadding() + dynamicTopPadding + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "audit_state") {
                AuditStateCardMiuix(uiState, actions)
            }

            if (stats.isEmpty()) {
                if (!uiState.isLoading) {
                    item(key = "empty") {
                        Card {
                            Text(
                                text = stringResource(R.string.safety_empty),
                                color = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                }
            } else {
                item(key = "by_app_title") {
                    Text(
                        text = stringResource(R.string.safety_by_app),
                        color = colorScheme.primary,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                items(stats, key = { it.name }) { stat ->
                    AuditAppCardMiuix(stat)
                }
            }

            item(key = "full_log") {
                Card(onClick = actions.onOpenFullLog) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.safety_view_full_log),
                            color = colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditStateCardMiuix(
    uiState: SulogUiState,
    actions: SafetyActions,
) {
    Card {
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
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.safety_audit_title),
                        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
                StatusTag(
                    label = stringResource(
                        if (uiState.isSulogEnabled) R.string.safety_audit_enabled
                        else R.string.safety_audit_disabled
                    ),
                    backgroundColor = if (uiState.isSulogEnabled) colorScheme.primaryContainer
                    else colorScheme.primaryContainer,
                    contentColor = if (uiState.isSulogEnabled) colorScheme.onPrimaryContainer
                    else colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.safety_audit_summary),
                style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!uiState.isSulogEnabled) {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    text = stringResource(R.string.safety_audit_enable),
                    onClick = actions.onEnableSulog,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun AuditAppCardMiuix(stat: AuditAppStat) {
    Card {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stat.name,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = auditTypeSummary(stat),
                        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
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
    }
}
