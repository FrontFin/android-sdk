package com.meshpay.link

import com.meshpay.link.internal.LinkMessage
import com.meshpay.link.internal.buildCloseRequestedScript
import com.meshpay.link.internal.buildDocumentStartScript
import com.meshpay.link.internal.buildSdkSpecsScript
import com.meshpay.link.internal.parseLinkMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Message parsing and injected-script building (§2.5, §2.9). */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BridgeMessageTest {
    @Test
    fun `close with success status`() {
        val message = parseLinkMessage("""{"type":"close","payload":{"status":"success"}}""")
        assertEquals(LinkMessage.Close(LinkExitStatus.SUCCESS), message)
        assertEquals(LinkExitStatus.SUCCESS, (message as LinkMessage.Close).status)
    }

    @Test
    fun `close with canceled status`() {
        val message = parseLinkMessage("""{"type":"close","payload":{"status":"canceled"}}""")
        assertEquals(LinkMessage.Close(LinkExitStatus.CANCELED), message)
    }

    @Test
    fun `close without a payload is canceled`() {
        assertEquals(LinkMessage.Close(LinkExitStatus.CANCELED), parseLinkMessage("""{"type":"close"}"""))
    }

    @Test
    fun `close with a null payload is canceled`() {
        assertEquals(
            LinkMessage.Close(LinkExitStatus.CANCELED),
            parseLinkMessage("""{"type":"close","payload":null}"""),
        )
    }

    @Test
    fun `close with an unknown status is canceled`() {
        assertEquals(
            LinkMessage.Close(LinkExitStatus.CANCELED),
            parseLinkMessage("""{"type":"close","payload":{"status":"whatever"}}"""),
        )
    }

    @Test
    fun `loaded message`() {
        assertEquals(LinkMessage.Loaded, parseLinkMessage("""{"type":"loaded"}"""))
    }

    @Test
    fun `dropped and malformed messages yield null`() {
        assertNull(parseLinkMessage("""{"type":"transferFinished","payload":{"userId":"u"}}"""))
        assertNull(parseLinkMessage("""{"type":"brokerageAccountAccessToken"}"""))
        assertNull(parseLinkMessage("""{"type":"unknown"}"""))
        assertNull(parseLinkMessage("""{"no":"type"}"""))
        assertNull(parseLinkMessage("""not json"""))
        assertNull(parseLinkMessage("""[1,2,3]"""))
        assertNull(parseLinkMessage(null))
        assertNull(parseLinkMessage(""))
    }

    @Test
    fun `document start script sets the platform and version globals`() {
        val script = buildDocumentStartScript("1.2.3")
        assertTrue(script.contains("""window.meshSdkPlatform = "android";"""))
        assertTrue(script.contains("""window.meshSdkVersion = "1.2.3";"""))
    }

    @Test
    fun `sdk specs script posts the handshake with JSON-encoded values`() {
        val script = buildSdkSpecsScript("1.2.3")
        assertTrue(script.startsWith("window.postMessage("))
        assertTrue(script.contains("\"type\":\"meshSDKSpecs\""))
        assertTrue(script.contains("\"platform\":\"android\""))
        assertTrue(script.contains("\"version\":\"1.2.3\""))
        assertTrue(script.contains(", '*');"))
    }

    @Test
    fun `close requested script posts the closeRequested message`() {
        assertEquals("""window.postMessage({"type":"closeRequested"}, '*');true;""", buildCloseRequestedScript())
    }
}
