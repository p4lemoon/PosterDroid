package com.posterdroid.caml.engine

import com.posterdroid.caml.model.*

data class EvaluatedLayer(
    val originalLayer: CamlLayer,
    val bounds: Rect4f,
    val position: Point2f,
    val anchorPoint: Point2f,
    val opacity: Float,
    val rotationZ: Float,
    val scaleX: Float,
    val scaleY: Float,
    val hidden: Boolean,
    val backgroundColor: Color4f?,
    val contents: CamlContents?,
    val sublayers: List<EvaluatedLayer>
)

class LayerInterpolator {

    fun interpolateTree(tree: CamlLayerTree, progress: Float, timeSeconds: Float = 0f): EvaluatedLayer {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val lockedState = tree.findState("Locked")
        val unlockState = tree.findState("Unlock")

        val lockedValuesByTarget = lockedState?.setValues?.groupBy { it.targetId } ?: emptyMap()
        val unlockValuesByTarget = unlockState?.setValues?.groupBy { it.targetId } ?: emptyMap()

        return interpolateLayer(tree.rootLayer, clampedProgress, lockedValuesByTarget, unlockValuesByTarget, timeSeconds)
    }

    private fun interpolateLayer(
        layer: CamlLayer,
        progress: Float,
        lockedMap: Map<String, List<CamlStateSetValue>>,
        unlockMap: Map<String, List<CamlStateSetValue>>,
        timeSeconds: Float
    ): EvaluatedLayer {
        val layerId = layer.id
        val layerName = layer.name
        val idCandidates = listOfNotNull(
            layerId,
            layerName,
            layerName?.let { if (!it.startsWith("#")) "#$it" else null }
        ).distinct()

        val lockedValues = idCandidates.flatMap { lockedMap[it] ?: emptyList() }
        val unlockValues = idCandidates.flatMap { unlockMap[it] ?: emptyList() }

        // Base properties
        var posX0 = layer.position.x
        var posY0 = layer.position.y
        var posX1 = posX0
        var posY1 = posY0

        var bX0 = layer.bounds.x
        var bY0 = layer.bounds.y
        var bW0 = layer.bounds.width
        var bH0 = layer.bounds.height
        var bX1 = bX0
        var bY1 = bY0
        var bW1 = bW0
        var bH1 = bH0

        var opacity0 = layer.opacity
        var opacity1 = opacity0

        var rot0 = layer.transform.rotationZ
        var rot1 = rot0

        var scaleX0 = layer.transform.scaleX
        var scaleY0 = layer.transform.scaleY
        var scaleX1 = scaleX0
        var scaleY1 = scaleY0

        var hidden0 = layer.hidden
        var hidden1 = hidden0

        // Apply Locked overrides
        for (sv in lockedValues) {
            when (sv.keyPath) {
                "position.x" -> sv.asFloat()?.let { posX0 = it }
                "position.y" -> sv.asFloat()?.let { posY0 = it }
                "position" -> sv.asPoint()?.let { posX0 = it.x; posY0 = it.y }
                "bounds.origin.x" -> sv.asFloat()?.let { bX0 = it }
                "bounds.origin.y" -> sv.asFloat()?.let { bY0 = it }
                "bounds.size.width" -> sv.asFloat()?.let { bW0 = it }
                "bounds.size.height" -> sv.asFloat()?.let { bH0 = it }
                "bounds" -> sv.asRect()?.let { bX0 = it.x; bY0 = it.y; bW0 = it.width; bH0 = it.height }
                "opacity" -> sv.asFloat()?.let { opacity0 = it }
                "transform.rotation.z" -> sv.asFloat()?.let { rot0 = it }
                "transform.scale.x" -> sv.asFloat()?.let { scaleX0 = it }
                "transform.scale.y" -> sv.asFloat()?.let { scaleY0 = it }
                "transform.scale", "transform.scale.xy" -> sv.asFloat()?.let { scaleX0 = it; scaleY0 = it }
                "hidden" -> hidden0 = sv.value == true || sv.value == "1" || sv.value == "true"
            }
        }

        // Apply Unlock overrides
        for (sv in unlockValues) {
            when (sv.keyPath) {
                "position.x" -> sv.asFloat()?.let { posX1 = it }
                "position.y" -> sv.asFloat()?.let { posY1 = it }
                "position" -> sv.asPoint()?.let { posX1 = it.x; posY1 = it.y }
                "bounds.origin.x" -> sv.asFloat()?.let { bX1 = it }
                "bounds.origin.y" -> sv.asFloat()?.let { bY1 = it }
                "bounds.size.width" -> sv.asFloat()?.let { bW1 = it }
                "bounds.size.height" -> sv.asFloat()?.let { bH1 = it }
                "bounds" -> sv.asRect()?.let { bX1 = it.x; bY1 = it.y; bW1 = it.width; bH1 = it.height }
                "opacity" -> sv.asFloat()?.let { opacity1 = it }
                "transform.rotation.z" -> sv.asFloat()?.let { rot1 = it }
                "transform.scale.x" -> sv.asFloat()?.let { scaleX1 = it }
                "transform.scale.y" -> sv.asFloat()?.let { scaleY1 = it }
                "transform.scale", "transform.scale.xy" -> sv.asFloat()?.let { scaleX1 = it; scaleY1 = it }
                "hidden" -> hidden1 = sv.value == true || sv.value == "1" || sv.value == "true"
            }
        }

        // Lerp between state 0 (progress 0) and state 1 (progress 1)
        var curX = posX0 + (posX1 - posX0) * progress
        var curY = posY0 + (posY1 - posY0) * progress

        val curBx = bX0 + (bX1 - bX0) * progress
        val curBy = bY0 + (bY1 - bY0) * progress
        val curBw = bW0 + (bW1 - bW0) * progress
        val curBh = bH0 + (bH1 - bH0) * progress

        var curOpacity = (opacity0 + (opacity1 - opacity0) * progress).coerceIn(0f, 1f)
        var curRot = rot0 + (rot1 - rot0) * progress
        var curScaleX = scaleX0 + (scaleX1 - scaleX0) * progress
        var curScaleY = scaleY0 + (scaleY1 - scaleY0) * progress
        val curHidden = if (progress >= 0.5f) hidden1 else hidden0

        // Apply continuous CAKeyframeAnimation / CABasicAnimation at timeSeconds
        if (timeSeconds > 0f && layer.animations.isNotEmpty()) {
            for (anim in layer.animations) {
                if (anim.values.isEmpty()) continue
                val duration = if (anim.duration > 0.0) anim.duration.toFloat() else 1f
                val speed = if (anim.speed != 0.0) anim.speed.toFloat() else 1f
                val t = (timeSeconds * speed)

                val cycle = if (duration > 0f) t / duration else 0f
                val isEvenCycle = (cycle.toInt() % 2 == 0)
                val tMod = if (duration > 0f) (t % duration + duration) % duration else 0f
                var animProgress = if (duration > 0f) (tMod / duration).coerceIn(0f, 1f) else 0f

                if (anim.autoreverses && !isEvenCycle) {
                    animProgress = 1f - animProgress
                }

                val values = anim.values
                if (values.size == 1) {
                    when (anim.keyPath) {
                        "transform.rotation.z" -> (values[0] as? Number)?.toFloat()?.let { curRot += it }
                        "opacity" -> (values[0] as? Number)?.toFloat()?.let { curOpacity *= it }
                    }
                    continue
                }

                val keyTimes = anim.keyTimes
                val segProgress: Float
                val idx0: Int
                val idx1: Int
                if (keyTimes.size == values.size && keyTimes.size >= 2) {
                    var foundIdx = 0
                    for (k in 0 until keyTimes.size - 1) {
                        if (animProgress >= keyTimes[k]) {
                            foundIdx = k
                        }
                    }
                    idx0 = foundIdx.coerceIn(0, values.size - 2)
                    idx1 = idx0 + 1
                    val t0 = keyTimes[idx0]
                    val t1 = keyTimes[idx1]
                    segProgress = if (t1 > t0) ((animProgress - t0) / (t1 - t0)).coerceIn(0f, 1f) else 0f
                } else {
                    val segCount = values.size - 1
                    val rawIdx = animProgress * segCount
                    idx0 = rawIdx.toInt().coerceIn(0, segCount - 1)
                    idx1 = idx0 + 1
                    segProgress = rawIdx - idx0
                }

                val v0 = values[idx0]
                val v1 = values[idx1]

                when (anim.keyPath) {
                    "transform.rotation.z" -> {
                        val r0 = (v0 as? Number)?.toFloat() ?: v0.toString().toFloatOrNull() ?: 0f
                        val r1 = (v1 as? Number)?.toFloat() ?: v1.toString().toFloatOrNull() ?: 0f
                        curRot += (r0 + (r1 - r0) * segProgress)
                    }
                    "position" -> {
                        val p0 = toPoint(v0)
                        val p1 = toPoint(v1)
                        if (p0 != null && p1 != null) {
                            curX = p0.x + (p1.x - p0.x) * segProgress
                            curY = p0.y + (p1.y - p0.y) * segProgress
                        }
                    }
                    "position.x" -> {
                        val x0 = (v0 as? Number)?.toFloat() ?: v0.toString().toFloatOrNull() ?: curX
                        val x1 = (v1 as? Number)?.toFloat() ?: v1.toString().toFloatOrNull() ?: curX
                        curX = x0 + (x1 - x0) * segProgress
                    }
                    "position.y" -> {
                        val y0 = (v0 as? Number)?.toFloat() ?: v0.toString().toFloatOrNull() ?: curY
                        val y1 = (v1 as? Number)?.toFloat() ?: v1.toString().toFloatOrNull() ?: curY
                        curY = y0 + (y1 - y0) * segProgress
                    }
                    "opacity" -> {
                        val o0 = (v0 as? Number)?.toFloat() ?: 1f
                        val o1 = (v1 as? Number)?.toFloat() ?: 1f
                        curOpacity *= (o0 + (o1 - o0) * segProgress).coerceIn(0f, 1f)
                    }
                }
            }
        }

        val evaluatedSublayers = layer.sublayers.map { sub ->
            interpolateLayer(sub, progress, lockedMap, unlockMap, timeSeconds)
        }

        return EvaluatedLayer(
            originalLayer = layer,
            bounds = Rect4f(curBx, curBy, curBw, curBh),
            position = Point2f(curX, curY),
            anchorPoint = layer.anchorPoint,
            opacity = curOpacity,
            rotationZ = curRot,
            scaleX = curScaleX,
            scaleY = curScaleY,
            hidden = curHidden,
            backgroundColor = layer.backgroundColor,
            contents = layer.contents,
            sublayers = evaluatedSublayers
        )
    }

    private fun toPoint(v: Any): Point2f? {
        if (v is Point2f) return v
        if (v is String) {
            val parts = v.trim().split("\\s+".toRegex())
            if (parts.size == 2) {
                val x = parts[0].toFloatOrNull()
                val y = parts[1].toFloatOrNull()
                if (x != null && y != null) return Point2f(x, y)
            }
        }
        return null
    }
}
