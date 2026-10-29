package com.meshpay.link

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Contract plumbing and the blank-argument guard (§2.2, §5.2). */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinkContractTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `createIntent targets the internal LinkActivity`() {
        val intent = LinkContract().createIntent(context, LinkConfiguration("tok", "cid"))
        assertEquals("com.meshpay.link.internal.LinkActivity", intent.component?.className)
    }

    @Test
    fun `no result extra is reported as a cancel, never an error`() {
        assertEquals(LinkResult.Exit(LinkExitStatus.CANCELED), LinkContract().parseResult(0, null))
    }

    @Test
    fun `blank token or clientId throws at construction`() {
        assertThrows(IllegalArgumentException::class.java) { LinkConfiguration("  ", "cid") }
        assertThrows(IllegalArgumentException::class.java) { LinkConfiguration("tok", "   ") }
    }
}
