package com.posterdroid.caml.model

data class CamlState(
    val name: String,
    val setValues: List<CamlStateSetValue> = emptyList()
)

data class CamlStateSetValue(
    val targetId: String,
    val keyPath: String,
    val value: Any?
) {
    fun asFloat(): Float? = when (val v = value) {
        is Number -> v.toFloat()
        is String -> v.toFloatOrNull()
        else -> null
    }

    fun asPoint(): Point2f? = when (val v = value) {
        is Point2f -> v
        is String -> {
            val parts = v.trim().split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
            if (parts.size >= 2) Point2f(parts[0], parts[1]) else null
        }
        else -> null
    }

    fun asRect(): Rect4f? = when (val v = value) {
        is Rect4f -> v
        is String -> {
            val parts = v.trim().split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
            if (parts.size >= 4) Rect4f(parts[0], parts[1], parts[2], parts[3]) else null
        }
        else -> null
    }

    fun asColor(): Color4f? = when (val v = value) {
        is Color4f -> v
        is String -> Color4f.parse(v)
        else -> null
    }

    fun asColorMatrix(): FloatArray? = when (val v = value) {
        is FloatArray -> v
        is String -> CamlFilter.ColorMatrix.parse(v)?.matrix
        else -> null
    }

    fun asTransform(): com.posterdroid.caml.math.CATransform3D? = when (val v = value) {
        is com.posterdroid.caml.math.CATransform3D -> v
        is String -> com.posterdroid.caml.math.CATransform3D.parse(v)
        else -> null
    }
}

data class CamlStateTransition(
    val fromState: String,
    val toState: String,
    val elements: List<CamlStateTransitionElement> = emptyList()
)

data class CamlStateTransitionElement(
    val key: String? = null,
    val targetId: String? = null,
    val duration: Float? = null,
    val timingFunction: String? = null,
    val mass: Float? = null,
    val stiffness: Float? = null,
    val damping: Float? = null,
    val velocity: Float? = null
)
