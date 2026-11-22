package org.musu.next.ui.component.uninstalldialog

import androidx.compose.runtime.Composable
import org.musu.next.ui.LocalUiMode
import org.musu.next.ui.UiMode

@Composable
fun UninstallDialog(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> UninstallDialogMiuix(show, onDismissRequest)
        UiMode.Material -> UninstallDialogMaterial(show, onDismissRequest)
    }
}
