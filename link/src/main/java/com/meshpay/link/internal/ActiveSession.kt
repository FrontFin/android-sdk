package com.meshpay.link.internal

import android.os.Handler
import android.os.Looper

/**
 * Bridges [com.meshpay.link.LinkHandle.close] to the running [LinkActivity]. At
 * most one session is active at a time (§2.3), so a single slot is enough.
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
            // Re-check on the main thread: the session may have ended (and its
            // Activity been destroyed) between this post and the time it runs.
            mainHandler.post { if (closer === current) current() }
        }
    }
}
