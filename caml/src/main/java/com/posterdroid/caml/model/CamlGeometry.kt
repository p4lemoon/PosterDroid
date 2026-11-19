package com.posterdroid.caml.model

import kotlin.math.cos
import kotlin.math.sin

data class Point2f(val x: Float = 0f, val y: Float = 0f) {
    fun lerp(other: Point2f, fraction: Float): Point2f =
        Point2f(x + (other.x - x) * fraction, y + (other.y - y) * fraction)
}

data class Rect4f(val x: Float = 0f, val y: Float = 0f, val width: Float = 0f, val height: Float = 0f) {
    fun lerp(other: Rect4f, fraction: Float): Rect4f = Rect4f(
        x + (other.x - x) * fraction,
        y + (other.y - y) * fraction,
        width + (other.width - width) * fraction,
        height + (other.height - height) * fraction
    )
}

data class Color4f(val r: Float = 0f, val g: Float = 0f, val b: Float = 0f, val a: Float = 1f) {
    fun lerp(other: Color4f, fraction: Float): Color4f = Color4f(
        r + (other.r - r) * fraction,
        g + (other.g - g) * fraction,
        b + (other.b - b) * fraction,
        a + (other.a - a) * fraction
    )

    fun toArgbInt(): Int {
        val alpha = (a.coerceIn(0f, 1f) * 255).toInt()
        val red = (r.coerceIn(0f, 1f) * 255).toInt()
        val green = (g.coerceIn(0f, 1f) * 255).toInt()
        val blue = (b.coerceIn(0f, 1f) * 255).toInt()
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    companion object {
        val TRANSPARENT = Color4f(0f, 0f, 0f, 0f)
        val WHITE = Color4f(1f, 1f, 1f, 1f)
        val BLACK = Color4f(0f, 0f, 0f, 1f)

        fun parse(str: String): Color4f? {
            val trimmed = str.trim()
            if (trimmed.startsWith("#")) {
                val hex = trimmed.substring(1)
                return when (hex.length) {
                    6 -> {
                        val r = (hex.substring(0, 2).toIntOrNull(16) ?: return null) / 255f
                        val g = (hex.substring(2, 4).toIntOrNull(16) ?: return null) / 255f
                        val b = (hex.substring(4, 6).toIntOrNull(16) ?: return null) / 255f
                        Color4f(r, g, b, 1f)
                    }
                    8 -> {
                        val a = (hex.substring(0, 2).toIntOrNull(16) ?: return null) / 255f
                        val r = (hex.substring(2, 4).toIntOrNull(16) ?: return null) / 255f
                        val g = (hex.substring(4, 6).toIntOrNull(16) ?: return null) / 255f
                        val b = (hex.substring(6, 8).toIntOrNull(16) ?: return null) / 255f
                        Color4f(r, g, b, a)
                    }
                    else -> null
                }
            }
            val parts = trimmed.split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
            return when (parts.size) {
                1 -> Color4f(parts[0], parts[0], parts[0], 1f)
                2 -> Color4f(parts[0], parts[0], parts[0], parts[1])
                3 -> Color4f(parts[0], parts[1], parts[2], 1f)
                4 -> Color4f(parts[0], parts[1], parts[2], parts[3])
                else -> null
            }
        }
    }
}

data class Vector3f(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    companion object {
        val ZERO = Vector3f()
        fun parse(str: String): Vector3f? {
            val parts = str.trim().split("[\\s,]+".toRegex()).mapNotNull { it.toFloatOrNull() }
            return when {
                parts.size >= 3 -> Vector3f(parts[0], parts[1], parts[2])
                parts.size == 2 -> Vector3f(parts[0], parts[1], 0f)
                parts.size == 1 -> Vector3f(parts[0], 0f, 0f)
                else -> null
            }
        }
    }
}

typealias Transform3D = com.posterdroid.caml.math.CATransform3D
