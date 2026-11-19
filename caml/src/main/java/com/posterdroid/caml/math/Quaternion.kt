package com.posterdroid.caml.math

import kotlin.math.*

/**
 * Unit quaternion (x, y, z, w) representing 3D orientation and rotation.
 * Compatible with Apple Core Animation CATransform3D and W3C CSS 3D matrix decomposition.
 */
data class Quaternion(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val w: Float = 1f
) {

    val length: Float get() = sqrt(x * x + y * y + z * z + w * w)

    fun normalized(): Quaternion {
        val len = length
        if (len < 1e-8f) return IDENTITY
        val inv = 1f / len
        return Quaternion(x * inv, y * inv, z * inv, w * inv)
    }

    /**
     * Spherical linear interpolation (SLERP) between this and other quaternion.
     * Takes the shortest path along the 4D unit hypersphere S^3.
     */
    fun slerp(other: Quaternion, t: Float): Quaternion {
        val fraction = t.coerceIn(0f, 1f)

        var q2x = other.x
        var q2y = other.y
        var q2z = other.z
        var q2w = other.w

        // Compute dot product
        var dot = x * q2x + y * q2y + z * q2z + w * q2w

        // If dot < 0, negate other to take the shortest path.
        // Guard against float precision noise near 0 (e.g. -1e-8 for exact 180 deg rotations).
        if (dot < -1e-6f) {
            dot = -dot
            q2x = -q2x
            q2y = -q2y
            q2z = -q2z
            q2w = -q2w
        } else if (dot < 0f) {
            dot = 0f
        }

        // If quaternions are very close, use linear interpolation to avoid division by zero
        if (dot > 0.9995f) {
            val rx = x + (q2x - x) * fraction
            val ry = y + (q2y - y) * fraction
            val rz = z + (q2z - z) * fraction
            val rw = w + (q2w - w) * fraction
            return Quaternion(rx, ry, rz, rw).normalized()
        }

        val theta0 = acos(dot.toDouble().coerceIn(-1.0, 1.0))
        val sinTheta0 = sin(theta0)

        val s0 = (sin((1.0 - fraction) * theta0) / sinTheta0).toFloat()
        val s1 = (sin(fraction.toDouble() * theta0) / sinTheta0).toFloat()

        val rx = x * s0 + q2x * s1
        val ry = y * s0 + q2y * s1
        val rz = z * s0 + q2z * s1
        val rw = w * s0 + q2w * s1

        return Quaternion(rx, ry, rz, rw).normalized()
    }

    /**
     * Converts this quaternion into a 4x4 rotation matrix CATransform3D.
     */
    fun toRotationMatrix(): CATransform3D {
        val q = normalized()
        val xx = q.x * q.x
        val yy = q.y * q.y
        val zz = q.z * q.z
        val xy = q.x * q.y
        val xz = q.x * q.z
        val yz = q.y * q.z
        val wx = q.w * q.x
        val wy = q.w * q.y
        val wz = q.w * q.z

        return CATransform3D(
            m11 = 1f - 2f * (yy + zz),
            m12 = 2f * (xy + wz),
            m13 = 2f * (xz - wy),
            m14 = 0f,

            m21 = 2f * (xy - wz),
            m22 = 1f - 2f * (xx + zz),
            m23 = 2f * (yz + wx),
            m24 = 0f,

            m31 = 2f * (xz + wy),
            m32 = 2f * (yz - wx),
            m33 = 1f - 2f * (xx + yy),
            m34 = 0f,

            m41 = 0f,
            m42 = 0f,
            m43 = 0f,
            m44 = 1f
        )
    }

    companion object {
        val IDENTITY = Quaternion(0f, 0f, 0f, 1f)

        fun fromAxisAngle(radians: Float, rx: Float, ry: Float, rz: Float): Quaternion {
            val lenSq = rx * rx + ry * ry + rz * rz
            if (lenSq < 1e-8f) return IDENTITY
            val invLen = 1f / sqrt(lenSq)
            val halfAngle = radians * 0.5f
            val rawS = sin(halfAngle.toDouble())
            val rawC = cos(halfAngle.toDouble())
            val s = (if (abs(rawS) < 1e-7) 0.0 else rawS).toFloat()
            val c = (if (abs(rawC) < 1e-7) 0.0 else rawC).toFloat()
            return Quaternion(rx * invLen * s, ry * invLen * s, rz * invLen * s, c).normalized()
        }

        fun fromEulerZ(radians: Float): Quaternion = fromAxisAngle(radians, 0f, 0f, 1f)
        fun fromEulerX(radians: Float): Quaternion = fromAxisAngle(radians, 1f, 0f, 0f)
        fun fromEulerY(radians: Float): Quaternion = fromAxisAngle(radians, 0f, 1f, 0f)
    }
}
