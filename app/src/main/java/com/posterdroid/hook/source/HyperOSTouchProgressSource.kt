package com.posterdroid.hook.source

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import com.posterdroid.core.UnlockState
import com.posterdroid.hook.HookTargets
import com.posterdroid.hook.PosterDroidHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class HyperOSTouchProgressSource : ProgressSource {
    override val name: String = "HyperOSTouchProgressSource"

    override fun attach(classLoader: ClassLoader, dispatcher: ProgressDispatcher) {
        // 1. Hook KeyguardPanelViewController.updateKeyguardElementsExpansionInternal(float f)
        // Called continuously during lockscreen touch swipe-up gestures on Xiaomi HyperOS.
        // f = 1.0f when fully locked, decreasing down to 0.0f when swiped up towards unlock.
        try {
            val keyguardPanelClass = classLoader.loadClass(HookTargets.HYPEROS_KEYGUARD_PANEL_CLASS)
            XposedHelpers.findAndHookMethod(
                keyguardPanelClass,
                "updateKeyguardElementsExpansionInternal",
                java.lang.Float.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val isOnKeyguard = try {
                                XposedHelpers.callMethod(param.thisObject, "isOnKeyguard") as? Boolean ?: true
                            } catch (e: Throwable) {
                                true
                            }
                            if (!isOnKeyguard) return

                            // Cache windowToken from notificationShadeWindowView for reliable IPC
                            if (PosterDroidHook.currentWindowToken == null) {
                                try {
                                    val windowView = XposedHelpers.getObjectField(
                                        param.thisObject,
                                        "notificationShadeWindowView"
                                    ) as? View
                                    val token = windowView?.windowToken
                                    if (token != null) {
                                        PosterDroidHook.currentWindowToken = token
                                    }
                                } catch (ignored: Throwable) {}
                            }

                            val f = (param.args[0] as? Number)?.toFloat() ?: return
                            val progress = (1.0f - f).coerceIn(0f, 1f)
                            val state = when {
                                progress <= 0.001f -> UnlockState.LOCKED
                                progress >= 0.999f -> UnlockState.UNLOCKED
                                else -> UnlockState.DRAGGING
                            }
                            dispatcher.dispatch(progress, state)
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )
            XposedBridge.log("[PosterDroid] Attached $name (updateKeyguardElementsExpansionInternal)")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook updateKeyguardElementsExpansionInternal: ${t.message}")
        }

        // 2. Hook KeyguardPanelViewController.setBouncerShowingFraction$default
        // Covers bouncer fraction changes and snap-back/open animations
        try {
            val keyguardPanelClass = classLoader.loadClass(HookTargets.HYPEROS_KEYGUARD_PANEL_CLASS)
            XposedHelpers.findAndHookMethod(
                keyguardPanelClass,
                "setBouncerShowingFraction\$default",
                keyguardPanelClass,
                java.lang.Float.TYPE,
                java.lang.Boolean.TYPE,
                java.lang.Integer.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val target = param.args[0]
                            val isOnKeyguard = try {
                                XposedHelpers.callMethod(target, "isOnKeyguard") as? Boolean ?: true
                            } catch (e: Throwable) {
                                true
                            }
                            if (!isOnKeyguard) return

                            val bouncerFraction = (param.args[1] as? Number)?.toFloat() ?: return
                            val progress = bouncerFraction.coerceIn(0f, 1f)
                            val state = when {
                                progress <= 0.001f -> UnlockState.LOCKED
                                progress >= 0.999f -> UnlockState.UNLOCKED
                                else -> UnlockState.DRAGGING
                            }
                            dispatcher.dispatch(progress, state)
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )
            XposedBridge.log("[PosterDroid] Attached $name (setBouncerShowingFraction)")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook setBouncerShowingFraction: ${t.message}")
        }

        // 3. Hook KeyguardPanelViewController.startUnlockKeyguardDismissAnim
        // Tracks biometric, face, PIN/pattern unlock dismiss animation
        try {
            val keyguardPanelClass = classLoader.loadClass(HookTargets.HYPEROS_KEYGUARD_PANEL_CLASS)
            XposedBridge.hookAllMethods(
                keyguardPanelClass,
                "startUnlockKeyguardDismissAnim",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        dispatcher.dispatch(1f, UnlockState.UNLOCKING)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val anim = XposedHelpers.getObjectField(
                                param.thisObject,
                                "unlockAlphaAndScaleAnimator"
                            ) as? ValueAnimator ?: return

                            anim.addUpdateListener { va ->
                                val alpha = (va.animatedValue as? Number)?.toFloat() ?: return@addUpdateListener
                                val progress = (1.0f - alpha).coerceIn(0f, 1f)
                                dispatcher.dispatch(progress, UnlockState.UNLOCKING)
                            }

                            anim.addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    dispatcher.dispatch(1f, UnlockState.UNLOCKED)
                                }
                            })
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )
            XposedBridge.log("[PosterDroid] Attached $name (startUnlockKeyguardDismissAnim)")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook startUnlockKeyguardDismissAnim: ${t.message}")
        }

        // 4. Hook NotificationPanelViewController.setExpandedHeightInternalAosp as fallback progress
        try {
            val panelClass = classLoader.loadClass(HookTargets.NOTIFICATION_PANEL_CLASS)
            XposedHelpers.findAndHookMethod(
                panelClass,
                "setExpandedHeightInternalAosp",
                java.lang.Float.TYPE,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val isKeyguard = XposedHelpers.callMethod(param.thisObject, "isKeyguardShowing") as? Boolean ?: false
                            if (isKeyguard) {
                                val fraction = XposedHelpers.getFloatField(param.thisObject, "mExpandedFraction")
                                val progress = (1.0f - fraction).coerceIn(0f, 1f)
                                val state = when {
                                    progress <= 0.001f -> UnlockState.LOCKED
                                    progress >= 0.999f -> UnlockState.UNLOCKED
                                    else -> UnlockState.DRAGGING
                                }
                                dispatcher.dispatch(progress, state)
                            }
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )
            XposedBridge.log("[PosterDroid] Attached $name (setExpandedHeightInternalAosp)")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook setExpandedHeightInternalAosp: ${t.message}")
        }

        // 5. Hook KeyguardUnlockAnimationController
        try {
            val unlockAnimClass = classLoader.loadClass(HookTargets.AOSP_UNLOCK_CONTROLLER_CLASS)
            XposedHelpers.findAndHookMethod(
                unlockAnimClass,
                "setSurfaceBehindAppearAmount",
                java.lang.Float.TYPE,
                java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val amount = (param.args[0] as? Number)?.toFloat() ?: return
                            dispatcher.dispatch(amount.coerceIn(0f, 1f), UnlockState.UNLOCKING)
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )
            XposedBridge.hookAllMethods(
                unlockAnimClass,
                "notifyFinishedKeyguardExitAnimation",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        dispatcher.dispatch(1f, UnlockState.UNLOCKED)
                    }
                }
            )
            XposedBridge.log("[PosterDroid] Attached $name (KeyguardUnlockAnimationController)")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook KeyguardUnlockAnimationController: ${t.message}")
        }

        // 6. Hook KeyguardStateControllerImpl for lifecycle state boundaries
        try {
            val keyguardStateClass = classLoader.loadClass(HookTargets.KEYGUARD_STATE_CONTROLLER_CLASS)
            XposedBridge.hookAllMethods(
                keyguardStateClass,
                "notifyKeyguardState",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val showing = param.args[0] as? Boolean ?: true
                            if (showing) {
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
            XposedBridge.hookAllMethods(
                keyguardStateClass,
                "notifyKeyguardGoingAway",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        dispatcher.dispatch(1f, UnlockState.UNLOCKING)
                    }
                }
            )
            XposedBridge.log("[PosterDroid] Attached $name (KeyguardStateControllerImpl)")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook KeyguardStateControllerImpl: ${t.message}")
        }
    }
}
