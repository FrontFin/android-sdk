package com.meshpay.link.internal

// Small, strict URL helpers. `android.net.Uri` and `java.net.URI` disagree on
// scheme casing, default ports and credentials, so origin comparison — which the
// bridge security check depends on — is done here instead, matching the web SDK's
// `origin.ts`.

private val SCHEME = Regex("^([a-z][a-z0-9+.-]*):", RegexOption.IGNORE_CASE)
private val SCHEME_AND_AUTHORITY = Regex("^([a-z][a-z0-9+.-]*)://([^/?#]*)", RegexOption.IGNORE_CASE)
private val HOST_AND_PORT = Regex("^([a-z0-9.-]+)(?::(\\d{1,5}))?$", RegexOption.IGNORE_CASE)
private val DEFAULT_PORTS = mapOf("http" to "80", "https" to "443")
private val WHITESPACE = Regex("\\s")

/** Lower-case scheme of [url] (`"https"`, `"metamask"`, …), or `null`. */
internal fun getScheme(url: String): String? {
    if (WHITESPACE.containsMatchIn(url)) return null
    return SCHEME.find(url)?.groupValues?.get(1)?.lowercase()
}

/**
 * `scheme://host[:port]` of a hierarchical URL, lower-cased with the default port
 * dropped, or `null` when there is none. URLs with credentials (`https://a@b`) or
 * whitespace are rejected rather than normalised.
 */
internal fun getOrigin(url: String): String? {
    if (WHITESPACE.containsMatchIn(url)) return null
    val match = SCHEME_AND_AUTHORITY.find(url) ?: return null
    val scheme = match.groupValues[1].lowercase()
    val authority = match.groupValues[2]
    if (authority.isEmpty() || authority.contains('@')) return null
    val hostAndPort = HOST_AND_PORT.find(authority) ?: return null
    val host = hostAndPort.groupValues[1].lowercase()
    if (host.isEmpty()) return null
    val port = hostAndPort.groupValues[2]
    val keepPort = port.isNotEmpty() && port != DEFAULT_PORTS[scheme]
    return if (keepPort) "$scheme://$host:$port" else "$scheme://$host"
}
