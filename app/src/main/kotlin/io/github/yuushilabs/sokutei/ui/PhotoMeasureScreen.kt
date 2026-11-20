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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.yuushilabs.sokutei.core.LengthFormat
import io.github.yuushilabs.sokutei.core.PixelScale
import io.github.yuushilabs.sokutei.core.PxPoint
import io.github.yuushilabs.sokutei.core.ReferenceObjects
import io.github.yuushilabs.sokutei.data.HistoryViewModel

private enum class Phase { REFERENCE, MEASURE }

/**
 * 静止画測定: まず基準物（実寸が分かる物）の両端をタップし、
 * 次に測りたい物の点をタップすると実寸に換算する。
 * 点は画像相対座標（0..1）で保持するので、表示サイズに依存しない。
 */
@Composable
fun PhotoMeasureScreen(history: HistoryViewModel) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var phase by remember { mutableStateOf(Phase.REFERENCE) }
    var refPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var measurePoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var scale by remember { mutableStateOf<PixelScale?>(null) }
    var showScaleDialog by remember { mutableStateOf(false) }
    var refError by remember { mutableStateOf<String?>(null) }

    val bmp = bitmap
    val lengthMm = remember(scale, measurePoints, bmp) {
        val s = scale
        val b = bmp
        if (s != null && b != null && measurePoints.size >= 2) {
            s.polylineMm(measurePoints.map { it.toPx(b) })
        } else {
            null
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("静止画で測る", style = MaterialTheme.typography.headlineSmall)
        ImagePickerSection { loaded ->
            bitmap = loaded
            phase = Phase.REFERENCE
            refPoints = emptyList()
            measurePoints = emptyList()
            scale = null
            refError = null
            showScaleDialog = false
        }

        if (bmp != null) {
            Text(
                when {
                    phase == Phase.REFERENCE && refPoints.size < 2 ->
                        "手順1: 基準物の両端をタップしてください（あと ${2 - refPoints.size} 点）"
                    phase == Phase.MEASURE ->
                        "手順2: 測りたい物の点をタップしてください（2点以上で長さ、追加タップで折れ線）"
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            refError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            FitImageCanvas(
                bitmap = bmp,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                onTapRelative = { rx, ry ->
                    when (phase) {
                        Phase.REFERENCE -> {
                            refError = null
                            refPoints = refPoints + (rx to ry)
                            if (refPoints.size == 2) showScaleDialog = true
                        }
                        Phase.MEASURE -> measurePoints = measurePoints + (rx to ry)
                    }
                },
            ) { fit ->
                // 基準線（青）
                val refPx = refPoints.map { (rx, ry) -> Offset(fit.toDisplayX(rx), fit.toDisplayY(ry)) }
                for (i in 1 until refPx.size) {
                    drawLine(Color.Blue, refPx[i - 1], refPx[i], strokeWidth = 4f)
                }
                refPx.forEach { drawCircle(Color.Blue, radius = 10f, center = it) }
                // 測定線（赤）
                val measPx = measurePoints.map { (rx, ry) -> Offset(fit.toDisplayX(rx), fit.toDisplayY(ry)) }
                for (i in 1 until measPx.size) {
                    drawLine(Color.Red, measPx[i - 1], measPx[i], strokeWidth = 4f)
                }
                measPx.forEach { drawCircle(Color.Red, radius = 10f, center = it) }
            }

            if (lengthMm != null) {
                Text("長さ: ${LengthFormat.format(lengthMm)}", style = MaterialTheme.typography.headlineSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    phase = Phase.REFERENCE
                    refPoints = emptyList()
                    measurePoints = emptyList()
                    scale = null
                    refError = null
                }) { Text("基準からやり直し") }
                if (measurePoints.isNotEmpty()) {
                    OutlinedButton(onClick = { measurePoints = emptyList() }) { Text("測定点をクリア") }
                }
                if (lengthMm != null) {
                    Button(onClick = {
                        history.add("静止画測定", LengthFormat.format(lengthMm), "基準物スケール換算")
                    }) { Text("履歴に保存") }
                }
            }
            Text(
                "※ 基準物と測る物が同じ平面にある前提です。奥行きが違うと誤差になります。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showScaleDialog && bmp != null) {
        ScaleInputDialog(
            onDismiss = {
                showScaleDialog = false
                refPoints = emptyList() // やり直し
            },
            onConfirm = { realMm ->
                // 画像の差し替えなどで基準点が消えている場合は確定しない
                if (refPoints.size != 2) {
                    showScaleDialog = false
                    return@ScaleInputDialog
                }
                val a = refPoints[0].toPx(bmp)
                val b = refPoints[1].toPx(bmp)
                // 同じ位置を2回タップすると距離 0 になり PixelScale が作れないため、
                // ここで弾いて基準点の指定からやり直させる
                val distancePx = kotlin.math.hypot(b.x - a.x, b.y - a.y)
                if (distancePx <= 0.0) {
                    refError = "基準の2点が重なっています。離れた2点をタップしてください"
                    refPoints = emptyList()
                    showScaleDialog = false
                    return@ScaleInputDialog
                }
                scale = PixelScale.fromReference(a, b, realMm)
                phase = Phase.MEASURE
                showScaleDialog = false
            },
        )
    }
}

private fun Pair<Float, Float>.toPx(bitmap: Bitmap): PxPoint =
    PxPoint(first.toDouble() * bitmap.width, second.toDouble() * bitmap.height)

@Composable
private fun ScaleInputDialog(onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var customText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val presets = listOf(
        "クレジットカード長辺" to ReferenceObjects.CREDIT_CARD_MM,
        "1円玉" to ReferenceObjects.ONE_YEN_COIN_MM,
        "10円玉" to ReferenceObjects.TEN_YEN_COIN_MM,
        "100円玉" to ReferenceObjects.HUNDRED_YEN_COIN_MM,
        "A4短辺" to ReferenceObjects.A4_SHORT_SIDE_MM,
        "A4長辺" to ReferenceObjects.A4_LONG_SIDE_MM,
    )
    var selected by remember { mutableStateOf(presets[0].second) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("基準物の実寸") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("タップした2点の実際の長さを選ぶか、入力してください。")
                presets.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, mm) ->
                            FilterChip(
                                selected = selected == mm && customText.isBlank(),
                                onClick = { selected = mm; customText = "" },
                                label = { Text("$label (${mm}mm)") },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it; inputError = null },
                    label = { Text("カスタム (mm)") },
                    singleLine = true,
                    isError = inputError != null,
                )
                inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (customText.isBlank()) {
                    onConfirm(selected)
                } else {
                    // 入力があるのに解釈できない場合、プリセット値で黙って確定しない
                    val value = customText.toDoubleOrNull()
                    if (value != null && value.isFinite() && value > 0) {
                        onConfirm(value)
                    } else {
                        inputError = "数値（mm）を入力してください。例: 85.6"
                    }
                }
            }) { Text("決定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
