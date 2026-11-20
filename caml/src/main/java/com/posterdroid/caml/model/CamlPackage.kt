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
    fun findState(
        name: String,
        isDarkMode: Boolean = false,
        orientation: String = "PortraitUp"
    ): CamlState? {
        val exact = states.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (exact != null) return exact

        val isLockTarget = name.startsWith("Lock", ignoreCase = true)
        val isHomeTarget = name.startsWith("Unlock", ignoreCase = true) || name.startsWith("Home", ignoreCase = true)
        val isSleepTarget = name.startsWith("Sleep", ignoreCase = true)

        val candidates = states.filter { s ->
            when {
                isLockTarget -> s.name.contains("Lock", ignoreCase = true) &&
                        !s.name.contains("Sleep", ignoreCase = true) &&
                        !s.name.contains("Home", ignoreCase = true) &&
                        !s.name.contains("Unlock", ignoreCase = true)
                isHomeTarget -> (s.name.contains("Unlock", ignoreCase = true) || s.name.contains("Home", ignoreCase = true)) &&
                        !s.name.contains("Lock", ignoreCase = true) &&
                        !s.name.contains("Sleep", ignoreCase = true)
                isSleepTarget -> s.name.contains("Sleep", ignoreCase = true)
                else -> s.name.contains(name, ignoreCase = true)
            }
        }

        if (candidates.isNotEmpty()) {
            return candidates.maxByOrNull { s ->
                var score = 100
                val sName = s.name
                if (isDarkMode) {
                    if (sName.contains("Dark", ignoreCase = true)) score += 40
                    if (sName.contains("Light", ignoreCase = true)) score -= 30
                } else {
                    if (sName.contains("Light", ignoreCase = true)) score += 40
                    if (sName.contains("Dark", ignoreCase = true)) score -= 30
                }

                if (sName.contains(orientation, ignoreCase = true)) {
                    score += 20
                } else if (orientation.startsWith("Portrait", ignoreCase = true) && sName.contains("Portrait", ignoreCase = true)) {
                    score += 15
                } else if (orientation.startsWith("Landscape", ignoreCase = true) && sName.contains("Landscape", ignoreCase = true)) {
                    score += 15
                }

                if (sName.startsWith(name, ignoreCase = true)) {
                    score += 10
                }
                score
            }
        }

        return when {
            isLockTarget -> states.firstOrNull { it.name.contains("Lock", ignoreCase = true) }
            isHomeTarget -> states.firstOrNull { it.name.contains("Unlock", ignoreCase = true) || it.name.contains("Home", ignoreCase = true) }
            isSleepTarget -> states.firstOrNull { it.name.contains("Sleep", ignoreCase = true) }
            else -> states.firstOrNull { it.name.contains(name, ignoreCase = true) } ?: states.firstOrNull()
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
