package io.github.yuushilabs.sokutei.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CountSmootherTest {

    @Test
    fun `単発の外れ値は中央値で抑えられる`() {
        val s = CountSmoother(windowSize = 5)
        assertEquals(3, s.add(3))
        assertEquals(3, s.add(3))
        assertEquals(3, s.add(9)) // [3,3,9] の中央値
        assertEquals(3, s.add(3))
        assertEquals(3, s.add(9)) // [3,3,9,3,9] の中央値
    }

    @Test
    fun `持続する変化には追従する`() {
        val s = CountSmoother(windowSize = 3)
        s.add(2)
        s.add(2)
        assertEquals(2, s.add(5)) // [2,2,5]
        assertEquals(5, s.add(5)) // [2,5,5]
        assertEquals(5, s.add(5))
    }

    @Test
    fun `リセット後は次の値がそのまま返る`() {
        val s = CountSmoother(windowSize = 5)
        s.add(4)
        s.add(4)
        s.reset()
        assertEquals(7, s.add(7))
    }
}
