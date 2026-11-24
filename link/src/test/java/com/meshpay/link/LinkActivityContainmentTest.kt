package com.meshpay.link

import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import com.meshpay.link.internal.LinkActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * A foreign document in the main frame — e.g. committed by a `target=_top` POST,
 * which never reaches `shouldOverrideUrlLoading` — ends the session and is never
 * shown.
 */
@RunWith(RobolectricTestRunner::class)
class LinkActivityContainmentTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val linkUrl = "https://link.meshpay.com/"

    private fun launch(): ActivityController<LinkActivity> =
        Robolectric
            .buildActivity(
                LinkActivity::class.java,
                LinkActivity.newIntent(context, LinkConfiguration("tok", "cid")),
            ).setup()

    private fun LinkActivity.webView(): WebView? = findWebView(window.decorView)

    private fun findWebView(view: View): WebView? =
        when (view) {
            is WebView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
            else -> null
        }

    private fun WebView.client(): WebViewClient = shadowOf(this).webViewClient

    private fun LinkActivity.result(): LinkResult? = LinkActivity.resultFrom(shadowOf(this).resultIntent)

    private fun request(
        url: String,
        mainFrame: Boolean = true,
    ): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame(): Boolean = mainFrame

            override fun isRedirect(): Boolean = false

            override fun hasGesture(): Boolean = true

            override fun getMethod(): String = "GET"

            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

    private fun assertContained(activity: LinkActivity) {
        val result = activity.result()
        assertTrue(result is LinkResult.Error)
        assertEquals(LinkErrorCode.SESSION_INTERRUPTED, (result as LinkResult.Error).error.code)
        assertTrue(activity.isFinishing)
        assertNull("the foreign page must not stay on screen", activity.webView())
    }

    @Test
    fun `foreign https page started in the main frame ends the session`() {
        val activity = launch().get()
        val view = activity.webView()!!

        view.client().onPageStarted(view, "https://evil.example/login", null)

        assertContained(activity)
    }

    @Test
    fun `cleartext link host started in the main frame ends the session`() {
        val activity = launch().get()
        val view = activity.webView()!!

        view.client().onPageStarted(view, "http://link.meshpay.com/", null)

        assertContained(activity)
    }

    @Test
    fun `foreign history entry ends the session`() {
        val activity = launch().get()
        val view = activity.webView()!!

        view.client().doUpdateVisitedHistory(view, "https://evil.example/", false)

        assertContained(activity)
    }

    @Test
    fun `link origin pages keep the session`() {
        val activity = launch().get()
        val view = activity.webView()!!

        view.client().onPageStarted(view, "${linkUrl}deposit", null)
        view.client().doUpdateVisitedHistory(view, "${linkUrl}deposit?step=2", false)
        view.client().onPageStarted(view, "about:blank", null)

        assertNull(activity.result())
        assertFalse(activity.isFinishing)
        assertNotNull(activity.webView())
    }

    @Test
    fun `contained page can no longer navigate, open windows or leave the app`() {
        val activity = launch().get()
        val view = activity.webView()!!
        val client = view.client()
        val chrome = shadowOf(view).webChromeClient!!

        client.onPageStarted(view, "https://evil.example/", null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(client.shouldOverrideUrlLoading(view, request("metamask://wc?uri=x")))
        assertTrue(client.shouldOverrideUrlLoading(view, request(linkUrl)))
        assertFalse(chrome.onCreateWindow(view, false, true, android.os.Message()))
        assertNull(shadowOf(activity).nextStartedActivity)
        // Only the first result counts: still the containment error.
        assertEquals(LinkErrorCode.SESSION_INTERRUPTED, (activity.result() as LinkResult.Error).error.code)
    }

    @Test
    fun `wallet deep links still open before any foreign page`() {
        val activity = launch().get()
        val view = activity.webView()!!

        val cancelled = view.client().shouldOverrideUrlLoading(view, request("metamask://wc?uri=x"))

        assertTrue(cancelled)
        assertNotNull(shadowOf(activity).nextStartedActivity)
        assertNull(activity.result())
    }
}
