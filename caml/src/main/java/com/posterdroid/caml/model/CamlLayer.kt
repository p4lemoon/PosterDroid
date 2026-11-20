package com.posterdroid.caml.model

import com.posterdroid.caml.math.CATransform3D

sealed class CamlLayer {
    abstract val id: String?
    abstract val name: String?
    abstract val bounds: Rect4f
    abstract val position: Point2f
    abstract val anchorPoint: Point2f
    open val anchorPointZ: Float get() = 0f
    abstract val zPosition: Float
    abstract val opacity: Float
    abstract val hidden: Boolean
    abstract val masksToBounds: Boolean
    abstract val cornerRadius: Float
    abstract val backgroundColor: Color4f?
    abstract val contents: CamlContents?
    open val contentsGravity: String get() = "resize"
    open val contentsRect: Rect4f get() = Rect4f(0f, 0f, 1f, 1f)
    open val mask: CamlLayer? get() = null
    open val speed: Double get() = 1.0
    open val timeOffset: Double get() = 0.0
    open val beginTime: Double get() = 0.0
    open val repeatCount: Float get() = 0f
    open val repeatDuration: Double get() = 0.0
    open val autoreverses: Boolean get() = false
    open val allowsGroupOpacity: Boolean get() = true
    open val borderWidth: Float get() = 0f
    open val borderColor: Color4f? get() = null
    open val shadowColor: Color4f? get() = null
    open val shadowOpacity: Float get() = 0f
    open val shadowOffset: Point2f get() = Point2f(0f, -3f)
    open val shadowRadius: Float get() = 3f
    open val shadowPath: String? get() = null
    abstract val transform: CATransform3D
    abstract val sublayerTransform: CATransform3D
    abstract val geometryFlipped: Boolean
    abstract val sublayers: List<CamlLayer>
    abstract val animations: List<CamlAnimation>
    abstract val filters: List<CamlFilter>

    fun findLayerById(targetId: String): CamlLayer? {
        if (id == targetId) return this
        for (sub in sublayers) {
            val found = sub.findLayerById(targetId)
            if (found != null) return found
        }
        return null
    }

    fun collectAllLayers(): List<CamlLayer> {
        val list = mutableListOf<CamlLayer>(this)
        for (sub in sublayers) {
            list.addAll(sub.collectAllLayers())
        }
        return list
    }
}

data class StandardCALayer(
    override val id: String?,
    override val name: String? = null,
    override val bounds: Rect4f = Rect4f(),
    override val position: Point2f = Point2f(),
    override val anchorPoint: Point2f = Point2f(0.5f, 0.5f),
    override val zPosition: Float = 0f,
    override val opacity: Float = 1f,
    override val hidden: Boolean = false,
    override val masksToBounds: Boolean = false,
    override val cornerRadius: Float = 0f,
    override val backgroundColor: Color4f? = null,
    override val contents: CamlContents? = null,
    override val transform: CATransform3D = CATransform3D.IDENTITY,
    override val sublayerTransform: CATransform3D = CATransform3D.IDENTITY,
    override val geometryFlipped: Boolean = false,
    override val sublayers: List<CamlLayer> = emptyList(),
    override val animations: List<CamlAnimation> = emptyList(),
    override val filters: List<CamlFilter> = emptyList(),
    override val anchorPointZ: Float = 0f,
    override val contentsGravity: String = "resize",
    override val contentsRect: Rect4f = Rect4f(0f, 0f, 1f, 1f),
    override val mask: CamlLayer? = null,
    override val speed: Double = 1.0,
    override val timeOffset: Double = 0.0,
    override val beginTime: Double = 0.0,
    override val repeatCount: Float = 0f,
    override val repeatDuration: Double = 0.0,
    override val autoreverses: Boolean = false,
    override val allowsGroupOpacity: Boolean = true,
    override val borderWidth: Float = 0f,
    override val borderColor: Color4f? = null,
    override val shadowColor: Color4f? = null,
    override val shadowOpacity: Float = 0f,
    override val shadowOffset: Point2f = Point2f(0f, -3f),
    override val shadowRadius: Float = 3f,
    override val shadowPath: String? = null
) : CamlLayer()

