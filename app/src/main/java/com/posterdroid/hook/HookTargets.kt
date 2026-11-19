package com.posterdroid.hook

import android.os.Build

object HookTargets {
    val androidSdkInt: Int get() = Build.VERSION.SDK_INT
    val androidRelease: String get() = Build.VERSION.RELEASE

    const val KEYGUARD_STATE_CONTROLLER_CLASS = "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl"

    // samsung
    const val KEYGUARD_TOUCH_BASE_CLASS = "com.android.systemui.keyguard.animator.KeyguardTouchBase"
    const val KEYGUARD_TOUCH_ANIMATOR_CLASS = "com.android.systemui.keyguard.animator.KeyguardTouchAnimator"

    const val SEC_UNLOCK_CONTROLLER_CLASS = "com.android.systemui.keyguard.KeyguardSecLegacyUnlockAnimationControllerImpl"
    const val AOSP_UNLOCK_CONTROLLER_CLASS = "com.android.systemui.keyguard.KeyguardUnlockAnimationController"
    const val KEYGUARD_TRANSITION_INTERACTOR_CLASS = "com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor"

    // hyperOS
    const val HYPEROS_KEYGUARD_PANEL_CLASS = "com.android.keyguard.panel.KeyguardPanelViewController"
    const val HYPEROS_KEYGUARD_MEDIATOR_INJECTOR_CLASS = "com.android.keyguard.injector.KeyguardViewMediatorInjector"

    // AOSP
    const val SHADE_EXPANSION_EVENT_CLASS = "com.android.systemui.shade.ShadeExpansionChangeEvent"
    const val SHADE_EXPANSION_STATE_MANAGER_CLASS = "com.android.systemui.shade.ShadeExpansionStateManager"
    const val NOTIFICATION_PANEL_CLASS = "com.android.systemui.shade.NotificationPanelViewController"

    const val PROGRESS_DELTA_THRESHOLD = 0.005f
}
