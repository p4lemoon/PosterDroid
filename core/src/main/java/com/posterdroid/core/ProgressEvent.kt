package com.posterdroid.core

data class ProgressEvent(
    val progress: Float,
    val state: UnlockState,
    val timestamp: Long = System.currentTimeMillis()
)
