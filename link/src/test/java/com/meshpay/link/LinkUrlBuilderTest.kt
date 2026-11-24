package com.meshpay.link

import android.net.Uri
import com.meshpay.link.internal.buildLinkUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs the shared URL vectors U1–U7 (`test-vectors/link-url.json`). Tests
 * compare decoded query values, so `+`/`%20` differences never fail a test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinkUrlBuilderTest {
    @Test
    fun `unset environment opens production`() {
        val unset = buildLinkUrl("t", "c", LinkOptions())
        val prod = buildLinkUrl("t", "c", LinkOptions { environment(LinkEnvironment.PROD) })
        assertEquals(prod, unset)
        assertEquals("https://link.meshpay.com", unset.origin)
    }

    @Test
    fun `origin is the scheme and host of the environment's Link URL`() {
        val prod = buildLinkUrl("t", "c", LinkOptions { environment(LinkEnvironment.PROD) })
        assertEquals("https://link.meshpay.com", prod.origin)
        val sbx = buildLinkUrl("t", "c", LinkOptions { environment(LinkEnvironment.SBX) })
        assertEquals("https://link.sbx.meshpay.com", sbx.origin)
    }

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

            if (vector.optBoolean("expectedError")) {
                try {
                    buildLinkUrl(token, clientId, options)
                    fail("$id: expected an error but none was thrown")
                } catch (_: IllegalArgumentException) {
                    // expected
                }
                continue
            }

            val actual = buildLinkUrl(token, clientId, options).href
            assertUrlEquals(id, vector.getString("expected"), actual)
        }
    }

    private fun assertUrlEquals(
        id: String,
        expected: String,
        actual: String,
    ) {
        val expectedUri = Uri.parse(expected)
        val actualUri = Uri.parse(actual)
        assertEquals("$id: base URL", base(expectedUri), base(actualUri))
        assertEquals("$id: param names", expectedUri.queryParameterNames, actualUri.queryParameterNames)
        for (name in expectedUri.queryParameterNames) {
            assertEquals("$id: $name", expectedUri.getQueryParameter(name), actualUri.getQueryParameter(name))
        }
    }

    private fun base(uri: Uri): String = "${uri.scheme}://${uri.authority}${uri.path}"

    private fun parseOptions(json: JSONObject?): LinkOptions {
        if (json == null) return LinkOptions()
        return LinkOptions {
            environment(
                when (json.optString("environment")) {
                    "sbx" -> LinkEnvironment.SBX
                    "dev" -> LinkEnvironment.DEV
                    "prod" -> LinkEnvironment.PROD
                    else -> null
                },
            )
            language(json.optString("language").ifEmpty { null })
            theme(
                when (json.optString("theme")) {
                    "light" -> LinkTheme.LIGHT
                    "dark" -> LinkTheme.DARK
                    "system" -> LinkTheme.SYSTEM
                    else -> null
                },
            )
            integrationId(json.optString("integrationId").ifEmpty { null })
            displayFiatCurrency(json.optString("displayFiatCurrency").ifEmpty { null })
        }
    }

    @Test
    fun `theme is sent unchanged, including system`() {
        assertEquals("system", th(LinkTheme.SYSTEM))
        assertEquals("light", th(LinkTheme.LIGHT))
        assertEquals("dark", th(LinkTheme.DARK))
        assertNull(th(null))
    }

    private fun th(theme: LinkTheme?): String? =
        Uri.parse(buildLinkUrl("t", "c", LinkOptions { theme(theme) }).href).getQueryParameter("th")

    @Test
    fun `blank token or clientId throws for the host to catch`() {
        try {
            buildLinkUrl("  ", "cid", LinkOptions())
            fail("expected an error for a blank token")
        } catch (e: IllegalArgumentException) {
            assertEquals("token is required.", e.message)
        }
        try {
            buildLinkUrl("tok", "   ", LinkOptions())
            fail("expected an error for a blank clientId")
        } catch (e: IllegalArgumentException) {
            assertEquals("clientId is required.", e.message)
        }
    }

    private fun loadVectors(path: String): JSONArray {
        val text = javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().readText()
        return JSONObject(text).getJSONArray("vectors")
    }
}
