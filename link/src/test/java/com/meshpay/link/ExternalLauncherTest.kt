package com.meshpay.link

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
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
import org.robolectric.shadows.ShadowLog

/**
 * A launch failure of any kind is reported as "not opened" and tries the
 * fallback; it never escapes into the WebView callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ExternalLauncherTest {
    private val primary = "metamask://wc?uri=abc"
    private val fallback = "https://metamask.app.link/wc?uri=abc"

    /** Records every launch attempt and throws whatever [failures] maps the URL to. */
    private class FakeContext(
        private val failures: Map<String, RuntimeException>,
        private val debuggable: Boolean = false,
    ) : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val attempts = mutableListOf<String>()

        override fun getApplicationInfo(): ApplicationInfo =
            ApplicationInfo(super.getApplicationInfo()).apply {
                flags =
                    if (debuggable) {
                        flags or ApplicationInfo.FLAG_DEBUGGABLE
                    } else {
                        flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
                    }
            }

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

    // --- Diagnostics: debuggable hosts only, never the URL ------------------

    private fun launcherLogs() = ShadowLog.getLogsForTag(ExternalLauncher.LOG_TAG)

    @Test
    fun `a debuggable host logs each failure by exception class only`() {
        ShadowLog.clear()
        val context =
            FakeContext(
                mapOf(
                    primary to SecurityException("Permission Denial: $primary"),
                    fallback to ActivityNotFoundException("No Activity found to handle $fallback"),
                ),
                debuggable = true,
            )
        ExternalLauncher(context).open(NavigationDecision.OpenExternal(primary, fallback))

        val logs = launcherLogs()
        assertEquals(listOf(Log.WARN, Log.WARN), logs.map { it.type })
        assertEquals(
            listOf(
                "Could not open the primary external URL: java.lang.SecurityException",
                "Could not open the fallback external URL: android.content.ActivityNotFoundException",
            ),
            logs.map { it.msg },
        )
        logs.forEach { log ->
            assertFalse(log.msg.contains("metamask"))
            assertEquals(null, log.throwable)
        }
    }

    @Test
    fun `a non-debuggable host logs nothing`() {
        ShadowLog.clear()
        val context = FakeContext(mapOf(primary to SecurityException()), debuggable = false)
        ExternalLauncher(context).open(NavigationDecision.OpenExternal(primary, null))
        assertTrue(launcherLogs().isEmpty())
    }

    @Test
    fun `a successful launch logs nothing`() {
        ShadowLog.clear()
        ExternalLauncher(FakeContext(emptyMap(), debuggable = true)).open(NavigationDecision.OpenExternal(primary))
        assertTrue(launcherLogs().isEmpty())
    }
}
