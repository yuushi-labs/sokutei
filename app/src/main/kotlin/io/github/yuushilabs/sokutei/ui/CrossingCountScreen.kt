package io.github.yuushilabs.sokutei.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yuushilabs.sokutei.camera.CameraAnalysisPreview
import io.github.yuushilabs.sokutei.core.FrameCounter
import io.github.yuushilabs.sokutei.core.GrayImage
import io.github.yuushilabs.sokutei.core.LineCrossingCounter
import io.github.yuushilabs.sokutei.data.HistoryViewModel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 通過カウント。画面中央の水平線を物が横切るたびに、方向別に数える。
 * カメラを固定して使う前提（手持ちで動かすと誤検出する）。
 */
@Composable
fun CrossingCountScreen(history: HistoryViewModel) {
    CameraPermissionGate {
        CrossingCountContent(history)
    }
}

@Composable
private fun CrossingCountContent(history: HistoryViewModel) {
    var invert by remember { mutableStateOf(false) }
    var centroids by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var frameWidth by remember { mutableIntStateOf(0) }
    var frameHeight by remember { mutableIntStateOf(0) }
    var downCount by remember { mutableIntStateOf(0) }
    var upCount by remember { mutableIntStateOf(0) }
    var hasFrame by remember { mutableStateOf(false) }

    val counter = remember { LineCrossingCounter(lineY = 0.5) }
    val resetRequested = remember { AtomicBoolean(false) }
    val invertState = rememberUpdatedState(invert)

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("通過カウント", style = MaterialTheme.typography.headlineSmall)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            CameraAnalysisPreview(Modifier.fillMaxSize()) { gray, w, h, rotation ->
                val rotated = GrayImage.rotateClockwise(gray, w, h, rotation)
                val blobs = FrameCounter.findObjects(
                    rotated.gray, rotated.width, rotated.height,
                    FrameCounter.Options(invert = invertState.value),
                )
                if (resetRequested.getAndSet(false)) counter.reset()
                val points = blobs.map {
                    (it.centroidX / rotated.width) to (it.centroidY / rotated.height)
                }
                counter.update(points)
                downCount = counter.downCount
                upCount = counter.upCount
                centroids = points.map { (x, y) -> x.toFloat() to y.toFloat() }
                frameWidth = rotated.width
                frameHeight = rotated.height
                hasFrame = true
            }
            FrameOverlay(
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                centroids = centroids,
                lineY = 0.5f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text("合計: ${downCount + upCount} 個", style = MaterialTheme.typography.headlineMedium)
        Text("上→下: $downCount 個 / 下→上: $upCount 個")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("明るい物を数える（反転）")
            Switch(checked = invert, onCheckedChange = { invert = it })
        }
        Text(
            "※ カメラは固定してください。物が線の上で重なると数え間違えることがあります。",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = hasFrame,
                onClick = {
                    history.add(
                        "通過カウント",
                        "${downCount + upCount} 個",
                        "上→下 $downCount / 下→上 $upCount",
                    )
                },
            ) { Text("履歴に保存") }
            OutlinedButton(onClick = { resetRequested.set(true) }) { Text("リセット") }
        }
    }
}
