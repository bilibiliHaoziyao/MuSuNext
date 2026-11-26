package org.musu.next.ui.screen.dirtyfrag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.musu.next.R
import org.musu.next.ui.component.WarningLevel
import org.musu.next.ui.component.miuix.WarningCard
import org.musu.next.ui.theme.LocalEnableBlur
import org.musu.next.ui.util.BlurredBar
import org.musu.next.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun DirtyFragScreenMiuix(
    state: DirtyFragUiState,
    actions: DirtyFragActions,
) {
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.dirtyfrag_title),
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
            item(key = "intro") {
                Card {
                    Text(
                        text = stringResource(R.string.dirtyfrag_intro),
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            when (state.phase) {
                DfPhase.Idle, DfPhase.Probing -> item(key = "probing") {
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = colorScheme.primary,
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                text = stringResource(R.string.dirtyfrag_probing),
                                fontSize = 14.sp,
                                color = colorScheme.onSurface,
                            )
                        }
                    }
                }

                else -> {
                    state.detect?.let { detect ->
                        item(key = "compat") { CompatCardMiuix(detect) }
                        if (detect.risks.isNotEmpty()) {
                            item(key = "risks") { RiskCardMiuix(detect.risks) }
                        }
                    }
                    when (state.phase) {
                        DfPhase.Ready -> item(key = "action") {
                            Card {
                                TextButton(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    text = stringResource(R.string.dirtyfrag_action),
                                    onClick = actions.onJailbreak,
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                            }
                        }

                        DfPhase.Unsupported -> item(key = "unsupported") {
                            Card {
                                Text(
                                    text = stringResource(R.string.dirtyfrag_unsupported_hint),
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }

                        DfPhase.ConfirmRisk -> item(key = "confirm") { ConfirmCardMiuix(state, actions) }
                        DfPhase.Running -> item(key = "running") { RunningCardMiuix(state) }
                        DfPhase.Succeeded -> item(key = "succeeded") { SucceededCardMiuix(state, actions) }
                        DfPhase.Failed -> item(key = "failed") { FailedCardMiuix(state, actions) }
                        DfPhase.AlreadyRooted -> item(key = "rooted") { AlreadyRootedCardMiuix(actions) }
                        DfPhase.ReadError -> item(key = "readerror") { ReadErrorCardMiuix(state, actions) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompatCardMiuix(detect: DfDetect) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRowMiuix(Icons.Filled.Memory, stringResource(R.string.dirtyfrag_kmi), detect.device.kmi)
            InfoRowMiuix(
                Icons.Filled.Description,
                stringResource(R.string.dirtyfrag_kernel),
                detect.device.kernelRelease,
            )
            InfoRowMiuix(
                Icons.Filled.Android,
                stringResource(R.string.dirtyfrag_android),
                detect.device.androidRelease,
            )
            InfoRowMiuix(Icons.Filled.Security, stringResource(R.string.dirtyfrag_abi), detect.device.abi)
            InfoRowMiuix(
                Icons.Filled.Security,
                stringResource(R.string.dirtyfrag_selinux),
                detect.device.selinux,
            )
            SmallTitle(
                text = stringResource(R.string.dirtyfrag_verdict_line, dfVerdictLabel(detect.verdict)),
                modifier = Modifier.padding(top = 2.dp),
            )
            detect.reasons.forEach { reason ->
                val isBlocker = reason.isBlocker
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = if (isBlocker) Icons.Rounded.Warning else Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isBlocker) colorScheme.error else colorScheme.primary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text(
                            text = dfReasonLabel(reason.code),
                            fontSize = 14.sp,
                            color = if (isBlocker) colorScheme.error else colorScheme.onSurface,
                        )
                        if (reason.message.isNotBlank()) {
                            Text(
                                text = reason.message,
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
            val info = detect.info
            val source = buildList {
                info.payloadVersion?.let { add(stringResource(R.string.dirtyfrag_payload_version, it)) }
                info.payloadLicense?.let { add(it) }
                info.enginePackage?.let { add(it) }
            }.joinToString(" · ")
            if (source.isNotBlank()) {
                Text(
                    text = stringResource(R.string.dirtyfrag_source_line, source),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun InfoRowMiuix(icon: ImageVector, label: String, value: String?) {
    val text = value?.takeIf { it.isNotBlank() } ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.size(10.dp))
        Text(text = label, fontSize = 14.sp, color = colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.size(8.dp))
        Text(text = text, fontSize = 14.sp, color = colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RiskCardMiuix(risks: List<String>) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.dirtyfrag_risk_title),
                fontSize = 15.sp,
                color = colorScheme.error,
            )
            risks.forEach { token ->
                Text(
                    text = "· " + dfRiskLabel(token),
                    fontSize = 14.sp,
                    color = colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ConfirmCardMiuix(state: DirtyFragUiState, actions: DirtyFragActions) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.dirtyfrag_confirm_title),
                fontSize = 16.sp,
                color = colorScheme.error,
            )
            Text(
                text = stringResource(R.string.dirtyfrag_confirm_body),
                fontSize = 14.sp,
                color = colorScheme.onSurface,
            )
            state.detect?.risks?.forEach { token ->
                Text(text = "· " + dfRiskLabel(token), fontSize = 13.sp, color = colorScheme.onSurface)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.dirtyfrag_confirm_cancel),
                    onClick = actions.onCancel,
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.dirtyfrag_confirm_accept),
                    onClick = actions.onConfirm,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun RunningCardMiuix(state: DirtyFragUiState) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.dirtyfrag_running_title), fontSize = 15.sp)
            Text(
                text = stringResource(R.string.dirtyfrag_running_hint),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            val stages = state.run?.stages.orEmpty()
            if (stages.isEmpty()) {
                listOf("detect", "stage_ksud", "engine_exec", "verify_exposed").forEach { name ->
                    StageRowMiuix(dfStageLabel(name), false)
                }
            } else {
                stages.forEach { stage -> StageRowMiuix(dfStageLabel(stage.name), stage.ok) }
            }
        }
    }
}

@Composable
private fun StageRowMiuix(label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (done) Icons.Rounded.CheckCircle else Icons.Filled.Security,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (done) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.size(8.dp))
        Text(text = label, fontSize = 14.sp, color = colorScheme.onSurface)
    }
}

@Composable
private fun SucceededCardMiuix(state: DirtyFragUiState, actions: DirtyFragActions) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.dirtyfrag_success),
                    fontSize = 15.sp,
                    color = colorScheme.primary,
                )
            }
            if (state.run?.next == "no_reboot_needed") {
                Text(
                    text = stringResource(R.string.dirtyfrag_no_reboot),
                    fontSize = 14.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            state.status?.riskNote?.let { note ->
                Text(text = dfRiskLabel(note), fontSize = 12.sp, color = colorScheme.onSurfaceVariantSummary)
            }
            TextButton(
                text = stringResource(R.string.dirtyfrag_recheck),
                onClick = actions.onRetry,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun FailedCardMiuix(state: DirtyFragUiState, actions: DirtyFragActions) {
    WarningCard(
        message = dfFailureMessage(state),
        level = WarningLevel.Error,
        action = {
            TextButton(text = stringResource(R.string.dirtyfrag_retry), onClick = actions.onRetry)
        },
    )
    if (state.errorCode == DF_EXPLOIT_FAILED) {
        SmallTitle(
            text = stringResource(R.string.dirtyfrag_error_2300_hint),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun AlreadyRootedCardMiuix(actions: DirtyFragActions) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = stringResource(R.string.dirtyfrag_already_rooted), fontSize = 15.sp)
            TextButton(
                text = stringResource(R.string.dirtyfrag_recheck),
                onClick = actions.onRetry,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun ReadErrorCardMiuix(state: DirtyFragUiState, actions: DirtyFragActions) {
    val message = if (state.busy) {
        stringResource(R.string.dirtyfrag_busy)
    } else {
        dfFailureMessage(state)
    }
    WarningCard(
        message = message,
        level = WarningLevel.Error,
        action = {
            TextButton(
                text = stringResource(R.string.dirtyfrag_retry),
                onClick = actions.onRetry,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        },
    )
}
