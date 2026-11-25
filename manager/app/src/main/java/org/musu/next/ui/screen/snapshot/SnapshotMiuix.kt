package org.musu.next.ui.screen.snapshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.musu.next.R
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
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun SnapshotScreenMiuix(
    state: SnapshotUiState,
    actions: SnapshotActions,
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
    var pendingRestore by remember { mutableStateOf<SnapshotInfo?>(null) }

    OverlayDialog(
        show = pendingRestore != null,
        title = stringResource(R.string.snapshot_restore_confirm_title),
        onDismissRequest = { pendingRestore = null },
        content = {
            Text(
                text = stringResource(R.string.snapshot_restore_confirm_text),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(android.R.string.cancel),
                    onClick = { pendingRestore = null },
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.snapshot_restore_confirm),
                    onClick = {
                        val path = pendingRestore?.path ?: return@TextButton
                        pendingRestore = null
                        actions.onRestore(path)
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )

    val needReboot = state.restoreNeedReboot
    OverlayDialog(
        show = needReboot != null,
        title = stringResource(R.string.snapshot_restore_success),
        onDismissRequest = actions.onDismissRestoreResult,
        content = {
            Text(
                text = stringResource(
                    if (needReboot == true) R.string.snapshot_need_reboot
                    else R.string.snapshot_restore_success_detail
                ),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(android.R.string.ok),
                onClick = actions.onDismissRestoreResult,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        },
    )

    val busyText = when (state.busy) {
        SnapshotBusy.Creating -> stringResource(R.string.snapshot_creating)
        SnapshotBusy.Restoring -> stringResource(R.string.snapshot_restoring)
        SnapshotBusy.Deleting -> stringResource(R.string.snapshot_deleting)
        SnapshotBusy.None -> null
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.snapshot_title),
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.snapshot_intro),
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            text = stringResource(R.string.snapshot_create),
                            onClick = actions.onCreate,
                            enabled = state.busy == SnapshotBusy.None,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        state.createdPath?.let { path ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.snapshot_created_hint, path),
                                fontSize = 13.sp,
                                color = colorScheme.primary,
                            )
                        }
                        busyText?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = it,
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }

            state.errorMessage?.let { message ->
                item(key = "error") {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.snapshot_error, message),
                                fontSize = 14.sp,
                                color = colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                text = stringResource(R.string.snapshot_retry),
                                onClick = actions.onRefresh,
                            )
                        }
                    }
                }
            }

            when {
                state.isLoading && state.snapshots.isEmpty() -> {
                    item(key = "loading") {
                        Card {
                            Text(
                                text = stringResource(R.string.snapshot_loading),
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                state.snapshots.isEmpty() -> {
                    item(key = "empty") {
                        Card {
                            Text(
                                text = stringResource(R.string.snapshot_list_empty),
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                else -> {
                    item(key = "list_title") {
                        SmallTitle(
                            text = stringResource(R.string.snapshot_list_title),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    items(state.snapshots, key = { it.path }) { info ->
                        SnapshotItemCardMiuix(info, state.busy, actions, onRestoreRequest = { pendingRestore = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotItemCardMiuix(
    info: SnapshotInfo,
    busy: SnapshotBusy,
    actions: SnapshotActions,
    onRestoreRequest: (SnapshotInfo) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = info.created ?: stringResource(R.string.snapshot_unknown_time),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = snapshotCountsText(info),
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = stringResource(R.string.snapshot_size, snapshotSizeText(info.size)),
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.snapshot_restore),
                    onClick = { onRestoreRequest(info) },
                    enabled = busy == SnapshotBusy.None,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.snapshot_delete),
                    onClick = { actions.onDelete(info.path) },
                    enabled = busy == SnapshotBusy.None,
                )
            }
        }
    }
}
