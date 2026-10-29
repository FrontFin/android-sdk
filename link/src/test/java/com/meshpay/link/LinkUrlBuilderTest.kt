package com.meshpay.link

import android.net.Uri
import com.meshpay.link.internal.buildLinkUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the shared URL vectors U1–U13 (`test-vectors/link-url.json`, §2.4). Tests
 * compare decoded query values; `flow` is compared as parsed JSON, so key order
 * and `+`/`%20` differences never fail a test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinkUrlBuilderTest {

    @Test
    fun `matches the shared link-url vectors`() {
        val vectors = loadVectors("test-vectors/link-url.json")
        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val id = vector.getString("id")
            val input = vector.getJSONObject("input")
            val token = input.getString("token")
            val clientId = input.getString("clientId")
            val options = parseOptions(input.optJSONObject("options"))
            val isSystemDark = input.optString("colorScheme") == "dark"

            if (vector.optBoolean("expectedError")) {
                try {
                    buildLinkUrl(token, clientId, options, isSystemDark)
                    fail("$id: expected an error but none was thrown")
                } catch (_: IllegalArgumentException) {
                    // expected
                }
                continue
            }

            val actual = buildLinkUrl(token, clientId, options, isSystemDark).href
            assertUrlEquals(id, vector.getString("expected"), actual)
        }
    }

    private fun assertUrlEquals(id: String, expected: String, actual: String) {
        val expectedUri = Uri.parse(expected)
        val actualUri = Uri.parse(actual)
        assertEquals("$id: base URL", base(expectedUri), base(actualUri))
        assertEquals("$id: param names", paramNames(expectedUri), paramNames(actualUri))
        for (name in paramNames(expectedUri)) {
            val e = expectedUri.getQueryParameter(name)!!
            val a = actualUri.getQueryParameter(name)!!
            if (name == "flow") {
                assertEquals("$id: flow JSON", canonical(e), canonical(a))
            } else {
                assertEquals("$id: $name", e, a)
            }
        }
    }

    private fun base(uri: Uri): String = "${uri.scheme}://${uri.authority}${uri.path}"

    private fun paramNames(uri: Uri): Set<String> = uri.queryParameterNames

    private fun parseOptions(json: JSONObject?): LinkOptions {
        if (json == null) return LinkOptions()
        return LinkOptions(
            environment = when (json.optString("environment")) {
                "sbx" -> LinkEnvironment.SBX
                "dev" -> LinkEnvironment.DEV
                else -> LinkEnvironment.PROD
            },
            language = json.optString("language").ifEmpty { null },
            theme = when (json.optString("theme")) {
                "light" -> LinkTheme.LIGHT
                "dark" -> LinkTheme.DARK
                "system" -> LinkTheme.SYSTEM
                else -> null
            },
            flow = json.optJSONObject("flow")?.let(::parseFlow),
            integrationId = json.optString("integrationId").ifEmpty { null },
            displayFiatCurrency = json.optString("displayFiatCurrency").ifEmpty { null },
            linkUrl = json.optString("linkUrl").ifEmpty { null },
        )
    }

    private fun parseFlow(json: JSONObject): LinkFlowConfig = when (json.getString("flow")) {
        "fiatOnRamp" -> LinkFlowConfig.FiatOnRamp
        "fiatOffRamp" -> LinkFlowConfig.FiatOffRamp
        "enter-amount" -> LinkFlowConfig.EnterAmount(
            depositHomeDrawer = if (json.has("depositHomeDrawer")) json.getBoolean("depositHomeDrawer") else null,
            showBuyOption = if (json.has("showBuyOption")) json.getBoolean("showBuyOption") else null,
        )

        "login" -> LinkFlowConfig.Login(parseEntry(json.getJSONObject("entry")))
        else -> error("unknown flow ${json.getString("flow")}")
    }

    private fun parseEntry(json: JSONObject): LoginEntry = when (json.getString("type")) {
        "catalog" -> LoginEntry.Catalog
        "deposit-home" -> LoginEntry.DepositHome(
            if (json.has("showBuyOption")) json.getBoolean("showBuyOption") else null,
        )

        "deposit-home-integrations" -> LoginEntry.DepositHomeIntegrations(
            if (json.has("showBuyOption")) json.getBoolean("showBuyOption") else null,
        )

        else -> error("unknown entry ${json.getString("type")}")
    }

    @Test
    fun `system theme follows the device appearance`() {
        val light = buildLinkUrl("t", "c", LinkOptions(theme = LinkTheme.SYSTEM), isSystemDark = false)
        val dark = buildLinkUrl("t", "c", LinkOptions(theme = LinkTheme.SYSTEM), isSystemDark = true)
        assertTrue(light.href.contains("th=light"))
        assertTrue(dark.href.contains("th=dark"))
    }

    private fun loadVectors(path: String): JSONArray {
        val text = javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().readText()
        return JSONObject(text).getJSONArray("vectors")
    }

    /** Recursively sorts object keys so two JSON strings compare regardless of order. */
    private fun canonical(json: String): String = canonicalValue(JSONObject(json))

    private fun canonicalValue(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted()
            .joinToString(",", "{", "}") { "$it:${canonicalValue(value.get(it))}" }

        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalValue(value.get(it)) }
        is String -> "\"$value\""
        else -> value.toString()
    }
}
