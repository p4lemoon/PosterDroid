package com.posterdroid.hook

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import com.posterdroid.core.PosterConstants
import com.posterdroid.core.ProgressEvent
import com.posterdroid.hook.source.BiometricProgressSource
import com.posterdroid.hook.source.HyperOSTouchProgressSource
import com.posterdroid.hook.source.ProgressDispatcher
import com.posterdroid.hook.source.ProgressSource
import com.posterdroid.hook.source.SamsungTouchProgressSource
import com.posterdroid.hook.source.ShadeProgressSource
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class PosterDroidHook : IXposedHookLoadPackage {

    companion object {
        @Volatile
        var currentWindowToken: IBinder? = null
    }

    private var progressDispatcher: ProgressDispatcher? = null
    private var appContext: Context? = null
    private var wallpaperManager: WallpaperManager? = null
    private var semMethod: java.lang.reflect.Method? = null
    private var semMethodLookupDone = false
    private var isInitialized = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == PosterConstants.PACKAGE_NAME) {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.posterdroid.hook.status.HookStatus",
                    lpparam.classLoader,
                    "isModuleActive",
                    XC_MethodReplacement.returnConstant(true)
                )
                XposedBridge.log("[PosterDroid] Hooked HookStatus in PosterDroid process")
            } catch (t: Throwable) {
                // Ignore
            }
            return
        }

        if (lpparam.packageName != PosterConstants.SYSTEMUI_PACKAGE) {
            return
        }

        try {
            XposedBridge.log("[PosterDroid] Initializing on Android ${Build.VERSION.SDK_INT}")

            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            appContext = param.thisObject as? Context
                            initProgressSources(lpparam.classLoader)
                        } catch (t: Throwable) {
                            XposedBridge.log("[PosterDroid] Error initializing progress sources: ${t.message}")
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Fatal error in handleLoadPackage: ${t.message}")
        }
    }

    private fun initProgressSources(classLoader: ClassLoader) {
        if (isInitialized) return
        isInitialized = true

        val dispatcher = ProgressDispatcher(
            deltaThreshold = HookTargets.PROGRESS_DELTA_THRESHOLD,
            onEvent = { event ->
                dispatchToWallpaper(event)
            }
        )
        progressDispatcher = dispatcher

        val isSamsung = try {
            classLoader.loadClass(HookTargets.KEYGUARD_TOUCH_BASE_CLASS)
            true
        } catch (e: Throwable) {
            false
        }

        val isHyperOS = try {
            classLoader.loadClass(HookTargets.HYPEROS_KEYGUARD_PANEL_CLASS)
            true
        } catch (e: Throwable) {
            false
        }

        val sources = mutableListOf<ProgressSource>()
        if (isSamsung) {
            XposedBridge.log("[PosterDroid] Detected Samsung One UI environment")
            sources.add(SamsungTouchProgressSource())
            sources.add(BiometricProgressSource())
        } else if (isHyperOS) {
            XposedBridge.log("[PosterDroid] Detected Xiaomi HyperOS environment")
            sources.add(HyperOSTouchProgressSource())
            sources.add(BiometricProgressSource())
        } else {
            XposedBridge.log("[PosterDroid] Detected AOSP or unknown environment")
            sources.add(ShadeProgressSource())
            sources.add(BiometricProgressSource())
        }

        for (source in sources) {
            try {
                source.attach(classLoader, dispatcher)
            } catch (t: Throwable) {
                XposedBridge.log("[PosterDroid] Failed to attach ${source.name}: ${t.message}")
            }
        }
        XposedBridge.log("[PosterDroid] All progress sources initialized")
    }

    private fun dispatchToWallpaper(event: ProgressEvent) {
        try {
            val context = appContext ?: return
            if (wallpaperManager == null) {
                wallpaperManager = context.getSystemService(Context.WALLPAPER_SERVICE) as? WallpaperManager
            }
            val wm = wallpaperManager ?: return

            val bundle = Bundle().apply {
                putFloat(PosterConstants.EXTRA_PROGRESS, event.progress)
                putString(PosterConstants.EXTRA_STATE, event.state.name)
                putLong(PosterConstants.EXTRA_TIMESTAMP, event.timestamp)
            }

            if (!semMethodLookupDone) {
                semMethodLookupDone = true
                try {
                    semMethod = wm.javaClass.getMethod(
                        "semSendWallpaperCommand",
                        java.lang.Integer.TYPE,
                        String::class.java,
                        Bundle::class.java
                    )
                } catch (ignored: Throwable) {
                    semMethod = null
                }
            }

            var sent = false
            if (semMethod != null) {
                try {
                    semMethod?.invoke(wm, 2, PosterConstants.COMMAND_PROGRESS, bundle)
                    sent = true
                } catch (ignored: Throwable) {
                    // Fallback to standard AOSP sendWallpaperCommand
                }
            }

            // 2. Standard AOSP sendWallpaperCommand fallback
            if (!sent) {
                try {
                    wm.sendWallpaperCommand(currentWindowToken, PosterConstants.COMMAND_PROGRESS, 0, 0, 0, bundle)
                } catch (t: Throwable) {
                    // Safe catch
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log("[PosterDroid] Error dispatching wallpaper command: ${t.message}")
        }
    }
}
