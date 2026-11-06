package com.meshpay.link

import android.os.Handler
import android.os.Looper

/**
 * Entry point for host-initiated control of the active Link session.
 *
 * Opening Link is done with [LinkContract] and the Activity Result API; this
 * object is only for closing the session from your own UI (a custom button, a
 * timeout, a navigation event), the Android form of the web SDK's `close()`.
 */
public object MeshPayLink {

    /**
     * Closes the active Link session, if one is open. Link is asked to close
     * gracefully first, so a user who already finished the flow is still reported
     * as [LinkExitStatus.SUCCESS]; after a short grace period the session is torn
     * down regardless. Does nothing when no session is active. Safe to call from
     * any thread.
     */
    public fun closeActiveSession() {
        ActiveSession.requestClose()
    }
}

/**
 * Bridges [MeshPayLink.closeActiveSession] to the running `LinkActivity`. At most
 * one session is active at a time (§2.3), so a single slot is enough.
 */
internal object ActiveSession {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var closer: (() -> Unit)? = null

    fun register(closer: () -> Unit) {
        this.closer = closer
    }

    fun unregister(closer: () -> Unit) {
        if (this.closer === closer) this.closer = null
    }

    fun requestClose() {
        val current = closer ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            current()
        } else {
            mainHandler.post { current() }
        }
    }
}
