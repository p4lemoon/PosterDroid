package com.posterdroid.caml.math

import kotlin.math.*

/**
 * Analytical damped harmonic oscillator matching Apple CASpringAnimation and LKStateTransitionElement.
 *
 * Differential equation: m * x''(t) + d * x'(t) + k * x(t) = 0
 *
 * For a state transition from 0 to 1:
 * x(0) = 0, x'(0) = velocity, target = 1.0.
 */
data class CASpringTiming(
    val mass: Float = 1.0f,
    val stiffness: Float = 100.0f,
    val damping: Float = 10.0f,
    val initialVelocity: Float = 0.0f
) {
    val m = mass.coerceAtLeast(0.001f)
    val k = stiffness.coerceAtLeast(0.001f)
    val d = damping.coerceAtLeast(0.0f)
    val v0 = initialVelocity

    // Discriminant: d^2 - 4*m*k
    private val delta = d * d - 4f * m * k

    /**
     * Estimated settling duration in seconds (when amplitude stays within epsilon = 0.001 of target).
     */
    val settlingDuration: Float

    init {
        settlingDuration = when {
            delta < 0f -> {
                // Underdamped: envelope is e^(-gamma * t)
                val gamma = d / (2f * m)
                if (gamma > 0.0001f) {
                    (ln(1000f) / gamma).coerceIn(0.15f, 2.5f)
                } else {
                    1.0f
                }
            }
            delta == 0f -> {
                // Critically damped
                val gamma = d / (2f * m)
                if (gamma > 0.0001f) (ln(1000f) / gamma).coerceIn(0.15f, 2.5f) else 1.0f
            }
            else -> {
                // Overdamped
                val sqrtDelta = sqrt(delta)
                val r1 = (-d + sqrtDelta) / (2f * m) // dominant (slower) decay rate
                val decay = -r1
                if (decay > 0.0001f) (ln(1000f) / decay).coerceIn(0.15f, 2.5f) else 1.0f
            }
        }
    }

    /**
     * Evaluates spring value at time t in seconds, where at t=0 result is 0 and at t=infinity result is 1.
     */
    fun solve(tSeconds: Float): Float {
        if (tSeconds <= 0f) return 0f
        if (tSeconds >= settlingDuration * 1.5f) return 1f

        val t = tSeconds.toDouble()
        val md = m.toDouble()
        val kd = k.toDouble()
        val dd = d.toDouble()
        val v0d = v0.toDouble()

        val disc = dd * dd - 4.0 * md * kd

        return if (disc < 0.0) {
            // Underdamped
            val gamma = dd / (2.0 * md)
            val omega = sqrt(-disc) / (2.0 * md)
            val b = (gamma - v0d) / omega
            val decay = exp(-gamma * t)
            val osc = cos(omega * t) + b * sin(omega * t)
            (1.0 - decay * osc).toFloat()
        } else if (abs(disc) < 1e-7) {
            // Critically damped
            val gamma = dd / (2.0 * md)
            val decay = exp(-gamma * t)
            (1.0 - decay * (1.0 + (gamma - v0d) * t)).toFloat()
        } else {
            // Overdamped
            val sqrtDisc = sqrt(disc)
            val r1 = (-dd + sqrtDisc) / (2.0 * md)
            val r2 = (-dd - sqrtDisc) / (2.0 * md)
            val c1 = (-v0d - r2) / (r1 - r2)
            val c2 = 1.0 - c1
            (1.0 - c1 * exp(r1 * t) - c2 * exp(r2 * t)).toFloat()
        }
    }

    /**
     * Evaluates normalized spring curve across [0..1] mapped over settlingDuration.
     */
    fun solveNormalized(progress: Float): Float {
        val clamped = progress.coerceIn(0f, 1f)
        return solve(clamped * settlingDuration)
    }
}
