package org.musu.next.ui.screen.dirtyfrag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.musu.next.R
import org.musu.next.ui.component.material.ExpressiveScaffold
import org.musu.next.ui.component.material.TonalCard
import org.musu.next.ui.component.material.TopBarBackButton
import org.musu.next.ui.component.material.expressiveTopAppBarColors

@Composable
fun DirtyFragScreenMaterial(
    state: DirtyFragUiState,
    actions: DirtyFragActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dirtyfrag_title)) },
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
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
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                TonalCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.dirtyfrag_intro),
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when (state.phase) {
                DfPhase.Idle, DfPhase.Probing -> item(key = "probing") {
                    TonalCard {
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
                            Text(stringResource(R.string.dirtyfrag_probing), style = typography.bodyMedium)
                        }
                    }
                }

                else -> {
                    state.detect?.let { detect ->
                        item(key = "compat") { CompatCardMaterial(detect) }
                        if (detect.risks.isNotEmpty()) {
                            item(key = "risks") { RiskCardMaterial(detect.risks) }
                        }
                    }
                    when (state.phase) {
                        DfPhase.Ready -> item(key = "action") { ReadyActionMaterial(actions) }
                        DfPhase.Unsupported -> item(key = "unsupported") {
                            NoticeCardMaterial(stringResource(R.string.dirtyfrag_unsupported_hint))
                        }
                        DfPhase.ConfirmRisk -> item(key = "confirm") { ConfirmCardMaterial(state, actions) }
                        DfPhase.Running -> item(key = "running") { RunningCardMaterial(state) }
                        DfPhase.Succeeded -> item(key = "succeeded") { SucceededCardMaterial(state, actions) }
                        DfPhase.Failed -> item(key = "failed") { FailedCardMaterial(state, actions) }
                        DfPhase.AlreadyRooted -> item(key = "rooted") { AlreadyRootedCardMaterial(actions) }
                        DfPhase.ReadError -> item(key = "readerror") { ReadErrorCardMaterial(state, actions) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompatCardMaterial(detect: DfDetect) {
    TonalCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRowMaterial(Icons.Filled.Memory, stringResource(R.string.dirtyfrag_kmi), detect.device.kmi)
            InfoRowMaterial(
                Icons.Filled.Description,
                stringResource(R.string.dirtyfrag_kernel),
                detect.device.kernelRelease,
            )
            InfoRowMaterial(
                Icons.Filled.Android,
                stringResource(R.string.dirtyfrag_android),
                detect.device.androidRelease,
            )
            InfoRowMaterial(Icons.Filled.Security, stringResource(R.string.dirtyfrag_abi), detect.device.abi)
            InfoRowMaterial(
                Icons.Filled.Security,
                stringResource(R.string.dirtyfrag_selinux),
                detect.device.selinux,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.dirtyfrag_verdict_line,
                    dfVerdictLabel(detect.verdict),
                ),
                style = typography.titleSmall,
                color = colorScheme.primary,
            )
            detect.reasons.forEach { reason ->
                val isBlocker = reason.isBlocker
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = if (isBlocker) Icons.Rounded.Warning else Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isBlocker) colorScheme.error else colorScheme.tertiary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text(
                            text = dfReasonLabel(reason.code),
                            style = typography.labelLarge,
                            color = if (isBlocker) colorScheme.error else colorScheme.tertiary,
                        )
                        // Backend message is a concrete cause; hide the row when absent.
                        if (reason.message.isNotBlank()) {
                            Text(
                                text = reason.message,
                                style = typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
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
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoRowMaterial(icon: ImageVector, label: String, value: String?) {
    val text = value?.takeIf { it.isNotBlank() } ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(10.dp))
        Text(text = label, style = typography.bodyMedium, color = colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        Text(text = text, style = typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RiskCardMaterial(risks: List<String>) {
    TonalCard(containerColor = colorScheme.errorContainer) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.dirtyfrag_risk_title),
                style = typography.titleSmall,
                color = colorScheme.onErrorContainer,
            )
            risks.forEach { token ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = dfRiskLabel(token),
                        style = typography.bodyMedium,
                        color = colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyActionMaterial(actions: DirtyFragActions) {
    Button(
        onClick = actions.onJailbreak,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.error,
            contentColor = colorScheme.onError,
        ),
    ) {
        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.dirtyfrag_action))
    }
}

@Composable
private fun ConfirmCardMaterial(state: DirtyFragUiState, actions: DirtyFragActions) {
    TonalCard(containerColor = colorScheme.errorContainer) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.dirtyfrag_confirm_title),
                style = typography.titleMedium,
                color = colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.dirtyfrag_confirm_body),
                style = typography.bodyMedium,
                color = colorScheme.onErrorContainer,
            )
            state.detect?.risks?.forEach { token ->
                Text(
                    text = "· " + dfRiskLabel(token),
                    style = typography.bodySmall,
                    color = colorScheme.onErrorContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = actions.onCancel) {
                    Text(stringResource(R.string.dirtyfrag_confirm_cancel))
                }
                Button(
                    onClick = actions.onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.dirtyfrag_confirm_accept))
                }
            }
        }
    }
}

