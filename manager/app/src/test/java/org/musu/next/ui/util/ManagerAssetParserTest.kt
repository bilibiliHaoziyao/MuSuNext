package org.musu.next.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerAssetParserTest {

    private val sha = "8a56f350cc93dde4e78d07d6e92c2f86c9976329132f65c4031b63fa76beff88"

    @Test
    fun parsesMuSuNamingWithDigest() {
        val info = parseManagerAsset(
            name = "MuSu_Next_0.0.9_9-release.apk",
            downloadUrl = "https://github.com/bilibiliHaoziyao/MuSuNext/releases/download/v0.0.9/MuSu_Next_0.0.9_9-release.apk",
            size = 19_298_398L,
            digest = "sha256:$sha",
            changelog = "release notes",
        )!!
        assertEquals(9, info.versionCode)
        assertEquals("0.0.9", info.versionName)
        assertEquals(sha, info.sha256)
        assertEquals(19_298_398L, info.size)
        assertTrue(info.isVerifiable)
    }

    @Test
    fun parsesUpstreamNaming() {
        val info = parseManagerAsset(
            name = "KernelSU_v1.0.0_12345-release.apk",
            downloadUrl = "https://github.com/tiann/KernelSU/releases/download/v1.0.0/KernelSU_v1.0.0_12345-release.apk",
            size = 100L,
            digest = "sha256:$sha",
            changelog = "",
        )!!
        assertEquals(12345, info.versionCode)
        assertEquals("1.0.0", info.versionName)
    }

    @Test
    fun missingDigestIsNotVerifiable() {
        val info = parseManagerAsset(
            name = "MuSu_Next_0.0.9_9-release.apk",
            downloadUrl = "https://github.com/bilibiliHaoziyao/MuSuNext/releases/download/v0.0.9/x.apk",
            size = 100L,
            digest = null,
            changelog = "",
        )!!
        assertEquals("", info.sha256)
        assertFalse(info.isVerifiable)
    }

    @Test
    fun nonApkAndUnparseableNamesReturnNull() {
        assertNull(
            parseManagerAsset("ksud-aarch64-linux-android", "url", 1L, null, "")
        )
        assertNull(
            parseManagerAsset("random-release.apk", "url", 1L, null, "")
        )
    }
}
