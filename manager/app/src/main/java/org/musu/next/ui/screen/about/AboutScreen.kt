package org.musu.next.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import org.musu.next.BuildConfig
import org.musu.next.R
import org.musu.next.ui.LocalUiMode
import org.musu.next.ui.UiMode
import org.musu.next.ui.navigation3.LocalNavigator

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val htmlString = stringResource(
        id = R.string.about_source_code,
        "<b><a href=\"https://github.com/bilibiliHaoziyao/MuSuNext\">GitHub</a></b>",
        "<b><a href=\"https://b23.tv/6UtmCB0\">Bilibili</a></b>"
    ) + "<br/>" + stringResource(
        id = R.string.about_acknowledge,
        "<b><a href=\"https://github.com/tiann/KernelSU\">KernelSU</a></b>"
    )
    val state = AboutUiState(
        title = stringResource(R.string.about),
        appName = stringResource(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
        links = extractLinks(htmlString),
    )
    val actions = AboutScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onOpenLink = uriHandler::openUri,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> AboutScreenMiuix(state, actions)
        UiMode.Material -> AboutScreenMaterial(state, actions)
    }
}
