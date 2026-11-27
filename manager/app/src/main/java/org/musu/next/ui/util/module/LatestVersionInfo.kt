package org.musu.next.ui.util.module

/**
 * Information about the latest manager release published on GitHub.
 *
 * [size] and [sha256] are read from the release asset's `digest` (GitHub computes this for every
 * asset). When both are present the APK is downloaded through the verified path: URL allowlist,
 * size and SHA-256 are checked before the file is published, so a hijacked download is rejected.
 */
data class LatestVersionInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val downloadUrl: String = "",
    val changelog: String = "",
    val size: Long = -1L,
    val sha256: String = "",
) {
    val isVerifiable: Boolean
        get() = size > 0 && sha256.length == 64
}
