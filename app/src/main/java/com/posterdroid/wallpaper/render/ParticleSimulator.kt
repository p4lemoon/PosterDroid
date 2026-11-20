// тут все сделала ии, даже не смотрите

package com.posterdroid.wallpaper.render

import android.graphics.*
import com.posterdroid.caml.model.CAEmitterCell
import com.posterdroid.caml.model.CAEmitterLayer
import com.posterdroid.caml.model.CamlContents
import com.posterdroid.caml.model.Point2f
import kotlin.math.*
import kotlin.random.Random

/**
 * High-performance, zero-allocation particle simulator for CAEmitterLayer.
 * Simulates blowing snow, embers, and atmospheric particles.
 */
class ParticleSimulator(private val emitter: CAEmitterLayer) {

    private class Particle {
        var active = false
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var ax = 0f
        var ay = 0f
        var scale = 1f
        var scaleSpeed = 0f
        var contentsScale = 1f
        var alpha = 1f
        var baseAlpha = 1f
        var alphaSpeed = 0f
        var hasTint = false
        var tintColor = 0
        var rotation = 0f
        var spin = 0f
        var age = 0f
        var lifetime = 1f
        var timeScale = 1f
        var waveForceY = 0f
        var waveFreq = 1f
        var bmp: Bitmap? = null
        var frameIndex = 0
        var totalFrames = 1
        var framesPerRow = 1
    }

    private val maxParticles = 300
    private val pool = Array(maxParticles) { Particle() }
    private var spawnAccumulator = 0f

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = RectF()

    fun warmUp(
        durationSeconds: Float,
        w: Float,
        h: Float,
        findBitmap: (String) -> Bitmap?
    ) {
        if (emitter.hidden || emitter.emitterCells.isEmpty()) return
        val layerSpeed = (emitter.speed.toFloat()).coerceAtLeast(0.0001f)
        val dt = 0.05f
        val steps = ((durationSeconds.coerceIn(1.0f, 6.0f) / layerSpeed) / dt).toInt().coerceIn(30, 150)
        for (i in 0 until steps) {
            stepSimulation(dt, w, h, findBitmap)
        }
    }

    private fun stepSimulation(
        dt: Float,
        w: Float,
        h: Float,
        findBitmap: (String) -> Bitmap?
    ) {
        for (cell in emitter.emitterCells) {
            if (!cell.enabled) continue
            spawnFromCell(cell, dt, w, h, findBitmap)
            for (subCell in cell.emitterCells) {
                if (subCell.enabled) {
                    spawnFromCell(subCell, dt, w, h, findBitmap)
                }
            }
        }

        for (p in pool) {
            if (!p.active) continue

            val pDt = dt * p.timeScale
            p.age += pDt
            if (p.age >= p.lifetime) {
                p.active = false
                continue
            }

            var curAy = p.ay
            if (p.waveForceY != 0f) {
                curAy += p.waveForceY * sin(2.0 * Math.PI * p.waveFreq * p.age).toFloat()
            }

            p.vx += p.ax * pDt
            p.vy += curAy * pDt
            p.x += p.vx * pDt
            p.y += p.vy * pDt
            p.rotation += p.spin * pDt

            if (p.scaleSpeed != 0f) {
                p.scale = (p.scale + p.scaleSpeed * pDt).coerceAtLeast(0.01f)
            }

            if (p.alphaSpeed != 0f) {
                p.baseAlpha = (p.baseAlpha + p.alphaSpeed * pDt).coerceIn(0f, 1f)
            }
        }
    }

    private fun spawnFromCell(
        cell: CAEmitterCell,
        dt: Float,
        w: Float,
        h: Float,
        findBitmap: (String) -> Bitmap?
    ) {
        val rate = cell.birthRate * emitter.birthRate
        if (rate <= 0f) return

        val layerSpeed = (emitter.speed.toFloat()).coerceAtLeast(0.0001f)
        val effectiveDt = dt * layerSpeed * (cell.speed).coerceAtLeast(0.0001f)
        spawnAccumulator += rate * effectiveDt
        val toSpawn = spawnAccumulator.toInt().coerceAtMost(20)
        spawnAccumulator -= toSpawn

        for (i in 0 until toSpawn) {
            val p = pool.firstOrNull { !it.active } ?: break
            initParticle(p, cell, w, h, findBitmap)
        }
    }

