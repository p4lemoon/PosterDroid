package com.posterdroid.caml.engine

import com.posterdroid.caml.math.CATransform3D
import com.posterdroid.caml.model.Point2f
import com.posterdroid.caml.model.Rect4f
import kotlin.math.max
import kotlin.math.min

/**
 * Coordinate space and time converter matching Apple Core Animation geometry model.
 *
 * Handles hierarchical transforms, anchor points (2D and Z), bounds.origin offsets,
 * sublayerTransform, geometryFlipped, and matrix inversion.
 */
object CACoordinateConverter {

    /**
     * Finds the path of layers from root down to targetLayer.
     * Returns null if targetLayer is not found in the root hierarchy.
     */
    fun findPathToRoot(targetLayer: PresentationLayer, root: PresentationLayer): List<PresentationLayer>? {
        if (targetLayer === root || targetLayer.id == root.id) {
            return listOf(root)
        }
        for (sub in root.sublayers) {
            val subPath = findPathToRoot(targetLayer, sub)
            if (subPath != null) {
                return listOf(root) + subPath
            }
        }
        return null
    }

    /**
     * Computes the 4x4 transform from layer's local coordinate space to its parent's coordinate space.
     *
     * Order of operations for a local point v:
     * 1. Shift by -anchorPoint in local bounds: (v - (bounds.origin + anchorPoint * bounds.size))
     * 2. Apply layer.transform
     * 3. Shift by +position in parent space: + position
     * 4. If parent has sublayerTransform, apply it around parent's anchor point.
     * 5. If parent is geometryFlipped, invert Y in parent's bounds.
     */
    fun computeLocalTransform(layer: PresentationLayer, parent: PresentationLayer? = null): CATransform3D {
        val anchorX = layer.bounds.x + layer.anchorPoint.x * layer.bounds.width
        val anchorY = layer.bounds.y + layer.anchorPoint.y * layer.bounds.height
        val anchorZ = layer.anchorPointZ

        // 1. Shift to anchor origin
        var m = CATransform3D.makeTranslation(-anchorX, -anchorY, -anchorZ)

        // 2. Layer 3D transform
        m = m * layer.transform

        // 3. Shift to layer position and zPosition in parent space
        m = m * CATransform3D.makeTranslation(layer.position.x, layer.position.y, layer.zPosition)

        // 4. Sublayer transform of parent (if present)
        if (parent != null && !parent.sublayerTransform.isIdentity()) {
            val parentAnchorX = parent.bounds.x + parent.anchorPoint.x * parent.bounds.width
            val parentAnchorY = parent.bounds.y + parent.anchorPoint.y * parent.bounds.height

            val toParentAnchor = CATransform3D.makeTranslation(-parentAnchorX, -parentAnchorY, 0f)
            val fromParentAnchor = CATransform3D.makeTranslation(parentAnchorX, parentAnchorY, 0f)

            m = m * toParentAnchor * parent.sublayerTransform * fromParentAnchor
        }

        // 5. Parent geometry flipped (macOS / CAML flipped coordinate system)
        if (parent?.originalLayer?.geometryFlipped == true) {
            val flip = CATransform3D(
                m22 = -1f,
                m42 = parent.bounds.height
            )
            m = m * flip
        }

        return m
    }

    /**
     * Computes the accumulated 4x4 transform from targetLayer's local coordinates up to root space.
     */
    fun computeWorldTransform(targetLayer: PresentationLayer, root: PresentationLayer): CATransform3D {
        val path = findPathToRoot(targetLayer, root) ?: return computeLocalTransform(targetLayer)
        if (path.size <= 1) return computeLocalTransform(targetLayer)

        var accumulated = CATransform3D.IDENTITY
        for (i in path.size - 1 downTo 1) {
            val current = path[i]
            val parent = path[i - 1]
            val local = computeLocalTransform(current, parent)
            accumulated = accumulated * local
        }
        return accumulated
    }

    /**
     * Converts a 2D point from one layer's coordinate space to another layer's coordinate space.
     * If layer is null, root (canvas) coordinate space is assumed.
     */
    fun convertPoint(
        point: Point2f,
        from: PresentationLayer?,
        to: PresentationLayer?,
        root: PresentationLayer
    ): Point2f {
        if (from === to) return point

        // 1. Transform from 'from' space to root space
        val pRoot = if (from != null && from !== root) {
            val worldM = computeWorldTransform(from, root)
            val (rx, ry, _) = worldM.transformPoint(point.x, point.y, 0f)
            Point2f(rx, ry)
        } else {
            point
        }

        // 2. Transform from root space to 'to' space
        if (to == null || to === root) {
            return pRoot
        }

        val toWorldM = computeWorldTransform(to, root)
        val invM = toWorldM.inverse() ?: return pRoot
        val (tx, ty, _) = invM.transformPoint(pRoot.x, pRoot.y, 0f)
        return Point2f(tx, ty)
    }

    /**
     * Converts a 2D bounding rectangle from one layer's coordinate space to another layer's coordinate space.
     * Transforms all 4 corners and computes the tight bounding box.
     */
    fun convertRect(
        rect: Rect4f,
        from: PresentationLayer?,
        to: PresentationLayer?,
        root: PresentationLayer
    ): Rect4f {
        if (from === to) return rect

        val p1 = convertPoint(Point2f(rect.x, rect.y), from, to, root)
        val p2 = convertPoint(Point2f(rect.x + rect.width, rect.y), from, to, root)
        val p3 = convertPoint(Point2f(rect.x, rect.y + rect.height), from, to, root)
        val p4 = convertPoint(Point2f(rect.x + rect.width, rect.y + rect.height), from, to, root)

        val minX = min(min(p1.x, p2.x), min(p3.x, p4.x))
        val maxX = max(max(p1.x, p2.x), max(p3.x, p4.x))
        val minY = min(min(p1.y, p2.y), min(p3.y, p4.y))
        val maxY = max(max(p1.y, p2.y), max(p3.y, p4.y))

        return Rect4f(minX, minY, maxX - minX, maxY - minY)
    }

    /**
     * Converts time between layers or between root clock and a layer according to CAMediaTiming:
     * childTime = (parentTime - beginTime) * speed + timeOffset
     * parentTime = (childTime - timeOffset) / speed + beginTime
     */
    fun convertTime(
        timeSeconds: Double,
        from: PresentationLayer?,
        to: PresentationLayer?,
        root: PresentationLayer
    ): Double {
        if (from === to) return timeSeconds

        // 1. Convert from 'from' to root time
        var rootTime = timeSeconds
        if (from != null && from !== root) {
            val fromPath = findPathToRoot(from, root)
            if (fromPath != null) {
                for (i in fromPath.size - 1 downTo 1) {
                    val layer = fromPath[i].originalLayer
                    val speed = if (layer.speed != 0.0) layer.speed else 1.0
                    rootTime = (rootTime - layer.timeOffset) / speed + layer.beginTime
                }
            }
        }

        // 2. Convert from root time down to 'to' layer time
        var targetTime = rootTime
        if (to != null && to !== root) {
            val toPath = findPathToRoot(to, root)
            if (toPath != null) {
                for (i in 1 until toPath.size) {
                    val layer = toPath[i].originalLayer
                    val speed = if (layer.speed != 0.0) layer.speed else 1.0
                    targetTime = (targetTime - layer.beginTime) * speed + layer.timeOffset
                }
            }
        }

        return targetTime
    }
}
