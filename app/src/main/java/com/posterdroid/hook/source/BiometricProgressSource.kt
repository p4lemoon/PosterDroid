package com.posterdroid.hook.source

import com.posterdroid.core.UnlockState
import com.posterdroid.hook.HookTargets
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class BiometricProgressSource : ProgressSource {
    override val name: String = "BiometricProgressSource"

    override fun attach(classLoader: ClassLoader, dispatcher: ProgressDispatcher) {
        // 1. Samsung KeyguardSecLegacyUnlockAnimationControllerImpl
        try {
            val secClass = classLoader.loadClass(HookTargets.SEC_UNLOCK_CONTROLLER_CLASS)

            XposedHelpers.findAndHookMethod(
                secClass,
                "setSurfaceBehindAppearAmount",
                java.lang.Float.TYPE,
                java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val amount = (param.args[0] as? Number)?.toFloat() ?: return
                            dispatcher.dispatch(amount, UnlockState.UNLOCKING)
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                secClass,
                "notifyFinishedKeyguardExitAnimation",
                java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val cancelled = param.args[0] as? Boolean ?: false
                            if (cancelled) {
                                dispatcher.dispatch(0f, UnlockState.CANCELLED)
                                dispatcher.dispatch(0f, UnlockState.LOCKED)
                            } else {
                                dispatcher.dispatch(1f, UnlockState.UNLOCKED)
                            }
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )

            XposedBridge.log("[PosterDroid] Attached $name (SecLegacyUnlockAnimationController)")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook SecLegacyUnlockAnimationController in $name: ${t.message}")
        }

        // 2. AOSP KeyguardUnlockAnimationController
        try {
            val aospClass = classLoader.loadClass(HookTargets.AOSP_UNLOCK_CONTROLLER_CLASS)

            XposedHelpers.findAndHookMethod(
                aospClass,
                "notifyFinishedKeyguardExitAnimation",
                java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val cancelled = param.args[0] as? Boolean ?: false
                            if (cancelled) {
                                dispatcher.dispatch(0f, UnlockState.CANCELLED)
                                dispatcher.dispatch(0f, UnlockState.LOCKED)
                            } else {
                                dispatcher.dispatch(1f, UnlockState.UNLOCKED)
                            }
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )

            XposedBridge.log("[PosterDroid] Attached $name (KeyguardUnlockAnimationController)")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook KeyguardUnlockAnimationController in $name: ${t.message}")
        }
    }
}
