package io.github.yuushilabs.sokutei.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.util.concurrent.atomic.AtomicReference

/** 方向別カウントの組。保存時に合計と内訳が食い違わないよう、1つの状態として公開する。 */
private data class CrossingCounts(val down: Int, val up: Int) {
    val total: Int get() = down + up
}

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
    var counts by remember { mutableStateOf(CrossingCounts(0, 0)) }
    var hasFrame by remember { mutableStateOf(false) }

    val counter = remember { LineCrossingCounter(lineY = 0.5) }
    val resetRequested = remember { AtomicBoolean(false) }
    val invertState = rememberUpdatedState(invert)
    // 解析スレッド側で保持する「前回適用した反転設定」。変わっていたら追跡だけを
    // 破棄する（検出対象が入れ替わり、別物への対応付けで幻の横切りを数えるため）
    val appliedInvert = remember { AtomicReference(invert) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("通過カウント", style = MaterialTheme.typography.headlineSmall)
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
            CameraAnalysisPreview(Modifier.fillMaxSize()) { gray, w, h, rotation ->
                val rotated = GrayImage.rotateClockwise(gray, w, h, rotation)
                val inv = invertState.value
                val blobs = FrameCounter.findObjects(
                    rotated.gray, rotated.width, rotated.height,
                    FrameCounter.Options(invert = inv),
                )
                if (resetRequested.getAndSet(false)) counter.reset()
                if (appliedInvert.getAndSet(inv) != inv) counter.clearTracks()
                val points = blobs.map {
                    (it.centroidX / rotated.width) to (it.centroidY / rotated.height)
                }
                counter.update(points)
                counts = CrossingCounts(counter.downCount, counter.upCount)
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
        Text("合計: ${counts.total} 個", style = MaterialTheme.typography.headlineMedium)
        Text("上→下: ${counts.down} 個 / 下→上: ${counts.up} 個")
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
                    // 合計と内訳は同じスナップショットから作る
                    val snapshot = counts
                    history.add(
                        "通過カウント",
                        "${snapshot.total} 個",
                        "上→下 ${snapshot.down} / 下→上 ${snapshot.up}",
                    )
                },
            ) { Text("履歴に保存") }
            OutlinedButton(onClick = { resetRequested.set(true) }) { Text("リセット") }
        }
    }
}
