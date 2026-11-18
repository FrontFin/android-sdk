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
            mainHandler.post { current() }
        }
    }
}