@Composable
private fun RunningCardMaterial(state: DirtyFragUiState) {
    TonalCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dirtyfrag_running_title), style = typography.titleSmall)
            Text(
                text = stringResource(R.string.dirtyfrag_running_hint),
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
            // Stage names only — no percentage (spec §11 / U9: 0% is a log-mapping artifact).
            val stages = state.run?.stages.orEmpty()
            if (stages.isEmpty()) {
                listOf("detect", "stage_ksud", "engine_exec", "verify_exposed").forEach { name ->
                    StageRowMaterial(dfStageLabel(name), done = false, active = false)
                }
            } else {
                stages.forEach { stage -> StageRowMaterial(dfStageLabel(stage.name), stage.ok, active = false) }
            }
        }
    }
}

@Composable
private fun StageRowMaterial(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (done) Icons.Rounded.CheckCircle else Icons.Filled.Security,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (done) colorScheme.primary else colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = typography.bodyMedium,
            color = if (active || done) colorScheme.onSurface else colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SucceededCardMaterial(state: DirtyFragUiState, actions: DirtyFragActions) {
    TonalCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.dirtyfrag_success),
                    style = typography.titleSmall,
                    color = colorScheme.primary,
                )
            }
            val next = state.run?.next
            if (next == "no_reboot_needed") {
                Text(
                    text = stringResource(R.string.dirtyfrag_no_reboot),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            val note = state.status?.riskNote
            if (note != null) {
                Text(
                    text = dfRiskLabel(note),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = actions.onRetry) {
                Text(stringResource(R.string.dirtyfrag_recheck))
            }
        }
    }
}

@Composable
private fun FailedCardMaterial(state: DirtyFragUiState, actions: DirtyFragActions) {
    TonalCard(containerColor = colorScheme.errorContainer) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.dirtyfrag_failed_title),
                style = typography.titleSmall,
                color = colorScheme.onErrorContainer,
            )
            Text(
                text = dfFailureMessage(state),
                style = typography.bodyMedium,
                color = colorScheme.onErrorContainer,
            )
            if (state.errorCode == DF_EXPLOIT_FAILED) {
                Text(
                    text = stringResource(R.string.dirtyfrag_error_2300_hint),
                    style = typography.bodySmall,
                    color = colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = actions.onRetry) {
                Text(stringResource(R.string.dirtyfrag_retry))
            }
        }
    }
}

@Composable
private fun AlreadyRootedCardMaterial(actions: DirtyFragActions) {
    TonalCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.dirtyfrag_already_rooted), style = typography.titleSmall)
            TextButton(onClick = actions.onRetry) {
                Text(stringResource(R.string.dirtyfrag_recheck))
            }
        }
    }
}

@Composable
private fun ReadErrorCardMaterial(state: DirtyFragUiState, actions: DirtyFragActions) {
    TonalCard(containerColor = colorScheme.errorContainer) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (state.busy) stringResource(R.string.dirtyfrag_busy) else dfFailureMessage(state),
                style = typography.bodyMedium,
                color = colorScheme.onErrorContainer,
            )
            TextButton(onClick = actions.onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.dirtyfrag_retry))
            }
        }
    }
}

@Composable
private fun NoticeCardMaterial(text: String) {
    TonalCard {
        Text(
            text = text,
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
