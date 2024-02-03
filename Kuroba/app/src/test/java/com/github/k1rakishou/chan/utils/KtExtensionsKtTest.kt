package com.github.k1rakishou.chan.utils

import junit.framework.TestCase.assertEquals
import org.junit.Test


class KtExtensionsKtTest {

    @Test
    fun testCountDigits() {
        assertEquals(1, 0.countDigits())
        assertEquals(2, 10.countDigits())
        assertEquals(2, (-10).countDigits())
        assertEquals(3, 100.countDigits())
        assertEquals(3, (-100).countDigits())
    }
}