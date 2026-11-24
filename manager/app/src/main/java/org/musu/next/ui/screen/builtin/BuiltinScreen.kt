package org.musu.next.ui.screen.builtin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import org.musu.next.data.model.BuiltinComponentId
import org.musu.next.data.model.BuiltinPendingReboot
import org.musu.next.ui.LocalUiMode
import org.musu.next.ui.UiMode
import org.musu.next.ui.component.bottombar.BottomBarDestination
import org.musu.next.ui.component.rebootlistpopup.rememberRebootAction
import org.musu.next.ui.navigation3.LocalNavigator
import org.musu.next.ui.navigation3.Route
import org.musu.next.ui.viewmodel.BuiltinViewModel
import org.musu.next.ui.viewmodel.MainActivityViewModel

/**
 * One page per component; [componentId] is a [BuiltinComponentId.wireId] carried by [Route.Builtin].
 */
@Composable
fun BuiltinScreen(componentId: String) {
    val id = BuiltinComponentId.fromWire(componentId) ?: return
    val navigator = LocalNavigator.current
    val mainViewModel = viewModel<MainActivityViewModel>()
    val viewModel = viewModel<BuiltinViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val reboot = rememberRebootAction()

    val component = uiState.component(id)

    // Re-read on every resume: coming back from the module list must not show a stale "blocked".
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val actions = BuiltinActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSetEnabled = { viewModel.setEnabled(id, it) },
        onRetry = viewModel::refresh,
        onReboot = {
            // "待重启" is not the same on both pages (§9.4): the metamodule mounts during boot only,
            // so it always asks for a full reboot and ignores the soft-reboot preference.
            val soft = id == BuiltinComponentId.Zygisk &&
                component.pendingReboot == BuiltinPendingReboot.Soft
            reboot(if (soft) "soft_reboot" else "")
        },
        onOpenModuleList = {
            // The module list is a tab of the main pager; pushing its route opens it on that tab.
            mainViewModel.setSelectedMainPage(BottomBarDestination.Module.ordinal)
            navigator.push(Route.Module)
        },
        onViewLog = { navigator.push(Route.Sulog) },
        onOpenLink = uriHandler::openUri,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> BuiltinScreenMiuix(component, actions)
        UiMode.Material -> BuiltinScreenMaterial(component, actions)
    }
}
