package org.musu.next.ui.screen.envcheck

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import org.musu.next.R
import org.musu.next.ui.LocalUiMode
import org.musu.next.ui.UiMode
import org.musu.next.ui.navigation3.LocalNavigator
import org.musu.next.ui.viewmodel.EnvCheckViewModel

data class EnvCheckActions(
    val onBack: () -> Unit,
    val onCheck: () -> Unit,
)

@Composable
fun EnvCheckScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<EnvCheckViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // A result already fetched by the Security entry page is reused; no second probe on entry.
    val actions = EnvCheckActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onCheck = viewModel::check,
    )

    when (LocalUiMode.current) {
        UiMode.Material -> EnvCheckScreenMaterial(uiState, actions)
        UiMode.Miuix -> EnvCheckScreenMiuix(uiState, actions)
    }
}

@Composable
fun envCheckStatusLabel(exposed: Boolean?): String = stringResource(
    when (exposed) {
        true -> R.string.envcheck_exposed
        false -> R.string.envcheck_safe
        null -> R.string.envcheck_unknown
    },
)

@Composable
fun envCheckViewLabel(result: EnvCheckResult): String? {
    val view = result.view?.takeIf { it.isNotBlank() } ?: return null
    val viewName = if (view == "unauthorized_uid") {
        stringResource(R.string.envcheck_view_unauthorized)
    } else {
        view
    }
    return if (result.uid != null) {
        stringResource(R.string.envcheck_view_with_uid, viewName, result.uid)
    } else {
        stringResource(R.string.envcheck_view, viewName)
    }
}
