package io.github.yuushilabs.sokutei.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max

/**
 * CameraX 初期化の共通処理。
 *
 * - 破棄済みの effect から届いた listener では、状態更新もバインドもしない
 * - 初期化・バインドの失敗は例外で落とさず、エラーとして呼び出し側へ返す
 * - 破棄時は未完了の future を待たず、取得済みの provider だけを解放する
 */
@Composable
private fun rememberCameraProvider(
    attempt: Int,
    onError: (String) -> Unit,
): ProcessCameraProvider? {
    val context = LocalContext.current
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(attempt) {
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            runCatching { future.get() }
                .onSuccess { provider = it }
                .onFailure { onError("カメラを初期化できませんでした") }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
            provider = null
        }
    }
    return provider
}

@Composable
private fun CameraErrorRetry(error: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error)
            Button(onClick = onRetry) { Text("再試行") }
        }
    }
}

/** CameraX のプレビューだけを表示する。 */
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val provider = rememberCameraProvider(attempt) { error = it }
    DisposableEffect(provider, lifecycleOwner) {
        if (provider != null) {
            runCatching {
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            }.onFailure { error = "カメラを開始できませんでした" }
        }
        onDispose { }
    }
    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
        error?.let { CameraErrorRetry(it, { error = null; attempt++ }, Modifier.matchParentSize()) }
    }
}

/** フレームの輝度（Y平面）を最大幅 [MAX_ANALYSIS_WIDTH] に間引いて取り出す。 */
private fun extractGray(image: ImageProxy): Triple<IntArray, Int, Int> {
    val plane = image.planes[0]
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val srcW = image.width
    val srcH = image.height
    val step = max(1, ceil(srcW.toDouble() / MAX_ANALYSIS_WIDTH).toInt())
    val outW = (srcW + step - 1) / step
    val outH = (srcH + step - 1) / step
    val bytes = ByteArray(plane.buffer.remaining())
    plane.buffer.get(bytes)
    val out = IntArray(outW * outH)
    for (oy in 0 until outH) {
        val sy = oy * step
        for (ox in 0 until outW) {
            val sx = ox * step
            out[oy * outW + ox] = bytes[sy * rowStride + sx * pixelStride].toInt() and 0xFF
        }
    }
    return Triple(out, outW, outH)
}

private const val MAX_ANALYSIS_WIDTH = 320

/**
 * 解析付きプレビュー。フレームの輝度と寸法、表示に合わせて正立させるための
 * 回転角を [onFrame] へ渡す（解析専用スレッドで呼ばれる）。
 * プレビューはフィット表示にし、呼び出し側が重心マーカーを正確に重ねられるようにする。
 */
@Composable
fun CameraAnalysisPreview(
    modifier: Modifier = Modifier,
    onFrame: (gray: IntArray, width: Int, height: Int, rotationDegrees: Int) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val provider = rememberCameraProvider(attempt) { error = it }
    val frameHandler by rememberUpdatedState(onFrame)
    DisposableEffect(provider, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        if (provider != null) {
            runCatching {
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    image.use {
                        val (gray, w, h) = extractGray(it)
                        frameHandler(gray, w, h, it.imageInfo.rotationDegrees)
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                )
            }.onFailure { error = "カメラを開始できませんでした" }
        }
        onDispose { executor.shutdown() }
    }
    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
        error?.let { CameraErrorRetry(it, { error = null; attempt++ }, Modifier.matchParentSize()) }
    }
}

/** 撮影機能付きプレビュー。シャッターは呼び出し側が [controller] 経由で切る。 */
class CaptureController {
    var imageCapture: ImageCapture? = null

    fun capture(context: Context, onSaved: (File) -> Unit, onError: (String) -> Unit) {
        val capture = imageCapture ?: run {
            onError("カメラの準備ができていません")
            return
        }
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSaved(file)
                override fun onError(exception: ImageCaptureException) =
                    onError(exception.message ?: "撮影に失敗しました")
            },
        )
    }
}

@Composable
fun CameraCapturePreview(controller: CaptureController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val provider = rememberCameraProvider(attempt) { error = it }
    DisposableEffect(provider, lifecycleOwner) {
        if (provider != null) {
            runCatching {
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder().build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture,
                )
                // バインドが成功した場合だけ撮影可能にする（失敗時に未準備の
                // ImageCapture が残ってシャッターで誤動作しないように）
                controller.imageCapture = capture
            }.onFailure { error = "カメラを開始できませんでした" }
        }
        onDispose {
            controller.imageCapture = null
        }
    }
    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
        error?.let { CameraErrorRetry(it, { error = null; attempt++ }, Modifier.matchParentSize()) }
    }
}
