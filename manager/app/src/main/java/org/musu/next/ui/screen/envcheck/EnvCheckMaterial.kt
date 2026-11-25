package org.musu.next.ui.screen.envcheck

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
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
import org.musu.next.ui.component.statustag.StatusTag

@Composable
fun EnvCheckScreenMaterial(
    state: EnvCheckUiState,
    actions: EnvCheckActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val viewLabel = state.result?.takeIf { it.ok }?.let { envCheckViewLabel(it) }

    ExpressiveScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.envcheck_title)) },
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
                            text = stringResource(R.string.envcheck_intro),
                            style = typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = actions.onCheck,
                            enabled = !state.isChecking,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(
                                    if (state.isChecking) R.string.envcheck_checking
                                    else R.string.envcheck_start
                                ),
                            )
                        }
                    }
                }
            }

            state.errorMessage?.let { message ->
                item(key = "shell_error") {
                    TonalCard {
                        Text(
                            text = stringResource(R.string.envcheck_error_detail, message),
                            style = typography.bodyMedium,
                            color = colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            val result = state.result
            if (result != null && !result.ok) {
                // Honest partial answer: never render this as "all safe".
                item(key = "backend_error") {
                    TonalCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.envcheck_error_title),
                                style = typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.envcheck_error_detail,
                                    result.error ?: stringResource(R.string.envcheck_error_unknown),
                                ),
                                style = typography.bodyMedium,
                                color = colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (result.partialItems.isNotEmpty()) {
                    item(key = "partial_title") {
                        Text(
                            text = stringResource(R.string.envcheck_partial_title),
                            style = typography.titleSmallEmphasized,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    items(result.partialItems, key = { "partial_${it.id}" }) { item ->
                        EnvCheckItemCardMaterial(item)
                    }
                }
            } else if (result != null) {
                if (viewLabel != null) {
                    item(key = "view") {
                        Text(
                            text = viewLabel,
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }

                if (result.items.isEmpty()) {
                    item(key = "empty_items") {
                        TonalCard {
                            Text(
                                text = stringResource(R.string.envcheck_empty),
                                style = typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(result.items, key = { it.id }) { item ->
                        EnvCheckItemCardMaterial(item)
                    }
                }

                item(key = "summary") {
                    TonalCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.envcheck_exposed_count, result.effectiveExposedCount),
                                style = typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (result.effectiveExposedCount > 0) colorScheme.error else colorScheme.primary,
                            )
                            result.checkedAt?.let { checkedAt ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.envcheck_checked_at, checkedAt),
                                    style = typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvCheckItemCardMaterial(item: EnvCheckItem) {
    val (background, foreground) = when (item.exposed) {
        true -> colorScheme.errorContainer to colorScheme.onErrorContainer
        false -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
        null -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
    }
    val icon = when (item.exposed) {
        true -> Icons.Filled.Warning
        false -> Icons.Filled.CheckCircle
        null -> Icons.AutoMirrored.Filled.HelpOutline
    }

    TonalCard {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                item.detail?.let { detail ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = detail,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusTag(
                label = envCheckStatusLabel(item.exposed),
                backgroundColor = background,
                contentColor = foreground,
            )
        }
    }
}
