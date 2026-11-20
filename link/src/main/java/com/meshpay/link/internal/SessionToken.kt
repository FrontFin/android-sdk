package com.meshpay.link.internal

import com.meshpay.link.LinkTheme
import org.json.JSONException
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/]*={0,2}$")
private const val BITS_PER_CHAR = 6
private const val BITS_PER_BYTE = 8
private const val BYTE_MASK = 0xFF
private const val BLOCK = 4

/**
 * Reads the theme Link will use from the session token, if present. Returns
 * [LinkTheme.LIGHT] or [LinkTheme.DARK], or `null` for any token without one.
 * Never throws or logs.
 */
internal fun readTokenTheme(token: String): LinkTheme? {
    val envelope = parseEnvelope(token) ?: return null
    return when (envelope.opt("th")) {
        "LINK_THEME_LIGHT" -> LinkTheme.LIGHT
        "LINK_THEME_DARK" -> LinkTheme.DARK
        else -> null
    }
}

/**
 * Whether the SDK's own UI (system bars, background, loader, close button) is dark.
 * An explicit LIGHT/DARK wins; otherwise the session token's theme, then the device.
 */
internal fun isNativeDark(
    theme: LinkTheme?,
    token: String,
    isSystemDark: Boolean,
    tokenTheme: (String) -> LinkTheme? = ::readTokenTheme,
): Boolean =
    when (theme) {
        LinkTheme.LIGHT -> false
        LinkTheme.DARK -> true
        LinkTheme.SYSTEM, null ->
            when (tokenTheme(token.trim())) {
                LinkTheme.LIGHT -> false
                LinkTheme.DARK -> true
                else -> isSystemDark
            }
    }

/** The token's JSON envelope, or `null` when the token is a raw code or malformed. */
private fun parseEnvelope(token: String): JSONObject? {
    val json = decodeBase64Url(token)?.let(::decodeUtf8) ?: return null
    val envelope =
        try {
            JSONObject(json)
        } catch (_: JSONException) {
            null
        }
    val valid =
        envelope != null &&
            envelope.opt("v") is Number &&
            (envelope.opt("c") as? String)?.isNotEmpty() == true
    return if (valid) envelope else null
}

/**
 * Base64 or base64url (padding optional) to bytes, or `null` when the input is not
 * valid. Self-contained because `java.util.Base64` needs API 26.
 */
internal fun decodeBase64Url(input: String): ByteArray? {
    val base64 =
        buildString {
            append(input.replace('-', '+').replace('_', '/'))
            while (length % BLOCK != 0) append('=')
        }
    if (!BASE64_PATTERN.matches(base64)) return null
    // Padded to a multiple of 4 with at most two `=`, so no dangling 6-bit group is left.
    val data = base64.trimEnd('=')
    val out = ArrayList<Byte>(data.length * BITS_PER_CHAR / BITS_PER_BYTE)
    var buffer = 0
    var bits = 0
    for (char in data) {
        buffer = (buffer shl BITS_PER_CHAR) or BASE64_ALPHABET.indexOf(char)
        bits += BITS_PER_CHAR
        if (bits >= BITS_PER_BYTE) {
            bits -= BITS_PER_BYTE
            out.add(((buffer shr bits) and BYTE_MASK).toByte())
        }
        buffer = buffer and ((1 shl bits) - 1)
    }
    return out.toByteArray()
}

private fun decodeUtf8(bytes: ByteArray): String? =
    try {
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