    fun updateAndDraw(
        canvas: Canvas,
        dt: Float,
        w: Float,
        h: Float,
        parentAlpha: Float,
        findBitmap: (String) -> Bitmap?
    ) {
        if (emitter.hidden || emitter.opacity <= 0.001f || emitter.emitterCells.isEmpty()) return

        val clampedDt = dt.coerceIn(0.001f, 0.05f)
        stepSimulation(clampedDt, w, h, findBitmap)

        val isAdditive = emitter.renderMode.equals("additive", ignoreCase = true)
        val prevXfermode = particlePaint.xfermode
        if (isAdditive) {
            particlePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }

        // 2. Draw
        for (p in pool) {
            if (!p.active) continue

            // Fade in/out over lifetime
            val progress = p.age / p.lifetime
            val fade = if (p.bmp != null) {
                if (progress > 0.95f) (1.0f - progress) / 0.05f else 1.0f
            } else {
                when {
                    progress < 0.15f -> progress / 0.15f
                    progress > 0.85f -> (1.0f - progress) / 0.15f
                    else -> 1.0f
                }
            }
            val finalAlpha = (p.baseAlpha * fade * parentAlpha * emitter.opacity).coerceIn(0f, 1f)
            particlePaint.alpha = (finalAlpha * 255).toInt()

            if (p.hasTint) {
                particlePaint.colorFilter = PorterDuffColorFilter(p.tintColor, PorterDuff.Mode.MULTIPLY)
            } else {
                particlePaint.colorFilter = null
            }

            val bmp = p.bmp
            if (bmp != null && !bmp.isRecycled) {
                val pw = (bmp.width / p.framesPerRow) * p.scale / p.contentsScale
                val ph = (bmp.height / (p.totalFrames / p.framesPerRow).coerceAtLeast(1)) * p.scale / p.contentsScale

                val halfW = pw / 2f
                val halfH = ph / 2f

                canvas.save()
                canvas.translate(p.x, p.y)
                if (p.rotation != 0f) {
                    canvas.rotate(Math.toDegrees(p.rotation.toDouble()).toFloat())
                }

                if (p.totalFrames > 1) {
                    val frameW = bmp.width / p.framesPerRow
                    val frameH = bmp.height / (p.totalFrames / p.framesPerRow).coerceAtLeast(1)
                    val col = p.frameIndex % p.framesPerRow
                    val row = p.frameIndex / p.framesPerRow

                    srcRect.set(col * frameW, row * frameH, (col + 1) * frameW, (row + 1) * frameH)
                    dstRect.set(-halfW, -halfH, halfW, halfH)
                    canvas.drawBitmap(bmp, srcRect, dstRect, particlePaint)
                } else {
                    dstRect.set(-halfW, -halfH, halfW, halfH)
                    canvas.drawBitmap(bmp, null, dstRect, particlePaint)
                }

                canvas.restore()
            } else {
                // Soft glowing circular snowflake / particle fallback
                if (p.hasTint) {
                    particlePaint.color = Color.argb(
                        (finalAlpha * 255).toInt(),
                        Color.red(p.tintColor),
                        Color.green(p.tintColor),
                        Color.blue(p.tintColor)
                    )
                } else {
                    particlePaint.color = Color.argb((finalAlpha * 255).toInt(), 255, 255, 255)
                }
                val radius = (p.scale * 3.5f).coerceAtLeast(1.5f)
                canvas.drawCircle(p.x, p.y, radius, particlePaint)
            }
        }

        particlePaint.colorFilter = null
        particlePaint.xfermode = prevXfermode
    }

