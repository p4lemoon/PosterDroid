package com.posterdroid.hook.status

object HookStatus {
    /**
     * Replaced at runtime by [com.posterdroid.hook.PosterDroidHook] via Xposed.
     * Returns true if the Xposed/LSPosed module is loaded and active.
     */
    fun isModuleActive(): Boolean = false
}
