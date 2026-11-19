package com.posterdroid.caml.engine

import com.posterdroid.caml.math.CAFillMode
import com.posterdroid.caml.math.CAMediaTiming
import com.posterdroid.caml.math.CAMediaTimingFunction
import com.posterdroid.caml.math.CASpringTiming
import com.posterdroid.caml.model.CamlAnimation
import com.posterdroid.caml.model.Color4f
import com.posterdroid.caml.model.Point2f
import com.posterdroid.caml.model.Rect4f
import kotlin.math.floor

/**
 * Pure function evaluator for Apple Core Animation animations:
 * Supports CABasicAnimation, CAKeyframeAnimation, CASpringAnimation, and CAAnimationGroup.
 * Handles isAdditive, isCumulative, nested group timings, and multi-animation composition.
 */
object CoreAnimationEvaluator {

    /**
     * Evaluates a single animation at timeSeconds.
     * Returns null if animation is inactive at this time (e.g. fillMode removed before/after active duration).
     */
    fun evaluate(anim: CamlAnimation, timeSeconds: Double, baseValue: Any? = null): Any? {
        val timing = CAMediaTiming(
            beginTime = anim.beginTime,
            duration = if (anim.duration > 0.0) anim.duration else 1.0,
            speed = if (anim.speed != 0.0) anim.speed.toFloat() else 1.0f,
            timeOffset = anim.timeOffset,
            repeatCount = anim.repeatCount.toFloatOrNull() ?: if (anim.isInfiniteRepeat) 1000000f else 0f,
            repeatDuration = anim.repeatDuration,
            autoreverses = anim.autoreverses,
            fillMode = CAFillMode.fromString(anim.fillMode)
        )

        // Calculate normalized progress [0.0, 1.0]
        val progress = timing.computeNormalizedProgress(timeSeconds) ?: return null

        val effSpeed = if (anim.speed != 0.0) anim.speed else 1.0
        val activeTime = (timeSeconds - anim.beginTime) * effSpeed + anim.timeOffset

        // Resolve single-cycle timing function
        val timingFunc = CAMediaTimingFunction.fromString(anim.timingFunction)
        val easedProgress = timingFunc.solve(progress)

        val rawValue: Any? = when (anim.type) {
            "CASpringAnimation" -> {
                val spring = CASpringTiming(
                    mass = anim.mass ?: 1.0f,
                    stiffness = anim.stiffness ?: 100.0f,
                    damping = anim.damping ?: 10.0f,
                    initialVelocity = anim.initialVelocity ?: 0.0f
                )
                val from: Any = anim.fromValue ?: anim.values.firstOrNull() ?: 0f
                val to: Any = anim.toValue ?: anim.values.lastOrNull() ?: 1f
                val springFactor = spring.solve(activeTime.toFloat())
                CAKeyframeEvaluator.interpolate(from, to, springFactor)
            }
            "CABasicAnimation" -> {
                val from: Any = anim.fromValue ?: anim.values.firstOrNull() ?: baseValue ?: 0f
                val to: Any = anim.toValue ?: (if (anim.byValue != null) addValues(from, anim.byValue) else anim.values.lastOrNull()) ?: 1f
                CAKeyframeEvaluator.interpolate(from, to, easedProgress)
            }
            "CAKeyframeAnimation" -> {
                if (anim.values.isNotEmpty()) {
                    val timingFuncs = anim.timingFunctions.map { CAMediaTimingFunction.fromString(it) }
                    CAKeyframeEvaluator.evaluateValue(
                        values = anim.values,
                        keyTimes = anim.keyTimes,
                        timingFunctions = timingFuncs,
                        progress = progress,
                        calculationMode = anim.calculationMode
                    )
                } else null
            }
            else -> {
                // Fallback: evaluate values if available, otherwise interpolate from/to
                if (anim.values.isNotEmpty()) {
                    val timingFuncs = anim.timingFunctions.map { CAMediaTimingFunction.fromString(it) }
                    CAKeyframeEvaluator.evaluateValue(
                        values = anim.values,
                        keyTimes = anim.keyTimes,
                        timingFunctions = timingFuncs,
                        progress = progress,
                        calculationMode = anim.calculationMode
                    )
                } else {
                    val from = anim.fromValue ?: baseValue ?: 0f
                    val to = anim.toValue ?: 1f
                    CAKeyframeEvaluator.interpolate(from, to, easedProgress)
                }
            }
        }

        if (rawValue == null) return null

        // Cumulative repeat handling
        var resultValue = rawValue
        if (anim.isCumulative && timing.duration > 0.0) {
            val cycles = floor(activeTime / timing.duration).toInt()
            if (cycles > 0) {
                val startVal = anim.fromValue ?: anim.values.firstOrNull() ?: 0f
                val endVal = anim.toValue ?: anim.values.lastOrNull() ?: 0f
                val delta = subtractValues(endVal, startVal)
                if (delta != null) {
                    for (c in 0 until cycles) {
                        resultValue = addValues(resultValue, delta) ?: resultValue
                    }
                }
            }
        }

        // Additive animation handling
        if (anim.isAdditive && baseValue != null) {
            resultValue = addValues(baseValue, resultValue) ?: resultValue
        }

        return resultValue
    }

