package org.campus.autoconnect

/** Pure first-run progress rules, separate from Android views and persistence. */
object SetupFlow {
    enum class Step { WIFI, LOGIN, READY }

    fun next(hasSavedRouter: Boolean, hasSavedLogin: Boolean, httpConsent: Boolean): Step =
        when {
            !hasSavedRouter -> Step.WIFI
            !hasSavedLogin || !httpConsent -> Step.LOGIN
            else -> Step.READY
        }

    fun shouldAutoOpen(hasCompleteSetup: Boolean, completedBefore: Boolean,
                       deferred: Boolean): Boolean =
        !hasCompleteSetup && !completedBefore && !deferred
}
