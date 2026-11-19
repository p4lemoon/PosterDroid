package com.posterdroid.caml.parser

class ParseReport(val sourceName: String) {
    var totalLayersParsed: Int = 0
    var statesCount: Int = 0
    var animationsCount: Int = 0
    val layerTypesCount = mutableMapOf<String, Int>()
    val skippedElements = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    fun recordLayer(type: String) {
        totalLayersParsed++
        layerTypesCount[type] = (layerTypesCount[type] ?: 0) + 1
    }

    fun recordSkipped(element: String, reason: String) {
        skippedElements.add("$element ($reason)")
    }

    fun recordWarning(warning: String) {
        warnings.add(warning)
    }

    fun generateSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("══════════════════════════════════════════")
        sb.appendLine(" CAML Parse Report: $sourceName")
        sb.appendLine("══════════════════════════════════════════")
        sb.appendLine("Total Layers Parsed: $totalLayersParsed")
        sb.appendLine("Total States: $statesCount")
        sb.appendLine("Total Animations: $animationsCount")
        sb.appendLine("Supported Layer Breakdown:")
        layerTypesCount.forEach { (type, count) ->
            sb.appendLine("  • $type: $count")
        }
        if (skippedElements.isNotEmpty()) {
            sb.appendLine("Skipped/Unsupported Elements (${skippedElements.size}):")
            skippedElements.take(10).forEach { sb.appendLine("  ⚠ $it") }
            if (skippedElements.size > 10) {
                sb.appendLine("  ... and ${skippedElements.size - 10} more")
            }
        } else {
            sb.appendLine("Skipped/Unsupported Elements: 0 (All elements recognized!)")
        }
        if (warnings.isNotEmpty()) {
            sb.appendLine("Warnings (${warnings.size}):")
            warnings.take(5).forEach { sb.appendLine("  ℹ $it") }
        }
        sb.appendLine("══════════════════════════════════════════")
        return sb.toString()
    }
}
