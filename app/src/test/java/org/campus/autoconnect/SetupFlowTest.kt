package org.campus.autoconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupFlowTest {
    @Test fun firstRunStartsWithTheCurrentWifi() {
        assertEquals(SetupFlow.Step.WIFI, SetupFlow.next(false, false, false))
    }

    @Test fun savedWifiDoesNotAskForItAgain() {
        assertEquals(SetupFlow.Step.LOGIN, SetupFlow.next(true, false, false))
    }

    @Test fun storedPasswordDoesNotSilentlyAssumeHttpConsent() {
        assertEquals(SetupFlow.Step.LOGIN, SetupFlow.next(true, true, false))
    }

    @Test fun existingValidLoginSkipsDirectlyToOptionalAutomation() {
        assertEquals(SetupFlow.Step.READY, SetupFlow.next(true, true, true))
    }

    @Test fun neverRepeatedlyForcesPeopleWhoDeferred() {
        assertFalse(SetupFlow.shouldAutoOpen(false, false, true))
        assertFalse(SetupFlow.shouldAutoOpen(false, true, false))
        assertFalse(SetupFlow.shouldAutoOpen(true, false, false))
        assertTrue(SetupFlow.shouldAutoOpen(false, false, false))
    }
}
