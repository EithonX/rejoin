package com.eithonx.rejoin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdateTest {
    private val digest = "sha256:" + "c".repeat(64)

    @Test fun readsGitHubAssetMetadata() {
        val update = ReleaseUpdate.parse("v2.4.0-rc3", 1_000_000, digest)!!
        assertEquals("2.4.0-rc3", update.version)
        assertEquals("c".repeat(64), update.sha256)
        assertEquals("https://github.com/EithonX/rejoin/releases/download/v2.4.0-rc3/Rejoin.apk", update.apkUrl)
        assertNull(ReleaseUpdate.parse("v2.4.0/../other", 1_000_000, digest))
    }

    @Test fun rejectsMalformedMetadata() {
        assertNull(ReleaseUpdate.parse("v2.4.0", 1_000_000, ""))
        assertNull(ReleaseUpdate.parse("v2.4.0", 1_000_000, "sha256:abc"))
        assertNull(ReleaseUpdate.parse("v2.4.0", 0, digest))
        assertNull(ReleaseUpdate.parse("v2.4.0", 50_000_001, digest))
    }

    @Test fun sortsNumberedReleaseCandidatesAndStableVersions() {
        val rc2 = ReleaseUpdate.parse("v2.4.0-rc2", 1_000_000, digest)!!
        val rc10 = ReleaseUpdate.parse("v2.4.0-rc10", 1_000_000, digest)!!
        val stable = ReleaseUpdate.parse("v2.4.0", 1_000_000, digest)!!
        assertTrue(rc10.isNewerThan("2.4.0-rc2"))
        assertTrue(stable.isNewerThan("2.4.0-rc10"))
        assertFalse(rc10.isNewerThan("2.4.0"))
        assertFalse(rc2.isNewerThan("2.4.0-rc2"))
        assertEquals(rc10, ReleaseUpdate.latest(listOf(rc10, rc2)))
        assertEquals(stable, ReleaseUpdate.latest(listOf(rc10, stable, rc2)))
    }
}
