package io.github.yuushilabs.sokutei.core

import kotlin.math.tan

/**
 * 三角測量による高さ測定。
 *
 * カメラ（スマホ）を高さ H の位置に構え、物体の下端を見下ろす角度と
 * 上端を見上げる角度から、水平距離と物体の高さを求める。
 * 角度は水平を 0° とし、上向きを正、下向き（見下ろし）を負とする。
 */
object TiltMath {

    /** 物体の下端までの水平距離（メートル）。下端は水平より下にある前提。 */
    fun distanceMeters(cameraHeightM: Double, bottomAngleDeg: Double): Double {
        require(cameraHeightM > 0) { "cameraHeightM must be positive" }
        require(bottomAngleDeg < 0) { "bottomAngleDeg must be negative (looking down)" }
        // tan の特異点（-90°）に近づくと距離が発散するため、直下は測定不能として弾く
        require(bottomAngleDeg > -90) { "bottomAngleDeg must be greater than -90" }
        val b = Math.toRadians(bottomAngleDeg)
        return -cameraHeightM / tan(b)
    }

    /** 物体の高さ（メートル）。 */
    fun heightMeters(cameraHeightM: Double, bottomAngleDeg: Double, topAngleDeg: Double): Double {
        require(topAngleDeg > bottomAngleDeg) { "topAngleDeg must be greater than bottomAngleDeg" }
        // tan の特異点（+90°）では高さが発散するため、真上は測定不能として弾く
        require(topAngleDeg < 90) { "topAngleDeg must be less than 90" }
        val d = distanceMeters(cameraHeightM, bottomAngleDeg)
        val t = Math.toRadians(topAngleDeg)
        return cameraHeightM + d * tan(t)
    }
}
