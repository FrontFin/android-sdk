package com.meshpay.link

import android.content.Intent
import android.os.Parcel
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import com.meshpay.link.internal.LinkActivity
import org.junit.Assert.assertEquals
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
    fun `result and configuration survive the Intent crossing a process boundary`() {
        val exit = LinkResult.Exit(LinkExitStatus.SUCCESS)
        val error = LinkResult.Error(LinkError(LinkErrorCode.LOAD_TIMEOUT, "timeout"))
        assertEquals(
            exit,
            LinkContract().parseResult(-1, parcelled(Intent().putExtra(LinkActivity.EXTRA_RESULT, exit))),
        )
        assertEquals(
            error,
            LinkContract().parseResult(-1, parcelled(Intent().putExtra(LinkActivity.EXTRA_RESULT, error))),
        )

        val config = LinkConfiguration("tok", "cid", LinkOptions { environment(LinkEnvironment.SBX) })
        val launch = parcelled(LinkContract().createIntent(context, config))
        assertEquals(
            config,
            IntentCompat.getParcelableExtra(launch, LinkActivity.EXTRA_CONFIG, LinkConfiguration::class.java),
        )
    }

    @Test
    fun `blank token or clientId does not throw at construction`() {
        // Blank values are reported as load-error by LinkActivity, never thrown
        // to the host (§2.2); constructing the configuration must not throw.
        val blankToken = LinkConfiguration("  ", "cid")
        val blankClientId = LinkConfiguration("tok", "   ")
        assertEquals("  ", blankToken.token)
        assertEquals("   ", blankClientId.clientId)
    }

    /** Writes the Intent to a Parcel and reads it back, as Android does between processes. */
    private fun parcelled(intent: Intent): Intent {
        val parcel = Parcel.obtain()
        try {
            intent.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return Intent.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
