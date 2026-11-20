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
 * 静止の判定は加速度だけでは行わない。等速移動中は加速度がほぼ 0 になるため、
 * 加速度だけで判定すると移動の途中で区間が分断され、変位が消えてしまう。
 * そこで、前後を移動に挟まれた静止候補区間については、次のどちらかを満たす
 * 場合にだけ静止として扱う。
 *
 * - 積分速度が「直近で確定した静止区間での速度誤差」と比べて十分小さい
 *   （[DEFAULT_STILL_VELOCITY_THRESHOLD]）。速度の基準は確定した静止のたびに
 *   更新するので、前の移動で残った速度誤差を次の判定に持ち込まない。
 * - 区間が [DEFAULT_LONG_STILL_MS] 以上続いている。速度誤差が残っていても、
 *   それだけ長く加速度ゼロが続けば静止とみなす（代償として、同じ長さ以上の
 *   等速移動は静止と誤認しうる）。
 *
 * それでも絶対精度は出ない（目安は SPEC.md / README の精度に関する注意を参照）。
 */
class VerticalDisplacementEstimator(
    private val stillAccelThreshold: Double = DEFAULT_STILL_THRESHOLD,
    private val stillWindowMillis: Long = DEFAULT_STILL_WINDOW_MS,
    private val stillVelocityThreshold: Double = DEFAULT_STILL_VELOCITY_THRESHOLD,
    private val longStillMillis: Long = DEFAULT_LONG_STILL_MS,
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

    fun displacementMeters(): Double = estimateDisplacement(
        samples, stillAccelThreshold, stillWindowMillis, stillVelocityThreshold, longStillMillis,
    )

    companion object {
        const val DEFAULT_STILL_THRESHOLD = 0.35 // m/s^2
        const val DEFAULT_STILL_WINDOW_MS = 250L
        const val DEFAULT_STILL_VELOCITY_THRESHOLD = 0.12 // m/s
        const val DEFAULT_LONG_STILL_MS = 2000L

        fun estimateDisplacement(
            samples: List<AccelSample>,
            stillAccelThreshold: Double = DEFAULT_STILL_THRESHOLD,
            stillWindowMillis: Long = DEFAULT_STILL_WINDOW_MS,
            stillVelocityThreshold: Double = DEFAULT_STILL_VELOCITY_THRESHOLD,
            longStillMillis: Long = DEFAULT_LONG_STILL_MS,
        ): Double {
            if (samples.size < 2) return 0.0
            val accelStill = stillFlags(samples, stillAccelThreshold, stillWindowMillis)
            val still = applyVelocityVeto(
                samples, accelStill, stillVelocityThreshold, longStillMillis,
            )
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

        /** 補正なしの積分速度。先頭サンプルの速度は 0 とする。 */
        private fun rawVelocities(samples: List<AccelSample>): DoubleArray {
            val v = DoubleArray(samples.size)
            var cur = 0.0
            for (k in 1 until samples.size) {
                val dt = (samples[k].tMillis - samples[k - 1].tMillis) / 1000.0
                if (dt > 0) {
                    cur += (samples[k].aVertical + samples[k - 1].aVertical) / 2.0 * dt
                }
                v[k] = cur
            }
            return v
        }

        /**
         * 等速移動の誤分割を防ぐための速度条件。
         *
         * 前後を移動に挟まれた静止候補区間は、次のどちらかで静止として確定する。
         * - 積分速度が、直近で確定した静止区間時点の速度誤差（基準）から
         *   [velocityThreshold] 以内まで下がっている
         * - 区間が [longStillMillis] 以上続いている
         *
         * 確定した静止区間では速度基準をその区間の積分速度へ更新する。これにより、
         * 前の移動で残った速度誤差（例: 加速度バイアスで残る +0.4 m/s）を次の
         * 静止判定に持ち込まない。どちらも満たさない区間は等速移動の一部とみなして
         * 静止扱いを取り消す。先頭と末尾の静止区間は ZUPT のアンカーとして常に残す
         * （利用者は静止した状態で開始・停止するため）。
         */
        private fun applyVelocityVeto(
            samples: List<AccelSample>,
            accelStill: BooleanArray,
            velocityThreshold: Double,
            longStillMillis: Long,
        ): BooleanArray {
            val n = samples.size
            val still = accelStill.copyOf()
            val velocity = rawVelocities(samples)
            var velocityBaseline = 0.0
            var i = 0
            while (i < n) {
                if (!accelStill[i]) {
                    i++
                    continue
                }
                var end = i
                while (end < n && accelStill[end]) end++
                val isEdge = i == 0 || end == n
                if (isEdge) {
                    velocityBaseline = velocity[end - 1]
                } else {
                    var minAdjusted = Double.MAX_VALUE
                    for (k in i until end) {
                        minAdjusted = minOf(minAdjusted, abs(velocity[k] - velocityBaseline))
                    }
                    val durationMs = samples[end - 1].tMillis - samples[i].tMillis
                    if (minAdjusted <= velocityThreshold || durationMs >= longStillMillis) {
                        velocityBaseline = velocity[end - 1]
                    } else {
                        for (k in i until end) still[k] = false
                    }
                }
                i = end
            }
            return still
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
