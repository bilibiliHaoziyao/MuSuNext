package org.musu.next.ui.screen.envcheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.musu.next.R
import org.musu.next.ui.component.statustag.StatusTag
import org.musu.next.ui.theme.LocalEnableBlur
import org.musu.next.ui.util.BlurredBar
import org.musu.next.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun EnvCheckScreenMiuix(
    state: EnvCheckUiState,
    actions: EnvCheckActions,
) {
    val enableBlur = LocalEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val layoutDirection = LocalLayoutDirection.current
    val viewLabel = state.result?.takeIf { it.ok }?.let { envCheckViewLabel(it) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.envcheck_title),
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            val direction = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (direction == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 12.dp,
                end = padding.calculateEndPadding(layoutDirection) + 12.dp,
                top = padding.calculateTopPadding() + dynamicTopPadding + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.envcheck_intro),
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(
                            text = stringResource(
                                if (state.isChecking) R.string.envcheck_checking
                                else R.string.envcheck_start
                            ),
                            onClick = actions.onCheck,
                            enabled = !state.isChecking,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }

            state.errorMessage?.let { message ->
                item(key = "shell_error") {
                    Card {
                        Text(
                            text = stringResource(R.string.envcheck_error_detail, message),
                            fontSize = 14.sp,
                            color = colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            val result = state.result
            if (result != null && !result.ok) {
                item(key = "backend_error") {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.envcheck_error_title),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.envcheck_error_detail,
                                    result.error ?: stringResource(R.string.envcheck_error_unknown),
                                ),
                                fontSize = 14.sp,
                                color = colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (result.partialItems.isNotEmpty()) {
                    item(key = "partial_title") {
                        SmallTitle(
                            text = stringResource(R.string.envcheck_partial_title),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    items(result.partialItems, key = { "partial_${it.id}" }) { item ->
                        EnvCheckItemCardMiuix(item)
                    }
                }
            } else if (result != null) {
                if (viewLabel != null) {
                    item(key = "view") {
                        Text(
                            text = viewLabel,
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }

                if (result.items.isEmpty()) {
                    item(key = "empty_items") {
                        Card {
                            Text(
                                text = stringResource(R.string.envcheck_empty),
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(result.items, key = { it.id }) { item ->
                        EnvCheckItemCardMiuix(item)
                    }
                }

                item(key = "summary") {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.envcheck_exposed_count, result.effectiveExposedCount),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (result.effectiveExposedCount > 0) colorScheme.error else colorScheme.primary,
                            )
                            result.checkedAt?.let { checkedAt ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.envcheck_checked_at, checkedAt),
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
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
private fun EnvCheckItemCardMiuix(item: EnvCheckItem) {
    val (background, foreground) = when (item.exposed) {
        true -> colorScheme.errorContainer to colorScheme.onErrorContainer
        false -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
        null -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariantSummary
    }
    val icon = when (item.exposed) {
        true -> Icons.Filled.Warning
        false -> Icons.Filled.CheckCircle
        null -> Icons.AutoMirrored.Filled.HelpOutline
    }

    Card {
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                item.detail?.let { detail ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = detail,
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
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
