package com.posterdroid.caml.engine

import com.posterdroid.caml.math.CAMediaTimingFunction
import com.posterdroid.caml.model.Color4f
import com.posterdroid.caml.model.Point2f
import com.posterdroid.caml.model.Rect4f
import kotlin.math.*

/**
 * Pure function evaluator for Apple Core Animation keyframe animations.
 * Supports calculationMode: "linear", "discrete", "paced", "cubic", "cubicPaced",
 * and segment cubic bezier timing functions.
 */
object CAKeyframeEvaluator {

    /**
     * Evaluates a keyframe sequence at normalized progress [0.0, 1.0].
     */
    fun evaluateValue(
        values: List<Any>,
        keyTimes: List<Float>,
        timingFunctions: List<CAMediaTimingFunction>,
        progress: Float,
        calculationMode: String = "linear"
    ): Any? {
        if (values.isEmpty()) return null
        if (values.size == 1) return values[0]

        val clampedProgress = progress.coerceIn(0f, 1f)
        val mode = calculationMode.lowercase()

        // 1. Resolve keyTimes
        val effectiveKeyTimes = if (mode == "paced" || mode == "cubicpaced" || keyTimes.size != values.size || keyTimes.size < 2) {
            computePacedKeyTimes(values)
        } else {
            keyTimes
        }

        // 2. Find active segment [idx0, idx1]
        val segmentCount = values.size - 1
        var foundIdx = 0
        for (i in 0 until effectiveKeyTimes.size - 1) {
            if (clampedProgress >= effectiveKeyTimes[i]) {
                foundIdx = i
            }
        }
        val idx0 = foundIdx.coerceIn(0, segmentCount - 1)
        val idx1 = idx0 + 1

        val t0 = effectiveKeyTimes[idx0]
        val t1 = effectiveKeyTimes[idx1]
        val localProgress = if (t1 > t0) {
            ((clampedProgress - t0) / (t1 - t0)).coerceIn(0f, 1f)
        } else 0f

        // Discrete calculation mode (step function)
        if (mode == "discrete") {
            return if (localProgress < 1f) values[idx0] else values[idx1]
        }

        // Segment timing function (cubic bezier easing)
        val easedProgress = if (idx0 < timingFunctions.size && mode != "paced" && mode != "cubicpaced") {
            timingFunctions[idx0].solve(localProgress)
        } else {
            localProgress
        }

        // Cubic spline calculation mode (Catmull-Rom spline)
        if (mode == "cubic" || mode == "cubicpaced") {
            val p0 = values[max(0, idx0 - 1)]
            val p1 = values[idx0]
            val p2 = values[idx1]
            val p3 = values[min(values.size - 1, idx1 + 1)]
            return evaluateCatmullRom(p0, p1, p2, p3, easedProgress)
        }

        // Standard linear interpolation
        return interpolate(values[idx0], values[idx1], easedProgress)
    }

    /**
     * Computes paced keyTimes so that animation velocity is uniform across all segments.
     */
    fun computePacedKeyTimes(values: List<Any>): List<Float> {
        val count = values.size
        if (count < 2) return listOf(0f)

        val distances = FloatArray(count - 1)
        var totalDistance = 0f

        for (i in 0 until count - 1) {
            val dist = computeDistance(values[i], values[i + 1])
            distances[i] = dist
            totalDistance += dist
        }

        if (totalDistance <= 1e-6f) {
            // Equidistant fallback if all values are identical
            return List(count) { i -> i.toFloat() / (count - 1) }
        }

        val result = mutableListOf(0f)
        var accumulated = 0f
        for (i in 0 until count - 1) {
            accumulated += distances[i]
            result.add((accumulated / totalDistance).coerceIn(0f, 1f))
        }
        result[count - 1] = 1f
        return result
    }

    private fun computeDistance(v0: Any, v1: Any): Float {
        val n0 = toFloat(v0)
        val n1 = toFloat(v1)
        if (n0 != null && n1 != null) {
            return abs(n1 - n0)
        }

        val p0 = toPoint(v0)
        val p1 = toPoint(v1)
        if (p0 != null && p1 != null) {
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            return sqrt(dx * dx + dy * dy)
        }

        val r0 = toRect(v0)
        val r1 = toRect(v1)
        if (r0 != null && r1 != null) {
            val dx = (r1.x + r1.width * 0.5f) - (r0.x + r0.width * 0.5f)
            val dy = (r1.y + r1.height * 0.5f) - (r0.y + r0.height * 0.5f)
            return sqrt(dx * dx + dy * dy)
        }

        return 1f
    }

