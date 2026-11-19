// тут все сделала ии, даже не смотрите

package com.posterdroid.wallpaper.render

import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.graphics.PathParser
import com.posterdroid.caml.engine.CoreAnimationEngine
import com.posterdroid.caml.engine.PresentationLayer
import com.posterdroid.caml.math.CATransform3D
import com.posterdroid.caml.model.*
import com.posterdroid.core.PosterConstants
import kotlin.math.hypot
import kotlin.math.max

class CamlRenderer(private val pkg: CamlPackage) {

    private val caEngine = CoreAnimationEngine()
    private val bitmapCache = mutableMapOf<String, Bitmap>()
    private val pathCache = mutableMapOf<String, Path>()
    private val particleSimulators = mutableMapOf<String, ParticleSimulator>()
    private var lastTimeSeconds = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val dstRectF = RectF()
    private val srcRect = Rect()
    private val cornerPath = Path()
    private val trimmedPath = Path()
    private val pathMeasure = PathMeasure()

    private val transformMatrix = Matrix()
    private val sublayerMatrix = Matrix()
    private val replicatorMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val replicatorValues = FloatArray(9)

    private val colorMatrix = ColorMatrix()
    private val contrastMatrix = ColorMatrix()
    private val contrastValues = FloatArray(20)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

    // --- DEBUG RENDER MODES (Additive diagnostic tools, do not alter production defaults) ---
    var debugFixedProgress: Float? = null
    var debugOnlyLayerName: String? = null
    var debugOnlyLayerIndex: Int? = null
    var debugHighlightBounds: Boolean = false
    private var debugLayerIndexCounter = 0

    init {
        preloadBitmaps()
    }

    private fun preloadBitmaps() {
        for ((src, bytes) in pkg.assets) {
            try {
                val boundsOpts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = if (boundsOpts.outWidth > 2000 || boundsOpts.outHeight > 2000) 2 else 1
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                if (bmp != null) {
                    bitmapCache[src] = bmp
                    val decoded = try { java.net.URLDecoder.decode(src, "UTF-8") } catch (e: Exception) { src }
                    bitmapCache[decoded] = bmp
                    val fileName = decoded.substringAfterLast("/")
                    bitmapCache[fileName] = bmp
                    bitmapCache["assets/$fileName"] = bmp
                }
            } catch (t: Throwable) {
                Log.w(PosterConstants.TAG_WALLPAPER, "Failed to decode bitmap $src: ${t.message}")
            }
        }
    }

    fun release() {
        for (bmp in bitmapCache.values.toSet()) {
            try {
                if (!bmp.isRecycled) {
                    bmp.recycle()
                }
            } catch (t: Throwable) {
                // Safe catch
            }
        }
        bitmapCache.clear()
        pathCache.clear()
        particleSimulators.clear()
    }

    fun render(
        canvas: Canvas,
        progress: Float,
        timeSeconds: Float = 0f,
        tiltX: Float = 0f,
        tiltY: Float = 0f
    ) {
        val root = pkg.background?.rootLayer
            ?: pkg.floating?.rootLayer
            ?: pkg.foreground?.rootLayer
            ?: return

        val rootW = root.bounds.width.takeIf { it > 0 } ?: 390f
        val rootH = root.bounds.height.takeIf { it > 0 } ?: 844f

        val scale = max(canvas.width / rootW, canvas.height / rootH)
        val offsetX = (canvas.width - rootW * scale) / 2f
        val offsetY = (canvas.height - rootH * scale) / 2f

        val effectiveProgress = debugFixedProgress ?: progress
        debugLayerIndexCounter = 0

        val dt = if (lastTimeSeconds > 0f && timeSeconds > lastTimeSeconds) {
            (timeSeconds - lastTimeSeconds).coerceAtMost(0.05f)
        } else {
            0.016f
        }
        lastTimeSeconds = timeSeconds

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        // 1. Render Background Tree
        pkg.background?.let { tree ->
            renderTree(canvas, tree, effectiveProgress, timeSeconds, tiltX, tiltY, rootH, dt, fallbackParallaxScale = 5f)
        }

        // 2. Render Floating Tree (clock / mid-ground)
        pkg.floating?.let { tree ->
            renderTree(canvas, tree, effectiveProgress, timeSeconds, tiltX, tiltY, rootH, dt, fallbackParallaxScale = 25f)
        }

        // 3. Render Foreground Tree
        pkg.foreground?.let { tree ->
            renderTree(canvas, tree, effectiveProgress, timeSeconds, tiltX, tiltY, rootH, dt, fallbackParallaxScale = 40f)
        }

        canvas.restore()
    }

