package io.github.yuushilabs.sokutei.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FrameCounterTest {

    /** 背景 bg の上に value の矩形を描いたグレースケール画像を作る。 */
    private fun grayWithRects(
        w: Int,
        h: Int,
        bg: Int,
        value: Int,
        vararg rects: IntArray,
    ): IntArray {
        val g = IntArray(w * h) { bg }
        for ((x0, y0, rw, rh) in rects) {
            for (y in y0 until y0 + rh) for (x in x0 until x0 + rw) g[y * w + x] = value
        }
        return g
    }

    @Test
    fun `暗い背景の上の明るい物ではなく暗い物を数える`() {
        val gray = grayWithRects(
            100, 80, 200, 30,
            intArrayOf(10, 10, 10, 10), intArrayOf(50, 10, 10, 10), intArrayOf(10, 50, 10, 10),
        )
        val blobs = FrameCounter.findObjects(gray, 100, 80)
        assertEquals(3, blobs.size)
        assertEquals(14.5, blobs[0].centroidX, 1e-9)
        assertEquals(14.5, blobs[0].centroidY, 1e-9)
    }

    @Test
    fun `invert で明るい物を数える`() {
        val gray = grayWithRects(
            100, 80, 30, 200,
            intArrayOf(10, 10, 10, 10), intArrayOf(50, 40, 10, 10),
        )
        val blobs = FrameCounter.findObjects(
            gray, 100, 80, FrameCounter.Options(invert = true),
        )
        assertEquals(2, blobs.size)
    }

    @Test
    fun `単発ノイズは下限面積で除外される`() {
        val gray = grayWithRects(
            100, 80, 200, 30,
            intArrayOf(10, 10, 10, 10),
        )
        // 1px のノイズを 5 個ばらまく
        for ((x, y) in listOf(90 to 5, 95 to 20, 5 to 75, 60 to 70, 30 to 30)) {
            gray[y * 100 + x] = 30
        }
        val blobs = FrameCounter.findObjects(gray, 100, 80)
        assertEquals(1, blobs.size)
    }

    @Test
    fun `一様な画像は 0 個`() {
        val gray = IntArray(50 * 40) { 128 }
        assertEquals(0, FrameCounter.findObjects(gray, 50, 40).size)
        assertEquals(
            0,
            FrameCounter.findObjects(gray, 50, 40, FrameCounter.Options(invert = true)).size,
        )
    }
}
