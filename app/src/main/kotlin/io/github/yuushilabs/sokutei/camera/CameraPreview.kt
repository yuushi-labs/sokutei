package io.github.yuushilabs.sokutei.camera

import android.content.Context
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
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

/**
 * フレームの輝度（Y平面）を最大幅 [MAX_ANALYSIS_WIDTH] に間引いて取り出す抽出器。
 *
 * 解析スレッドは単一なので、バッファを使い回して毎フレームの確保を避ける。
 * 返す配列も使い回しのため、受け取った側はコールバック内で同期的に消費すること
 * （現在の呼び出し側は回転→検出までその場で終える）。
 * 処理が追いつかない場合は [MIN_FRAME_INTERVAL_MS] 未満の間隔のフレームを捨てる。
 */
private class GrayExtractor {
    private var planeBytes: ByteArray? = null
    private var outPixels: IntArray? = null
    private var lastProcessedAtMs = 0L

    /** 間隔が短すぎるフレームでは null を返す（そのフレームは解析しない）。 */
    fun extract(image: ImageProxy): Triple<IntArray, Int, Int>? {
        val nowMs = System.nanoTime() / 1_000_000
        if (nowMs - lastProcessedAtMs < MIN_FRAME_INTERVAL_MS) return null
        lastProcessedAtMs = nowMs

        val plane = image.planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val crop = image.cropRect
        val srcX = crop.left.coerceIn(0, image.width - 1)
        val srcY = crop.top.coerceIn(0, image.height - 1)
        val srcW = crop.width().coerceIn(1, image.width - srcX)
        val srcH = crop.height().coerceIn(1, image.height - srcY)
        val step = max(1, ceil(srcW.toDouble() / MAX_ANALYSIS_WIDTH).toInt())
        val outW = (srcW + step - 1) / step
        val outH = (srcH + step - 1) / step

        val remaining = plane.buffer.remaining()
        val bytes = planeBytes?.takeIf { it.size >= remaining }
            ?: ByteArray(remaining).also { planeBytes = it }
        plane.buffer.duplicate().get(bytes, 0, remaining)

        val out = outPixels?.takeIf { it.size == outW * outH }
            ?: IntArray(outW * outH).also { outPixels = it }
        for (oy in 0 until outH) {
            val sy = srcY + oy * step
            for (ox in 0 until outW) {
                val sx = srcX + ox * step
                out[oy * outW + ox] = bytes[sy * rowStride + sx * pixelStride].toInt() and 0xFF
            }
        }
        return Triple(out, outW, outH)
    }

    companion object {
        private const val MAX_ANALYSIS_WIDTH = 320
        private const val MIN_FRAME_INTERVAL_MS = 50L
    }
}

/**
 * 解析付きプレビュー。フレームの輝度と寸法、表示に合わせて正立させるための
 * 回転角を [onFrame] へ渡す（解析専用スレッドで呼ばれる）。
 * プレビューはフィット表示にし、呼び出し側が重心マーカーを正確に重ねられるようにする。
 *
 * プレビューと解析は ViewPort を共有して画角を一致させる。ViewPort は
 * PreviewView がレイアウトされるまで取得できない（初回コンポジション直後は
 * null）ため、レイアウト完了・サイズ変更のたびに確認し、取得できたら
 * 共有バインドへ切り替える。共有しないまま固定すると、表示範囲の外の物を
 * 数えたりマーカーがずれたりする。
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
        var layoutListener: View.OnLayoutChangeListener? = null
        if (provider != null) {
            val extractor = GrayExtractor()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                image.use {
                    val extracted = extractor.extract(it) ?: return@use
                    val (gray, w, h) = extracted
                    frameHandler(gray, w, h, it.imageInfo.rotationDegrees)
                }
            }
            var boundWithViewPort = false
            var boundWidth = -1
            var boundHeight = -1
            fun bind() {
                runCatching {
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    provider.unbindAll()
                    // プレビューと解析で ViewPort を共有し、画角（切り出し範囲）を一致させる
                    val viewPort = previewView.viewPort
                    if (viewPort != null) {
                        val group = UseCaseGroup.Builder()
                            .addUseCase(preview)
                            .addUseCase(analysis)
                            .setViewPort(viewPort)
                            .build()
                        provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group,
                        )
                        boundWithViewPort = true
                    } else {
                        provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                        )
                        boundWithViewPort = false
                    }
                    boundWidth = previewView.width
                    boundHeight = previewView.height
                }.onFailure { error = "カメラを開始できませんでした" }
            }
            bind()
            // レイアウト前は ViewPort が null になりやすい。レイアウト完了や
            // 回転によるサイズ変更で、共有バインドへの切り替えと再計算を行う
            val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val sizeChanged = previewView.width != boundWidth ||
                    previewView.height != boundHeight
                val viewPortReady = !boundWithViewPort && previewView.viewPort != null
                if (sizeChanged || viewPortReady) bind()
            }
            layoutListener = listener
            previewView.addOnLayoutChangeListener(listener)
        }
        onDispose {
            layoutListener?.let { previewView.removeOnLayoutChangeListener(it) }
            executor.shutdown()
        }
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
