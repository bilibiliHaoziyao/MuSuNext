package org.musu.next.ui.component.choosekmidialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.musu.next.R
import org.musu.next.ui.util.getCurrentKmi
import org.musu.next.ui.util.getSupportedKmis
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference

@Composable
fun ChooseKmiDialogMiuix(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSelected: (String?) -> Unit
) {
    val supportedKMIs by produceState(initialValue = emptyList<String>()) {
        value = getSupportedKmis()
    }
    val currentKmi by produceState(initialValue = "") {
        value = getCurrentKmi()
    }

    // 清单是异步取回来的，默认值只能等它到了再定：设备自身的 KMI 优先，
    // 否则退回第一个，避免确认键一直灰着。
    var currentSelection by remember { mutableStateOf("") }
    LaunchedEffect(supportedKMIs, currentKmi) {
        if (currentSelection.isBlank() || currentSelection !in supportedKMIs) {
            currentSelection = if (currentKmi in supportedKMIs) {
                currentKmi
            } else {
                supportedKMIs.firstOrNull().orEmpty()
            }
        }
    }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.select_kmi),
        summary = stringResource(R.string.current_kmi, currentKmi.ifBlank { "Unknown" }),
        onDismissRequest = onDismissRequest,
        insideMargin = DpSize(0.dp, 24.dp),
        content = {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                if (supportedKMIs.isEmpty()) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 30.dp, vertical = 16.dp),
                        text = stringResource(R.string.lkm_not_bundled),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(supportedKMIs) { kmi ->
                            CheckboxPreference(
                                title = kmi,
                                summary = if (kmi == currentKmi) stringResource(R.string.current_device_kmi) else null,
                                insideMargin = PaddingValues(horizontal = 30.dp, vertical = 16.dp),
                                checkboxLocation = CheckboxLocation.End,
                                checked = currentSelection == kmi,
                                holdDownState = currentSelection == kmi,
                                onCheckedChange = { _ ->
                                    currentSelection = kmi
                                }
                            )
                        }
                    }
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp),
                        text = stringResource(R.string.bundled_kmi_count, supportedKMIs.size),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        text = stringResource(android.R.string.cancel),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(
                        enabled = currentSelection.isNotBlank() && currentSelection in supportedKMIs,
                        onClick = {
                            onSelected(currentSelection)
                            onDismissRequest()
                        },
                        text = stringResource(R.string.confirm),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}
