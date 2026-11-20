package io.github.yuushilabs.sokutei.core

import kotlin.math.hypot

/** 画像上の点（ピクセル座標）。 */
data class PxPoint(val x: Double, val y: Double) {
    fun distanceTo(other: PxPoint): Double = hypot(x - other.x, y - other.y)
}

/** よく使う基準物の実寸（ミリメートル）。静止画測定のスケール合わせ用。 */
object ReferenceObjects {
    const val CREDIT_CARD_MM = 85.60 // ISO/IEC 7810 ID-1 の長辺
    const val ONE_YEN_COIN_MM = 20.0
    const val TEN_YEN_COIN_MM = 23.5
    const val HUNDRED_YEN_COIN_MM = 22.6
    const val A4_SHORT_SIDE_MM = 210.0
    const val A4_LONG_SIDE_MM = 297.0
}

/**
 * 静止画のピクセル距離を実寸に変換するスケール。
 *
 * 基準物（実寸が分かっている物）を写真の中で測り、その px 長と実寸から作る。
 * 写真の平面と測りたい物の平面が一致している前提（奥行き方向の差は誤差になる）。
 */
class PixelScale(referencePxLength: Double, referenceRealMm: Double) {
    val mmPerPx: Double

    init {
        require(referencePxLength > 0) { "referencePxLength must be positive" }
        require(referenceRealMm > 0) { "referenceRealMm must be positive" }
        mmPerPx = referenceRealMm / referencePxLength
    }

    /** 2点間の長さ（ミリメートル）。 */
    fun lengthMm(a: PxPoint, b: PxPoint): Double = a.distanceTo(b) * mmPerPx

    /** 折れ線（複数点）の合計長（ミリメートル）。 */
    fun polylineMm(points: List<PxPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until points.size) sum += points[i - 1].distanceTo(points[i])
        return sum * mmPerPx
    }

    companion object {
        /** 基準物の両端の点と実寸からスケールを作る。 */
        fun fromReference(a: PxPoint, b: PxPoint, realMm: Double): PixelScale =
            PixelScale(a.distanceTo(b), realMm)
    }
}
