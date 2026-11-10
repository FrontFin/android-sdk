package com.meshpay.link

import com.meshpay.link.internal.NavigationDecision
import com.meshpay.link.internal.decideNavigation
import com.meshpay.link.internal.decideNewWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Branch coverage for the `intent://` parser and the `window.open` policy beyond
 * the shared N1–N8 vectors (§2.6).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NavigationPolicyEdgeTest {
    private val origin = "https://link.meshpay.com"

    @Test
    fun `intent without an Intent fragment is blocked`() {
        assertEquals(NavigationDecision.Block, decideNavigation("intent://wc", true, origin))
    }

    @Test
    fun `launchable intent without a fallback opens the deep link`() {
        val decision = decideNavigation("intent://wc?uri=abc#Intent;scheme=metamask;end", true, origin)
        assertEquals(NavigationDecision.OpenExternal("metamask://wc?uri=abc", null), decision)
    }

    @Test
    fun `non-launchable intent scheme falls back to its https fallback`() {
        // `javascript` is a blocked scheme, so the deep link is dropped and the
        // https browser_fallback_url is used instead.
        val url = "intent://x#Intent;scheme=javascript;S.browser_fallback_url=https%3A%2F%2Fa.example.com%2Fx;end"
        assertEquals(NavigationDecision.OpenExternal("https://a.example.com/x"), decideNavigation(url, true, origin))
    }

    @Test
    fun `non-launchable intent with no usable fallback is blocked`() {
        val url = "intent://x#Intent;scheme=about;end"
        assertEquals(NavigationDecision.Block, decideNavigation(url, true, origin))
    }

    @Test
    fun `intent whose fallback is not https drops the fallback`() {
        // metamask is launchable; an http (cleartext) fallback is ignored.
        val url = "intent://wc#Intent;scheme=metamask;S.browser_fallback_url=http%3A%2F%2Fa.example.com;end"
        assertEquals(NavigationDecision.OpenExternal("metamask://wc", null), decideNavigation(url, true, origin))
    }

    @Test
    fun `new window opens https and wallet schemes externally`() {
        assertEquals(
            NavigationDecision.OpenExternal("https://coinbase.com/x"),
            decideNewWindow("https://coinbase.com/x"),
        )
        assertEquals(NavigationDecision.OpenExternal("metamask://wc"), decideNewWindow("metamask://wc"))
    }

    @Test
    fun `new window blocks dangerous and about schemes`() {
        assertEquals(NavigationDecision.Block, decideNewWindow("javascript:alert(1)"))
        assertEquals(NavigationDecision.Block, decideNewWindow("about:blank"))
        assertEquals(NavigationDecision.Block, decideNewWindow("not a url"))
    }

    @Test
    fun `new window routes intent urls through the intent parser`() {
        val decision = decideNewWindow("intent://wc#Intent;scheme=metamask;end")
        assertEquals(NavigationDecision.OpenExternal("metamask://wc", null), decision)
    }
}
