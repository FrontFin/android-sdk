package com.meshpay.link

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.meshpay.link.internal.LinkActivity

/**
 * The Activity Result contract that opens Link and delivers its outcome.
 *
 * ```kotlin
 * val linkLauncher = registerForActivityResult(LinkContract()) { result ->
 *     when (result) {
 *         is LinkResult.Exit  -> handle(result.status)
 *         is LinkResult.Error -> log(result.error.code)
 *     }
 * }
 *
 * linkLauncher.launch(LinkConfiguration(token = sessionToken, clientId = clientId))
 * ```
 *
 * The result is delivered exactly once and survives host Activity recreation. A
 * blank `token` or `clientId` throws [IllegalArgumentException] when the
 * [LinkConfiguration] is constructed, before anything opens (§2.2).
 */
public class LinkContract : ActivityResultContract<LinkConfiguration, LinkResult>() {

    override fun createIntent(context: Context, input: LinkConfiguration): Intent =
        LinkActivity.newIntent(context, input)

    override fun parseResult(resultCode: Int, intent: Intent?): LinkResult =
        LinkActivity.resultFrom(intent)
            // No result extra means the Activity went away without reporting one
            // (e.g. the task was removed). Treat it as a cancel, never an error.
            ?: LinkResult.Exit(LinkExitStatus.CANCELED)
}