    /**
     * Diagnostic render helper: renders the scene to an offscreen Bitmap of target dimensions.
     */
    fun renderToBitmap(
        width: Int,
        height: Int,
        progress: Float = 0f,
        timeSeconds: Float = 0f,
        tiltX: Float = 0f,
        tiltY: Float = 0f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        render(canvas, progress, timeSeconds, tiltX, tiltY)
        return bitmap
    }

    private fun renderTree(
        canvas: Canvas,
        tree: CamlLayerTree,
        progress: Float,
        timeSeconds: Float,
        tiltX: Float,
        tiltY: Float,
        rootH: Float,
        dt: Float,
        fallbackParallaxScale: Float
    ) {
        val treeYUp = !tree.rootLayer.geometryFlipped
        canvas.save()
        if (treeYUp) {
            canvas.translate(0f, rootH)
            canvas.scale(1f, -1f)
        }
        val pres = caEngine.computePresentation(
            tree = tree,
            progress = progress,
            timeSeconds = timeSeconds,
            tiltX = tiltX,
            tiltY = tiltY,
            fallbackParallaxScale = fallbackParallaxScale
        )
        drawPresentationLayer(canvas, pres, 1f, treeYUp, dt)
        canvas.restore()
    }

    private fun drawPresentationLayer(
        canvas: Canvas,
        layer: PresentationLayer,
        parentAlpha: Float,
        isYUp: Boolean,
        dt: Float
    ) {
        if (layer.hidden || layer.opacity <= 0.001f) return

        val w = layer.bounds.width
        val h = layer.bounds.height

        // Group opacity: offscreen composite when translucent and has sublayers
        val useGroupAlpha = layer.allowsGroupOpacity && layer.opacity < 0.999f && layer.sublayers.isNotEmpty()
        val effectiveAlpha = if (useGroupAlpha) 1f else (parentAlpha * layer.opacity).coerceIn(0f, 1f)

        canvas.save()

        // 1. Position in parent
        canvas.translate(layer.position.x, layer.position.y)

        // 2. Full 4x4 Core Animation transform (with 3D perspective / rotation / scale)
        if (!layer.transform.isIdentity()) {
            layer.transform.toAndroidMatrixValues(matrixValues)
            transformMatrix.setValues(matrixValues)
            canvas.concat(transformMatrix)
        }

        // 3. Anchor Point translation: bounds origin centered around anchorPoint
        val left = -layer.anchorPoint.x * w
        val top = -layer.anchorPoint.y * h
        canvas.translate(left, top)

        val currentLayerIdx = debugLayerIndexCounter++

        // Debug highlight bounds
        if (debugHighlightBounds && w > 0 && h > 0) {
            val debugColors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA)
            strokePaint.color = debugColors[currentLayerIdx % debugColors.size]
            strokePaint.strokeWidth = 4f
            strokePaint.alpha = 255
            canvas.drawRect(0f, 0f, w, h, strokePaint)
        }

        val matchesOnlyFilter = (debugOnlyLayerIndex == null || currentLayerIdx == debugOnlyLayerIndex) &&
                (debugOnlyLayerName == null || layer.name == debugOnlyLayerName || layer.id == debugOnlyLayerName)

        val groupAlphaSaveCount = if (useGroupAlpha) {
            canvas.saveLayerAlpha(null, (layer.opacity * 255).toInt())
        } else {
            -1
        }

        val mask = layer.mask
        val maskSaveCount = if (mask != null && w > 0 && h > 0) {
            canvas.saveLayer(0f, 0f, w, h, null)
        } else {
            -1
        }

        // 4. Masks to bounds & corner radius
        if (layer.masksToBounds && w > 0 && h > 0) {
            if (layer.cornerRadius > 0f) {
                cornerPath.rewind()
                val cr = layer.cornerRadius
                cornerPath.addRoundRect(0f, 0f, w, h, cr, cr, Path.Direction.CW)
                canvas.clipPath(cornerPath)
            } else {
                canvas.clipRect(0f, 0f, w, h)
            }
        }

