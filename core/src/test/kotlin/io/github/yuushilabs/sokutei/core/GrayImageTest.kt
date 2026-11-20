package io.github.yuushilabs.sokutei.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrayImageTest {

    // 3x2: [0 1 2 / 3 4 5]
    private val src = intArrayOf(0, 1, 2, 3, 4, 5)

    @Test
    fun `90 度回転で寸法が入れ替わり中身が時計回りになる`() {
        val r = GrayImage.rotateClockwise(src, 3, 2, 90)
        assertEquals(2, r.width)
        assertEquals(3, r.height)
        // [3 0 / 4 1 / 5 2]
        assertTrue(r.gray.contentEquals(intArrayOf(3, 0, 4, 1, 5, 2)))
    }

    @Test
    fun `180 度回転は反転`() {
        val r = GrayImage.rotateClockwise(src, 3, 2, 180)
        assertEquals(3, r.width)
        assertEquals(2, r.height)
        assertTrue(r.gray.contentEquals(intArrayOf(5, 4, 3, 2, 1, 0)))
    }

    @Test
    fun `270 度回転`() {
        val r = GrayImage.rotateClockwise(src, 3, 2, 270)
        assertEquals(2, r.width)
        assertEquals(3, r.height)
        // [2 5 / 1 4 / 0 3]
        assertTrue(r.gray.contentEquals(intArrayOf(2, 5, 1, 4, 0, 3)))
    }

    @Test
    fun `0 度と 360 度はそのまま`() {
        assertTrue(GrayImage.rotateClockwise(src, 3, 2, 0).gray.contentEquals(src))
        assertTrue(GrayImage.rotateClockwise(src, 3, 2, 360).gray.contentEquals(src))
    }

    @Test
    fun `90 度を 4 回で元に戻る`() {
        var cur = GrayImage.Rotated(src, 3, 2)
        repeat(4) { cur = GrayImage.rotateClockwise(cur.gray, cur.width, cur.height, 90) }
        assertEquals(3, cur.width)
        assertEquals(2, cur.height)
        assertTrue(cur.gray.contentEquals(src))
    }
}
