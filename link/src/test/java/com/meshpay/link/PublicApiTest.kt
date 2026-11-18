package com.meshpay.link

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.parcelableCreator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The hand-written value semantics of the public input types, which are plain
 * classes (not data classes) so they can grow in 1.x without breaking callers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PublicApiTest {
    private val options =
        LinkOptions(
            environment = LinkEnvironment.SBX,
            language = "es",
            theme = LinkTheme.DARK,
            integrationId = "int-1",
            displayFiatCurrency = "DKK",
        )

    @Test
    fun `LinkOptions defaults`() {
        val defaults = LinkOptions()
        assertEquals(null, defaults.environment)
        assertEquals(null, defaults.language)
        assertEquals(null, defaults.theme)
        assertEquals(null, defaults.integrationId)
        assertEquals(null, defaults.displayFiatCurrency)
    }

    @Test
    fun `LinkOptions equal by value`() {
        val same = LinkOptions(LinkEnvironment.SBX, "es", LinkTheme.DARK, "int-1", "DKK")
        assertEquals(options, options)
        assertEquals(options, same)
        assertEquals(options.hashCode(), same.hashCode())
        assertNotEquals(options, null)
        assertNotEquals(options, "not options")
    }

    @Test
    fun `LinkOptions differ when any field differs`() {
        val variants =
            listOf(
                LinkOptions(LinkEnvironment.PROD, "es", LinkTheme.DARK, "int-1", "DKK"),
                LinkOptions(LinkEnvironment.SBX, "en", LinkTheme.DARK, "int-1", "DKK"),
                LinkOptions(LinkEnvironment.SBX, "es", LinkTheme.LIGHT, "int-1", "DKK"),
                LinkOptions(LinkEnvironment.SBX, "es", LinkTheme.DARK, "int-2", "DKK"),
                LinkOptions(LinkEnvironment.SBX, "es", LinkTheme.DARK, "int-1", "USD"),
            )
        variants.forEach { assertNotEquals(options, it) }
    }

    @Test
    fun `LinkOptions toString names every field`() {
        assertEquals(
            "LinkOptions(environment=SBX, language=es, theme=DARK, integrationId=int-1, displayFiatCurrency=DKK)",
            options.toString(),
        )
    }

    @Test
    fun `LinkConfiguration equal by value`() {
        val config = LinkConfiguration("tok", "cid", options)
        val same =
            LinkConfiguration("tok", "cid", LinkOptions(LinkEnvironment.SBX, "es", LinkTheme.DARK, "int-1", "DKK"))
        assertEquals(config, config)
        assertEquals(config, same)
        assertEquals(config.hashCode(), same.hashCode())
        assertNotEquals(config, null)
        assertNotEquals(config, "not a config")
        assertNotEquals(config, LinkConfiguration("tok2", "cid", options))
        assertNotEquals(config, LinkConfiguration("tok", "cid2", options))
        assertNotEquals(config, LinkConfiguration("tok", "cid"))
        assertEquals(LinkOptions(), LinkConfiguration("tok", "cid").options)
    }

    @Test
    fun `LinkConfiguration toString never contains the token`() {
        val text = LinkConfiguration("secret-session-token", "cid", options).toString()
        assertFalse(text.contains("secret-session-token"))
        assertTrue(text.contains("token=<redacted>"))
        assertTrue(text.contains("clientId=cid"))
        assertTrue(text.contains(options.toString()))
    }

    @Test
    fun `input types survive a Parcel round trip`() {
        val config = LinkConfiguration("tok", "cid", options)
        assertEquals(config, roundTrip(config, parcelableCreator<LinkConfiguration>()))
        assertEquals(options, roundTrip(options, parcelableCreator<LinkOptions>()))
    }

    @Test
    fun `LinkSdk VERSION is the build version`() {
        assertEquals(BuildConfig.SDK_VERSION, LinkSdk.VERSION)
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(LinkSdk.VERSION))
    }

    private fun <T : Parcelable> roundTrip(
        value: T,
        creator: Parcelable.Creator<T>,
    ): T {
        val parcel = Parcel.obtain()
        try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
