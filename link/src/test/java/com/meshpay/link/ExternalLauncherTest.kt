package com.meshpay.link

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.meshpay.link.internal.ExternalLauncher
import com.meshpay.link.internal.NavigationDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A launch failure of any kind is reported as "not opened" and tries the
 * fallback; it never escapes into the WebView callback (SR-6, PRG-2290).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ExternalLauncherTest {
    private val primary = "metamask://wc?uri=abc"
    private val fallback = "https://metamask.app.link/wc?uri=abc"

    /** Records every launch attempt and throws whatever [failures] maps the URL to. */
    private class FakeContext(
        private val failures: Map<String, RuntimeException>,
    ) : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val attempts = mutableListOf<String>()

        override fun startActivity(intent: Intent) {
            val url = intent.dataString.orEmpty()
            attempts += url
            failures[url]?.let { throw it }
        }
    }

    private fun open(
        failures: Map<String, RuntimeException>,
        fallbackUrl: String? = fallback,
    ): Pair<Boolean, List<String>> {
        val context = FakeContext(failures)
        val opened = ExternalLauncher(context).open(NavigationDecision.OpenExternal(primary, fallbackUrl))
        return opened to context.attempts
    }

    @Test
    fun `a successful launch does not try the fallback`() {
        val (opened, attempts) = open(emptyMap())
        assertTrue(opened)
        assertEquals(listOf(primary), attempts)
    }

    @Test
    fun `primary throwing ActivityNotFoundException uses the fallback`() {
        val (opened, attempts) = open(mapOf(primary to ActivityNotFoundException()))
        assertTrue(opened)
        assertEquals(listOf(primary, fallback), attempts)
    }

    @Test
    fun `primary throwing SecurityException uses the fallback`() {
        val (opened, attempts) = open(mapOf(primary to SecurityException("Permission Denial")))
        assertTrue(opened)
        assertEquals(listOf(primary, fallback), attempts)
    }

    @Test
    fun `primary throwing a generic RuntimeException uses the fallback`() {
        val (opened, attempts) = open(mapOf(primary to RuntimeException("TransactionTooLargeException")))
        assertTrue(opened)
        assertEquals(listOf(primary, fallback), attempts)
    }

    @Test
    fun `primary failing with no fallback returns false`() {
        val (opened, attempts) = open(mapOf(primary to SecurityException()), fallbackUrl = null)
        assertFalse(opened)
        assertEquals(listOf(primary), attempts)
    }

    @Test
    fun `fallback throwing SecurityException returns false`() {
        val (opened, attempts) =
            open(mapOf(primary to SecurityException(), fallback to SecurityException()))
        assertFalse(opened)
        assertEquals(listOf(primary, fallback), attempts)
    }

    @Test
    fun `fallback throwing a generic RuntimeException returns false`() {
        val (opened, attempts) =
            open(mapOf(primary to ActivityNotFoundException(), fallback to RuntimeException()))
        assertFalse(opened)
        assertEquals(listOf(primary, fallback), attempts)
    }
}