data class CATextLayer(
    override val id: String?,
    override val name: String?,
    override val bounds: Rect4f = Rect4f(),
    override val position: Point2f = Point2f(),
    override val anchorPoint: Point2f = Point2f(0.5f, 0.5f),
    override val zPosition: Float = 0f,
    override val opacity: Float = 1f,
    override val hidden: Boolean = false,
    override val masksToBounds: Boolean = false,
    override val cornerRadius: Float = 0f,
    override val backgroundColor: Color4f? = null,
    override val contents: CamlContents? = null,
    override val transform: CATransform3D = CATransform3D.IDENTITY,
    override val sublayerTransform: CATransform3D = CATransform3D.IDENTITY,
    override val geometryFlipped: Boolean = false,
    override val sublayers: List<CamlLayer> = emptyList(),
    override val animations: List<CamlAnimation> = emptyList(),
    override val filters: List<CamlFilter> = emptyList(),
    val text: String = "",
    val fontSize: Float = 14f,
    val fontName: String? = null,
    val foregroundColor: Color4f? = null,
    val alignmentMode: String = "natural",
    val isWrapped: Boolean = false,
    val truncationMode: String = "none",
    val allowsFontSubpixelQuantization: Boolean = false
) : CamlLayer()

data class CAGradientLayer(
    override val id: String?,
    override val name: String?,
    override val bounds: Rect4f = Rect4f(),
    override val position: Point2f = Point2f(),
    override val anchorPoint: Point2f = Point2f(0.5f, 0.5f),
    override val zPosition: Float = 0f,
    override val opacity: Float = 1f,
    override val hidden: Boolean = false,
    override val masksToBounds: Boolean = false,
    override val cornerRadius: Float = 0f,
    override val backgroundColor: Color4f? = null,
    override val contents: CamlContents? = null,
    override val transform: CATransform3D = CATransform3D.IDENTITY,
    override val sublayerTransform: CATransform3D = CATransform3D.IDENTITY,
    override val geometryFlipped: Boolean = false,
    override val sublayers: List<CamlLayer> = emptyList(),
    override val animations: List<CamlAnimation> = emptyList(),
    override val filters: List<CamlFilter> = emptyList(),
    val colors: List<Color4f> = emptyList(),
    val locations: List<Float> = emptyList(),
    val startPoint: Point2f = Point2f(0.5f, 0f),
    val endPoint: Point2f = Point2f(0.5f, 1f),
    val gradientType: String = "axial"
) : CamlLayer()

data class CAShapeLayer(
    override val id: String?,
    override val name: String?,
    override val bounds: Rect4f = Rect4f(),
    override val position: Point2f = Point2f(),
    override val anchorPoint: Point2f = Point2f(0.5f, 0.5f),
    override val zPosition: Float = 0f,
    override val opacity: Float = 1f,
    override val hidden: Boolean = false,
    override val masksToBounds: Boolean = false,
    override val cornerRadius: Float = 0f,
    override val backgroundColor: Color4f? = null,
    override val contents: CamlContents? = null,
    override val transform: CATransform3D = CATransform3D.IDENTITY,
    override val sublayerTransform: CATransform3D = CATransform3D.IDENTITY,
    override val geometryFlipped: Boolean = false,
    override val sublayers: List<CamlLayer> = emptyList(),
    override val animations: List<CamlAnimation> = emptyList(),
    override val filters: List<CamlFilter> = emptyList(),
    val pathData: String? = null,
    val fillColor: Color4f? = null,
    val strokeColor: Color4f? = null,
    val lineWidth: Float = 1f,
    val strokeStart: Float = 0f,
    val strokeEnd: Float = 1f,
    val fillRule: String = "non-zero",
    val lineCap: String = "butt",
    val lineJoin: String = "miter",
    val miterLimit: Float = 10f,
    val lineDashPattern: List<Float> = emptyList(),
    val lineDashPhase: Float = 0f
) : CamlLayer()

data class CAReplicatorLayer(
    override val id: String?,
    override val name: String? = null,
    override val bounds: Rect4f = Rect4f(),
    override val position: Point2f = Point2f(),
    override val anchorPoint: Point2f = Point2f(0.5f, 0.5f),
    override val zPosition: Float = 0f,
    override val opacity: Float = 1f,
    override val hidden: Boolean = false,
    override val masksToBounds: Boolean = false,
    override val cornerRadius: Float = 0f,
    override val backgroundColor: Color4f? = null,
    override val contents: CamlContents? = null,
    override val transform: CATransform3D = CATransform3D.IDENTITY,
    override val sublayerTransform: CATransform3D = CATransform3D.IDENTITY,
    override val geometryFlipped: Boolean = false,
    override val sublayers: List<CamlLayer> = emptyList(),
    override val animations: List<CamlAnimation> = emptyList(),
    override val filters: List<CamlFilter> = emptyList(),
    override val anchorPointZ: Float = 0f,
    override val contentsGravity: String = "resize",
    override val contentsRect: Rect4f = Rect4f(0f, 0f, 1f, 1f),
    override val mask: CamlLayer? = null,
    override val speed: Double = 1.0,
    override val timeOffset: Double = 0.0,
    override val beginTime: Double = 0.0,
    override val repeatCount: Float = 0f,
    override val repeatDuration: Double = 0.0,
    override val autoreverses: Boolean = false,
    override val allowsGroupOpacity: Boolean = true,
    val instanceCount: Int = 1,
    val preservesDepth: Boolean = false,
    val instanceDelay: Double = 0.0,
    val instanceTransform: CATransform3D = CATransform3D.IDENTITY,
    val instanceColor: Color4f? = null,
    val instanceRedOffset: Float = 0f,
    val instanceGreenOffset: Float = 0f,
    val instanceBlueOffset: Float = 0f,
    val instanceAlphaOffset: Float = 0f
) : CamlLayer()

