package org.musu.next.ui.screen.module

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.musu.next.R
import org.musu.next.data.model.Module
import org.musu.next.data.model.ModuleBlock

/** Message for a module whose preinit actions will not run on the next boot, null otherwise. */
@StringRes
fun Module.preinitWarning(): Int? = when (preinit) {
    "no_space" -> R.string.module_preinit_no_space
    "error" -> R.string.module_preinit_error
    "bootloop" -> R.string.module_preinit_bootloop
    "unsupported" -> R.string.module_preinit_unsupported
    else -> null
}

/**
 * Why this module cannot be enabled right now, or null when nothing blocks it. The wording is fixed
 * by `builtin-ui.md` §6.3: name the running component and the step that frees the module.
 */
@Composable
fun Module.blockedReason(): String? = when (blockedBy) {
    ModuleBlock.MuZygisk -> stringResource(R.string.builtin_module_blocked_zygisk)
    ModuleBlock.Metamodule -> stringResource(R.string.builtin_module_blocked_meta)
    ModuleBlock.None -> null
}

