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
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
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
 * tende al nero, ma una luce rossa, arancio e gialla pulsa sulla pietra.
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

/**
 * Centro medio del fuoco, in frazioni della superficie. Sta appena sotto il bordo
 * inferiore: quello che si vede e' la luce che sale, non la sorgente. Tenendolo
 * dentro la tela il nucleo si leggeva come un faretto acceso in mezzo allo schermo.
 */
private const val FIRE_X = 0.5f
private const val FIRE_Y = 0.98f

/**
 * La cripta prima della luce: base scura, pietra a rilievo e vignettatura forte.
 * Disegnata una volta per dimensione.
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
 * Riempie tutta la tela con un gradiente schiacciato attorno a [pivot].
 *
 * Un `radialGradient` da solo e' un cerchio perfetto e si legge come un faretto.
 * Deformandolo — piu' largo che alto — la stessa luce sembra invece spandersi
 * radente sulla pietra. Il rettangolo deborda dalla tela perche' dopo lo
 * schiacciamento in verticale deve comunque coprirla tutta.
 */
private fun DrawScope.drawSpill(brush: Brush, pivot: Offset, scaleX: Float, scaleY: Float) {
    scale(scaleX, scaleY, pivot) {
        drawRect(brush, topLeft = Offset(-size.width, -size.height), size = size * 3f)
    }
}

/**
 * Luce del fuoco fuori campo.
 *
 * Frequenze diverse evitano il respiro regolare di una semplice sinusoide, e sono
 * tutte armoniche intere, quindi il ciclo si richiude senza scatto. Il nucleo e'
 * diviso in due lobi con sfarfallio sfasato: il punto piu' luminoso si sposta da
 * un lato all'altro invece di limitarsi a pulsare, che era quello che faceva
 * sembrare il fondale un'unica superficie che respira.
 */
private fun DrawScope.drawFirelight(time: Float) {
    val w = size.width
    val h = size.height
    val maxDim = size.maxDimension
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
    val outerRadius = maxDim * (0.78f + flicker * 0.10f)
    drawSpill(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to FireOrange.copy(alpha = 0.20f * flicker),
                0.18f to FireRed.copy(alpha = 0.17f * flicker),
                0.46f to FireDeepRed.copy(alpha = 0.11f * flicker),
                0.76f to FireDeepRed.copy(alpha = 0.035f * flicker),
                1f to Color.Transparent,
            ),
            center = source,
            radius = outerRadius,
        ),
        pivot = source,
        scaleX = 1.32f,
        scaleY = 0.80f,
    )

    // I due lobi del nucleo. Sono quelli che fanno percepire la luce variabile
    // sulle superfici semitrasparenti dei pannelli.
    repeat(2) { lobe ->
        val side = if (lobe == 0) -1f else 1f
        val phase = lobe * 2.3f
        val local = (
            0.68f +
                0.20f * sin(turn * 5f + phase) +
                0.12f * sin(turn * 11f + phase * 1.7f)
            ).coerceIn(0.34f, 1f)
        val lobeCenter = Offset(
            x = source.x + side * w * 0.05f * (0.6f + 0.4f * sin(turn * 3f + phase)),
            y = source.y,
        )
        drawSpill(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to FireYellow.copy(alpha = 0.17f * local),
                    0.20f to FireOrange.copy(alpha = 0.14f * local),
                    0.48f to FireRed.copy(alpha = 0.07f * local),
                    1f to Color.Transparent,
                ),
                center = lobeCenter,
                radius = maxDim * (0.26f + local * 0.10f),
            ),
            pivot = lobeCenter,
            scaleX = 1.22f,
            scaleY = 0.84f,
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
        // La brace scatta via dal fuoco e rallenta salendo, invece di scorrere a
        // velocita' costante. La derivata di questa curva va da 2 a 0 e serve
        // anche a dimensionare la scia.
        val rise = life * (2f - life)
        val speed = (2f - 2f * life) * particle.travel * particle.cycles
        val y = 1.04f - rise * particle.travel
        val drift = particle.sway *
            sin(2.0 * PI * (time * particle.cycles + particle.phase)).toFloat()
        val x = particle.startX + drift * (0.45f + rise)
        // La scintilla nasce e muore gia' trasparente, quindi il riciclo non scatta.
        val fade = sin(PI * life).toFloat()
        if (fade <= 0.01f) return@forEach
        val flicker = 0.68f + 0.32f *
            sin(2.0 * PI * (time * particle.flicker + particle.phase)).toFloat()
        val alpha = 0.72f * fade * flicker
        // La brace si raffredda mentre sale: giallo, arancio, rosso. Il tono di
        // partenza cambia da particella a particella, cosi' non si accendono tutte
        // insieme dello stesso colore.
        val cooling = (life + particle.heat * 0.18f).coerceIn(0f, 1f)
        val color = if (cooling < 0.5f) {
            lerp(FireYellow, FireOrange, cooling * 2f)
        } else {
            lerp(FireOrange, FireRed, (cooling - 0.5f) * 2f)
        }
        val center = Offset(x * w, y * h)
        // Anche il corpo si consuma: le braci vecchie sono piu' piccole.
        val radius = particle.radius * density * (0.55f + 0.45f * (1f - life))
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
        // La scia e' lunga quanto la velocita' del momento: le scintille appena
        // nate filano, quelle che si stanno fermando in alto non la hanno piu'.
        drawLine(
            color = color.copy(alpha = alpha * 0.55f),
            start = center,
            end = Offset(center.x, center.y + radius * (1.2f + speed * 0.9f)),
            strokeWidth = radius * 0.75f,
            cap = StrokeCap.Round,
        )
        // Cuore incandescente solo finche' la brace e' giovane.
        drawCircle(
            color = lerp(color, FireYellow, (1f - cooling) * 0.8f).copy(alpha = alpha),
            radius = radius * 0.68f,
            center = center,
        )
    }
}
