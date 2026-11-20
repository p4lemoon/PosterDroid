package com.posterdroid.caml.engine

import com.posterdroid.caml.math.CAMediaTiming
import com.posterdroid.caml.math.CAMediaTimingFunction
import com.posterdroid.caml.math.CASpringTiming
import com.posterdroid.caml.math.CATransform3D
import com.posterdroid.caml.model.*

/**
 * Pure function Core Animation engine.
 *
 * Computes the Presentation Tree from a model layer tree, state transitions (driven by unlock progress),
 * and active continuous animations (driven by timeSeconds).
 */
class CoreAnimationEngine {

    fun computePresentation(
        tree: CamlLayerTree,
        progress: Float,
        timeSeconds: Float = 0f,
        tiltX: Float = 0f,
        tiltY: Float = 0f,
        fallbackParallaxScale: Float = 0f,
        fromStateName: String = "Locked",
        toStateName: String = "Unlock",
        interruptedPresentation: PresentationLayer? = null,
        isDarkMode: Boolean = false,
        orientation: String = "PortraitUp"
    ): PresentationLayer {
        val clampedProgress = progress.coerceIn(0f, 1f)

        // 1. Resolve State Transition
        val fromState = tree.findState(fromStateName, isDarkMode = isDarkMode, orientation = orientation)
            ?: tree.findState("Locked", isDarkMode = isDarkMode, orientation = orientation)
        val toState = tree.findState(toStateName, isDarkMode = isDarkMode, orientation = orientation)
            ?: tree.findState("Unlock", isDarkMode = isDarkMode, orientation = orientation)

        val transition = tree.stateTransitions.firstOrNull { t ->
            val fromMatch = t.fromState == "*" ||
                    t.fromState.equals(fromStateName, ignoreCase = true) ||
                    (fromState != null && t.fromState.equals(fromState.name, ignoreCase = true)) ||
                    (fromStateName.startsWith("Lock", ignoreCase = true) && t.fromState.contains("Lock", ignoreCase = true))
            val toMatch = t.toState == "*" ||
                    t.toState.equals(toStateName, ignoreCase = true) ||
                    (toState != null && t.toState.equals(toState.name, ignoreCase = true)) ||
                    ((toStateName.startsWith("Unlock", ignoreCase = true) || toStateName.startsWith("Home", ignoreCase = true)) &&
                            (t.toState.contains("Unlock", ignoreCase = true) || t.toState.contains("Home", ignoreCase = true)))
            fromMatch && toMatch
        }

        // Presentation progress directly drives state interpolation between fromState (0.0) and toState (1.0).
        val effectiveProgress = clampedProgress

        val fromMap = fromState?.setValues?.groupBy { it.targetId } ?: emptyMap()
        val toMap = toState?.setValues?.groupBy { it.targetId } ?: emptyMap()
        val interruptedMap = interruptedPresentation?.collectAllLayers()?.associateBy { it.id ?: "" } ?: emptyMap()
        val parallaxMap = tree.parallaxGroups.groupBy { it.layerName }

        return evaluateLayer(
            layer = tree.rootLayer,
            progress = effectiveProgress,
            fromMap = fromMap,
            toMap = toMap,
            timeSeconds = timeSeconds,
            interruptedMap = interruptedMap,
            parallaxMap = parallaxMap,
            tiltX = tiltX,
            tiltY = tiltY,
            isRoot = true,
            fallbackParallaxScale = fallbackParallaxScale
        )
    }

