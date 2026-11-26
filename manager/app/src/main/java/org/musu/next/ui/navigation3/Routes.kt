package org.musu.next.ui.navigation3

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.musu.next.ui.screen.flash.FlashIt
import org.musu.next.ui.screen.modulerepo.RepoModuleArg
import org.musu.next.ui.screen.scriptrun.ScriptRunArg
import org.musu.next.ui.util.FlashItSerializer
import org.musu.next.ui.util.RepoModuleArgSerializer
import org.musu.next.ui.util.ScriptRunArgSerializer
import org.musu.next.ui.util.TemplateInfoSerializer
import org.musu.next.ui.viewmodel.TemplateViewModel
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * Type-safe navigation keys for miuix-nav.
 * Each destination is a NavKey (data object/data class) and can be saved/restored in the back stack.
 */
@Serializable
sealed interface Route : NavKey, Parcelable {
    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data object Home : Route

    @Parcelize
    @Serializable
    data object SuperUser : Route

    @Parcelize
    @Serializable
    data object Module : Route

    @Parcelize
    @Serializable
    data object Settings : Route

    @Parcelize
    @Serializable
    data object About : Route

    @Parcelize
    @Serializable
    data object Sulog : Route

    @Parcelize
    @Serializable
    data object SafetyAudit : Route

    @Parcelize
    @Serializable
    data object EnvCheck : Route

    @Parcelize
    @Serializable
    data object Snapshot : Route

    @Parcelize
    @Serializable
    data object DirtyFrag : Route

    @Parcelize
    @Serializable
    data object ColorPalette : Route

    @Parcelize
    @Serializable
    data object AppProfileTemplate : Route

    /** One page per built-in component; [id] is `BuiltinComponentId.wireId`. */
    @Parcelize
    @Serializable
    data class Builtin(val id: String) : Route

    @Parcelize
    @Serializable
    data class TemplateEditor(
        @Serializable(with = TemplateInfoSerializer::class) val template: TemplateViewModel.TemplateInfo,
        val readOnly: Boolean
    ) : Route

    @Parcelize
    @Serializable
    data class AppProfile(val uid: Int) : Route

    @Parcelize
    @Serializable
    data object Install : Route

    @Parcelize
    @Serializable
    data class ModuleRepoDetail(@Serializable(with = RepoModuleArgSerializer::class) val module: RepoModuleArg) : Route

    @Parcelize
    @Serializable
    data object ModuleRepo : Route

    @Parcelize
    @Serializable
    data class Flash(@Serializable(with = FlashItSerializer::class) val flashIt: FlashIt) : Route

    @Parcelize
    @Serializable
    data class ExecuteModuleAction(val moduleId: String, val fromShortcut: Boolean = false) : Route

    @Parcelize
    @Serializable
    data class ScriptRun(@Serializable(with = ScriptRunArgSerializer::class) val arg: ScriptRunArg) : Route
}
