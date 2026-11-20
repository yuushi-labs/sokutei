package io.github.yuushilabs.sokutei.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TiltMathTest {

    @Test
    fun `下端 -45 度・上端 +45 度・カメラ高 1_5m なら高さ 3m`() {
        val h = TiltMath.heightMeters(1.5, -45.0, 45.0)
        assertEquals(3.0, h, 1e-9)
    }

    @Test
    fun `下端 -45 度なら距離はカメラ高と等しい`() {
        assertEquals(1.5, TiltMath.distanceMeters(1.5, -45.0), 1e-9)
    }

    @Test
    fun `上端が水平なら高さはカメラ高と等しい`() {
        val h = TiltMath.heightMeters(1.2, -30.0, 0.0)
        assertEquals(1.2, h, 1e-9)
    }

    @Test
    fun `下端が水平以上なら例外`() {
        assertThrows(IllegalArgumentException::class.java) {
            TiltMath.heightMeters(1.5, 0.0, 30.0)
        }
    }

    @Test
    fun `角度の順序が逆なら例外`() {
        assertThrows(IllegalArgumentException::class.java) {
            TiltMath.heightMeters(1.5, -10.0, -20.0)
        }
    }

    @Test
    fun `下端が -90 度以下なら例外`() {
        assertThrows(IllegalArgumentException::class.java) {
            TiltMath.heightMeters(1.5, -90.0, 30.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TiltMath.distanceMeters(1.5, -95.0)
        }
    }

    @Test
    fun `上端が 90 度以上なら例外`() {
        assertThrows(IllegalArgumentException::class.java) {
            TiltMath.heightMeters(1.5, -45.0, 90.0)
        }
    }
}
