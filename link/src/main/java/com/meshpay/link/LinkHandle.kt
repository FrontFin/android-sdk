package com.meshpay.link

import com.meshpay.link.internal.ActiveSession

/**
 * Entry point for host-initiated control of the active Link session.
 *
 * Opening Link is done with [LinkContract] and the Activity Result API; this
 * object is only for closing the session from your own UI (a custom button, a
 * timeout, a navigation event), the Android form of the web SDK's `close()`.
 */
public object LinkHandle {
    /**
     * Closes the active Link session, if one is open. Link is asked to close
     * gracefully first, so a user who already finished the flow is still reported
     * as [LinkExitStatus.SUCCESS]; after a short grace period the session is torn
     * down regardless. Does nothing when no session is active. Safe to call from
     * any thread.
     */
    public fun close() {
        ActiveSession.requestClose()
    }
}
