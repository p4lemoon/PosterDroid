package com.posterdroid.caml.math

import kotlin.math.*

/**
 * 4x4 matrix matching Apple Core Animation CATransform3D.
 *
 * Vectors are treated as row vectors: [x, y, z, 1] * M
 *
 * M = | m11  m12  m13  m14 |
 *     | m21  m22  m23  m24 |
 *     | m31  m32  m33  m34 |
 *     | m41  m42  m43  m44 |
 */
data class CATransform3D(
    val m11: Float = 1f, val m12: Float = 0f, val m13: Float = 0f, val m14: Float = 0f,
    val m21: Float = 0f, val m22: Float = 1f, val m23: Float = 0f, val m24: Float = 0f,
    val m31: Float = 0f, val m32: Float = 0f, val m33: Float = 1f, val m34: Float = 0f,
    val m41: Float = 0f, val m42: Float = 0f, val m43: Float = 0f, val m44: Float = 1f
) {

    val rotationZ: Float get() = atan2(m12, m11)
    val scaleX: Float get() = sqrt(m11 * m11 + m12 * m12)
    val scaleY: Float get() = sqrt(m21 * m21 + m22 * m22)
    val translationX: Float get() = m41
    val translationY: Float get() = m42
    val translationZ: Float get() = m43

    fun isIdentity(tolerance: Float = 0f): Boolean {
        if (tolerance <= 0f) {
            return m11 == 1f && m12 == 0f && m13 == 0f && m14 == 0f &&
                   m21 == 0f && m22 == 1f && m23 == 0f && m24 == 0f &&
                   m31 == 0f && m32 == 0f && m33 == 1f && m34 == 0f &&
                   m41 == 0f && m42 == 0f && m43 == 0f && m44 == 1f
        }
        return abs(m11 - 1f) <= tolerance && abs(m12) <= tolerance && abs(m13) <= tolerance && abs(m14) <= tolerance &&
               abs(m21) <= tolerance && abs(m22 - 1f) <= tolerance && abs(m23) <= tolerance && abs(m24) <= tolerance &&
               abs(m31) <= tolerance && abs(m32) <= tolerance && abs(m33 - 1f) <= tolerance && abs(m34) <= tolerance &&
               abs(m41) <= tolerance && abs(m42) <= tolerance && abs(m43) <= tolerance && abs(m44 - 1f) <= tolerance
    }

    /**
     * Concatenates this transform with another: result = this * other.
     * Equivalent to CATransform3DConcat(this, other).
     */
    operator fun times(b: CATransform3D): CATransform3D {
        val a = this
        return CATransform3D(
            m11 = a.m11 * b.m11 + a.m12 * b.m21 + a.m13 * b.m31 + a.m14 * b.m41,
            m12 = a.m11 * b.m12 + a.m12 * b.m22 + a.m13 * b.m32 + a.m14 * b.m42,
            m13 = a.m11 * b.m13 + a.m12 * b.m23 + a.m13 * b.m33 + a.m14 * b.m43,
            m14 = a.m11 * b.m14 + a.m12 * b.m24 + a.m13 * b.m34 + a.m14 * b.m44,

            m21 = a.m21 * b.m11 + a.m22 * b.m21 + a.m23 * b.m31 + a.m24 * b.m41,
            m22 = a.m21 * b.m12 + a.m22 * b.m22 + a.m23 * b.m32 + a.m24 * b.m42,
            m23 = a.m21 * b.m13 + a.m22 * b.m23 + a.m23 * b.m33 + a.m24 * b.m43,
            m24 = a.m21 * b.m14 + a.m22 * b.m24 + a.m23 * b.m34 + a.m24 * b.m44,

            m31 = a.m31 * b.m11 + a.m32 * b.m21 + a.m33 * b.m31 + a.m34 * b.m41,
            m32 = a.m31 * b.m12 + a.m32 * b.m22 + a.m33 * b.m32 + a.m34 * b.m42,
            m33 = a.m31 * b.m13 + a.m32 * b.m23 + a.m33 * b.m33 + a.m34 * b.m43,
            m34 = a.m31 * b.m14 + a.m32 * b.m24 + a.m33 * b.m34 + a.m34 * b.m44,

            m41 = a.m41 * b.m11 + a.m42 * b.m21 + a.m43 * b.m31 + a.m44 * b.m41,
            m42 = a.m41 * b.m12 + a.m42 * b.m22 + a.m43 * b.m32 + a.m44 * b.m42,
            m43 = a.m41 * b.m13 + a.m42 * b.m23 + a.m43 * b.m33 + a.m44 * b.m43,
            m44 = a.m41 * b.m14 + a.m42 * b.m24 + a.m43 * b.m34 + a.m44 * b.m44
        )
    }

    fun translate(tx: Float, ty: Float, tz: Float = 0f): CATransform3D {
        return this * makeTranslation(tx, ty, tz)
    }

    fun scale(sx: Float, sy: Float, sz: Float = 1f): CATransform3D {
        return this * makeScale(sx, sy, sz)
    }

    fun rotateZ(angleRadians: Float): CATransform3D {
        return this * makeRotationZ(angleRadians)
    }

    fun rotateX(angleRadians: Float): CATransform3D {
        return this * makeRotationX(angleRadians)
    }

    fun rotateY(angleRadians: Float): CATransform3D {
        return this * makeRotationY(angleRadians)
    }

    fun rotate(angleRadians: Float, rx: Float, ry: Float, rz: Float): CATransform3D {
        return this * makeRotation(angleRadians, rx, ry, rz)
    }

    /**
     * Converts the 3D transform into a 9-element array compatible with Android Matrix.setValues():
     * [ MSCALE_X, MSKEW_X,  MTRANS_X,
     *   MSKEW_Y,  MSCALE_Y, MTRANS_Y,
     *   MPERSP_0, MPERSP_1, MPERSP_2 ]
     */
    fun toAndroidMatrixValues(out: FloatArray) {
        if (out.size >= 9) {
            out[0] = m11; out[1] = m21; out[2] = m41
            out[3] = m12; out[4] = m22; out[5] = m42
            out[6] = m14; out[7] = m24; out[8] = m44
        }
    }

    fun toAndroidMatrixValues(): FloatArray {
        val out = FloatArray(9)
        toAndroidMatrixValues(out)
        return out
    }

    /**
     * Transforms a 3D point and applies perspective division (w).
     */
    fun transformPoint(x: Float, y: Float, z: Float = 0f): Triple<Float, Float, Float> {
        val px = x * m11 + y * m21 + z * m31 + m41
        val py = x * m12 + y * m22 + z * m32 + m42
        val pz = x * m13 + y * m23 + z * m33 + m43
        val pw = x * m14 + y * m24 + z * m34 + m44

        return if (pw != 0f && pw != 1f) {
            val invW = 1f / pw
            Triple(px * invW, py * invW, pz * invW)
        } else {
            Triple(px, py, pz)
        }
    }

    fun transpose(): CATransform3D {
        return CATransform3D(
            m11 = m11, m12 = m21, m13 = m31, m14 = m41,
            m21 = m12, m22 = m22, m23 = m32, m24 = m42,
            m31 = m13, m32 = m23, m33 = m33, m34 = m43,
            m41 = m14, m42 = m24, m43 = m34, m44 = m44
        )
    }

    /**
     * Calculates the determinant of this 4x4 matrix using Laplace expansion.
     */
    fun determinant(): Float {
        val b00 = m11 * m22 - m12 * m21
        val b01 = m11 * m23 - m13 * m21
        val b02 = m11 * m24 - m14 * m21
        val b03 = m12 * m23 - m13 * m22
        val b04 = m12 * m24 - m14 * m22
        val b05 = m13 * m24 - m14 * m23
        val b06 = m31 * m42 - m32 * m41
        val b07 = m31 * m43 - m33 * m41
        val b08 = m31 * m44 - m34 * m41
        val b09 = m32 * m43 - m33 * m42
        val b10 = m32 * m44 - m34 * m42
        val b11 = m33 * m44 - m34 * m43

        return b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06
    }

    /**
     * Calculates the inverse of this 4x4 matrix. Returns null if matrix is singular (det == 0).
     */
    fun inverse(): CATransform3D? {
        val b00 = m11 * m22 - m12 * m21
        val b01 = m11 * m23 - m13 * m21
        val b02 = m11 * m24 - m14 * m21
        val b03 = m12 * m23 - m13 * m22
        val b04 = m12 * m24 - m14 * m22
        val b05 = m13 * m24 - m14 * m23
        val b06 = m31 * m42 - m32 * m41
        val b07 = m31 * m43 - m33 * m41
        val b08 = m31 * m44 - m34 * m41
        val b09 = m32 * m43 - m33 * m42
        val b10 = m32 * m44 - m34 * m42
        val b11 = m33 * m44 - m34 * m43

        val det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06
        if (abs(det) < 1e-8f) return null

        val invDet = 1f / det

        return CATransform3D(
            m11 = (m22 * b11 - m23 * b10 + m24 * b09) * invDet,
            m12 = (-m12 * b11 + m13 * b10 - m14 * b09) * invDet,
            m13 = (m42 * b05 - m43 * b04 + m44 * b03) * invDet,
            m14 = (-m32 * b05 + m33 * b04 - m34 * b03) * invDet,

            m21 = (-m21 * b11 + m23 * b08 - m24 * b07) * invDet,
            m22 = (m11 * b11 - m13 * b08 + m14 * b07) * invDet,
            m23 = (-m41 * b05 + m43 * b02 - m44 * b01) * invDet,
            m24 = (m31 * b05 - m33 * b02 + m34 * b01) * invDet,

            m31 = (m21 * b10 - m22 * b08 + m24 * b06) * invDet,
            m32 = (-m11 * b10 + m12 * b08 - m14 * b06) * invDet,
            m33 = (m41 * b04 - m42 * b02 + m44 * b00) * invDet,
            m34 = (-m31 * b04 + m32 * b02 - m34 * b00) * invDet,

            m41 = (-m21 * b09 + m22 * b07 - m23 * b06) * invDet,
            m42 = (m11 * b09 - m12 * b07 + m13 * b06) * invDet,
            m43 = (-m41 * b03 + m42 * b01 - m43 * b00) * invDet,
            m44 = (m31 * b03 - m32 * b01 + m33 * b00) * invDet
        )
    }

    /**
     * Decomposes this transform into translation, rotation (quaternion), scale, skew, and perspective.
     */
    fun decompose(): DecomposedCATransform? = DecomposedCATransform.decompose(this)

    /**
     * Geometric matrix interpolation conforming to Apple Core Animation and W3C CSS 3D Transforms:
     * - Deconstructs matrices into Translation, Rotation (Quaternion), Scale, Skew, Perspective.
     * - Performs spherical linear interpolation (SLERP) on rotation quaternions.
     * - Linearly interpolates (LERP) translation, scale, skew, and perspective.
     * - Recomposes the interpolated components into an exact 4x4 matrix.
     * - Falls back to linear component-wise interpolation if matrices are non-decomposable (singular).
     */
    fun interpolate(other: CATransform3D, t: Float): CATransform3D {
        val decA = this.decompose()
        val decB = other.decompose()
        if (decA != null && decB != null) {
            return decA.interpolate(decB, t).recompose()
        }
        return lerp(other, t)
    }

    /**
     * Linear component-wise interpolation between matrices.
     */
    fun lerp(other: CATransform3D, t: Float): CATransform3D {
        val f = t.coerceIn(0f, 1f)
        return CATransform3D(
            m11 = m11 + (other.m11 - m11) * f,
            m12 = m12 + (other.m12 - m12) * f,
            m13 = m13 + (other.m13 - m13) * f,
            m14 = m14 + (other.m14 - m14) * f,

            m21 = m21 + (other.m21 - m21) * f,
            m22 = m22 + (other.m22 - m22) * f,
            m23 = m23 + (other.m23 - m23) * f,
            m24 = m24 + (other.m24 - m24) * f,

            m31 = m31 + (other.m31 - m31) * f,
            m32 = m32 + (other.m32 - m32) * f,
            m33 = m33 + (other.m33 - m33) * f,
            m34 = m34 + (other.m34 - m34) * f,

            m41 = m41 + (other.m41 - m41) * f,
            m42 = m42 + (other.m42 - m42) * f,
            m43 = m43 + (other.m43 - m43) * f,
            m44 = m44 + (other.m44 - m44) * f
        )
    }

    companion object {
        val IDENTITY = CATransform3D()

        fun makeTranslation(tx: Float, ty: Float, tz: Float = 0f): CATransform3D =
            CATransform3D(
                m41 = tx,
                m42 = ty,
                m43 = tz
            )

        fun makeScale(sx: Float, sy: Float, sz: Float = 1f): CATransform3D =
            CATransform3D(
                m11 = sx,
                m22 = sy,
                m33 = sz
            )

        fun makeRotationZ(radians: Float): CATransform3D {
            val c = cos(radians.toDouble()).toFloat()
            val s = sin(radians.toDouble()).toFloat()
            return CATransform3D(
                m11 = c, m12 = s,
                m21 = -s, m22 = c
            )
        }

        fun makeRotationX(radians: Float): CATransform3D {
            val c = cos(radians.toDouble()).toFloat()
            val s = sin(radians.toDouble()).toFloat()
            return CATransform3D(
                m22 = c, m23 = s,
                m32 = -s, m33 = c
            )
        }

        fun makeRotationY(radians: Float): CATransform3D {
            val c = cos(radians.toDouble()).toFloat()
            val s = sin(radians.toDouble()).toFloat()
            return CATransform3D(
                m11 = c, m13 = -s,
                m31 = s, m33 = c
            )
        }

        fun makeRotation(radians: Float, rx: Float, ry: Float, rz: Float): CATransform3D {
            val lenSq = rx * rx + ry * ry + rz * rz
            if (lenSq < 1e-8f) return IDENTITY
            val invLen = 1f / sqrt(lenSq)
            val x = rx * invLen
            val y = ry * invLen
            val z = rz * invLen

            val c = cos(radians.toDouble()).toFloat()
            val s = sin(radians.toDouble()).toFloat()
            val t = 1f - c

            return CATransform3D(
                m11 = t * x * x + c,
                m12 = t * x * y + s * z,
                m13 = t * x * z - s * y,

                m21 = t * x * y - s * z,
                m22 = t * y * y + c,
                m23 = t * y * z + s * x,

                m31 = t * x * z + s * y,
                m32 = t * y * z - s * x,
                m33 = t * z * z + c
            )
        }

        /**
         * Creates a 3D perspective projection transform.
         * d is the distance from viewer to projection plane (e.g. -1.0 / 500f).
         */
        fun makePerspective(m34: Float): CATransform3D {
            return CATransform3D(m34 = m34)
        }

        /**
         * Parses a transform string which can be either:
         * 1) A 16-element matrix: "1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1"
         * 2) A chained function sequence: "rotate(134.744deg) scale(1.24116, 1.24116, 1)"
         */
        fun parse(str: String): CATransform3D? {
            val trimmed = str.trim()
            if (trimmed.isEmpty()) return null

            // 1. Try 16-element matrix if no function parens
            if (!trimmed.contains("(")) {
                val parts = trimmed
                    .replace("[", "")
                    .replace("]", "")
                    .split("[\\s,]+".toRegex())
                    .mapNotNull { it.toFloatOrNull() }

                if (parts.size == 16) {
                    return CATransform3D(
                        m11 = parts[0], m12 = parts[1], m13 = parts[2], m14 = parts[3],
                        m21 = parts[4], m22 = parts[5], m23 = parts[6], m24 = parts[7],
                        m31 = parts[8], m32 = parts[9], m33 = parts[10], m34 = parts[11],
                        m41 = parts[12], m42 = parts[13], m43 = parts[14], m44 = parts[15]
                    )
                }
                return null
            }

            // 2. Parse function sequence e.g. "rotate(134.744deg) scale(1.24116, 1.24116, 1)"
            val funcRegex = "([a-zA-Z0-9]+)\\s*\\(([^)]*)\\)".toRegex()
            val matches = funcRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) return null

            var result = IDENTITY
            for (match in matches) {
                val funcName = match.groupValues[1]
                val argsStr = match.groupValues[2].trim()
                val args = if (argsStr.isEmpty()) emptyList() else argsStr.split("[\\s,]+".toRegex())

                when (funcName.lowercase()) {
                    "rotate" -> {
                        when (args.size) {
                            1 -> result = result * makeRotationZ(parseAngle(args[0]))
                            4 -> result = result * makeRotation(
                                parseAngle(args[0]),
                                args[1].toFloatOrNull() ?: 0f,
                                args[2].toFloatOrNull() ?: 0f,
                                args[3].toFloatOrNull() ?: 0f
                            )
                        }
                    }
                    "rotatez" -> {
                        if (args.isNotEmpty()) result = result * makeRotationZ(parseAngle(args[0]))
                    }
                    "rotatex" -> {
                        if (args.isNotEmpty()) result = result * makeRotationX(parseAngle(args[0]))
                    }
                    "rotatey" -> {
                        if (args.isNotEmpty()) result = result * makeRotationY(parseAngle(args[0]))
                    }
                    "scale" -> {
                        when (args.size) {
                            1 -> {
                                val s = args[0].toFloatOrNull() ?: 1f
                                result = result * makeScale(s, s, 1f)
                            }
                            2 -> {
                                val sx = args[0].toFloatOrNull() ?: 1f
                                val sy = args[1].toFloatOrNull() ?: 1f
                                result = result * makeScale(sx, sy, 1f)
                            }
                            3 -> {
                                val sx = args[0].toFloatOrNull() ?: 1f
                                val sy = args[1].toFloatOrNull() ?: 1f
                                val sz = args[2].toFloatOrNull() ?: 1f
                                result = result * makeScale(sx, sy, sz)
                            }
                        }
                    }
                    "scalex" -> {
                        if (args.isNotEmpty()) {
                            val sx = args[0].toFloatOrNull() ?: 1f
                            result = result * makeScale(sx, 1f, 1f)
                        }
                    }
                    "scaley" -> {
                        if (args.isNotEmpty()) {
                            val sy = args[0].toFloatOrNull() ?: 1f
                            result = result * makeScale(1f, sy, 1f)
                        }
                    }
                    "scalez" -> {
                        if (args.isNotEmpty()) {
                            val sz = args[0].toFloatOrNull() ?: 1f
                            result = result * makeScale(1f, 1f, sz)
                        }
                    }
                    "translate" -> {
                        when (args.size) {
                            1 -> {
                                val tx = args[0].toFloatOrNull() ?: 0f
                                result = result * makeTranslation(tx, 0f, 0f)
                            }
                            2 -> {
                                val tx = args[0].toFloatOrNull() ?: 0f
                                val ty = args[1].toFloatOrNull() ?: 0f
                                result = result * makeTranslation(tx, ty, 0f)
                            }
                            3 -> {
                                val tx = args[0].toFloatOrNull() ?: 0f
                                val ty = args[1].toFloatOrNull() ?: 0f
                                val tz = args[2].toFloatOrNull() ?: 0f
                                result = result * makeTranslation(tx, ty, tz)
                            }
                        }
                    }
                    "translatex" -> {
                        if (args.isNotEmpty()) result = result * makeTranslation(args[0].toFloatOrNull() ?: 0f, 0f, 0f)
                    }
                    "translatey" -> {
                        if (args.isNotEmpty()) result = result * makeTranslation(0f, args[0].toFloatOrNull() ?: 0f, 0f)
                    }
                    "translatez" -> {
                        if (args.isNotEmpty()) result = result * makeTranslation(0f, 0f, args[0].toFloatOrNull() ?: 0f)
                    }
                    "perspective" -> {
                        if (args.isNotEmpty()) {
                            val d = args[0].toFloatOrNull() ?: 0f
                            if (abs(d) > 1e-4f) {
                                result = result * makePerspective(-1f / d)
                            }
                        }
                    }
                }
            }
            return result
        }

        private fun parseAngle(s: String): Float {
            val trimmed = s.trim()
            return when {
                trimmed.endsWith("deg", ignoreCase = true) -> {
                    val v = trimmed.substring(0, trimmed.length - 3).trim().toFloatOrNull() ?: 0f
                    Math.toRadians(v.toDouble()).toFloat()
                }
                trimmed.endsWith("rad", ignoreCase = true) -> {
                    trimmed.substring(0, trimmed.length - 3).trim().toFloatOrNull() ?: 0f
                }
                else -> trimmed.toFloatOrNull() ?: 0f
            }
        }
    }
}
