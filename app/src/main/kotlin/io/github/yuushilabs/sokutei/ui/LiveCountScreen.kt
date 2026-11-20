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
import androidx.compose.material3.Slider
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
import io.github.yuushilabs.sokutei.core.CountSmoother
import io.github.yuushilabs.sokutei.core.FrameCounter
import io.github.yuushilabs.sokutei.core.GrayImage
import io.github.yuushilabs.sokutei.data.HistoryViewModel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ライブで数える。カメラをかざすと写っている物をその場で数える。
 * 検出は解析スレッドで行い、表示は直近フレームの中央値で平滑化する。
 */
@Composable
fun LiveCountScreen(history: HistoryViewModel) {
    CameraPermissionGate {
        LiveCountContent(history)
    }
}

@Composable
private fun LiveCountContent(history: HistoryViewModel) {
    var invert by remember { mutableStateOf(false) }
    var minAreaPercent by remember { mutableStateOf(0.05f) }
    var centroids by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var frameWidth by remember { mutableIntStateOf(0) }
    var frameHeight by remember { mutableIntStateOf(0) }
    var displayCount by remember { mutableIntStateOf(0) }
    var hasFrame by remember { mutableStateOf(false) }

    val smoother = remember { CountSmoother() }
    val resetRequested = remember { AtomicBoolean(false) }
    val invertState = rememberUpdatedState(invert)
    val minAreaState = rememberUpdatedState(minAreaPercent)

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ライブで数える", style = MaterialTheme.typography.headlineSmall)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            CameraAnalysisPreview(Modifier.fillMaxSize()) { gray, w, h, rotation ->
                val rotated = GrayImage.rotateClockwise(gray, w, h, rotation)
                val blobs = FrameCounter.findObjects(
                    rotated.gray, rotated.width, rotated.height,
                    FrameCounter.Options(
                        invert = invertState.value,
                        minAreaFraction = minAreaState.value / 100.0,
                    ),
                )
                if (resetRequested.getAndSet(false)) smoother.reset()
                displayCount = smoother.add(blobs.size)
                centroids = blobs.map {
                    (it.centroidX / rotated.width).toFloat() to
                        (it.centroidY / rotated.height).toFloat()
                }
                frameWidth = rotated.width
                frameHeight = rotated.height
                hasFrame = true
            }
            FrameOverlay(
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                centroids = centroids,
                lineY = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text("個数: $displayCount 個", style = MaterialTheme.typography.headlineMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("明るい物を数える（反転）")
            Switch(checked = invert, onCheckedChange = { invert = it })
        }
        Text("小さいノイズを除外する下限: 全体の %.2f%%".format(minAreaPercent))
        Slider(
            value = minAreaPercent,
            onValueChange = { minAreaPercent = it },
            valueRange = 0.01f..1.0f,
        )
        Text(
            "※ 背景とコントラストがはっきりしている物ほど正確です。くっついた物は1個に数えます。表示は数フレーム遅れて追従します。",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = hasFrame,
                onClick = {
                    history.add("個数（ライブ）", "$displayCount 個", "その場で自動カウント")
                },
            ) { Text("履歴に保存") }
            OutlinedButton(onClick = { resetRequested.set(true) }) { Text("表示をリセット") }
        }
    }
}
