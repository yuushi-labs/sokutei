package io.github.yuushilabs.sokutei.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.yuushilabs.sokutei.camera.CameraPreview
import io.github.yuushilabs.sokutei.core.LengthFormat
import io.github.yuushilabs.sokutei.core.TiltMath
import io.github.yuushilabs.sokutei.data.HistoryViewModel
import io.github.yuushilabs.sokutei.sensor.MotionTracker
import kotlin.math.abs

@Composable
fun ScanScreen(history: HistoryViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tracker = remember { MotionTracker(context) }

    // 測定状態はタブを切り替えても失われないよう、この画面で保持する
    var moveMeasuring by remember { mutableStateOf(false) }
    var moveFinished by remember { mutableStateOf<Double?>(null) }
    var moveNotice by remember { mutableStateOf<String?>(null) }

    fun cancelMoveMeasurement(notice: String) {
        if (moveMeasuring) {
            tracker.cancelMeasurement()
            moveMeasuring = false
            moveFinished = null
            moveNotice = notice
        }
    }

    // センサー登録はライフサイクルに連動させる。バックグラウンドではセンサー
    // イベントが欠落し、欠落時間をまたいだ積分は不正な結果になるため、停止時に
    // 登録を解除し、進行中の測定も破棄する
    DisposableEffect(lifecycleOwner, tracker) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> tracker.startListening()
                Lifecycle.Event.ON_STOP -> {
                    cancelMoveMeasurement("バックグラウンドに移ったため、測定を中止しました")
                    tracker.stopListening()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tracker.stopListening()
        }
    }

    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("上下スキャン") },
            )
            Tab(
                selected = tab == 1,
                onClick = {
                    cancelMoveMeasurement("タブを切り替えたため、測定を中止しました")
                    tab = 1
                },
                text = { Text("傾けて測る") },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CameraPermissionGate {
                CameraPreview(Modifier.fillMaxSize())
                Crosshair()
            }
        }
        if (!tracker.hasSensors) {
            Text(
                "この端末では回転ベクトル／線形加速度センサが使えないため、上下スキャンは動作しません。",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
        when (tab) {
            0 -> MovePanel(
                tracker = tracker,
                history = history,
                measuring = moveMeasuring,
                finished = moveFinished,
                notice = moveNotice,
                onStart = {
                    moveFinished = null
                    moveNotice = null
                    tracker.startMeasurement()
                    moveMeasuring = true
                },
                onStop = {
                    moveFinished = tracker.stopMeasurement()
                    moveMeasuring = false
                },
                onSaved = { moveFinished = null },
            )
            else -> TiltPanel(tracker, history)
        }
    }
}

@Composable
private fun Crosshair() {
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2
        val len = 40f
        drawLine(Color.Red, Offset(cx - len, cy), Offset(cx + len, cy), strokeWidth = 3f)
        drawLine(Color.Red, Offset(cx, cy - len), Offset(cx, cy + len), strokeWidth = 3f)
        drawCircle(Color.Red, radius = 6f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    }
}

@Composable
private fun MovePanel(
    tracker: MotionTracker,
    history: HistoryViewModel,
    measuring: Boolean,
    finished: Double?,
    notice: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSaved: () -> Unit,
) {
    val displacement by tracker.displacementMeters.collectAsState()
    val still by tracker.still.collectAsState()

    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "高さ（上下の移動量）: ${LengthFormat.format(displacement * 1000)}",
            style = MaterialTheme.typography.headlineSmall,
        )
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            when {
                measuring && still -> "静止中（このまま保つと精度が上がります）"
                measuring -> "移動中…"
                else -> "手順: スマホを物の下端の高さで静止 → 開始 → ゆっくり上端まで持ち上げる → 静止して停止"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!measuring) {
                Button(onClick = onStart) { Text("測定開始") }
            } else {
                Button(onClick = onStop) { Text("停止") }
            }
            val result = finished
            if (result != null && abs(result) > 0.001) {
                OutlinedButton(onClick = {
                    history.add("上下スキャン", LengthFormat.format(result * 1000), "加速度二重積分（ZUPT＋ドリフト補正）")
                    onSaved()
                }) { Text("履歴に保存") }
            }
        }
        Text(
            "※ 加速度センサの二重積分なので誤差は大きめです（大きさは実機未検証のため分かりません）。速く動かすほど悪化します。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TiltPanel(tracker: MotionTracker, history: HistoryViewModel) {
    val elevation by tracker.elevationDeg.collectAsState()
    var heightCmText by remember { mutableStateOf("150") }
    var bottomDeg by remember { mutableStateOf<Double?>(null) }
    var topDeg by remember { mutableStateOf<Double?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val heightM = heightCmText.toDoubleOrNull()?.div(100.0)
    val result = remember(heightM, bottomDeg, topDeg) {
        val h = heightM
        val b = bottomDeg
        val t = topDeg
        if (h != null && b != null && t != null && b < 0 && t > b) {
            runCatching { TiltMath.heightMeters(h, b, t) }.getOrNull()
        } else {
            null
        }
    }

    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("現在の仰角: %.1f°".format(elevation), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = heightCmText,
            onValueChange = { heightCmText = it },
            label = { Text("カメラの高さ (cm) ※スマホを持つ高さ") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                bottomDeg = elevation
                error = if (elevation >= 0) "下端は見下ろす角度（マイナス）で記録してください" else null
            }) { Text("下端を記録 ${bottomDeg?.let { "%.1f°".format(it) } ?: ""}") }
            Button(onClick = { topDeg = elevation }) {
                Text("上端を記録 ${topDeg?.let { "%.1f°".format(it) } ?: ""}")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (result != null) {
            Text(
                "高さ: ${LengthFormat.format(result * 1000)}",
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedButton(onClick = {
                history.add("三角測量", LengthFormat.format(result * 1000), "カメラ高 ${heightCmText}cm")
            }) { Text("履歴に保存") }
        } else {
            Text(
                "手順: 物の正面に立つ → 照準を下端に合わせて「下端を記録」→ 上端に合わせて「上端を記録」",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
