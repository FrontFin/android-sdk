package com.meshpay.link

import com.meshpay.link.internal.flowToJson
import com.meshpay.link.internal.getOrigin
import com.meshpay.link.internal.getScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Covers the remaining [flowToJson] variants and the origin/scheme helpers. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FlowAndOriginTest {

    @Test
    fun `flow variants serialise like the web SDK`() {
        assertEquals("""{"flow":"fiatOnRamp"}""", flowToJson(LinkFlowConfig.FiatOnRamp))
        assertEquals("""{"flow":"fiatOffRamp"}""", flowToJson(LinkFlowConfig.FiatOffRamp))
        assertEquals(
            """{"flow":"login","entry":{"type":"catalog"}}""",
            flowToJson(LinkFlowConfig.Login(LoginEntry.Catalog)),
        )
        assertEquals(
            """{"flow":"login","entry":{"type":"deposit-home","showBuyOption":true}}""",
            flowToJson(LinkFlowConfig.Login(LoginEntry.DepositHome(showBuyOption = true))),
        )
        assertEquals(
            """{"flow":"login","entry":{"type":"deposit-home-integrations"}}""",
            flowToJson(LinkFlowConfig.Login(LoginEntry.DepositHomeIntegrations())),
        )
        assertEquals(
            """{"flow":"enter-amount","depositHomeDrawer":true,"showBuyOption":false}""",
            flowToJson(LinkFlowConfig.EnterAmount(depositHomeDrawer = true, showBuyOption = false)),
        )
        assertEquals("""{"flow":"enter-amount"}""", flowToJson(LinkFlowConfig.EnterAmount()))
    }

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
        assertEquals("https://abc.localcan.dev:8443", getOrigin("https://abc.localcan.dev:8443/x"))
        assertNull(getOrigin("https://user@link.meshpay.com"))
        assertNull(getOrigin("mailto:a@b.com"))
        assertNull(getOrigin("https://link meshpay.com"))
    }
}
