package io.github.yuushilabs.sokutei.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * 1フレーム分の表示状態。個数とマーカーは同じフレームの解析結果から作り、
 * 1つの状態としてまとめて差し替える（別フレーム同士が混ざって表示されないように）。
 */
private data class LiveSnapshot(
    val displayCount: Int = 0,
    val centroids: List<Pair<Float, Float>> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val hasFrame: Boolean = false,
    val oversizedExcluded: Int = 0,
)

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
    KeepScreenOn()
    var invert by remember { mutableStateOf(false) }
    var minAreaPercent by remember { mutableStateOf(0.05f) }
    var snapshot by remember { mutableStateOf(LiveSnapshot()) }

    val smoother = remember { CountSmoother() }
    val resetRequested = remember { AtomicBoolean(false) }
    val invertState = rememberUpdatedState(invert)
    val minAreaState = rememberUpdatedState(minAreaPercent)

    @Composable
    fun previewBox(modifier: Modifier) {
        Box(modifier) {
            CameraAnalysisPreview(Modifier.fillMaxSize()) { gray, w, h, rotation ->
                val rotated = GrayImage.rotateClockwise(gray, w, h, rotation)
                val result = FrameCounter.analyze(
                    rotated.gray, rotated.width, rotated.height,
                    FrameCounter.Options(
                        invert = invertState.value,
                        minAreaFraction = minAreaState.value / 100.0,
                    ),
                )
                if (resetRequested.getAndSet(false)) smoother.reset()
                snapshot = LiveSnapshot(
                    displayCount = smoother.add(result.blobs.size),
                    centroids = result.blobs.map {
                        (it.centroidX / rotated.width).toFloat() to
                            (it.centroidY / rotated.height).toFloat()
                    },
                    frameWidth = rotated.width,
                    frameHeight = rotated.height,
                    hasFrame = true,
                    oversizedExcluded = result.oversizedExcluded,
                )
            }
            FrameOverlay(
                frameWidth = snapshot.frameWidth,
                frameHeight = snapshot.frameHeight,
                centroids = snapshot.centroids,
                lineY = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Composable
    fun controls(modifier: Modifier) {
        Column(
            modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("個数: ${snapshot.displayCount} 個", style = MaterialTheme.typography.headlineMedium)
            if (snapshot.oversizedExcluded > 0) {
                Text(
                    "※ 大きすぎて数えていない領域があります（画面の25%超）。少し離れてください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
                    enabled = snapshot.hasFrame,
                    onClick = {
                        history.add("個数（ライブ）", "${snapshot.displayCount} 個", "その場で自動カウント")
                    },
                ) { Text("履歴に保存") }
                OutlinedButton(onClick = { resetRequested.set(true) }) { Text("表示をリセット") }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val landscape = maxWidth > maxHeight
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ライブで数える", style = MaterialTheme.typography.headlineSmall)
            if (landscape) {
                // 横向き: プレビューが画面を占有しないよう左右に並べる
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    previewBox(Modifier.fillMaxHeight().aspectRatio(4f / 3f))
                    controls(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                previewBox(Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                controls(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
