package org.musu.next.data.repository

import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.musu.next.data.model.BuiltinComponent
import org.musu.next.data.model.BuiltinComponentId
import org.musu.next.data.model.BuiltinListResponse
import org.musu.next.data.model.toComponent
import org.musu.next.ui.util.execKsudForResult

/**
 * One `ksud builtin list` answer. ksud owns the state: the manager never writes `state.json` and
 * never recomputes `ui_state` (`builtin-components.md` §0, §4.3).
 */
@Immutable
data class BuiltinSnapshot(
    val components: Map<BuiltinComponentId, BuiltinComponent> = emptyMap(),
    /** Modules ksud refuses to enable because a built-in component is running (§6.4). */
    val blockedModuleIds: Set<String> = emptySet(),
    val blockedMetamoduleIds: Set<String> = emptySet(),
) {
    operator fun get(id: BuiltinComponentId): BuiltinComponent =
        components[id] ?: BuiltinComponent(id = id)

    companion object {
        val Empty = BuiltinSnapshot()
    }
}

interface BuiltinRepository {
    suspend fun list(): Result<BuiltinSnapshot>

    /** `builtin enable|disable <id>`; the caller reloads [list] afterwards for the real state. */
    suspend fun setEnabled(id: BuiltinComponentId, enabled: Boolean): Result<String>
}

class BuiltinRepositoryImpl : BuiltinRepository {

    override suspend fun list(): Result<BuiltinSnapshot> = withContext(Dispatchers.IO) {
        execKsudForResult("builtin list")
            .mapCatching { stdout -> parse(stdout) }
            .onFailure { Log.w(TAG, "builtin list failed", it) }
    }

    override suspend fun setEnabled(id: BuiltinComponentId, enabled: Boolean): Result<String> {
        val action = if (enabled) "enable" else "disable"
        return withContext(Dispatchers.IO) {
            execKsudForResult("builtin $action ${id.wireId}", newShell = true)
                .onFailure { Log.w(TAG, "builtin $action ${id.wireId} failed", it) }
        }
    }

    private suspend fun parse(stdout: String): BuiltinSnapshot {
        val start = stdout.indexOf('{')
        val end = stdout.lastIndexOf('}')
        check(start >= 0 && end > start) { "ksud builtin list returned no JSON object" }
        val response = json.decodeFromString<BuiltinListResponse>(stdout.substring(start, end + 1))

        val blocked = response.blockedModuleIds.toSet()
        val blockedMeta = response.blockedMetamoduleIds.toSet()
        val names = resolveModuleNames(blocked + blockedMeta)

        fun resolved(ids: Set<String>): List<String> = ids.map { names[it] ?: it }

        val components = response.components.mapNotNull { dto ->
            val id = BuiltinComponentId.fromWire(dto.id) ?: return@mapNotNull null
            id to dto.toComponent(
                id = id,
                suppressed = when (id) {
                    BuiltinComponentId.Zygisk -> resolved(blocked)
                    BuiltinComponentId.Metamodule -> resolved(blockedMeta)
                },
            )
        }.toMap()

        return BuiltinSnapshot(
            components = components,
            blockedModuleIds = blocked,
            blockedMetamoduleIds = blockedMeta,
        )
    }

    /** The notice card names the module, so ids from `builtin list` are resolved to display names. */
    private suspend fun resolveModuleNames(ids: Set<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val modules = ModuleRepositoryImpl().getModules().getOrDefault(emptyList())
        return modules.filter { it.id in ids }.associate { it.id to it.name }
    }

    private companion object {
        const val TAG = "BuiltinRepository"
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
