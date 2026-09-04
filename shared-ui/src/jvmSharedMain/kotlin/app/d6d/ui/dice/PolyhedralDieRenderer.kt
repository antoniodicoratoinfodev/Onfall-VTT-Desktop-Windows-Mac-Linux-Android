package app.d6d.ui.dice

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Geometria convessa normalizzata usata dal renderer software dei dadi. */
internal data class DieGeometry(
    val vertices: List<DieVector>,
    val faces: List<List<Int>>,
)

internal data class DieVector(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun plus(other: DieVector) = DieVector(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: DieVector) = DieVector(x - other.x, y - other.y, z - other.z)
    operator fun times(value: Double) = DieVector(x * value, y * value, z * value)
    operator fun div(value: Double) = DieVector(x / value, y / value, z / value)

    fun dot(other: DieVector) = x * other.x + y * other.y + z * other.z
    fun cross(other: DieVector) = DieVector(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )
    fun length() = sqrt(dot(this))
    fun normalized(): DieVector {
        val magnitude = length()
        return if (magnitude < 1e-9) this else this / magnitude
    }
}

/** Posa conclusiva della faccia estratta, esposta ai test geometrici. */
internal data class SettledDieFace(
    val normal: DieVector,
    val up: DieVector,
)

private data class DieQuaternion(
    val w: Double,
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun times(other: DieQuaternion) = DieQuaternion(
        w * other.w - x * other.x - y * other.y - z * other.z,
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
    )

    fun normalized(): DieQuaternion {
        val magnitude = sqrt(w * w + x * x + y * y + z * z)
        return DieQuaternion(w / magnitude, x / magnitude, y / magnitude, z / magnitude)
    }

    fun rotate(vector: DieVector): DieVector {
        val q = normalized()
        val pure = DieQuaternion(0.0, vector.x, vector.y, vector.z)
        val inverse = DieQuaternion(q.w, -q.x, -q.y, -q.z)
        val rotated = q * pure * inverse
        return DieVector(rotated.x, rotated.y, rotated.z)
    }

    companion object {
        val Identity = DieQuaternion(1.0, 0.0, 0.0, 0.0)

        fun axisAngle(axis: DieVector, angle: Double): DieQuaternion {
            val normalizedAxis = axis.normalized()
            val half = angle / 2.0
            val sine = sin(half)
            return DieQuaternion(
                cos(half),
                normalizedAxis.x * sine,
                normalizedAxis.y * sine,
                normalizedAxis.z * sine,
            ).normalized()
        }

        fun euler(x: Double, y: Double, z: Double): DieQuaternion {
            val qx = axisAngle(DieVector(1.0, 0.0, 0.0), x)
            val qy = axisAngle(DieVector(0.0, 1.0, 0.0), y)
            val qz = axisAngle(DieVector(0.0, 0.0, 1.0), z)
            return (qz * qy * qx).normalized()
        }

        fun fromTo(from: DieVector, to: DieVector): DieQuaternion {
            val start = from.normalized()
            val end = to.normalized()
            val dot = start.dot(end).coerceIn(-1.0, 1.0)
            if (dot > 0.999999) return Identity
            if (dot < -0.999999) {
                val helper = if (abs(start.x) < 0.8) DieVector(1.0, 0.0, 0.0) else DieVector(0.0, 1.0, 0.0)
                return axisAngle(start.cross(helper), PI)
            }
            val cross = start.cross(end)
            return DieQuaternion(1.0 + dot, cross.x, cross.y, cross.z).normalized()
        }

        fun slerp(from: DieQuaternion, to: DieQuaternion, amount: Double): DieQuaternion {
            var destination = to
            var dot = from.w * to.w + from.x * to.x + from.y * to.y + from.z * to.z
            if (dot < 0.0) {
                destination = DieQuaternion(-to.w, -to.x, -to.y, -to.z)
                dot = -dot
            }
            if (dot > 0.9995) {
                return DieQuaternion(
                    from.w + amount * (destination.w - from.w),
                    from.x + amount * (destination.x - from.x),
                    from.y + amount * (destination.y - from.y),
                    from.z + amount * (destination.z - from.z),
                ).normalized()
            }
            val angle = acos(dot.coerceIn(-1.0, 1.0))
            val sine = sin(angle)
            val first = sin((1.0 - amount) * angle) / sine
            val second = sin(amount * angle) / sine
            return DieQuaternion(
                from.w * first + destination.w * second,
                from.x * first + destination.x * second,
                from.y * first + destination.y * second,
                from.z * first + destination.z * second,
            ).normalized()
        }
    }
}

