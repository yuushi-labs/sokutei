package io.github.yuushilabs.sokutei.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BlobCounterTest {

    private fun maskWithRects(w: Int, h: Int, vararg rects: IntArray): BooleanArray {
        val m = BooleanArray(w * h)
        for ((x0, y0, rw, rh) in rects) {
            for (y in y0 until y0 + rh) for (x in x0 until x0 + rw) m[y * w + x] = true
        }
        return m
    }

    @Test
    fun `離れた 2 個の矩形は 2 個と数える`() {
        val mask = maskWithRects(10, 10, intArrayOf(1, 1, 2, 2), intArrayOf(6, 6, 3, 3))
        val blobs = BlobCounter.findBlobs(mask, 10, 10)
        assertEquals(2, blobs.size)
        assertEquals(listOf(4, 9), blobs.map { it.area }.sorted())
    }

    @Test
    fun `重心が矩形の中心になる`() {
        val mask = maskWithRects(10, 10, intArrayOf(2, 2, 3, 3))
        val blob = BlobCounter.findBlobs(mask, 10, 10).single()
        assertEquals(3.0, blob.centroidX, 1e-9)
        assertEquals(3.0, blob.centroidY, 1e-9)
    }

    @Test
    fun `斜めに触れているだけなら別々に数える（4 近傍）`() {
        val mask = BooleanArray(100)
        mask[0] = true // (0,0)
        mask[11] = true // (1,1) 斜め
        assertEquals(2, BlobCounter.count(mask, 10, 10))
    }

    @Test
    fun `minArea 未満のノイズは除外される`() {
        val mask = maskWithRects(10, 10, intArrayOf(1, 1, 2, 2), intArrayOf(6, 6, 3, 3))
        mask[0] = true // 1px ノイズ（ただし矩形と離れている位置）
        assertEquals(2, BlobCounter.count(mask, 10, 10, minArea = 2))
        assertEquals(3, BlobCounter.count(mask, 10, 10, minArea = 1))
    }

    @Test
    fun `空マスクは 0 個`() {
        assertEquals(0, BlobCounter.count(BooleanArray(100), 10, 10))
    }

    @Test
    fun `サイズ不一致は例外`() {
        assertThrows(IllegalArgumentException::class.java) {
            BlobCounter.count(BooleanArray(99), 10, 10)
        }
    }
}
