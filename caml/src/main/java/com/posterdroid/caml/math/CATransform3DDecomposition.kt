package com.posterdroid.caml.math

import kotlin.math.*

/**
 * Representation of a decomposed CATransform3D matrix into independent geometric components:
 * Translation, Scale, Skew (shear), Rotation (quaternion), and Perspective.
 *
 * Conforms to Apple Core Animation matrix interpolation and W3C CSS 3D Transforms specifications.
 */
data class DecomposedCATransform(
    val translation: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val scale: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
    val skew: Triple<Float, Float, Float> = Triple(0f, 0f, 0f), // xy, xz, yz
    val quaternion: Quaternion = Quaternion.IDENTITY,
    val perspective: FloatArray = floatArrayOf(0f, 0f, 0f, 1f) // m14, m24, m34, m44
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecomposedCATransform) return false
        return translation == other.translation &&
               scale == other.scale &&
               skew == other.skew &&
               quaternion == other.quaternion &&
               perspective.contentEquals(other.perspective)
    }

    override fun hashCode(): Int {
        var result = translation.hashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + skew.hashCode()
        result = 31 * result + quaternion.hashCode()
        result = 31 * result + perspective.contentHashCode()
        return result
    }

    /**
     * Recomposes the decomposed components back into a full 4x4 CATransform3D matrix:
     * In row-vector convention: M = Scale * Skew * Rotation * Translation * Perspective
     */
    fun recompose(): CATransform3D {
        var m = CATransform3D.makeScale(scale.first, scale.second, scale.third)

        // Skew
        if (skew.first != 0f || skew.second != 0f || skew.third != 0f) {
            val skewMat = CATransform3D(
                m11 = 1f, m12 = 0f, m13 = 0f, m14 = 0f,
                m21 = skew.first, m22 = 1f, m23 = 0f, m24 = 0f,
                m31 = skew.second, m32 = skew.third, m33 = 1f, m34 = 0f,
                m41 = 0f, m42 = 0f, m43 = 0f, m44 = 1f
            )
            m = m * skewMat
        }

        // Rotation
        m = m * quaternion.toRotationMatrix()

        // Translation
        m = m.translate(translation.first, translation.second, translation.third)

        // Perspective
        if (perspective[0] != 0f || perspective[1] != 0f || perspective[2] != 0f || perspective[3] != 1f) {
            val perspMat = CATransform3D(
                m14 = perspective[0],
                m24 = perspective[1],
                m34 = perspective[2],
                m44 = perspective[3]
            )
            m = m * perspMat
        }

        return m
    }

    /**
     * Interpolates geometrically between this decomposed transform and another:
     * - Translation: LERP
     * - Scale: LERP
     * - Skew: LERP
     * - Perspective: LERP
     * - Rotation: SLERP (spherical linear interpolation)
     */
    fun interpolate(other: DecomposedCATransform, t: Float): DecomposedCATransform {
        val f = t.coerceIn(0f, 1f)

        val tx = translation.first + (other.translation.first - translation.first) * f
        val ty = translation.second + (other.translation.second - translation.second) * f
        val tz = translation.third + (other.translation.third - translation.third) * f

        val sx = scale.first + (other.scale.first - scale.first) * f
        val sy = scale.second + (other.scale.second - scale.second) * f
        val sz = scale.third + (other.scale.third - scale.third) * f

        val kxy = skew.first + (other.skew.first - skew.first) * f
        val kxz = skew.second + (other.skew.second - skew.second) * f
        val kyz = skew.third + (other.skew.third - skew.third) * f

        val p = FloatArray(4) { i ->
            perspective[i] + (other.perspective[i] - perspective[i]) * f
        }

        val q = quaternion.slerp(other.quaternion, f)

        return DecomposedCATransform(
            translation = Triple(tx, ty, tz),
            scale = Triple(sx, sy, sz),
            skew = Triple(kxy, kxz, kyz),
            quaternion = q,
            perspective = p
        )
    }

    companion object {

        private class Vec3(var x: Float, var y: Float, var z: Float) {
            val length: Float get() = sqrt(x * x + y * y + z * z)
            fun normalize(): Float {
                val len = length
                if (len > 1e-8f) {
                    val inv = 1f / len
                    x *= inv; y *= inv; z *= inv
                }
                return len
            }
            fun dot(b: Vec3): Float = x * b.x + y * b.y + z * b.z
            fun cross(b: Vec3): Vec3 = Vec3(
                y * b.z - z * b.y,
                z * b.x - x * b.z,
                x * b.y - y * b.x
            )
            fun combine(b: Vec3, ascl: Float, bscl: Float): Vec3 = Vec3(
                ascl * x + bscl * b.x,
                ascl * y + bscl * b.y,
                ascl * z + bscl * b.z
            )
        }

        /**
         * Decomposes a CATransform3D matrix into translation, rotation (quaternion), scale, skew, and perspective.
         * Returns null if matrix is singular (determinant zero or m44 zero).
         */
        fun decompose(matrix: CATransform3D): DecomposedCATransform? {
            if (abs(matrix.m44) < 1e-8f) return null

            // 1. Normalize matrix so that m44 = 1.0
            val invM44 = 1f / matrix.m44
            var a11 = matrix.m11 * invM44; var a12 = matrix.m12 * invM44; var a13 = matrix.m13 * invM44; val a14 = matrix.m14 * invM44
            var a21 = matrix.m21 * invM44; var a22 = matrix.m22 * invM44; var a23 = matrix.m23 * invM44; val a24 = matrix.m24 * invM44
            var a31 = matrix.m31 * invM44; var a32 = matrix.m32 * invM44; var a33 = matrix.m33 * invM44; val a34 = matrix.m34 * invM44
            val a41 = matrix.m41 * invM44; val a42 = matrix.m42 * invM44; val a43 = matrix.m43 * invM44

            // 2. Perspective
            val persp = floatArrayOf(0f, 0f, 0f, 1f)
            if (a14 != 0f || a24 != 0f || a34 != 0f) {
                persp[0] = a14
                persp[1] = a24
                persp[2] = a34
                persp[3] = 1f
            }

            // 3. Translation
            val translation = Triple(a41, a42, a43)

            // 4. Rows of 3x3 linear part
            val row0 = Vec3(a11, a12, a13)
            val row1 = Vec3(a21, a22, a23)
            val row2 = Vec3(a31, a32, a33)

            // 5. Scale X and normalize row0
            var scaleX = row0.normalize()
            if (scaleX < 1e-8f) return null

            // 6. Skew XY and make row1 orthogonal to row0
            var skewXY = row0.dot(row1)
            val row1Ortho = row1.combine(row0, 1f, -skewXY)

            // 7. Scale Y and normalize row1
            var scaleY = row1Ortho.normalize()
            if (scaleY < 1e-8f) return null
            skewXY /= scaleY

            // 8. Skew XZ and YZ
            var skewXZ = row0.dot(row2)
            var row2Ortho = row2.combine(row0, 1f, -skewXZ)
            var skewYZ = row1Ortho.dot(row2Ortho)
            row2Ortho = row2Ortho.combine(row1Ortho, 1f, -skewYZ)

            // 9. Scale Z and normalize row2
            var scaleZ = row2Ortho.normalize()
            if (scaleZ < 1e-8f) return null
            skewXZ /= scaleZ
            skewYZ /= scaleZ

            // 10. Check determinant for negative scale (reflection)
            val pdum3 = row1Ortho.cross(row2Ortho)
            if (row0.dot(pdum3) < 0f) {
                scaleX = -scaleX
                row0.x = -row0.x; row0.y = -row0.y; row0.z = -row0.z
            }

            // 11. Extract Quaternion from orthonormal rotation matrix
            val r00 = row0.x; val r01 = row0.y; val r02 = row0.z
            val r10 = row1Ortho.x; val r11 = row1Ortho.y; val r12 = row1Ortho.z
            val r20 = row2Ortho.x; val r21 = row2Ortho.y; val r22 = row2Ortho.z

            val trace = r00 + r11 + r22
            val quat: Quaternion = if (trace > 0f) {
                val s = 0.5f / sqrt(trace + 1.0f)
                val qw = 0.25f / s
                val qx = (r12 - r21) * s
                val qy = (r20 - r02) * s
                val qz = (r01 - r10) * s
                Quaternion(qx, qy, qz, qw).normalized()
            } else if (r00 > r11 && r00 > r22) {
                val s = 2.0f * sqrt(1.0f + r00 - r11 - r22)
                val qw = (r12 - r21) / s
                val qx = 0.25f * s
                val qy = (r01 + r10) / s
                val qz = (r02 + r20) / s
                Quaternion(qx, qy, qz, qw).normalized()
            } else if (r11 > r22) {
                val s = 2.0f * sqrt(1.0f + r11 - r00 - r22)
                val qw = (r20 - r02) / s
                val qx = (r01 + r10) / s
                val qy = 0.25f * s
                val qz = (r12 + r21) / s
                Quaternion(qx, qy, qz, qw).normalized()
            } else {
                val s = 2.0f * sqrt(1.0f + r22 - r00 - r11)
                val qw = (r01 - r10) / s
                val qx = (r02 + r20) / s
                val qy = (r12 + r21) / s
                val qz = 0.25f * s
                Quaternion(qx, qy, qz, qw).normalized()
            }

            return DecomposedCATransform(
                translation = translation,
                scale = Triple(scaleX, scaleY, scaleZ),
                skew = Triple(skewXY, skewXZ, skewYZ),
                quaternion = quat,
                perspective = persp
            )
        }
    }
}
