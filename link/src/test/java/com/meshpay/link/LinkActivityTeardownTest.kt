package com.meshpay.link

import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.meshpay.link.internal.CLOSE_GRACE_MS
import com.meshpay.link.internal.LinkActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `second back press within the exit window closes a loaded Link at once`() {
        val activity = launch().get()
        activity.onBridgeMessage("""{"type":"loaded"}""")

        activity.onBackPressedDispatcher.onBackPressed()
        looper.idleFor(Duration.ofMillis(1_900))
        assertNull(shadowOf(activity).resultIntent)
        activity.onBackPressedDispatcher.onBackPressed()

        assertNotNull(shadowOf(activity).resultIntent)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `second back press after the exit window asks Link again and restarts the window`() {
        val activity = launch().get()
        activity.onBridgeMessage("""{"type":"loaded"}""")
        val webView = shadowOf(findWebView(activity.window.decorView))

        activity.onBackPressedDispatcher.onBackPressed()
        looper.idleFor(Duration.ofMillis(2_000))
        findWebView(activity.window.decorView).evaluateJavascript("0", null)
        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(CLOSE_REQUESTED_SCRIPT, webView.lastEvaluatedJavascript)
        assertNull(shadowOf(activity).resultIntent)

        looper.idleFor(Duration.ofMillis(1_000))
        activity.onBackPressedDispatcher.onBackPressed()
        assertNotNull(shadowOf(activity).resultIntent)
    }

    @Test
    fun `back press that goes back through history does not open the exit window`() {
        val activity = launch().get()
        activity.onBridgeMessage("""{"type":"loaded"}""")
        val realWebView = findWebView(activity.window.decorView)
        shadowOf(realWebView).setCanGoBack(true)

        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(realWebView).setCanGoBack(false)
        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(CLOSE_REQUESTED_SCRIPT, shadowOf(realWebView).lastEvaluatedJavascript)
        assertNull(shadowOf(activity).resultIntent)
    }

    @Test
    fun `back press after the page finished but before Link posts loaded closes at once`() {
        val activity = launch().get()
        val realWebView = findWebView(activity.window.decorView)
        val webView = shadowOf(realWebView)
        webView.webViewClient.onPageFinished(realWebView, webView.lastLoadedUrl)
        webView.setCanGoBack(true)

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(0, webView.goBackInvocations)
        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), result(activity))
    }

    @Test
    fun `back press before Link has loaded closes at once`() {
        val activity = launch().get()

        activity.onBackPressedDispatcher.onBackPressed()

        assertNotNull(shadowOf(activity).resultIntent)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `back press on a failed page closes at once`() {
        val activity = launch().get()
        activity.onBridgeMessage("""{"type":"loaded"}""")
        val realWebView = findWebView(activity.window.decorView)
        shadowOf(realWebView).setCanGoBack(true)
        shadowOf(realWebView).webViewClient.onReceivedHttpError(
            realWebView,
            mainFrameRequest("https://link.meshpay.com/failed"),
            WebResourceResponse("text/html", "utf-8", 500, "Server Error", emptyMap(), null),
        )

        activity.onBackPressedDispatcher.onBackPressed()

        assertNotNull(shadowOf(activity).resultIntent)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `back press during a host close on a loaded Link keeps Link's own result`() {
        val activity = launch().get()
        activity.onBridgeMessage("""{"type":"loaded"}""")
        LinkHandle.close()
        looper.idle()

        activity.onBackPressedDispatcher.onBackPressed()
        assertNull(shadowOf(activity).resultIntent)
        activity.onBridgeMessage("""{"type":"close","payload":{"status":"success"}}""")

        assertEquals(LinkResult.Exit(LinkExitStatus.SUCCESS), result(activity))
    }

    @Test
    fun `back press during a host close on a loaded Link exits after the grace timeout`() {
        val activity = launch().get()
        activity.onBridgeMessage("""{"type":"loaded"}""")
        LinkHandle.close()
        looper.idle()

        activity.onBackPressedDispatcher.onBackPressed()
        assertNull(shadowOf(activity).resultIntent)
        looper.idleFor(Duration.ofMillis(CLOSE_GRACE_MS))

        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), result(activity))
    }

    @Test
    fun `back press during a host close before Link has loaded closes at once`() {
        val activity = launch().get()
        LinkHandle.close()
        looper.idle()

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), result(activity))
    }

    private fun result(activity: LinkActivity): LinkResult? = LinkActivity.resultFrom(shadowOf(activity).resultIntent)

    @Test
    fun `back press on a loaded Link goes back through the WebView history first`() {
        val activity = launch().get()
        activity.onBridgeMessage("""{"type":"loaded"}""")
        val realWebView = findWebView(activity.window.decorView)
        shadowOf(realWebView).setCanGoBack(true)

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, shadowOf(realWebView).goBackInvocations)
        assertNull(shadowOf(activity).resultIntent)
    }

    private fun mainFrameRequest(url: String): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame(): Boolean = true

            override fun isRedirect(): Boolean = false

            override fun hasGesture(): Boolean = false

            override fun getMethod(): String = "GET"

            override fun getRequestHeaders(): Map<String, String> = emptyMap()
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
