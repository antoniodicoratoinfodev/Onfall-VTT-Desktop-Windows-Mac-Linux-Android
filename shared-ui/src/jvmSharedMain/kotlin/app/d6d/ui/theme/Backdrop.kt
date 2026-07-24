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
 * La scena e' una cripta quasi buia rischiarata da un fuoco fuori campo: il fondo
 * tende al nero, ma una luce rossa, arancio e gialla pulsa sulla pietra incisa.
 * Scintille di dimensione e velocita' diverse salgono dalla sorgente.
 *
 * E' pensato per stare dietro i menu e i pannelli, che vi si dissolvono sopra: la
 * mappa tattica dipinge invece il proprio fondo opaco e resta pulita.
 *
 * ## Dithering
 * Anche i gradienti animati sono coperti a ogni fotogramma da un rumore fine a
 * copertura piena e ampiezza bassissima: spezza gli anelli di banding senza
 * leggersi come grana. E' distinto dalla trama piu' marcata usata sulla mappa.
 */
@Composable
fun AtmosphericBackground(
    modifier: Modifier = Modifier,
    embers: Boolean = true,
    vignette: Float = 0.52f,
) {
    // Strato fermo: base, pietra, crepe e vignettatura. Legge solo la dimensione,
    // quindi si ridisegna soltanto al ridimensionamento.
    Canvas(modifier.fillMaxSize()) {
        drawStoneField(vignette)
    }

    if (embers) {
        val transition = rememberInfiniteTransition(label = "backdrop")
        // Un tempo 0..1 in ciclo continuo. Luce e particelle usano solo armoniche
        // intere, quindi il passaggio fra fine e inizio del ciclo resta invisibile.
        val time by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "firelight",
        )
        Canvas(modifier.fillMaxSize()) {
            drawFirelight(time)
            // Il dithering deve stare sopra il bagliore animato, non soltanto sul
            // fondale statico, altrimenti gli anelli del gradiente tornano visibili.
            drawRect(backdropDither)
            drawFlameParticles(time)
        }
    } else {
        Canvas(modifier.fillMaxSize()) { drawRect(backdropDither) }
    }
}

private val FireYellow = Color(0xFFFFD36A)
private val FireOrange = Color(0xFFFF7417)
private val FireRed = Color(0xFFD62D16)
private val FireDeepRed = Color(0xFF650B08)

/** Centro medio del fuoco fuori campo, in frazioni della superficie. */
private const val FIRE_X = 0.5f
private const val FIRE_Y = 0.74f

/**
 * La cripta prima della luce: base scura, pietra a rilievo, crepe incise e
 * vignettatura forte. Disegnata una volta per dimensione.
 */
private fun DrawScope.drawStoneField(vignetteStrength: Float) {
    val w = size.width
    val h = size.height
    val minDim = size.minDimension
    val maxDim = size.maxDimension
    val glow = Offset(w * FIRE_X, h * FIRE_Y)

    // Base: notte in alto, abisso verso il basso e i bordi.
    drawRect(Brush.verticalGradient(listOf(Palette.Night, Palette.Abyss)))

    // Una brace residua resta anche quando l'animazione e' disattivata.
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to FireDeepRed.copy(alpha = 0.24f),
                0.26f to FireDeepRed.copy(alpha = 0.11f),
                0.58f to FireDeepRed.copy(alpha = 0.035f),
                1f to Color.Transparent,
            ),
            center = glow,
            radius = maxDim * 0.82f,
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
}

/**
 * Luce del fuoco fuori campo. Tre frequenze diverse evitano il respiro regolare
 * di una semplice sinusoide; centro, raggio e intensita' variano insieme, ma
 * tornano esattamente al punto iniziale alla chiusura del ciclo.
 */
