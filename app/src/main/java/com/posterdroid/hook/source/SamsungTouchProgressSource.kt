package com.posterdroid.hook.source

import com.posterdroid.core.UnlockState
import com.posterdroid.hook.HookTargets
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class SamsungTouchProgressSource : ProgressSource {
    override val name: String = "SamsungTouchProgressSource"

    @Volatile
    private var isKeyguardShowing: Boolean = true

    @Volatile
    private var isUnlocking: Boolean = false

    override fun attach(classLoader: ClassLoader, dispatcher: ProgressDispatcher) {
        // 1. KeyguardStateControllerImpl: notifyKeyguardState & notifyKeyguardGoingAway
        try {
            val stateControllerClass = classLoader.loadClass(HookTargets.KEYGUARD_STATE_CONTROLLER_CLASS)

            // notifyKeyguardState(boolean showing, boolean occluded)
            XposedHelpers.findAndHookMethod(
                stateControllerClass,
                "notifyKeyguardState",
                java.lang.Boolean.TYPE,
                java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val showing = param.args[0] as? Boolean ?: return
                            val occluded = param.args[1] as? Boolean ?: false
                            val activeLockscreen = showing && !occluded
                            isKeyguardShowing = activeLockscreen

                            if (activeLockscreen) {
                                isUnlocking = false
                                dispatcher.dispatch(0f, UnlockState.LOCKED)
                            } else {
                                isUnlocking = false
                                dispatcher.dispatch(1f, UnlockState.UNLOCKED)
                            }
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )

            // notifyKeyguardGoingAway(boolean goingAway)
            try {
                XposedHelpers.findAndHookMethod(
                    stateControllerClass,
                    "notifyKeyguardGoingAway",
                    java.lang.Boolean.TYPE,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val goingAway = param.args[0] as? Boolean ?: false
                                if (goingAway) {
                                    isUnlocking = true
                                    dispatcher.dispatch(1f, UnlockState.UNLOCKING)
                                }
                            } catch (t: Throwable) {
                                // Safe catch
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                XposedBridge.log("[PosterDroid] Optional notifyKeyguardGoingAway hook skipped: ${t.message}")
            }

            XposedBridge.log("[PosterDroid] Attached KeyguardStateController hook")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook KeyguardStateController: ${t.message}")
        }

        // 2. KeyguardTouchBase: updateDistance & setTouch
        try {
            val touchBaseClass = classLoader.loadClass(HookTargets.KEYGUARD_TOUCH_BASE_CLASS)

            // Hook updateDistance(MotionEvent, boolean)
            // During drag, update progress continuously. Do NOT trigger UNLOCKING here:
            // finger is still touching, user can drag back or release without unlocking.
            XposedHelpers.findAndHookMethod(
                touchBaseClass,
                "updateDistance",
                android.view.MotionEvent::class.java,
                java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            if (!isKeyguardShowing || isUnlocking) return

                            val touchBase = param.thisObject ?: return
                            val isTouching = try {
                                XposedHelpers.getBooleanField(touchBase, "isTouching")
                            } catch (e: Throwable) {
                                true
                            }
                            if (!isTouching) return

                            val motionEvent = param.args[0] as? android.view.MotionEvent
                            val currentY = motionEvent?.rawY ?: -1f

                            val touchDownPos = try {
                                XposedHelpers.getObjectField(touchBase, "touchDownPos") as? android.graphics.PointF
                            } catch (e: Throwable) {
                                null
                            }
                            val startY = touchDownPos?.y ?: -1f

                            val context = try {
                                XposedHelpers.getObjectField(touchBase, "context") as? android.content.Context
                            } catch (e: Throwable) {
                                null
                            }
                            val screenHeight = context?.resources?.displayMetrics?.heightPixels?.toFloat() ?: 2400f

                            val progress = if (startY >= 0f && currentY >= 0f && screenHeight > 0f) {
                                // Fixed travel height = display height (h1 = h), anchored at touch point
                                ((startY - currentY) / screenHeight).coerceIn(0f, 1f)
                            } else {
                                val distance = XposedHelpers.getFloatField(touchBase, "distance")
                                val radius = XposedHelpers.getIntField(touchBase, "swipeUnlockRadius")
                                if (radius > 0) (distance / radius.toFloat()).coerceIn(0f, 1f) else 0f
                            }

                            dispatcher.dispatch(progress, UnlockState.DRAGGING)
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )

            // Hook setTouch(boolean) for touch down / release events
            XposedHelpers.findAndHookMethod(
                touchBaseClass,
                "setTouch",
                java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            if (!isKeyguardShowing) return

                            val isTouching = param.args[0] as Boolean

                            if (isTouching) {
                                if (!isUnlocking) {
                                    dispatcher.dispatch(0f, UnlockState.DRAGGING)
                                }
                            } else {
                                // Touch released: if we are already unlocking, under NO circumstances should we cancel
                                if (isUnlocking) return

                                val touchBase = param.thisObject ?: return
                                val isUnlockExecuted = try {
                                    XposedHelpers.getBooleanField(touchBase, "isUnlockExecuted")
                                } catch (e: Throwable) {
                                    false
                                }

                                if (isUnlockExecuted) {
                                    isUnlocking = true
                                    dispatcher.dispatch(1f, UnlockState.UNLOCKING)
                                } else {
                                    // Unlock DID NOT happen: reset state and return wallpaper to initial locked view
                                    dispatcher.dispatch(0f, UnlockState.CANCELLED)
                                }
                            }
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )

            XposedBridge.log("[PosterDroid] Attached KeyguardTouchBase hooks")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook KeyguardTouchBase: ${t.message}")
        }

        // 3. KeyguardTouchAnimator: onTouchEvent, reset(boolean) and restoreChildViewVI
        try {
            val touchAnimatorClass = classLoader.loadClass(HookTargets.KEYGUARD_TOUCH_ANIMATOR_CLASS)

            // Hook onTouchEvent(MotionEvent) to catch ACTION_UP early when swipe exceeds unlock radius
            try {
                XposedHelpers.findAndHookMethod(
                    touchAnimatorClass,
                    "onTouchEvent",
                    android.view.MotionEvent::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                if (!isKeyguardShowing || isUnlocking) return
                                val event = param.args[0] as? android.view.MotionEvent ?: return
                                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                                    val animator = param.thisObject ?: return
                                    val canBeUnlock = try {
                                        XposedHelpers.callMethod(animator, "getCanBeUnlock") as? Boolean ?: false
                                    } catch (e: Throwable) {
                                        false
                                    }
                                    val distance = try {
                                        XposedHelpers.getFloatField(animator, "distance")
                                    } catch (e: Throwable) {
                                        0f
                                    }
                                    val radius = try {
                                        XposedHelpers.getIntField(animator, "swipeUnlockRadius")
                                    } catch (e: Throwable) {
                                        0
                                    }
                                    if (canBeUnlock && radius > 0 && distance >= radius) {
                                        isUnlocking = true
                                        dispatcher.dispatch(1f, UnlockState.UNLOCKING)
                                    }
                                }
                            } catch (t: Throwable) {
                                // Safe catch
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                XposedBridge.log("[PosterDroid] Optional onTouchEvent hook skipped: ${t.message}")
            }

            // Hook restoreChildViewVI (called when swipe fails or does not unlock)
            for (method in touchAnimatorClass.declaredMethods) {
                if (method.name.startsWith("restoreChildViewVI")) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                if (!isKeyguardShowing || isUnlocking) return
                                val animator = param.thisObject ?: return
                                val isUnlockExecuted = try {
                                    XposedHelpers.getBooleanField(animator, "isUnlockExecuted")
                                } catch (e: Throwable) {
                                    false
                                }
                                if (!isUnlockExecuted) {
                                    dispatcher.dispatch(0f, UnlockState.CANCELLED)
                                }
                            } catch (t: Throwable) {
                                // Safe catch
                            }
                        }
                    })
                }
            }

            // Hook reset(boolean)
            XposedHelpers.findAndHookMethod(
                touchAnimatorClass,
                "reset",
                java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val animator = param.thisObject ?: return
                            val isUnlockExecuted = try {
                                XposedHelpers.getBooleanField(animator, "isUnlockExecuted")
                            } catch (e: Throwable) {
                                false
                            }
                            if (isUnlockExecuted) {
                                isUnlocking = true
                                dispatcher.dispatch(1f, UnlockState.UNLOCKING)
                            }
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            if (!isKeyguardShowing || isUnlocking) return
                            // If keyguard is still showing and not unlocking, return wallpaper to initial locked view
                            dispatcher.dispatch(0f, UnlockState.CANCELLED)
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )
            XposedBridge.log("[PosterDroid] Attached KeyguardTouchAnimator hooks")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook KeyguardTouchAnimator: ${t.message}")
        }
    }
}
