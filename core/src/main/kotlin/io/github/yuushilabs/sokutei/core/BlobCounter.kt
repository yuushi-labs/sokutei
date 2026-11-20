package io.github.yuushilabs.sokutei.core

/** 連結成分（かたまり）1個分の情報。 */
data class Blob(
    val area: Int,
    val centroidX: Double,
    val centroidY: Double,
)

/**
 * 二値マスクの連結成分を数える（個数測定用）。
 * 接続性は4近傍（上下左右）。斜めに触れているだけのものは別々に数える。
 */
object BlobCounter {

    fun findBlobs(
        mask: BooleanArray,
        width: Int,
        height: Int,
        minArea: Int = 1,
        maxArea: Int = Int.MAX_VALUE,
    ): List<Blob> {
        require(width > 0 && height > 0) { "width/height must be positive" }
        require(mask.size == width * height) { "mask size must equal width*height" }
        require(minArea >= 1) { "minArea must be >= 1" }

        val visited = BooleanArray(mask.size)
        val blobs = mutableListOf<Blob>()
        val stack = ArrayDeque<Int>()

        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            // 深さ優先で1成分をたどる
            var area = 0
            var sumX = 0.0
            var sumY = 0.0
            stack.addLast(start)
            visited[start] = true
            while (stack.isNotEmpty()) {
                val idx = stack.removeLast()
                val x = idx % width
                val y = idx / width
                area++
                sumX += x
                sumY += y
                // 4近傍
                if (x > 0) visit(mask, visited, stack, idx - 1)
                if (x < width - 1) visit(mask, visited, stack, idx + 1)
                if (y > 0) visit(mask, visited, stack, idx - width)
                if (y < height - 1) visit(mask, visited, stack, idx + width)
            }
            if (area in minArea..maxArea) {
                blobs.add(Blob(area, sumX / area, sumY / area))
            }
        }
        return blobs
    }

    private fun visit(mask: BooleanArray, visited: BooleanArray, stack: ArrayDeque<Int>, idx: Int) {
        if (mask[idx] && !visited[idx]) {
            visited[idx] = true
            stack.addLast(idx)
        }
    }

    fun count(
        mask: BooleanArray,
        width: Int,
        height: Int,
        minArea: Int = 1,
        maxArea: Int = Int.MAX_VALUE,
    ): Int = findBlobs(mask, width, height, minArea, maxArea).size
}
