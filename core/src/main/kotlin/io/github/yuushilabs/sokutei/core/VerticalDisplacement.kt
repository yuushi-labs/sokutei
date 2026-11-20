package io.github.yuushilabs.sokutei.core

import kotlin.math.abs

/** 世界座標系の鉛直成分だけを取り出した加速度サンプル（重力は除去済み）。 */
data class AccelSample(val tMillis: Long, val aVertical: Double)

/**
 * 鉛直加速度を二重積分して上下方向の変位（メートル）を推定する。
 *
 * スマホの加速度センサはドリフトが大きいため、そのまま二重積分すると
 * 数秒で誤差が爆発する。本実装では次の2つで誤差を抑える。
 *
 * 1. ZUPT（Zero Velocity Update）: 静止区間では速度を 0 とみなす。
 * 2. 線形ドリフト補正: 静止→静止で挟まれた移動区間では、積分した速度が
 *    区間末尾で 0 になるよう、速度に線形ランプ状の誤差が乗ったと仮定して差し引く。
 *
 * それでも絶対精度は出ない（目安は SPEC.md / README の精度に関する注意を参照）。
 */
class VerticalDisplacementEstimator(
    private val stillAccelThreshold: Double = DEFAULT_STILL_THRESHOLD,
    private val stillWindowMillis: Long = DEFAULT_STILL_WINDOW_MS,
) {
    private val samples = mutableListOf<AccelSample>()

    fun reset() {
        samples.clear()
    }

    fun add(tMillis: Long, aVertical: Double) {
        samples.add(AccelSample(tMillis, aVertical))
    }

    val sampleCount: Int get() = samples.size

    /** 直近のサンプル列が静止状態かどうか。 */
    fun isStill(): Boolean {
        if (samples.isEmpty()) return true
        val last = samples.last()
        val windowStart = last.tMillis - stillWindowMillis
        var sawAny = false
        for (s in samples.asReversed()) {
            if (s.tMillis < windowStart) break
            sawAny = true
            if (abs(s.aVertical) >= stillAccelThreshold) return false
        }
        return sawAny
    }

    fun displacementMeters(): Double =
        estimateDisplacement(samples, stillAccelThreshold, stillWindowMillis)

    companion object {
        const val DEFAULT_STILL_THRESHOLD = 0.35 // m/s^2
        const val DEFAULT_STILL_WINDOW_MS = 250L

        fun estimateDisplacement(
            samples: List<AccelSample>,
            stillAccelThreshold: Double = DEFAULT_STILL_THRESHOLD,
            stillWindowMillis: Long = DEFAULT_STILL_WINDOW_MS,
        ): Double {
            if (samples.size < 2) return 0.0
            val still = stillFlags(samples, stillAccelThreshold, stillWindowMillis)
            var total = 0.0
            var i = 0
            while (i < samples.size) {
                if (still[i]) {
                    i++
                    continue
                }
                // 移動区間 [segStart, segEnd]（両端は静止サンプルで挟む）
                var segEnd = i
                while (segEnd < samples.size && !still[segEnd]) segEnd++
                val from = if (i > 0) i - 1 else i // 直前の静止サンプル
                val to = if (segEnd < samples.size) segEnd else samples.size - 1 // 直後の静止サンプル
                total += integrateSegment(samples, from, to, hasEndStill = segEnd < samples.size)
                i = segEnd
            }
            return total
        }

        /** 各サンプルが「静止ウィンドウの中にある」かを返す。 */
        private fun stillFlags(
            samples: List<AccelSample>,
            threshold: Double,
            windowMillis: Long,
        ): BooleanArray {
            val n = samples.size
            val flags = BooleanArray(n)
            var left = 0
            var right = 0
            var violations = 0
            // スライディングウィンドウ [t - window/2, t + window/2] 内の違反数を数える
            for (i in 0 until n) {
                val t = samples[i].tMillis
                while (right < n && samples[right].tMillis <= t + windowMillis / 2) {
                    if (abs(samples[right].aVertical) >= threshold) violations++
                    right++
                }
                while (left < n && samples[left].tMillis < t - windowMillis / 2) {
                    if (abs(samples[left].aVertical) >= threshold) violations--
                    left++
                }
                flags[i] = violations == 0
            }
            return flags
        }

        /** 静止（速度0）から静止（速度0）までの1区間を台形積分し、速度ドリフトを線形補正する。 */
        private fun integrateSegment(
            samples: List<AccelSample>,
            from: Int,
            to: Int,
            hasEndStill: Boolean,
        ): Double {
            if (to <= from) return 0.0
            val count = to - from + 1
            val velocity = DoubleArray(count)
            var v = 0.0
            for (k in 1 until count) {
                val dt = (samples[from + k].tMillis - samples[from + k - 1].tMillis) / 1000.0
                if (dt <= 0) continue
                val aAvg = (samples[from + k].aVertical + samples[from + k - 1].aVertical) / 2.0
                v += aAvg * dt
                velocity[k] = v
            }
            // 区間末尾の速度は本来 0。残った分は線形に蓄積した誤差とみなして差し引く。
            if (hasEndStill) {
                val vEnd = velocity[count - 1]
                val tSpan = (samples[to].tMillis - samples[from].tMillis).toDouble()
                if (tSpan > 0 && vEnd != 0.0) {
                    for (k in 0 until count) {
                        val frac = (samples[from + k].tMillis - samples[from].tMillis) / tSpan
                        velocity[k] -= vEnd * frac
                    }
                }
            }
            var x = 0.0
            for (k in 1 until count) {
                val dt = (samples[from + k].tMillis - samples[from + k - 1].tMillis) / 1000.0
                if (dt <= 0) continue
                x += (velocity[k] + velocity[k - 1]) / 2.0 * dt
            }
            return x
        }
    }
}
