package com.meshpay.link.internal

/** Where a WebView navigation request goes. */
internal sealed interface NavigationDecision {
    /** Load in the WebView. */
    data object Load : NavigationDecision

    /** Refuse: never loaded and never handed to the OS. */
    data object Block : NavigationDecision

    /**
     * Hand [url] to the OS (wallet app, exchange app or browser). When opening it
     * fails and [fallbackUrl] is set, open that instead.
     */
    data class OpenExternal(val url: String, val fallbackUrl: String? = null) : NavigationDecision
}

/**
 * Schemes never loaded or handed to the OS from web content. `javascript:` and
 * `data:` execute in whatever context receives them; `file:` and `content:`
 * reach local storage; `android-app:` addresses an arbitrary component; `blob:`
 * is meaningless outside the page; `http:` is cleartext.
 */
private val BLOCKED_SCHEMES =
    setOf(
        "javascript",
        "data",
        "file",
        "content",
        "android-app",
        "blob",
        "http",
    )

/** `about:` pages a frame may legitimately load (e.g. captcha iframes). */
private val ALLOWED_ABOUT_PAGES = setOf("about:blank", "about:srcdoc")

private fun isAllowedAboutPage(url: String): Boolean = url.lowercase() in ALLOWED_ABOUT_PAGES

private val PLAIN_SCHEME = Regex("^[a-z][a-z0-9+.-]*$", RegexOption.IGNORE_CASE)

/**
 * Longest URL handed to the OS (SR-6, PRG-2290). The URL travels in an Intent
 * over Binder, so an unbounded one can fail with TransactionTooLargeException.
 * 32 KiB is far above real wallet deep links (WalletConnect URIs and signed
 * payloads stay in the low kilobytes) and far below the Binder buffer.
 */
internal const val MAX_EXTERNAL_URL_LENGTH: Int = 32 * 1024

/**
 * Where a WebView navigation request goes (solution design §2.6, vectors N1–N8).
 *
 * - The session's own Link origin loads in the WebView; any other `https` page in
 *   the main frame (OAuth, on-ramp, exchange handoff) opens externally, so
 *   universal links and App Links can reach the wallet or exchange app.
 * - Subframes may load any `https` page (captcha, payment iframes).
 * - Custom schemes (wallet deep links) and `intent://` go to the OS.
 */
internal fun decideNavigation(
    url: String,
    isMainFrame: Boolean,
    linkOrigin: String,
): NavigationDecision {
    val scheme = getScheme(url)
    if (scheme == null || scheme in BLOCKED_SCHEMES) return NavigationDecision.Block
    if (scheme == "about") {
        return if (isAllowedAboutPage(url)) {
            NavigationDecision.Load
        } else {
            NavigationDecision.Block
        }
    }
    if (scheme == "https") {
        return if (!isMainFrame || getOrigin(url) == linkOrigin) {
            NavigationDecision.Load
        } else {
            openExternal(url)
        }
    }
    return externalAppDecision(url, scheme)
}

/**
 * Whether a main-frame document that has already started loading is outside the
 * session's Link origin, so it must not stay in the WebView (PRG-2294).
 *
 * Second gate behind [decideNavigation]: Android never calls
 * `shouldOverrideUrlLoading` for POST navigations, so a `target=_top` form (from
 * Link or one of its iframes) can commit any `https` or `http` page in the main
 * frame. Every other foreign main-frame page is opened externally, so one seen
 * here got past the first gate.
 */
internal fun isForeignTopFrameDocument(
    url: String,
    linkOrigin: String,
): Boolean = !isAllowedAboutPage(url) && getOrigin(url) != linkOrigin

/**
 * Where a `window.open()` / `target="_blank"` request goes. A new window never
 * opens inside the SDK: `https` and wallet schemes leave the app, anything else
 * is dropped.
 */
internal fun decideNewWindow(url: String): NavigationDecision {
    val scheme = getScheme(url)
    if (scheme == null || scheme in BLOCKED_SCHEMES || scheme == "about") {
        return NavigationDecision.Block
    }
    if (scheme == "https") return openExternal(url)
    return externalAppDecision(url, scheme)
}

private fun externalAppDecision(
    url: String,
    scheme: String,
): NavigationDecision {
    // Checked on the raw URL, so an over-long `intent://` is blocked before it is
    // parsed, whatever deep link or fallback it would yield.
    if (url.length > MAX_EXTERNAL_URL_LENGTH) return NavigationDecision.Block
    if (scheme == "intent") return intentDecision(url)
    // Deliberately not gated on `resolveActivity` / package visibility: on
    // Android 11+ it answers false for wallets the host manifest does not list
    // (legacy bug ONC-447).
    return NavigationDecision.OpenExternal(url)
}

/** An external `https` URL, or [NavigationDecision.Block] when it is too long. */
private fun openExternal(url: String): NavigationDecision =
    if (url.length > MAX_EXTERNAL_URL_LENGTH) NavigationDecision.Block else NavigationDecision.OpenExternal(url)

/**
 * Android `intent://` URL → the plain deep link it describes, plus its
 * `browser_fallback_url`. Only the scheme, path and an `https` fallback are used;
 * `package`, `component`, `selector` and extras are ignored, so a crafted URL
 * cannot address an arbitrary app component (N6).
 *
 * `intent://wc?uri=x#Intent;scheme=metamask;package=io.metamask;end`
 *   → `metamask://wc?uri=x`
 */
private fun intentDecision(url: String): NavigationDecision {
    val prefix = "intent://"
    val marker = "#Intent;"
    // Malformed forms such as `intent:#Intent;…` or `intent:/#Intent;…` have no
    // `//` authority part; block them rather than slice out of range.
    if (!url.startsWith(prefix, ignoreCase = true)) return NavigationDecision.Block
    val fragmentIndex = url.indexOf(marker, startIndex = prefix.length)
    if (fragmentIndex == -1) return NavigationDecision.Block
    val target = url.substring(prefix.length, fragmentIndex)
    val fields = HashMap<String, String>()
    for (part in url.substring(fragmentIndex + marker.length).split(';')) {
        val separator = part.indexOf('=')
        if (separator > 0) {
            fields[part.substring(0, separator)] = part.substring(separator + 1)
        }
    }

    val fallbackUrl = decodeFallback(fields["S.browser_fallback_url"])
    val scheme = fields["scheme"]?.lowercase()
    val launchable =
        scheme != null &&
            PLAIN_SCHEME.matches(scheme) &&
            scheme !in BLOCKED_SCHEMES &&
            scheme != "intent" &&
            scheme != "about"

    if (launchable) {
        return NavigationDecision.OpenExternal("$scheme://$target", fallbackUrl)
    }
    return if (fallbackUrl != null) {
        NavigationDecision.OpenExternal(fallbackUrl)
    } else {
        NavigationDecision.Block
    }
}

private fun decodeFallback(value: String?): String? {
    if (value.isNullOrEmpty()) return null
    return try {
        val decoded = java.net.URLDecoder.decode(value, "UTF-8")
        if (getScheme(decoded) == "https" && getOrigin(decoded) != null) decoded else null
    } catch (_: IllegalArgumentException) {
        null
    }
}
