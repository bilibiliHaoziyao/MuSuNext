package org.musu.next.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.musu.next.ui.screen.snapshot.SnapshotCreateInfo
import org.musu.next.ui.screen.snapshot.SnapshotCreateResponse
import org.musu.next.ui.screen.snapshot.SnapshotInfo
import org.musu.next.ui.screen.snapshot.SnapshotListResponse
import org.musu.next.ui.screen.snapshot.SnapshotOkResponse
import org.musu.next.ui.screen.snapshot.SnapshotRestoreResponse
import org.musu.next.ui.screen.snapshot.toInfo
import org.musu.next.ui.util.execKsudForResult
import java.io.IOException

interface SnapshotRepository {
    suspend fun create(): Result<SnapshotCreateInfo>
    suspend fun list(): Result<List<SnapshotInfo>>
    /** Returns whether a full reboot is required. */
    suspend fun restore(path: String): Result<Boolean>
    suspend fun delete(path: String): Result<Unit>
}

class SnapshotRepositoryImpl : SnapshotRepository {

    override suspend fun create(): Result<SnapshotCreateInfo> = withContext(Dispatchers.IO) {
        runCommand("snapshot create") { stdout ->
            val response = decode<SnapshotCreateResponse>(stdout)
            requireOk(response.ok, response.error, "snapshot create")
            SnapshotCreateInfo(
                path = response.path.orEmpty(),
                modules = response.modules,
                profiles = response.profiles,
                builtins = response.builtins,
                bytes = response.bytes,
            )
        }
    }

    override suspend fun list(): Result<List<SnapshotInfo>> = withContext(Dispatchers.IO) {
        runCommand("snapshot list") { stdout ->
            val response = decode<SnapshotListResponse>(stdout)
            requireOk(response.ok, response.error, "snapshot list")
            response.snapshots.map { it.toInfo() }
        }
    }

    override suspend fun restore(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCommand("snapshot restore ${path.shellQuote()}") { stdout ->
            val response = decode<SnapshotRestoreResponse>(stdout)
            requireOk(response.ok, response.error, "snapshot restore")
            response.needReboot
        }
    }

    override suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCommand("snapshot delete ${path.shellQuote()}") { stdout ->
            val response = decode<SnapshotOkResponse>(stdout)
            requireOk(response.ok, response.error, "snapshot delete")
            Unit
        }
    }

    private inline fun <T> runCommand(args: String, crossinline parse: (String) -> T): Result<T> {
        return execKsudForResult(args, newShell = true)
            .mapCatching { stdout -> parse(stdout) }
            .onFailure { Log.w(TAG, "$args failed", it) }
    }

    private inline fun <reified T> decode(stdout: String): T {
        val start = stdout.indexOf('{')
        val end = stdout.lastIndexOf('}')
        check(start >= 0 && end > start) { "ksud snapshot returned no JSON object" }
        return json.decodeFromString<T>(stdout.substring(start, end + 1))
    }

    private fun requireOk(ok: Boolean, error: String?, command: String) {
        if (!ok) throw IOException(error?.takeIf { it.isNotBlank() } ?: "$command failed")
    }

    /** Paths come from the backend but are still quoted before entering a root shell. */
    private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

    private companion object {
        const val TAG = "SnapshotRepository"
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