    /**
     * Evaluates a list of animations on a layer at timeSeconds and composites their values by keyPath.
     * Supports CAAnimationGroup expansion and chronological layering.
     */
    fun compositeAnimations(
        animations: List<CamlAnimation>,
        timeSeconds: Double,
        baseValues: Map<String, Any?> = emptyMap()
    ): Map<String, Any> {
        val compositeMap = mutableMapOf<String, Any>()

        // Flatten any CAAnimationGroup into child animations with adjusted nested times
        val flattened = mutableListOf<CamlAnimation>()
        for (anim in animations) {
            if (anim.type == "CAAnimationGroup" && anim.animations.isNotEmpty()) {
                val groupSpeed = if (anim.speed != 0.0) anim.speed else 1.0
                for (child in anim.animations) {
                    val childBegin = anim.beginTime + child.beginTime / groupSpeed
                    val childDuration = if (child.duration > 0.0) child.duration / groupSpeed else anim.duration
                    flattened.add(
                        child.copy(
                            beginTime = childBegin,
                            duration = childDuration,
                            speed = child.speed * groupSpeed,
                            timeOffset = child.timeOffset + anim.timeOffset
                        )
                    )
                }
            } else {
                flattened.add(anim)
            }
        }

        for (anim in flattened) {
            val keyPath = anim.keyPath
            if (keyPath.isEmpty()) continue

            val currentBase = compositeMap[keyPath] ?: baseValues[keyPath]
            val eval = evaluate(anim, timeSeconds, currentBase) ?: continue

            compositeMap[keyPath] = eval
        }

        return compositeMap
    }

    fun addValues(v1: Any?, v2: Any?): Any? {
        if (v1 == null) return v2
        if (v2 == null) return v1

        val n1 = CAKeyframeEvaluator.toFloat(v1)
        val n2 = CAKeyframeEvaluator.toFloat(v2)
        if (n1 != null && n2 != null) return n1 + n2

        val p1 = CAKeyframeEvaluator.toPoint(v1)
        val p2 = CAKeyframeEvaluator.toPoint(v2)
        if (p1 != null && p2 != null) return Point2f(p1.x + p2.x, p1.y + p2.y)

        val r1 = CAKeyframeEvaluator.toRect(v1)
        val r2 = CAKeyframeEvaluator.toRect(v2)
        if (r1 != null && r2 != null) return Rect4f(r1.x + r2.x, r1.y + r2.y, r1.width + r2.width, r1.height + r2.height)

        return v2
    }

    fun subtractValues(v1: Any?, v2: Any?): Any? {
        if (v1 == null || v2 == null) return null

        val n1 = CAKeyframeEvaluator.toFloat(v1)
        val n2 = CAKeyframeEvaluator.toFloat(v2)
        if (n1 != null && n2 != null) return n1 - n2

        val p1 = CAKeyframeEvaluator.toPoint(v1)
        val p2 = CAKeyframeEvaluator.toPoint(v2)
        if (p1 != null && p2 != null) return Point2f(p1.x - p2.x, p1.y - p2.y)

        val r1 = CAKeyframeEvaluator.toRect(v1)
        val r2 = CAKeyframeEvaluator.toRect(v2)
        if (r1 != null && r2 != null) return Rect4f(r1.x - r2.x, r1.y - r2.y, r1.width - r2.width, r1.height - r2.height)

        return null
    }
}
