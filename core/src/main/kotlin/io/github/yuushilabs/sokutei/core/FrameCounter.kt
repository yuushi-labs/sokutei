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
     * 解析結果。数えた物に加えて、面積で除外した成分の件数も返す。
     * 大きすぎて除外した物は「0個」とだけ表示すると利用者が気づけないため、
     * 呼び出し側が警告を出せるよう件数を分けて持つ。
     */
    data class Result(
        val blobs: List<Blob>,
        /** 面積上限を超えて除外した成分の数（大きすぎる物・背景の巻き込み）。 */
        val oversizedExcluded: Int,
        /** 面積下限に満たず除外した成分の数（ノイズ）。 */
        val undersizedExcluded: Int,
    )

    fun analyze(
        gray: IntArray,
        width: Int,
        height: Int,
        options: Options = Options(),
    ): Result {
        require(width > 0 && height > 0) { "width/height must be positive" }
        require(gray.size == width * height) { "gray size must equal width*height" }
        val threshold = Otsu.threshold(gray)
        val mask = Otsu.binarize(gray, threshold, options.invert)
        val total = width * height
        val minArea = (total * options.minAreaFraction).toInt().coerceAtLeast(2)
        val maxArea = (total * options.maxAreaFraction).toInt().coerceAtLeast(minArea)
        val all = BlobCounter.findAllBlobs(mask, width, height)
        return Result(
            blobs = all.filter { it.area in minArea..maxArea },
            oversizedExcluded = all.count { it.area > maxArea },
            undersizedExcluded = all.count { it.area < minArea },
        )
    }

    /**
     * @return 検出した物の一覧。重心はピクセル座標（[Blob] の定義どおり）
     */
    fun findObjects(
        gray: IntArray,
        width: Int,
        height: Int,
        options: Options = Options(),
    ): List<Blob> = analyze(gray, width, height, options).blobs
}