private fun DrawScope.drawFirelight(time: Float) {
    val w = size.width
    val h = size.height
    val maxDim = size.maxDimension
    val minDim = size.minDimension
    val turn = (2.0 * PI * time).toFloat()
    val flicker = (
        0.72f +
            0.13f * sin(turn * 3f) +
            0.08f * sin(turn * 7f + 1.1f) +
            0.05f * sin(turn * 13f + 0.35f)
        ).coerceIn(0.48f, 0.98f)
    val sway = sin(turn * 2f + 0.4f)
    val lift = sin(turn * 5f + 1.7f)
    val source = Offset(
        x = w * (FIRE_X + sway * 0.018f),
        y = h * (FIRE_Y + lift * 0.012f),
    )

    // Alone rosso molto largo: colora la pietra senza trasformare il fondale in
    // una superficie arancione uniforme.
    val outerRadius = maxDim * (0.72f + flicker * 0.10f)
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to FireOrange.copy(alpha = 0.18f * flicker),
                0.18f to FireRed.copy(alpha = 0.16f * flicker),
                0.46f to FireDeepRed.copy(alpha = 0.11f * flicker),
                0.76f to FireDeepRed.copy(alpha = 0.035f * flicker),
                1f to Color.Transparent,
            ),
            center = source,
            radius = outerRadius,
        ),
    )

    // Nucleo caldo: piu' piccolo e rapido, e' quello che fa percepire la luce
    // variabile sulle superfici semitrasparenti dei pannelli.
    val coreRadius = maxDim * (0.24f + flicker * 0.10f)
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to FireYellow.copy(alpha = 0.27f * flicker),
                0.20f to FireOrange.copy(alpha = 0.22f * flicker),
                0.48f to FireRed.copy(alpha = 0.10f * flicker),
                1f to Color.Transparent,
            ),
            center = Offset(source.x, source.y + h * 0.035f),
            radius = coreRadius,
        ),
    )

    // Lingue morbide, appena visibili: suggeriscono la sorgente senza disegnare
    // una fiamma letterale dietro ai contenuti.
    repeat(4) { index ->
        val direction = if (index % 2 == 0) -1f else 1f
        val local = sin(turn * (4f + index) + index * 1.37f)
        val tongueWidth = minDim * (0.055f + index * 0.012f)
        val tongueHeight = minDim * (0.22f + index * 0.035f + local * 0.025f)
        val tongueCenterX = source.x +
            direction * minDim * (0.025f + index * 0.018f) +
            local * minDim * 0.018f
        val tongueTop = source.y - tongueHeight * (0.76f + flicker * 0.10f)
        drawOval(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.38f to FireYellow.copy(alpha = 0.025f * flicker),
                    0.70f to FireOrange.copy(alpha = 0.075f * flicker),
                    1f to FireRed.copy(alpha = 0.015f * flicker),
                ),
                startY = tongueTop,
                endY = tongueTop + tongueHeight,
            ),
            topLeft = Offset(tongueCenterX - tongueWidth / 2f, tongueTop),
            size = Size(tongueWidth, tongueHeight),
        )
    }
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
 * Una particella: punto di partenza, quota percorsa e velocita' di salita.
 * I cicli interi fanno chiudere il giro senza scatti.
 */
private class FlameParticle(
    val startX: Float,
    val phase: Float,
    val cycles: Int,
    val travel: Float,
    val radius: Float,
    val flicker: Int,
    val sway: Float,
    val heat: Int,
)

// Seme fisso: la traiettoria non cambia quando Compose ricrea il Canvas.
private val flameParticles: List<FlameParticle> by lazy {
    val random = Random(4_242)
    List(42) {
        // Due numeri mediati concentrano le scintille attorno al fuoco, lasciando
        // comunque alcune traiettorie abbastanza larghe da animare tutto il fondo.
        val centeredX = (random.nextFloat() + random.nextFloat()) * 0.5f
        FlameParticle(
            startX = 0.12f + centeredX * 0.76f,
            phase = random.nextFloat(),
            cycles = 1 + random.nextInt(3),
            travel = 0.52f + random.nextFloat() * 0.58f,
            radius = 0.65f + random.nextFloat() * 2.25f,
            flicker = 5 + random.nextInt(8),
            sway = (0.018f + random.nextFloat() * 0.055f) *
                if (random.nextBoolean()) 1f else -1f,
            heat = random.nextInt(3),
        )
    }
}

/** Parte frazionaria sempre positiva: tiene la particella nell'intervallo 0..1. */
private fun frac(value: Float): Float = ((value % 1f) + 1f) % 1f

private fun DrawScope.drawFlameParticles(time: Float) {
    val w = size.width
    val h = size.height
    flameParticles.forEach { particle ->
        val life = frac(time * particle.cycles + particle.phase)
        val y = 1.04f - life * particle.travel
        val drift = particle.sway *
            sin(2.0 * PI * (time * particle.cycles + particle.phase)).toFloat()
        val x = particle.startX + drift * (0.45f + life)
        // La scintilla nasce e muore gia' trasparente, quindi il riciclo non scatta.
        val fade = sin(PI * life).toFloat()
        if (fade <= 0.01f) return@forEach
        val flicker = 0.68f + 0.32f *
            sin(2.0 * PI * (time * particle.flicker + particle.phase)).toFloat()
        val alpha = 0.72f * fade * flicker
        val color = when (particle.heat) {
            0 -> FireYellow
            1 -> FireOrange
            else -> FireRed
        }
        val center = Offset(x * w, y * h)
        val radius = particle.radius * density
        val glow = radius * 5.5f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = alpha * 0.72f), Color.Transparent),
                center = center,
                radius = glow,
            ),
            radius = glow,
            center = center,
        )
        // Una breve coda verticale rende le particelle veloci simili a scintille
        // senza sfocare quelle piu' lente e piccole.
        drawLine(
            color = color.copy(alpha = alpha * 0.55f),
            start = center,
            end = Offset(center.x, center.y + radius * (1.6f + particle.cycles)),
            strokeWidth = radius * 0.75f,
            cap = StrokeCap.Round,
        )
        drawCircle(FireYellow.copy(alpha = alpha), radius = radius * 0.68f, center = center)
    }
}