        // 5. Apply ColorMatrix, Contrast, and Blur Filters
        val matrixVal = layer.filterColorMatrix
        val contrastVal = layer.filterContrast
        val hasMatrix = matrixVal != null
        val hasContrast = contrastVal != null && kotlin.math.abs(contrastVal - 1.0f) > 0.001f
        val prevBitmapFilter = bitmapPaint.colorFilter
        val prevFillFilter = fillPaint.colorFilter

        if (hasMatrix || hasContrast) {
            colorMatrix.reset()
            matrixVal?.let { colorMatrix.set(it) }
            contrastVal?.let { contrast ->
                val scale = contrast
                val translate = 128f * (1f - contrast)
                java.util.Arrays.fill(contrastValues, 0f)
                contrastValues[0] = scale
                contrastValues[4] = translate
                contrastValues[6] = scale
                contrastValues[9] = translate
                contrastValues[12] = scale
                contrastValues[14] = translate
                contrastValues[18] = 1f
                contrastMatrix.set(contrastValues)
                colorMatrix.postConcat(contrastMatrix)
            }
            val filter = ColorMatrixColorFilter(colorMatrix)
            bitmapPaint.colorFilter = filter
            fillPaint.colorFilter = filter
        }

        // Blur Filter (BlurMaskFilter for Paint)
        val blurFilter = layer.filters.firstOrNull { it is CamlFilter.GaussianBlur } as? CamlFilter.GaussianBlur
        val prevBitmapMaskFilter = bitmapPaint.maskFilter
        val prevFillMaskFilter = fillPaint.maskFilter
        if (blurFilter != null && blurFilter.radius > 0.5f) {
            try {
                val maskFilter = BlurMaskFilter(blurFilter.radius, BlurMaskFilter.Blur.NORMAL)
                bitmapPaint.maskFilter = maskFilter
                fillPaint.maskFilter = maskFilter
            } catch (t: Throwable) {
                // Safe fallback
            }
        }

        if (matchesOnlyFilter) {
            // 6. Drop Shadow
            if (layer.shadowOpacity > 0.001f && layer.shadowColor != null && w > 0 && h > 0) {
                val sc = layer.shadowColor!!
                val sAlpha = (effectiveAlpha * sc.a * layer.shadowOpacity).coerceIn(0f, 1f)
                shadowPaint.color = sc.toArgbInt()
                shadowPaint.alpha = (sAlpha * 255).toInt()
                val sy = if (isYUp) -layer.shadowOffset.y else layer.shadowOffset.y
                shadowPaint.setShadowLayer(
                    layer.shadowRadius.coerceAtLeast(0.1f),
                    layer.shadowOffset.x,
                    sy,
                    sc.toArgbInt()
                )
                if (layer.cornerRadius > 0f) {
                    canvas.drawRoundRect(0f, 0f, w, h, layer.cornerRadius, layer.cornerRadius, shadowPaint)
                } else {
                    canvas.drawRect(0f, 0f, w, h, shadowPaint)
                }
                shadowPaint.clearShadowLayer()
            }

            // 7. Background Color
            layer.backgroundColor?.let { bg ->
                if (bg.a > 0.001f) {
                    fillPaint.color = bg.toArgbInt()
                    fillPaint.alpha = (effectiveAlpha * bg.a * 255).toInt()
                    if (layer.cornerRadius > 0f) {
                        val cr = layer.cornerRadius
                        canvas.drawRoundRect(0f, 0f, w, h, cr, cr, fillPaint)
                    } else {
                        canvas.drawRect(0f, 0f, w, h, fillPaint)
                    }
                }
            }

            // 8. Contents (CGImage)
            val contents = layer.contents
            if (contents is CamlContents.CGImage && w > 0 && h > 0) {
                val bmp = findBitmap(contents.src)
                if (bmp != null) {
                    bitmapPaint.alpha = (effectiveAlpha * 255).toInt()
                    computeContentsRects(bmp, w, h, layer.contentsGravity, layer.contentsRect, srcRect, dstRectF)
                    if (isYUp) {
                        canvas.save()
                        canvas.translate(0f, h)
                        canvas.scale(1f, -1f)
                        canvas.drawBitmap(bmp, srcRect, dstRectF, bitmapPaint)
                        canvas.restore()
                    } else {
                        canvas.drawBitmap(bmp, srcRect, dstRectF, bitmapPaint)
                    }
                }
            }

            // 9. Custom Subclasses (Gradient, Shape, Text, Emitter)
            when (val orig = layer.originalLayer) {
                is CAGradientLayer -> {
                    drawGradientLayer(canvas, orig, w, h, effectiveAlpha)
                }
                is CAShapeLayer -> {
                    drawShapeLayer(canvas, orig, effectiveAlpha)
                }
                is CATextLayer -> {
                    if (isYUp) {
                        canvas.save()
                        canvas.translate(0f, h)
                        canvas.scale(1f, -1f)
                        drawTextLayer(canvas, orig, w, h, effectiveAlpha)
                        canvas.restore()
                    } else {
                        drawTextLayer(canvas, orig, w, h, effectiveAlpha)
                    }
                }
                is CAEmitterLayer -> {
                    val simKey = layer.id ?: orig.name ?: "emitter_${orig.hashCode()}"
                    val sim = particleSimulators.getOrPut(simKey) { ParticleSimulator(orig) }
                    if (isYUp) {
                        canvas.save()
                        canvas.translate(0f, h)
                        canvas.scale(1f, -1f)
                        sim.updateAndDraw(canvas, dt, w, h, effectiveAlpha, ::findBitmap)
                        canvas.restore()
                    } else {
                        sim.updateAndDraw(canvas, dt, w, h, effectiveAlpha, ::findBitmap)
                    }
                }
                else -> {}
            }

            // 10. Border
            if (layer.borderWidth > 0f && layer.borderColor != null && w > 0 && h > 0) {
                val bc = layer.borderColor!!
                strokePaint.color = bc.toArgbInt()
                strokePaint.strokeWidth = layer.borderWidth
                strokePaint.alpha = (effectiveAlpha * bc.a * 255).toInt()
                val halfBw = layer.borderWidth / 2f
                if (layer.cornerRadius > 0f) {
                    canvas.drawRoundRect(halfBw, halfBw, w - halfBw, h - halfBw, layer.cornerRadius, layer.cornerRadius, strokePaint)
                } else {
                    canvas.drawRect(halfBw, halfBw, w - halfBw, h - halfBw, strokePaint)
                }
            }
        }

