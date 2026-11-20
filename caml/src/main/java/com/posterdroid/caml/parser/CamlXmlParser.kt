package com.posterdroid.caml.parser

import com.posterdroid.caml.math.CATransform3D
import com.posterdroid.caml.model.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

class CamlXmlParser(private val report: ParseReport? = null) {

    fun parse(inputStream: InputStream): CamlLayerTree {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            } catch (ignored: Throwable) {}
        }
        val doc = factory.newDocumentBuilder().parse(inputStream)
        return parseDocument(doc.documentElement)
    }

    fun parse(xmlString: String): CamlLayerTree {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                isXIncludeAware = false
                isExpandEntityReferences = false
            } catch (ignored: Throwable) {}
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xmlString)))
        return parseDocument(doc.documentElement)
    }

    private fun parseDocument(root: Element): CamlLayerTree {
        val statesList = mutableListOf<CamlState>()
        val transitionsList = mutableListOf<CamlStateTransition>()
        val parallaxGroups = mutableListOf<WallpaperParallaxGroup>()
        var foundRootLayer: CamlLayer? = null

        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                when {
                    element.tagName.endsWith("Layer") -> {
                        if (foundRootLayer == null) {
                            foundRootLayer = parseLayer(element, statesList, transitionsList, parallaxGroups)
                        }
                    }
                    element.tagName == "states" -> {
                        statesList.addAll(parseStates(element))
                    }
                    element.tagName == "stateTransitions" -> {
                        transitionsList.addAll(parseStateTransitions(element))
                    }
                    element.tagName == "style" -> {
                        parallaxGroups.addAll(parseParallaxGroups(element))
                    }
                    else -> {
                        report?.recordSkipped(element.tagName, "Top-level element under <caml>")
                    }
                }
            }
        }

        val finalRoot = foundRootLayer ?: StandardCALayer(
            id = "fallbackRoot",
            name = "Empty Root",
            bounds = Rect4f(0f, 0f, 390f, 844f)
        )

        report?.statesCount = statesList.size
        return CamlLayerTree(
            rootLayer = finalRoot,
            states = statesList,
            stateTransitions = transitionsList,
            parallaxGroups = parallaxGroups
        )
    }

    private fun parseLayer(
        element: Element,
        outStates: MutableList<CamlState>,
        outTransitions: MutableList<CamlStateTransition>,
        outParallax: MutableList<WallpaperParallaxGroup> = mutableListOf()
    ): CamlLayer {
        val tagName = element.tagName.substringAfter(":")
        val id = element.getAttribute("id").ifEmpty { element.getAttribute("name").ifEmpty { null } }
        val name = element.getAttribute("name").ifEmpty { null }
        val bounds = parseRect(element.getAttribute("bounds")) ?: Rect4f()
        val position = parsePoint(element.getAttribute("position")) ?: Point2f(bounds.width / 2f, bounds.height / 2f)
        val anchorPoint = parsePoint(element.getAttribute("anchorPoint")) ?: Point2f(0.5f, 0.5f)
        val anchorPointZ = element.getAttribute("anchorPointZ").toFloatOrNull() ?: 0f
        val zPosition = element.getAttribute("zPosition").toFloatOrNull() ?: 0f
        val opacity = element.getAttribute("opacity").toFloatOrNull() ?: 1f
        val hidden = parseBoolean(element.getAttribute("hidden"))
        val masksToBounds = parseBoolean(element.getAttribute("masksToBounds"))
        val cornerRadius = element.getAttribute("cornerRadius").toFloatOrNull() ?: 0f
        val contentsGravity = element.getAttribute("contentsGravity").ifEmpty { "resize" }
        val contentsRect = parseRect(element.getAttribute("contentsRect")) ?: Rect4f(0f, 0f, 1f, 1f)
        var backgroundColor = parseColor(element.getAttribute("backgroundColor"))
        val transform = parseTransform(element)
        val sublayerTransform = parseSublayerTransform(element)
        val geometryFlipped = parseBoolean(element.getAttribute("geometryFlipped"))
        val allowsGroupOpacity = if (element.hasAttribute("allowsGroupOpacity")) parseBoolean(element.getAttribute("allowsGroupOpacity")) else true
        val borderWidth = element.getAttribute("borderWidth").toFloatOrNull() ?: 0f
        val borderColor = parseColor(element.getAttribute("borderColor"))
        val shadowColor = parseColor(element.getAttribute("shadowColor"))
        val shadowOpacity = element.getAttribute("shadowOpacity").toFloatOrNull() ?: 0f
        val shadowOffset = parsePoint(element.getAttribute("shadowOffset")) ?: Point2f(0f, -3f)
        val shadowRadius = element.getAttribute("shadowRadius").toFloatOrNull() ?: 3f
        val shadowPath = element.getAttribute("shadowPath").ifEmpty { null }
        val speed = element.getAttribute("speed").toDoubleOrNull() ?: 1.0
        val timeOffset = element.getAttribute("timeOffset").toDoubleOrNull() ?: 0.0
        val beginTime = element.getAttribute("beginTime").toDoubleOrNull() ?: 0.0
        val repeatCount = element.getAttribute("repeatCount").toFloatOrNull() ?: 0f
        val repeatDuration = element.getAttribute("repeatDuration").toDoubleOrNull() ?: 0.0
        val autoreverses = parseBoolean(element.getAttribute("autoreverses"))

        var contents: CamlContents? = null
        var mask: CamlLayer? = null
        val sublayers = mutableListOf<CamlLayer>()
        val animations = mutableListOf<CamlAnimation>()
        val filters = mutableListOf<CamlFilter>()

        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val child = node as Element
            val childTag = child.tagName.substringAfter(":")

            when (childTag) {
                "sublayers" -> {
                    val subChildren = child.childNodes
                    for (j in 0 until subChildren.length) {
                        val subNode = subChildren.item(j)
                        if (subNode.nodeType == Node.ELEMENT_NODE) {
                            sublayers.add(parseLayer(subNode as Element, outStates, outTransitions, outParallax))
                        }
                    }
                }
                "contents" -> {
                    contents = parseContents(child)
                }
                "animations" -> {
                    animations.addAll(parseAnimations(child))
                }
                "states" -> {
                    outStates.addAll(parseStates(child))
                }
                "stateTransitions" -> {
                    outTransitions.addAll(parseStateTransitions(child))
                }
                "style" -> {
                    outParallax.addAll(parseParallaxGroups(child))
                }
                "backgroundColor" -> {
                    // Sometimes background color is a child <CGColor ...>
                    parseChildColor(child)?.let { backgroundColor = it }
                }
                "filters" -> {
                    filters.addAll(parseFilters(child))
                }
                "mask" -> {
                    val mChildren = child.childNodes
                    for (m in 0 until mChildren.length) {
                        val mNode = mChildren.item(m)
                        if (mNode.nodeType == Node.ELEMENT_NODE) {
                            mask = parseLayer(mNode as Element, outStates, outTransitions, outParallax)
                            break
                        }
                    }
                }
                else -> {
                    // Check if it's an inline sublayer
                    if (childTag.endsWith("Layer")) {
                        sublayers.add(parseLayer(child, outStates, outTransitions, outParallax))
                    } else {
                        report?.recordSkipped(childTag, "Child element of $tagName")
                    }
                }
            }
        }

        report?.recordLayer(tagName)
        report?.animationsCount = (report?.animationsCount ?: 0) + animations.size

        return when (tagName) {
            "CALayer" -> StandardCALayer(
                id, name, bounds, position, anchorPoint, zPosition, opacity, hidden,
                masksToBounds, cornerRadius, backgroundColor, contents, transform, sublayerTransform,
                geometryFlipped, sublayers, animations, filters,
                anchorPointZ, contentsGravity, contentsRect, mask,
                speed, timeOffset, beginTime, repeatCount, repeatDuration, autoreverses,
                allowsGroupOpacity, borderWidth, borderColor, shadowColor, shadowOpacity,
                shadowOffset, shadowRadius, shadowPath
            )
            "CAReplicatorLayer" -> {
                val instanceCount = element.getAttribute("instanceCount").toIntOrNull() ?: 1
                val preservesDepth = parseBoolean(element.getAttribute("preservesDepth"))
                val instanceDelay = element.getAttribute("instanceDelay").toDoubleOrNull() ?: 0.0
                val instanceTransformStr = element.getAttribute("instanceTransform")
                val instanceTransform = if (instanceTransformStr.isNotEmpty()) {
                    CATransform3D.parse(instanceTransformStr) ?: CATransform3D.IDENTITY
                } else CATransform3D.IDENTITY
                val instanceColor = parseColor(element.getAttribute("instanceColor"))
                val instanceRedOffset = element.getAttribute("instanceRedOffset").toFloatOrNull() ?: 0f
                val instanceGreenOffset = element.getAttribute("instanceGreenOffset").toFloatOrNull() ?: 0f
                val instanceBlueOffset = element.getAttribute("instanceBlueOffset").toFloatOrNull() ?: 0f
                val instanceAlphaOffset = element.getAttribute("instanceAlphaOffset").toFloatOrNull() ?: 0f

                CAReplicatorLayer(
                    id, name, bounds, position, anchorPoint, zPosition, opacity, hidden,
                    masksToBounds, cornerRadius, backgroundColor, contents, transform, sublayerTransform,
                    geometryFlipped, sublayers, animations, filters,
                    anchorPointZ, contentsGravity, contentsRect, mask,
                    speed, timeOffset, beginTime, repeatCount, repeatDuration, autoreverses,
                    allowsGroupOpacity, instanceCount, preservesDepth, instanceDelay,
                    instanceTransform, instanceColor, instanceRedOffset, instanceGreenOffset,
                    instanceBlueOffset, instanceAlphaOffset
                )
            }
            "CATextLayer" -> {
                val text = element.getAttribute("string").ifEmpty {
                    findChildText(element)
                }
                val fontSize = element.getAttribute("fontSize").toFloatOrNull() ?: 14f
                val fontName = element.getAttribute("font").ifEmpty { null }
                val foregroundColor = parseColor(element.getAttribute("foregroundColor"))
                val alignmentMode = element.getAttribute("alignmentMode").ifEmpty { "natural" }
                val isWrapped = parseBoolean(element.getAttribute("wrapped")) || parseBoolean(element.getAttribute("isWrapped"))
                val truncationMode = element.getAttribute("truncationMode").ifEmpty { "none" }
                val allowsSubpixel = parseBoolean(element.getAttribute("allowsFontSubpixelQuantization"))
                CATextLayer(
                    id, name, bounds, position, anchorPoint, zPosition, opacity, hidden,
                    masksToBounds, cornerRadius, backgroundColor, contents, transform, sublayerTransform,
                    geometryFlipped, sublayers, animations, filters,
                    text, fontSize, fontName, foregroundColor, alignmentMode,
                    isWrapped, truncationMode, allowsSubpixel
                )
            }
            "CAGradientLayer" -> {
                val colors = parseGradientColors(element)
                val locations = parseFloats(element.getAttribute("locations"))
                val startPoint = parsePoint(element.getAttribute("startPoint")) ?: Point2f(0.5f, 0f)
                val endPoint = parsePoint(element.getAttribute("endPoint")) ?: Point2f(0.5f, 1f)
                var gradientType = element.getAttribute("type").ifEmpty { "axial" }
                findDirectChild(element, "type")?.let { typeNode ->
                    val tVal = typeNode.getAttribute("value").ifEmpty { typeNode.textContent.trim() }
                    if (tVal.isNotEmpty()) gradientType = tVal
                }
                CAGradientLayer(
                    id, name, bounds, position, anchorPoint, zPosition, opacity, hidden,
                    masksToBounds, cornerRadius, backgroundColor, contents, transform, sublayerTransform,
                    geometryFlipped, sublayers, animations, filters,
                    colors, locations, startPoint, endPoint, gradientType
                )
            }
            "CAShapeLayer" -> {
                val pathData = element.getAttribute("path").ifEmpty { null }
                val fillColor = parseColor(element.getAttribute("fillColor"))
                val strokeColor = parseColor(element.getAttribute("strokeColor"))
                val lineWidth = element.getAttribute("lineWidth").toFloatOrNull() ?: 1f
                val strokeStart = element.getAttribute("strokeStart").toFloatOrNull() ?: 0f
                val strokeEnd = element.getAttribute("strokeEnd").toFloatOrNull() ?: 1f
                val fillRule = element.getAttribute("fillRule").ifEmpty { "non-zero" }
                val lineCap = element.getAttribute("lineCap").ifEmpty { "butt" }
                val lineJoin = element.getAttribute("lineJoin").ifEmpty { "miter" }
                val miterLimit = element.getAttribute("miterLimit").toFloatOrNull() ?: 10f
                val lineDashPattern = parseFloats(element.getAttribute("lineDashPattern"))
                val lineDashPhase = element.getAttribute("lineDashPhase").toFloatOrNull() ?: 0f
                CAShapeLayer(
                    id, name, bounds, position, anchorPoint, zPosition, opacity, hidden,
                    masksToBounds, cornerRadius, backgroundColor, contents, transform, sublayerTransform,
                    geometryFlipped, sublayers, animations, filters,
                    pathData, fillColor, strokeColor, lineWidth,
                    strokeStart, strokeEnd, fillRule, lineCap, lineJoin, miterLimit,
                    lineDashPattern, lineDashPhase
                )
            }
            "CATransformLayer" -> CATransformLayer(
                id, name, bounds, position, anchorPoint, zPosition, opacity, hidden,
                masksToBounds, cornerRadius, backgroundColor, contents, transform, sublayerTransform,
                geometryFlipped, sublayers, animations, filters
            )
            "CAEmitterLayer" -> {
                val emitterPosition = parsePoint(element.getAttribute("emitterPosition")) ?: Point2f()
                val emitterZPosition = element.getAttribute("emitterZPosition").toFloatOrNull() ?: 0f
                val emitterSize = parsePoint(element.getAttribute("emitterSize")) ?: Point2f()
                val emitterDepth = element.getAttribute("emitterDepth").toFloatOrNull() ?: 0f
                val emitterShape = element.getAttribute("emitterShape").ifEmpty { "point" }
                val renderMode = element.getAttribute("renderMode").ifEmpty { "unordered" }
                val scale = element.getAttribute("scale").toFloatOrNull() ?: 1f
                val birthRate = element.getAttribute("birthRate").toFloatOrNull() ?: 1f
                val velocity = element.getAttribute("velocity").toFloatOrNull() ?: 1f
                val lifetime = element.getAttribute("lifetime").toFloatOrNull() ?: 1f
                val spinBias = element.getAttribute("spinBias").toFloatOrNull() ?: 0f

                val emitterCells = parseEmitterCells(element)
                val emitterBehaviors = parseEmitterBehaviors(element)

                CAEmitterLayer(
                    id, name, bounds, position, anchorPoint, zPosition, opacity, hidden,
                    masksToBounds, cornerRadius, backgroundColor, contents, transform, sublayerTransform,
                    geometryFlipped, sublayers, animations, filters,
                    speed, beginTime, timeOffset,
                    emitterPosition, emitterZPosition, emitterSize, emitterDepth, emitterShape,
                    renderMode, scale, birthRate, velocity, lifetime, spinBias, emitterCells, emitterBehaviors
                )
            }
            else -> {
                val attrs = mutableMapOf<String, String>()
                for (a in 0 until element.attributes.length) {
                    val attr = element.attributes.item(a)
                    attrs[attr.nodeName] = attr.nodeValue
                }
                UnknownLayer(
                    rawTag = tagName,
                    id = id, name = name, bounds = bounds, position = position,
                    anchorPoint = anchorPoint, zPosition = zPosition, opacity = opacity,
                    hidden = hidden, masksToBounds = masksToBounds, cornerRadius = cornerRadius,
                    backgroundColor = backgroundColor, contents = contents, transform = transform,
                    sublayerTransform = sublayerTransform, geometryFlipped = geometryFlipped,
                    sublayers = sublayers, animations = animations, filters = filters, attributes = attrs
                )
            }
        }
    }

    private fun parseContents(element: Element): CamlContents? {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                val tag = el.tagName.substringAfter(":")
                if (tag == "CGImage") {
                    val rawSrc = el.getAttribute("src")
                    if (rawSrc.isNotEmpty()) {
                        val src = try { java.net.URLDecoder.decode(rawSrc, "UTF-8") } catch (e: Exception) { rawSrc }
                        return CamlContents.CGImage(src)
                    }
                }
            }
        }
        val rawSrc = element.getAttribute("src")
        if (rawSrc.isNotEmpty()) {
            val src = try { java.net.URLDecoder.decode(rawSrc, "UTF-8") } catch (e: Exception) { rawSrc }
            return CamlContents.CGImage(src)
        }
        return null
    }

    private fun parseStates(element: Element): List<CamlState> {
        val list = mutableListOf<CamlState>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val el = node as Element
            if (el.tagName.substringAfter(":") == "LKState") {
                val stateName = el.getAttribute("name").ifEmpty { "Default" }
                val setValues = mutableListOf<CamlStateSetValue>()

                val elChildren = el.childNodes
                for (j in 0 until elChildren.length) {
                    val jNode = elChildren.item(j)
                    if (jNode.nodeType != Node.ELEMENT_NODE) continue
                    val jEl = jNode as Element
                    if (jEl.tagName.substringAfter(":") == "elements") {
                        val setValChildren = jEl.childNodes
                        for (k in 0 until setValChildren.length) {
                            val kNode = setValChildren.item(k)
                            if (kNode.nodeType != Node.ELEMENT_NODE) continue
                            val kEl = kNode as Element
                            if (kEl.tagName.substringAfter(":") == "LKStateSetValue") {
                                val targetId = kEl.getAttribute("targetId")
                                val keyPath = kEl.getAttribute("keyPath")
                                if (keyPath == "sublayers") {
                                    parseSublayersStateValue(kEl, targetId, setValues)
                                } else {
                                    val value = parseStateValue(kEl)
                                    setValues.add(CamlStateSetValue(targetId, keyPath, value))
                                }
                            }
                        }
                    }
                }
                list.add(CamlState(name = stateName, setValues = setValues))
            }
        }
        return list
    }

    private fun parseSublayersStateValue(
        element: Element,
        parentTargetId: String,
        outSetValues: MutableList<CamlStateSetValue>
    ) {
        val children = element.childNodes
        var valueElem: Element? = null
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                if (el.tagName.substringAfter(":") == "value") {
                    valueElem = el
                    break
                }
            }
        }
        if (valueElem == null) return

        val sublayerOrder = mutableListOf<String>()
        val valChildren = valueElem.childNodes
        for (i in 0 until valChildren.length) {
            val node = valChildren.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val childEl = node as Element
            if (childEl.tagName.substringAfter(":") == "CALayer") {
                val objectId = childEl.getAttribute("object").ifEmpty { null }
                if (objectId != null) {
                    sublayerOrder.add(objectId)
                } else {
                    val layerId = childEl.getAttribute("id").ifEmpty { childEl.getAttribute("name").ifEmpty { null } }
                    if (layerId != null) {
                        sublayerOrder.add(layerId)
                    }
                    parseInlineLayerOverrides(childEl, layerId, outSetValues)
                }
            }
        }

        if (sublayerOrder.isNotEmpty()) {
            outSetValues.add(CamlStateSetValue(parentTargetId, "sublayers", sublayerOrder))
        }
    }

    private fun parseInlineLayerOverrides(
        element: Element,
        layerId: String?,
        outSetValues: MutableList<CamlStateSetValue>
    ) {
        if (layerId == null) return

        if (element.hasAttribute("position")) {
            parsePoint(element.getAttribute("position"))?.let {
                outSetValues.add(CamlStateSetValue(layerId, "position", it))
            }
        }
        if (element.hasAttribute("bounds")) {
            parseRect(element.getAttribute("bounds"))?.let {
                outSetValues.add(CamlStateSetValue(layerId, "bounds", it))
            }
        }
        if (element.hasAttribute("transform")) {
            val trStr = element.getAttribute("transform")
            val tr = CATransform3D.parse(trStr)
            if (tr != null) {
                outSetValues.add(CamlStateSetValue(layerId, "transform", tr))
            }
        }
        if (element.hasAttribute("opacity")) {
            element.getAttribute("opacity").toFloatOrNull()?.let {
                outSetValues.add(CamlStateSetValue(layerId, "opacity", it))
            }
        }
        if (element.hasAttribute("hidden")) {
            val h = element.getAttribute("hidden")
            outSetValues.add(CamlStateSetValue(layerId, "hidden", h == "1" || h.equals("true", ignoreCase = true)))
        }
        if (element.hasAttribute("cornerRadius")) {
            element.getAttribute("cornerRadius").toFloatOrNull()?.let {
                outSetValues.add(CamlStateSetValue(layerId, "cornerRadius", it))
            }
        }
        if (element.hasAttribute("shadowOpacity")) {
            element.getAttribute("shadowOpacity").toFloatOrNull()?.let {
                outSetValues.add(CamlStateSetValue(layerId, "shadowOpacity", it))
            }
        }
        if (element.hasAttribute("zPosition")) {
            element.getAttribute("zPosition").toFloatOrNull()?.let {
                outSetValues.add(CamlStateSetValue(layerId, "zPosition", it))
            }
        }
        if (element.hasAttribute("anchorPoint")) {
            parsePoint(element.getAttribute("anchorPoint"))?.let {
                outSetValues.add(CamlStateSetValue(layerId, "anchorPoint", it))
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val childEl = node as Element
            val tagName = childEl.tagName.substringAfter(":")
            if (tagName == "sublayers") {
                val subChildren = childEl.childNodes
                for (j in 0 until subChildren.length) {
                    val subNode = subChildren.item(j)
                    if (subNode.nodeType != Node.ELEMENT_NODE) continue
                    val subEl = subNode as Element
                    if (subEl.tagName.substringAfter(":") == "CALayer") {
                        val subId = subEl.getAttribute("id").ifEmpty { subEl.getAttribute("name").ifEmpty { null } }
                        parseInlineLayerOverrides(subEl, subId, outSetValues)
                    }
                }
            }
        }
    }

    private fun parseStateValue(element: Element): Any? {
        val valAttr = element.getAttribute("value")
        if (valAttr.isNotEmpty()) {
            return valAttr
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                val type = el.getAttribute("type")
                val valueStr = el.getAttribute("value").ifEmpty { el.textContent.trim() }

                val typeLow = type.lowercase()
                return when (typeLow) {
                    "integer", "int" -> valueStr.toIntOrNull() ?: valueStr.toFloatOrNull()
                    "real", "float", "double" -> valueStr.toFloatOrNull()
                    "point", "cgpoint" -> parsePoint(valueStr)
                    "rect", "cgrect" -> parseRect(valueStr)
                    "color", "cgcolor" -> parseColor(valueStr)
                    else -> valueStr.toFloatOrNull() ?: valueStr
                }
            }
        }
        return null
    }

    private fun parseStateTransitions(element: Element): List<CamlStateTransition> {
        val list = mutableListOf<CamlStateTransition>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val el = node as Element
            if (el.tagName.substringAfter(":") == "LKStateTransition") {
                val from = el.getAttribute("fromState").ifEmpty { "*" }
                val to = el.getAttribute("toState").ifEmpty { "*" }
                val transitionElements = mutableListOf<CamlStateTransitionElement>()

                val elChildren = el.childNodes
                for (j in 0 until elChildren.length) {
                    val jNode = elChildren.item(j)
                    if (jNode.nodeType != Node.ELEMENT_NODE) continue
                    val jEl = jNode as Element
                    if (jEl.tagName.substringAfter(":") == "elements") {
                        val transChildren = jEl.childNodes
                        for (k in 0 until transChildren.length) {
                            val kNode = transChildren.item(k)
                            if (kNode.nodeType != Node.ELEMENT_NODE) continue
                            val kEl = kNode as Element
                            if (kEl.tagName.substringAfter(":") == "LKStateTransitionElement") {
                                transitionElements.add(
                                    CamlStateTransitionElement(
                                        key = kEl.getAttribute("key").ifEmpty { null },
                                        targetId = kEl.getAttribute("targetId").ifEmpty { null },
                                        duration = kEl.getAttribute("duration").toFloatOrNull(),
                                        timingFunction = kEl.getAttribute("timingFunction").ifEmpty { null },
                                        mass = kEl.getAttribute("mass").toFloatOrNull(),
                                        stiffness = kEl.getAttribute("stiffness").toFloatOrNull(),
                                        damping = kEl.getAttribute("damping").toFloatOrNull(),
                                        velocity = kEl.getAttribute("velocity").toFloatOrNull()
                                    )
                                )
                            }
                        }
                    }
                }
                list.add(CamlStateTransition(from, to, transitionElements))
            }
        }
        return list
    }

    private fun parseAnimations(element: Element): List<CamlAnimation> {
        val list = mutableListOf<CamlAnimation>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val el = node as Element
            val key = el.getAttribute("key").ifEmpty { "anim_$i" }
            val type = el.getAttribute("type").ifEmpty { "CAKeyframeAnimation" }
            val keyPath = el.getAttribute("keyPath")
            val beginTime = el.getAttribute("beginTime").toDoubleOrNull() ?: 0.0
            val duration = el.getAttribute("duration").toDoubleOrNull() ?: 0.0
            val speed = el.getAttribute("speed").toDoubleOrNull() ?: 1.0
            val timeOffset = el.getAttribute("timeOffset").toDoubleOrNull() ?: 0.0
            val repeatCount = el.getAttribute("repeatCount").ifEmpty { "1" }
            val repeatDuration = el.getAttribute("repeatDuration").toDoubleOrNull() ?: 0.0
            val autoreverses = parseBoolean(el.getAttribute("autoreverses"))
            val fillMode = el.getAttribute("fillMode").ifEmpty { "removed" }
            val timingFunction = el.getAttribute("timingFunction").ifEmpty { "linear" }
            val calculationMode = el.getAttribute("calculationMode").ifEmpty { "linear" }

            val values = mutableListOf<Any>()
            val keyTimes = mutableListOf<Float>()
            val timingFunctions = mutableListOf<String>()

            val animChildren = el.childNodes
            for (j in 0 until animChildren.length) {
                val aNode = animChildren.item(j)
                if (aNode.nodeType != Node.ELEMENT_NODE) continue
                val aEl = aNode as Element
                when (aEl.tagName.substringAfter(":")) {
                    "values" -> {
                        val vChildren = aEl.childNodes
                        for (k in 0 until vChildren.length) {
                            val vNode = vChildren.item(k)
                            if (vNode.nodeType != Node.ELEMENT_NODE) continue
                            val vEl = vNode as Element
                            val valStr = vEl.getAttribute("value").ifEmpty { vEl.textContent.trim() }
                            val num = valStr.toFloatOrNull()
                            if (num != null) {
                                values.add(num)
                            } else {
                                values.add(valStr)
                            }
                        }
                    }
                    "keyTimes" -> {
                        val kChildren = aEl.childNodes
                        for (k in 0 until kChildren.length) {
                            val kNode = kChildren.item(k)
                            if (kNode.nodeType != Node.ELEMENT_NODE) continue
                            val kEl = kNode as Element
                            val valStr = kEl.getAttribute("value").ifEmpty { kEl.textContent.trim() }
                            valStr.toFloatOrNull()?.let { keyTimes.add(it) }
                        }
                    }
                    "timingFunctions" -> {
                        val tfChildren = aEl.childNodes
                        for (k in 0 until tfChildren.length) {
                            val tfNode = tfChildren.item(k)
                            if (tfNode.nodeType != Node.ELEMENT_NODE) continue
                            val tfEl = tfNode as Element
                            val tfVal = tfEl.getAttribute("value").ifEmpty { tfEl.textContent.trim() }
                            if (tfVal.isNotEmpty()) {
                                timingFunctions.add(tfVal)
                            }
                        }
                    }
                }
            }

            list.add(
                CamlAnimation(
                    key = key,
                    type = type,
                    keyPath = keyPath,
                    beginTime = beginTime,
                    duration = duration,
                    speed = speed,
                    timeOffset = timeOffset,
                    repeatCount = repeatCount,
                    repeatDuration = repeatDuration,
                    autoreverses = autoreverses,
                    fillMode = fillMode,
                    values = values,
                    keyTimes = keyTimes,
                    timingFunction = timingFunction,
                    timingFunctions = timingFunctions,
                    calculationMode = calculationMode
                )
            )
        }
        return list
    }

    private fun parseTransform(element: Element): CATransform3D {
        val transformAttr = element.getAttribute("transform")
        if (transformAttr.isNotEmpty()) {
            CATransform3D.parse(transformAttr)?.let { return it }
        }
        var t = CATransform3D.IDENTITY
        val rotX = element.getAttribute("transform.rotation.x").toFloatOrNull()
        val rotY = element.getAttribute("transform.rotation.y").toFloatOrNull()
        val rotZ = element.getAttribute("transform.rotation.z").toFloatOrNull()
            ?: element.getAttribute("transform.rotation").toFloatOrNull()?.let { Math.toRadians(it.toDouble()).toFloat() }
        val scaleX = element.getAttribute("transform.scale.x").toFloatOrNull()
            ?: element.getAttribute("transform.scale").toFloatOrNull() ?: 1f
        val scaleY = element.getAttribute("transform.scale.y").toFloatOrNull()
            ?: element.getAttribute("transform.scale").toFloatOrNull() ?: 1f

        if (scaleX != 1f || scaleY != 1f) {
            t = t.scale(scaleX, scaleY)
        }
        if (rotX != null && rotX != 0f) {
            t = t.rotateX(rotX)
        }
        if (rotY != null && rotY != 0f) {
            t = t.rotateY(rotY)
        }
        if (rotZ != null && rotZ != 0f) {
            t = t.rotateZ(rotZ)
        }
        return t
    }

    private fun parseSublayerTransform(element: Element): CATransform3D {
        val str = element.getAttribute("sublayerTransform")
        if (str.isNotEmpty()) {
            CATransform3D.parse(str)?.let { return it }
        }
        val scaleXY = element.getAttribute("sublayerTransform.scale.xy").toFloatOrNull()
        if (scaleXY != null && scaleXY != 1f) {
            return CATransform3D.makeScale(scaleXY, scaleXY)
        }
        return CATransform3D.IDENTITY
    }

    private fun parseFilters(element: Element): List<CamlFilter> {
        val list = mutableListOf<CamlFilter>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val child = node as Element
            val name = child.getAttribute("name").ifEmpty { null }
            val filterType = child.getAttribute("filter").ifEmpty { child.tagName.substringAfter(":") }
            when (filterType) {
                "colorMatrix" -> {
                    val matStr = child.getAttribute("inputColorMatrix")
                    if (matStr.isNotEmpty()) {
                        CamlFilter.ColorMatrix.parse(matStr, name)?.let { list.add(it) }
                    }
                }
                "colorContrast" -> {
                    val amount = child.getAttribute("inputAmount").toFloatOrNull() ?: 1.0f
                    list.add(CamlFilter.ColorContrast(name, amount))
                }
                "gaussianBlur" -> {
                    val radius = child.getAttribute("inputRadius").toFloatOrNull() ?: 0.0f
                    list.add(CamlFilter.GaussianBlur(name, radius))
                }
                else -> {
                    val attrs = mutableMapOf<String, String>()
                    for (a in 0 until child.attributes.length) {
                        val attr = child.attributes.item(a)
                        attrs[attr.nodeName] = attr.nodeValue
                    }
                    list.add(CamlFilter.Unknown(name, filterType, attrs))
                }
            }
        }
        return list
    }

    private fun parseEmitterCells(element: Element): List<CAEmitterCell> {
        val list = mutableListOf<CAEmitterCell>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val child = node as Element
            val tag = child.tagName.substringAfter(":")
            if (tag == "emitterCells") {
                val cellNodes = child.childNodes
                for (j in 0 until cellNodes.length) {
                    val cellNode = cellNodes.item(j)
                    if (cellNode.nodeType == Node.ELEMENT_NODE) {
                        parseSingleCell(cellNode as Element)?.let { list.add(it) }
                    }
                }
            } else if (tag == "CAEmitterCell") {
                parseSingleCell(child)?.let { list.add(it) }
            }
        }
        return list
    }

    private fun parseSingleCell(element: Element): CAEmitterCell? {
        val name = element.getAttribute("name").ifEmpty { null }
        val birthRate = element.getAttribute("birthRate").toFloatOrNull() ?: 0f
        val lifetime = element.getAttribute("lifetime").toFloatOrNull() ?: 1f
        val lifetimeRange = element.getAttribute("lifetimeRange").toFloatOrNull() ?: 0f
        val velocity = element.getAttribute("velocity").toFloatOrNull() ?: 0f
        val velocityRange = element.getAttribute("velocityRange").toFloatOrNull() ?: 0f
        val xAcc = element.getAttribute("xAcceleration").toFloatOrNull() ?: 0f
        val yAcc = element.getAttribute("yAcceleration").toFloatOrNull() ?: 0f
        val zAcc = element.getAttribute("zAcceleration").toFloatOrNull() ?: 0f
        val scale = element.getAttribute("scale").toFloatOrNull() ?: 1f
        val scaleRange = element.getAttribute("scaleRange").toFloatOrNull() ?: 0f
        val scaleSpeed = element.getAttribute("scaleSpeed").toFloatOrNull() ?: 0f
        val spin = element.getAttribute("spin").toFloatOrNull() ?: 0f
        val spinRange = element.getAttribute("spinRange").toFloatOrNull() ?: 0f
        val alphaRange = element.getAttribute("alphaRange").toFloatOrNull() ?: 0f
        val alphaSpeed = element.getAttribute("alphaSpeed").toFloatOrNull() ?: 0f
        val emLat = element.getAttribute("emissionLatitude").toFloatOrNull() ?: 0f
        val emLong = element.getAttribute("emissionLongitude").toFloatOrNull() ?: 0f
        val emRange = element.getAttribute("emissionRange").toFloatOrNull() ?: 0f
        val rotation = element.getAttribute("rotation").toFloatOrNull() ?: 0f
        val rotationRange = element.getAttribute("rotationRange").toFloatOrNull() ?: 0f
        val enabled = if (element.hasAttribute("enabled")) parseBoolean(element.getAttribute("enabled")) else true
        val contentsScale = element.getAttribute("contentsScale").toFloatOrNull() ?: 1f
        val speed = element.getAttribute("speed").toFloatOrNull() ?: 1f

        val contentsFrameCount = element.getAttribute("contentsFrameCount").toIntOrNull() ?: 1
        val contentsFramesPerRow = element.getAttribute("contentsFramesPerRow").toIntOrNull() ?: 1
        val contentsRect = parseRect(element.getAttribute("contentsRect"))

        var cellColor = parseColor(element.getAttribute("color"))
        var contents: CamlContents? = null
        val subCells = mutableListOf<CAEmitterCell>()

        val cellChildren = element.childNodes
        for (i in 0 until cellChildren.length) {
            val cn = cellChildren.item(i)
            if (cn.nodeType == Node.ELEMENT_NODE) {
                val cel = cn as Element
                when (cel.tagName.substringAfter(":")) {
                    "contents" -> contents = parseContents(cel)
                    "color" -> parseChildColor(cel)?.let { cellColor = it }
                    "emitterCells" -> {
                        val scNodes = cel.childNodes
                        for (k in 0 until scNodes.length) {
                            val sc = scNodes.item(k)
                            if (sc.nodeType == Node.ELEMENT_NODE) {
                                parseSingleCell(sc as Element)?.let { subCells.add(it) }
                            }
                        }
                    }
                    "CAEmitterCell" -> parseSingleCell(cel)?.let { subCells.add(it) }
                }
            }
        }

        val behaviors = parseEmitterBehaviors(element)

        return CAEmitterCell(
            name, birthRate, lifetime, lifetimeRange, velocity, velocityRange,
            xAcc, yAcc, zAcc, scale, scaleRange, scaleSpeed, spin, spinRange,
            alphaRange, alphaSpeed, emLat, emLong, emRange,
            contents, contentsFrameCount, contentsFramesPerRow, contentsRect,
            cellColor, rotation, rotationRange, behaviors, enabled, subCells,
            contentsScale, speed
        )
    }

    private fun parseEmitterBehaviors(element: Element): List<CAEmitterBehavior> {
        val list = mutableListOf<CAEmitterBehavior>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val child = node as Element
            val tag = child.tagName.substringAfter(":")
            if (tag == "emitterBehaviors") {
                val bNodes = child.childNodes
                for (j in 0 until bNodes.length) {
                    val bn = bNodes.item(j)
                    if (bn.nodeType == Node.ELEMENT_NODE) {
                        parseSingleBehavior(bn as Element)?.let { list.add(it) }
                    }
                }
            } else if (tag == "CAEmitterBehavior") {
                parseSingleBehavior(child)?.let { list.add(it) }
            }
        }
        return list
    }

    private fun parseSingleBehavior(element: Element): CAEmitterBehavior? {
        val name = element.getAttribute("name").ifEmpty { null }
        val behavior = element.getAttribute("behavior").ifEmpty { "" }
        val force = Vector3f.parse(element.getAttribute("force")) ?: Vector3f.ZERO
        val freq = element.getAttribute("frequency").toFloatOrNull() ?: 1f
        val lat = element.getAttribute("orientationLatitude").toFloatOrNull() ?: 0f
        val long = element.getAttribute("orientationLongitude").toFloatOrNull() ?: 0f

        val attrs = mutableMapOf<String, String>()
        for (a in 0 until element.attributes.length) {
            val attr = element.attributes.item(a)
            attrs[attr.nodeName] = attr.nodeValue
        }
        return CAEmitterBehavior(name, behavior, force, freq, lat, long, attrs)
    }

    private fun parseGradientColors(element: Element): List<Color4f> {
        val list = mutableListOf<Color4f>()
        val colorsAttr = element.getAttribute("colors")
        if (colorsAttr.isNotEmpty()) {
            val parts = colorsAttr.split(",").map { it.trim() }
            for (p in parts) {
                Color4f.parse(p)?.let { list.add(it) }
            }
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                if (el.tagName.substringAfter(":") == "colors") {
                    val cChildren = el.childNodes
                    for (j in 0 until cChildren.length) {
                        val cNode = cChildren.item(j)
                        if (cNode.nodeType == Node.ELEMENT_NODE) {
                            parseChildColor(cNode as Element)?.let { list.add(it) }
                        }
                    }
                }
            }
        }
        return list
    }

    private fun parseChildColor(element: Element): Color4f? {
        val valStr = element.getAttribute("value").ifEmpty { element.textContent.trim() }
        val color = Color4f.parse(valStr) ?: return null
        val opacityAttr = element.getAttribute("opacity")
        return if (opacityAttr.isNotEmpty()) {
            val op = opacityAttr.toFloatOrNull() ?: 1f
            color.copy(a = (color.a * op).coerceIn(0f, 1f))
        } else {
            color
        }
    }

    private fun findDirectChild(element: Element, tag: String): Element? {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                if (el.tagName.substringAfter(":") == tag) return el
            }
        }
        return null
    }

    private fun parseParallaxGroups(element: Element): List<WallpaperParallaxGroup> {
        val list = mutableListOf<WallpaperParallaxGroup>()
        val dicts = element.getElementsByTagName("NSDictionary")
        for (i in 0 until dicts.length) {
            val dict = dicts.item(i) as Element
            var axis = "x"
            var keyPath = "position.x"
            var layerName = ""
            var minTo = -20f
            var maxTo = 20f
            var view: String? = null

            val children = dict.childNodes
            for (j in 0 until children.length) {
                val node = children.item(j)
                if (node.nodeType != Node.ELEMENT_NODE) continue
                val child = node as Element
                val tag = child.tagName.substringAfter(":")
                val v = child.getAttribute("value").ifEmpty { child.textContent.trim() }
                when (tag) {
                    "axis" -> axis = v.lowercase()
                    "keyPath" -> keyPath = v
                    "layerName" -> layerName = v
                    "mapMinTo" -> minTo = v.toFloatOrNull() ?: -20f
                    "mapMaxTo" -> maxTo = v.toFloatOrNull() ?: 20f
                    "view" -> view = v
                }
            }
            if (layerName.isNotEmpty()) {
                list.add(WallpaperParallaxGroup(axis, keyPath, layerName, minTo, maxTo, view))
            }
        }
        return list
    }

    private fun findChildText(element: Element): String {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.TEXT_NODE) {
                val t = node.textContent.trim()
                if (t.isNotEmpty()) return t
            }
        }
        return ""
    }

    private fun parseRect(str: String): Rect4f? {
        if (str.isEmpty()) return null
        val parts = str.trim().split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
        return if (parts.size >= 4) Rect4f(parts[0], parts[1], parts[2], parts[3]) else null
    }

    private fun parsePoint(str: String): Point2f? {
        if (str.isEmpty()) return null
        val parts = str.trim().split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
        return if (parts.size >= 2) Point2f(parts[0], parts[1]) else null
    }

    private fun parseColor(str: String): Color4f? {
        if (str.isEmpty()) return null
        return Color4f.parse(str)
    }

    private fun parseFloats(str: String): List<Float> {
        if (str.isEmpty()) return emptyList()
        return str.trim().split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
    }

    private fun parseBoolean(str: String): Boolean {
        return str == "1" || str.equals("true", ignoreCase = true)
    }
}
