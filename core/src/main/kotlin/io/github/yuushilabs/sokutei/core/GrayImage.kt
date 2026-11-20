package io.github.yuushilabs.sokutei.core

/** グレースケール配列の回転（カメラフレームを正立させるため）。 */
object GrayImage {

    data class Rotated(val gray: IntArray, val width: Int, val height: Int)

    /** 時計回りに [degrees]（0/90/180/270）回転する。それ以外の角度はそのまま返す。 */
    fun rotateClockwise(gray: IntArray, width: Int, height: Int, degrees: Int): Rotated {
        require(width > 0 && height > 0) { "width/height must be positive" }
        require(gray.size == width * height) { "gray size must equal width*height" }
        return when (((degrees % 360) + 360) % 360) {
            90 -> {
                val out = IntArray(gray.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[x * height + (height - 1 - y)] = gray[y * width + x]
                    }
                }
                Rotated(out, height, width)
            }
            180 -> {
                val out = IntArray(gray.size)
                for (i in gray.indices) out[gray.size - 1 - i] = gray[i]
                Rotated(out, width, height)
            }
            270 -> {
                val out = IntArray(gray.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        out[(width - 1 - x) * height + y] = gray[y * width + x]
                    }
                }
                Rotated(out, height, width)
            }
            else -> Rotated(gray, width, height)
        }
    }
}
