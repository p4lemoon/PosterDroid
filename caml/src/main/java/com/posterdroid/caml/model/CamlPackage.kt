package com.posterdroid.caml.model

data class WallpaperParallaxGroup(
    val axis: String,
    val keyPath: String,
    val layerName: String,
    val mapMinTo: Float,
    val mapMaxTo: Float,
    val view: String? = null
)

data class CamlLayerTree(
    val rootLayer: CamlLayer,
    val states: List<CamlState> = emptyList(),
    val stateTransitions: List<CamlStateTransition> = emptyList(),
    val parallaxGroups: List<WallpaperParallaxGroup> = emptyList()
) {
    fun findState(name: String): CamlState? {
        val exact = states.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (exact != null) return exact

        return when {
            name.startsWith("Lock", ignoreCase = true) ->
                states.firstOrNull { it.name.startsWith("Lock", ignoreCase = true) }
            name.startsWith("Unlock", ignoreCase = true) || name.startsWith("Home", ignoreCase = true) ->
                states.firstOrNull { it.name.startsWith("Unlock", ignoreCase = true) || it.name.startsWith("Home", ignoreCase = true) }
            name.startsWith("Sleep", ignoreCase = true) ->
                states.firstOrNull { it.name.startsWith("Sleep", ignoreCase = true) }
            else ->
                states.firstOrNull { it.name.contains(name, ignoreCase = true) }
        }
    }

    fun findLayerById(targetId: String): CamlLayer? =
        rootLayer.findLayerById(targetId)

    fun totalLayerCount(): Int =
        rootLayer.collectAllLayers().size
}

data class CamlPackage(
    val name: String,
    val background: CamlLayerTree? = null,
    val floating: CamlLayerTree? = null,
    val foreground: CamlLayerTree? = null,
    val assets: Map<String, ByteArray> = emptyMap(),
    val metadata: Map<String, String> = emptyMap()
) {
    val totalLayers: Int
        get() = (background?.totalLayerCount() ?: 0) +
                (floating?.totalLayerCount() ?: 0) +
                (foreground?.totalLayerCount() ?: 0)

    val hasContinuousAnimations: Boolean
        get() {
            val checkTree: (CamlLayerTree?) -> Boolean = { tree ->
                tree?.rootLayer?.collectAllLayers()?.any {
                    it.animations.isNotEmpty() || it is CAEmitterLayer
                } ?: false
            }
            return checkTree(background) || checkTree(floating) || checkTree(foreground)
        }

    val hasParallax: Boolean
        get() = (background?.parallaxGroups?.isNotEmpty() == true) ||
                (floating?.parallaxGroups?.isNotEmpty() == true) ||
                (foreground?.parallaxGroups?.isNotEmpty() == true)
}
