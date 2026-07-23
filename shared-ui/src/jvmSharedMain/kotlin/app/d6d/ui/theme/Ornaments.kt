package app.d6d.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import androidx.compose.ui.graphics.Canvas as bitmapCanvas

/**
 * Ornamenti disegnati da codice: cornici, fregi e luci del tema dark fantasy.
 *
 * Nessuna immagine importata — vale lo stesso vincolo di licenza dei ritratti —
 * quindi ogni decorazione e' un tracciato vettoriale. Sono tocchi piccoli e
 * riusabili: gradienti che danno rilievo ai pannelli, angoli dorati sulle targhe
 * e vignettature che concentrano la luce dove si gioca.
 */

/** Scurisce un colore verso il nero: serve ai gradienti "in ombra". */
internal fun Color.shaded(amount: Float): Color = lerp(this, Color.Black, amount)

/**
 * Sfondo dei pannelli: gradiente verticale appena percettibile, piu' chiaro in
 * alto come una superficie colpita dalla luce dall'alto.
 */
fun Modifier.panelBrush(
    shape: Shape,
    top: Color = Palette.SurfaceHigh,
    bottom: Color = Palette.Surface,
): Modifier = background(Brush.verticalGradient(listOf(top, bottom)), shape)

/**
 * Cornice ornamentale: quattro angoli a squadra, come i fermagli di metallo che
 * tengono ferma una mappa sul tavolo. Va sopra un normale bordo sottile: il
 * bordo delimita, gli angoli impreziosiscono.
 */
fun Modifier.ornateFrame(
    accent: Color = Palette.Gold,
    alpha: Float = 0.55f,
    cornerLength: Dp = 9.dp,
    inset: Dp = 4.dp,
): Modifier = drawBehind {
    val len = cornerLength.toPx()
    val pad = inset.toPx()
    val stroke = 1.4.dp.toPx()
    val color = accent.copy(alpha = alpha)
    val right = size.width - pad
    val bottom = size.height - pad

    // In alto a sinistra.
    drawLine(color, Offset(pad, pad), Offset(pad + len, pad), stroke, StrokeCap.Round)
    drawLine(color, Offset(pad, pad), Offset(pad, pad + len), stroke, StrokeCap.Round)
    // In alto a destra.
    drawLine(color, Offset(right - len, pad), Offset(right, pad), stroke, StrokeCap.Round)
    drawLine(color, Offset(right, pad), Offset(right, pad + len), stroke, StrokeCap.Round)
    // In basso a sinistra.
    drawLine(color, Offset(pad, bottom - len), Offset(pad, bottom), stroke, StrokeCap.Round)
    drawLine(color, Offset(pad, bottom), Offset(pad + len, bottom), stroke, StrokeCap.Round)
    // In basso a destra.
    drawLine(color, Offset(right, bottom - len), Offset(right, bottom), stroke, StrokeCap.Round)
    drawLine(color, Offset(right - len, bottom), Offset(right, bottom), stroke, StrokeCap.Round)
}

/**
 * Divisore con diamante centrale: la riga cede al centro a un piccolo rombo,
 * il fregio ricorrente del tema. Sostituisce le righe piatte nei pannelli.
 */
@Composable
fun OrnateDivider(
    modifier: Modifier = Modifier,
    color: Color = Palette.GoldDim,
) {
    Canvas(modifier.fillMaxWidth().height(9.dp)) {
        val cy = size.height / 2f
        val cx = size.width / 2f
        val half = 3.dp.toPx()
        val gap = half * 2.2f
        val line = color.copy(alpha = 0.45f)

        if (cx - gap > 0f) {
            drawLine(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, line),
                    startX = 0f,
                    endX = cx - gap,
                ),
                Offset(0f, cy),
                Offset(cx - gap, cy),
                1.dp.toPx(),
            )
            drawLine(
                Brush.horizontalGradient(
                    listOf(line, Color.Transparent),
                    startX = cx + gap,
                    endX = size.width,
                ),
                Offset(cx + gap, cy),
                Offset(size.width, cy),
                1.dp.toPx(),
            )
        }

        val diamond = Path().apply {
            moveTo(cx, cy - half)
            lineTo(cx + half, cy)
            lineTo(cx, cy + half)
            lineTo(cx - half, cy)
            close()
        }
        drawPath(diamond, color)
    }
}

/**
 * Filo d'oro: una riga alta un pixel che sfuma ai lati. Separa le fasce
 * dell'interfaccia (intestazione, comandi) senza il peso di un bordo pieno.
 */
@Composable
fun GoldenRule(
    modifier: Modifier = Modifier,
    color: Color = Palette.Gold,
    alpha: Float = 0.38f,
) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawRect(
            Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    color.copy(alpha = alpha),
                    color.copy(alpha = alpha),
                    Color.Transparent,
                ),
            ),
        )
    }
}

/**
 * Grana pellicolare: puntini chiari e scuri quasi invisibili, generati una sola
 * volta e ripetuti come trama.
 *
 * Serve da dithering: fra due colori scurissimi lo schermo dispone di pochi
 * livelli e un gradiente ampio si quantizza in anelli concentrici visibili
 * (banding). Il rumore li spezza e la sfumatura torna liscia. E' generata da
 * codice, quindi rispetta lo stesso vincolo di licenza dei ritratti.
 */
private const val GRAIN_SIDE = 180

private fun grainBitmap(): ImageBitmap {
    val bitmap = ImageBitmap(GRAIN_SIDE, GRAIN_SIDE)
    val random = Random(20260723)
    val bright = ArrayList<Offset>(GRAIN_SIDE * GRAIN_SIDE / 6)
    val dark = ArrayList<Offset>(GRAIN_SIDE * GRAIN_SIDE / 6)
    repeat(GRAIN_SIDE * GRAIN_SIDE / 3) {
        val point = Offset(random.nextFloat() * GRAIN_SIDE, random.nextFloat() * GRAIN_SIDE)
        if (random.nextBoolean()) bright += point else dark += point
    }
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        bitmapCanvas(bitmap),
        Size(GRAIN_SIDE.toFloat(), GRAIN_SIDE.toFloat()),
    ) {
        drawPoints(bright, PointMode.Points, Color.White, strokeWidth = 1f, alpha = 0.028f)
        drawPoints(dark, PointMode.Points, Color.Black, strokeWidth = 1f, alpha = 0.042f)
    }
    return bitmap
}

/** Pennello della grana, condiviso e pigro: la trama si ripete su ogni superficie. */
internal val grainBrush: ShaderBrush by lazy {
    ShaderBrush(ImageShader(grainBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * Vignettatura: gli angoli scuriscono e la luce resta al centro, come un lume
 * sospeso sopra il tavolo. Va disegnata sopra il contenuto, quindi e' un
 * composable da mettere per ultimo nel Box della mappa; non intercetta i tocchi.
 *
 * Le tappe intermedie ammorbidiscono la curva e la grana finale fa da dithering:
 * senza, il gradiente radiale su fondo scuro mostrerebbe anelli di colore.
 */
@Composable
fun Vignette(
    modifier: Modifier = Modifier,
    strength: Float = 0.30f,
) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                colorStops = arrayOf(
                    0.5f to Color.Transparent,
                    0.75f to Color.Black.copy(alpha = strength * 0.35f),
                    1f to Color.Black.copy(alpha = strength),
                ),
                center = center,
                radius = size.maxDimension * 0.72f,
            ),
        )
        drawRect(grainBrush)
    }
}
