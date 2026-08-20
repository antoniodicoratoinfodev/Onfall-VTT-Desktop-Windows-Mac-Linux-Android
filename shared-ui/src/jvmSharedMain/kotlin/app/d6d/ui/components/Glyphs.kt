package app.d6d.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glifi dell'applicazione, disegnati come tracciati.
 *
 * Come i ritratti, niente immagini importate: le icone sono poche linee — spade
 * incrociate per la battaglia, un d20 per la nuova partita, un tomo per il
 * Compendio, un ingranaggio per le impostazioni — cosi' l'identita' visiva resta
 * originale e scala a ogni densita'.
 */
enum class AppGlyph {
    SWORDS, D20, TOME, GEAR,
    TABLE, EDIT_BOARD, HAND, MEASURE, INK, TEMPLATE, LABEL, PING, FOG, FLOOR, WALL, LAYERS, ERASER, TOKEN,
}

@Composable
fun GlyphIcon(
    glyph: AppGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    /**
     * Nome letto dalle tecnologie assistive.
     *
     * Un tracciato su tela non ha testo: senza questo, un comando fatto di solo
     * glifo — la barra ridotta alle icone — non ha alcun nome da annunciare. Va
     * lasciato nullo quando accanto c'e' gia' un'etichetta visibile, altrimenti
     * lo stesso nome verrebbe letto due volte.
     */
    contentDescription: String? = null,
) {
    val described = contentDescription?.let { label ->
        modifier.semantics { this.contentDescription = label }
    } ?: modifier
    Canvas(described.size(size)) {
        val stroke = Stroke(
            width = this.size.minDimension * 0.085f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (glyph) {
            AppGlyph.SWORDS -> drawSwords(tint, stroke)
            AppGlyph.D20 -> drawD20(tint, stroke)
            AppGlyph.TOME -> drawTome(tint, stroke)
            AppGlyph.GEAR -> drawGear(tint, stroke)
            else -> drawBoardGlyph(glyph, tint, stroke)
        }
    }
}

/** Famiglia dei glifi del Lucido, costruita con lo stesso tratto degli originali. */
private fun DrawScope.drawBoardGlyph(glyph: AppGlyph, tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, scale: Float = 1f) =
        drawLine(tint, Offset(w * x1, h * y1), Offset(w * x2, h * y2), stroke.width * scale, StrokeCap.Round)

    when (glyph) {
        AppGlyph.TABLE -> {
            line(.16f, .24f, .84f, .24f); line(.22f, .24f, .28f, .82f)
            line(.78f, .24f, .72f, .82f); line(.25f, .58f, .75f, .58f, .75f)
        }
        AppGlyph.EDIT_BOARD -> {
            val cursor = Path().apply {
                moveTo(w * .20f, h * .14f); lineTo(w * .72f, h * .57f)
                lineTo(w * .49f, h * .62f); lineTo(w * .39f, h * .84f); close()
            }
            drawPath(cursor, tint, style = stroke)
            drawCircle(tint, stroke.width * .8f, Offset(w * .78f, h * .78f), style = stroke)
        }
        AppGlyph.HAND -> {
            line(.27f, .76f, .20f, .48f); line(.20f, .48f, .31f, .43f)
            line(.31f, .43f, .35f, .18f); line(.35f, .18f, .45f, .20f)
            line(.45f, .20f, .48f, .42f); line(.48f, .42f, .53f, .16f)
            line(.53f, .16f, .63f, .20f); line(.63f, .20f, .64f, .44f)
            line(.64f, .44f, .70f, .25f); line(.70f, .25f, .79f, .31f)
            line(.79f, .31f, .73f, .72f); line(.73f, .72f, .57f, .86f); line(.57f, .86f, .27f, .76f)
        }
        AppGlyph.MEASURE -> {
            line(.18f, .76f, .78f, .20f); drawCircle(tint, stroke.width, Offset(w * .18f, h * .76f))
            drawCircle(tint, stroke.width, Offset(w * .78f, h * .20f)); line(.30f, .65f, .36f, .72f, .7f)
            line(.46f, .50f, .52f, .57f, .7f); line(.62f, .35f, .68f, .42f, .7f)
        }
        AppGlyph.INK -> {
            val nib = Path().apply {
                moveTo(w * .20f, h * .77f); lineTo(w * .42f, h * .22f)
                lineTo(w * .79f, h * .14f); lineTo(w * .69f, h * .52f); close()
            }
            drawPath(nib, tint, style = stroke); line(.20f, .77f, .54f, .43f)
            drawCircle(tint, stroke.width * .65f, Offset(w * .54f, h * .43f))
        }
        AppGlyph.TEMPLATE -> {
            line(.18f, .78f, .48f, .18f); line(.18f, .78f, .83f, .63f)
            drawArc(tint, 300f, 82f, false, Offset(w * .23f, h * .27f), androidx.compose.ui.geometry.Size(w * .52f, h * .42f), style = stroke)
        }
        AppGlyph.LABEL -> {
            val scroll = Path().apply {
                moveTo(w * .25f, h * .18f); lineTo(w * .76f, h * .18f); lineTo(w * .72f, h * .75f)
                lineTo(w * .31f, h * .75f); lineTo(w * .25f, h * .18f)
            }
            drawPath(scroll, tint, style = stroke); line(.36f, .38f, .65f, .38f, .75f); line(.36f, .53f, .61f, .53f, .75f)
        }
        AppGlyph.PING -> {
            drawCircle(tint, w * .09f, center, style = stroke)
            drawCircle(tint, w * .24f, center, style = stroke)
            drawCircle(tint, w * .40f, center, style = stroke)
        }
        AppGlyph.FOG -> {
            line(.12f, .40f, .52f, .40f); line(.32f, .58f, .86f, .58f); line(.16f, .74f, .68f, .74f)
            drawCircle(tint.copy(alpha = .45f), w * .18f, Offset(w * .56f, h * .34f))
        }
        AppGlyph.FLOOR -> {
            drawRect(
                tint.copy(alpha = .16f), Offset(w * .14f, h * .14f),
                androidx.compose.ui.geometry.Size(w * .72f, h * .72f),
            )
            line(.14f, .14f, .86f, .14f); line(.86f, .14f, .86f, .86f)
            line(.86f, .86f, .14f, .86f); line(.14f, .86f, .14f, .14f)
            line(.50f, .14f, .50f, .86f); line(.14f, .50f, .86f, .50f)
            drawCircle(tint.copy(alpha = .55f), stroke.width * .55f, Offset(w * .32f, h * .32f))
            drawCircle(tint.copy(alpha = .55f), stroke.width * .55f, Offset(w * .68f, h * .68f))
        }
        AppGlyph.WALL -> {
            drawRect(tint.copy(alpha = .18f), Offset(w * .12f, h * .20f),
                androidx.compose.ui.geometry.Size(w * .76f, h * .62f))
            line(.12f, .20f, .88f, .20f); line(.88f, .20f, .88f, .82f)
            line(.88f, .82f, .12f, .82f); line(.12f, .82f, .12f, .20f)
            line(.12f, .50f, .88f, .50f); line(.38f, .20f, .38f, .50f)
            line(.66f, .50f, .66f, .82f)
        }
        AppGlyph.LAYERS -> {
            fun diamond(cy: Float) {
                val p = Path().apply {
                    moveTo(w * .50f, h * (cy - .18f)); lineTo(w * .84f, h * cy)
                    lineTo(w * .50f, h * (cy + .18f)); lineTo(w * .16f, h * cy); close()
                }
                drawPath(p, tint, style = stroke)
            }
            diamond(.35f); diamond(.62f)
        }
        AppGlyph.ERASER -> {
            val p = Path().apply {
                moveTo(w * .23f, h * .68f); lineTo(w * .56f, h * .19f); lineTo(w * .82f, h * .40f)
                lineTo(w * .50f, h * .81f); lineTo(w * .23f, h * .68f); close()
            }
            drawPath(p, tint, style = stroke); line(.38f, .57f, .62f, .76f, .75f)
        }
        AppGlyph.TOKEN -> {
            drawCircle(tint, w * .31f, Offset(w * .43f, h * .49f), style = stroke)
            drawCircle(tint, w * .12f, Offset(w * .43f, h * .40f), style = stroke)
            drawArc(
                tint,
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(w * .27f, h * .45f),
                size = androidx.compose.ui.geometry.Size(w * .32f, h * .25f),
                style = stroke,
            )
            line(.70f, .55f, .70f, .82f)
            line(.57f, .685f, .83f, .685f)
        }
        else -> Unit
    }
}

