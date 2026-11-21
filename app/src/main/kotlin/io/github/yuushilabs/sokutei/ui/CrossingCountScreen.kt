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

/** 1フレーム分の表示状態。カウントとマーカーは同じフレームの結果をまとめて差し替える。 */
private data class CrossingSnapshot(
    val counts: CrossingCounts = CrossingCounts(0, 0),
    val centroids: List<Pair<Float, Float>> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val hasFrame: Boolean = false,
    val oversizedExcluded: Int = 0,
)

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
    KeepScreenOn()
    var invert by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf(CrossingSnapshot()) }
    var confirmReset by remember { mutableStateOf(false) }

    val counter = remember { LineCrossingCounter(lineY = 0.5) }
    val resetRequested = remember { AtomicBoolean(false) }
    val invertState = rememberUpdatedState(invert)
    // 解析スレッド側で保持する「前回適用した反転設定」。変わっていたら追跡だけを
    // 破棄する（検出対象が入れ替わり、別物への対応付けで幻の横切りを数えるため）
    val appliedInvert = remember { AtomicReference(invert) }

    @Composable
    fun previewBox(modifier: Modifier) {
        Box(modifier) {
            CameraAnalysisPreview(Modifier.fillMaxSize()) { gray, w, h, rotation ->
                val rotated = GrayImage.rotateClockwise(gray, w, h, rotation)
                val inv = invertState.value
                val result = FrameCounter.analyze(
                    rotated.gray, rotated.width, rotated.height,
                    FrameCounter.Options(invert = inv),
                )
                if (resetRequested.getAndSet(false)) counter.reset()
                if (appliedInvert.getAndSet(inv) != inv) counter.clearTracks()
                val points = result.blobs.map {
                    (it.centroidX / rotated.width) to (it.centroidY / rotated.height)
                }
                counter.update(points)
                snapshot = CrossingSnapshot(
                    counts = CrossingCounts(counter.downCount, counter.upCount),
                    centroids = points.map { (x, y) -> x.toFloat() to y.toFloat() },
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
                lineY = 0.5f,
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
            Text("合計: ${snapshot.counts.total} 個", style = MaterialTheme.typography.headlineMedium)
            Text("上→下: ${snapshot.counts.down} 個 / 下→上: ${snapshot.counts.up} 個")
            if (snapshot.oversizedExcluded > 0) {
                Text(
                    "※ 大きすぎて数えていない領域があります（画面の25%超）。",
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
            Text(
                "※ カメラは固定してください。物が線の上で重なると数え間違えることがあります。速く横切る物（1フレームで画面の約1/4超の移動）は数え落とすことがあります。",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = snapshot.hasFrame,
                    onClick = {
                        // 合計と内訳は同じスナップショットから作る
                        val s = snapshot.counts
                        history.add(
                            "通過カウント",
                            "${s.total} 個",
                            "上→下 ${s.down} / 下→上 ${s.up}",
                        )
                    },
                ) { Text("履歴に保存") }
                OutlinedButton(onClick = { confirmReset = true }) { Text("リセット") }
            }
        }
    }

    ConfirmDialog(
        show = confirmReset,
        title = "通過カウントをリセット",
        message = "数えた回数を 0 に戻します。よろしいですか？",
        confirmLabel = "リセットする",
        onConfirm = {
            confirmReset = false
            resetRequested.set(true)
        },
        onDismiss = { confirmReset = false },
    )

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val landscape = maxWidth > maxHeight
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("通過カウント", style = MaterialTheme.typography.headlineSmall)
            if (landscape) {
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
