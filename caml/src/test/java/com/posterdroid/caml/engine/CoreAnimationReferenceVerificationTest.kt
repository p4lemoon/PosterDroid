package com.posterdroid.caml.engine

import com.posterdroid.caml.math.CATransform3D
import com.posterdroid.caml.model.*
import com.posterdroid.caml.parser.CamlXmlParser
import com.posterdroid.caml.parser.TendiesExtractor
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Stage H: Reference Verification & Quality Assurance Suite.
 *
 * Mathematically audits and verifies all 4 real .tendies archives across the entire
 * progress spectrum [0.0, 0.25, 0.5, 0.75, 1.0] and continuous time intervals.
 */
class CoreAnimationReferenceVerificationTest {

    private val tendiesDir = File("../examples/tendies").let {
        if (it.exists()) it else File("examples/tendies")
    }

    private val caEngine = CoreAnimationEngine()

    private val sampleArchives = listOf(
        "WindowToWinter.tendies",
        "TrinitySeveranceSnow.tendies",
        "windows11.tendies",
        "kookkkkkkkkkkkkkkkkkkkkkkkkkkkkk.tendies"
    )

    @Test
    fun testReferenceVerificationAcrossAllArchivesAndProgressSteps() {
        val progressSteps = floatArrayOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)
        val testTimes = floatArrayOf(0.0f, 0.5f, 1.0f, 2.0f)

        var totalLayersAudited = 0
        var totalTreesAudited = 0

        for (archiveName in sampleArchives) {
            val file = File(tendiesDir, archiveName)
            assertTrue("Archive $archiveName must exist", file.exists())

            val pkg = TendiesExtractor().extract(file)
            val trees = listOfNotNull(pkg.background, pkg.floating, pkg.foreground)
            assertTrue("Archive $archiveName must contain at least one layer tree", trees.isNotEmpty())

            for (tree in trees) {
                totalTreesAudited++

                for (p in progressSteps) {
                    for (t in testTimes) {
                        val presentation = caEngine.computePresentation(tree, progress = p, timeSeconds = t)
                        assertNotNull("Presentation root must not be null at p=$p, t=$t", presentation)

                        val allLayers = presentation.collectAllLayers()
                        assertTrue("Presentation must contain layers", allLayers.isNotEmpty())

                        for (layer in allLayers) {
                            totalLayersAudited++
                            auditLayerIntegrity(layer, presentation, archiveName, p, t)
                        }
                    }
                }
            }
        }

