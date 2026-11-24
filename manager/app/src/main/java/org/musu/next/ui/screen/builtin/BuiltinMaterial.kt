package org.musu.next.ui.screen.builtin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.musu.next.R
import org.musu.next.data.model.BuiltinComponent
import org.musu.next.data.model.BuiltinComponentId
import org.musu.next.data.model.BuiltinPhase
import org.musu.next.ui.component.WarningLevel
import org.musu.next.ui.component.material.ExpressiveScaffold
import org.musu.next.ui.component.material.SegmentedColumn
import org.musu.next.ui.component.material.SegmentedListItem
import org.musu.next.ui.component.material.SegmentedSwitchItem
import org.musu.next.ui.component.material.TonalCard
import org.musu.next.ui.component.material.TopBarBackButton
import org.musu.next.ui.component.material.expressiveTopAppBarColors
import org.musu.next.ui.component.statustag.StatusTag

/**
 * Built-in component page, Material3 flavour. Sections in the order fixed by `builtin-ui.md` §4:
 * switch → status → conflict (conditional) → details.
 */
@Composable
fun BuiltinScreenMaterial(
    component: BuiltinComponent,
    actions: BuiltinActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val title = builtinTitleText(component.id)

    val statusRows = if (component.phase == BuiltinPhase.Probing) {
        emptyList()
    } else {
        builtinStatusRows(component)
    }
    val detailRows = builtinDetailRows(component)
    val notice = builtinNotice(component)

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            // A. The only switch of the page.
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                item {
                    SegmentedSwitchItem(
                        icon = component.id.materialIcon(),
                        title = title,
                        summary = builtinSummaryText(component),
                        checked = component.checked,
                        enabled = component.canToggle,
                        onCheckedChange = actions.onSetEnabled,
                    )
                }
            }

            // B. Status.
            SegmentedColumn(
                title = stringResource(R.string.builtin_section_status),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
            ) {
                if (component.phase == BuiltinPhase.Probing) {
                    item { StatusSkeletonMaterial() }
                } else {
                    statusRows.forEach { row ->
                        item(row.key) { StatusRowMaterial(row, component) }
                    }
                }
            }

            // C. Conflict, between status and details, only when there is something to say.
            notice?.let {
                NoticeCardMaterial(it, actions)
            }

            // D. Details.
            SegmentedColumn(
                title = stringResource(R.string.builtin_section_detail),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
            ) {
                detailRows.forEach { row ->
                    item(row.key) { DetailRowMaterial(row, actions) }
                }
            }

            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                )
            )
        }
    }
}

@Composable
private fun StatusRowMaterial(
    row: BuiltinRow,
    component: BuiltinComponent,
) {
    val tagLabel = builtinTagLabelRes(component.phase)?.let { stringResource(it) }
    val value = row.value
    val supporting: (@Composable () -> Unit)? = if (value != null) {
        {
            Column {
                Text(
                    text = value,
                    color = if (row.valueIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                row.hint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        null
    }
    val trailing: (@Composable () -> Unit)? = if (row.showTag && tagLabel != null) {
        {
            val (background, content) = builtinTagColorsMaterial(component.phase)
            StatusTag(
                label = tagLabel,
                backgroundColor = background,
                contentColor = content,
            )
        }
    } else {
        null
    }

    SegmentedListItem(
        headlineContent = { Text(row.title) },
        supportingContent = supporting,
        trailingContent = trailing,
    )
}

@Composable
private fun DetailRowMaterial(
    row: BuiltinRow,
    actions: BuiltinActions,
) {
    val value = row.value
    val supporting: (@Composable () -> Unit)? = if (value != null) {
        {
            Column {
                Text(text = value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.hint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    } else {
        null
    }
    val linkUrl = row.linkUrl
    val trailing: (@Composable () -> Unit)? = if (linkUrl != null) {
        {
            FilledTonalButton(
                onClick = { actions.onOpenLink(linkUrl) },
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = stringResource(R.string.builtin_detail_upstream),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    } else {
        null
    }

    SegmentedListItem(
        headlineContent = { Text(row.title) },
        supportingContent = supporting,
        trailingContent = trailing,
    )
}

@Composable
private fun NoticeCardMaterial(
    notice: BuiltinNotice,
    actions: BuiltinActions,
) {
    val (container, content) = when (notice.level) {
        WarningLevel.Error -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer

        WarningLevel.Notice -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
    }
    TonalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        containerColor = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = notice.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            notice.actionLabel?.let { label ->
                TextButton(
                    onClick = { notice.action.dispatch(actions) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(text = label, color = content)
                }
            }
        }
    }
}

/** Three static bars: the only loading state of the page (§9.2), and it never shimmers. */
@Composable
private fun StatusSkeletonMaterial() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceBright,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0.5f, 0.35f, 0.7f).forEach { width ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(width)
                        .height(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(4.dp),
                ) {}
            }
        }
    }
}

/** Tag colours come straight from the three-step order of §5.3; no new colour is introduced. */
@Composable
private fun builtinTagColorsMaterial(phase: BuiltinPhase): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (phase) {
        BuiltinPhase.Failed -> scheme.error to scheme.onError
        BuiltinPhase.BlockedByOther -> scheme.errorContainer to scheme.onErrorContainer
        BuiltinPhase.PendingEnable, BuiltinPhase.PendingDisable ->
            scheme.tertiaryContainer to scheme.onTertiaryContainer

        BuiltinPhase.Active -> scheme.primary to scheme.onPrimary
        BuiltinPhase.Unsupported, BuiltinPhase.Off, BuiltinPhase.ReadError, BuiltinPhase.Probing ->
            scheme.surfaceVariant to scheme.onSurfaceVariant
    }
}

private fun BuiltinComponentId.materialIcon(): ImageVector = when (this) {
    BuiltinComponentId.Zygisk -> Icons.Filled.Memory
    BuiltinComponentId.Metamodule -> Icons.Filled.Layers
}
