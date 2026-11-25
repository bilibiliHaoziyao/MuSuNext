package org.musu.next.ui.screen.snapshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import org.musu.next.R
import org.musu.next.ui.LocalUiMode
import org.musu.next.ui.UiMode
import org.musu.next.ui.navigation3.LocalNavigator
import org.musu.next.ui.viewmodel.SnapshotViewModel

data class SnapshotActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onCreate: () -> Unit,
    val onRestore: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onDismissRestoreResult: () -> Unit,
)

@Composable
fun SnapshotScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<SnapshotViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val actions = SnapshotActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onRefresh = viewModel::refresh,
        onCreate = viewModel::create,
        onRestore = viewModel::restore,
        onDelete = viewModel::delete,
        onDismissRestoreResult = viewModel::dismissRestoreResult,
    )

    when (LocalUiMode.current) {
        UiMode.Material -> SnapshotScreenMaterial(uiState, actions)
        UiMode.Miuix -> SnapshotScreenMiuix(uiState, actions)
    }
}

@Composable
fun snapshotSizeText(bytes: Long): String = when {
    bytes < 1024L -> stringResource(R.string.snapshot_size_bytes, bytes)
    bytes < 1024L * 1024L -> stringResource(R.string.snapshot_size_kb, bytes / 1024f)
    else -> stringResource(R.string.snapshot_size_mb, bytes / (1024f * 1024f))
}

@Composable
fun snapshotCountsText(info: SnapshotInfo): String =
    stringResource(R.string.snapshot_counts, info.modules, info.profiles)
