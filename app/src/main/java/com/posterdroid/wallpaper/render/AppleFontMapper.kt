// тут все сделала ии, даже не смотрите

package com.posterdroid.wallpaper.render

import android.graphics.Typeface
import android.os.Build

/**
 * Maps Apple Core Text font families and styles (SF Pro Display, SF Pro Text, SF Pro Rounded,
 * New York, etc.) to Android system Typefaces, maintaining accurate font weight and posture.
 */
object AppleFontMapper {

    private val typefaceCache = mutableMapOf<String, Typeface>()

    fun resolveTypeface(fontName: String?): Typeface {
        if (fontName.isNullOrBlank()) {
            return Typeface.DEFAULT
        }
        return typefaceCache.getOrPut(fontName) {
            createTypeface(fontName)
        }
    }

    private fun createTypeface(fontName: String): Typeface {
        val lower = fontName.lowercase()
        val isItalic = lower.contains("italic") || lower.contains("oblique")
        val isRounded = lower.contains("rounded")
        val isSerif = lower.contains("serif") || lower.contains("newyork") || lower.contains("times") || lower.contains("georgia")
        val isMono = lower.contains("mono") || lower.contains("courier") || lower.contains("menlo")

        val family = when {
            isRounded -> "sans-serif-rounded"
            isSerif -> "serif"
            isMono -> "monospace"
            lower.contains("condensed") -> "sans-serif-condensed"
            else -> "sans-serif"
        }

        val isBold = lower.contains("bold") || lower.contains("heavy") || lower.contains("black") || lower.contains("semibold")
        val style = when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        val baseTypeface = try {
            Typeface.create(family, style)
        } catch (t: Throwable) {
            Typeface.DEFAULT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val weight = when {
                lower.contains("ultralight") || lower.contains("thin") -> 100
                lower.contains("extralight") -> 200
                lower.contains("light") -> 300
                lower.contains("regular") || lower.contains("normal") -> 400
                lower.contains("medium") -> 500
                lower.contains("semibold") || lower.contains("demibold") -> 600
                lower.contains("extrabold") -> 800
                lower.contains("black") || lower.contains("heavy") -> 900
                isBold -> 700
                else -> 400
            }
            return try {
                Typeface.create(baseTypeface, weight, isItalic)
            } catch (t: Throwable) {
                baseTypeface
            }
        }

        return baseTypeface
    }
}
