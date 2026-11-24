package org.musu.next.ui.screen.builtin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.musu.next.R
import org.musu.next.data.model.BuiltinComponent
import org.musu.next.data.model.BuiltinComponentId
import org.musu.next.data.model.BuiltinPhase
import org.musu.next.ui.component.miuix.WarningCard
import org.musu.next.ui.component.statustag.StatusTag
import org.musu.next.ui.theme.LocalEnableBlur
import org.musu.next.ui.util.BlurredBar
import org.musu.next.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Built-in component page, Miuix flavour. Same sections, wording and state mapping as
 * [BuiltinScreenMaterial]; only the containers and the screen margin differ (`builtin-ui.md` §7).
 */
@Composable
fun BuiltinScreenMiuix(
    component: BuiltinComponent,
    actions: BuiltinActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    val title = builtinTitleText(component.id)

    val statusRows = if (component.phase == BuiltinPhase.Probing) {
        emptyList()
    } else {
        builtinStatusRows(component)
    }
    val detailRows = builtinDetailRows(component)
    val notice = builtinNotice(component)

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = title,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                // A. The only switch of the page.
                item(key = "switch") {
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = title,
                            summary = builtinSummaryText(component),
                            startAction = {
                                Icon(
                                    imageVector = component.id.miuixIcon(),
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = title,
                                    tint = if (component.canToggle) {
                                        colorScheme.onBackground
                                    } else {
                                        colorScheme.disabledOnSecondaryVariant
                                    },
                                )
                            },
                            checked = component.checked,
                            enabled = component.canToggle,
                            onCheckedChange = actions.onSetEnabled,
                        )
                    }
                }

                // B. Status.
                item(key = "status") {
                    if (component.phase == BuiltinPhase.Probing) {
                        StatusSkeletonMiuix()
                    } else {
                        val rows: List<@Composable () -> Unit> = statusRows.map { row ->
                            { StatusRowMiuix(row, component) }
                        }
                        SectionMiuix(
                            title = stringResource(R.string.builtin_section_status),
                            rows = rows,
                        )
                    }
                }

                // C. Conflict, only when there is something to say.
                if (notice != null) {
                    item(key = "notice") {
                        val label = notice.actionLabel
                        val noticeAction: (@Composable () -> Unit)? = if (label != null) {
                            {
                                TextButton(
                                    text = label,
                                    onClick = { notice.action.dispatch(actions) },
                                )
                            }
                        } else {
                            null
                        }
                        WarningCard(
                            message = notice.message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            level = notice.level,
                            action = noticeAction,
                        )
                    }
                }

                // D. Details.
                item(key = "detail") {
                    val rows: List<@Composable () -> Unit> = detailRows.map { row ->
                        { DetailRowMiuix(row, actions) }
                    }
                    SectionMiuix(
                        title = stringResource(R.string.builtin_section_detail),
                        rows = rows,
                    )
                }

                item(key = "bottom") {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionMiuix(
    title: String,
    rows: List<@Composable () -> Unit>,
) {
    if (rows.isEmpty()) return
    Column {
        SmallTitle(text = title, modifier = Modifier.padding(top = 12.dp))
        Card(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    row()
                    if (index != rows.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 0.5.dp,
                            color = colorScheme.outline.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRowMiuix(
    row: BuiltinRow,
    component: BuiltinComponent,
) {
    val tagLabel = builtinTagLabelRes(component.phase)?.let { stringResource(it) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, fontSize = 14.sp, color = colorScheme.onSurface)
            row.value?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = if (row.valueIsError) colorScheme.error else colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            row.hint?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (row.showTag && tagLabel != null) {
            val (background, content) = builtinTagColorsMiuix(component.phase)
            StatusTag(label = tagLabel, backgroundColor = background, contentColor = content)
        }
    }
}

@Composable
private fun DetailRowMiuix(
    row: BuiltinRow,
    actions: BuiltinActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, fontSize = 14.sp, color = colorScheme.onSurface)
            row.value?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            row.hint?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        row.linkUrl?.let { url ->
            IconButton(
                backgroundColor = colorScheme.secondaryContainer,
                minHeight = 35.dp,
                minWidth = 35.dp,
                onClick = { actions.onOpenLink(url) },
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = MiuixIcons.Link,
                    tint = colorScheme.onSecondaryContainer,
                    contentDescription = stringResource(R.string.builtin_detail_upstream),
                )
            }
        }
    }
}

/** Three static bars; no shimmer, so turning animations off changes nothing (§8.5). */
@Composable
private fun StatusSkeletonMiuix() {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.35f, 0.7f).forEach { width ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(width)
                            .height(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

/** Same three steps as the Material page (§5.3), read from the Miuix scheme. */
@Composable
private fun builtinTagColorsMiuix(phase: BuiltinPhase): Pair<Color, Color> = when (phase) {
    BuiltinPhase.Failed -> colorScheme.error to colorScheme.onError
    BuiltinPhase.BlockedByOther -> colorScheme.errorContainer to colorScheme.onErrorContainer
    BuiltinPhase.PendingEnable, BuiltinPhase.PendingDisable ->
        colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer

    BuiltinPhase.Active -> colorScheme.primary to colorScheme.onPrimary
    BuiltinPhase.Unsupported, BuiltinPhase.Off, BuiltinPhase.ReadError, BuiltinPhase.Probing ->
        colorScheme.surfaceVariant to colorScheme.onSurfaceVariantSummary
}

private fun BuiltinComponentId.miuixIcon(): ImageVector = when (this) {
    BuiltinComponentId.Zygisk -> Icons.Rounded.Memory
    BuiltinComponentId.Metamodule -> Icons.Rounded.Layers
}
