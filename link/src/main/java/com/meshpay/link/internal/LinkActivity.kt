package com.meshpay.link.internal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.meshpay.link.BuildConfig
import com.meshpay.link.LinkConfiguration
import com.meshpay.link.LinkError
import com.meshpay.link.LinkErrorCode
import com.meshpay.link.LinkExitStatus
import com.meshpay.link.LinkResult

/**
 * Hosts the Link WebView full screen and runs the session lifecycle (§2.3, §5.3).
 * Not launched directly: [com.meshpay.link.LinkContract] creates its Intent, and
 * the Activity Result API delivers [LinkResult] exactly once.
 */
internal class LinkActivity : ComponentActivity() {
    private lateinit var config: LinkConfiguration
    private lateinit var built: BuiltLinkUrl
    private lateinit var launcher: ExternalLauncher
    private lateinit var root: FrameLayout
    private var webView: WebView? = null

    /**
     * WebViews off-screen and awaiting destruction: throwaway popups from
     * `onCreateWindow` and a main WebView whose renderer died. Destroyed on the
     * next main-loop turn (never inside their own callback) or in `onDestroy`.
     */
    private val orphans = mutableListOf<WebView>()
    private lateinit var closeButton: LinkCloseButton
    private lateinit var loader: LinkLoader

    private val main = Handler(Looper.getMainLooper())
    private val loadTimeout =
        Runnable {
            // Same message as react-native-sdk.
            val message = "Link did not load within ${LINK_LOAD_TIMEOUT_MS}ms."
            finishWith(LinkResult.Error(LinkError(LinkErrorCode.LOAD_TIMEOUT, message)))
        }
    private val graceTimeout = Runnable { cancel() }
    private val revealCloseButton = Runnable { closeButton.visibility = View.VISIBLE }
    private val revealLoader = Runnable { loader.visibility = View.VISIBLE }

    private var done = false
    private var closing = false
    private var loaded = false
    private var navigationFailed = false

    /**
     * Set once a foreign document reached the main frame (PRG-2294). From then
     * on nothing that page does — navigations, popups, deep links — leaves the
     * SDK, even at the moment before the WebView is destroyed.
     */
    private var contained = false

