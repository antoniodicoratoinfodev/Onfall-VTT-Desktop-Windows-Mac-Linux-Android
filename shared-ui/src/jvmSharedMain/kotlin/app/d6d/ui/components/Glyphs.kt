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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glifi dell'applicazione, disegnati come tracciati.
 *
 * Come i ritratti, niente immagini importate: le icone sono poche linee — spade
 * incrociate per la battaglia, un d20 per la nuova partita, un tomo per il
 * Compendio — cosi' l'identita' visiva resta originale e scala a ogni densita'.
 */
enum class AppGlyph { SWORDS, D20, TOME }

@Composable
fun GlyphIcon(
    glyph: AppGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(
            width = this.size.minDimension * 0.085f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (glyph) {
            AppGlyph.SWORDS -> drawSwords(tint, stroke)
            AppGlyph.D20 -> drawD20(tint, stroke)
            AppGlyph.TOME -> drawTome(tint, stroke)
        }
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
