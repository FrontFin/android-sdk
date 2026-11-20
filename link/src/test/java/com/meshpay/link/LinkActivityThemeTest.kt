package com.meshpay.link

import androidx.test.core.app.ApplicationProvider
import com.meshpay.link.internal.LinkActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/**
 * The WebView reports `prefers-color-scheme` from the Activity theme (targetSdk 33+), so the
 * theme must match the native one, or Link turns dark on a light device (PRG-2331).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkActivityThemeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    @Config(qualifiers = "notnight")
    fun `system theme on a light device gives a light Activity theme`() {
        assertEquals(true, isLightTheme(launch(LinkTheme.SYSTEM)))
    }

    @Test
    @Config(qualifiers = "night")
    fun `system theme on a dark device gives a dark Activity theme`() {
        assertEquals(false, isLightTheme(launch(LinkTheme.SYSTEM)))
    }

    @Test
    @Config(qualifiers = "night")
    fun `explicit light wins over a dark device`() {
        assertEquals(true, isLightTheme(launch(LinkTheme.LIGHT)))
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `token theme wins over the device for system`() {
        val json = """{"v":1,"c":"code","th":"LINK_THEME_DARK"}"""
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        assertEquals(false, isLightTheme(launch(LinkTheme.SYSTEM, token)))
    }

    private fun launch(
        theme: LinkTheme,
        token: String = "tok",
    ): LinkActivity =
        Robolectric
            .buildActivity(
                LinkActivity::class.java,
                LinkActivity.newIntent(context, LinkConfiguration(token, "cid", LinkOptions { theme(theme) })),
            ).setup()
            .get()

    private fun isLightTheme(activity: LinkActivity): Boolean {
        val attrs = activity.theme.obtainStyledAttributes(intArrayOf(android.R.attr.isLightTheme))
        try {
            return attrs.getBoolean(0, false)
        } finally {
            attrs.recycle()
        }
    }
}
