package org.musu.next.ui.screen.dirtyfrag

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.musu.next.ui.util.execKsudStdinForResult

/**
 * The only place that talks to `ksud dirtyfrag` (spec §3.1). The page never calls the CLI directly;
 * JSON parsing and error normalisation happen here so detect/run/status share one contract reader.
 */
interface DirtyFragRepository {
    suspend fun detect(): Result<DfDetect>
    suspend fun run(clientRequestId: String): Result<DfRun>
    suspend fun status(): Result<DfStatus>
}

class DirtyFragRepositoryImpl : DirtyFragRepository {

    override suspend fun detect(): Result<DfDetect> = withContext(Dispatchers.IO) {
        exec("dirtyfrag detect", "{}") { stdout ->
            decode<DfDetectResponse>(stdout).toDomain()
        }
    }

    override suspend fun run(clientRequestId: String): Result<DfRun> = withContext(Dispatchers.IO) {
        val stdin = json.encodeToString(DfRunRequest(clientRequestId = clientRequestId))
        exec("dirtyfrag run", stdin) { stdout ->
            decode<DfRunResponse>(stdout).toDomain()
        }
    }

    override suspend fun status(): Result<DfStatus> = withContext(Dispatchers.IO) {
        exec("dirtyfrag status", "{}") { stdout ->
            decode<DfStatusResponse>(stdout).toDomain()
        }
    }

    private inline fun <T> exec(args: String, stdinJson: String, crossinline parse: (String) -> T): Result<T> {
        return execKsudStdinForResult(args, stdinJson)
            .mapCatching { stdout -> parse(stdout) }
            .onFailure { Log.w(TAG, "$args failed", it) }
    }

    private inline fun <reified T> decode(stdout: String): T {
        val start = stdout.indexOf('{')
        val end = stdout.lastIndexOf('}')
        check(start >= 0 && end > start) { "ksud dirtyfrag returned no JSON object" }
        return json.decodeFromString<T>(stdout.substring(start, end + 1))
    }

    private companion object {
        const val TAG = "DirtyFragRepository"
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}

/** Boundary guard used by the ViewModel: a blank or malformed id must never reach the shell. */
internal fun isValidRequestId(id: String): Boolean =
    id.length in 1..64 && id.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
