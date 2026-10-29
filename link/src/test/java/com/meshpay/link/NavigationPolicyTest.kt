package com.meshpay.link

import com.meshpay.link.internal.NavigationDecision
import com.meshpay.link.internal.decideNavigation
import com.meshpay.link.internal.decideNewWindow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the shared navigation vectors N1–N9 (`test-vectors/navigation.json`, §2.6). */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NavigationPolicyTest {

    @Test
    fun `matches the shared navigation vectors`() {
        val file = loadFile("test-vectors/navigation.json")
        val defaultOrigin = file.getString("linkOrigin")
        val vectors = file.getJSONArray("vectors")
        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val id = vector.getString("id")
            val url = vector.getString("url")
            val linkOrigin = vector.optString("linkOrigin").ifEmpty { defaultOrigin }
            val expected = vector.getJSONObject("expected")

            val decision = if (vector.optBoolean("newWindow")) {
                decideNewWindow(url)
            } else {
                decideNavigation(url, vector.optBoolean("isTopFrame", true), linkOrigin)
            }

            assertDecision(id, expected, decision)
        }
    }

    private fun assertDecision(id: String, expected: JSONObject, actual: NavigationDecision) {
        when (expected.getString("action")) {
            "load" -> assertEquals("$id", NavigationDecision.Load, actual)
            "block" -> assertEquals("$id", NavigationDecision.Block, actual)
            "open-external" -> {
                val external = actual as? NavigationDecision.OpenExternal
                    ?: throw AssertionError("$id: expected open-external, got $actual")
                assertEquals("$id: url", expected.getString("url"), external.url)
                val expectedFallback = expected.optString("fallbackUrl").ifEmpty { null }
                assertEquals("$id: fallbackUrl", expectedFallback, external.fallbackUrl)
            }

            else -> throw AssertionError("$id: unknown action ${expected.getString("action")}")
        }
    }

    private fun loadFile(path: String): JSONObject {
        val text = javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().readText()
        return JSONObject(text)
    }
}