/** Due spade incrociate: lama, guardia perpendicolare e pomolo per ciascuna. */
private fun DrawScope.drawSwords(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height

    fun sword(fromX: Float, toX: Float) {
        val tip = Offset(w * toX, h * 0.14f)
        val heel = Offset(w * fromX, h * 0.78f)
        drawLine(tint, heel, tip, stroke.width, StrokeCap.Round)
        // Guardia: segmento perpendicolare alla lama, poco sopra l'impugnatura.
        val along = Offset(tip.x - heel.x, tip.y - heel.y)
        val guardCentre = Offset(heel.x + along.x * 0.22f, heel.y + along.y * 0.22f)
        val normal = Offset(-along.y, along.x)
        val magnitude = kotlin.math.sqrt(normal.x * normal.x + normal.y * normal.y)
        val guardHalf = w * 0.13f
        val unit = Offset(normal.x / magnitude, normal.y / magnitude)
        drawLine(
            tint,
            Offset(guardCentre.x - unit.x * guardHalf, guardCentre.y - unit.y * guardHalf),
            Offset(guardCentre.x + unit.x * guardHalf, guardCentre.y + unit.y * guardHalf),
            stroke.width,
            StrokeCap.Round,
        )
        // Pomolo.
        drawCircle(tint, radius = stroke.width * 0.55f, center = Offset(heel.x, heel.y + h * 0.05f))
    }

    sword(fromX = 0.18f, toX = 0.84f)
    sword(fromX = 0.82f, toX = 0.16f)
}

