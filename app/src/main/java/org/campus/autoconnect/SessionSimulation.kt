package org.campus.autoconnect

/**
 * Pure in-memory model; no network, login, logout or device permissions.
 * Demonstrates timeouts and the conservative decision policy.
 */
object SessionSimulation {
    private enum class State { ONLINE, PORTAL, OUTAGE }
    private enum class Action { SLEEP, INSPECT_PORTAL, BACKOFF }

    private fun decide(state: State): Action = when (state) {
        State.ONLINE -> Action.SLEEP
        State.PORTAL -> Action.INSPECT_PORTAL
        State.OUTAGE -> Action.BACKOFF
    }

    fun run(): String {
        val cases = listOf(
            State.ONLINE to Action.SLEEP,
            State.PORTAL to Action.INSPECT_PORTAL,
            State.OUTAGE to Action.BACKOFF
        )
        var passed = 0
        cases.forEach { (state, expected) ->
            if (decide(state) == expected) passed++
        }
        // Ten independent clients observing an already-restored session must not submit login.
        val clients = (1..10).map { decide(State.ONLINE) }
        val safe = clients.all { it == Action.SLEEP }
        if (safe) passed++
        return "Offline simulation: $passed/4 checks passed\n" +
            "Active: no login\n" +
            "Expired: inspect portal before any action\n" +
            "Outage: back off, never assume credentials are needed\n" +
            "10 online clients: " + (if (safe) "zero login attempts" else "test failed") +
            "\nNo network requests were sent."
    }
}
