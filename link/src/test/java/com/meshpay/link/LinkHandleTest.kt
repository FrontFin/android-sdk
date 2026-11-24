package com.meshpay.link

import android.os.Looper
import com.meshpay.link.internal.ActiveSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [LinkHandle.close] delegates to the registered session closer. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinkHandleTest {
    @Test
    fun `close invokes the active session closer`() {
        var closed = false
        ActiveSession.register { closed = true }
        LinkHandle.close()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(closed)
        ActiveSession.register { } // reset the shared slot
    }

    @Test
    fun `close does nothing after the closer is unregistered`() {
        var closed = false
        val closer = { closed = true }
        ActiveSession.register(closer)
        ActiveSession.unregister(closer)
        LinkHandle.close()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(closed)
    }

    @Test
    fun `close from a background thread runs the closer on the main thread`() {
        var closedOnMain = false
        val closer = { closedOnMain = Looper.myLooper() == Looper.getMainLooper() }
        ActiveSession.register(closer)
        closeFromBackgroundThread()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(closedOnMain)
        ActiveSession.unregister(closer)
    }

    @Test
    fun `close posted from a background thread is dropped if the session ended first`() {
        var closed = false
        val closer = { closed = true }
        ActiveSession.register(closer)
        closeFromBackgroundThread() // posted to the main thread, not run yet
        ActiveSession.unregister(closer) // the Activity is destroyed in between
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(closed)
    }

    @Test
    fun `close posted from a background thread is dropped if another session replaced it`() {
        var oldClosed = false
        var newClosed = false
        val oldCloser = { oldClosed = true }
        ActiveSession.register(oldCloser)
        closeFromBackgroundThread()
        val newCloser = { newClosed = true }
        ActiveSession.register(newCloser) // a new session opened before the post ran
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(oldClosed)
        assertFalse(newClosed)
        ActiveSession.unregister(newCloser)
    }

    private fun closeFromBackgroundThread() {
        Thread { LinkHandle.close() }.apply {
            start()
            join()
        }
    }
}
