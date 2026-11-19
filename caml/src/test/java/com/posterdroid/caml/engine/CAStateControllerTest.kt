package com.posterdroid.caml.engine

import com.posterdroid.caml.model.*
import org.junit.Assert.*
import org.junit.Test

class CAStateControllerTest {

    private fun createTestTree(): CamlLayerTree {
        val root = StandardCALayer(
            id = "root",
            name = "root",
            bounds = Rect4f(0f, 0f, 1000f, 1000f),
            position = Point2f(0f, 0f)
        )
        val child = StandardCALayer(
            id = "layerA",
            name = "layerA",
            bounds = Rect4f(0f, 0f, 100f, 100f),
            position = Point2f(100f, 100f)
        )
        val treeRoot = StandardCALayer(
            id = "treeRoot",
            name = "treeRoot",
            bounds = Rect4f(0f, 0f, 1000f, 1000f),
            sublayers = listOf(child)
        )

        val lockedState = CamlState(
            name = "Locked",
            setValues = listOf(
                CamlStateSetValue(targetId = "layerA", keyPath = "position.x", value = 100f),
                CamlStateSetValue(targetId = "layerA", keyPath = "opacity", value = 0.2f)
            )
        )
        val unlockState = CamlState(
            name = "Unlock",
            setValues = listOf(
                CamlStateSetValue(targetId = "layerA", keyPath = "position.x", value = 300f),
                CamlStateSetValue(targetId = "layerA", keyPath = "opacity", value = 1.0f)
            )
        )

        return CamlLayerTree(
            rootLayer = treeRoot,
            states = listOf(lockedState, unlockState)
        )
    }

    @Test
    fun testNormalStateProgression() {
        val tree = createTestTree()
        val controller = CAStateController(tree)

        controller.transitionToState("Unlock")

        // Progress 0.0 -> fromState (Locked): posX = 100, opacity = 0.2
        val p0 = controller.evaluate(0.0f)
        val layerA0 = p0.findLayerById("layerA")!!
        assertEquals(100f, layerA0.position.x, 1e-3f)
        assertEquals(0.2f, layerA0.opacity, 1e-3f)

        // Progress 0.5 -> midpoint: posX = 200, opacity = 0.6
        val pMid = controller.evaluate(0.5f)
        val layerAMid = pMid.findLayerById("layerA")!!
        assertEquals(200f, layerAMid.position.x, 1e-3f)
        assertEquals(0.6f, layerAMid.opacity, 1e-3f)

        // Progress 1.0 -> toState (Unlock): posX = 300, opacity = 1.0
        val p1 = controller.evaluate(1.0f)
        val layerA1 = p1.findLayerById("layerA")!!
        assertEquals(300f, layerA1.position.x, 1e-3f)
        assertEquals(1.0f, layerA1.opacity, 1e-3f)
    }

    @Test
    fun testInterruptedTransitionReversal() {
        val tree = createTestTree()
        val controller = CAStateController(tree)

        controller.transitionToState("Unlock")

        // User drags halfway to progress 0.4:
        // posX: 100 + (300 - 100) * 0.4 = 180
        val pInterrupted = controller.evaluate(0.4f)
        val layerInterrupted = pInterrupted.findLayerById("layerA")!!
        assertEquals(180f, layerInterrupted.position.x, 1e-3f)

        // User releases / cancels drag: reverse transition back to Locked
        controller.transitionToState("Locked")

        // Progress 0.0 of the new reverse transition MUST start at the interrupted presentation position (180),
        // NOT jump to 100 or 300!
        val pReverseStart = controller.evaluate(0.0f)
        val layerReverseStart = pReverseStart.findLayerById("layerA")!!
        assertEquals("Must start at interrupted position without jump", 180f, layerReverseStart.position.x, 1e-3f)

        // Progress 0.5 of the rollback transition interpolates between 180 and 100: 180 + (100 - 180) * 0.5 = 140
        val pReverseMid = controller.evaluate(0.5f)
        val layerReverseMid = pReverseMid.findLayerById("layerA")!!
        assertEquals(140f, layerReverseMid.position.x, 1e-3f)

        // Progress 1.0 completes back at Locked: 100
        val pReverseEnd = controller.evaluate(1.0f)
        val layerReverseEnd = pReverseEnd.findLayerById("layerA")!!
        assertEquals(100f, layerReverseEnd.position.x, 1e-3f)
    }
}
