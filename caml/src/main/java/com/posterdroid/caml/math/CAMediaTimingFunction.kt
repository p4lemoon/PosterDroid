package com.posterdroid.caml.math

import kotlin.math.abs

/**
 * Unit cubic bezier timing function matching Apple CAMediaTimingFunction.
 *
 * Curve starts at (0, 0) and ends at (1, 1), with control points (c1x, c1y) and (c2x, c2y).
 */
class CAMediaTimingFunction(
    val c1x: Float,
    val c1y: Float,
    val c2x: Float,
    val c2y: Float
) {

    /**
     * Given an input fraction x in [0, 1], returns the interpolated y value in [0, 1].
     */
    fun solve(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f

        // Fast path for linear
        if (c1x == c1y && c2x == c2y && c1x == 0f && c2x == 1f) {
            return x
        }

        // Solve for t where sampleX(t) = x using Newton's method with bisection fallback
        val t = solveCurveX(x)
        return sampleY(t)
    }

    private fun sampleX(t: Float): Float {
        // ((1 - 3*c2x + 3*c1x)*t + (3*c2x - 6*c1x))*t + 3*c1x)*t
        return ((A(c1x, c2x) * t + B(c1x, c2x)) * t + C(c1x)) * t
    }

    private fun sampleY(t: Float): Float {
        return ((A(c1y, c2y) * t + B(c1y, c2y)) * t + C(c1y)) * t
    }

    private fun sampleDerivativeX(t: Float): Float {
        return (3f * A(c1x, c2x) * t + 2f * B(c1x, c2x)) * t + C(c1x)
    }

    private fun A(aA1: Float, aA2: Float) = 1.0f - 3.0f * aA2 + 3.0f * aA1
    private fun B(aA1: Float, aA2: Float) = 3.0f * aA2 - 6.0f * aA1
    private fun C(aA1: Float) = 3.0f * aA1

    private fun solveCurveX(x: Float): Float {
        // Newton-Raphson iteration
        var t = x
        for (i in 0 until 8) {
            val currentX = sampleX(t) - x
            if (abs(currentX) < 1e-6f) {
                return t
            }
            val dX = sampleDerivativeX(t)
            if (abs(dX) < 1e-6f) {
                break
            }
            t -= currentX / dX
        }

        // Fallback: bisection search with maximum iteration guard
        var t0 = 0.0f
        var t1 = 1.0f
        t = x

        var iter = 0
        while (t0 < t1 && ++iter < 24) {
            val currentX = sampleX(t)
            if (abs(currentX - x) < 1e-6f) {
                return t
            }
            if (x > currentX) {
                t0 = t
            } else {
                t1 = t
            }
            val nextT = (t1 - t0) * 0.5f + t0
            if (nextT == t) break
            t = nextT
        }

        return t
    }

    companion object {
        val LINEAR = CAMediaTimingFunction(0.0f, 0.0f, 1.0f, 1.0f)
        val EASE_IN = CAMediaTimingFunction(0.42f, 0.0f, 1.0f, 1.0f)
        val EASE_OUT = CAMediaTimingFunction(0.0f, 0.0f, 0.58f, 1.0f)
        val EASE_IN_EASE_OUT = CAMediaTimingFunction(0.42f, 0.0f, 0.58f, 1.0f)
        val DEFAULT = CAMediaTimingFunction(0.25f, 0.1f, 0.25f, 1.0f)

        fun fromString(str: String?): CAMediaTimingFunction {
            if (str.isNullOrBlank()) return DEFAULT

            val trimmed = str.trim()
            when (trimmed.lowercase()) {
                "linear", "kcamediatimingfunctionlinear" -> return LINEAR
                "easein", "kcamediatimingfunctioneasein" -> return EASE_IN
                "easeout", "kcamediatimingfunctioneaseout" -> return EASE_OUT
                "easeineaseout", "kcamediatimingfunctioneaseineaseout" -> return EASE_IN_EASE_OUT
                "default", "kcamediatimingfunctiondefault" -> return DEFAULT
            }

            // Check if string is 4 control point coordinates: "c1x c1y c2x c2y"
            val parts = trimmed.split("[\\s,]+".toRegex()).mapNotNull { it.toFloatOrNull() }
            if (parts.size == 4) {
                return CAMediaTimingFunction(
                    c1x = parts[0].coerceIn(0f, 1f),
                    c1y = parts[1],
                    c2x = parts[2].coerceIn(0f, 1f),
                    c2y = parts[3]
                )
            }

            return DEFAULT
        }
    }
}
