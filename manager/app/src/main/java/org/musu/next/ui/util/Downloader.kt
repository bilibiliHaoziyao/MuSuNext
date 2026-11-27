package org.musu.next.ui.util

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.musu.next.data.registry.ExpectedFile
import org.musu.next.ksuApp
import org.musu.next.ui.util.module.LatestVersionInfo
import okhttp3.Request

/**
 * @author weishu
 * @date 2023/6/22.
 */
suspend fun download(
    url: String,
    fileName: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {},
    expected: ExpectedFile? = null,
    completionAction: DownloadCompletionAction = DownloadCompletionAction.INSTALL_MODULE,
    onFailed: (DownloadManager.DownloadState) -> Unit = {},
) {
    onDownloading()

    val downloadId = DownloadManager.enqueue(
        context = ksuApp,
        url = url,
        fileName = fileName,
        completionAction = completionAction,
        onCompleted = onDownloaded,
        expected = expected,
    )

    val finalState = DownloadManager.downloads
        .onEach { map -> map[downloadId]?.let { onProgress(it.progress) } }
        .first { map ->
            val status = map[downloadId]?.status
            status == DownloadManager.Status.COMPLETED ||
                status == DownloadManager.Status.FAILED
        }[downloadId]
    if (finalState?.status == DownloadManager.Status.FAILED) {
        withContext(Dispatchers.Main) { onFailed(finalState) }
    }
}

internal suspend fun isDownloadAvailable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ksuApp.contentResolver.openFileDescriptor(uri, "r").use { it != null }
    }.getOrDefault(false)
}

/**
 * Parses a manager release asset into [LatestVersionInfo], or null when the asset name does not
 * follow the `<versionName>_<versionCode>-release.apk` convention.
 *
 * Both `MuSu_Next_0.0.9_9-release.apk` and the upstream `KernelSU_v1.0.0_12345-release.apk`
 * naming are accepted. [digest] is the value of GitHub's `digest` field (`sha256:<hex>`).
 * Kept separate from the network call so it can be unit tested.
 */
internal fun parseManagerAsset(
    name: String,
    downloadUrl: String,
    size: Long,
    digest: String?,
    changelog: String,
): LatestVersionInfo? {
    if (!name.endsWith(".apk", ignoreCase = true)) return null
    // matches "0.0.9_9-" in MuSu_Next_0.0.9_9-release.apk and "1.0.0_12345-" in KernelSU_v1.0.0_12345-release.apk
    val match = Regex("(\\d+(?:\\.\\d+)+)_(\\d+)-").find(name) ?: return null
    val versionName = match.groupValues[1]
    val versionCode = match.groupValues[2].toIntOrNull() ?: return null
    val sha256 = digest
        ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.length == 64 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
        ?: ""
    return LatestVersionInfo(
        versionCode = versionCode,
        versionName = versionName,
        downloadUrl = downloadUrl,
        changelog = changelog,
        size = size,
        sha256 = sha256,
    )
}

fun checkNewVersion(): LatestVersionInfo {
    if (!isNetworkAvailable(ksuApp)) return LatestVersionInfo()
    val url = "https://api.github.com/repos/bilibiliHaoziyao/MuSuNext/releases/latest"
    val defaultValue = LatestVersionInfo()
    runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(url).build()).execute()
            .use { response ->
                if (!response.isSuccessful) return defaultValue
                val json = org.json.JSONObject(response.body.string())
                val changelog = json.optString("body")
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val parsed = parseManagerAsset(
                        name = asset.getString("name"),
                        downloadUrl = asset.getString("browser_download_url"),
                        size = asset.optLong("size", -1L),
                        digest = asset.optString("digest").ifEmpty { null },
                        changelog = changelog,
                    )
                    if (parsed != null) return parsed
                }
            }
    }
    return defaultValue
}
