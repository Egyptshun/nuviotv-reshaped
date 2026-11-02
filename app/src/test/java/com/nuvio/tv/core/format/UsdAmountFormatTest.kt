package com.nuvio.tv.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsdAmountFormatTest {
    @Test
    fun formatUsdAmountForDisplay_returnsNullForUnknown() {
        assertNull(formatUsdAmountForDisplay(null))
        assertNull(formatUsdAmountForDisplay(0L))
        assertNull(formatUsdAmountForDisplay(-1L))
    }

    @Test
    fun formatUsdAmountForDisplay_groupsThousands() {
        assertEquals("$1,234,567", formatUsdAmountForDisplay(1_234_567L))
        assertEquals("$500", formatUsdAmountForDisplay(500L))
    }
}
