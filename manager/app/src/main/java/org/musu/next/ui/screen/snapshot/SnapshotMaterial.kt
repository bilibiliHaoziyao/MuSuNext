package org.musu.next.ui.screen.snapshot

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.musu.next.R
import org.musu.next.ui.component.material.ExpressiveScaffold
import org.musu.next.ui.component.material.TonalCard
import org.musu.next.ui.component.material.TopBarBackButton
import org.musu.next.ui.component.material.expressiveTopAppBarColors

@Composable
fun SnapshotScreenMaterial(
    state: SnapshotUiState,
    actions: SnapshotActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var pendingRestore by remember { mutableStateOf<SnapshotInfo?>(null) }

    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.snapshot_restore_confirm_title)) },
            text = { Text(stringResource(R.string.snapshot_restore_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    val path = pendingRestore?.path ?: return@TextButton
                    pendingRestore = null
                    actions.onRestore(path)
                }) {
                    Text(stringResource(R.string.snapshot_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    val needReboot = state.restoreNeedReboot
    if (needReboot != null) {
        AlertDialog(
            onDismissRequest = actions.onDismissRestoreResult,
            title = { Text(stringResource(R.string.snapshot_restore_success)) },
            text = {
                Text(
                    stringResource(
                        if (needReboot) R.string.snapshot_need_reboot
                        else R.string.snapshot_restore_success_detail
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = actions.onDismissRestoreResult) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    val busyText = when (state.busy) {
        SnapshotBusy.Creating -> stringResource(R.string.snapshot_creating)
        SnapshotBusy.Restoring -> stringResource(R.string.snapshot_restoring)
        SnapshotBusy.Deleting -> stringResource(R.string.snapshot_deleting)
        SnapshotBusy.None -> null
    }

    ExpressiveScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.snapshot_title)) },
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
                            text = stringResource(R.string.snapshot_intro),
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = actions.onCreate,
                            enabled = state.busy == SnapshotBusy.None,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.snapshot_create))
                        }
                        state.createdPath?.let { path ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.snapshot_created_hint, path),
                                style = typography.bodySmall,
                                color = colorScheme.primary,
                            )
                        }
                        busyText?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = it,
                                style = typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            state.errorMessage?.let { message ->
                item(key = "error") {
                    TonalCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.snapshot_error, message),
                                style = typography.bodyMedium,
                                color = colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = actions.onRefresh) {
                                Text(stringResource(R.string.snapshot_retry))
                            }
                        }
                    }
                }
            }

            when {
                state.isLoading && state.snapshots.isEmpty() -> {
                    item(key = "loading") {
                        TonalCard {
                            Text(
                                text = stringResource(R.string.snapshot_loading),
                                style = typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                state.snapshots.isEmpty() -> {
                    item(key = "empty") {
                        TonalCard {
                            Text(
                                text = stringResource(R.string.snapshot_list_empty),
                                style = typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                else -> {
                    item(key = "list_title") {
                        Text(
                            text = stringResource(R.string.snapshot_list_title),
                            style = typography.titleSmallEmphasized,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    items(state.snapshots, key = { it.path }) { info ->
                        SnapshotItemCardMaterial(info, state.busy, actions, onRestoreRequest = { pendingRestore = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotItemCardMaterial(
    info: SnapshotInfo,
    busy: SnapshotBusy,
    actions: SnapshotActions,
    onRestoreRequest: (SnapshotInfo) -> Unit,
) {
    TonalCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = info.created ?: stringResource(R.string.snapshot_unknown_time),
                style = typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = snapshotCountsText(info),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.snapshot_size, snapshotSizeText(info.size)),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onRestoreRequest(info) },
                    enabled = busy == SnapshotBusy.None,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.snapshot_restore))
                }
                TextButton(
                    onClick = { actions.onDelete(info.path) },
                    enabled = busy == SnapshotBusy.None,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.snapshot_delete))
                }
            }
        }
    }
}
