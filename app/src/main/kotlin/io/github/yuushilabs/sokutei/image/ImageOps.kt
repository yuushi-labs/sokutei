package io.github.yuushilabs.sokutei.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    /** Uri から最大辺 maxDim 以下に縮小して読み込む。 */
    fun loadBitmap(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    /** File から最大辺 maxDim 以下に縮小して読み込む。 */
    fun loadBitmap(file: File, maxDim: Int = 2048): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
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
