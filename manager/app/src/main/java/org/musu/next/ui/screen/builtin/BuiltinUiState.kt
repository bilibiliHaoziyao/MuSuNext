package org.musu.next.ui.screen.builtin

import androidx.compose.runtime.Immutable
import org.musu.next.data.model.BuiltinComponent
import org.musu.next.data.model.BuiltinComponentId
import org.musu.next.data.model.BuiltinPhase
import org.musu.next.data.repository.BuiltinSnapshot
import org.musu.next.ui.component.WarningLevel

/**
 * State of the two built-in component pages. Both pages read the same snapshot: ksud is the only
 * source of `ui_state`, so a page never derives a phase of its own.
 */
@Immutable
data class BuiltinUiState(
    val snapshot: BuiltinSnapshot? = null,
    /** The last `builtin list` failed. A previous snapshot is kept on purpose (no white flash). */
    val loadFailed: Boolean = false,
    val toggling: Set<BuiltinComponentId> = emptySet(),
    val retrying: Boolean = false,
) {
    fun component(id: BuiltinComponentId): BuiltinComponent {
        val base = snapshot?.get(id) ?: BuiltinComponent(id = id)
        return when {
            id in toggling -> base.copy(toggling = true)
            snapshot == null && loadFailed -> base.copy(phase = BuiltinPhase.ReadError)
            else -> base
        }
    }
}

@Immutable
data class BuiltinActions(
    val onBack: () -> Unit,
    val onSetEnabled: (Boolean) -> Unit,
    val onRetry: () -> Unit,
    val onReboot: () -> Unit,
    val onOpenModuleList: () -> Unit,
    val onViewLog: () -> Unit,
    val onOpenLink: (String) -> Unit,
)

/** What a row or card can do. Both themes render the same action, each with its own control. */
enum class BuiltinRowAction { None, Reboot, Retry, ViewLog, OpenModuleList }

/**
 * One read-only row. Built once in [builtinStatusRows] / [builtinDetailRows] so the Material and the
 * Miuix page cannot drift apart in wording or order.
 */
@Immutable
data class BuiltinRow(
    val key: String,
    val title: String,
    val value: String? = null,
    val hint: String? = null,
    val valueIsError: Boolean = false,
    /** Row B1 carries the StatusTag at its trailing edge (§4.2). */
    val showTag: Boolean = false,
    val linkUrl: String? = null,
    val actionLabel: String? = null,
    val action: BuiltinRowAction = BuiltinRowAction.None,
)

/** Conflict / error card (§4.3). Shown between the status and the detail section. */
@Immutable
data class BuiltinNotice(
    val level: WarningLevel,
    val message: String,
    val actionLabel: String? = null,
    val action: BuiltinRowAction = BuiltinRowAction.None,
)

/**
 * Runs the action a row or a card advertises. One place, so a card can never point at the wrong screen
 * on one theme only.
 */
fun BuiltinRowAction.dispatch(actions: BuiltinActions) {
    when (this) {
        BuiltinRowAction.None -> Unit
        BuiltinRowAction.Reboot -> actions.onReboot()
        BuiltinRowAction.Retry -> actions.onRetry()
        BuiltinRowAction.ViewLog -> actions.onViewLog()
        BuiltinRowAction.OpenModuleList -> actions.onOpenModuleList()
    }
}

