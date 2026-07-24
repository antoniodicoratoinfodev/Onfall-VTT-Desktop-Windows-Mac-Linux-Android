package app.d6d.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.Canvas as bitmapCanvas

/**
 * Fondale atmosferico dell'applicazione: "dark fantasy cupo".
 *
 * Nessuna immagine importata — vale lo stesso vincolo di licenza dei ritratti e
 * degli ornamenti — quindi anche lo sfondo e' interamente disegnato da codice.
 * La scena e' una cripta quasi buia attraversata da una luce fredda: il fondo
 * tende al nero, ma dove batte la luce la pietra emerge a rilievo, incisa di
 * crepe; poche particelle salgono lente nell'aria.
 *
 * E' pensato per stare dietro i menu e i pannelli, che vi si dissolvono sopra: la
 * mappa tattica dipinge invece il proprio fondo opaco e resta pulita.
 *
 * ## Perche' non c'e' foschia in movimento
 * Sul nero i gradienti radiali a bassa opacita' si quantizzano in anelli visibili
 * (banding): fermi si dominano col dithering, ma se il gradiente si muove gli
 * anelli scorrono e sfarfallano. Percio' le uniche cose animate sono le particelle,
 * che sono punti netti e non creano banding; ogni sfumatura resta ferma e dithered.
 *
 * ## Dithering
 * I gradienti scuri (bagliore, vignettatura) sono coperti da un rumore fine a
 * copertura piena e ampiezza bassissima: spezza gli anelli restando invisibile.
 * E' distinto dalla grana pellicolare piu' marcata usata sulla mappa.
 */
@Composable
fun AtmosphericBackground(
    modifier: Modifier = Modifier,
    embers: Boolean = true,
    vignette: Float = 0.52f,
) {
    // Strato fermo: base, luce radente, pietra, crepe, vignettatura e dithering. Legge
    // solo la dimensione, quindi si ridisegna soltanto al ridimensionamento.
    Canvas(modifier.fillMaxSize()) {
        drawStoneField(vignette)
    }

    if (embers) {
        val transition = rememberInfiniteTransition(label = "backdrop")
        // Un tempo 0..1 in ciclo continuo. Ogni particella percorre un numero intero di
        // altezze per ciclo, cosi' il salto di riavvio e' invisibile.
        val time by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 21_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "emberDrift",
        )
        Canvas(modifier.fillMaxSize()) {
            drawEmbers(time)
        }
    }
}

/** Centro della luce radente, in frazioni della superficie. */
private const val GLOW_X = 0.5f
private const val GLOW_Y = 0.36f

/**
 * La cripta illuminata da un alone d'acciaio: base scura, pietra a rilievo,
 * crepe incise, vignettatura forte e dithering. Disegnata una volta per dimensione.
 */
private fun DrawScope.drawStoneField(vignetteStrength: Float) {
    val w = size.width
    val h = size.height
    val minDim = size.minDimension
    val maxDim = size.maxDimension
    val glow = Offset(w * GLOW_X, h * GLOW_Y)

    // Base: notte in alto, abisso verso il basso e i bordi.
    drawRect(Brush.verticalGradient(listOf(Palette.Night, Palette.Abyss)))

    // Alone freddo a basso contrasto e con caduta lunga fino al bordo. Meno
    // contrasto = meno gradini di quantizzazione da nascondere, cosi' il
    // dithering sotto riesce a renderlo del tutto liscio.
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to Palette.Bronze.copy(alpha = 0.30f),
                0.20f to Palette.Bronze.copy(alpha = 0.17f),
                0.42f to Palette.Bronze.copy(alpha = 0.08f),
                0.65f to Palette.Bronze.copy(alpha = 0.03f),
                1f to Color.Transparent,
            ),
            center = glow,
            radius = maxDim * 1.0f,
        ),
    )
    // Nucleo appena piu' chiaro, anch'esso tenue e a caduta morbida.
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to Palette.GoldDim.copy(alpha = 0.10f),
                0.4f to Palette.GoldDim.copy(alpha = 0.03f),
                0.75f to Color.Transparent,
            ),
            center = glow,
            radius = maxDim * 0.44f,
        ),
    )

    // Seme fisso: la pietra e' sempre la stessa, non danza fra un disegno e l'altro.
    val random = Random(90_210)

    // Chiazze di pietra: solo scure, tenui e uniformi, per la nuvolosita' della
    // roccia. Niente facce "illuminate": sporcavano il bagliore con altri gradini.
    repeat(160) {
        val c = Offset(random.nextFloat() * w, random.nextFloat() * h)
        val radius = (0.05f + 0.13f * random.nextFloat()) * minDim
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.Black.copy(alpha = 0.07f), Color.Transparent),
                center = c,
                radius = radius,
            ),
            radius = radius,
            center = c,
        )
    }

    // Crepe incise nella pietra: numerose e leggibili. Prima un bordo freddo in luce,
    // poi l'incisione scura sopra, cosi' la fenditura sembra scavata.
    repeat(13) {
        val path = Path()
        var x = random.nextFloat() * w
        var y = random.nextFloat() * h * 0.25f
        path.moveTo(x, y)
        val segments = 8 + random.nextInt(7)
        repeat(segments) {
            x += (random.nextFloat() - 0.5f) * w * 0.06f
            y += (h / segments) * (0.5f + 0.8f * random.nextFloat())
            path.lineTo(x, y)
        }
        drawPath(path, Palette.Bronze.copy(alpha = 0.08f), style = Stroke(width = 2.6f, cap = StrokeCap.Round))
        drawPath(path, Color.Black.copy(alpha = 0.24f), style = Stroke(width = 1.2f, cap = StrokeCap.Round))
    }

    // Vignettatura forte, ma a molti passaggi cosi' scende senza scalini.
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0.30f to Color.Transparent,
                0.55f to Color.Black.copy(alpha = vignetteStrength * 0.18f),
                0.75f to Color.Black.copy(alpha = vignetteStrength * 0.42f),
                0.90f to Color.Black.copy(alpha = vignetteStrength * 0.64f),
                1f to Color.Black.copy(alpha = vignetteStrength),
            ),
            center = center,
            radius = maxDim * 0.86f,
        ),
    )
    // Dithering fine sopra ogni sfumatura scura: rompe gli anelli di banding
    // restando quasi invisibile. Distinto dalla grana piu' marcata della mappa.
    drawRect(backdropDither)
}

