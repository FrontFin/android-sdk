package com.meshpay.link

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.meshpay.link.internal.LOADER_SPIN_MS
import com.meshpay.link.internal.LinkCloseButton
import com.meshpay.link.internal.LinkLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sizes and behaviour of the native ✕ and loader, matching react-native-sdk. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, qualifiers = "xhdpi")
class LinkOverlaysTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val density = context.resources.displayMetrics.density

    @Test
    fun `close button is a 40dp circle padded by a 12dp hit slop`() {
        val button = LinkCloseButton(context, Color.BLACK)
        button.measure(0, 0)
        val expected = ((40 + 2 * 12) * density).toInt()
        assertEquals(expected, button.measuredWidth)
        assertEquals(expected, button.measuredHeight)
        assertEquals("Close", button.contentDescription)
        assertTrue(button.isClickable)
        draw(button)
    }

    @Test
    fun `loader is a 24dp ring that never takes touches`() {
        val loader = LinkLoader(context, Color.WHITE)
        loader.measure(0, 0)
        assertEquals((24 * density).toInt(), loader.measuredWidth)
        assertEquals((24 * density).toInt(), loader.measuredHeight)
        assertEquals("Loading", loader.contentDescription)
        assertFalse(loader.isClickable)
        assertEquals(LOADER_SPIN_MS, loader.spinner.duration)
        draw(loader)
    }

    @Test
    fun `loader spins only while visible`() {
        val loader = LinkLoader(context, Color.WHITE)
        loader.onVisibilityAggregated(true)
        assertTrue(loader.spinner.isStarted)
        loader.onVisibilityAggregated(true)
        assertTrue(loader.spinner.isStarted)
        loader.onVisibilityAggregated(false)
        assertFalse(loader.spinner.isStarted)
    }

    private fun draw(view: View) {
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
    }
}
