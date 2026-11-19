package com.posterdroid.caml.model

/**
 * Represents a particle emitter cell in CAEmitterLayer.
 */
data class CAEmitterCell(
    val name: String? = null,
    val birthRate: Float = 0f,
    val lifetime: Float = 1f,
    val lifetimeRange: Float = 0f,
    val velocity: Float = 0f,
    val velocityRange: Float = 0f,
    val xAcceleration: Float = 0f,
    val yAcceleration: Float = 0f,
    val zAcceleration: Float = 0f,
    val scale: Float = 1f,
    val scaleRange: Float = 0f,
    val scaleSpeed: Float = 0f,
    val spin: Float = 0f,
    val spinRange: Float = 0f,
    val alphaRange: Float = 0f,
    val alphaSpeed: Float = 0f,
    val emissionLatitude: Float = 0f,
    val emissionLongitude: Float = 0f,
    val emissionRange: Float = 0f,
    val contents: CamlContents? = null,
    val contentsFrameCount: Int = 1,
    val contentsFramesPerRow: Int = 1,
    val contentsRect: Rect4f? = null,
    val color: Color4f? = null,
    val rotation: Float = 0f,
    val rotationRange: Float = 0f,
    val behaviors: List<CAEmitterBehavior> = emptyList()
)

/**
 * Represents a physics or lighting behavior applied to emitter cells.
 */
data class CAEmitterBehavior(
    val name: String? = null,
    val behavior: String = "", // "wave", "light", "alignToMotion", "attractor", "valueOverDistance"
    val force: Vector3f = Vector3f.ZERO,
    val frequency: Float = 1f,
    val orientationLatitude: Float = 0f,
    val orientationLongitude: Float = 0f,
    val attributes: Map<String, String> = emptyMap()
)
