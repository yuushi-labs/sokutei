package io.github.yuushilabs.sokutei.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitRectTest {

    @Test
    fun `横長画像は幅いっぱいにフィットする`() {
        // コンテナ 100x100、画像 200x100 → scale 0.5 → 100x50、上下に 25 ずつ余白
        val fit = computeFitRect(100f, 100f, 200, 100)
        assertEquals(0f, fit.left, 1e-6f)
        assertEquals(25f, fit.top, 1e-6f)
        assertEquals(100f, fit.width, 1e-6f)
        assertEquals(50f, fit.height, 1e-6f)
    }

    @Test
    fun `相対座標と表示座標は往復する`() {
        val fit = computeFitRect(300f, 200f, 100, 100)
        // scale=2 → 200x200、left=50, top=0
        val rel = fit.toRelative(fit.toDisplayX(0.25f), fit.toDisplayY(0.75f))
        assertEquals(0.25f, rel!!.first, 1e-6f)
        assertEquals(0.75f, rel.second, 1e-6f)
    }

    @Test
    fun `画像の外のタップは null`() {
        val fit = computeFitRect(100f, 100f, 200, 100) // top=25
        assertNull(fit.toRelative(50f, 10f)) // 上の余白
        assertNull(fit.toRelative(50f, 90f)) // 下の余白
    }

    @Test
    fun `不正なサイズでは空の矩形`() {
        val fit = computeFitRect(0f, 100f, 200, 100)
        assertEquals(0f, fit.width, 0f)
    }
}
