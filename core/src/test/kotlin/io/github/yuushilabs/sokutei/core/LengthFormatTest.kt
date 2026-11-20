package io.github.yuushilabs.sokutei.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LengthFormatTest {
    @Test
    fun `100mm 未満は mm 表記`() {
        assertEquals("85.6 mm", LengthFormat.format(85.6))
        assertEquals("0.0 mm", LengthFormat.format(0.0))
    }

    @Test
    fun `100mm 以上は cm 表記`() {
        assertEquals("10.0 cm", LengthFormat.format(100.0))
        assertEquals("42.8 cm", LengthFormat.format(428.0))
    }

    @Test
    fun `1m 以上は m 表記`() {
        assertEquals("1.00 m", LengthFormat.format(1000.0))
        assertEquals("2.35 m", LengthFormat.format(2350.0))
    }

    @Test
    fun `負の値も整形できる`() {
        assertEquals("-50.0 mm", LengthFormat.format(-50.0))
    }
}
