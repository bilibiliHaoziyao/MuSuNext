package org.musu.next.ui.screen.dirtyfrag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import org.musu.next.ui.LocalUiMode
import org.musu.next.ui.UiMode
import org.musu.next.ui.navigation3.LocalNavigator

/** Screen-level actions; theme implementations receive these and render identical state/copy. */
data class DirtyFragActions(
    val onBack: () -> Unit,
    val onProbe: () -> Unit,
    val onJailbreak: () -> Unit,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
    val onRetry: () -> Unit,
)

@Composable
fun DirtyFragScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<DirtyFragViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val actions = DirtyFragActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onProbe = viewModel::probe,
        onJailbreak = viewModel::requestJailbreak,
        onConfirm = viewModel::run,
        onCancel = viewModel::cancelRisk,
        onRetry = viewModel::retry,
    )

    when (LocalUiMode.current) {
        UiMode.Material -> DirtyFragScreenMaterial(uiState, actions)
        UiMode.Miuix -> DirtyFragScreenMiuix(uiState, actions)
    }
}
