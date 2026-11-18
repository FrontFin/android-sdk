package com.meshpay.link

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.meshpay.link.internal.LinkActivity
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** Teardown leaves nothing behind that runs against (and so retains) the destroyed Activity. */
@RunWith(RobolectricTestRunner::class)
class LinkActivityTeardownTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val looper = shadowOf(Looper.getMainLooper())

    private fun launch() =
        Robolectric
            .buildActivity(
                LinkActivity::class.java,
                LinkActivity.newIntent(context, LinkConfiguration("tok", "cid")),
            ).setup()

    @Test
    fun `no load guard fires after destroy`() {
        val controller = launch()

        controller.pause().stop().destroy()
        looper.idleFor(Duration.ofMinutes(5))

        assertNull(shadowOf(controller.get()).resultIntent)
    }

    @Test
    fun `host close queued before destroy does nothing after it`() {
        val controller = launch()
        // From a background thread, so ActiveSession posts the close to the main looper.
        Thread { LinkHandle.close() }.apply {
            start()
            join()
        }

        controller.pause().stop().destroy()
        looper.idleFor(Duration.ofMinutes(5))

        assertNull(shadowOf(controller.get()).resultIntent)
    }
}
