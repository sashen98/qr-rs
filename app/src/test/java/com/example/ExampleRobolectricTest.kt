package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.scanner.BarcodePriceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Price Scanner", appName)
    }

    @Test
    fun `parse known soap barcode returns product and price`() {
        val result = BarcodePriceParser.parse("8901030381001", "EAN-13")
        assertTrue(result.title.contains("Lifebuoy"))
        assertNotNull(result.displayPrice)
        assertTrue(result.displayPrice!!.contains("140.00"))
        assertEquals("Soap & Body Care", result.category)
    }

    @Test
    fun `extract price from supermarket bill text`() {
        val billText = "Keells Super\nInvoice: INV-9872\nDate: 2026-10-04\nTotal: Rs. 1,450.00"
        val result = BarcodePriceParser.parse(billText, "QR Code")
        assertNotNull(result.displayPrice)
        assertTrue(result.displayPrice!!.contains("1,450.00"))
    }
}
