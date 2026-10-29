package com.meshpay.link.internal

import com.meshpay.link.LinkExitStatus
import org.json.JSONException
import org.json.JSONObject

/**
 * Messages from Link that the SDK acts on. Link also posts `transferFinished`,
 * `withdrawalRequested` and `brokerageAccountAccessToken`; they are dropped,
 * because their payloads carry user data and broker credentials (§2.5).
 */
internal sealed interface LinkMessage {
    data class Close(val status: LinkExitStatus) : LinkMessage
    data object Loaded : LinkMessage
}

/**
 * Parse a bridge payload from Link. Anything that is not a known message —
 * invalid JSON, a non-object, a missing `type` — yields `null` and never throws.
 */
internal fun parseLinkMessage(data: String?): LinkMessage? {
    if (data.isNullOrBlank()) return null
    val message = try {
        JSONObject(data)
    } catch (_: JSONException) {
        return null
    }
    return when (message.optString("type")) {
        "close" -> {
            // Older Link builds post `close` with no payload. Only an explicit
            // `success` counts, so `success` is never reported unless Link says so.
            val status = message.optJSONObject("payload")?.optString("status")
            val exitStatus = if (status == "success") LinkExitStatus.SUCCESS else LinkExitStatus.CANCELED
            LinkMessage.Close(exitStatus)
        }

        "loaded" -> LinkMessage.Loaded
        else -> null
    }
}

/**
 * Run at document start in the main frame: announces the host to Link before any
 * of its code runs. `isLegacySdk()` and Link's analytics read these globals.
 */
internal fun buildDocumentStartScript(version: String): String =
    "window.meshSdkPlatform = ${JSONObject.quote(SDK_PLATFORM)};" +
        "window.meshSdkVersion = ${JSONObject.quote(version)};" +
        "true;"

/**
 * The `meshSDKSpecs` handshake, sent after Link reports `loaded`. No `origin`:
 * the native transport does not need one.
 */
internal fun buildSdkSpecsScript(version: String): String = buildPostMessageScript(
    JSONObject()
        .put("type", "meshSDKSpecs")
        .put("payload", JSONObject().put("platform", SDK_PLATFORM).put("version", version)),
)

/** Asks Link to close gracefully, so it can report the session status. */
internal fun buildCloseRequestedScript(): String =
    buildPostMessageScript(JSONObject().put("type", "closeRequested"))

/**
 * In a top-level WebView `window.parent === window`, so a message posted to the
 * page itself passes Link's "came from the host" check. Values are always
 * JSON-encoded by [JSONObject], never concatenated into the script.
 */
private fun buildPostMessageScript(message: JSONObject): String =
    "window.postMessage($message, '*');true;"