    private val closeRequester = { requestClose() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A recreation with saved state means process death (config changes are
        // handled in the manifest, so they never land here). The token is
        // single-use, so the session cannot be resumed: report it and stop.
        if (savedInstanceState != null) {
            deliverResult(
                LinkResult.Error(
                    LinkError(LinkErrorCode.SESSION_INTERRUPTED, "The session could not be resumed."),
                ),
            )
            done = true
            finish()
            return
        }

        val incoming = intentConfig(intent)
        if (incoming == null) {
            deliverResult(LinkResult.Error(LinkError(LinkErrorCode.LOAD_ERROR, "Missing configuration.")))
            done = true
            finish()
            return
        }
        config = incoming
        launcher = ExternalLauncher(this)

        // A blank token/clientId is reported as load-error, never thrown to the
        // host app: the builder throws internally and is caught here (§2.2, §2.3).
        built =
            try {
                buildLinkUrl(config.token, config.clientId, config.options)
            } catch (e: IllegalArgumentException) {
                deliverResult(
                    LinkResult.Error(LinkError(LinkErrorCode.LOAD_ERROR, e.message ?: "Invalid configuration.")),
                )
                done = true
                finish()
                return
            }
        val dark = isNativeDark(config.options.theme, config.token, isSystemDark())

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark

        root =
            FrameLayout(this).apply {
                setBackgroundColor(if (dark) BACKGROUND_DARK else BACKGROUND_LIGHT)
            }
        setContentView(root)
        addWebView()
        addCloseButton(dark)
        addLoader(dark)
        applyInsets()

        ActiveSession.register(closeRequester)
        onBackPressedDispatcher.addCallback(this, backCallback)

        startLoadGuards()
        webView?.loadUrl(built.href)
    }

    // --- View construction ---------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun addWebView() {
        val view = WebView(this)
        webView = view
        view.setBackgroundColor(Color.TRANSPARENT)
        view.webViewClient = linkWebViewClient
        view.webChromeClient = linkChromeClient
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            androidx.webkit.WebSettingsCompat.setSafeBrowsingEnabled(view.settings, true)
        }
        installBridge(view)
        installDocumentStartScript(view)

        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /** The native ✕, drawn like react-native-sdk's `CloseButton`. Top-right, in the
     *  same spot as Link's own header close, so it does not jump when Link loads.
     *
     *  `RIGHT`, not `END`, on purpose: Link's header close stays top-right in every
     *  locale, and `applyInsets` sets `rightMargin` to match. `END` would move the
     *  ✕ to the left in RTL with no inset on that side. */
    @SuppressLint("RtlHardcoded")
    private fun addCloseButton(dark: Boolean) {
        closeButton =
            LinkCloseButton(this, foregroundColor(dark)).apply {
                visibility = View.GONE
                setOnClickListener { requestClose() }
            }
        root.addView(
            closeButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.RIGHT,
            ),
        )
    }

    /** A small centred spinning ring shown while Link loads, drawn like
     *  react-native-sdk's `Loader`. It never blocks touches and is shown only
     *  before Link's `loaded`. */
    private fun addLoader(dark: Boolean) {
        loader = LinkLoader(this, foregroundColor(dark)).apply { visibility = View.GONE }
        root.addView(
            loader,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
    }

    private fun foregroundColor(dark: Boolean): Int = if (dark) BACKGROUND_LIGHT else BACKGROUND_DARK

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Edge-to-edge turns off adjustResize on Android 11+, so shrink the
            // WebView above the keyboard ourselves (as react-native-sdk's does).
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            webView?.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = bars.top
                bottomMargin = maxOf(bars.bottom, ime.bottom)
                leftMargin = bars.left
                rightMargin = bars.right
            }
            // 16 dp from the safe area's top-right corner, less the hit slop the
            // view is padded with, so the drawn circle lands where RN's does.
            val density = resources.displayMetrics.density
            val offset = ((CLOSE_BUTTON_INSET_DP - CLOSE_BUTTON_HIT_SLOP_DP) * density).toInt()
            closeButton.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = bars.top + offset
                rightMargin = bars.right + offset
            }
            insets
        }
    }

    // --- Bridge --------------------------------------------------------------

    private fun installBridge(view: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            // The WebView restricts the channel to the Link origin and reports the
            // sending frame, so no extra origin/frame check is needed here.
            WebViewCompat.addWebMessageListener(
                view,
                "JSBridge",
                setOf(built.origin),
                object : WebViewCompat.WebMessageListener {
                    override fun onPostMessage(
                        webView: WebView,
                        message: WebMessageCompat,
                        sourceOrigin: android.net.Uri,
                        isMainFrame: Boolean,
                        replyProxy: JavaScriptReplyProxy,
                    ) {
                        if (isMainFrame) onBridgeMessage(message.data)
                    }
                },
            )
        } else {
            view.addJavascriptInterface(LegacyJsBridge(), "JSBridge")
        }
    }

    /** Fallback bridge for old WebViews. Visible to every frame, so the current
     *  main-frame URL origin is checked before a message is processed (§5.3, §5.5). */
    private inner class LegacyJsBridge {
        @android.webkit.JavascriptInterface
        fun sendNativeMessage(json: String?) {
            main.post {
                val origin = webView?.url?.let { getOrigin(it) }
                if (origin == built.origin) onBridgeMessage(json)
            }
        }
    }

    private fun installDocumentStartScript(view: WebView) {
        val script = buildDocumentStartScript(BuildConfig.SDK_VERSION)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(view, script, setOf(built.origin))
        }
        // Otherwise the globals are injected on Link's `loaded` message, below.
    }

    private fun onBridgeMessage(json: String?) {
        when (val message = parseLinkMessage(json)) {
            is LinkMessage.Close -> finishWith(LinkResult.Exit(message.status))
            LinkMessage.Loaded -> onLinkLoaded()
            null -> Unit
        }
    }

    private fun onLinkLoaded() {
        main.removeCallbacks(loadTimeout)
        main.removeCallbacks(revealCloseButton)
        main.removeCallbacks(revealLoader)
        loaded = true
        navigationFailed = false
        closeButton.visibility = View.GONE
        loader.visibility = View.GONE
        val view = webView ?: return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            view.evaluateJavascript(buildDocumentStartScript(BuildConfig.SDK_VERSION), null)
        }
        view.evaluateJavascript(buildSdkSpecsScript(BuildConfig.SDK_VERSION), null)
    }

    // --- WebView clients -----------------------------------------------------

    private val linkWebViewClient =
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                if (contained) return true
                val decision = decideNavigation(request.url.toString(), request.isForMainFrame, built.origin)
                if (decision is NavigationDecision.OpenExternal) launcher.open(decision)
                // Return true to cancel; only `Load` proceeds in the WebView.
                return decision !is NavigationDecision.Load
            }

            // Second origin gate. Called once per main-frame document, POST
            // included, which `shouldOverrideUrlLoading` never sees.
            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: Bitmap?,
            ) {
                enforceMainFrameOrigin(view, url)
            }

            // Also checked on every history change, so the gate does not rest on
            // a single callback.
            override fun doUpdateVisitedHistory(
                view: WebView,
                url: String,
                isReload: Boolean,
            ) {
                enforceMainFrameOrigin(view, url)
            }

            override fun onPageFinished(
                view: WebView,
                url: String,
            ) {
                // Fallback for Link builds that never post `loaded`: the main-frame
                // page finished, so stop the load timer.
                if (getOrigin(url) == built.origin) main.removeCallbacks(loadTimeout)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) onMainFrameLoadFailed()
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame) onMainFrameLoadFailed()
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                // MUST return true; returning false crashes the host app (§2.3).
                // Detach now, destroy after this callback returns.
                webView = null
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                orphans += view
                destroyLater(view)
                if (closing) {
                    cancel()
                } else {
                    finishWith(
                        LinkResult.Error(
                            LinkError(LinkErrorCode.SESSION_INTERRUPTED, "The Link WebView stopped."),
                        ),
                    )
                }
                return true
            }
        }

    private val linkChromeClient =
        object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message,
            ): Boolean {
                if (contained) return false
                // Read the target URL through a throwaway WebView and route it, so
                // `window.open` / `target=_blank` reaches the wallet or browser (N4).
                // A popup that never navigates (`window.open()` with no URL, or
                // `about:blank` written to by script) is tracked so it is still
                // destroyed in `onDestroy` and cannot outlive this Activity.
                val popup = WebView(this@LinkActivity)
                orphans += popup
                popup.webViewClient =
                    object : WebViewClient() {
                        private var routed = false

                        override fun shouldOverrideUrlLoading(
                            v: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            // Route only the first URL: a redirect can arrive
                            // before the deferred destroy runs.
                            if (routed) return true
                            routed = true
                            val decision = decideNewWindow(request.url.toString())
                            if (decision is NavigationDecision.OpenExternal && !contained) launcher.open(decision)
                            // Never destroy a WebView from inside its own callback.
                            destroyLater(v)
                            return true
                        }

                        override fun onRenderProcessGone(
                            v: WebView,
                            detail: RenderProcessGoneDetail,
                        ): Boolean {
                            // The popup shares Link's renderer; the default `false`
                            // would crash the host app (§2.3).
                            destroyLater(v)
                            return true
                        }
                    }
                // `window.close()` from the popup lands on the popup's own chrome
                // client; release it then instead of holding it until `onDestroy`.
                popup.webChromeClient =
                    object : WebChromeClient() {
                        override fun onCloseWindow(window: WebView) {
                            destroyLater(window)
                        }
                    }
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean = true
        }

    /**
     * Ends the session when a document outside the Link origin is in the main
     * frame. The WebView is detached at once, so the page is never shown, and
     * destroyed after this callback returns.
     */
    private fun enforceMainFrameOrigin(
        view: WebView,
        url: String,
    ) {
        if (contained || !isForeignTopFrameDocument(url, built.origin)) return
        contained = true
        view.stopLoading()
        if (view === webView) {
            webView = null
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            orphans += view
            destroyLater(view)
        }
        finishWith(
            LinkResult.Error(
                LinkError(LinkErrorCode.SESSION_INTERRUPTED, "Link navigated to a page outside its origin."),
            ),
        )
    }

    private fun onMainFrameLoadFailed() {
        if (loaded && !closing) {
            // A page the user may be halfway through failed: keep the session and
            // surface the native close button instead of discarding it.
            navigationFailed = true
            closeButton.visibility = View.VISIBLE
        } else {
            onPreLoadFailure()
        }
    }

    private fun onPreLoadFailure() {
        if (closing) {
            cancel()
        } else {
            finishWith(LinkResult.Error(LinkError(LinkErrorCode.LOAD_ERROR, "The Link page failed to load.")))
        }
    }

    // --- Lifecycle control ---------------------------------------------------

    private fun startLoadGuards() {
        main.postDelayed(loadTimeout, LINK_LOAD_TIMEOUT_MS)
        main.postDelayed(revealCloseButton, CLOSE_BUTTON_DELAY_MS)
        main.postDelayed(revealLoader, LOADER_DELAY_MS)
    }

    /** Host- or user-initiated close: ask Link to close, then tear down. */
    private fun requestClose() {
        if (done || closing) return
        closing = true
        main.removeCallbacks(loadTimeout)
        webView?.evaluateJavascript(buildCloseRequestedScript(), null)
        main.postDelayed(graceTimeout, CLOSE_GRACE_MS)
    }

    /** Link has loaded and its page has not failed, so it can answer a close request. */
    private val linkReady: Boolean
        get() = loaded && !navigationFailed

    private val backCallback =
        object : OnBackPressedCallback(true) {
            /** Uptime of the last Back that asked Link to close; `null` if none. */
            private var lastCloseRequestAt: Long? = null

            override fun handleOnBackPressed() {
                val view = webView
                when {
                    // Nothing can answer Back: close at once.
                    view == null || !linkReady -> cancel()
                    // A close is under way: Link's own `close` (which may report SUCCESS)
                    // or the grace timer ends it.
                    closing -> Unit
                    view.canGoBack() -> view.goBack()
                    // A second Back within the window is the way out when Link does not
                    // answer, or when the user does not want its exit prompt.
                    withinBackExitWindow() -> cancel()
                    else -> {
                        // Link decides for itself (it may open an exit prompt), so no
                        // force-close timer runs.
                        lastCloseRequestAt = SystemClock.uptimeMillis()
                        view.evaluateJavascript(buildCloseRequestedScript(), null)
                    }
                }
            }

            private fun withinBackExitWindow(): Boolean {
                val last = lastCloseRequestAt ?: return false
                return SystemClock.uptimeMillis() - last < BACK_EXIT_WINDOW_MS
            }
        }

    private fun cancel() = finishWith(LinkResult.Exit(LinkExitStatus.CANCELED))

    private fun finishWith(result: LinkResult) {
        if (done) return
        done = true
        clearTimers()
        deliverResult(result)
        finish()
    }

    private fun deliverResult(result: LinkResult) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, result))
    }

    private fun clearTimers() {
        main.removeCallbacks(loadTimeout)
        main.removeCallbacks(graceTimeout)
        main.removeCallbacks(revealCloseButton)
        main.removeCallbacks(revealLoader)
    }

    private fun isSystemDark(): Boolean {
        val mask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun destroyLater(view: WebView) {
        main.post { if (orphans.remove(view)) destroyWebView(view) }
    }

    /** Detach before destroying: a WebView destroyed while attached can keep the Activity. */
    private fun destroyWebView(view: WebView) {
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.stopLoading()
        view.webChromeClient = null
        view.destroy()
    }

    override fun onDestroy() {
        // Any close still queued (host `close()` posted from another thread)
        // becomes a no-op instead of scheduling work on a destroyed Activity.
        done = true
        ActiveSession.unregister(closeRequester)
        // Drops the named timers and every anonymous post (legacy bridge, orphans).
        main.removeCallbacksAndMessages(null)
        webView?.let { destroyWebView(it) }
        webView = null
        orphans.forEach { destroyWebView(it) }
        orphans.clear()
        super.onDestroy()
    }

    internal companion object {
        const val EXTRA_CONFIG = "com.meshpay.link.CONFIG"
        const val EXTRA_RESULT = "com.meshpay.link.RESULT"

        /** After a Back that asks a loaded Link to close, a second Back within this window closes it at once. */
        private const val BACK_EXIT_WINDOW_MS: Long = 2_000L

        fun newIntent(
            context: Context,
            config: LinkConfiguration,
        ): Intent = Intent(context, LinkActivity::class.java).putExtra(EXTRA_CONFIG, config)

        fun resultFrom(intent: Intent?): LinkResult? = intent?.let { intentResult(it) }

        // IntentCompat picks the right framework call per API level, and skips the
        // typed overload on API 33, where it has a framework bug.
        private fun intentConfig(intent: Intent): LinkConfiguration? =
            IntentCompat.getParcelableExtra(intent, EXTRA_CONFIG, LinkConfiguration::class.java)

        private fun intentResult(intent: Intent): LinkResult? =
            IntentCompat.getParcelableExtra(intent, EXTRA_RESULT, LinkResult::class.java)
    }
}
