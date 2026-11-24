package com.meshpay.link

import com.meshpay.link.internal.isForeignTopFrameDocument
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The second origin gate for main-frame documents. */
class ForeignTopFrameDocumentTest {
    private val origin = "https://link.meshpay.com"

    @Test
    fun `link origin pages are not foreign`() {
        assertFalse(isForeignTopFrameDocument("https://link.meshpay.com/", origin))
        assertFalse(isForeignTopFrameDocument("https://link.meshpay.com/deposit?x=1#y", origin))
        assertFalse(isForeignTopFrameDocument("HTTPS://LINK.MESHPAY.COM:443/", origin))
    }

    @Test
    fun `allowed about pages are not foreign`() {
        assertFalse(isForeignTopFrameDocument("about:blank", origin))
        assertFalse(isForeignTopFrameDocument("ABOUT:SRCDOC", origin))
    }

    @Test
    fun `other https origins are foreign`() {
        assertTrue(isForeignTopFrameDocument("https://evil.example/login", origin))
        assertTrue(isForeignTopFrameDocument("https://link.meshpay.com.evil.example/", origin))
        assertTrue(isForeignTopFrameDocument("https://sub.link.meshpay.com/", origin))
        assertTrue(isForeignTopFrameDocument("https://link.meshpay.com:8443/", origin))
    }

    @Test
    fun `cleartext link host is foreign`() {
        assertTrue(isForeignTopFrameDocument("http://link.meshpay.com/", origin))
    }

    @Test
    fun `other about pages and schemes are foreign`() {
        assertTrue(isForeignTopFrameDocument("about:config", origin))
        assertTrue(isForeignTopFrameDocument("data:text/html,<p>x</p>", origin))
        assertTrue(isForeignTopFrameDocument("chrome-error://chromewebdata/", origin))
    }

    @Test
    fun `urls without a comparable origin are foreign`() {
        assertTrue(isForeignTopFrameDocument("", origin))
        assertTrue(isForeignTopFrameDocument("https://user@link.meshpay.com/", origin))
        assertTrue(isForeignTopFrameDocument("https://link.meshpay.com /", origin))
    }
}
