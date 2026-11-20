package io.github.yuushilabs.sokutei.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.yuushilabs.sokutei.core.BlobCounter
import io.github.yuushilabs.sokutei.core.Otsu
import io.github.yuushilabs.sokutei.data.HistoryViewModel
import io.github.yuushilabs.sokutei.image.ImageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 個数測定。
 * 自動: 縮小→グレースケール→大津二値化→連結成分ラベリング（core 実装）。
 * 手動: タップした点を数える。
 */
@Composable
fun CountScreen(history: HistoryViewModel) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var manualMode by remember { mutableStateOf(false) }
    var invert by remember { mutableStateOf(false) }
    var minAreaPercent by remember { mutableStateOf(0.05f) } // 画像全体に対する割合(%)
    var autoCentroids by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var manualPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var computing by remember { mutableStateOf(false) }
    // 自動計算の結果が「現在の画像と設定」に対応して確定済みかどうか。
    // 設定変更や画像の差し替えのたびに false にし、再計算が終わるまで履歴保存を止める
    var autoResultValid by remember { mutableStateOf(false) }

    val bmp = bitmap

    LaunchedEffect(bmp, invert, minAreaPercent, manualMode) {
        val source = bmp
        if (source == null || manualMode) {
            autoCentroids = emptyList()
            autoResultValid = false
            computing = false
            return@LaunchedEffect
        }
        computing = true
        val result = withContext(Dispatchers.Default) {
            val small = ImageOps.downscale(source, 640)
            val gray = ImageOps.toGrayscale(small)
            val threshold = Otsu.threshold(gray)
            val mask = Otsu.binarize(gray, threshold, invert)
            val total = small.width * small.height
            val minArea = (total * minAreaPercent / 100.0).toInt().coerceAtLeast(2)
            val blobs = BlobCounter.findBlobs(
                mask, small.width, small.height,
                minArea = minArea,
                maxArea = total / 4,
            )
            blobs.map { blob ->
                (blob.centroidX / small.width).toFloat() to (blob.centroidY / small.height).toFloat()
            }
        }
        autoCentroids = result
        autoResultValid = true
        computing = false
    }

    val count = if (manualMode) manualPoints.size else autoCentroids.size

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("個数を数える", style = MaterialTheme.typography.headlineSmall)
        ImagePickerSection { loaded ->
            bitmap = loaded
            manualPoints = emptyList()
            autoCentroids = emptyList()
            autoResultValid = false
        }

        if (bmp != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !manualMode,
                    onClick = { manualMode = false; autoResultValid = false },
                    label = { Text("自動で数える") },
                )
                FilterChip(
                    selected = manualMode,
                    onClick = { manualMode = true; autoResultValid = false },
                    label = { Text("タップで数える") },
                )
            }

            FitImageCanvas(
                bitmap = bmp,
                modifier = Modifier.fillMaxWidth(),
                onTapRelative = if (manualMode) {
                    { rx, ry -> manualPoints = manualPoints + (rx to ry) }
                } else {
                    null
                },
            ) { fit ->
                val points = if (manualMode) manualPoints else autoCentroids
                points.forEach { (rx, ry) ->
                    val c = Offset(fit.toDisplayX(rx), fit.toDisplayY(ry))
                    drawCircle(Color.Red, radius = 14f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                    drawCircle(Color.Red, radius = 3f, center = c)
                }
            }

            Text(
                if (computing) "数えています…" else "個数: $count 個",
                style = MaterialTheme.typography.headlineSmall,
            )

            if (!manualMode) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("明るい物を数える（反転）")
                    Switch(checked = invert, onCheckedChange = { invert = it; autoResultValid = false })
                }
                Text("小さいノイズを除外する下限: 全体の %.2f%%".format(minAreaPercent))
                Slider(
                    value = minAreaPercent,
                    onValueChange = { minAreaPercent = it; autoResultValid = false },
                    valueRange = 0.01f..1.0f,
                )
                Text(
                    "※ 背景とコントラストがはっきりしている写真ほど正確です。くっついた物は1個に数えます。",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { manualPoints = manualPoints.dropLast(1) }) { Text("1つ戻す") }
                    OutlinedButton(onClick = { manualPoints = emptyList() }) { Text("クリア") }
                }
            }

            Button(
                enabled = manualMode || autoResultValid,
                onClick = {
                    history.add(
                        if (manualMode) "個数（手動）" else "個数（自動）",
                        "$count 個",
                        if (manualMode) "タップカウント" else "大津二値化＋連結成分",
                    )
                },
            ) { Text("履歴に保存") }
        }
    }
}
