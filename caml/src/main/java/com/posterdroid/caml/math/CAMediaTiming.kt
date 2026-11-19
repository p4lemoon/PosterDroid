package com.posterdroid.caml.math

import kotlin.math.floor

enum class CAFillMode {
    REMOVED,
    FORWARDS,
    BACKWARDS,
    BOTH;

    companion object {
        fun fromString(str: String?): CAFillMode {
            return when (str?.trim()?.lowercase()) {
                "forwards", "kcafillmodeforwards" -> FORWARDS
                "backwards", "kcafillmodebackwards" -> BACKWARDS
                "both", "kcafillmodeboth" -> BOTH
                else -> REMOVED
            }
        }
    }
}

/**
 * Encapsulates the CAMediaTiming protocol from Apple Core Animation.
 */
data class CAMediaTiming(
    val beginTime: Double = 0.0,
    val duration: Double = 0.0,
    val speed: Float = 1.0f,
    val timeOffset: Double = 0.0,
    val repeatCount: Float = 0f,
    val repeatDuration: Double = 0.0,
    val autoreverses: Boolean = false,
    val fillMode: CAFillMode = CAFillMode.REMOVED,
    val isRemovedOnCompletion: Boolean = true
) {

    /**
     * Total duration of the animation taking repeatCount, repeatDuration, and autoreverses into account.
     */
    val activeDuration: Double
        get() {
            if (duration <= 0.0) return 0.0

            val singleCycleDuration = if (autoreverses) duration * 2.0 else duration

            return when {
                repeatDuration > 0.0 -> repeatDuration
                repeatCount > 0f -> duration * repeatCount.toDouble()
                else -> singleCycleDuration
            }
        }

    /**
     * Calculates the normalized progress [0.0, 1.0] at a given parent time.
     *
     * Returns null if the animation is not active at this time and fillMode does not apply.
     */
    fun computeNormalizedProgress(parentTime: Double): Float? {
        if (duration <= 0.0) return null

        val effSpeed = if (speed != 0f) speed else 1.0f
        val activeTime = (parentTime - beginTime) * effSpeed + timeOffset

        val totalActiveDuration = activeDuration

        // 1. Before beginTime
        if (activeTime < 0.0) {
            return when (fillMode) {
                CAFillMode.BACKWARDS, CAFillMode.BOTH -> 0.0f
                CAFillMode.REMOVED, CAFillMode.FORWARDS -> null
            }
        }

        // 2. At or after activeDuration
        if (activeTime >= totalActiveDuration) {
            val totalIterations = totalActiveDuration / duration
            val fullIterations = floor(totalIterations).toInt()
            val remainder = totalIterations - fullIterations

            val baseProgress = if (remainder > 0.0) remainder.toFloat() else 1.0f

            val finalProgress = if (autoreverses) {
                val isOdd = (fullIterations % 2 != 0)
                if (remainder == 0.0) {
                    if (isOdd) 1.0f else 0.0f
                } else {
                    if (isOdd) 1.0f - baseProgress else baseProgress
                }
            } else {
                baseProgress
            }

            return if (activeTime > totalActiveDuration) {
                when (fillMode) {
                    CAFillMode.FORWARDS, CAFillMode.BOTH -> finalProgress
                    CAFillMode.REMOVED, CAFillMode.BACKWARDS -> null
                }
            } else {
                finalProgress
            }
        }

        // 3. Inside active animation window
        val iterationIndex = floor(activeTime / duration).toInt()
        val timeInIteration = activeTime - (iterationIndex * duration)
        var progress = (timeInIteration / duration).toFloat().coerceIn(0f, 1f)

        if (autoreverses) {
            val isOddCycle = (iterationIndex % 2 != 0)
            if (isOddCycle) {
                progress = 1.0f - progress
            }
        }

        return progress
    }
}
