package io.github.yuushilabs.sokutei.core

import kotlin.math.hypot

/**
 * 重心の時系列から、水平線を横切った回数を数える（通過カウント用）。
 *
 * - フレーム間の対応付けは二段構え。第1段は [matchRadius] 以内の最近傍を貪欲に
 *   対応付け、第2段は残った同士で「相互に最も近く、かつ2番手より明確に近い」組
 *   だけを [relaxedMatchRadius] まで広げて対応付ける。速い物（1フレームで
 *   [matchRadius] を超えて動く物）を取りこぼさないためで、曖昧な組は広げない
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
    private val relaxedMatchRadius: Double = 0.25,
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
        val assignment = match(centroids)
        val used = BooleanArray(centroids.size)
        val next = mutableListOf<Track>()
        for ((index, track) in tracks.withIndex()) {
            val centroidIndex = assignment[index]
            if (centroidIndex >= 0) {
                used[centroidIndex] = true
                val (nx, ny) = centroids[centroidIndex]
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

    /**
     * 追跡 → 重心の対応付け（対応する重心の添字。なければ -1）。
     * 第1段: [matchRadius] 以内の最近傍を貪欲に。
     * 第2段: 残った同士で、相互に最も近く、2番手との差が明確（0.8倍未満）な組を
     * [relaxedMatchRadius] まで許して、距離の小さい順に確定する。
     */
    private fun match(centroids: List<Pair<Double, Double>>): IntArray {
        val result = IntArray(tracks.size) { -1 }
        val used = BooleanArray(centroids.size)

        fun distance(track: Track, i: Int): Double =
            hypot(centroids[i].first - track.x, centroids[i].second - track.y)

        // 第1段
        for ((index, track) in tracks.withIndex()) {
            var bestIdx = -1
            var bestDist = Double.MAX_VALUE
            for (i in centroids.indices) {
                if (used[i]) continue
                val d = distance(track, i)
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            if (bestIdx >= 0 && bestDist <= matchRadius) {
                result[index] = bestIdx
                used[bestIdx] = true
            }
        }

        // 第2段
        val candidates = mutableListOf<Triple<Double, Int, Int>>() // 距離, 追跡, 重心
        for ((index, track) in tracks.withIndex()) {
            if (result[index] >= 0) continue
            // この追跡から見た未使用の重心のうち、最も近いものと2番目
            var nearest = -1
            var nearestDist = Double.MAX_VALUE
            var secondDist = Double.MAX_VALUE
            for (i in centroids.indices) {
                if (used[i]) continue
                val d = distance(track, i)
                if (d < nearestDist) {
                    secondDist = nearestDist
                    nearestDist = d
                    nearest = i
                } else if (d < secondDist) {
                    secondDist = d
                }
            }
            if (nearest < 0 || nearestDist > relaxedMatchRadius) continue
            if (secondDist != Double.MAX_VALUE && nearestDist >= secondDist * 0.8) continue
            // その重心から見ても、この追跡が最も近く明確であること
            var trackNearest = -1
            var trackNearestDist = Double.MAX_VALUE
            var trackSecondDist = Double.MAX_VALUE
            for ((otherIndex, other) in tracks.withIndex()) {
                if (result[otherIndex] >= 0) continue
                val d = hypot(
                    centroids[nearest].first - other.x,
                    centroids[nearest].second - other.y,
                )
                if (d < trackNearestDist) {
                    trackSecondDist = trackNearestDist
                    trackNearestDist = d
                    trackNearest = otherIndex
                } else if (d < trackSecondDist) {
                    trackSecondDist = d
                }
            }
            if (trackNearest != index) continue
            if (trackSecondDist != Double.MAX_VALUE && trackNearestDist >= trackSecondDist * 0.8) {
                continue
            }
            candidates.add(Triple(nearestDist, index, nearest))
        }
        candidates.sortBy { it.first }
        for ((_, trackIndex, centroidIndex) in candidates) {
            if (result[trackIndex] < 0 && !used[centroidIndex]) {
                result[trackIndex] = centroidIndex
                used[centroidIndex] = true
            }
        }
        return result
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
