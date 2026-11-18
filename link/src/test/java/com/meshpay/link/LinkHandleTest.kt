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

/** [LinkHandle.close] delegates to the registered session closer (§2.3). */
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
}
