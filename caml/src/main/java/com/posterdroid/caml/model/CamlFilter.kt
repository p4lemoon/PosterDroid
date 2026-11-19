package com.posterdroid.caml.model

/**
 * Represents image/layer filters (CIFilter / CAFilter) in Core Animation.
 */
sealed class CamlFilter {
    abstract val name: String?
    abstract val type: String

    data class ColorMatrix(
        override val name: String?,
        val matrix: FloatArray // 20 floats (4x5 Android / 5x4 CA format)
    ) : CamlFilter() {
        override val type: String = "colorMatrix"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ColorMatrix) return false
            if (name != other.name) return false
            return matrix.contentEquals(other.matrix)
        }

        override fun hashCode(): Int {
            var result = name?.hashCode() ?: 0
            result = 31 * result + matrix.contentHashCode()
            return result
        }

        companion object {
            val IDENTITY = ColorMatrix(
                name = "identity",
                matrix = floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )

            /**
             * Parses matrix string format: "matrix(1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 1 0)"
             */
            fun parse(str: String, name: String? = null): ColorMatrix? {
                val clean = str.trim()
                    .replace("matrix(", "", ignoreCase = true)
                    .replace(")", "")
                    .replace("[", "")
                    .replace("]", "")
                val parts = clean.split("[\\s,]+".toRegex()).mapNotNull { it.toFloatOrNull() }
                if (parts.size >= 20) {
                    return ColorMatrix(name, parts.take(20).toFloatArray())
                }
                return null
            }
        }
    }

    data class ColorContrast(
        override val name: String?,
        val amount: Float = 1.0f
    ) : CamlFilter() {
        override val type: String = "colorContrast"
    }

    data class GaussianBlur(
        override val name: String?,
        val radius: Float = 0.0f
    ) : CamlFilter() {
        override val type: String = "gaussianBlur"
    }

    data class Unknown(
        override val name: String?,
        override val type: String,
        val attributes: Map<String, String> = emptyMap()
    ) : CamlFilter()
}
