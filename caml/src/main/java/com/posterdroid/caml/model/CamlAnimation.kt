package com.posterdroid.caml.model

/**
 * Encapsulates Apple Core Animation animations (CABasicAnimation, CAKeyframeAnimation,
 * CASpringAnimation, CAAnimationGroup, CATransition).
 */
data class CamlAnimation(
    val key: String,
    val type: String = "CAKeyframeAnimation",
    val keyPath: String,
    val beginTime: Double = 0.0,
    val duration: Double = 0.0,
    val speed: Double = 1.0,
    val timeOffset: Double = 0.0,
    val repeatCount: String = "1",
    val repeatDuration: Double = 0.0,
    val autoreverses: Boolean = false,
    val fillMode: String = "removed",
    val fromValue: Any? = null,
    val toValue: Any? = null,
    val byValue: Any? = null,
    val values: List<Any> = emptyList(),
    val keyTimes: List<Float> = emptyList(),
    val timingFunction: String = "linear",
    val timingFunctions: List<String> = emptyList(),
    val calculationMode: String = "linear", // linear, discrete, paced, cubic, cubicPaced
    val isAdditive: Boolean = false,
    val isCumulative: Boolean = false,
    val valueFunction: String? = null,
    val path: String? = null,
    val rotationMode: String? = null, // auto, autoReverse
    val mass: Float? = null,
    val stiffness: Float? = null,
    val damping: Float? = null,
    val initialVelocity: Float? = null,
    val animations: List<CamlAnimation> = emptyList()
) {
    val isInfiniteRepeat: Boolean get() = repeatCount.equals("inf", ignoreCase = true) || repeatCount.equals("infinity", ignoreCase = true)
}
