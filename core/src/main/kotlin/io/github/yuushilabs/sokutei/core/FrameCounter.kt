package io.github.yuushilabs.sokutei.core

/**
 * グレースケール画像から物を数える共通処理。
 * 大津二値化 → 連結成分ラベリング。写真・ライブ・通過の各モードが共有する。
 *
 * 前提は「背景と物のコントラストがはっきりしている」こと。
 * くっついた・重なった物は 1 個に数える（限界は docs/ACCURACY.md）。
 */
object FrameCounter {

    data class Options(
        /** true のとき明るいピクセルを前景とする。 */
        val invert: Boolean = false,
        /** これより面積割合が小さい成分はノイズとして除外する。 */
        val minAreaFraction: Double = 0.0005,
        /** これより面積割合が大きい成分は背景の巻き込みとして除外する。 */
        val maxAreaFraction: Double = 0.25,
    )

    /**
     * @return 検出した物の一覧。重心はピクセル座標（[Blob] の定義どおり）
     */
    fun findObjects(
        gray: IntArray,
        width: Int,
        height: Int,
        options: Options = Options(),
    ): List<Blob> {
        require(width > 0 && height > 0) { "width/height must be positive" }
        require(gray.size == width * height) { "gray size must equal width*height" }
        val threshold = Otsu.threshold(gray)
        val mask = Otsu.binarize(gray, threshold, options.invert)
        val total = width * height
        val minArea = (total * options.minAreaFraction).toInt().coerceAtLeast(2)
        val maxArea = (total * options.maxAreaFraction).toInt().coerceAtLeast(minArea)
        return BlobCounter.findBlobs(mask, width, height, minArea = minArea, maxArea = maxArea)
    }
}
