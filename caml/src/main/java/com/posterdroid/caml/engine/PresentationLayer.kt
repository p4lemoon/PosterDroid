package com.posterdroid.caml.engine

import com.posterdroid.caml.math.CATransform3D
import com.posterdroid.caml.model.CamlContents
import com.posterdroid.caml.model.CamlLayer
import com.posterdroid.caml.model.Color4f
import com.posterdroid.caml.model.Point2f
import com.posterdroid.caml.model.Rect4f

/**
 * Represents the presentation state of a CALayer at a specific instant,
 * incorporating model properties, state transitions, and active animations.
 */
data class PresentationLayer(
    val originalLayer: CamlLayer,
    val bounds: Rect4f,
    val position: Point2f,
    val anchorPoint: Point2f,
    val anchorPointZ: Float = originalLayer.anchorPointZ,
    val zPosition: Float,
    val transform: CATransform3D,
    val sublayerTransform: CATransform3D,
    val opacity: Float,
    val hidden: Boolean,
    val cornerRadius: Float,
    val masksToBounds: Boolean,
    val allowsGroupOpacity: Boolean = originalLayer.allowsGroupOpacity,
    val borderWidth: Float = originalLayer.borderWidth,
    val borderColor: Color4f? = originalLayer.borderColor,
    val shadowColor: Color4f? = originalLayer.shadowColor,
    val shadowOpacity: Float = originalLayer.shadowOpacity,
    val shadowOffset: Point2f = originalLayer.shadowOffset,
    val shadowRadius: Float = originalLayer.shadowRadius,
    val shadowPath: String? = originalLayer.shadowPath,
    val backgroundColor: Color4f?,
    val contents: CamlContents?,
    val contentsGravity: String = originalLayer.contentsGravity,
    val contentsRect: Rect4f = originalLayer.contentsRect,
    val mask: PresentationLayer? = null,
    val speed: Double = originalLayer.speed,
    val timeOffset: Double = originalLayer.timeOffset,
    val beginTime: Double = originalLayer.beginTime,
    val filterColorMatrix: FloatArray? = null,
    val filterContrast: Float? = null,
    val filters: List<com.posterdroid.caml.model.CamlFilter> = emptyList(),
    val sublayers: List<PresentationLayer>
) {
    val id: String? get() = originalLayer.id
    val name: String? get() = originalLayer.name

    fun findLayerById(targetId: String): PresentationLayer? {
        if (id == targetId) return this
        for (sub in sublayers) {
            val found = sub.findLayerById(targetId)
            if (found != null) return found
        }
        return null
    }

    fun collectAllLayers(): List<PresentationLayer> {
        val list = mutableListOf(this)
        for (sub in sublayers) {
            list.addAll(sub.collectAllLayers())
        }
        return list
    }

    val localMatrix: CATransform3D
        get() = CACoordinateConverter.computeLocalTransform(this)

    fun modelMatrix(root: PresentationLayer): CATransform3D =
        CACoordinateConverter.computeWorldTransform(this, root)

    fun convertPoint(point: Point2f, to: PresentationLayer?, root: PresentationLayer): Point2f =
        CACoordinateConverter.convertPoint(point, this, to, root)

    fun convertRect(rect: Rect4f, to: PresentationLayer?, root: PresentationLayer): Rect4f =
        CACoordinateConverter.convertRect(rect, this, to, root)

    fun convertTime(timeSeconds: Double, to: PresentationLayer?, root: PresentationLayer): Double =
        CACoordinateConverter.convertTime(timeSeconds, this, to, root)
}
