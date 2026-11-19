package com.posterdroid.hook.source

import com.posterdroid.core.ProgressEvent
import com.posterdroid.core.UnlockState
import com.posterdroid.hook.HookTargets
import de.robv.android.xposed.XposedBridge
import kotlin.math.abs

class ProgressDispatcher(
    private val deltaThreshold: Float = HookTargets.PROGRESS_DELTA_THRESHOLD,
    private val onEvent: (ProgressEvent) -> Unit
) {
    private var lastDispatchedProgress = -1f
    private var lastDispatchedState: UnlockState? = null

    @Synchronized
    fun dispatch(rawProgress: Float, state: UnlockState) {
        val clampedProgress = rawProgress.coerceIn(0f, 1f)

        val isStateChanged = state != lastDispatchedState
        val isSignificantDelta = abs(clampedProgress - lastDispatchedProgress) >= deltaThreshold
        val isBoundary = (clampedProgress == 0f || clampedProgress == 1f) && clampedProgress != lastDispatchedProgress

        if (isStateChanged || isSignificantDelta || isBoundary) {
            lastDispatchedProgress = clampedProgress
            lastDispatchedState = state

            val event = ProgressEvent(progress = clampedProgress, state = state)
            if (isStateChanged || isBoundary) {
                val formatted = String.format(java.util.Locale.US, "%.4f", clampedProgress)
                XposedBridge.log("[PosterDroid-Progress] State: $state, Progress: $formatted")
            }
            onEvent(event)
        }
    }

    val currentState: UnlockState? get() = lastDispatchedState
    val currentProgress: Float get() = lastDispatchedProgress
}
