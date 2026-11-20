package io.github.yuushilabs.sokutei.core

import kotlin.math.hypot

/**
 * 重心の時系列から、水平線を横切った回数を数える（通過カウント用）。
 *
 * - フレーム間の対応付けは最近傍の貪欲マッチング（[matchRadius] 以内）
 * - 線の近傍 [deadband] では「どちら側か」を更新しない（線際の震えで二重に数えない）
 * - [maxMissedFrames] を超えて見失った追跡は破棄する。再び現れた物は別物として扱う
 * - 線の内側（デッドバンド内）で初めて現れた物は、出自が分からないため横切りを数えない
 *
 * 座標は画像相対（0..1）。線は y = [lineY] の水平線。
 * 物同士がすれ違う・重なる場面では対応付けを間違えることがある（限界は docs/ACCURACY.md）。
 */
class LineCrossingCounter(
    private val lineY: Double = 0.5,
    private val matchRadius: Double = 0.10,
    private val deadband: Double = 0.02,
    private val maxMissedFrames: Int = 5,
) {
    private class Track(var x: Double, var y: Double, var side: Int, var missed: Int)

    private val tracks = mutableListOf<Track>()

    /** 上から下へ横切った回数。 */
    var downCount = 0
        private set

    /** 下から上へ横切った回数。 */
    var upCount = 0
        private set

    val totalCount: Int get() = downCount + upCount

    /** 1フレーム分の重心一覧（画像相対座標）を取り込む。 */
    fun update(centroids: List<Pair<Double, Double>>) {
        val used = BooleanArray(centroids.size)
        val next = mutableListOf<Track>()
        for (track in tracks) {
            var bestIdx = -1
            var bestDist = Double.MAX_VALUE
            for (i in centroids.indices) {
                if (used[i]) continue
                val d = hypot(centroids[i].first - track.x, centroids[i].second - track.y)
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            if (bestIdx >= 0 && bestDist <= matchRadius) {
                used[bestIdx] = true
                val (nx, ny) = centroids[bestIdx]
                val newSide = sideOf(ny)
                if (newSide != 0 && track.side != 0 && newSide != track.side) {
                    if (newSide > track.side) downCount++ else upCount++
                }
                if (newSide != 0) track.side = newSide
                track.x = nx
                track.y = ny
                track.missed = 0
                next.add(track)
            } else {
                track.missed++
                if (track.missed <= maxMissedFrames) next.add(track)
            }
        }
        for (i in centroids.indices) {
            if (!used[i]) {
                val (x, y) = centroids[i]
                next.add(Track(x, y, sideOf(y), 0))
            }
        }
        tracks.clear()
        tracks.addAll(next)
    }

    /** -1: 線より上、+1: 線より下、0: デッドバンド内。 */
    private fun sideOf(y: Double): Int = when {
        y < lineY - deadband -> -1
        y > lineY + deadband -> 1
        else -> 0
    }

    /**
     * 追跡だけを破棄し、累積カウントは保持する。
     * 検出設定（反転など）が変わると検出対象が入れ替わるため、変更前の追跡を
     * 変更後の別物へ対応付けて幻の横切りを数えないよう、設定変更時に呼ぶ。
     */
    fun clearTracks() {
        tracks.clear()
    }

    fun reset() {
        tracks.clear()
        downCount = 0
        upCount = 0
    }
}
