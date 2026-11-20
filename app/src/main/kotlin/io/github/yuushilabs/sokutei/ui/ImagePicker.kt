package io.github.yuushilabs.sokutei.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.yuushilabs.sokutei.camera.CameraCapturePreview
import io.github.yuushilabs.sokutei.camera.CaptureController
import io.github.yuushilabs.sokutei.image.ImageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 「撮影する / ギャラリーから選ぶ」の共通 UI。読み込めたら onBitmap を呼ぶ。 */
@Composable
fun ImagePickerSection(onBitmap: (Bitmap) -> Unit) {
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val controller = remember { CaptureController() }
    val scope = rememberCoroutineScope()

    // Activity Result のコールバックはメインスレッドで呼ばれるため、
    // デコードは IO スレッドへ逃がしてから状態を更新する
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { ImageOps.loadBitmap(context, uri) }
                if (bmp != null) {
                    error = null
                    onBitmap(bmp)
                } else {
                    error = "画像を読み込めませんでした"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showCamera = !showCamera }) { Text(if (showCamera) "カメラを閉じる" else "撮影する") }
            OutlinedButton(onClick = {
                pickLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }) { Text("ギャラリーから選ぶ") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (showCamera) {
            CameraPermissionGate {
                Column {
                    Box(Modifier.fillMaxWidth().height(320.dp)) {
                        CameraCapturePreview(controller, Modifier.matchParentSize())
                    }
                    Button(
                        onClick = {
                            controller.capture(
                                context,
                                onSaved = { file ->
                                    scope.launch {
                                        val bmp = withContext(Dispatchers.IO) {
                                            ImageOps.loadBitmap(file)
                                        }
                                        if (bmp != null) {
                                            error = null
                                            showCamera = false
                                            onBitmap(bmp)
                                        } else {
                                            error = "撮影画像を読み込めませんでした"
                                        }
                                    }
                                },
                                onError = { error = it },
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("シャッター") }
                }
            }
        }
    }
}
