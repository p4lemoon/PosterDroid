package com.posterdroid.caml

import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class CoreAnimationAuditTest {

    private val tendiesDir = File("../examples/tendies").let {
        if (it.exists()) it else File("examples/tendies")
    }

    @Test
    fun auditAllTendiesFeatures() {
        val files = tendiesDir.listFiles { _, name -> name.endsWith(".tendies") } ?: emptyArray()
        println("Found ${files.size} tendies files for audit.")

        val elementCounts = mutableMapOf<String, Int>()
        val attributeCounts = mutableMapOf<String, Int>()
        val keyPathsAnimated = mutableMapOf<String, Int>()
        val timingFunctions = mutableMapOf<String, Int>()
        val calculationModes = mutableMapOf<String, Int>()
        val stateElementTypes = mutableMapOf<String, Int>()

        for (file in files) {
            println("--- Auditing ${file.name} ---")
            val zip = ZipInputStream(file.inputStream())
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".caml")) {
                    val xmlBytes = zip.readBytes()
                    auditXml(
                        xmlBytes,
                        elementCounts,
                        attributeCounts,
                        keyPathsAnimated,
                        timingFunctions,
                        calculationModes,
                        stateElementTypes
                    )
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            zip.close()
        }

        println("\n═══════════════ CORE ANIMATION AUDIT REPORT ═══════════════")
        println("1. ALL XML ELEMENTS FOUND:")
        elementCounts.entries.sortedByDescending { it.value }.forEach {
            println("   • ${it.key}: ${it.value}")
        }

        println("\n2. ALL ATTRIBUTES FOUND:")
        attributeCounts.entries.sortedByDescending { it.value }.forEach {
            println("   • ${it.key}: ${it.value}")
        }

        println("\n3. ANIMATED KEYPATHS:")
        keyPathsAnimated.entries.sortedByDescending { it.value }.forEach {
            println("   • ${it.key}: ${it.value}")
        }

        println("\n4. TIMING FUNCTIONS:")
        timingFunctions.entries.sortedByDescending { it.value }.forEach {
            println("   • ${it.key}: ${it.value}")
        }

        println("\n5. CALCULATION MODES:")
        calculationModes.entries.sortedByDescending { it.value }.forEach {
            println("   • ${it.key}: ${it.value}")
        }

        println("\n6. STATE ELEMENT TYPES:")
        stateElementTypes.entries.sortedByDescending { it.value }.forEach {
            println("   • ${it.key}: ${it.value}")
        }
        println("════════════════════════════════════════════════════════════\n")
    }

    private fun auditXml(
        xmlBytes: ByteArray,
        elements: MutableMap<String, Int>,
        attributes: MutableMap<String, Int>,
        keyPaths: MutableMap<String, Int>,
        timings: MutableMap<String, Int>,
        calcModes: MutableMap<String, Int>,
        stateElements: MutableMap<String, Int>
    ) {
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                try {
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                } catch (ignored: Throwable) {}
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
            traverse(doc.documentElement, elements, attributes, keyPaths, timings, calcModes, stateElements)
        } catch (t: Throwable) {
            println("   [!] Could not parse XML (${xmlBytes.size} bytes): ${t.message}")
        }
    }

    private fun traverse(
        element: Element,
        elements: MutableMap<String, Int>,
        attributes: MutableMap<String, Int>,
        keyPaths: MutableMap<String, Int>,
        timings: MutableMap<String, Int>,
        calcModes: MutableMap<String, Int>,
        stateElements: MutableMap<String, Int>
    ) {
        val tag = element.tagName.substringAfter(":")
        elements[tag] = (elements[tag] ?: 0) + 1

        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val name = attr.nodeName.substringAfter(":")
            attributes[name] = (attributes[name] ?: 0) + 1
        }

        if (element.hasAttribute("keyPath")) {
            val kp = element.getAttribute("keyPath")
            keyPaths[kp] = (keyPaths[kp] ?: 0) + 1
        }
        if (element.hasAttribute("timingFunction")) {
            val tf = element.getAttribute("timingFunction")
            timings[tf] = (timings[tf] ?: 0) + 1
        }
        if (element.hasAttribute("calculationMode")) {
            val cm = element.getAttribute("calculationMode")
            calcModes[cm] = (calcModes[cm] ?: 0) + 1
        }
        if (tag.startsWith("CAState") || tag.contains("State")) {
            stateElements[tag] = (stateElements[tag] ?: 0) + 1
        }
        if (tag.contains("filter", ignoreCase = true) || tag.contains("Emitter", ignoreCase = true)) {
            val attrStr = (0 until attrs.length).joinToString(", ") { idx -> "${attrs.item(idx).nodeName}=${attrs.item(idx).nodeValue}" }
            println("   [FOUND] <$tag $attrStr>")
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                traverse(child as Element, elements, attributes, keyPaths, timings, calcModes, stateElements)
            }
        }
    }
}