    private fun initParticle(
        p: Particle,
        cell: CAEmitterCell,
        w: Float,
        h: Float,
        findBitmap: (String) -> Bitmap?
    ) {
        p.active = true
        p.age = 0f
        val layerSpeed = (emitter.speed.toFloat()).coerceAtLeast(0.0001f)
        p.timeScale = layerSpeed * (cell.speed).coerceAtLeast(0.0001f)
        p.lifetime = ((cell.lifetime + (Random.nextFloat() - 0.5f) * cell.lifetimeRange) * emitter.lifetime).coerceAtLeast(0.1f)
        p.contentsScale = cell.contentsScale.coerceAtLeast(0.01f)
        p.scaleSpeed = cell.scaleSpeed

        // Emitter position + size
        val defaultPoint = Point2f()
        val ex = if (emitter.emitterPosition != defaultPoint) emitter.emitterPosition.x else (w / 2f)
        val ey = if (emitter.emitterPosition != defaultPoint) emitter.emitterPosition.y else (h / 2f)
        val ew = if (emitter.emitterSize.x > 0f) emitter.emitterSize.x else w
        val eh = if (emitter.emitterSize.y > 0f) emitter.emitterSize.y else h

        p.x = ex + (Random.nextFloat() - 0.5f) * ew
        p.y = ey + (Random.nextFloat() - 0.5f) * eh

        p.scale = (cell.scale + (Random.nextFloat() - 0.5f) * cell.scaleRange).coerceAtLeast(0.01f) * emitter.scale
        val depthFactor = (p.scale / (cell.scale * emitter.scale).coerceAtLeast(0.001f)).coerceIn(0.5f, 2.0f)

        // Direction & Speed in 3D (longitude & latitude) modulated by depth factor
        val angle = cell.emissionLongitude + (Random.nextFloat() - 0.5f) * cell.emissionRange
        val latAngle = cell.emissionLatitude + (Random.nextFloat() - 0.5f) * cell.emissionRange * 0.3f
        val speed = (cell.velocity + (Random.nextFloat() - 0.5f) * cell.velocityRange) * emitter.velocity * depthFactor

        p.vx = (cos(latAngle) * cos(angle) * speed).toFloat()
        p.vy = (cos(latAngle) * sin(angle) * speed).toFloat()

        p.ax = cell.xAcceleration
        p.ay = cell.yAcceleration
        val color = cell.color
        if (color != null && (color.r < 0.99f || color.g < 0.99f || color.b < 0.99f)) {
            p.hasTint = true
            p.tintColor = Color.rgb(
                (color.r * 255).toInt().coerceIn(0, 255),
                (color.g * 255).toInt().coerceIn(0, 255),
                (color.b * 255).toInt().coerceIn(0, 255)
            )
            p.baseAlpha = (color.a * (1.0f - (Random.nextFloat() - 0.5f) * cell.alphaRange)).coerceIn(0.01f, 1.0f)
        } else {
            p.hasTint = false
            val a = color?.a ?: 1.0f
            p.baseAlpha = (a * (1.0f - (Random.nextFloat() - 0.5f) * cell.alphaRange)).coerceIn(0.01f, 1.0f)
        }
        p.alphaSpeed = cell.alphaSpeed

        p.spin = cell.spin + (Random.nextFloat() - 0.5f) * cell.spinRange + emitter.spinBias
        if (cell.rotation != 0f || cell.rotationRange != 0f) {
            p.rotation = cell.rotation + (Random.nextFloat() - 0.5f) * cell.rotationRange
        } else {
            p.rotation = Random.nextFloat() * (2.0 * Math.PI).toFloat()
        }

        // Behaviors (wave force, etc.)
        val wave = cell.behaviors.firstOrNull { it.behavior == "wave" }
            ?: emitter.emitterBehaviors.firstOrNull { it.behavior == "wave" }
        p.waveForceY = wave?.force?.y ?: 0f
        p.waveFreq = wave?.frequency ?: 1f

        // Contents
        val contents = cell.contents
        if (contents is CamlContents.CGImage) {
            p.bmp = findBitmap(contents.src)
        } else {
            p.bmp = null
        }
        p.totalFrames = cell.contentsFrameCount.coerceAtLeast(1)
        p.framesPerRow = cell.contentsFramesPerRow.coerceAtLeast(1)
        p.frameIndex = if (p.totalFrames > 1) Random.nextInt(p.totalFrames) else 0
    }
}
