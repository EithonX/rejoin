package org.campus.autoconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkProfileSelectionTest {
    @Test fun exactRouterMatchingKeepsProfilesSeparate() {
        val a = TrustedNetworks.Profile("one", "Room", "StudentRouter",
            "00:11:22:33:44:55")
        val b = TrustedNetworks.Profile("two", "Library", "StudentRouter",
            "00:11:22:33:44:66")
        assertTrue(TrustedNetworks.matches(a, "StudentRouter", "00:11:22:33:44:55"))
        assertTrue(!TrustedNetworks.matches(a, b.ssid, b.bssid))
        assertTrue(TrustedNetworks.matches(b, b.ssid, b.bssid.uppercase()))
    }

    @Test fun defaultLoginWorksWithoutPerRouterSetup() {
        assertEquals("main", LoginSelection.choose(null, "main", listOf("main", "other")))
    }

    @Test fun assignedLoginOverridesDefault() {
        assertEquals("other", LoginSelection.choose("other", "main",
            listOf("main", "other")))
    }

    @Test fun deletingAssignedLoginDoesNotSilentlySubmitDefault() {
        assertNull(LoginSelection.choose("deleted", "main", listOf("main")))
    }

    @Test fun missingDefaultFallsBackToAnotherSavedLogin() {
        assertEquals("other", LoginSelection.choose(null, "deleted", listOf("other")))
    }

    @Test fun noSavedLoginsMeansNoAutomaticAuthentication() {
        assertNull(LoginSelection.choose(null, null, emptyList()))
    }
}
