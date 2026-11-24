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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** The Android back button and gesture: history first, then ask Link to close. */
@RunWith(RobolectricTestRunner::class)
class LinkActivityBackTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val looper = shadowOf(Looper.getMainLooper())

    @Test
    fun `back press on a loaded Link asks it to close without a force-close timer`() {
        val activity = launch()
        postFromLink(activity, """{"type":"loaded"}""")
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

        postFromLink(activity, """{"type":"close","payload":{"status":"canceled"}}""")
        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), result(activity))
    }

    @Test
    fun `second back press within the exit window closes a loaded Link at once`() {
        val activity = launch()
        postFromLink(activity, """{"type":"loaded"}""")

        activity.onBackPressedDispatcher.onBackPressed()
        looper.idleFor(Duration.ofMillis(1_900))
        assertNull(shadowOf(activity).resultIntent)
        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), result(activity))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `second back press after the exit window asks Link again and restarts the window`() {
        val activity = launch()
        postFromLink(activity, """{"type":"loaded"}""")
        val webView = shadowOf(findWebView(activity.window.decorView))

        activity.onBackPressedDispatcher.onBackPressed()
        looper.idleFor(Duration.ofMillis(2_000))
        findWebView(activity.window.decorView).evaluateJavascript("0", null)
        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(CLOSE_REQUESTED_SCRIPT, webView.lastEvaluatedJavascript)
        assertNull(shadowOf(activity).resultIntent)

        looper.idleFor(Duration.ofMillis(1_000))
        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), result(activity))
    }

    @Test
    fun `back press on a loaded Link goes back through the WebView history first`() {
        val activity = launch()
        postFromLink(activity, """{"type":"loaded"}""")
        val realWebView = findWebView(activity.window.decorView)
        shadowOf(realWebView).setCanGoBack(true)

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, shadowOf(realWebView).goBackInvocations)
        assertNull(shadowOf(activity).resultIntent)
    }

    @Test
    fun `back press that goes back through history does not open the exit window`() {
        val activity = launch()
        postFromLink(activity, """{"type":"loaded"}""")
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
        val activity = launch()
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
        val activity = launch()

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), result(activity))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `back press on a failed page closes at once`() {
        val activity = launch()
        postFromLink(activity, """{"type":"loaded"}""")
        val realWebView = findWebView(activity.window.decorView)
        shadowOf(realWebView).setCanGoBack(true)
        shadowOf(realWebView).webViewClient.onReceivedHttpError(
            realWebView,
            mainFrameRequest("https://link.meshpay.com/failed"),
            WebResourceResponse("text/html", "utf-8", 500, "Server Error", emptyMap(), null),
        )

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), result(activity))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `back press during a host close on a loaded Link keeps Link's own result`() {
        val activity = launch()
        postFromLink(activity, """{"type":"loaded"}""")
        LinkHandle.close()
        looper.idle()

        activity.onBackPressedDispatcher.onBackPressed()
        assertNull(shadowOf(activity).resultIntent)
        postFromLink(activity, """{"type":"close","payload":{"status":"success"}}""")

        assertEquals(LinkResult.Exit(LinkExitStatus.SUCCESS), result(activity))
    }

    @Test
    fun `back press during a host close on a loaded Link exits after the grace timeout`() {
        val activity = launch()
        postFromLink(activity, """{"type":"loaded"}""")
        LinkHandle.close()
        looper.idle()

        activity.onBackPressedDispatcher.onBackPressed()
        assertNull(shadowOf(activity).resultIntent)
        looper.idleFor(Duration.ofMillis(CLOSE_GRACE_MS))

        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), result(activity))
    }

    @Test
    fun `back press during a host close before Link has loaded closes at once`() {
        val activity = launch()
        LinkHandle.close()
        looper.idle()

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), result(activity))
    }

    @Test
    fun `host close after back on a loaded Link still exits after the grace timeout`() {
        val activity = launch()
        postFromLink(activity, """{"type":"loaded"}""")

        activity.onBackPressedDispatcher.onBackPressed()
        LinkHandle.close()
        looper.idle()
        looper.idleFor(Duration.ofSeconds(5))

        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), result(activity))
    }

    // --- Helpers -------------------------------------------------------------

    private fun launch(): LinkActivity =
        Robolectric
            .buildActivity(
                LinkActivity::class.java,
                LinkActivity.newIntent(context, LinkConfiguration("tok", "cid")),
            ).setup()
            .get()

    private fun result(activity: LinkActivity): LinkResult? = LinkActivity.resultFrom(shadowOf(activity).resultIntent)

    /** Sends [json] through the SDK's `JSBridge`, the way Link's page does. */
    private fun postFromLink(
        activity: LinkActivity,
        json: String,
    ) {
        val bridge = checkNotNull(shadowOf(findWebView(activity.window.decorView)).getJavascriptInterface("JSBridge"))
        bridge.javaClass
            .getMethod("sendNativeMessage", String::class.java)
            .apply { isAccessible = true }
            .invoke(bridge, json)
        looper.idle()
    }

    private fun findWebView(view: View): WebView = findWebViewOrNull(view) ?: error("no WebView")

    private fun findWebViewOrNull(view: View): WebView? =
        view as? WebView
            ?: (view as? ViewGroup)?.let { group ->
                (0 until group.childCount).firstNotNullOfOrNull { findWebViewOrNull(group.getChildAt(it)) }
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

    private companion object {
        const val CLOSE_REQUESTED_SCRIPT = """window.postMessage({"type":"closeRequested"}, '*');true;"""
    }
}
