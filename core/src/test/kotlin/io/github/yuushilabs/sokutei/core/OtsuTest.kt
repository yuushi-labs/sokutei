package io.github.yuushilabs.sokutei.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OtsuTest {

    @Test
    fun `二峰性の画像では閾値が谷に入る`() {
        val gray = IntArray(1000) { if (it < 500) 30 else 200 }
        val t = Otsu.threshold(gray)
        assertTrue(t in 30..199, "threshold=$t")
        val mask = Otsu.binarize(gray, t)
        assertEquals(500, mask.count { it })
    }

    @Test
    fun `invert で前景が反転する`() {
        val gray = intArrayOf(10, 20, 200, 210)
        val t = Otsu.threshold(gray)
        val normal = Otsu.binarize(gray, t, invert = false)
        val inverted = Otsu.binarize(gray, t, invert = true)
        assertTrue(normal.indices.all { normal[it] != inverted[it] })
    }

    @Test
    fun `グラデーションでも閾値は範囲内`() {
        val gray = IntArray(256) { it }
        val t = Otsu.threshold(gray)
        assertTrue(t in 0..255)
    }
}
