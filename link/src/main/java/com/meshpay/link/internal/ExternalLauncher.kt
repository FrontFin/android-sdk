package com.meshpay.link.internal

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.core.net.toUri

/**
 * Hands a URL to the OS — the wallet app for a custom scheme, the app or browser
 * for `https`. Uses `ACTION_VIEW` + `CATEGORY_BROWSABLE` with no `resolveActivity`
 * check (which is package-visibility filtered on Android 11+ and answers false
 * for wallets the host manifest does not list, ONC-447), and falls back when
 * there is a fallback URL.
 *
 * The URL is chosen by web content, so a failed launch must never escape into
 * the WebView callback and crash the host app (SR-6, PRG-2290).
 */
internal class ExternalLauncher(private val context: Context) {
    /** @return `true` when the primary or the fallback URL was handed to the OS. */
    fun open(decision: NavigationDecision.OpenExternal): Boolean =
        launch(decision.url, "primary") || decision.fallbackUrl?.let { launch(it, "fallback") } == true

    @Suppress("TooGenericExceptionCaught")
    private fun launch(
        url: String,
        target: String,
    ): Boolean =
        try {
            val intent =
                Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            true
        } catch (e: RuntimeException) {
            // ActivityNotFoundException (nothing handles it), SecurityException (the
            // handler is not exported or needs a permission), or a RuntimeException
            // wrapping TransactionTooLargeException. All mean "not opened": the
            // caller tries the fallback, else the user stays on the Link page.
            logLaunchFailure(target, e)
            false
        }

    /**
     * Tells the integrator why a wallet link did nothing, without crashing the
     * host. Only when the host app is debuggable: the SDK ships as a release AAR,
     * so its own `BuildConfig.DEBUG` is always false. Logs the exception class
     * only, never its message, which can embed the Intent and with it the URL (§2.7).
     */
    private fun logLaunchFailure(
        target: String,
        error: RuntimeException,
    ) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Log.w(LOG_TAG, "Could not open the $target external URL: ${error.javaClass.name}")
    }

    internal companion object {
        const val LOG_TAG: String = "MeshPayLink"
    }
}
