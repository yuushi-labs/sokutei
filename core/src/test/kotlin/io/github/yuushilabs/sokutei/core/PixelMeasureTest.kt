package io.github.yuushilabs.sokutei.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PixelMeasureTest {

    @Test
    fun `クレジットカード基準で 500px は 428mm`() {
        // 基準: 100px = 85.6mm → 0.856 mm/px。(0,0)-(300,400) は 500px。
        val scale = PixelScale(100.0, ReferenceObjects.CREDIT_CARD_MM)
        val len = scale.lengthMm(PxPoint(0.0, 0.0), PxPoint(300.0, 400.0))
        assertEquals(428.0, len, 1e-9)
    }

    @Test
    fun `fromReference で両端から作れる`() {
        val scale = PixelScale.fromReference(
            PxPoint(10.0, 10.0), PxPoint(110.0, 10.0), ReferenceObjects.ONE_YEN_COIN_MM,
        )
        assertEquals(0.2, scale.mmPerPx, 1e-12)
    }

    @Test
    fun `折れ線は合計長になる`() {
        val scale = PixelScale(10.0, 10.0) // 1 mm/px
        val pts = listOf(PxPoint(0.0, 0.0), PxPoint(3.0, 4.0), PxPoint(3.0, 9.0))
        assertEquals(10.0, scale.polylineMm(pts), 1e-9) // 5 + 5
    }

    @Test
    fun `点が 1 個以下ならゼロ`() {
        val scale = PixelScale(10.0, 10.0)
        assertEquals(0.0, scale.polylineMm(listOf(PxPoint(1.0, 1.0))), 0.0)
        assertEquals(0.0, scale.polylineMm(emptyList()), 0.0)
    }

    @Test
    fun `不正な基準は例外`() {
        assertThrows(IllegalArgumentException::class.java) { PixelScale(0.0, 10.0) }
        assertThrows(IllegalArgumentException::class.java) { PixelScale(10.0, -1.0) }
    }
}