/**
 * Rumore di dithering dedicato al fondale: copertura piena (ogni pixel ha un micro
 * scarto in piu' o in meno) ma ampiezza bassissima. Rende lisce le sfumature scure
 * senza leggersi come grana. Generato una sola volta e ripetuto come trama.
 */
private const val DITHER_SIDE = 140

private fun ditherBitmap(): ImageBitmap {
    val bitmap = ImageBitmap(DITHER_SIDE, DITHER_SIDE)
    val random = Random(13_09)
    val bright = ArrayList<Offset>(DITHER_SIDE * DITHER_SIDE / 2)
    val dark = ArrayList<Offset>(DITHER_SIDE * DITHER_SIDE / 2)
    for (y in 0 until DITHER_SIDE) {
        for (x in 0 until DITHER_SIDE) {
            val point = Offset(x + 0.5f, y + 0.5f)
            if (random.nextBoolean()) bright += point else dark += point
        }
    }
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        bitmapCanvas(bitmap),
        Size(DITHER_SIDE.toFloat(), DITHER_SIDE.toFloat()),
    ) {
        drawPoints(bright, PointMode.Points, Color.White, strokeWidth = 1f, alpha = 0.020f)
        drawPoints(dark, PointMode.Points, Color.Black, strokeWidth = 1f, alpha = 0.028f)
    }
    return bitmap
}

private val backdropDither: ShaderBrush by lazy {
    ShaderBrush(ImageShader(ditherBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/**
 * Una particella: colonna e altezza iniziale in frazioni (0..1), velocita' di salita in
 * numeri interi di altezze per ciclo — cosi' il giro si chiude senza scatti.
 */
private class Ember(
    val x: Float,
    val startY: Float,
    val rise: Int,
    val radius: Float,
    val phase: Float,
    val flicker: Int,
    val drift: Float,
)

// Poche particelle fredde e discrete, con seme fisso.
private val embers: List<Ember> by lazy {
    val random = Random(4_242)
    List(15) {
        Ember(
            x = random.nextFloat(),
            startY = random.nextFloat(),
            rise = if (random.nextFloat() > 0.65f) 2 else 1,
            radius = 1.2f + random.nextFloat() * 1.9f,
            phase = random.nextFloat(),
            flicker = 2 + random.nextInt(3),
            drift = (random.nextFloat() - 0.5f) * 0.06f,
        )
    }
}

/** Parte frazionaria sempre positiva: tiene la particella nell'intervallo 0..1. */
private fun frac(value: Float): Float = ((value % 1f) + 1f) % 1f

private fun DrawScope.drawEmbers(time: Float) {
    val w = size.width
    val h = size.height
    embers.forEach { ember ->
        // Sale: y cala nel tempo. Il moltiplicatore intero fa combaciare inizio e
        // fine del ciclo. La deriva orizzontale oscilla con periodo unitario.
        val y = frac(ember.startY - time * ember.rise)
        val x = frac(ember.x + ember.drift * sin(2.0 * PI * (time + ember.phase)).toFloat())
        // Dissolvenza ai bordi: la particella nasce fioca in basso e si spegne in alto,
        // percio' il momento del riavvolgimento (y ~ 0 o 1) e' gia' trasparente.
        val fade = sin(PI * y).toFloat()
        if (fade <= 0.01f) return@forEach
        val flicker = 0.5f + 0.5f * sin(2.0 * PI * (time * ember.flicker + ember.phase)).toFloat()
        val alpha = 0.5f * fade * flicker
        val center = Offset(x * w, y * h)
        val glow = ember.radius * density * 4f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Palette.GoldBright.copy(alpha = alpha * 0.85f), Color.Transparent),
                center = center,
                radius = glow,
            ),
            radius = glow,
            center = center,
        )
        drawCircle(Palette.GoldBright.copy(alpha = alpha), radius = ember.radius * density * 0.8f, center = center)
    }
}
