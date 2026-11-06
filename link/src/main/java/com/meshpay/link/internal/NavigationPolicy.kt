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
private val BLOCKED_SCHEMES = setOf(
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

private val PLAIN_SCHEME = Regex("^[a-z][a-z0-9+.-]*$", RegexOption.IGNORE_CASE)

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
        return if (url.lowercase() in ALLOWED_ABOUT_PAGES) {
            NavigationDecision.Load
        } else {
            NavigationDecision.Block
        }
    }
    if (scheme == "https") {
        return if (!isMainFrame || getOrigin(url) == linkOrigin) {
            NavigationDecision.Load
        } else {
            NavigationDecision.OpenExternal(url)
        }
    }
    return externalAppDecision(url, scheme)
}

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
    if (scheme == "https") return NavigationDecision.OpenExternal(url)
    return externalAppDecision(url, scheme)
}

private fun externalAppDecision(url: String, scheme: String): NavigationDecision {
    if (scheme == "intent") return intentDecision(url)
    // Deliberately not gated on `resolveActivity` / package visibility: on
    // Android 11+ it answers false for wallets the host manifest does not list
    // (legacy bug ONC-447).
    return NavigationDecision.OpenExternal(url)
}

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
    val marker = "#Intent;"
    val fragmentIndex = url.indexOf(marker)
    if (fragmentIndex == -1) return NavigationDecision.Block
    val target = url.substring("intent://".length, fragmentIndex)
    val fields = HashMap<String, String>()
    for (part in url.substring(fragmentIndex + marker.length).split(';')) {
        val separator = part.indexOf('=')
        if (separator > 0) {
            fields[part.substring(0, separator)] = part.substring(separator + 1)
        }
    }

    val fallbackUrl = decodeFallback(fields["S.browser_fallback_url"])
    val scheme = fields["scheme"]?.lowercase()
    val launchable = scheme != null &&
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
