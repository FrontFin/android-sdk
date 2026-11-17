package com.meshpay.link

import com.meshpay.link.internal.getOrigin
import com.meshpay.link.internal.getScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Covers the origin/scheme helpers used by the URL builder and navigation policy. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OriginTest {
    @Test
    fun `getScheme lower-cases and rejects whitespace`() {
        assertEquals("https", getScheme("HTTPS://link.meshpay.com"))
        assertEquals("metamask", getScheme("metamask://wc"))
        assertNull(getScheme("not a url"))
        assertNull(getScheme("/relative/path"))
    }

    @Test
    fun `getOrigin drops default ports and rejects credentials`() {
        assertEquals("https://link.meshpay.com", getOrigin("https://link.meshpay.com/path?x=1"))
        assertEquals("https://link.meshpay.com", getOrigin("https://LINK.meshpay.com:443/"))
        assertEquals("https://abc.example.com:8443", getOrigin("https://abc.example.com:8443/x"))
        assertNull(getOrigin("https://user@link.meshpay.com"))
        assertNull(getOrigin("mailto:a@b.com"))
        assertNull(getOrigin("https://link meshpay.com"))
    }

    @Test
    fun `getOrigin rejects a missing or empty authority`() {
        assertNull(getOrigin("https://"))
        assertNull(getOrigin("notaurl"))
        assertNull(getOrigin("https:///path"))
    }
}