private data class VisibleDieFace(
    val index: Int,
    val depth: Double,
    val normal: DieVector,
    val centre3d: DieVector,
    val up3d: DieVector,
    val right3d: DieVector,
    val radius3d: Double,
    val points: List<Offset>,
    val centre: Offset,
)

private data class DiePlanePoint(
    val x: Double,
    val y: Double,
)

private val hundredSidedGeometry: DieGeometry by lazy { hundredSidedDie() }

/** Restituisce il solido corretto per ogni dado supportato dal vassoio. */
internal fun dieGeometry(sides: Int): DieGeometry = when (sides) {
    4 -> tetrahedron()
    6 -> cube()
    8 -> octahedron()
    10 -> pentagonalTrapezohedron()
    12 -> dual(icosahedron())
    20 -> icosahedron()
    100 -> hundredSidedGeometry
    else -> error("Unsupported polyhedral die: d$sides")
}

/** Indice della faccia che il renderer portera' davanti alla camera. */
internal fun resultFaceIndex(sides: Int, value: Int): Int =
    if (sides == 10 && value == 0) 0 else (value - 1).mod(sides)

/** Diagnostica deterministica usata per garantire che l'esito sia davvero la faccia piu' frontale. */
internal fun settledFrontFaceIndex(sides: Int, targetFaceIndex: Int): Int {
    val geometry = dieGeometry(sides)
    val target = targetOrientation(geometry, targetFaceIndex.mod(geometry.faces.size))
    val transformed = geometry.vertices.map(target::rotate)
    return geometry.faces.indices.maxBy { faceIndex ->
        faceNormal(geometry.faces[faceIndex].map(transformed::get)).z
    }
}

/**
 * La faccia estratta deve essere parallela allo schermo e leggibile diritta.
 * Verificare soltanto che fosse «la piu' frontale» permetteva ancora pose molto inclinate.
 */
internal fun settledDieFace(sides: Int, targetFaceIndex: Int): SettledDieFace {
    val geometry = dieGeometry(sides)
    val faceIndex = targetFaceIndex.mod(geometry.faces.size)
    val target = targetOrientation(geometry, faceIndex)
    val originalVertices = geometry.faces[faceIndex].map(geometry.vertices::get)
    val vertices = geometry.faces[faceIndex].map { vertexIndex ->
        target.rotate(geometry.vertices[vertexIndex])
    }
    return SettledDieFace(
        normal = faceNormal(vertices),
        up = target.rotate(faceLabelUpDirection(originalVertices)),
    )
}

