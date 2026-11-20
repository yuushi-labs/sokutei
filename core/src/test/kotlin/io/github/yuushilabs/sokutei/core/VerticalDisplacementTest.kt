package io.github.yuushilabs.sokutei.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sin

class VerticalDisplacementTest {

    /** dt=10ms でサンプル列を作る。phases は (継続秒, 加速度) のリスト。 */
    private fun synthesize(phases: List<Pair<Double, Double>>): List<AccelSample> {
        val out = mutableListOf<AccelSample>()
        var t = 0L
        for ((seconds, a) in phases) {
            val steps = (seconds * 100).toInt()
            repeat(steps) {
                out.add(AccelSample(t, a))
                t += 10
            }
        }
        return out
    }

    @Test
    fun `静止だけなら変位はゼロ`() {
        val samples = synthesize(listOf(2.0 to 0.0))
        val est = VerticalDisplacementEstimator.estimateDisplacement(samples)
        assertEquals(0.0, est, 1e-9)
    }

    @Test
    fun `既知の台形プロファイルで 1 メートル上がる`() {
        // +4 m/s^2 で 0.5 秒（0.5m 上昇・速度 2m/s）、-4 m/s^2 で 0.5 秒（さらに 0.5m・停止）
        val samples = synthesize(
            listOf(0.5 to 0.0, 0.5 to 4.0, 0.5 to -4.0, 0.5 to 0.0),
        )
        val est = VerticalDisplacementEstimator.estimateDisplacement(samples)
        assertEquals(1.0, est, 0.05, "estimated=$est")
    }

    @Test
    fun `等速区間を含む台形プロファイルでも変位が消えない`() {
        // 静止 → +1 m/s^2 で 0.5 秒（速度 0.5 m/s）→ 等速 1 秒 → -1 m/s^2 で 0.5 秒 → 静止
        // 真の変位は 0.125 + 0.5 + 0.125 = 0.75 m。
        // 等速区間を静止と誤判定すると区間が分断され、変位がほぼ 0 になってしまう。
        val samples = synthesize(
            listOf(0.5 to 0.0, 0.5 to 1.0, 1.0 to 0.0, 0.5 to -1.0, 0.5 to 0.0),
        )
        val est = VerticalDisplacementEstimator.estimateDisplacement(samples)
        assertEquals(0.75, est, 0.05, "estimated=$est")
    }

    @Test
    fun `速度誤差が残っても長い静止を挟んだ 2 回の移動は合算される`() {
        // 1回目の移動だけ加速度に +0.4 m/s^2 の誤差が乗り、静止中も未補正速度が
        // 0.4 m/s 残るケース。中央の静止（5秒）を等速移動と誤判定して区間を
        // つなげてしまうと、合計が約 3.2m に化ける。各 1m の移動として合算されること。
        val samples = synthesize(
            listOf(
                0.5 to 0.0, 0.5 to 4.4, 0.5 to -3.6, 5.0 to 0.0,
                0.5 to 4.0, 0.5 to -4.0, 0.5 to 0.0,
            ),
        )
        val est = VerticalDisplacementEstimator.estimateDisplacement(samples)
        assertEquals(2.0, est, 0.15, "estimated=$est")
    }

    @Test
    fun `下方向の移動は負になる`() {
        val samples = synthesize(
            listOf(0.5 to 0.0, 0.5 to -4.0, 0.5 to 4.0, 0.5 to 0.0),
        )
        val est = VerticalDisplacementEstimator.estimateDisplacement(samples)
        assertEquals(-1.0, est, 0.05, "estimated=$est")
    }

    @Test
    fun `小さなノイズが乗っても大きく崩れない`() {
        val base = synthesize(listOf(0.5 to 0.0, 0.5 to 4.0, 0.5 to -4.0, 0.5 to 0.0))
        val noisy = base.mapIndexed { i, s ->
            s.copy(aVertical = s.aVertical + 0.08 * sin(i * 1.7))
        }
        val est = VerticalDisplacementEstimator.estimateDisplacement(noisy)
        assertEquals(1.0, est, 0.15, "estimated=$est")
    }

    @Test
    fun `2 回に分けた移動も合算される`() {
        val one = listOf(0.5 to 0.0, 0.5 to 4.0, 0.5 to -4.0, 1.0 to 0.0)
        val samples = synthesize(one + one.drop(1))
        val est = VerticalDisplacementEstimator.estimateDisplacement(samples)
        assertEquals(2.0, est, 0.1, "estimated=$est")
    }

    @Test
    fun `isStill は静止区間で true になる`() {
        val est = VerticalDisplacementEstimator()
        var t = 0L
        repeat(50) { est.add(t, 0.0); t += 10 }
        assertTrue(est.isStill())
        est.add(t, 3.0)
        assertTrue(!est.isStill())
    }

    @Test
    fun `サンプル不足ではゼロ`() {
        assertEquals(0.0, VerticalDisplacementEstimator.estimateDisplacement(emptyList()), 0.0)
        assertEquals(
            0.0,
            VerticalDisplacementEstimator.estimateDisplacement(listOf(AccelSample(0, 1.0))),
            0.0,
        )
    }
}