data class CATransformLayer(
    override val id: String?,
    override val name: String?,
    override val bounds: Rect4f = Rect4f(),
    override val position: Point2f = Point2f(),
    override val anchorPoint: Point2f = Point2f(0.5f, 0.5f),
    override val zPosition: Float = 0f,
    override val opacity: Float = 1f,
    override val hidden: Boolean = false,
    override val masksToBounds: Boolean = false,
    override val cornerRadius: Float = 0f,
    override val backgroundColor: Color4f? = null,
    override val contents: CamlContents? = null,
    override val transform: CATransform3D = CATransform3D.IDENTITY,
    override val sublayerTransform: CATransform3D = CATransform3D.IDENTITY,
    override val geometryFlipped: Boolean = false,
    override val sublayers: List<CamlLayer> = emptyList(),
    override val animations: List<CamlAnimation> = emptyList(),
    override val filters: List<CamlFilter> = emptyList()
) : CamlLayer()

data class CAEmitterLayer(
    override val id: String?,
    override val name: String?,
    override val bounds: Rect4f = Rect4f(),
    override val position: Point2f = Point2f(),
    override val anchorPoint: Point2f = Point2f(0.5f, 0.5f),
    override val zPosition: Float = 0f,
    override val opacity: Float = 1f,
    override val hidden: Boolean = false,
    override val masksToBounds: Boolean = false,
    override val cornerRadius: Float = 0f,
    override val backgroundColor: Color4f? = null,
    override val contents: CamlContents? = null,
    override val transform: CATransform3D = CATransform3D.IDENTITY,
    override val sublayerTransform: CATransform3D = CATransform3D.IDENTITY,
    override val geometryFlipped: Boolean = false,
    override val sublayers: List<CamlLayer> = emptyList(),
    override val animations: List<CamlAnimation> = emptyList(),
    override val filters: List<CamlFilter> = emptyList(),
    override val speed: Double = 1.0,
    override val beginTime: Double = 0.0,
    override val timeOffset: Double = 0.0,
    val emitterPosition: Point2f = Point2f(),
    val emitterZPosition: Float = 0f,
    val emitterSize: Point2f = Point2f(),
    val emitterDepth: Float = 0f,
    val emitterShape: String = "point", // "point", "rectangle", "cuboid", "circle", "sphere"
    val renderMode: String = "unordered", // "unordered", "oldestFirst", "oldestLast", "backToFront", "additive"
    val scale: Float = 1f,
    val birthRate: Float = 1f,
    val velocity: Float = 1f,
    val lifetime: Float = 1f,
    val spinBias: Float = 0f,
    val emitterCells: List<CAEmitterCell> = emptyList(),
    val emitterBehaviors: List<CAEmitterBehavior> = emptyList()
) : CamlLayer()

data class UnknownLayer(
    val rawTag: String,
    override val id: String?,
    override val name: String?,
    override val bounds: Rect4f = Rect4f(),
    override val position: Point2f = Point2f(),
    override val anchorPoint: Point2f = Point2f(0.5f, 0.5f),
    override val zPosition: Float = 0f,
    override val opacity: Float = 1f,
    override val hidden: Boolean = false,
    override val masksToBounds: Boolean = false,
    override val cornerRadius: Float = 0f,
    override val backgroundColor: Color4f? = null,
    override val contents: CamlContents? = null,
    override val transform: CATransform3D = CATransform3D.IDENTITY,
    override val sublayerTransform: CATransform3D = CATransform3D.IDENTITY,
    override val geometryFlipped: Boolean = false,
    override val sublayers: List<CamlLayer> = emptyList(),
    override val animations: List<CamlAnimation> = emptyList(),
    override val filters: List<CamlFilter> = emptyList(),
    val attributes: Map<String, String> = emptyMap()
) : CamlLayer()
