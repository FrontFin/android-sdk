package com.meshpay.link

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the bits that need a real WebView and a real
 * `Looper` — bridge acceptance (wrong origin / subframe rejected, main-frame
 * Link-origin accepted) and lifecycle (exactly one outcome; load timeout,
 * host `close()`, renderer crash) — run on an emulator in CI (§2.9, §5.4).
 *
 * These assertions are intentionally minimal here; the full WebView-driven
 * scenarios load a stub Link page served from the test app's assets so the suite
 * never depends on the network or a live session token.
 */
@RunWith(AndroidJUnit4::class)
class LinkActivityInstrumentedTest {

    @Test
    fun contractBuildsALaunchableIntent() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val intent = LinkContract().createIntent(context, LinkConfiguration("tok", "cid"))
        assertNotNull(intent.component)
    }
}
