package com.posterdroid.caml.engine

import com.posterdroid.caml.parser.TendiesExtractor
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CoreAnimationEngineTest {

    private val tendiesDir = File("../examples/tendies").let {
        if (it.exists()) it else File("examples/tendies")
    }

    @Test
    fun testCoreAnimationEngineWindowToWinter() {
        val file = File(tendiesDir, "WindowToWinter.tendies")
        val extractor = TendiesExtractor()
        val pkg = extractor.extract(file)
        assertNotNull(pkg.background)

        val engine = CoreAnimationEngine()

        // 1. Evaluate at locked state (progress = 0)
        val p0 = engine.computePresentation(pkg.background!!, progress = 0f, timeSeconds = 0f)
        assertNotNull(p0)
        assertEquals(0f, p0.bounds.x, 1e-4f)
        assertFalse(p0.hidden)

        // 2. Evaluate at mid-progress (progress = 0.5)
        val p50 = engine.computePresentation(pkg.background!!, progress = 0.5f, timeSeconds = 0f)
        assertNotNull(p50)

        // 3. Evaluate at unlocked state (progress = 1.0)
        val p100 = engine.computePresentation(pkg.background!!, progress = 1.0f, timeSeconds = 0f)
        assertNotNull(p100)

        // 4. Test continuous snowflake animation at time = 1.5s
        val pTime0 = engine.computePresentation(pkg.background!!, progress = 0f, timeSeconds = 0f)
        val pTime1 = engine.computePresentation(pkg.background!!, progress = 0f, timeSeconds = 1.5f)

        val snowflake0 = pTime0.findLayerById("sgjbd7qgb3fmlybdiwm")
        val snowflake1 = pTime1.findLayerById("sgjbd7qgb3fmlybdiwm")
        assertNotNull(snowflake0)
        assertNotNull(snowflake1)

        println("Snowflake rot at t=0: ${snowflake0!!.transform.rotationZ}, at t=1.5: ${snowflake1!!.transform.rotationZ}")
        assertNotEquals(snowflake0.transform.rotationZ, snowflake1.transform.rotationZ, 1e-4f)
    }

    @Test
    fun testCoreAnimationEngineSeveranceSnow() {
        val file = File(tendiesDir, "TrinitySeveranceSnow.tendies")
        val extractor = TendiesExtractor()
        val pkg = extractor.extract(file)
        assertNotNull(pkg.foreground)

        val engine = CoreAnimationEngine()
        val pres = engine.computePresentation(pkg.foreground!!, progress = 0f, timeSeconds = 0f)
        assertNotNull(pres)
        assertTrue(pres.collectAllLayers().size > 10)
    }

    @Test
    fun testCoreAnimationEngineWindows11() {
        val file = File(tendiesDir, "windows11.tendies")
        val extractor = TendiesExtractor()
        val pkg = extractor.extract(file)
        assertNotNull(pkg.background)

        val engine = CoreAnimationEngine()
        val pres0 = engine.computePresentation(pkg.background!!, progress = 0f)
        val pres1 = engine.computePresentation(pkg.background!!, progress = 1f)
        assertNotNull(pres0)
        assertNotNull(pres1)
    }

    @Test
    fun testCASpringPhysics() {
        // Standard iOS lockscreen spring: mass=1.0, stiffness=100.0, damping=10.0, v0=0
        val spring = com.posterdroid.caml.math.CASpringTiming(
            mass = 1.0f,
            stiffness = 100.0f,
            damping = 10.0f,
            initialVelocity = 0.0f
        )
        // Value at t=0 should be 0.0
        assertEquals(0.0f, spring.solve(0.0f), 1e-4f)

        // As time approaches settling duration, value should approach 1.0
        val settleTime = spring.settlingDuration
        assertTrue("Settling duration should be reasonable (0.2s - 2.5s)", settleTime in 0.2f..2.5f)
        val endVal = spring.solve(settleTime * 1.5f)
        assertEquals(1.0f, endVal, 0.01f)

        // Mid-point should be smoothly progressing
        val midVal = spring.solveNormalized(0.5f)
        assertTrue("Spring normalized midpoint should be positive", midVal > 0.3f)
    }

    @Test
    fun testEmitterLayerAndFiltersSeverance() {
        val file = File(tendiesDir, "TrinitySeveranceSnow.tendies")
        val extractor = TendiesExtractor()
        val pkg = extractor.extract(file)
        assertNotNull(pkg.foreground)

        val fg = pkg.foreground!!
        val allLayers = fg.rootLayer.collectAllLayers()
        val emitterLayer = allLayers.filterIsInstance<com.posterdroid.caml.model.CAEmitterLayer>().firstOrNull()
        assertNotNull("Should find CAEmitterLayer in TrinitySeveranceSnow", emitterLayer)

        println("Found CAEmitterLayer: name=${emitterLayer!!.name}, cells=${emitterLayer.emitterCells.size}")
        assertTrue("Emitter should have cells", emitterLayer.emitterCells.isNotEmpty())

        val cell = emitterLayer.emitterCells.first()
        println("Cell 1: name=${cell.name}, birthRate=${cell.birthRate}, velocity=${cell.velocity}, xAcc=${cell.xAcceleration}")
        assertTrue("Cell birthRate should be > 0", cell.birthRate > 0f)

        // Check presentation evaluation with filters
        val engine = CoreAnimationEngine()
        val p0 = engine.computePresentation(fg, progress = 0.0f)
        val p1 = engine.computePresentation(fg, progress = 1.0f)
        assertNotNull(p0)
        assertNotNull(p1)

        val allPresLayers = p1.collectAllLayers()
        val hasFilters = allPresLayers.any { it.filterContrast != null || it.filterColorMatrix != null || it.filters.isNotEmpty() }
        println("Presentation tree has filters: $hasFilters")
    }

    @Test
    fun testRollingHillsGradientAndParallax() {
        val file = File("../ISSUES!!!/rolling hills/RollingHills.tendies").let {
            if (it.exists()) it else File("ISSUES!!!/rolling hills/RollingHills.tendies")
        }
        if (!file.exists()) return

        val pkg = TendiesExtractor().extract(file)
        val tree = pkg.background ?: pkg.floating ?: pkg.foreground
        assertNotNull(tree)

        // Verify CAGradientLayer sky colors parsed
        val allLayers = tree!!.rootLayer.collectAllLayers()
        val sky = allLayers.filterIsInstance<com.posterdroid.caml.model.CAGradientLayer>().firstOrNull { it.name == "Sky" }
        assertNotNull("Sky gradient layer must exist", sky)
        assertEquals("axial", sky!!.gradientType)
        assertTrue("Sky gradient must have at least 2 colors", sky.colors.size >= 2)
        assertEquals(0.9922f, sky.colors[0].r, 0.01f) // Yellow
        assertEquals(0.651f, sky.colors[1].r, 0.01f) // Purple

        // Verify WallpaperParallaxGroups parsed
        assertTrue("RollingHills must have parallax groups", tree.parallaxGroups.isNotEmpty())
        val g1Group = tree.parallaxGroups.firstOrNull { it.layerName == "g1" && it.axis == "x" }
        assertNotNull(g1Group)
        assertEquals(-10f, g1Group!!.mapMinTo, 0.01f)
        assertEquals(10f, g1Group.mapMaxTo, 0.01f)

        // Verify CoreAnimationEngine applies parallax tilt offsets
        val engine = CoreAnimationEngine()
        val neutral = engine.computePresentation(tree, progress = 0f, tiltX = 0f, tiltY = 0f)
        val tiltedLeft = engine.computePresentation(tree, progress = 0f, tiltX = -1f, tiltY = 0f)
        val tiltedRight = engine.computePresentation(tree, progress = 0f, tiltX = 1f, tiltY = 0f)

        val g1Neutral = neutral.collectAllLayers().first { it.originalLayer.name == "g1" }
        val g1Left = tiltedLeft.collectAllLayers().first { it.originalLayer.name == "g1" }
        val g1Right = tiltedRight.collectAllLayers().first { it.originalLayer.name == "g1" }

        assertTrue("Tilted left g1 position.x should be less than tilted right", g1Left.position.x < g1Right.position.x)
        assertEquals(20f, g1Right.position.x - g1Left.position.x, 0.5f)
    }

    @Test
    fun testCatVolleyballTransparencyAndFlipping() {
        val file = File("../ISSUES!!!/cat volleyball/catvolleyball-lite.tendies").let {
            if (it.exists()) it else File("ISSUES!!!/cat volleyball/catvolleyball-lite.tendies")
        }
        if (!file.exists()) return

        val pkg = TendiesExtractor().extract(file)
        assertNotNull(pkg.floating)
        val floating = pkg.floating!!

        // Verify floating tree root has geometryFlipped = true (1)
        assertTrue("Floating tree root should have geometryFlipped = true", floating.rootLayer.geometryFlipped)

        // Verify cat background color opacity is 0 (transparent)
        val catLayer = floating.rootLayer.collectAllLayers().firstOrNull { it.name == "Enemy (Cat)" }
        assertNotNull("Enemy (Cat) layer must exist", catLayer)
        assertNotNull(catLayer!!.backgroundColor)
        assertEquals(0f, catLayer.backgroundColor!!.a, 0.001f)
    }

    @Test
    fun testBlackHoleEmitterColorTint() {
        val file = File("../ISSUES!!!/black hole/BlackHole.tendies").let {
            if (it.exists()) it else File("ISSUES!!!/black hole/BlackHole.tendies")
        }
        if (!file.exists()) return

        val pkg = TendiesExtractor().extract(file)
        val tree = pkg.floating ?: pkg.background ?: pkg.foreground
        assertNotNull(tree)

        val allLayers = tree!!.rootLayer.collectAllLayers()
        val emitters = allLayers.filterIsInstance<com.posterdroid.caml.model.CAEmitterLayer>()
        assertTrue("BlackHole should have emitter layers", emitters.isNotEmpty())

        val disk1 = emitters.firstOrNull { it.name == "disk1" }
        assertNotNull("disk1 emitter must exist", disk1)
        val cell1 = disk1!!.emitterCells.firstOrNull()
        assertNotNull(cell1)
        assertNotNull("disk1 cell must have color parsed", cell1!!.color)
        assertEquals(1f, cell1.color!!.r, 0.01f)
        assertEquals(0.7658f, cell1.color!!.g, 0.01f)
        assertEquals(1f, cell1.color!!.b, 0.01f)

        val behindDisk1 = emitters.firstOrNull { it.name == "behidedisk1" }
        assertNotNull("behidedisk1 emitter must exist", behindDisk1)
        val behindCell = behindDisk1!!.emitterCells.firstOrNull()
        assertNotNull(behindCell)
        assertNotNull("behidedisk1 cell must have child color parsed", behindCell!!.color)
        assertEquals(0.479f, behindCell.color!!.a, 0.01f)
        assertEquals(0.8342f, behindCell.color!!.r, 0.01f)
    }
}
