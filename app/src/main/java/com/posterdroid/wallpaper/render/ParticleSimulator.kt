// тут все сделала ии, даже не смотрите

package com.posterdroid.wallpaper.render

import android.graphics.*
import com.posterdroid.caml.model.CAEmitterCell
import com.posterdroid.caml.model.CAEmitterLayer
import com.posterdroid.caml.model.CamlContents
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
        var alpha = 1f
        var baseAlpha = 1f
        var alphaSpeed = 0f
        var hasTint = false
        var tintColor = 0
        var rotation = 0f
        var spin = 0f
        var age = 0f
        var lifetime = 1f
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

        // 1. Spawning
        for (cell in emitter.emitterCells) {
            val rate = cell.birthRate * emitter.birthRate
            if (rate <= 0f) continue

            spawnAccumulator += rate * clampedDt
            val toSpawn = spawnAccumulator.toInt().coerceAtMost(20)
            spawnAccumulator -= toSpawn

            for (i in 0 until toSpawn) {
                val p = pool.firstOrNull { !it.active } ?: break
                initParticle(p, cell, w, h, findBitmap)
            }
        }

        val isAdditive = emitter.renderMode.equals("additive", ignoreCase = true)
        val prevXfermode = particlePaint.xfermode
        if (isAdditive) {
            particlePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }

        // 2. Physics & Draw
        for (p in pool) {
            if (!p.active) continue

            p.age += clampedDt
            if (p.age >= p.lifetime) {
                p.active = false
                continue
            }

            // Apply wave behavior force if present
            var curAy = p.ay
            if (p.waveForceY != 0f) {
                curAy += p.waveForceY * sin(2.0 * Math.PI * p.waveFreq * p.age).toFloat()
            }

            p.vx += p.ax * clampedDt
            p.vy += curAy * clampedDt
            p.x += p.vx * clampedDt
            p.y += p.vy * clampedDt
            p.rotation += p.spin * clampedDt

            if (p.alphaSpeed != 0f) {
                p.baseAlpha = (p.baseAlpha + p.alphaSpeed * clampedDt).coerceIn(0f, 1f)
            }

            // Fade in/out over lifetime
            val progress = p.age / p.lifetime
            val fade = when {
                progress < 0.15f -> progress / 0.15f
                progress > 0.85f -> (1.0f - progress) / 0.15f
                else -> 1.0f
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
                val pw = (bmp.width / p.framesPerRow) * p.scale
                val ph = (bmp.height / (p.totalFrames / p.framesPerRow).coerceAtLeast(1)) * p.scale

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
        p.lifetime = (cell.lifetime + (Random.nextFloat() - 0.5f) * cell.lifetimeRange).coerceAtLeast(0.2f)

        // Emitter position + size
        val ex = emitter.emitterPosition.x.takeIf { it != 0f } ?: (w / 2f)
        val ey = emitter.emitterPosition.y.takeIf { it != 0f } ?: (h / 2f)
        val ew = emitter.emitterSize.x.takeIf { it != 0f } ?: w
        val eh = emitter.emitterSize.y.takeIf { it != 0f } ?: h

        p.x = ex + (Random.nextFloat() - 0.5f) * ew
        p.y = ey + (Random.nextFloat() - 0.5f) * eh

        // Direction & Speed
        val angle = cell.emissionLongitude + (Random.nextFloat() - 0.5f) * cell.emissionRange
        val speed = cell.velocity + (Random.nextFloat() - 0.5f) * cell.velocityRange

        p.vx = cos(angle) * speed
        p.vy = sin(angle) * speed

        p.ax = cell.xAcceleration
        p.ay = cell.yAcceleration

        p.scale = (cell.scale + (Random.nextFloat() - 0.5f) * cell.scaleRange).coerceAtLeast(0.01f) * emitter.scale
        val color = cell.color
        if (color != null) {
            p.hasTint = true
            p.tintColor = Color.rgb(
                (color.r * 255).toInt().coerceIn(0, 255),
                (color.g * 255).toInt().coerceIn(0, 255),
                (color.b * 255).toInt().coerceIn(0, 255)
            )
            p.baseAlpha = (color.a * (1.0f - (Random.nextFloat() - 0.5f) * cell.alphaRange)).coerceIn(0.01f, 1.0f)
        } else {
            p.hasTint = false
            p.baseAlpha = (1.0f - (Random.nextFloat() - 0.5f) * cell.alphaRange).coerceIn(0.1f, 1.0f)
        }
        p.alphaSpeed = cell.alphaSpeed

        p.spin = cell.spin + (Random.nextFloat() - 0.5f) * cell.spinRange
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
