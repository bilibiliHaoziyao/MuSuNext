package org.musu.next.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.musu.next.ui.screen.envcheck.EnvCheckResponse
import org.musu.next.ui.screen.envcheck.EnvCheckResult
import org.musu.next.ui.screen.envcheck.toResult
import org.musu.next.ui.util.execKsudForResult

interface EnvCheckRepository {
    /** `ksud envcheck`. A well-formed `ok:false` answer is a success here: the UI shows its error. */
    suspend fun check(): Result<EnvCheckResult>
}

class EnvCheckRepositoryImpl : EnvCheckRepository {

    override suspend fun check(): Result<EnvCheckResult> = withContext(Dispatchers.IO) {
        execKsudForResult("envcheck")
            .mapCatching { stdout -> parse(stdout) }
            .onFailure { Log.w(TAG, "envcheck failed", it) }
    }

    private fun parse(stdout: String): EnvCheckResult {
        val start = stdout.indexOf('{')
        val end = stdout.lastIndexOf('}')
        check(start >= 0 && end > start) { "ksud envcheck returned no JSON object" }
        return json.decodeFromString<EnvCheckResponse>(stdout.substring(start, end + 1)).toResult()
    }

    private companion object {
        const val TAG = "EnvCheckRepository"
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
