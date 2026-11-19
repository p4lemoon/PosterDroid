package com.posterdroid.caml

import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class TendiesCorpusAnalyzerTest {

    data class CorpusStats(
        val fileCounts: MutableMap<String, Int> = mutableMapOf(),
        val tagFrequency: MutableMap<String, Int> = mutableMapOf(),
        val tagAttributes: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
        val animationKeyPaths: MutableMap<String, Int> = mutableMapOf(),
        val stateKeyPaths: MutableMap<String, Int> = mutableMapOf(),
        val stateTransitionProperties: MutableMap<String, Int> = mutableMapOf(),
        val filterTypes: MutableMap<String, Int> = mutableMapOf(),
        val assetExtensions: MutableMap<String, Int> = mutableMapOf(),
        val packageSummaries: MutableList<PackageSummary> = mutableListOf()
    )

    data class PackageSummary(
        val name: String,
        val fileSizeKb: Long,
        val camlFiles: List<String>,
        val totalLayers: Int,
        val maxDepth: Int,
        val totalAnimations: Int,
        val totalStates: Int,
        val totalTransitions: Int,
        val totalAssets: Int,
        val assetNames: List<String>
    )

    @Test
    fun analyzeCorpus() {
        val corpusDir = File("C:/Users/user/posterdroid/examples/tendies")
        val files = corpusDir.listFiles { f -> f.extension == "tendies" } ?: emptyArray()

        val stats = CorpusStats()
        val docBuilder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder()

        for (file in files.sortedBy { it.name }) {
            analyzePackage(file, docBuilder, stats)
        }

        // Print sample CAEmitterBehavior and LKState details
        val zip = ZipFile(File("C:/Users/user/posterdroid/examples/tendies/TrinitySeveranceSnow.tendies"))
        for (entry in zip.entries().asSequence()) {
            if (entry.name.endsWith(".caml") && !entry.name.contains("__MACOSX")) {
                val text = zip.getInputStream(entry).reader().readText()
                if (text.contains("CAEmitterBehavior")) {
                    println("=== Found CAEmitterBehavior in ${entry.name} ===")
                    val matches = "<CAEmitterBehavior[\\s\\S]*?</CAEmitterBehavior>".toRegex().findAll(text)
                    for (m in matches.take(3)) {
                        println(m.value)
                        println("-------------------")
                    }
                    break
                }
            }
        }
        zip.close()

        // Generate Markdown Report
        val md = buildMarkdownReport(stats)
        val outFile = File("C:/Users/user/posterdroid/CAML_STATISTICS.md")
        outFile.writeText(md)
        println("Corpus analysis completed! Written to ${outFile.absolutePath}")
    }

    private fun analyzePackage(
        file: File,
        docBuilder: javax.xml.parsers.DocumentBuilder,
        stats: CorpusStats
    ) {
        val zip = ZipFile(file)
        var totalLayers = 0
        var maxDepth = 0
        var totalAnimations = 0
        var totalStates = 0
        var totalTransitions = 0
        val camlFiles = mutableListOf<String>()
        val assets = mutableListOf<String>()

        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name
            if (name.contains("__MACOSX") || name.endsWith(".DS_Store")) {
                continue
            }
            if (name.endsWith(".caml") || name.endsWith(".xml")) {
                camlFiles.add(name)
                zip.getInputStream(entry).use { stream ->
                    try {
                        val doc = docBuilder.parse(stream)
                        val (layers, depth, anims, states, transitions) = analyzeDom(doc.documentElement, 1, stats)
                        totalLayers += layers
                        if (depth > maxDepth) maxDepth = depth
                        totalAnimations += anims
                        totalStates += states
                        totalTransitions += transitions
                    } catch (t: Throwable) {
                        println("Error parsing $name in ${file.name}: ${t.message}")
                    }
                }
            } else if (!entry.isDirectory && (name.contains("assets/") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
                assets.add(name)
                val ext = name.substringAfterLast('.', "unknown").lowercase()
                stats.assetExtensions[ext] = (stats.assetExtensions[ext] ?: 0) + 1
            }
        }
        zip.close()

        stats.packageSummaries.add(
            PackageSummary(
                name = file.name,
                fileSizeKb = file.length() / 1024,
                camlFiles = camlFiles,
                totalLayers = totalLayers,
                maxDepth = maxDepth,
                totalAnimations = totalAnimations,
                totalStates = totalStates,
                totalTransitions = totalTransitions,
                totalAssets = assets.size,
                assetNames = assets
            )
        )
    }

    private fun analyzeDom(
        element: Element,
        currentDepth: Int,
        stats: CorpusStats
    ): DomStats {
        val rawTag = element.tagName.substringAfter(":")
        stats.tagFrequency[rawTag] = (stats.tagFrequency[rawTag] ?: 0) + 1

        val attrMap = stats.tagAttributes.getOrPut(rawTag) { mutableMapOf() }
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val attrName = attr.nodeName.substringAfter(":")
            attrMap[attrName] = (attrMap[attrName] ?: 0) + 1

            if (attrName == "keyPath") {
                val kp = attr.nodeValue
                if (rawTag.contains("Animation")) {
                    stats.animationKeyPaths[kp] = (stats.animationKeyPaths[kp] ?: 0) + 1
                } else if (rawTag.contains("State")) {
                    stats.stateKeyPaths[kp] = (stats.stateKeyPaths[kp] ?: 0) + 1
                }
            }

            if (attrName == "type" && rawTag == "filter") {
                val ft = attr.nodeValue
                stats.filterTypes[ft] = (stats.filterTypes[ft] ?: 0) + 1
            }
        }

        var isLayer = rawTag.endsWith("Layer")
        var layerCount = if (isLayer) 1 else 0
        var maxSubDepth = currentDepth
        var animCount = if (rawTag.endsWith("Animation")) 1 else 0
        var stateCount = if (rawTag == "state" || rawTag == "CAState") 1 else 0
        var transitionCount = if (rawTag == "stateTransition" || rawTag == "CAStateTransition") 1 else 0

        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val childEl = node as Element
                val nextDepth = if (isLayer) currentDepth + 1 else currentDepth
                val childStats = analyzeDom(childEl, nextDepth, stats)
                layerCount += childStats.layerCount
                if (childStats.maxDepth > maxSubDepth) maxSubDepth = childStats.maxDepth
                animCount += childStats.animCount
                stateCount += childStats.stateCount
                transitionCount += childStats.transitionCount
            }
        }

        return DomStats(layerCount, maxSubDepth, animCount, stateCount, transitionCount)
    }

    data class DomStats(
        val layerCount: Int,
        val maxDepth: Int,
        val animCount: Int,
        val stateCount: Int,
        val transitionCount: Int
    )

    private fun buildMarkdownReport(stats: CorpusStats): String {
        val sb = StringBuilder()
        sb.append("# Core Animation (CAML) Corpus Statistics Report\n\n")
        sb.append("**Analyzed Packages:** ${stats.packageSummaries.size} real `.tendies` archives\n\n")

        sb.append("## 1. Package Overview\n\n")
        sb.append("| Package Name | Size (KB) | Layers | Max Depth | Animations | States | Transitions | Assets |\n")
        sb.append("|---|---|---|---|---|---|---|---|\n")
        for (p in stats.packageSummaries) {
            sb.append("| `${p.name}` | ${p.fileSizeKb} KB | ${p.totalLayers} | ${p.maxDepth} | ${p.totalAnimations} | ${p.totalStates} | ${p.totalTransitions} | ${p.totalAssets} |\n")
        }
        sb.append("\n---\n\n")

        sb.append("## 2. Element Tag Frequency\n\n")
        sb.append("| Element Tag | Frequency | Primary Role |\n")
        sb.append("|---|---|---|\n")
        for ((tag, count) in stats.tagFrequency.entries.sortedByDescending { it.value }) {
            val role = when {
                tag.endsWith("Layer") -> "Layer Hierarchy"
                tag.endsWith("Animation") -> "Animation Engine"
                tag.startsWith("CAState") || tag.contains("state", ignoreCase = true) -> "State & Transition Machine"
                tag == "filter" || tag.contains("Filter") -> "Visual Filter / Shader"
                tag == "sublayers" || tag == "animations" || tag == "states" || tag == "stateTransitions" -> "Structural Container"
                else -> "Model Property / Data"
            }
            sb.append("| `<$tag>` | $count | $role |\n")
        }
        sb.append("\n---\n\n")

        sb.append("## 3. Layer Classes & Attributes Breakdown\n\n")
        val layerTags = stats.tagFrequency.keys.filter { it.endsWith("Layer") || it == "layer" }
        for (tag in layerTags.sorted()) {
            sb.append("### `<$tag>` (Used ${stats.tagFrequency[tag]} times)\n\n")
            val attrs = stats.tagAttributes[tag] ?: emptyMap()
            sb.append("| Attribute | Occurrences | Category |\n")
            sb.append("|---|---|---|\n")
            for ((attr, count) in attrs.entries.sortedByDescending { it.value }) {
                val cat = when (attr) {
                    "bounds", "position", "anchorPoint", "anchorPointZ", "zPosition" -> "Geometry"
                    "transform", "sublayerTransform" -> "3D Transform (4x4)"
                    "opacity", "hidden", "masksToBounds", "cornerRadius", "backgroundColor", "allowsGroupOpacity" -> "Appearance"
                    "id", "name" -> "Identity"
                    "contents", "contentsRect" -> "Contents (Image)"
                    "colors", "locations", "startPoint", "endPoint", "type" -> "Gradient"
                    "path", "fillColor", "strokeColor", "lineWidth" -> "Shape / Vector"
                    "string", "font", "fontSize", "foregroundColor", "alignmentMode" -> "Typography"
                    "emitterPosition", "emitterSize", "birthRate", "lifetime", "velocity", "scale" -> "Particle Simulation"
                    else -> "Specialized / Other"
                }
                sb.append("| `$attr` | $count | $cat |\n")
            }
            sb.append("\n")
        }
        sb.append("---\n\n")

        sb.append("## 4. Animation KeyPaths & Types\n\n")
        sb.append("| Targeted `keyPath` | Count | Physics / Interpolation Required |\n")
        sb.append("|---|---|---|\n")
        for ((kp, count) in stats.animationKeyPaths.entries.sortedByDescending { it.value }) {
            val interp = when {
                kp.contains("transform") -> "CATransform3D Euler/Quaternion/Decomposition"
                kp.contains("opacity") || kp.contains("alpha") -> "Scalar Float [0..1]"
                kp.contains("position") || kp.contains("bounds") -> "Point2f / Rect4f Coordinate Math"
                else -> "Generic Float / Color"
            }
            sb.append("| `$kp` | $count | $interp |\n")
        }
        sb.append("\n---\n\n")

        sb.append("## 5. State Machine KeyPaths & Properties\n\n")
        sb.append("| State Target `keyPath` | Count |\n")
        sb.append("|---|---|\n")
        for ((kp, count) in stats.stateKeyPaths.entries.sortedByDescending { it.value }) {
            sb.append("| `$kp` | $count |\n")
        }
        sb.append("\n---\n\n")

        sb.append("## 6. Filters & Shaders Found\n\n")
        if (stats.filterTypes.isEmpty()) {
            sb.append("*No `<filter type=\"...\">` tags explicitly declared (filters mostly passed as attributes or subelements).*\n\n")
        } else {
            sb.append("| Filter Type | Occurrences |\n")
            sb.append("|---|---|\n")
            for ((ft, count) in stats.filterTypes.entries.sortedByDescending { it.value }) {
                sb.append("| `$ft` | $count |\n")
            }
            sb.append("\n")
        }
        sb.append("---\n\n")

        sb.append("## 7. Assets Summary\n\n")
        sb.append("| Format | Count |\n")
        sb.append("|---|---|\n")
        for ((ext, count) in stats.assetExtensions.entries.sortedByDescending { it.value }) {
            sb.append("| `.$ext` | $count |\n")
        }
        sb.append("\n")

        return sb.toString()
    }
}
