package io.github.yuushilabs.sokutei.core

/**
 * 大津の方法（二値化の閾値を自動決定する）。
 * グレースケール値のヒストグラムから、クラス間分散が最大になる閾値を返す。
 */
object Otsu {

    /**
     * @param gray 各ピクセルの輝度（0..255）
     * @return 閾値 t。「輝度 <= t」を前景とみなす想定
     */
    fun threshold(gray: IntArray): Int {
        require(gray.isNotEmpty()) { "gray must not be empty" }
        val hist = LongArray(256)
        for (v in gray) {
            require(v in 0..255) { "gray values must be in 0..255" }
            hist[v]++
        }
        val total = gray.size.toDouble()
        var sumAll = 0.0
        for (i in 0..255) sumAll += i * hist[i]

        var sumBg = 0.0
        var weightBg = 0L
        var bestVariance = -1.0
        var bestThreshold = 0
        for (t in 0..255) {
            weightBg += hist[t]
            if (weightBg == 0L) continue
            val weightFg = gray.size - weightBg
            if (weightFg == 0L) break
            sumBg += t * hist[t]
            val meanBg = sumBg / weightBg
            val meanFg = (sumAll - sumBg) / weightFg
            val diff = meanBg - meanFg
            val between = weightBg.toDouble() * weightFg.toDouble() * diff * diff
            if (between > bestVariance) {
                bestVariance = between
                bestThreshold = t
            }
        }
        return bestThreshold
    }

    /**
     * 二値化して前景マスクを作る。
     * @param invert false のとき「暗いピクセル（<= threshold）」を前景とする
     */
    fun binarize(gray: IntArray, threshold: Int, invert: Boolean = false): BooleanArray {
        val out = BooleanArray(gray.size)
        for (i in gray.indices) {
            val dark = gray[i] <= threshold
            out[i] = if (invert) !dark else dark
        }
        return out
    }
}
