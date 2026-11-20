package io.github.yuushilabs.sokutei.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** 画像がコンテナ内に等倍フィットして表示される矩形（表示座標系）。 */
data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    /** 画像相対座標（0..1）→ 表示座標。 */
    fun toDisplayX(relX: Float): Float = left + relX * width
    fun toDisplayY(relY: Float): Float = top + relY * height

    /** 表示座標 → 画像相対座標。画像の外なら null。 */
    fun toRelative(x: Float, y: Float): Pair<Float, Float>? {
        if (width <= 0 || height <= 0) return null
        val rx = (x - left) / width
        val ry = (y - top) / height
        return if (rx in 0f..1f && ry in 0f..1f) rx to ry else null
    }
}

/** ContentScale.Fit と同じ規則でフィット矩形を求める。 */
fun computeFitRect(containerW: Float, containerH: Float, imageW: Int, imageH: Int): FitRect {
    if (containerW <= 0 || containerH <= 0 || imageW <= 0 || imageH <= 0) {
        return FitRect(0f, 0f, 0f, 0f)
    }
    val scale = minOf(containerW / imageW, containerH / imageH)
    val w = imageW * scale
    val h = imageH * scale
    return FitRect((containerW - w) / 2f, (containerH - h) / 2f, w, h)
}

object ImageOps {

    /**
     * Uri から最大辺 maxDim 以下に縮小して読み込む。
     * 選択後に元画像が消えている場合など、読み込みに失敗したら例外ではなく null を返す。
     * EXIF の方向情報は適用してから返す（BitmapFactory は自動適用しない）。
     */
    fun loadBitmap(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? = runCatching {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        applyOrientation(decoded, orientation)
    }.getOrNull()

    /** File から最大辺 maxDim 以下に縮小して読み込む。失敗時は null。EXIF の方向を適用する。 */
    fun loadBitmap(file: File, maxDim: Int = 2048): Bitmap? = runCatching {
        val orientation = ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        applyOrientation(decoded, orientation)
    }.getOrNull()

    /** EXIF の方向（回転・反転）をビットマップへ適用する。通常方向なら元のまま返す。 */
    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun sampleSize(w: Int, h: Int, maxDim: Int): Int {
        var size = 1
        val longest = max(w, h)
        while (longest / size > maxDim) size *= 2
        return size
    }

    /** 最大辺 maxDim 以下になるよう縮小したコピーを返す（元が小さければそのまま）。 */
    fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** 輝度（0..255）の配列に変換する。長さは width*height。 */
    fun toGrayscale(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255)
        }
        return gray
    }
}
