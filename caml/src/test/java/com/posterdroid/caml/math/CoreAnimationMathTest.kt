package com.posterdroid.caml.math

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class CoreAnimationMathTest {

    @Test
    fun testCATransform3DIdentity() {
        val identity = CATransform3D.IDENTITY
        assertTrue(identity.isIdentity())

        val p = identity.transformPoint(10f, 20f, 30f)
        assertEquals(10f, p.first, 1e-5f)
        assertEquals(20f, p.second, 1e-5f)
        assertEquals(30f, p.third, 1e-5f)
    }

    @Test
    fun testCATransform3DTranslation() {
        val t = CATransform3D.makeTranslation(100f, 200f, 50f)
        assertFalse(t.isIdentity())

        val p = t.transformPoint(10f, 20f, 30f)
        assertEquals(110f, p.first, 1e-5f)
        assertEquals(220f, p.second, 1e-5f)
        assertEquals(80f, p.third, 1e-5f)
    }

    @Test
    fun testCATransform3DMultiplicationOrder() {
        // Translate by (100, 0), then scale by 2
        val t = CATransform3D.makeTranslation(100f, 0f)
        val s = CATransform3D.makeScale(2f, 2f)

        // in Core Animation, v' = v * T * S -> (v + 100) * 2 = 2v + 200
        val combined = t * s
        val p = combined.transformPoint(10f, 0f)
        assertEquals(220f, p.first, 1e-4f)

        // Reverse: v' = v * S * T -> v * 2 + 100 = 20 + 100 = 120
        val revCombined = s * t
        val pRev = revCombined.transformPoint(10f, 0f)
        assertEquals(120f, pRev.first, 1e-4f)
    }

    @Test
    fun testCATransform3DInverseAndDeterminant() {
        val t = CATransform3D.makeTranslation(50f, 100f, 20f)
            .rotateZ((PI / 4.0).toFloat())
            .scale(2f, 3f, 1.5f)

        val det = t.determinant()
        assertTrue(det != 0f)

        val inv = t.inverse()
        assertNotNull(inv)

        // T * T^-1 should be Identity
        val prod1 = t * inv!!
        assertTrue("Expected identity, but got $prod1", prod1.isIdentity(1e-4f))

        val prod2 = inv * t
        assertTrue("Expected identity, but got $prod2", prod2.isIdentity(1e-4f))

        // Transforming a point with T then T^-1 returns original point
        val original = Triple(15f, 25f, 35f)
        val transformed = t.transformPoint(original.first, original.second, original.third)
        val restored = inv.transformPoint(transformed.first, transformed.second, transformed.third)

        assertEquals(original.first, restored.first, 1e-3f)
        assertEquals(original.second, restored.second, 1e-3f)
        assertEquals(original.third, restored.third, 1e-3f)
    }

    @Test
    fun testDecompositionAndRecomposition() {
        // Standard transform order: scale, rotate, translate
        val original = CATransform3D.makeScale(1.5f, 2.0f, 0.8f)
            .rotateZ(0.785398f) // 45 deg
            .rotateX(0.2f)
            .translate(120f, -45f, 30f)

        val decomposed = original.decompose()
        assertNotNull("Matrix should decompose successfully", decomposed)

        assertEquals(120f, decomposed!!.translation.first, 1e-2f)
        assertEquals(-45f, decomposed.translation.second, 1e-2f)
        assertEquals(30f, decomposed.translation.third, 1e-2f)

        assertEquals(1.5f, decomposed.scale.first, 1e-2f)
        assertEquals(2.0f, decomposed.scale.second, 1e-2f)
        assertEquals(0.8f, decomposed.scale.third, 1e-2f)

        // Recomposing decomposed components must restore the original matrix
        val recomposed = decomposed.recompose()
        val pt = Triple(10f, 20f, 30f)
        val pOriginal = original.transformPoint(pt.first, pt.second, pt.third)
        val pRecomposed = recomposed.transformPoint(pt.first, pt.second, pt.third)

        assertEquals(pOriginal.first, pRecomposed.first, 1e-2f)
        assertEquals(pOriginal.second, pRecomposed.second, 1e-2f)
        assertEquals(pOriginal.third, pRecomposed.third, 1e-2f)
    }

    @Test
    fun testInterpolationPreventsZeroScaleOn180DegreeRotation() {
        // Rotate from 0 to 180 deg around Z
        val t0 = CATransform3D.IDENTITY
        val t1 = CATransform3D.makeRotationZ(PI.toFloat())

        // Linear interpolation at t = 0.5 collapses m11 and m22 to 0:
        val naiveLerp = t0.lerp(t1, 0.5f)
        assertEquals(0f, naiveLerp.scaleX, 1e-4f) // collapsed!
        assertEquals(0f, naiveLerp.scaleY, 1e-4f) // collapsed!

        // Geometric interpolation (SLERP) at t = 0.5 results in 90 deg rotation with scale = 1.0!
        val geometricInterp = t0.interpolate(t1, 0.5f)
        assertEquals(1.0f, geometricInterp.scaleX, 1e-2f)
        assertEquals(1.0f, geometricInterp.scaleY, 1e-2f)

        // At 90 deg, point (10, 0) should rotate to (0, 10)
        val rotatedPt = geometricInterp.transformPoint(10f, 0f, 0f)
        assertEquals(0f, rotatedPt.first, 1e-2f)
        assertEquals(10f, rotatedPt.second, 1e-2f)
    }

    @Test
    fun testQuaternionSlerp() {
        val q0 = Quaternion.IDENTITY
        val q1 = Quaternion.fromEulerZ(PI.toFloat()) // 180 deg

        val mid = q0.slerp(q1, 0.5f)
        // Midpoint of 180 deg rotation should be 90 deg rotation around Z
        val expected = Quaternion.fromEulerZ((PI / 2.0).toFloat())

        assertEquals(expected.x, mid.x, 1e-3f)
        assertEquals(expected.y, mid.y, 1e-3f)
        assertEquals(expected.z, mid.z, 1e-3f)
        assertEquals(expected.w, mid.w, 1e-3f)
    }

    @Test
    fun testCATransform3DPerspectiveProjection() {
        // m34 = -1/500
        val perspective = CATransform3D.makePerspective(-1f / 500f)
        assertEquals(-1f / 500f, perspective.m34, 1e-6f)

        // Point at z = 250 -> w = 1 + z * m34 = 1 - 250/500 = 0.5
        // x' = 100 / 0.5 = 200 (zoomed in towards camera)
        val p = perspective.transformPoint(100f, 50f, 250f)
        assertEquals(200f, p.first, 1e-4f)
        assertEquals(100f, p.second, 1e-4f)
    }

    @Test
    fun testCATransform3DAndroidMatrixValues() {
        val t = CATransform3D.makeTranslation(50f, 100f).scale(2f, 3f)
        val values = t.toAndroidMatrixValues()
        assertEquals(9, values.size)

        // MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y, MPERSP_0, MPERSP_1, MPERSP_2
        assertEquals(2f, values[0], 1e-5f)   // m11 (scaleX)
        assertEquals(0f, values[1], 1e-5f)   // m21
        assertEquals(100f, values[2], 1e-5f) // m41 (transX)
        assertEquals(0f, values[3], 1e-5f)   // m12
        assertEquals(3f, values[4], 1e-5f)   // m22 (scaleY)
        assertEquals(300f, values[5], 1e-5f) // m42 (transY)
    }

    @Test
    fun testCATransform3DParsing() {
        val str = "1 0 0 0 0 1 0 0 0 0 1 -0.002 50 100 0 1"
        val parsed = CATransform3D.parse(str)
        assertNotNull(parsed)
        assertEquals(1f, parsed!!.m11, 1e-5f)
        assertEquals(-0.002f, parsed.m34, 1e-5f)
        assertEquals(50f, parsed.m41, 1e-5f)
        assertEquals(100f, parsed.m42, 1e-5f)
    }

    @Test
    fun testCAMediaTimingFunctionLinear() {
        val linear = CAMediaTimingFunction.LINEAR
        assertEquals(0f, linear.solve(0f), 1e-5f)
        assertEquals(0.25f, linear.solve(0.25f), 1e-5f)
        assertEquals(0.5f, linear.solve(0.5f), 1e-5f)
        assertEquals(0.75f, linear.solve(0.75f), 1e-5f)
        assertEquals(1f, linear.solve(1f), 1e-5f)
    }

    @Test
    fun testCAMediaTimingFunctionEaseInEaseOut() {
        val easeInOut = CAMediaTimingFunction.EASE_IN_EASE_OUT
        assertEquals(0f, easeInOut.solve(0f), 1e-5f)
        assertEquals(1f, easeInOut.solve(1f), 1e-5f)

        // S-curve: at t=0.5, value is symmetric 0.5
        assertEquals(0.5f, easeInOut.solve(0.5f), 0.02f)

        // Ease in starts slower than linear
        assertTrue(easeInOut.solve(0.2f) < 0.2f)
        // Ease out ends faster than linear
        assertTrue(easeInOut.solve(0.8f) > 0.8f)
    }

    @Test
    fun testCAMediaTimingFunctionCustomParsing() {
        val custom = CAMediaTimingFunction.fromString("0.22368 0.002045 0.55342 0.95297")
        assertEquals(0.22368f, custom.c1x, 1e-4f)
        assertEquals(0.002045f, custom.c1y, 1e-4f)
        assertEquals(0.55342f, custom.c2x, 1e-4f)
        assertEquals(0.95297f, custom.c2y, 1e-4f)

        assertEquals(0f, custom.solve(0f), 1e-5f)
        assertEquals(1f, custom.solve(1f), 1e-5f)
    }

    @Test
    fun testCAMediaTimingBasic() {
        val timing = CAMediaTiming(duration = 2.0)
        assertEquals(0f, timing.computeNormalizedProgress(0.0)!!, 1e-4f)
        assertEquals(0.5f, timing.computeNormalizedProgress(1.0)!!, 1e-4f)
        assertEquals(1.0f, timing.computeNormalizedProgress(2.0)!!, 1e-4f)

        // Inactive outside range with REMOVED
        assertNull(timing.computeNormalizedProgress(-0.5))
        assertNull(timing.computeNormalizedProgress(2.5))
    }

    @Test
    fun testCAMediaTimingAutoreverses() {
        val timing = CAMediaTiming(duration = 2.0, autoreverses = true)
        assertEquals(4.0, timing.activeDuration, 1e-5)

        assertEquals(0.0f, timing.computeNormalizedProgress(0.0)!!, 1e-4f)
        assertEquals(0.5f, timing.computeNormalizedProgress(1.0)!!, 1e-4f)
        assertEquals(1.0f, timing.computeNormalizedProgress(2.0)!!, 1e-4f) // peak
        assertEquals(0.5f, timing.computeNormalizedProgress(3.0)!!, 1e-4f) // returning
        assertEquals(0.0f, timing.computeNormalizedProgress(4.0)!!, 1e-4f) // returned to 0
    }

    @Test
    fun testCAMediaTimingFillModes() {
        val timingForwards = CAMediaTiming(duration = 1.0, fillMode = CAFillMode.FORWARDS)
        assertNull(timingForwards.computeNormalizedProgress(-0.5))
        assertEquals(1.0f, timingForwards.computeNormalizedProgress(1.5)!!, 1e-4f)

        val timingBackwards = CAMediaTiming(beginTime = 1.0, duration = 1.0, fillMode = CAFillMode.BACKWARDS)
        assertEquals(0.0f, timingBackwards.computeNormalizedProgress(0.5)!!, 1e-4f)
        assertNull(timingBackwards.computeNormalizedProgress(2.5))

        val timingBoth = CAMediaTiming(beginTime = 1.0, duration = 1.0, fillMode = CAFillMode.BOTH)
        assertEquals(0.0f, timingBoth.computeNormalizedProgress(0.5)!!, 1e-4f)
        assertEquals(1.0f, timingBoth.computeNormalizedProgress(2.5)!!, 1e-4f)
    }
}
