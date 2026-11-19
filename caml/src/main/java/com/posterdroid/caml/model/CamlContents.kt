package com.posterdroid.caml.model

sealed class CamlContents {
    data class CGImage(val src: String) : CamlContents()
    data class Custom(val rawType: String, val attributes: Map<String, String>) : CamlContents()
}