        // 11. Draw Sublayers (including CAReplicatorLayer multi-instance accumulation)
        val orig = layer.originalLayer
        if (orig is CAReplicatorLayer && orig.instanceCount > 1) {
            val repCount = orig.instanceCount
            val instTransform = orig.instanceTransform
            var accumulatedTransform = CATransform3D.IDENTITY

            for (instance in 0 until repCount) {
                val instanceAlpha = (effectiveAlpha + instance * orig.instanceAlphaOffset).coerceIn(0f, 1f)
                canvas.save()
                if (!accumulatedTransform.isIdentity()) {
                    accumulatedTransform.toAndroidMatrixValues(replicatorValues)
                    replicatorMatrix.setValues(replicatorValues)
                    canvas.concat(replicatorMatrix)
                }

                if (!layer.sublayerTransform.isIdentity()) {
                    canvas.save()
                    layer.sublayerTransform.toAndroidMatrixValues(matrixValues)
                    sublayerMatrix.setValues(matrixValues)
                    canvas.concat(sublayerMatrix)
                    for (sub in layer.sublayers) {
                        drawPresentationLayer(canvas, sub, instanceAlpha, isYUp, dt)
                    }
                    canvas.restore()
                } else {
                    for (sub in layer.sublayers) {
                        drawPresentationLayer(canvas, sub, instanceAlpha, isYUp, dt)
                    }
                }

                canvas.restore()
                accumulatedTransform *= instTransform
            }
        } else {
            if (!layer.sublayerTransform.isIdentity()) {
                canvas.save()
                layer.sublayerTransform.toAndroidMatrixValues(matrixValues)
                sublayerMatrix.setValues(matrixValues)
                canvas.concat(sublayerMatrix)
                for (sub in layer.sublayers) {
                    drawPresentationLayer(canvas, sub, effectiveAlpha, isYUp, dt)
                }
                canvas.restore()
            } else {
                for (sub in layer.sublayers) {
                    drawPresentationLayer(canvas, sub, effectiveAlpha, isYUp, dt)
                }
            }
        }

