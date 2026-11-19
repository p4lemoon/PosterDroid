package com.posterdroid.caml

import com.posterdroid.caml.engine.LayerInterpolator
import com.posterdroid.caml.model.CAGradientLayer
import com.posterdroid.caml.model.CAShapeLayer
import com.posterdroid.caml.model.StandardCALayer
import com.posterdroid.caml.parser.ParseReport
import com.posterdroid.caml.parser.TendiesExtractor
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TendiesParserTest {

    private val tendiesDir = File("../examples/tendies").let {
        if (it.exists()) it else File("examples/tendies")
    }

    @Test
    fun testParseWindowToWinter() {
        val file = File(tendiesDir, "WindowToWinter.tendies")
        assertTrue("WindowToWinter.tendies must exist at ${file.absolutePath}", file.exists())

        val report = ParseReport(file.name)
        val extractor = TendiesExtractor(report)
        val pkg = extractor.extract(file)

        println(report.generateSummary())

        assertNotNull("Background tree should exist", pkg.background)
        assertNotNull("Floating tree should exist", pkg.floating)
        assertNotNull("Foreground tree should exist", pkg.foreground)

        val bg = pkg.background!!
        assertTrue("Background root layer should have sublayers", bg.rootLayer.sublayers.isNotEmpty())
        assertTrue("Background should have states", bg.states.isNotEmpty())

        val lockedState = bg.findState("Locked")
        val unlockState = bg.findState("Unlock")
        assertNotNull("Should contain 'Locked' state", lockedState)
        assertNotNull("Should contain 'Unlock' state", unlockState)

        assertTrue("Assets should contain images", pkg.assets.isNotEmpty())
        println("Assets found: ${pkg.assets.keys}")

        // Test Interpolation at 0.0, 0.5, 1.0
        val interpolator = LayerInterpolator()
        val at0 = interpolator.interpolateTree(bg, 0f)
        val at50 = interpolator.interpolateTree(bg, 0.5f)
        val at100 = interpolator.interpolateTree(bg, 1f)

        fun findEvaluated(layer: com.posterdroid.caml.engine.EvaluatedLayer, id: String): com.posterdroid.caml.engine.EvaluatedLayer? {
            if (layer.originalLayer.id == id) return layer
            for (sub in layer.sublayers) {
                val found = findEvaluated(sub, id)
                if (found != null) return found
            }
            return null
        }

        val win0 = findEvaluated(at0, "48k8dziru07mlyblka6")
        val win100 = findEvaluated(at100, "48k8dziru07mlyblka6")
        println("=== DEBUG: Window layer at 0.0: pos=${win0?.position}, bounds=${win0?.bounds} ===")
        println("=== DEBUG: Window layer at 1.0: pos=${win100?.position}, bounds=${win100?.bounds} ===")

        assertNotNull(at0)
        assertNotNull(at50)
        assertNotNull(at100)
        println("Interpolation test passed: bounds=${at0.bounds} -> ${at50.bounds} -> ${at100.bounds}")
    }

    @Test
    fun testContinuousSnowflakeAnimation() {
        val file = File(tendiesDir, "WindowToWinter.tendies")
        val extractor = TendiesExtractor()
        val pkg = extractor.extract(file)
        val bg = pkg.background!!
        val interpolator = LayerInterpolator()

        fun findEvaluated(layer: com.posterdroid.caml.engine.EvaluatedLayer, id: String): com.posterdroid.caml.engine.EvaluatedLayer? {
            if (layer.originalLayer.id == id) return layer
            for (sub in layer.sublayers) {
                val found = findEvaluated(sub, id)
                if (found != null) return found
            }
            return null
        }

        val t0 = interpolator.interpolateTree(bg, 0f, timeSeconds = 0f)
        val t1 = interpolator.interpolateTree(bg, 0f, timeSeconds = 1.0f)
        val t2 = interpolator.interpolateTree(bg, 0f, timeSeconds = 2.0f)

        // 5zvijnmp88bmlybdiwm is pngshka container with position animation
        val p0 = findEvaluated(t0, "5zvijnmp88bmlybdiwm")
        val p1 = findEvaluated(t1, "5zvijnmp88bmlybdiwm")
        val p2 = findEvaluated(t2, "5zvijnmp88bmlybdiwm")

        println("=== Snowflake container: t0=${p0?.position}, t1=${p1?.position}, t2=${p2?.position} ===")
        assertNotEquals("Position should change with time", p0?.position?.x, p1?.position?.x)

        // sgjbd7qgb3fmlybdiwm is snowflake with rotation animation
        val r0 = findEvaluated(t0, "sgjbd7qgb3fmlybdiwm")
        val r1 = findEvaluated(t1, "sgjbd7qgb3fmlybdiwm")
        val r2 = findEvaluated(t2, "sgjbd7qgb3fmlybdiwm")

        println("=== Snowflake rotation: t0=${r0?.rotationZ}, t1=${r1?.rotationZ}, t2=${r2?.rotationZ} ===")
        assertNotEquals("Rotation should change with time", r0?.rotationZ, r1?.rotationZ)
    }

    @Test
    fun testParseTrinitySeveranceSnow() {
        val file = File(tendiesDir, "TrinitySeveranceSnow.tendies")
        assertTrue("TrinitySeveranceSnow.tendies must exist", file.exists())

        val report = ParseReport(file.name)
        val extractor = TendiesExtractor(report)
        val pkg = extractor.extract(file)

        println(report.generateSummary())

        assertNotNull(pkg.background)
        assertNotNull(pkg.floating)
        assertNotNull(pkg.foreground)

        val fg = pkg.foreground!!
        val allFgLayers = fg.rootLayer.collectAllLayers()
        val hasGradient = allFgLayers.any { it is CAGradientLayer }
        val hasShape = allFgLayers.any { it is CAShapeLayer }
        println("Foreground has CAGradientLayer: $hasGradient, CAShapeLayer: $hasShape")
        assertTrue("Should support CAGradientLayer or CAShapeLayer in foreground", hasGradient || hasShape)
    }

    @Test
    fun testParseWindows11() {
        val file = File(tendiesDir, "windows11.tendies")
        assertTrue("windows11.tendies must exist", file.exists())

        val report = ParseReport(file.name)
        val extractor = TendiesExtractor(report)
        val pkg = extractor.extract(file)

        println(report.generateSummary())

        assertNotNull(pkg.background)
        assertNotNull(pkg.floating)
        assertTrue("Assets should have win11.heic", pkg.assets.keys.any { it.contains("win11.heic") })
    }

    @Test
    fun testParseKook() {
        val file = File(tendiesDir, "kookkkkkkkkkkkkkkkkkkkkkkkkkkkkk.tendies")
        assertTrue("kook.tendies must exist", file.exists())

        val report = ParseReport(file.name)
        val extractor = TendiesExtractor(report)
        val pkg = extractor.extract(file)

        println(report.generateSummary())

        assertNotNull(pkg.background)
        assertNotNull(pkg.floating)
        assertNotNull(pkg.foreground)
        assertTrue("Total layers should be > 0", pkg.totalLayers > 0)
    }

    @Test
    fun testHasContinuousAnimationsDetection() {
        val extractor = TendiesExtractor()

        val winter = extractor.extract(File(tendiesDir, "WindowToWinter.tendies"))
        assertTrue("WindowToWinter should detect continuous particle animations", winter.hasContinuousAnimations)

        val trinity = extractor.extract(File(tendiesDir, "TrinitySeveranceSnow.tendies"))
        assertTrue("TrinitySeveranceSnow should detect continuous snow particle animations", trinity.hasContinuousAnimations)

        val win11 = extractor.extract(File(tendiesDir, "windows11.tendies"))
        assertFalse("Windows 11 should have no continuous particle animations", win11.hasContinuousAnimations)
    }

    @Test
    fun testCorruptedArchiveThrowsGracefully() {
        val corruptedBytes = "This is corrupted non-zip data".toByteArray(Charsets.UTF_8)
        val extractor = TendiesExtractor()
        try {
            corruptedBytes.inputStream().use { stream ->
                extractor.extract(stream, "corrupted.tendies")
            }
            fail("Extracting corrupted data should throw an exception")
        } catch (e: Exception) {
            println("Caught expected exception on corrupted archive: ${e.javaClass.simpleName} - ${e.message}")
            assertTrue(e is java.util.zip.ZipException || e is IllegalArgumentException || e is java.io.IOException)
        }
    }
}
