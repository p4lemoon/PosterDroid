package com.posterdroid.caml.engine

import com.posterdroid.caml.model.CamlAnimation
import com.posterdroid.caml.model.Point2f
import org.junit.Assert.*
import org.junit.Test

class CoreAnimationEvaluatorTest {

    @Test
    fun testBasicAnimationFromTo() {
        val anim = CamlAnimation(
            key = "basic1",
            type = "CABasicAnimation",
            keyPath = "opacity",
            duration = 2.0,
            fromValue = 0.2f,
            toValue = 0.8f
        )

        val v0 = CoreAnimationEvaluator.evaluate(anim, 0.0) as Float
        assertEquals(0.2f, v0, 1e-4f)

        val vMid = CoreAnimationEvaluator.evaluate(anim, 1.0) as Float
        assertEquals(0.5f, vMid, 1e-4f)

        val vEnd = CoreAnimationEvaluator.evaluate(anim, 2.0) as Float
        assertEquals(0.8f, vEnd, 1e-4f)
    }

    @Test
    fun testBasicAnimationByValue() {
        val anim = CamlAnimation(
            key = "byAnim",
            type = "CABasicAnimation",
            keyPath = "position.x",
            duration = 1.0,
            fromValue = 100f,
            byValue = 50f
        )

        val v0 = CoreAnimationEvaluator.evaluate(anim, 0.0) as Float
        assertEquals(100f, v0, 1e-4f)

        val vMid = CoreAnimationEvaluator.evaluate(anim, 0.5) as Float
        assertEquals(125f, vMid, 1e-4f)

        val vEnd = CoreAnimationEvaluator.evaluate(anim, 1.0) as Float
        assertEquals(150f, vEnd, 1e-4f)
    }

    @Test
    fun testKeyframeDiscreteMode() {
        val anim = CamlAnimation(
            key = "discreteAnim",
            type = "CAKeyframeAnimation",
            keyPath = "opacity",
            duration = 3.0,
            values = listOf(0.1f, 0.5f, 0.9f),
            keyTimes = listOf(0.0f, 0.333f, 1.0f),
            calculationMode = "discrete"
        )

        // At progress < 0.333 (t < 1.0), returns first value 0.1
        assertEquals(0.1f, CoreAnimationEvaluator.evaluate(anim, 0.5) as Float, 1e-4f)
        // At progress >= 0.333 and < 1.0 (t = 1.5), returns second value 0.5
        assertEquals(0.5f, CoreAnimationEvaluator.evaluate(anim, 1.5) as Float, 1e-4f)
        // At end (t = 3.0), returns final value 0.9
        assertEquals(0.9f, CoreAnimationEvaluator.evaluate(anim, 3.0) as Float, 1e-4f)
    }

    @Test
    fun testKeyframePacedMode() {
        // Values at 0, 10, 100: distance 0->10 is 10, distance 10->100 is 90 (total = 100)
        // Paced mode ignores arbitrary keyTimes and computes pacing: 10% and 90%
        val values = listOf(0f, 10f, 100f)
        val pacedTimes = CAKeyframeEvaluator.computePacedKeyTimes(values)

        assertEquals(3, pacedTimes.size)
        assertEquals(0.0f, pacedTimes[0], 1e-4f)
        assertEquals(0.1f, pacedTimes[1], 1e-4f) // 10/100
        assertEquals(1.0f, pacedTimes[2], 1e-4f)
    }

    @Test
    fun testKeyframeCubicSplineCatmullRom() {
        // Points: 0, 10, 20, 30 (linear points should evaluate to 15 at t=0.5 between 10 and 20)
        val spline = CAKeyframeEvaluator.catmullRomSpline(0f, 10f, 20f, 30f, 0.5f)
        assertEquals(15.0f, spline, 1e-3f)

        // Non-linear curvature test: smooth curve between (0, 0) and (10, 50)
        val anim = CamlAnimation(
            key = "cubicAnim",
            type = "CAKeyframeAnimation",
            keyPath = "position.y",
            duration = 1.0,
            values = listOf(0f, 10f, 50f, 60f),
            calculationMode = "cubic"
        )

        val vMid = CoreAnimationEvaluator.evaluate(anim, 0.5) as Float
        assertTrue("Cubic value should be smoothly within bounds", vMid > 10f && vMid < 50f)
    }

    @Test
    fun testSpringAnimationAnalytical() {
        val anim = CamlAnimation(
            key = "springAnim",
            type = "CASpringAnimation",
            keyPath = "transform.scale.x",
            duration = 1.0,
            fromValue = 0.5f,
            toValue = 1.5f,
            mass = 1.0f,
            stiffness = 150f,
            damping = 18f
        )

        // At t = 0, starts at fromValue = 0.5
        val v0 = CoreAnimationEvaluator.evaluate(anim, 0.0) as Float
        assertEquals(0.5f, v0, 1e-3f)

        // At t = 0.6s, spring has settled close to toValue = 1.5
        val vSettled = CoreAnimationEvaluator.evaluate(anim, 0.6) as Float
        assertEquals(1.5f, vSettled, 0.05f)
    }

    @Test
    fun testAdditiveAnimation() {
        val anim = CamlAnimation(
            key = "additiveAnim",
            type = "CABasicAnimation",
            keyPath = "transform.rotation.z",
            duration = 1.0,
            fromValue = 0f,
            toValue = 0.5f,
            isAdditive = true
        )

        // Base rotation is 1.0 rad. At t = 0.5 (progress 0.5, delta = 0.25):
        // result = base (1.0) + delta (0.25) = 1.25
        val result = CoreAnimationEvaluator.evaluate(anim, 0.5, baseValue = 1.0f) as Float
        assertEquals(1.25f, result, 1e-4f)
    }

    @Test
    fun testCumulativeAnimation() {
        val anim = CamlAnimation(
            key = "cumulativeAnim",
            type = "CABasicAnimation",
            keyPath = "position.x",
            duration = 1.0,
            repeatCount = "3",
            fromValue = 0f,
            toValue = 100f,
            isCumulative = true
        )

        // Cycle 0 (t = 0.5): value = 50
        assertEquals(50f, CoreAnimationEvaluator.evaluate(anim, 0.5) as Float, 1e-4f)

        // Cycle 1 (t = 1.5): value = 100 (from cycle 0) + 50 = 150
        assertEquals(150f, CoreAnimationEvaluator.evaluate(anim, 1.5) as Float, 1e-4f)

        // Cycle 2 (t = 2.5): value = 200 (from cycles 0 and 1) + 50 = 250
        assertEquals(250f, CoreAnimationEvaluator.evaluate(anim, 2.5) as Float, 1e-4f)
    }

    @Test
    fun testAnimationGroupComposite() {
        val childA = CamlAnimation(
            key = "childA",
            type = "CABasicAnimation",
            keyPath = "opacity",
            duration = 2.0,
            fromValue = 0.0f,
            toValue = 1.0f
        )
        val childB = CamlAnimation(
            key = "childB",
            type = "CABasicAnimation",
            keyPath = "position.x",
            duration = 2.0,
            fromValue = 100f,
            toValue = 200f
        )
        val group = CamlAnimation(
            key = "groupAnim",
            type = "CAAnimationGroup",
            keyPath = "",
            duration = 2.0,
            animations = listOf(childA, childB)
        )

        val composite = CoreAnimationEvaluator.compositeAnimations(listOf(group), 1.0)
        assertEquals(2, composite.size)
        assertEquals(0.5f, composite["opacity"] as Float, 1e-4f)
        assertEquals(150f, composite["position.x"] as Float, 1e-4f)
    }
}
