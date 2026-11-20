package io.github.yuushilabs.sokutei.core

import java.util.Locale
import kotlin.math.abs

/** 長さ（ミリメートル）を読みやすい単位に整形する。 */
object LengthFormat {

    fun format(mm: Double): String {
        val a = abs(mm)
        return when {
            a < 100.0 -> String.format(Locale.US, "%.1f mm", mm)
            a < 1000.0 -> String.format(Locale.US, "%.1f cm", mm / 10.0)
            else -> String.format(Locale.US, "%.2f m", mm / 1000.0)
        }
    }

    /** 符号付き（上下変位など）でもそのまま整形する。 */
    fun formatSigned(mm: Double): String = format(mm)
}