@Composable
internal fun PolyhedralDie(
    sides: Int,
    faceLabels: List<String>,
    targetFaceIndex: Int,
    progress: Float,
    phaseOffset: Int,
    reducedEffects: Boolean,
    palette: DiceSkinPalette,
    modifier: Modifier = Modifier,
) {
    val geometry = remember(sides) { dieGeometry(sides) }
    val target = remember(geometry, targetFaceIndex) {
        targetOrientation(geometry, targetFaceIndex.mod(geometry.faces.size))
    }
    val faceLabelUps = remember(geometry) {
        geometry.faces.map { face -> faceLabelUpDirection(face.map(geometry.vertices::get)) }
    }
    val ready = remember(phaseOffset) {
        DieQuaternion.euler(
            x = 0.48 + phaseOffset * 0.07,
            y = (if (phaseOffset % 2 == 0) -0.62 else 0.62),
            z = 0.16 * (phaseOffset + 1),
        )
    }
    val rawProgress = progress.toDouble()
    val smooth = rawProgress * rawProgress * (3.0 - 2.0 * rawProgress)
    val base = DieQuaternion.slerp(ready, target, smooth)
    val direction = if (phaseOffset % 2 == 0) 1.0 else -1.0
    val spin = if (reducedEffects) {
        DieQuaternion.Identity
    } else {
        DieQuaternion.euler(
            x = rawProgress * PI * 4.0,
            y = direction * rawProgress * PI * 6.0,
            z = direction * rawProgress * PI * 4.0,
        )
    }
    val orientation = (spin * base).normalized()
    val textMeasurer = rememberTextMeasurer(cacheSize = 128)

    Canvas(modifier) {
        val transformed = geometry.vertices.map(orientation::rotate)
        val cameraDistance = 4.4
        val objectScale = size.minDimension.toDouble() * 0.40
        fun project(vector: DieVector): Offset {
            val perspective = cameraDistance / (cameraDistance - vector.z)
            return Offset(
                center.x + (vector.x * objectScale * perspective).toFloat(),
                center.y - (vector.y * objectScale * perspective).toFloat(),
            )
        }

        val visibleFaces = geometry.faces.mapIndexedNotNull { index, face ->
            val vertices = face.map(transformed::get)
            val centre3d = vertices.reduce(DieVector::plus) / vertices.size.toDouble()
            val normal = faceNormal(vertices)
            if (normal.z <= 0.015) return@mapIndexedNotNull null
            val centre2d = project(centre3d)
            // L'orientamento nasce nello spazio locale del solido e ruota con esso:
            // ogni numero resta davvero stampato sulla propria faccia.
            val up3d = orientation.rotate(faceLabelUps[index]).normalized()
            VisibleDieFace(
                index = index,
                depth = centre3d.z,
                normal = normal,
                centre3d = centre3d,
                up3d = up3d,
                right3d = up3d.cross(normal).normalized(),
                radius3d = vertices.minOf { vertex -> (vertex - centre3d).length() },
                points = vertices.map(::project),
                centre = centre2d,
            )
        }.sortedBy(VisibleDieFace::depth)

        val light = DieVector(-0.40, 0.62, 0.68).normalized()
        visibleFaces.forEach { face ->
            val path = Path().apply {
                moveTo(face.points.first().x, face.points.first().y)
                face.points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val diffuse = max(0.0, face.normal.dot(light))
            val brightness = (0.22 + diffuse * 0.78).toFloat()
            val faceColor = lerp(palette.faceBottom, palette.faceTop, brightness)
            val minimumX = face.points.minOf(Offset::x)
            val minimumY = face.points.minOf(Offset::y)
            val maximumX = face.points.maxOf(Offset::x)
            val maximumY = face.points.maxOf(Offset::y)
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        lerp(faceColor, Color.White, 0.10f),
                        faceColor,
                        lerp(faceColor, Color.Black, 0.20f),
                    ),
                    start = Offset(minimumX, minimumY),
                    end = Offset(maximumX, maximumY),
                ),
            )
            if (diffuse > 0.78) {
                drawPath(path, Color.White.copy(alpha = ((diffuse - 0.78) * 0.30).toFloat()))
            }
            val blackEdgeWidth = size.minDimension * if (sides == 100) 0.006f else 0.018f
            val accentEdgeWidth = size.minDimension * if (sides == 100) 0.0022f else 0.0065f
            drawPath(path, Color.Black.copy(alpha = 0.52f), style = Stroke(blackEdgeWidth))
            drawPath(path, palette.edge.copy(alpha = 0.88f), style = Stroke(accentEdgeWidth))

            if (face.normal.z > 0.10) {
                val label = faceLabels.getOrElse(face.index) { (face.index + 1).toString() }
                val centrePerspective = cameraDistance / (cameraDistance - face.centre3d.z)
                val nominalRadiusPixels = face.radius3d * objectScale * centrePerspective
                val labelScale = when (label.length) {
                    1 -> 0.78f
                    2 -> 0.62f
                    else -> 0.46f
                }
                val fontPixels = (nominalRadiusPixels.toFloat() * labelScale)
                    .coerceIn(size.minDimension * 0.035f, size.minDimension * 0.13f)
                val style = TextStyle(
                    color = palette.number,
                    fontSize = fontPixels.toSp(),
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(Color.Black.copy(alpha = 0.88f), blurRadius = 2.2f),
                )
                val layout = textMeasurer.measure(label, style = style)
                // Derivata prospettica centrata dei due assi della faccia. La matrice
                // risultante inclina, comprime e taglia il numero esattamente come il
                // piano su cui e' inciso; non rimane piu' un'etichetta verso la camera.
                val sample = 0.18
                val rightAxis = (
                    project(face.centre3d + face.right3d * sample) -
                        project(face.centre3d - face.right3d * sample)
                    ) / (2.0 * objectScale * sample * centrePerspective).toFloat()
                val upAxis = (
                    project(face.centre3d + face.up3d * sample) -
                        project(face.centre3d - face.up3d * sample)
                    ) / (2.0 * objectScale * sample * centrePerspective).toFloat()
                val faceTransform = Matrix().apply {
                    this[0, 0] = rightAxis.x
                    this[0, 1] = rightAxis.y
                    this[1, 0] = -upAxis.x
                    this[1, 1] = -upAxis.y
                    this[3, 0] = face.centre.x
                    this[3, 1] = face.centre.y
                }
                clipPath(path) {
                    withTransform({ transform(faceTransform) }) {
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                -layout.size.width / 2f,
                                -layout.size.height / 2f,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun targetOrientation(geometry: DieGeometry, faceIndex: Int): DieQuaternion {
    val face = geometry.faces[faceIndex]
    val vertices = face.map(geometry.vertices::get)
    val normal = faceNormal(vertices)
    // La camera guarda lungo +Z: la normale della faccia risultante deve coincidere
    // esattamente con quell'asse, non soltanto essere la normale piu' vicina.
    val cameraFacing = DieVector(0.0, 0.0, 1.0)
    val align = DieQuaternion.fromTo(normal, cameraFacing)
    val tangentUp = faceLabelUpDirection(vertices)
    val alignedUp = align.rotate(tangentUp)
    val worldUp = DieVector(0.0, 1.0, 0.0)
    // Il twist e' deliberatamente attorno alla direzione della camera: in questo
    // modo raddrizzare il numero non puo' inclinare nuovamente la faccia.
    val twistAngle = atan2(
        cameraFacing.dot(alignedUp.cross(worldUp)),
        alignedUp.dot(worldUp),
    )
    val twist = DieQuaternion.axisAngle(cameraFacing, twistAngle)
    return (twist * align).normalized()
}

/**
 * Asse verticale convenzionale e stabile di una faccia.
 *
 * Il primo vertice di una mesh e' un dettaglio d'implementazione e non puo' decidere
 * l'orientamento di una cifra. Proiettiamo invece un unico «nord» del dado sul piano
 * della faccia e scegliamo l'asse di simmetria piu' vicino: vertice per triangoli e
 * pentagoni, lato per quadrati. Le losanghe del d10 mantengono il proprio asse lungo.
 * Il vettore ottenuto resta in coordinate locali e viene poi ruotato assieme al dado.
 */
private fun faceLabelUpDirection(vertices: List<DieVector>): DieVector {
    val centre = vertices.reduce(DieVector::plus) / vertices.size.toDouble()
    val directions = vertices.map { it - centre }
    val shortest = directions.minOf(DieVector::length)
    val longest = directions.maxOf(DieVector::length)
    val candidates = if (vertices.size == 4) {
        if (longest > shortest * 1.08) {
            directions.filter { direction -> direction.length() >= longest * 0.999 }
        } else {
            vertices.indices.map { index ->
                ((vertices[index] + vertices[(index + 1) % vertices.size]) / 2.0) - centre
            }
        }
    } else {
        directions
    }
    val normal = faceNormal(vertices)
    val dieNorth = DieVector(0.173, 0.963, 0.207).normalized()
    val projectedNorth = dieNorth - normal * dieNorth.dot(normal)
    val reference = if (projectedNorth.length() > 0.12) {
        projectedNorth.normalized()
    } else {
        val fallback = DieVector(0.811, -0.119, 0.573).normalized()
        (fallback - normal * fallback.dot(normal)).normalized()
    }
    return candidates
        .map { candidate -> (candidate - normal * candidate.dot(normal)).normalized() }
        .maxBy { candidate -> candidate.dot(reference) }
}

private fun faceNormal(vertices: List<DieVector>): DieVector =
    (vertices[1] - vertices[0]).cross(vertices[2] - vertices[0]).normalized()

private fun tetrahedron() = geometry(
    vertices = listOf(
        DieVector(1.0, 1.0, 1.0),
        DieVector(-1.0, -1.0, 1.0),
        DieVector(-1.0, 1.0, -1.0),
        DieVector(1.0, -1.0, -1.0),
    ),
    faces = listOf(
        listOf(0, 1, 2),
        listOf(0, 3, 1),
        listOf(0, 2, 3),
        listOf(1, 3, 2),
    ),
)

private fun cube() = geometry(
    vertices = listOf(
        DieVector(-1.0, -1.0, -1.0), DieVector(1.0, -1.0, -1.0),
        DieVector(1.0, 1.0, -1.0), DieVector(-1.0, 1.0, -1.0),
        DieVector(-1.0, -1.0, 1.0), DieVector(1.0, -1.0, 1.0),
        DieVector(1.0, 1.0, 1.0), DieVector(-1.0, 1.0, 1.0),
    ),
    faces = listOf(
        listOf(4, 5, 6, 7), listOf(1, 0, 3, 2),
        listOf(0, 4, 7, 3), listOf(5, 1, 2, 6),
        listOf(3, 7, 6, 2), listOf(0, 1, 5, 4),
    ),
)

private fun octahedron() = geometry(
    vertices = listOf(
        DieVector(1.0, 0.0, 0.0), DieVector(-1.0, 0.0, 0.0),
        DieVector(0.0, 1.0, 0.0), DieVector(0.0, -1.0, 0.0),
        DieVector(0.0, 0.0, 1.0), DieVector(0.0, 0.0, -1.0),
    ),
    faces = listOf(
        listOf(4, 0, 2), listOf(4, 2, 1), listOf(4, 1, 3), listOf(4, 3, 0),
        listOf(5, 2, 0), listOf(5, 1, 2), listOf(5, 3, 1), listOf(5, 0, 3),
    ),
)

private fun icosahedron(): DieGeometry {
    val golden = (1.0 + sqrt(5.0)) / 2.0
    return geometry(
        vertices = listOf(
            DieVector(-1.0, golden, 0.0), DieVector(1.0, golden, 0.0),
            DieVector(-1.0, -golden, 0.0), DieVector(1.0, -golden, 0.0),
            DieVector(0.0, -1.0, golden), DieVector(0.0, 1.0, golden),
            DieVector(0.0, -1.0, -golden), DieVector(0.0, 1.0, -golden),
            DieVector(golden, 0.0, -1.0), DieVector(golden, 0.0, 1.0),
            DieVector(-golden, 0.0, -1.0), DieVector(-golden, 0.0, 1.0),
        ),
        faces = listOf(
            listOf(0, 11, 5), listOf(0, 5, 1), listOf(0, 1, 7), listOf(0, 7, 10), listOf(0, 10, 11),
            listOf(1, 5, 9), listOf(5, 11, 4), listOf(11, 10, 2), listOf(10, 7, 6), listOf(7, 1, 8),
            listOf(3, 9, 4), listOf(3, 4, 2), listOf(3, 2, 6), listOf(3, 6, 8), listOf(3, 8, 9),
            listOf(4, 9, 5), listOf(2, 4, 11), listOf(6, 2, 10), listOf(8, 6, 7), listOf(9, 8, 1),
        ),
    )
}

private fun pentagonalTrapezohedron(): DieGeometry {
    val ring = 5
    val top = List(ring) { index ->
        val angle = index * 2.0 * PI / ring
        DieVector(cos(angle), sin(angle), 0.5)
    }
    val bottom = List(ring) { index ->
        val angle = index * 2.0 * PI / ring + PI / ring
        DieVector(cos(angle), sin(angle), -0.5)
    }
    val faces = buildList {
        add((0 until ring).toList())
        add((0 until ring).map { ring + it }.reversed())
        repeat(ring) { index ->
            val next = (index + 1) % ring
            val previous = (index - 1).mod(ring)
            add(listOf(index, ring + index, ring + previous))
            add(listOf(index, next, ring + index))
        }
    }
    return dual(geometry(top + bottom, faces))
}

/**
 * Costruisce un unico d100 quasi sferico come intersezione di cento piani tangenti.
 *
 * Le normali sono distribuite con una spirale aurea; ciascuna diventa una faccia
 * reale del solido. Intersecare i relativi semispazi produce facce compatte e
 * leggibili, invece delle losanghe estremamente strette di un trapezoedro a 100 lati.
 */
private fun hundredSidedDie(): DieGeometry {
    val sides = 100
    val goldenAngle = PI * (3.0 - sqrt(5.0))
    val normals = List(sides) { index ->
        val vertical = 1.0 - 2.0 * (index + 0.5) / sides
        val horizontal = sqrt(1.0 - vertical * vertical)
        val angle = index * goldenAngle
        DieVector(
            x = cos(angle) * horizontal,
            y = vertical,
            z = sin(angle) * horizontal,
        )
    }
    val vertices = mutableListOf<DieVector>()
    val faces = normals.mapIndexed { normalIndex, normal ->
        val helper = if (abs(normal.y) < 0.9) {
            DieVector(0.0, 1.0, 0.0)
        } else {
            DieVector(1.0, 0.0, 0.0)
        }
        val tangent = helper.cross(normal).normalized()
        val bitangent = normal.cross(tangent).normalized()
        var polygon = listOf(
            DiePlanePoint(-4.0, -4.0),
            DiePlanePoint(4.0, -4.0),
            DiePlanePoint(4.0, 4.0),
            DiePlanePoint(-4.0, 4.0),
        )
        normals.forEachIndexed { clippingIndex, clippingNormal ->
            if (clippingIndex != normalIndex) {
                polygon = clipDieFace(
                    polygon = polygon,
                    a = clippingNormal.dot(tangent),
                    b = clippingNormal.dot(bitangent),
                    boundary = 1.0 - clippingNormal.dot(normal),
                )
            }
        }
        polygon = polygon.filterIndexed { index, point ->
            val previous = polygon[(index - 1).mod(polygon.size)]
            val dx = point.x - previous.x
            val dy = point.y - previous.y
            dx * dx + dy * dy > 1e-14
        }
        require(polygon.size >= 3) { "Invalid d100 face" }
        polygon.map { point ->
            val vertex = normal + tangent * point.x + bitangent * point.y
            val existing = vertices.indexOfFirst { candidate ->
                (candidate - vertex).dot(candidate - vertex) < 1e-12
            }
            if (existing >= 0) existing else vertices.size.also { vertices += vertex }
        }
    }
    return geometry(vertices, faces)
}

/** Taglio di Sutherland-Hodgman nel piano locale di una faccia. */
private fun clipDieFace(
    polygon: List<DiePlanePoint>,
    a: Double,
    b: Double,
    boundary: Double,
): List<DiePlanePoint> {
    if (polygon.isEmpty()) return emptyList()
    val clipped = mutableListOf<DiePlanePoint>()
    polygon.indices.forEach { index ->
        val current = polygon[index]
        val next = polygon[(index + 1) % polygon.size]
        val currentDistance = a * current.x + b * current.y - boundary
        val nextDistance = a * next.x + b * next.y - boundary
        val currentInside = currentDistance <= 1e-10
        val nextInside = nextDistance <= 1e-10
        if (currentInside) clipped += current
        if (currentInside != nextInside) {
            val amount = currentDistance / (currentDistance - nextDistance)
            clipped += DiePlanePoint(
                x = current.x + (next.x - current.x) * amount,
                y = current.y + (next.y - current.y) * amount,
            )
        }
    }
    return clipped
}

/** Costruisce d10 e d12 come duali, ordinando le facce attorno a ogni vertice. */
private fun dual(source: DieGeometry): DieGeometry {
    val dualVertices = source.faces.map { face ->
        face.map(source.vertices::get).reduce(DieVector::plus).normalized()
    }
    val dualFaces = source.vertices.indices.map { vertexIndex ->
        val axis = source.vertices[vertexIndex].normalized()
        val helper = if (abs(axis.z) < 0.85) DieVector(0.0, 0.0, 1.0) else DieVector(0.0, 1.0, 0.0)
        val tangent = helper.cross(axis).normalized()
        val bitangent = axis.cross(tangent).normalized()
        source.faces.indices
            .filter { vertexIndex in source.faces[it] }
            .sortedBy { faceIndex ->
                val point = dualVertices[faceIndex]
                atan2(point.dot(bitangent), point.dot(tangent))
            }
    }
    return geometry(dualVertices, dualFaces)
}

/** Normalizza il raggio e garantisce che tutte le facce guardino verso l'esterno. */
private fun geometry(vertices: List<DieVector>, faces: List<List<Int>>): DieGeometry {
    val radius = vertices.maxOf(DieVector::length)
    val normalizedVertices = vertices.map { it / radius }
    val orientedFaces = faces.map { face ->
        val points = face.map(normalizedVertices::get)
        val centre = points.reduce(DieVector::plus) / points.size.toDouble()
        if (faceNormal(points).dot(centre) < 0.0) face.reversed() else face
    }
    return DieGeometry(normalizedVertices, orientedFaces)
}
