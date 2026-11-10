package com.eithonx.rejoin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdateTest {
    private val digest = "c".repeat(64)

    @Test
    fun buildsUrlsOnlyForValidReleaseTags() {
        val update = ReleaseUpdate.parse("v2.4.0-rc1", 19, 23, 1_000_000, digest)!!
        assertEquals("2.4.0-rc1", update.version)
        assertEquals("https://github.com/EithonX/captive/releases/download/v2.4.0-rc1/Rejoin.apk", update.apkUrl)
        assertNull(ReleaseUpdate.parse("v2.4.0/../other", 19, 23, 1_000_000, digest))
    }

    @Test
    fun rejectsInvalidIntegrityMetadata() {
        assertNull(ReleaseUpdate.parse("v2.4.0", 19, 23, 0, digest))
        assertNull(ReleaseUpdate.parse("v2.4.0", 19, 23, 50_000_001, digest))
        assertNull(ReleaseUpdate.parse("v2.4.0", 19, 23, 1_000_000, "sha256:$digest"))
    }

    @Test
    fun requiresNewerCodeAndSupportedAndroid() {
        val update = ReleaseUpdate.parse("v2.4.0", 20, 24, 1_000_000, digest)!!
        assertFalse(update.isNewerAndCompatible(20, 35))
        assertFalse(update.isNewerAndCompatible(19, 23))
        assertTrue(update.isNewerAndCompatible(19, 24))
    }
}