    private fun evaluateLayer(
        layer: CamlLayer,
        progress: Float,
        fromMap: Map<String, List<CamlStateSetValue>>,
        toMap: Map<String, List<CamlStateSetValue>>,
        timeSeconds: Float,
        interruptedMap: Map<String, PresentationLayer> = emptyMap(),
        parallaxMap: Map<String, List<WallpaperParallaxGroup>> = emptyMap(),
        tiltX: Float = 0f,
        tiltY: Float = 0f,
        isRoot: Boolean = false,
        fallbackParallaxScale: Float = 0f
    ): PresentationLayer {
        val layerId = layer.id
        val layerName = layer.name
        val idCandidates = listOfNotNull(
            layerId,
            layerName,
            layerName?.let { if (!it.startsWith("#")) "#$it" else null }
        ).distinct()
        val fromValues = idCandidates.flatMap { fromMap[it] ?: emptyList() }
        val toValues = idCandidates.flatMap { toMap[it] ?: emptyList() }

        // 1. Base Geometry & Visual Properties
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

        var zPos0 = layer.zPosition
        var zPos1 = zPos0

        var opacity0 = layer.opacity
        var opacity1 = opacity0

        var shadowOpacity0 = layer.shadowOpacity
        var shadowOpacity1 = shadowOpacity0

        var rotX0 = 0f
        var rotX1 = 0f
        var rotY0 = 0f
        var rotY1 = 0f
        var rotZ0 = layer.transform.rotationZ
        var rotZ1 = rotZ0

        var scaleX0 = layer.transform.scaleX
        var scaleY0 = layer.transform.scaleY
        var scaleX1 = scaleX0
        var scaleY1 = scaleY0

        var cornerRadius0 = layer.cornerRadius
        var cornerRadius1 = cornerRadius0

        var hidden0 = layer.hidden
        var hidden1 = hidden0

        var anchorPoint0 = layer.anchorPoint
        var anchorPoint1 = anchorPoint0

        var bgColor0 = layer.backgroundColor
        var bgColor1 = bgColor0

        var subScaleX0 = 1f
        var subScaleY0 = 1f
        var subScaleX1 = 1f
        var subScaleY1 = 1f

        var colorMatrix0: FloatArray? = (layer.filters.firstOrNull { it is CamlFilter.ColorMatrix } as? CamlFilter.ColorMatrix)?.matrix
        var colorMatrix1: FloatArray? = colorMatrix0

        var contrast0: Float? = (layer.filters.firstOrNull { it is CamlFilter.ColorContrast } as? CamlFilter.ColorContrast)?.amount
        var contrast1: Float? = contrast0

        var transform0 = layer.transform
        var transform1 = layer.transform

        // If transitioning from an interrupted presentation state, start from the exact current values on screen
        val interrupted = layerId?.let { interruptedMap[it] }
        if (interrupted != null) {
            posX0 = interrupted.position.x
            posY0 = interrupted.position.y
            bX0 = interrupted.bounds.x
            bY0 = interrupted.bounds.y
            bW0 = interrupted.bounds.width
            bH0 = interrupted.bounds.height
            zPos0 = interrupted.zPosition
            opacity0 = interrupted.opacity
            rotZ0 = interrupted.transform.rotationZ
            scaleX0 = interrupted.transform.scaleX
            scaleY0 = interrupted.transform.scaleY
            cornerRadius0 = interrupted.cornerRadius
            hidden0 = interrupted.hidden
            anchorPoint0 = interrupted.anchorPoint
            bgColor0 = interrupted.backgroundColor
            transform0 = interrupted.transform
        } else {
            // Apply fromState values (at progress = 0.0)
            for (sv in fromValues) {
                when (sv.keyPath) {
                    "transform" -> sv.asTransform()?.let { transform0 = it }
                    "position.x" -> sv.asFloat()?.let { posX0 = it }
                    "position.y" -> sv.asFloat()?.let { posY0 = it }
                    "position" -> sv.asPoint()?.let { posX0 = it.x; posY0 = it.y }
                    "bounds.origin.x" -> sv.asFloat()?.let { bX0 = it }
                    "bounds.origin.y" -> sv.asFloat()?.let { bY0 = it }
                    "bounds.size.width" -> sv.asFloat()?.let { bW0 = it }
                    "bounds.size.height" -> sv.asFloat()?.let { bH0 = it }
                    "bounds" -> sv.asRect()?.let { bX0 = it.x; bY0 = it.y; bW0 = it.width; bH0 = it.height }
                    "zPosition" -> sv.asFloat()?.let { zPos0 = it }
                    "opacity" -> sv.asFloat()?.let { opacity0 = it }
                    "shadowOpacity" -> sv.asFloat()?.let { shadowOpacity0 = it }
                    "transform.rotation.x" -> sv.asFloat()?.let { rotX0 = it }
                    "transform.rotation.y" -> sv.asFloat()?.let { rotY0 = it }
                    "transform.rotation.z" -> sv.asFloat()?.let { rotZ0 = it }
                    "transform.scale.x" -> sv.asFloat()?.let { scaleX0 = it }
                    "transform.scale.y" -> sv.asFloat()?.let { scaleY0 = it }
                    "transform.scale", "transform.scale.xy" -> sv.asFloat()?.let { scaleX0 = it; scaleY0 = it }
                    "sublayerTransform.scale.xy" -> sv.asFloat()?.let { subScaleX0 = it; subScaleY0 = it }
                    "cornerRadius" -> sv.asFloat()?.let { cornerRadius0 = it }
                    "hidden" -> hidden0 = sv.value == true || sv.value == "1" || sv.value == "true"
                    "anchorPoint" -> sv.asPoint()?.let { anchorPoint0 = it }
                    "backgroundColor" -> sv.asColor()?.let { bgColor0 = it }
                    "filters.colorMatrix.inputColorMatrix" -> sv.asColorMatrix()?.let { colorMatrix0 = it }
                    "filters.colorContrast.inputAmount" -> sv.asFloat()?.let { contrast0 = it }
                }
            }
        }

        // Apply toState values (at progress = 1.0)
        for (sv in toValues) {
            when (sv.keyPath) {
                "transform" -> sv.asTransform()?.let { transform1 = it }
                "position.x" -> sv.asFloat()?.let { posX1 = it }
                "position.y" -> sv.asFloat()?.let { posY1 = it }
                "position" -> sv.asPoint()?.let { posX1 = it.x; posY1 = it.y }
                "bounds.origin.x" -> sv.asFloat()?.let { bX1 = it }
                "bounds.origin.y" -> sv.asFloat()?.let { bY1 = it }
                "bounds.size.width" -> sv.asFloat()?.let { bW1 = it }
                "bounds.size.height" -> sv.asFloat()?.let { bH1 = it }
                "bounds" -> sv.asRect()?.let { bX1 = it.x; bY1 = it.y; bW1 = it.width; bH1 = it.height }
                "zPosition" -> sv.asFloat()?.let { zPos1 = it }
                "opacity" -> sv.asFloat()?.let { opacity1 = it }
                "shadowOpacity" -> sv.asFloat()?.let { shadowOpacity1 = it }
                "transform.rotation.x" -> sv.asFloat()?.let { rotX1 = it }
                "transform.rotation.y" -> sv.asFloat()?.let { rotY1 = it }
                "transform.rotation.z" -> sv.asFloat()?.let { rotZ1 = it }
                "transform.scale.x" -> sv.asFloat()?.let { scaleX1 = it }
                "transform.scale.y" -> sv.asFloat()?.let { scaleY1 = it }
                "transform.scale", "transform.scale.xy" -> sv.asFloat()?.let { scaleX1 = it; scaleY1 = it }
                "sublayerTransform.scale.xy" -> sv.asFloat()?.let { subScaleX1 = it; subScaleY1 = it }
                "cornerRadius" -> sv.asFloat()?.let { cornerRadius1 = it }
                "hidden" -> hidden1 = sv.value == true || sv.value == "1" || sv.value == "true"
                "anchorPoint" -> sv.asPoint()?.let { anchorPoint1 = it }
                "backgroundColor" -> sv.asColor()?.let { bgColor1 = it }
                "filters.colorMatrix.inputColorMatrix" -> sv.asColorMatrix()?.let { colorMatrix1 = it }
                "filters.colorContrast.inputAmount" -> sv.asFloat()?.let { contrast1 = it }
            }
        }

        // 2. State Lerp by progress
        var curX = posX0 + (posX1 - posX0) * progress
        var curY = posY0 + (posY1 - posY0) * progress

        val curBx = bX0 + (bX1 - bX0) * progress
        val curBy = bY0 + (bY1 - bY0) * progress
        val curBw = bW0 + (bW1 - bW0) * progress
        val curBh = bH0 + (bH1 - bH0) * progress

        var curZ = zPos0 + (zPos1 - zPos0) * progress
        var curOpacity = (opacity0 + (opacity1 - opacity0) * progress).coerceIn(0f, 1f)
        val curShadowOpacity = (shadowOpacity0 + (shadowOpacity1 - shadowOpacity0) * progress).coerceIn(0f, 1f)

        var curRotX = rotX0 + (rotX1 - rotX0) * progress
        var curRotY = rotY0 + (rotY1 - rotY0) * progress
        var curRotZ = rotZ0 + (rotZ1 - rotZ0) * progress

        var curScaleX = scaleX0 + (scaleX1 - scaleX0) * progress
        var curScaleY = scaleY0 + (scaleY1 - scaleY0) * progress
        val curCornerRadius = cornerRadius0 + (cornerRadius1 - cornerRadius0) * progress
        val curHidden = if (progress >= 0.5f) hidden1 else hidden0

        val curSubScaleX = subScaleX0 + (subScaleX1 - subScaleX0) * progress
        val curSubScaleY = subScaleY0 + (subScaleY1 - subScaleY0) * progress

        val curColorMatrix: FloatArray? = if (colorMatrix0 != null || colorMatrix1 != null) {
            val m0 = colorMatrix0 ?: FloatArray(20) { if (it % 6 == 0) 1f else 0f }
            val m1 = colorMatrix1 ?: FloatArray(20) { if (it % 6 == 0) 1f else 0f }
            FloatArray(20) { i -> m0[i] + (m1[i] - m0[i]) * progress }
        } else null

        val curContrast: Float? = if (contrast0 != null || contrast1 != null) {
            val c0 = contrast0 ?: 1.0f
            val c1 = contrast1 ?: 1.0f
            c0 + (c1 - c0) * progress
        } else null

        val curAnchorX = anchorPoint0.x + (anchorPoint1.x - anchorPoint0.x) * progress
        val curAnchorY = anchorPoint0.y + (anchorPoint1.y - anchorPoint0.y) * progress

        val curBgColor: Color4f? = if (bgColor0 != null || bgColor1 != null) {
            val c0 = bgColor0 ?: Color4f(0f, 0f, 0f, 0f)
            val c1 = bgColor1 ?: Color4f(0f, 0f, 0f, 0f)
            Color4f(
                r = c0.r + (c1.r - c0.r) * progress,
                g = c0.g + (c1.g - c0.g) * progress,
                b = c0.b + (c1.b - c0.b) * progress,
                a = (c0.a + (c1.a - c0.a) * progress).coerceIn(0f, 1f)
            )
        } else null

        // 3. Continuous Animations at timeSeconds (Basic, Keyframe, Spring, Group)
        if (timeSeconds >= 0f && layer.animations.isNotEmpty()) {
            val baseMap = mapOf<String, Any?>(
                "position.x" to curX,
                "position.y" to curY,
                "position" to Point2f(curX, curY),
                "transform.rotation.z" to curRotZ,
                "transform.rotation.x" to curRotX,
                "transform.rotation.y" to curRotY,
                "transform.scale.x" to curScaleX,
                "transform.scale.y" to curScaleY,
                "opacity" to curOpacity,
                "zPosition" to curZ
            )

            val animValues = CoreAnimationEvaluator.compositeAnimations(layer.animations, timeSeconds.toDouble(), baseMap)
            for ((kp, eval) in animValues) {
                when (kp) {
                    "transform.rotation.z" -> CAKeyframeEvaluator.toFloat(eval)?.let { curRotZ = it }
                    "transform.rotation.x" -> CAKeyframeEvaluator.toFloat(eval)?.let { curRotX = it }
                    "transform.rotation.y" -> CAKeyframeEvaluator.toFloat(eval)?.let { curRotY = it }
                    "transform.scale.x" -> CAKeyframeEvaluator.toFloat(eval)?.let { curScaleX = it }
                    "transform.scale.y" -> CAKeyframeEvaluator.toFloat(eval)?.let { curScaleY = it }
                    "transform.scale" -> CAKeyframeEvaluator.toFloat(eval)?.let { curScaleX = it; curScaleY = it }
                    "position.x" -> CAKeyframeEvaluator.toFloat(eval)?.let { curX = it }
                    "position.y" -> CAKeyframeEvaluator.toFloat(eval)?.let { curY = it }
                    "position" -> CAKeyframeEvaluator.toPoint(eval)?.let { curX = it.x; curY = it.y }
                    "opacity" -> CAKeyframeEvaluator.toFloat(eval)?.let { curOpacity = it.coerceIn(0f, 1f) }
                    "zPosition" -> CAKeyframeEvaluator.toFloat(eval)?.let { curZ = it }
                }
            }
        }

        // 3.5 Apply Gyroscope Parallax (wallpaperParallaxGroups or subtle fallback)
        val pGroups = (layer.name?.let { parallaxMap[it] } ?: layerId?.let { parallaxMap[it] })
        if (pGroups != null) {
            for (group in pGroups) {
                val tilt = if (group.axis.equals("y", ignoreCase = true)) tiltY else tiltX
                val t = ((tilt + 1f) / 2f).coerceIn(0f, 1f)
                val offset = group.mapMinTo + (group.mapMaxTo - group.mapMinTo) * t
                when (group.keyPath) {
                    "position.x" -> curX += offset
                    "position.y" -> curY += offset
                    "transform.rotation.y" -> curRotY += offset
                    "transform.rotation.x" -> curRotX += offset
                    "transform.rotation.z" -> curRotZ += offset
                    "transform.scale.x" -> curScaleX += offset
                    "transform.scale.y" -> curScaleY += offset
                    "transform.scale" -> { curScaleX += offset; curScaleY += offset }
                    "opacity" -> curOpacity = (curOpacity + offset).coerceIn(0f, 1f)
                    "zPosition" -> curZ += offset
                }
            }
        } else if (isRoot && parallaxMap.isEmpty() && fallbackParallaxScale != 0f) {
            curX += tiltX * fallbackParallaxScale
            curY += tiltY * fallbackParallaxScale
        }

        // 4. Construct 4x4 Transforms
        // Combine matrix interpolation (if full 4x4 transform animated) with perspective (m34),
        // local scale, and 3D rotations (Euler X, Y, Z)
        val baseTransform = if (transform0 != transform1) {
            transform0.interpolate(transform1, progress)
        } else {
            (if (layer.transform.m34 != 0f) CATransform3D.makePerspective(layer.transform.m34) else CATransform3D.IDENTITY)
        }

        val finalTransform = baseTransform
            .scale(curScaleX, curScaleY)
            .rotateX(curRotX)
            .rotateY(curRotY)
            .rotateZ(curRotZ)

        // Sublayer transform (e.g. sublayerTransform.scale.xy)
        val finalSublayerTransform = if (curSubScaleX != 1f || curSubScaleY != 1f) {
            CATransform3D.makeScale(curSubScaleX, curSubScaleY)
        } else {
            layer.sublayerTransform
        }

        // 5. Recursively Evaluate Sublayers & Mask
        val evaluatedSublayers = layer.sublayers.map { sub ->
            evaluateLayer(
                layer = sub,
                progress = progress,
                fromMap = fromMap,
                toMap = toMap,
                timeSeconds = timeSeconds,
                interruptedMap = interruptedMap,
                parallaxMap = parallaxMap,
                tiltX = tiltX,
                tiltY = tiltY,
                isRoot = false,
                fallbackParallaxScale = fallbackParallaxScale
            )
        }
        val sublayerOrder = (if (progress >= 0.5f) toValues else fromValues)
            .firstOrNull { it.keyPath == "sublayers" }
            ?.value as? List<*>
        val sortedSublayers = if (sublayerOrder != null && sublayerOrder.isNotEmpty()) {
            val orderMap = sublayerOrder.mapIndexedNotNull { idx, item ->
                (item as? String)?.let { it to idx }
            }.toMap()
            evaluatedSublayers.sortedBy { sub ->
                val id1 = sub.originalLayer.id
                val id2 = sub.originalLayer.name
                val id3 = id2?.let { if (!it.startsWith("#")) "#$it" else null }
                orderMap[id1] ?: orderMap[id2] ?: orderMap[id3] ?: 999
            }
        } else {
            evaluatedSublayers
        }
        val evaluatedMask = layer.mask?.let {
            evaluateLayer(
                layer = it,
                progress = progress,
                fromMap = fromMap,
                toMap = toMap,
                timeSeconds = timeSeconds,
                interruptedMap = interruptedMap,
                parallaxMap = parallaxMap,
                tiltX = tiltX,
                tiltY = tiltY,
                isRoot = false,
                fallbackParallaxScale = fallbackParallaxScale
            )
        }

        return PresentationLayer(
            originalLayer = layer,
            bounds = Rect4f(curBx, curBy, curBw, curBh),
            position = Point2f(curX, curY),
            anchorPoint = Point2f(curAnchorX, curAnchorY),
            anchorPointZ = layer.anchorPointZ,
            zPosition = curZ,
            transform = finalTransform,
            sublayerTransform = finalSublayerTransform,
            opacity = curOpacity,
            hidden = curHidden,
            cornerRadius = curCornerRadius,
            masksToBounds = layer.masksToBounds,
            allowsGroupOpacity = layer.allowsGroupOpacity,
            borderWidth = layer.borderWidth,
            borderColor = layer.borderColor,
            shadowColor = layer.shadowColor,
            shadowOpacity = curShadowOpacity,
            shadowOffset = layer.shadowOffset,
            shadowRadius = layer.shadowRadius,
            shadowPath = layer.shadowPath,
            backgroundColor = curBgColor,
            contents = layer.contents,
            contentsGravity = layer.contentsGravity,
            contentsRect = layer.contentsRect,
            mask = evaluatedMask,
            filterColorMatrix = curColorMatrix,
            filterContrast = curContrast,
            filters = layer.filters,
            sublayers = sortedSublayers
        )
    }
}
