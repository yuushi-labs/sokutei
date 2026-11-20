package io.github.yuushilabs.sokutei.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.yuushilabs.sokutei.image.FitRect
import io.github.yuushilabs.sokutei.image.computeFitRect

/** カメラ権限が無ければリクエスト UI を出し、許可されたら content を表示する。 */
@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var requested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        requested = true
        granted = it
    }

    // 設定画面から戻ってきたときなど、再表示のたびに権限を再確認する
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (granted) {
        content()
    } else {
        // 永続的に拒否された場合はダイアログがもう出ないため、設定画面への導線を出す
        val activity = context as? Activity
        val permanentlyDenied = requested && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("カメラの許可が必要です", Modifier.padding(16.dp))
                if (permanentlyDenied) {
                    Button(onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    }) {
                        Text("設定を開く")
                    }
                } else {
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text("許可する")
                    }
                }
            }
        }
    }
}

/**
 * 画像をフィット表示し、その上に重ね描きとタップ取得を行う土台。
 * タップは画像相対座標（0..1）で返る。画像の外のタップは無視される。
 */
@Composable
fun FitImageCanvas(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    onTapRelative: ((Float, Float) -> Unit)? = null,
    drawOverlay: DrawScope.(FitRect) -> Unit = { },
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val fit = remember(size, bitmap) {
        computeFitRect(size.width.toFloat(), size.height.toFloat(), bitmap.width, bitmap.height)
    }
    Box(
        modifier
            .onSizeChanged { size = it }
            .then(
                if (onTapRelative != null) {
                    Modifier.pointerInput(fit) {
                        detectTapGestures { offset ->
                            fit.toRelative(offset.x, offset.y)?.let { (rx, ry) ->
                                onTapRelative(rx, ry)
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) { drawOverlay(fit) }
    }
}
