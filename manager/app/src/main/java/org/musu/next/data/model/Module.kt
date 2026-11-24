package org.musu.next.data.model

import androidx.compose.runtime.Immutable

/**
 * A built-in component that keeps this module from being enabled (`builtin-ui.md` §6.3).
 * ksud reports the affected ids in `builtin list`, the reason text belongs to the UI.
 */
enum class ModuleBlock {
    None,
    MuZygisk,
    Metamodule,
}

@Immutable
data class Module(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val versionCode: Int,
    val description: String,
    val enabled: Boolean,
    val update: Boolean,
    val remove: Boolean,
    val updateJson: String,
    val hasWebUi: Boolean,
    val hasActionScript: Boolean,
    val metamodule: Boolean,
    val actionIconPath: String?,
    val webUiIconPath: String?,
    // preinit state for the next boot: "", "ok", "no_space", "error" or "bootloop"
    val preinit: String = "",
    // set when a running built-in component refuses to let this module be enabled
    val blockedBy: ModuleBlock = ModuleBlock.None,
)
