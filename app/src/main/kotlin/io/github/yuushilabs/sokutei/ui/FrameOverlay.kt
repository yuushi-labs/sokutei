package io.github.yuushilabs.sokutei.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.yuushilabs.sokutei.image.computeFitRect

/**
 * 解析フレームに合わせた重ね描き。プレビュー（フィット表示）と同じ規則で
 * フィット矩形を求め、検出マーカーと（必要なら）通過ラインを描く。
 *
 * @param centroids 画像相対座標（0..1）の重心一覧
 * @param lineY 通過ラインの相対位置。null なら描かない
 */
@Composable
fun FrameOverlay(
    frameWidth: Int,
    frameHeight: Int,
    centroids: List<Pair<Float, Float>>,
    lineY: Float?,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Canvas(modifier.onSizeChanged { size = it }) {
        if (frameWidth <= 0 || frameHeight <= 0 || size.width <= 0 || size.height <= 0) {
            return@Canvas
        }
        val fit = computeFitRect(
            size.width.toFloat(), size.height.toFloat(), frameWidth, frameHeight,
        )
        if (lineY != null) {
            val y = fit.toDisplayY(lineY)
            drawLine(
                color = Color(0xFFFFC107),
                start = Offset(fit.left, y),
                end = Offset(fit.left + fit.width, y),
                strokeWidth = 5f,
            )
        }
        for ((rx, ry) in centroids) {
            val c = Offset(fit.toDisplayX(rx), fit.toDisplayY(ry))
            // 半径は dp 基準にする（px 直書きだと高密度端末で豆粒になる）
            drawCircle(Color.Red, radius = 12.dp.toPx(), center = c, style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(Color.Red, radius = 2.5.dp.toPx(), center = c)
        }
    }
}