    /**
     * Standard Catmull-Rom cubic spline interpolation between p1 and p2, using p0 and p3 as tangents.
     * P(t) = 0.5 * ( (2*p1) + (-p0 + p2)*t + (2*p0 - 5*p1 + 4*p2 - p3)*t^2 + (-p0 + 3*p1 - 3*p2 + p3)*t^3 )
     */
    fun catmullRomSpline(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            (2f * p1) +
            (-p0 + p2) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3
        )
    }

    private fun evaluateCatmullRom(v0: Any, v1: Any, v2: Any, v3: Any, t: Float): Any {
        val n0 = toFloat(v0)
        val n1 = toFloat(v1)
        val n2 = toFloat(v2)
        val n3 = toFloat(v3)
        if (n0 != null && n1 != null && n2 != null && n3 != null) {
            return catmullRomSpline(n0, n1, n2, n3, t)
        }

        val p0 = toPoint(v0)
        val p1 = toPoint(v1)
        val p2 = toPoint(v2)
        val p3 = toPoint(v3)
        if (p0 != null && p1 != null && p2 != null && p3 != null) {
            val x = catmullRomSpline(p0.x, p1.x, p2.x, p3.x, t)
            val y = catmullRomSpline(p0.y, p1.y, p2.y, p3.y, t)
            return Point2f(x, y)
        }

        val r0 = toRect(v0)
        val r1 = toRect(v1)
        val r2 = toRect(v2)
        val r3 = toRect(v3)
        if (r0 != null && r1 != null && r2 != null && r3 != null) {
            val rx = catmullRomSpline(r0.x, r1.x, r2.x, r3.x, t)
            val ry = catmullRomSpline(r0.y, r1.y, r2.y, r3.y, t)
            val rw = catmullRomSpline(r0.width, r1.width, r2.width, r3.width, t)
            val rh = catmullRomSpline(r0.height, r1.height, r2.height, r3.height, t)
            return Rect4f(rx, ry, rw, rh)
        }

        return interpolate(v1, v2, t)
    }

    fun interpolate(v0: Any, v1: Any, f: Float): Any {
        val n0 = toFloat(v0)
        val n1 = toFloat(v1)
        if (n0 != null && n1 != null) {
            return n0 + (n1 - n0) * f
        }

        val p0 = toPoint(v0)
        val p1 = toPoint(v1)
        if (p0 != null && p1 != null) {
            return p0.lerp(p1, f)
        }

        val r0 = toRect(v0)
        val r1 = toRect(v1)
        if (r0 != null && r1 != null) {
            return r0.lerp(r1, f)
        }

        val c0 = toColor(v0)
        val c1 = toColor(v1)
        if (c0 != null && c1 != null) {
            return c0.lerp(c1, f)
        }

        return if (f >= 0.5f) v1 else v0
    }

    fun toFloat(v: Any?): Float? = when (v) {
        is Number -> v.toFloat()
        is String -> v.toFloatOrNull()
        else -> null
    }

    fun toPoint(v: Any?): Point2f? = when (v) {
        is Point2f -> v
        is String -> {
            val parts = v.trim().split("[\\s,]+".toRegex()).mapNotNull { it.toFloatOrNull() }
            if (parts.size >= 2) Point2f(parts[0], parts[1]) else null
        }
        else -> null
    }

    fun toRect(v: Any?): Rect4f? = when (v) {
        is Rect4f -> v
        is String -> {
            val parts = v.trim().split("[\\s,]+".toRegex()).mapNotNull { it.toFloatOrNull() }
            if (parts.size >= 4) Rect4f(parts[0], parts[1], parts[2], parts[3]) else null
        }
        else -> null
    }

    fun toColor(v: Any?): Color4f? = when (v) {
        is Color4f -> v
        is String -> Color4f.parse(v)
        else -> null
    }
}
