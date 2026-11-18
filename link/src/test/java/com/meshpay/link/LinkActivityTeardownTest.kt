package com.meshpay.link

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.meshpay.link.internal.LinkActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun `back press on a loaded Link asks it to close without a force-close timer`() {
        val activity = launch().get()
        activity.onBridgeMessage("""{"type":"loaded"}""")
        val realWebView = findWebView(activity.window.decorView)
        val webView = shadowOf(realWebView)

        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(CLOSE_REQUESTED_SCRIPT, webView.lastEvaluatedJavascript)
        looper.idleFor(Duration.ofSeconds(5))
        assertNull(shadowOf(activity).resultIntent)

        realWebView.evaluateJavascript("0", null)
        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(CLOSE_REQUESTED_SCRIPT, webView.lastEvaluatedJavascript)
        assertNull(shadowOf(activity).resultIntent)

        activity.onBridgeMessage("""{"type":"close","payload":{"status":"canceled"}}""")
        assertNotNull(shadowOf(activity).resultIntent)
    }

    @Test
    fun `back press before Link has loaded still exits after the grace timeout`() {
        val activity = launch().get()

        activity.onBackPressedDispatcher.onBackPressed()
        looper.idleFor(Duration.ofSeconds(5))

        assertNotNull(shadowOf(activity).resultIntent)
    }

    @Test
    fun `host close after back on a loaded Link still exits after the grace timeout`() {
        val activity = launch().get()
        activity.onBridgeMessage("""{"type":"loaded"}""")

        activity.onBackPressedDispatcher.onBackPressed()
        LinkHandle.close()
        shadowOf(Looper.getMainLooper()).idle()
        looper.idleFor(Duration.ofSeconds(5))

        assertNotNull(shadowOf(activity).resultIntent)
    }

    private fun findWebView(view: View): WebView = findWebViewOrNull(view) ?: error("no WebView")

    private fun findWebViewOrNull(view: View): WebView? =
        view as? WebView
            ?: (view as? ViewGroup)?.let { group ->
                (0 until group.childCount).firstNotNullOfOrNull { findWebViewOrNull(group.getChildAt(it)) }
            }

    private companion object {
        const val CLOSE_REQUESTED_SCRIPT = """window.postMessage({"type":"closeRequested"}, '*');true;"""
    }
}
