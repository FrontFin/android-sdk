package com.meshpay.link.internal

import com.meshpay.link.LinkEnvironment
import com.meshpay.link.LinkFlowConfig
import com.meshpay.link.LinkOptions
import com.meshpay.link.LinkTheme
import com.meshpay.link.LoginEntry
import java.net.URLEncoder
import java.util.Locale

/** How long to wait for Link to load before reporting `load-timeout`. */
internal const val LINK_LOAD_TIMEOUT_MS: Long = 15_000L

/** How long a host-initiated close waits for Link's own `close` before tearing down. */
internal const val CLOSE_GRACE_MS: Long = 1_000L

/** How long the native close button stays hidden after a session opens. */
internal const val CLOSE_BUTTON_DELAY_MS: Long = 3_000L

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
 * Build the Link UI URL exactly like `buildLinkUrl` in `@meshpayhq/link-web-sdk`:
 * parameters in the order `token`, `clientId`, `lng`, `th`, `flow`,
 * `integrationId`, `fiatCur`, each only when set, form-encoded.
 *
 * @param isSystemDark the device appearance, used to resolve [LinkTheme.SYSTEM].
 * @throws IllegalArgumentException when `token` or `clientId` is blank, so a
 *   misconfigured integration fails at call time instead of opening a broken Link.
 */
internal fun buildLinkUrl(
    token: String,
    clientId: String,
    options: LinkOptions,
    isSystemDark: Boolean,
): BuiltLinkUrl {
    val trimmedToken = token.trim()
    require(trimmedToken.isNotEmpty()) { "LinkConfiguration: token is required" }
    val trimmedClientId = clientId.trim()
    require(trimmedClientId.isNotEmpty()) { "LinkConfiguration: clientId is required" }

    // `linkUrl` overrides the environment → URL map, but ONLY for a LocalCan tunnel
    // (https://*.localcan.dev). Anything else is ignored in favour of the
    // environment default, so a stray or untrusted value can never redirect Link.
    val linkUrl = options.linkUrl
    val base = if (linkUrl != null && isLocalCanTunnel(linkUrl)) {
        linkUrl
    } else {
        LINK_URLS[options.environment] ?: LINK_URLS.getValue(LinkEnvironment.PROD)
    }

    val params = buildList {
        add("token" to trimmedToken)
        add("clientId" to trimmedClientId)
        options.language?.let { add("lng" to it) }
        options.theme?.let { add("th" to resolveTheme(it, isSystemDark)) }
        options.flow?.let { add("flow" to flowToJson(it)) }
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

/** True only for an `https://*.localcan.dev` tunnel URL (subdomains only). */
private fun isLocalCanTunnel(value: String): Boolean {
    val origin = getOrigin(value) ?: return false
    if (!origin.startsWith("https://")) return false
    val host = origin.removePrefix("https://").substringBefore(':')
    return host.endsWith(".localcan.dev")
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
 * application/x-www-form-urlencoded, byte-for-byte like the web SDK's `formEncode`
 * (which is `URLSearchParams`). `URLEncoder` leaves `A-Za-z0-9-_.*` unescaped,
 * encodes space as `+`, and emits upper-case `%XX`, which is exactly that set.
 */
private fun formEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

/**
 * Serialise a [LinkFlowConfig] to the same JSON the web SDK sends: the value of
 * `JSON.stringify(flow)`. Hand-built with an ordered writer so the output is
 * deterministic; Link parses it as JSON, so key order never matters to Link.
 */
internal fun flowToJson(flow: LinkFlowConfig): String = when (flow) {
    is LinkFlowConfig.Login -> jsonObject(
        "flow" to jsonString("login"),
        "entry" to loginEntryToJson(flow.entry),
    )

    is LinkFlowConfig.EnterAmount -> jsonObject(
        buildList {
            add("flow" to jsonString("enter-amount"))
            flow.depositHomeDrawer?.let { add("depositHomeDrawer" to it.toString()) }
            flow.showBuyOption?.let { add("showBuyOption" to it.toString()) }
        },
    )

    LinkFlowConfig.FiatOnRamp -> jsonObject("flow" to jsonString("fiatOnRamp"))
    LinkFlowConfig.FiatOffRamp -> jsonObject("flow" to jsonString("fiatOffRamp"))
}

private fun loginEntryToJson(entry: LoginEntry): String = when (entry) {
    LoginEntry.Catalog -> jsonObject("type" to jsonString("catalog"))
    is LoginEntry.DepositHome -> jsonObject(
        buildList {
            add("type" to jsonString("deposit-home"))
            entry.showBuyOption?.let { add("showBuyOption" to it.toString()) }
        },
    )

    is LoginEntry.DepositHomeIntegrations -> jsonObject(
        buildList {
            add("type" to jsonString("deposit-home-integrations"))
            entry.showBuyOption?.let { add("showBuyOption" to it.toString()) }
        },
    )
}

private fun jsonObject(vararg entries: Pair<String, String>): String = jsonObject(entries.toList())

private fun jsonObject(entries: List<Pair<String, String>>): String =
    entries.joinToString(prefix = "{", postfix = "}") { (key, encodedValue) ->
        "${jsonString(key)}:$encodedValue"
    }

/** JSON string literal with the escaping `JSON.stringify` applies. */
private fun jsonString(value: String): String {
    val sb = StringBuilder(value.length + 2)
    sb.append('"')
    for (ch in value) {
        when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            else -> if (ch < ' ') {
                sb.append("\\u").append(String.format(Locale.ROOT, "%04x", ch.code))
            } else {
                sb.append(ch)
            }
        }
    }
    sb.append('"')
    return sb.toString()
}
