package com.posterdroid.caml.engine

import com.posterdroid.caml.model.CamlLayerTree
import com.posterdroid.caml.model.CamlState

/**
 * Manages layer states and interactive transitions matching Apple CAStateController.
 *
 * Supports:
 * - Direct transition progression between states
 * - Interrupted transition continuation: preserves the current presentation state
 *   when a gesture is cancelled or reversed mid-flight, eliminating visual jumps
 * - Continuous animations across state changes
 */
class CAStateController(
    val tree: CamlLayerTree,
    private val engine: CoreAnimationEngine = CoreAnimationEngine()
) {

    var currentStateName: String = "Locked"
        private set

    var previousStateName: String = "Locked"
        private set

    var lastPresentation: PresentationLayer? = null
        private set

    private var interruptedPresentation: PresentationLayer? = null

    init {
        // Evaluate initial locked state
        lastPresentation = engine.computePresentation(
            tree = tree,
            progress = 0f,
            timeSeconds = 0f,
            fromStateName = "Locked",
            toStateName = "Locked"
        )
    }

    /**
     * Changes target state. If an existing transition was in flight, captures
     * the current presentation state so the new transition starts smoothly from the
     * interrupted point on screen.
     */
    fun transitionToState(targetStateName: String) {
        if (targetStateName == currentStateName) return

        // Capture current presentation as the baseline for the new transition
        interruptedPresentation = lastPresentation
        previousStateName = currentStateName
        currentStateName = targetStateName
    }

    /**
     * Resets state without transition history.
     */
    fun setStateImmediate(stateName: String) {
        previousStateName = stateName
        currentStateName = stateName
        interruptedPresentation = null
    }

    /**
     * Evaluates the presentation tree at the given transition progress [0.0..1.0] and timeSeconds.
     */
    fun evaluate(
        progress: Float,
        timeSeconds: Float = 0f,
        tiltX: Float = 0f,
        tiltY: Float = 0f,
        fallbackParallaxScale: Float = 0f
    ): PresentationLayer {
        val pres = engine.computePresentation(
            tree = tree,
            progress = progress,
            timeSeconds = timeSeconds,
            tiltX = tiltX,
            tiltY = tiltY,
            fallbackParallaxScale = fallbackParallaxScale,
            fromStateName = previousStateName,
            toStateName = currentStateName,
            interruptedPresentation = interruptedPresentation
        )
        lastPresentation = pres

        // If transition has fully landed at destination (progress >= 1.0), clear the interrupted baseline
        if (progress >= 1.0f) {
            interruptedPresentation = null
            previousStateName = currentStateName
        }

        return pres
    }
}
