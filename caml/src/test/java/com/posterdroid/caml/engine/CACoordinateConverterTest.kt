package com.posterdroid.caml.engine

import com.posterdroid.caml.math.CATransform3D
import com.posterdroid.caml.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class CACoordinateConverterTest {

    private fun makeLayer(
        id: String,
        bounds: Rect4f = Rect4f(0f, 0f, 100f, 100f),
        position: Point2f = Point2f(0f, 0f),
        anchorPoint: Point2f = Point2f(0.5f, 0.5f),
        transform: CATransform3D = CATransform3D.IDENTITY,
        sublayerTransform: CATransform3D = CATransform3D.IDENTITY,
        speed: Double = 1.0,
        beginTime: Double = 0.0,
        timeOffset: Double = 0.0,
        sublayers: List<PresentationLayer> = emptyList()
    ): PresentationLayer {
        val modelLayer = StandardCALayer(
            id = id,
            name = id,
            bounds = bounds,
            position = position,
            anchorPoint = anchorPoint,
            transform = transform,
            sublayerTransform = sublayerTransform,
            speed = speed,
            beginTime = beginTime,
            timeOffset = timeOffset
        )
        return PresentationLayer(
            originalLayer = modelLayer,
            bounds = bounds,
            position = position,
            anchorPoint = anchorPoint,
            zPosition = 0f,
            transform = transform,
            sublayerTransform = sublayerTransform,
            opacity = 1f,
            hidden = false,
            cornerRadius = 0f,
            masksToBounds = false,
            allowsGroupOpacity = true,
            backgroundColor = null,
            contents = null,
            sublayers = sublayers
        )
    }

    @Test
    fun testLocalTransformAnchorPointAndPosition() {
        // Layer size 100x100, anchor (0.5, 0.5), placed at (150, 200)
        val layer = makeLayer("layer1", bounds = Rect4f(0f, 0f, 100f, 100f), position = Point2f(150f, 200f))

        val localM = CACoordinateConverter.computeLocalTransform(layer)

        // Anchor point (50, 50) must map directly to position (150, 200)
        val pAnchor = localM.transformPoint(50f, 50f, 0f)
        assertEquals(150f, pAnchor.first, 1e-4f)
        assertEquals(200f, pAnchor.second, 1e-4f)

        // Top-left corner (0, 0) must map to (100, 150)
        val pTopLeft = localM.transformPoint(0f, 0f, 0f)
        assertEquals(100f, pTopLeft.first, 1e-4f)
        assertEquals(150f, pTopLeft.second, 1e-4f)

        // Bottom-right corner (100, 100) must map to (200, 250)
        val pBottomRight = localM.transformPoint(100f, 100f, 0f)
        assertEquals(200f, pBottomRight.first, 1e-4f)
        assertEquals(250f, pBottomRight.second, 1e-4f)
    }

    @Test
    fun testRotationAroundAnchorPoint() {
        // 90 deg rotation around anchor point (0.5, 0.5)
        val rot90 = CATransform3D.makeRotationZ((PI / 2.0).toFloat())
        val layer = makeLayer(
            "rotatedLayer",
            bounds = Rect4f(0f, 0f, 100f, 100f),
            position = Point2f(200f, 200f),
            anchorPoint = Point2f(0.5f, 0.5f),
            transform = rot90
        )

        val localM = CACoordinateConverter.computeLocalTransform(layer)

        // Anchor point (50, 50) remains fixed at position (200, 200)
        val pAnchor = localM.transformPoint(50f, 50f, 0f)
        assertEquals(200f, pAnchor.first, 1e-3f)
        assertEquals(200f, pAnchor.second, 1e-3f)

        // Top center (50, 0) (dx=0, dy=-50) rotated 90 deg clockwise -> dx=50, dy=0 -> (250, 200)
        val pTopCenter = localM.transformPoint(50f, 0f, 0f)
        assertEquals(250f, pTopCenter.first, 1e-3f)
        assertEquals(200f, pTopCenter.second, 1e-3f)
    }

    @Test
    fun testConvertPointBetweenNestedLayers() {
        // Root: 1000x1000 at (0, 0)
        // Child: 200x200 placed at (300, 400) inside Root
        // Grandchild: 50x50 placed at (50, 60) inside Child
        val grandchild = makeLayer("grandchild", bounds = Rect4f(0f, 0f, 50f, 50f), position = Point2f(50f, 60f))
        val child = makeLayer(
            "child",
            bounds = Rect4f(0f, 0f, 200f, 200f),
            position = Point2f(300f, 400f),
            sublayers = listOf(grandchild)
        )
        val root = makeLayer("root", bounds = Rect4f(0f, 0f, 1000f, 1000f), position = Point2f(0f, 0f), sublayers = listOf(child))

        // Convert grandchild anchor (25, 25) to root space
        // Grandchild anchor (25, 25) is at Child position (50, 60)
        // Child bounds are 200x200 with anchor (100, 100) at Root (300, 400)
        // So Child (0, 0) is at Root (200, 300)
        // Grandchild position (50, 60) is at Root: 200 + 50 = 250, 300 + 60 = 360
        val pGrandchildAnchor = Point2f(25f, 25f)
        val pRoot = CACoordinateConverter.convertPoint(pGrandchildAnchor, grandchild, root, root)

        assertEquals(250f, pRoot.x, 1e-3f)
        assertEquals(360f, pRoot.y, 1e-3f)

        // Convert back from Root to Grandchild
        val pRestored = CACoordinateConverter.convertPoint(pRoot, root, grandchild, root)
        assertEquals(25f, pRestored.x, 1e-3f)
        assertEquals(25f, pRestored.y, 1e-3f)
    }

    @Test
    fun testConvertPointBetweenSiblingLayers() {
        val childA = makeLayer("childA", bounds = Rect4f(0f, 0f, 100f, 100f), position = Point2f(100f, 100f))
        val childB = makeLayer("childB", bounds = Rect4f(0f, 0f, 100f, 100f), position = Point2f(300f, 300f))
        val root = makeLayer("root", bounds = Rect4f(0f, 0f, 1000f, 1000f), sublayers = listOf(childA, childB))

        // Point at childA anchor (50, 50) is at root (100, 100)
        // childB anchor (50, 50) is at root (300, 300), so childB (0, 0) is at root (250, 250)
        // Root (100, 100) in childB space is (100 - 250, 100 - 250) = (-150, -150)
        val pA = Point2f(50f, 50f)
        val pB = CACoordinateConverter.convertPoint(pA, childA, childB, root)

        assertEquals(-150f, pB.x, 1e-3f)
        assertEquals(-150f, pB.y, 1e-3f)
    }

    @Test
    fun testConvertRect() {
        val child = makeLayer(
            "child",
            bounds = Rect4f(0f, 0f, 200f, 200f),
            position = Point2f(200f, 200f),
            transform = CATransform3D.makeScale(2f, 2f) // scaled 2x
        )
        val root = makeLayer("root", bounds = Rect4f(0f, 0f, 1000f, 1000f), sublayers = listOf(child))

        // Rect in child: (0, 0, 100, 100)
        // Scaled 2x around anchor (100, 100) placed at root (200, 200)
        // Local (100, 100) -> root (200, 200)
        // Local (0, 0): offset from anchor is (-100, -100) * 2 = (-200, -200) -> root (0, 0)
        // Local (100, 100) -> offset is (0, 0) -> root (200, 200)
        // So rect (0, 0, 100, 100) maps to root rect (0, 0, 200, 200)
        val rectInChild = Rect4f(0f, 0f, 100f, 100f)
        val rectInRoot = CACoordinateConverter.convertRect(rectInChild, child, root, root)

        assertEquals(0f, rectInRoot.x, 1e-2f)
        assertEquals(0f, rectInRoot.y, 1e-2f)
        assertEquals(200f, rectInRoot.width, 1e-2f)
        assertEquals(200f, rectInRoot.height, 1e-2f)
    }

    @Test
    fun testNestedConvertTime() {
        // Child with speed 2.0, beginTime 1.0, timeOffset 0.5
        val child = makeLayer("child", speed = 2.0, beginTime = 1.0, timeOffset = 0.5)
        val root = makeLayer("root", sublayers = listOf(child))

        // t_child = (t_parent - beginTime) * speed + timeOffset
        // At parent time 3.0: (3.0 - 1.0) * 2.0 + 0.5 = 4.5
        val childTime = CACoordinateConverter.convertTime(3.0, root, child, root)
        assertEquals(4.5, childTime, 1e-5)

        // In reverse: childTime 4.5 -> parentTime 3.0
        val restoredParentTime = CACoordinateConverter.convertTime(4.5, child, root, root)
        assertEquals(3.0, restoredParentTime, 1e-5)
    }
}
