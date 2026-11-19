package com.posterdroid.hook.source

interface ProgressSource {
    val name: String
    fun attach(classLoader: ClassLoader, dispatcher: ProgressDispatcher)
}
