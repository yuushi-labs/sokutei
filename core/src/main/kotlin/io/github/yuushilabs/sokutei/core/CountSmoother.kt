package io.github.yuushilabs.sokutei.core

/**
 * ライブ表示用に個数の時系列を平滑化する（直近フレームの中央値）。
 * フレームごとの検出のちらつきを抑えるためのもので、正確さが増すわけではない。
 * 代償として、実際の個数の変化への追従が最大で数フレーム遅れる。
 */
class CountSmoother(private val windowSize: Int = 7) {

    init {
        require(windowSize >= 1) { "windowSize must be >= 1" }
    }

    private val values = ArrayDeque<Int>()

    /** 新しい観測値を追加し、平滑化した現在値を返す。 */
    fun add(value: Int): Int {
        values.addLast(value)
        while (values.size > windowSize) values.removeFirst()
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    fun reset() {
        values.clear()
    }
}
