package com.eithonx.rejoin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class FortinetPortalTest {
    @Test fun usesTheCurrentRedirect() {
        val result = InternetVerifier.interpretPublicHttp(
            "HTTP/1.1 302 Found\r\nLocation: http://172.16.2.1:1000/fgtauth?fresh-token", ""
        )
        assertEquals(InternetVerifier.State.PORTAL, result?.state)
        assertEquals("/fgtauth?fresh-token", result?.portalPath)
    }

    @Test fun classifiesRedirectsBeforeVerification() {
        val secure = InternetVerifier.interpretPublicHttp(
            "HTTP/1.1 302 Found\r\nLocation: https://172.16.2.1:1000/fgtauth", ""
        )
        val changed = InternetVerifier.interpretPublicHttp(
            "HTTP/1.1 302 Found\r\nLocation: https://other.test/portal", ""
        )
        assertEquals(InternetVerifier.State.SECURE_PORTAL, secure?.state)
        assertEquals(InternetVerifier.State.UNSUPPORTED_PORTAL, changed?.state)
        assertNull(secure?.portalPath)
        assertNull(changed?.portalPath)
    }

    @Test fun ordinaryHttpRedirectDoesNotObscureVerifiedInternet() {
        val redirected = InternetVerifier.interpretPublicHttp(
            "HTTP/1.1 301 Moved Permanently\r\nLocation: https://1.1.1.1/cdn-cgi/trace", ""
        )
        assertEquals(InternetVerifier.State.UNSUPPORTED_PORTAL, redirected?.state)
        val result = InternetVerifier.resolveProbes(
            redirected,
            https = { InternetVerifier.Result(InternetVerifier.State.ONLINE, "HTTPS verified") },
            wifiState = { InternetVerifier.State.UNKNOWN }
        )
        assertEquals(InternetVerifier.State.ONLINE, result.state)
        assertNull(result.portalPath)
    }

    @Test fun ambiguousRedirectUsesAndroidValidationOrKeepsSignInPaused() {
        val redirected = InternetVerifier.Result(InternetVerifier.State.UNSUPPORTED_PORTAL, "Redirect")
        val https = { InternetVerifier.Result(InternetVerifier.State.UNKNOWN, "HTTPS inconclusive") }
        val online = InternetVerifier.resolveProbes(redirected, https) { InternetVerifier.State.ONLINE }
        val captive = InternetVerifier.resolveProbes(redirected, https) { InternetVerifier.State.PORTAL }
        assertEquals(InternetVerifier.State.ONLINE, online.state)
        assertEquals(InternetVerifier.State.UNSUPPORTED_PORTAL, captive.state)
        assertNull(captive.portalPath)
    }

    @Test fun confirmedCampusPortalAndCertificateFailuresRemainActionable() {
        val portal = InternetVerifier.Result(InternetVerifier.State.PORTAL, "Portal", "/fgtauth")
        val recognized = InternetVerifier.resolveProbes(portal, { error("Portal was already identified") }) {
            InternetVerifier.State.ONLINE
        }
        val certificate = InternetVerifier.resolveProbes(
            InternetVerifier.Result(InternetVerifier.State.UNSUPPORTED_PORTAL, "Redirect"),
            https = { InternetVerifier.Result(InternetVerifier.State.CERTIFICATE_ERROR, "Certificate failed") },
            wifiState = { InternetVerifier.State.ONLINE }
        )
        assertEquals(InternetVerifier.State.PORTAL, recognized.state)
        assertEquals("/fgtauth", recognized.portalPath)
        assertEquals(InternetVerifier.State.CERTIFICATE_ERROR, certificate.state)
    }

    @Test fun ignoresRejectionMessagesInScripts() {
        assertFalse(FortinetPortal.rejectsCredentials("<script>const message = 'invalid password';</script>"))
        assertTrue(FortinetPortal.rejectsCredentials("<p>Invalid <strong>username</strong> or password.</p>"))
    }

    @Test fun validatesFormActionsAndDestinations() {
        assertEquals("/", FortinetPortal.requestPath("/", fromGateway = true))
        assertEquals("/login?next=home", FortinetPortal.requestPath("/login?next=home", fromGateway = true))
        assertNull(FortinetPortal.requestPath("http://example.test:1000/fgtauth"))
        assertNull(FortinetPortal.requestPath("//example.test/login", fromGateway = true))
        assertNull(FortinetPortal.requestPath("http://user@172.16.2.1:1000/login"))
        assertNull(FortinetPortal.requestPath("http://172.16.2.1:1000/login/../fgtauth"))
        assertNull(FortinetPortal.requestPath("https://172.16.2.1:1000/login"))
    }
}
