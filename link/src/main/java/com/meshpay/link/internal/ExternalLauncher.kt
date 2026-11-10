package com.meshpay.link.internal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Hands a URL to the OS — the wallet app for a custom scheme, the app or browser
 * for `https`. Uses `ACTION_VIEW` + `CATEGORY_BROWSABLE` with no `resolveActivity`
 * check (which is package-visibility filtered on Android 11+ and answers false
 * for wallets the host manifest does not list, ONC-447), catching
 * `ActivityNotFoundException` and falling back when there is a fallback URL.
 */
internal class ExternalLauncher(private val context: Context) {
    fun open(decision: NavigationDecision.OpenExternal) {
        if (!launch(decision.url)) {
            decision.fallbackUrl?.let { launch(it) }
        }
    }

    private fun launch(url: String): Boolean =
        try {
            val intent =
                Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            // Nothing handles it; the caller tries the fallback, else the user stays
            // on the Link page.
            false
        }
}
