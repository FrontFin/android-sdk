package com.meshpay.link

import com.meshpay.link.internal.decodeBase64Url
import com.meshpay.link.internal.isNativeDark
import com.meshpay.link.internal.readTokenTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/** Covers reading the theme from the session token and the native UI theme resolution. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SessionTokenTest {
    @Test
    fun `reads the theme from a base64url envelope with - and _ and no padding`() {
        assertTrue(DARK_TOKEN.contains('-') && DARK_TOKEN.contains('_'))
        assertFalse(DARK_TOKEN.endsWith("="))
        assertEquals(LinkTheme.DARK, readTokenTheme(DARK_TOKEN))
        assertEquals(LinkTheme.LIGHT, readTokenTheme(envelope("""{"v":1,"c":"abc123","th":"LINK_THEME_LIGHT"}""")))
    }

    @Test
    fun `reads the theme from padded standard base64`() {
        assertTrue(LIGHT_STANDARD_TOKEN.endsWith("="))
        assertEquals(LinkTheme.LIGHT, readTokenTheme(LIGHT_STANDARD_TOKEN))
    }

    @Test
    fun `a raw or malformed token has no theme`() {
        assertNull(readTokenTheme("tok_123"))
        assertNull(readTokenTheme("raw-session-code"))
        assertNull(readTokenTheme(""))
        assertNull(readTokenTheme("not base64!"))
        assertNull(readTokenTheme("ab=c"))
        assertNull(readTokenTheme("abcde"))
        assertNull(readTokenTheme(envelope("""{"v":1,"c":"abc""")))
        assertNull(readTokenTheme(encode(byteArrayOf(0xC3.toByte(), 0x28))))
    }

    @Test
    fun `non-object JSON has no theme`() {
        assertNull(readTokenTheme(envelope("""["LINK_THEME_DARK"]""")))
        assertNull(readTokenTheme(envelope(""""LINK_THEME_DARK"""")))
        assertNull(readTokenTheme(envelope("42")))
        assertNull(readTokenTheme(envelope("null")))
    }

    @Test
    fun `only LINK_THEME_LIGHT and LINK_THEME_DARK count`() {
        assertNull(readTokenTheme(envelope("""{"v":1,"c":"abc","th":"LINK_THEME_UNSPECIFIED"}""")))
        assertNull(readTokenTheme(envelope("""{"v":1,"c":"abc","th":"dark"}""")))
        assertNull(readTokenTheme(envelope("""{"v":1,"c":"abc","th":1}""")))
        assertNull(readTokenTheme(envelope("""{"v":1,"c":"abc"}""")))
    }

    @Test
    fun `v must be a number and c a non-empty string`() {
        assertNull(readTokenTheme(envelope("""{"v":"1","c":"abc","th":"LINK_THEME_DARK"}""")))
        assertNull(readTokenTheme(envelope("""{"v":true,"c":"abc","th":"LINK_THEME_DARK"}""")))
        assertNull(readTokenTheme(envelope("""{"v":null,"c":"abc","th":"LINK_THEME_DARK"}""")))
        assertNull(readTokenTheme(envelope("""{"c":"abc","th":"LINK_THEME_DARK"}""")))
        assertNull(readTokenTheme(envelope("""{"v":1,"c":"","th":"LINK_THEME_DARK"}""")))
        assertNull(readTokenTheme(envelope("""{"v":1,"c":7,"th":"LINK_THEME_DARK"}""")))
        assertNull(readTokenTheme(envelope("""{"v":1,"th":"LINK_THEME_DARK"}""")))
        assertEquals(LinkTheme.DARK, readTokenTheme(envelope("""{"v":2.5,"c":"abc","th":"LINK_THEME_DARK"}""")))
    }

    @Test
    fun `decodeBase64Url matches the JDK decoder`() {
        for (size in 0..8) {
            val bytes = ByteArray(size) { (it * 37 + 250).toByte() }
            assertArrayEquals(bytes, decodeBase64Url(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)))
            assertArrayEquals(bytes, decodeBase64Url(Base64.getEncoder().encodeToString(bytes)))
        }
    }

    @Test
    fun `native UI theme resolution`() {
        val unspecified = envelope("""{"v":1,"c":"abc","th":"LINK_THEME_UNSPECIFIED"}""")

        data class Case(val theme: LinkTheme?, val token: String, val systemDark: Boolean, val dark: Boolean)
        val cases =
            listOf(
                Case(LinkTheme.LIGHT, DARK_TOKEN, systemDark = true, dark = false),
                Case(LinkTheme.DARK, LIGHT_STANDARD_TOKEN, systemDark = false, dark = true),
                Case(LinkTheme.SYSTEM, DARK_TOKEN, systemDark = false, dark = true),
                Case(LinkTheme.SYSTEM, LIGHT_STANDARD_TOKEN, systemDark = true, dark = false),
                Case(null, DARK_TOKEN, systemDark = false, dark = true),
                Case(null, LIGHT_STANDARD_TOKEN, systemDark = true, dark = false),
                Case(LinkTheme.SYSTEM, "tok_123", systemDark = true, dark = true),
                Case(LinkTheme.SYSTEM, "tok_123", systemDark = false, dark = false),
                Case(null, unspecified, systemDark = true, dark = true),
                Case(null, unspecified, systemDark = false, dark = false),
                Case(null, "  $DARK_TOKEN  ", systemDark = false, dark = true),
            )
        for (case in cases) {
            assertEquals(case.toString(), case.dark, isNativeDark(case.theme, case.token, case.systemDark))
        }
    }

    @Test
    fun `explicit LIGHT or DARK never reads the token`() {
        val noRead: (String) -> LinkTheme? = {
            fail("token read for an explicit theme")
            null
        }
        assertFalse(isNativeDark(LinkTheme.LIGHT, DARK_TOKEN, isSystemDark = true, tokenTheme = noRead))
        assertTrue(isNativeDark(LinkTheme.DARK, LIGHT_STANDARD_TOKEN, isSystemDark = false, tokenTheme = noRead))
    }

    private fun envelope(json: String): String = encode(json.toByteArray())

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        // Built at test time: base64url, no padding. This payload encodes to both `-` and `_`.
        val DARK_TOKEN: String =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString("""{"v":1,"c":"code_~~??>>","th":"LINK_THEME_DARK","exp":1795000000}""".toByteArray())

        // Standard base64 with `=` padding.
        val LIGHT_STANDARD_TOKEN: String =
            Base64.getEncoder().encodeToString("""{"v":1,"c":"abc123","th":"LINK_THEME_LIGHT"}""".toByteArray())
    }
}
