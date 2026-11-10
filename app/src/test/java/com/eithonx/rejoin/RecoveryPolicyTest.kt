package com.eithonx.rejoin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPolicyTest {
    @Test fun balancedIsQuieterWhenHealthy() {
        assertEquals(30, RecoveryPolicy.minutes(
            RecoveryPolicy.Mode.BALANCED, RecoveryPolicy.Outcome.HEALTHY))
        assertEquals(15, RecoveryPolicy.minutes(
            RecoveryPolicy.Mode.RESPONSIVE, RecoveryPolicy.Outcome.HEALTHY))
    }

    @Test fun allPoliciesHaveANonAggressiveMinimumInterval() {
        for (mode in RecoveryPolicy.Mode.values()) {
            for (outcome in RecoveryPolicy.Outcome.values()) {
                assertTrue(RecoveryPolicy.minutes(mode, outcome) >= 15)
            }
        }
    }

    @Test fun successfulLoginAndInternetAreNotMistakenForPortal() {
        assertEquals(RecoveryPolicy.Outcome.RECOVERED,
            RecoveryPolicy.fromMessage("LOGIN VERIFIED — internet restored"))
        assertEquals(RecoveryPolicy.Outcome.HEALTHY,
            RecoveryPolicy.fromMessage("Internet online; no login necessary"))
    }

    @Test fun uncertaintyDoesNotTriggerLoginState() {
        assertEquals(RecoveryPolicy.Outcome.UNKNOWN,
            RecoveryPolicy.fromMessage("Network state uncertain; no password sent"))
    }

    @Test fun cooldownTakesPriorityOverPortal() {
        assertEquals(RecoveryPolicy.Outcome.FAILED,
            RecoveryPolicy.fromMessage("Portal detected; retry cooldown active"))
    }
}