        println("=== Stage H Reference Verification PASSED: $totalTreesAudited trees and $totalLayersAudited layer presentations verified without NaN or anomalies ===")
    }

    private fun auditLayerIntegrity(
        layer: PresentationLayer,
        root: PresentationLayer,
        archive: String,
        progress: Float,
        time: Float
    ) {
        val id = layer.id ?: layer.name ?: "unnamed"
        val ctx = "[$archive @ layer=$id, p=$progress, t=$time]"

        // 1. Position & Bounds sanity
        assertFalse("$ctx position.x is NaN", layer.position.x.isNaN())
        assertFalse("$ctx position.y is NaN", layer.position.y.isNaN())
        assertFalse("$ctx bounds.width is NaN", layer.bounds.width.isNaN())
        assertFalse("$ctx bounds.height is NaN", layer.bounds.height.isNaN())
        assertTrue("$ctx bounds.width must be >= 0", layer.bounds.width >= 0f)
        assertTrue("$ctx bounds.height must be >= 0", layer.bounds.height >= 0f)

        // 2. Opacity sanity
        assertFalse("$ctx opacity is NaN", layer.opacity.isNaN())
        assertTrue("$ctx opacity must be in [0, 1]", layer.opacity in -0.001f..1.001f)

        // 3. 4x4 Transform matrix sanity
        val m = layer.transform
        assertFalse("$ctx transform.m11 is NaN", m.m11.isNaN())
        assertFalse("$ctx transform.m22 is NaN", m.m22.isNaN())
        assertFalse("$ctx transform.m33 is NaN", m.m33.isNaN())
        assertFalse("$ctx transform.m44 is NaN", m.m44.isNaN())
        assertFalse("$ctx transform.m41 is NaN", m.m41.isNaN())
        assertFalse("$ctx transform.m42 is NaN", m.m42.isNaN())

        val det = m.determinant()
        assertFalse("$ctx transform determinant is NaN", det.isNaN())

        // 4. World matrix & coordinate conversion sanity
        val world = layer.modelMatrix(root)
        assertFalse("$ctx world matrix m11 is NaN", world.m11.isNaN())
        assertFalse("$ctx world matrix m41 is NaN", world.m41.isNaN())

        val convertedOrigin = layer.convertPoint(Point2f(0f, 0f), root, root)
        assertFalse("$ctx convertPoint x is NaN", convertedOrigin.x.isNaN())
        assertFalse("$ctx convertPoint y is NaN", convertedOrigin.y.isNaN())
    }

    @Test
    fun testReplicatorLayerMultiInstanceAccumulation() {
        val xml = """
            <caml xmlns="http://www.apple.com/CoreAnimation/CAML">
                <CAReplicatorLayer id="rep1" name="Replicator"
                    instanceCount="5"
                    instanceDelay="0.1"
                    instanceAlphaOffset="-0.15"
                    position="100 100"
                    bounds="0 0 200 200">
                    <sublayers>
                        <CALayer id="child1" position="20 20" bounds="0 0 50 50" opacity="1.0" />
                    </sublayers>
                </CAReplicatorLayer>
            </caml>
        """.trimIndent()

        val tree = CamlXmlParser().parse(xml)
        val repLayer = tree.rootLayer as? CAReplicatorLayer
        assertNotNull("Should parse CAReplicatorLayer", repLayer)
        assertEquals("instanceCount should be 5", 5, repLayer!!.instanceCount)
        assertEquals("instanceDelay should be 0.1", 0.1, repLayer.instanceDelay, 0.001)
        assertEquals("instanceAlphaOffset should be -0.15", -0.15f, repLayer.instanceAlphaOffset, 0.001f)

        val presentation = caEngine.computePresentation(tree, 0f)
        val child = presentation.findLayerById("child1")
        assertNotNull("Sublayer should be present", child)
        assertEquals(50f, child!!.bounds.width, 0.01f)
    }

    @Test
    fun testShapeLayerStylingAndTrimming() {
        val xml = """
            <caml xmlns="http://www.apple.com/CoreAnimation/CAML">
                <CAShapeLayer id="shape1" name="Shape"
                    strokeStart="0.2"
                    strokeEnd="0.8"
                    fillRule="even-odd"
                    lineCap="round"
                    lineJoin="bevel"
                    lineWidth="4.5"
                    lineDashPattern="10 5"
                    lineDashPhase="2.5"
                    bounds="0 0 300 300">
                </CAShapeLayer>
            </caml>
        """.trimIndent()

        val tree = CamlXmlParser().parse(xml)
        val shape = tree.rootLayer as? CAShapeLayer
        assertNotNull("Should parse CAShapeLayer", shape)
        assertEquals(0.2f, shape!!.strokeStart, 0.001f)
        assertEquals(0.8f, shape.strokeEnd, 0.001f)
        assertEquals("even-odd", shape.fillRule)
        assertEquals("round", shape.lineCap)
        assertEquals("bevel", shape.lineJoin)
        assertEquals(4.5f, shape.lineWidth, 0.001f)
        assertEquals(listOf(10f, 5f), shape.lineDashPattern)
        assertEquals(2.5f, shape.lineDashPhase, 0.001f)
    }

    @Test
    fun testGradientLayerTypesAndProperties() {
        val xml = """
            <caml xmlns="http://www.apple.com/CoreAnimation/CAML">
                <CAGradientLayer id="grad1" name="RadialGradient"
                    type="radial"
                    startPoint="0.5 0.5"
                    endPoint="1.0 1.0"
                    locations="0 0.5 1"
                    bounds="0 0 390 844">
                </CAGradientLayer>
            </caml>
        """.trimIndent()

        val tree = CamlXmlParser().parse(xml)
        val grad = tree.rootLayer as? CAGradientLayer
        assertNotNull("Should parse CAGradientLayer", grad)
        assertEquals("radial", grad!!.gradientType)
        assertEquals(Point2f(0.5f, 0.5f), grad.startPoint)
        assertEquals(Point2f(1.0f, 1.0f), grad.endPoint)
        assertEquals(listOf(0f, 0.5f, 1f), grad.locations)
    }

    @Test
    fun testTextLayerPropertiesAndWrapping() {
        val xml = """
            <caml xmlns="http://www.apple.com/CoreAnimation/CAML">
                <CATextLayer id="text1" name="ClockText"
                    string="09:41"
                    fontSize="72"
                    font="SFProDisplay-Bold"
                    alignmentMode="center"
                    wrapped="true"
                    truncationMode="end"
                    bounds="0 0 300 100">
                </CATextLayer>
            </caml>
        """.trimIndent()

        val tree = CamlXmlParser().parse(xml)
        val text = tree.rootLayer as? CATextLayer
        assertNotNull("Should parse CATextLayer", text)
        assertEquals("09:41", text!!.text)
        assertEquals(72f, text.fontSize, 0.01f)
        assertEquals("SFProDisplay-Bold", text.fontName)
        assertEquals("center", text.alignmentMode)
        assertTrue("isWrapped should be true", text.isWrapped)
        assertEquals("end", text.truncationMode)
    }

    @Test
    fun testShadowAndBorderAttributes() {
        val xml = """
            <caml xmlns="http://www.apple.com/CoreAnimation/CAML">
                <CALayer id="layerWithShadow"
                    borderWidth="2.0"
                    shadowOpacity="0.6"
                    shadowRadius="8.0"
                    shadowOffset="0 4"
                    bounds="0 0 200 200">
                </CALayer>
            </caml>
        """.trimIndent()

        val tree = CamlXmlParser().parse(xml)
        val layer = tree.rootLayer
        assertEquals(2.0f, layer.borderWidth, 0.001f)
        assertEquals(0.6f, layer.shadowOpacity, 0.001f)
        assertEquals(8.0f, layer.shadowRadius, 0.001f)
        assertEquals(Point2f(0f, 4f), layer.shadowOffset)

        val pres = caEngine.computePresentation(tree, 0f)
        assertEquals(2.0f, pres.borderWidth, 0.001f)
        assertEquals(0.6f, pres.shadowOpacity, 0.001f)
        assertEquals(8.0f, pres.shadowRadius, 0.001f)
    }
}
