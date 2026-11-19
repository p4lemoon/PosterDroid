package com.posterdroid.hook.source

import com.posterdroid.core.UnlockState
import com.posterdroid.hook.HookTargets
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class ShadeProgressSource : ProgressSource {
    override val name: String = "ShadeProgressSource"

    override fun attach(classLoader: ClassLoader, dispatcher: ProgressDispatcher) {
        // 1. ShadeExpansionChangeEvent constructor (fraction: Float, expanded: Boolean, tracking: Boolean)
        try {
            val eventClass = classLoader.loadClass(HookTargets.SHADE_EXPANSION_EVENT_CLASS)
            val constructor = eventClass.getDeclaredConstructor(
                java.lang.Float.TYPE,
                java.lang.Boolean.TYPE,
                java.lang.Boolean.TYPE
            )
            XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val fraction = (param.args[0] as? Number)?.toFloat() ?: return
                        val expanded = param.args[1] as? Boolean ?: false
                        val tracking = param.args[2] as? Boolean ?: false

                        if (tracking) {
                            dispatcher.dispatch(fraction, UnlockState.DRAGGING)
                        } else {
                            if (fraction >= 0.99f || expanded) {
                                dispatcher.dispatch(1f, UnlockState.UNLOCKING)
                            } else if (fraction <= 0.01f) {
                                dispatcher.dispatch(0f, UnlockState.LOCKED)
                            }
                        }
                    } catch (t: Throwable) {
                        // Safe catch
                    }
                }
            })
            XposedBridge.log("[PosterDroid] Attached $name (ShadeExpansionChangeEvent)")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook ShadeExpansionChangeEvent in $name: ${t.message}")
        }

        // 2. ShadeExpansionStateManager.updateStateInternal(int)
        try {
            val mgrClass = classLoader.loadClass(HookTargets.SHADE_EXPANSION_STATE_MANAGER_CLASS)
            XposedHelpers.findAndHookMethod(
                mgrClass,
                "updateStateInternal",
                java.lang.Integer.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val state = (param.args[0] as? Number)?.toInt() ?: return
                            when (state) {
                                0 -> dispatcher.dispatch(0f, UnlockState.LOCKED)
                                1 -> {
                                    if (dispatcher.currentState != UnlockState.DRAGGING) {
                                        dispatcher.dispatch(dispatcher.currentProgress.coerceAtLeast(0f), UnlockState.DRAGGING)
                                    }
                                }
                                2 -> dispatcher.dispatch(1f, UnlockState.UNLOCKED)
                            }
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )
            XposedBridge.log("[PosterDroid] Attached $name (updateStateInternal)")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook updateStateInternal in $name: ${t.message}")
        }

        // 3. NotificationPanelViewController.setExpandedFraction(float)
        try {
            val panelClass = classLoader.loadClass(HookTargets.NOTIFICATION_PANEL_CLASS)
            XposedHelpers.findAndHookMethod(
                panelClass,
                "setExpandedFraction",
                java.lang.Float.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val fraction = (param.args[0] as? Number)?.toFloat() ?: return
                            if (fraction == 0f && dispatcher.currentState != UnlockState.LOCKED) {
                                dispatcher.dispatch(0f, UnlockState.LOCKED)
                            } else if (fraction >= 1f && dispatcher.currentState != UnlockState.UNLOCKED) {
                                dispatcher.dispatch(1f, UnlockState.UNLOCKED)
                            }
                        } catch (t: Throwable) {
                            // Safe catch
                        }
                    }
                }
            )
            XposedBridge.log("[PosterDroid] Attached $name (setExpandedFraction)")
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Failed to hook NotificationPanelViewController in $name: ${t.message}")
        }
    }
}
