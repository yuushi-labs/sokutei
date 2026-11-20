package io.github.yuushilabs.sokutei.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

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
                controller.imageCapture = capture
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture,
                )
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