/** Icosaedro visto di fronte: esagono con il triangolo centrale delle facce. */
private fun DrawScope.drawD20(tint: Color, stroke: Stroke) {
    val radius = size.minDimension * 0.44f
    val c = center

    fun vertex(angleDegrees: Float): Offset {
        val radians = Math.toRadians(angleDegrees.toDouble())
        return Offset(
            c.x + radius * cos(radians).toFloat(),
            c.y + radius * sin(radians).toFloat(),
        )
    }

    val hex = List(6) { vertex(-90f + it * 60f) }
    val outline = Path().apply {
        moveTo(hex[0].x, hex[0].y)
        hex.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(outline, tint, style = stroke)

    // La faccia frontale: triangolo fra i vertici alterni.
    val face = Path().apply {
        moveTo(hex[0].x, hex[0].y)
        lineTo(hex[2].x, hex[2].y)
        lineTo(hex[4].x, hex[4].y)
        close()
    }
    drawPath(face, tint, style = Stroke(stroke.width * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Tomo chiuso: copertina, dorso e borchia a rombo. */
private fun DrawScope.drawTome(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val left = w * 0.20f
    val top = h * 0.14f
    val right = w * 0.82f
    val bottom = h * 0.86f

    val cover = Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right, bottom)
        lineTo(left, bottom)
        close()
    }
    drawPath(cover, tint, style = stroke)

    // Dorso rilegato.
    drawLine(tint, Offset(w * 0.33f, top), Offset(w * 0.33f, bottom), stroke.width * 0.8f, StrokeCap.Round)

    // Borchia a rombo al centro della copertina: il fregio ricorrente del tema.
    val bx = (w * 0.33f + right) / 2f
    val by = (top + bottom) / 2f
    val half = w * 0.09f
    val boss = Path().apply {
        moveTo(bx, by - half)
        lineTo(bx + half, by)
        lineTo(bx, by + half)
        lineTo(bx - half, by)
        close()
    }
    drawPath(boss, tint)
}

/** Ingranaggio: corona di otto denti e mozzo centrale. */
private fun DrawScope.drawGear(tint: Color, stroke: Stroke) {
    val teeth = 8
    val tip = size.minDimension * 0.46f
    val root = size.minDimension * 0.33f
    val c = center
    val step = 360f / teeth
    // Il dente occupa poco meno di meta' periodo e i fianchi sono inclinati: cosi'
    // pieni e vuoti si equivalgono e la corona resta leggibile anche a 20 dp.
    val toothHalf = step * 0.22f
    val flank = step * 0.10f

    fun point(angleDegrees: Float, radius: Float): Offset {
        val radians = Math.toRadians(angleDegrees.toDouble())
        return Offset(
            c.x + radius * cos(radians).toFloat(),
            c.y + radius * sin(radians).toFloat(),
        )
    }

    val crown = Path()
    repeat(teeth) { index ->
        val centre = -90f + index * step
        val corners = listOf(
            point(centre - toothHalf, tip),
            point(centre + toothHalf, tip),
            point(centre + toothHalf + flank, root),
            point(centre + step - toothHalf - flank, root),
        )
        corners.forEachIndexed { corner, offset ->
            if (index == 0 && corner == 0) crown.moveTo(offset.x, offset.y)
            else crown.lineTo(offset.x, offset.y)
        }
    }
    crown.close()
    drawPath(crown, tint, style = stroke)

    // Mozzo: il foro dell'albero, che distingue l'ingranaggio da una stella.
    drawCircle(tint, radius = size.minDimension * 0.14f, center = c, style = stroke)
}