        // Restore filters
        bitmapPaint.colorFilter = prevBitmapFilter
        fillPaint.colorFilter = prevFillFilter
        bitmapPaint.maskFilter = prevBitmapMaskFilter
        fillPaint.maskFilter = prevFillMaskFilter

        // Apply layer mask if present
        if (mask != null && maskSaveCount != -1) {
            val prevBitmapXfer = bitmapPaint.xfermode
            val prevFillXfer = fillPaint.xfermode
            bitmapPaint.xfermode = maskXfermode
            fillPaint.xfermode = maskXfermode
            drawPresentationLayer(canvas, mask, 1f, isYUp, dt)
            bitmapPaint.xfermode = prevBitmapXfer
            fillPaint.xfermode = prevFillXfer
            canvas.restoreToCount(maskSaveCount)
        }

        if (groupAlphaSaveCount != -1) {
            canvas.restoreToCount(groupAlphaSaveCount)
        }
        canvas.restore()
    }

    private fun computeContentsRects(
        bmp: Bitmap,
        w: Float,
        h: Float,
        gravity: String,
        contentsRect: Rect4f,
        outSrcRect: Rect,
        outDstRect: RectF
    ) {
        val bw = bmp.width
        val bh = bmp.height

        val sx = (contentsRect.x * bw).toInt().coerceIn(0, bw)
        val sy = (contentsRect.y * bh).toInt().coerceIn(0, bh)
        val sw = (contentsRect.width * bw).toInt().coerceIn(1, bw - sx)
        val sh = (contentsRect.height * bh).toInt().coerceIn(1, bh - sy)
        outSrcRect.set(sx, sy, sx + sw, sy + sh)

        when (gravity.lowercase()) {
            "resizeaspect", "aspectfit" -> {
                val scale = minOf(w / sw.toFloat(), h / sh.toFloat())
                val dw = sw * scale
                val dh = sh * scale
                outDstRect.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            }
            "resizeaspectfill", "aspectfill" -> {
                val scale = maxOf(w / sw.toFloat(), h / sh.toFloat())
                val dw = sw * scale
                val dh = sh * scale
                outDstRect.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            }
            "center" -> {
                outDstRect.set((w - sw) / 2f, (h - sh) / 2f, (w + sw) / 2f, (h + sh) / 2f)
            }
            else -> {
                outDstRect.set(0f, 0f, w, h)
            }
        }
    }

    private fun drawGradientLayer(canvas: Canvas, layer: CAGradientLayer, w: Float, h: Float, alpha: Float) {
        if (layer.colors.size < 2 || w <= 0 || h <= 0) return
        val colors = layer.colors.map { color ->
            val a = (color.a * alpha * 255).toInt().coerceIn(0, 255)
            val r = (color.r * 255).toInt().coerceIn(0, 255)
            val g = (color.g * 255).toInt().coerceIn(0, 255)
            val b = (color.b * 255).toInt().coerceIn(0, 255)
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }.toIntArray()
        val positions = if (layer.locations.size == colors.size) layer.locations.toFloatArray() else null

        val x0 = layer.startPoint.x * w
        val y0 = layer.startPoint.y * h
        val x1 = layer.endPoint.x * w
        val y1 = layer.endPoint.y * h

        val shader = when (layer.gradientType.lowercase()) {
            "radial" -> {
                val dx = x1 - x0
                val dy = y1 - y0
                val radius = hypot(dx.toDouble(), dy.toDouble()).toFloat().takeIf { it > 0.5f } ?: (max(w, h) / 2f)
                RadialGradient(x0, y0, radius, colors, positions, Shader.TileMode.CLAMP)
            }
            "conic", "angular", "sweep" -> {
                SweepGradient(x0, y0, colors, positions)
            }
            else -> { // "axial"
                LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP)
            }
        }

        fillPaint.shader = shader
        fillPaint.alpha = 255
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        fillPaint.shader = null
    }

    private fun drawShapeLayer(canvas: Canvas, layer: CAShapeLayer, alpha: Float) {
        val pathStr = layer.pathData ?: return
        val path = pathCache.getOrPut(pathStr) {
            try {
                PathParser.createPathFromPathData(pathStr)
            } catch (t: Throwable) {
                Path()
            }
        }

        path.fillType = if (layer.fillRule.equals("even-odd", true) || layer.fillRule.equals("evenodd", true)) {
            Path.FillType.EVEN_ODD
        } else {
            Path.FillType.WINDING
        }

        layer.fillColor?.let { fc ->
            fillPaint.color = fc.toArgbInt()
            fillPaint.alpha = (alpha * fc.a * 255).toInt()
            canvas.drawPath(path, fillPaint)
        }

        layer.strokeColor?.let { sc ->
            strokePaint.color = sc.toArgbInt()
            strokePaint.strokeWidth = layer.lineWidth
            strokePaint.alpha = (alpha * sc.a * 255).toInt()

            strokePaint.strokeCap = when (layer.lineCap.lowercase()) {
                "round" -> Paint.Cap.ROUND
                "square" -> Paint.Cap.SQUARE
                else -> Paint.Cap.BUTT
            }

            strokePaint.strokeJoin = when (layer.lineJoin.lowercase()) {
                "round" -> Paint.Join.ROUND
                "bevel" -> Paint.Join.BEVEL
                else -> Paint.Join.MITER
            }

            strokePaint.strokeMiter = layer.miterLimit

            if (layer.lineDashPattern.isNotEmpty()) {
                strokePaint.pathEffect = DashPathEffect(layer.lineDashPattern.toFloatArray(), layer.lineDashPhase)
            } else {
                strokePaint.pathEffect = null
            }

            val sStart = layer.strokeStart.coerceIn(0f, 1f)
            val sEnd = layer.strokeEnd.coerceIn(0f, 1f)

            if (sStart > 0f || sEnd < 1f) {
                trimmedPath.rewind()
                pathMeasure.setPath(path, false)
                do {
                    val len = pathMeasure.length
                    val startDist = sStart * len
                    val endDist = sEnd * len
                    if (startDist < endDist) {
                        pathMeasure.getSegment(startDist, endDist, trimmedPath, true)
                    }
                } while (pathMeasure.nextContour())
                canvas.drawPath(trimmedPath, strokePaint)
            } else {
                canvas.drawPath(path, strokePaint)
            }
            strokePaint.pathEffect = null
        }
    }

    private fun drawTextLayer(canvas: Canvas, layer: CATextLayer, w: Float, h: Float, alpha: Float) {
        if (layer.text.isEmpty()) return
        textPaint.textSize = layer.fontSize
        textPaint.typeface = AppleFontMapper.resolveTypeface(layer.fontName)
        val col = layer.foregroundColor ?: Color4f.WHITE
        textPaint.color = col.toArgbInt()
        textPaint.alpha = (alpha * col.a * 255).toInt()

        if (layer.isWrapped && w > 0f) {
            val alignment = when (layer.alignmentMode.lowercase()) {
                "center" -> Layout.Alignment.ALIGN_CENTER
                "right" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }

            val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val builder = StaticLayout.Builder.obtain(layer.text, 0, layer.text.length, textPaint, w.toInt().coerceAtLeast(1))
                    .setAlignment(alignment)
                    .setIncludePad(false)
                when (layer.truncationMode.lowercase()) {
                    "start" -> builder.setEllipsize(TextUtils.TruncateAt.START).setMaxLines(1)
                    "middle" -> builder.setEllipsize(TextUtils.TruncateAt.MIDDLE).setMaxLines(1)
                    "end" -> builder.setEllipsize(TextUtils.TruncateAt.END).setMaxLines(1)
                    else -> {}
                }
                builder.build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(layer.text, textPaint, w.toInt().coerceAtLeast(1), alignment, 1.0f, 0.0f, false)
            }

            canvas.save()
            val layoutY = (h - staticLayout.height) / 2f
            canvas.translate(0f, layoutY)
            staticLayout.draw(canvas)
            canvas.restore()
        } else {
            val textW = textPaint.measureText(layer.text)
            val x = when (layer.alignmentMode.lowercase()) {
                "center" -> (w - textW) / 2f
                "right" -> w - textW
                else -> 0f
            }
            val y = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(layer.text, x, y, textPaint)
        }
    }

    private fun findBitmap(src: String): Bitmap? {
        bitmapCache[src]?.let { return it }
        val decoded = try { java.net.URLDecoder.decode(src, "UTF-8") } catch (e: Exception) { src }
        bitmapCache[decoded]?.let { return it }
        val filename = decoded.substringAfterLast("/")
        return bitmapCache[filename] ?: bitmapCache["assets/$filename"]
    }
}
