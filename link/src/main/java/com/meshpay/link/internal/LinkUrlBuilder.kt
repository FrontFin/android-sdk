package com.meshpay.link.internal

import com.meshpay.link.LinkEnvironment
import com.meshpay.link.LinkOptions
import com.meshpay.link.LinkTheme
import java.net.URLEncoder

/** How long to wait for Link to load before reporting `load-timeout`. */
internal const val LINK_LOAD_TIMEOUT_MS: Long = 15_000L

/** How long a host-initiated close waits for Link's own `close` before tearing down. */
internal const val CLOSE_GRACE_MS: Long = 1_000L

/** How long the native close button stays hidden after a session opens. */
internal const val CLOSE_BUTTON_DELAY_MS: Long = 2_000L

/** How long the native loading spinner stays hidden after a session opens. */
internal const val LOADER_DELAY_MS: Long = 1_000L

/** Platform identifier announced to Link. Link only recognises this exact string. */
internal const val SDK_PLATFORM: String = "android"

/** Background shown behind the WebView while Link loads, per resolved theme. */
internal const val BACKGROUND_DARK: Int = 0xFF0E0D0D.toInt()
internal const val BACKGROUND_LIGHT: Int = 0xFFFBFBFB.toInt()

/** Hosted Link deployment base URLs. */
internal val LINK_URLS: Map<LinkEnvironment, String> = mapOf(
    LinkEnvironment.DEV to "https://link.dev.meshpay.com",
    LinkEnvironment.PROD to "https://link.meshpay.com",
    LinkEnvironment.SBX to "https://link.sbx.meshpay.com",
)

/** The Link UI URL plus the origin bridge messages must come from. */
internal data class BuiltLinkUrl(val href: String, val origin: String)

/**
 * Build the Link UI URL exactly like `buildLinkUrl` in the web SDK: parameters in
 * the order `token`, `clientId`, `lng`, `th`, `integrationId`, `fiatCur`, each
 * only when set, form-encoded.
 *
 * @param isSystemDark the device appearance, used to resolve [LinkTheme.SYSTEM].
 * @throws IllegalArgumentException when `token` or `clientId` is blank, with a
 *   message naming the field. The public API never surfaces this throw:
 *   [LinkActivity] catches it and reports `load-error` instead (§2.3).
 */
internal fun buildLinkUrl(
    token: String,
    clientId: String,
    options: LinkOptions,
    isSystemDark: Boolean,
): BuiltLinkUrl {
    val trimmedToken = token.trim()
    require(trimmedToken.isNotEmpty()) { "token is required." }
    val trimmedClientId = clientId.trim()
    require(trimmedClientId.isNotEmpty()) { "clientId is required." }

    val base = LINK_URLS[options.environment] ?: LINK_URLS.getValue(LinkEnvironment.PROD)

    val params = buildList {
        add("token" to trimmedToken)
        add("clientId" to trimmedClientId)
        options.language?.let { add("lng" to it) }
        options.theme?.let { add("th" to resolveTheme(it, isSystemDark)) }
        options.integrationId?.let { add("integrationId" to it) }
        options.displayFiatCurrency?.let { add("fiatCur" to it) }
    }

    val href = appendQuery(base, params)
    // `base` is a constant or an origin-checked tunnel URL, so it always has one.
    val origin = getOrigin(href)!!
    return BuiltLinkUrl(href, origin)
}

/** `light` or `dark`; `system` follows the device appearance. */
internal fun resolveTheme(theme: LinkTheme, isSystemDark: Boolean): String = when (theme) {
    LinkTheme.LIGHT -> "light"
    LinkTheme.DARK -> "dark"
    LinkTheme.SYSTEM -> if (isSystemDark) "dark" else "light"
}

private fun appendQuery(base: String, params: List<Pair<String, String>>): String {
    val hashIndex = base.indexOf('#')
    val withoutHash = if (hashIndex == -1) base else base.substring(0, hashIndex)
    val hash = if (hashIndex == -1) "" else base.substring(hashIndex)
    // `new URL('https://host').toString()` is `https://host/`; match it.
    val withPath = if (SCHEME_AUTHORITY_ONLY.matches(withoutHash)) "$withoutHash/" else withoutHash
    val query = params.joinToString("&") { (key, value) -> "${formEncode(key)}=${formEncode(value)}" }
    val separator = if (withPath.contains('?')) '&' else '?'
    return "$withPath$separator$query$hash"
}

private val SCHEME_AUTHORITY_ONLY = Regex("^[a-z][a-z0-9+.-]*://[^/?#]*$", RegexOption.IGNORE_CASE)

/**
 * application/x-www-form-urlencoded, byte-for-byte like the web SDK's `formEncode`.
 * `URLEncoder` leaves `A-Za-z0-9-_.*` unescaped, encodes space as `+`, and emits
 * upper-case `%XX`, which is exactly that set.
 */
private fun formEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
