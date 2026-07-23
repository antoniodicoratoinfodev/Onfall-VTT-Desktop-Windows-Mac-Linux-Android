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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fondale atmosferico dell'applicazione: "dark fantasy cupo".
 *
 * Nessuna immagine importata — vale lo stesso vincolo di licenza dei ritratti e
 * degli ornamenti — quindi anche lo sfondo e' interamente disegnato da codice.
 * L'estetica e' cupa e tesa: pietra scura appena venata, ombre profonde ai bordi
 * e pochissime braci che salgono lente, come i tizzoni di un fuoco quasi spento.
 *
 * E' pensato per stare dietro i menu e i pannelli, che vi si dissolvono sopra: la
 * mappa tattica dipinge invece il proprio fondo opaco e resta pulita.
 *
 * Il disegno e' diviso in due strati per non sprecare lavoro: la pietra e la
 * vignettatura non cambiano mai e vengono ridisegnate solo al ridimensionamento;
 * le braci hanno un Canvas separato, l'unico ridisegnato a ogni fotogramma.
 */
@Composable
fun AtmosphericBackground(
    modifier: Modifier = Modifier,
    embers: Boolean = true,
    vignette: Float = 0.62f,
) {
    // Strato fermo: legge solo la dimensione, quindi non si ridisegna coi fotogrammi.
    Canvas(modifier.fillMaxSize()) {
        drawStoneField(vignette)
    }

    if (embers) {
        val transition = rememberInfiniteTransition(label = "backdrop")
        // Un unico tempo 0..1 in ciclo continuo. Ogni brace percorre un numero
        // intero di altezze per ciclo, cosi' il salto di riavvio e' invisibile:
        // a fine giro ognuna e' esattamente dove era partita.
        val time by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 23_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "emberDrift",
        )
        Canvas(modifier.fillMaxSize()) {
            drawEmbers(time)
        }
    }
}

/**
 * Superficie di pietra scura: base, chiazze morbide, qualche graffio, vignettatura
 * forte e grana anti-banding. Tutto a semi disegnato una volta sola per dimensione.
 */
private fun DrawScope.drawStoneField(vignetteStrength: Float) {
    val w = size.width
    val h = size.height
    val minDim = size.minDimension
    val maxDim = size.maxDimension

    // Base: notte in alto, abisso verso il basso e i bordi.
    drawRect(Brush.verticalGradient(listOf(Palette.Night, Palette.Abyss)))
    // Un tepore lontano, quasi spento, verso l'alto: rompe il nero piatto senza
    // schiarire davvero la scena.
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to Palette.Surface.copy(alpha = 0.34f),
                0.55f to Color.Transparent,
            ),
            center = Offset(w * 0.5f, h * 0.30f),
            radius = maxDim * 0.75f,
        ),
    )

    // Seme fisso: la pietra e' sempre la stessa, non danza fra un disegno e l'altro.
    val random = Random(90_210)

    // Chiazze di pietra: cerchi morbidi, in maggioranza scuri, con rare venature di
    // bronzo che fanno da minerale. Tenute a bassa opacita', danno rilievo e
    // nuvolosita' di roccia senza diventare rumore.
    repeat(150) {
        val c = Offset(random.nextFloat() * w, random.nextFloat() * h)
        val radius = (0.05f + 0.12f * random.nextFloat()) * minDim
        val dark = random.nextFloat() > 0.28f
        val color = if (dark) Color.Black else Palette.Bronze
        val alpha = if (dark) 0.075f else 0.026f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = alpha), Color.Transparent),
                center = c,
                radius = radius,
            ),
            radius = radius,
            center = c,
        )
    }

    // Graffi e crepe: linee sottili e spezzate che scendono, come incisioni nella
    // pietra. Una passata scura per l'incisione, una di bronzo appena accennata di
    // fianco che simula il bordo scheggiato in luce.
    repeat(9) {
        val path = Path()
        var x = random.nextFloat() * w
        var y = random.nextFloat() * h * 0.3f
        path.moveTo(x, y)
        val segments = 7 + random.nextInt(6)
        repeat(segments) {
            x += (random.nextFloat() - 0.5f) * w * 0.05f
            y += (h / segments) * (0.5f + 0.7f * random.nextFloat())
            path.lineTo(x, y)
        }
        drawPath(path, Color.Black.copy(alpha = 0.14f), style = Stroke(width = 1f, cap = StrokeCap.Round))
        drawPath(path, Palette.Bronze.copy(alpha = 0.05f), style = Stroke(width = 2f, cap = StrokeCap.Round))
    }

    // Vignettatura forte: i bordi cadono in ombra profonda, la luce resta al centro.
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0.42f to Color.Transparent,
                0.78f to Color.Black.copy(alpha = vignetteStrength * 0.5f),
                1f to Color.Black.copy(alpha = vignetteStrength),
            ),
            center = center,
            radius = maxDim * 0.78f,
        ),
    )
    // Grana condivisa: dithering che spezza gli anelli di quantizzazione sui neri.
    drawRect(grainBrush)
}

/**
 * Una brace: colonna e altezza iniziale in frazioni (0..1), velocita' di salita in
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

// Poche braci, come chiesto: rare scintille di un fuoco morente. Seme fisso.
private val embers: List<Ember> by lazy {
    val random = Random(4_242)
    List(9) {
        Ember(
            x = random.nextFloat(),
            startY = random.nextFloat(),
            rise = if (random.nextFloat() > 0.7f) 2 else 1,
            radius = 1.1f + random.nextFloat() * 1.5f,
            phase = random.nextFloat(),
            flicker = 2 + random.nextInt(3),
            drift = (random.nextFloat() - 0.5f) * 0.05f,
        )
    }
}

/** Parte frazionaria sempre positiva: tiene la brace nell'intervallo 0..1. */
private fun frac(value: Float): Float = ((value % 1f) + 1f) % 1f

private fun DrawScope.drawEmbers(time: Float) {
    val w = size.width
    val h = size.height
    embers.forEach { ember ->
        // Sale: y cala nel tempo. Il moltiplicatore intero fa combaciare inizio e
        // fine del ciclo. La deriva orizzontale oscilla con periodo unitario.
        val y = frac(ember.startY - time * ember.rise)
        val x = frac(ember.x + ember.drift * sin(2.0 * PI * (time + ember.phase)).toFloat())
        // Dissolvenza ai bordi: la brace nasce fioca in basso e si spegne in alto,
        // percio' il momento del riavvolgimento (y ~ 0 o 1) e' gia' trasparente.
        val fade = sin(PI * y).toFloat()
        if (fade <= 0.01f) return@forEach
        val flicker = 0.55f + 0.45f * sin(2.0 * PI * (time * ember.flicker + ember.phase)).toFloat()
        val alpha = 0.30f * fade * flicker
        val center = Offset(x * w, y * h)
        val glow = ember.radius * density * 3.2f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Palette.GoldBright.copy(alpha = alpha * 0.8f), Color.Transparent),
                center = center,
                radius = glow,
            ),
            radius = glow,
            center = center,
        )
        drawCircle(Palette.Gold.copy(alpha = alpha), radius = ember.radius * density * 0.7f, center = center)
    }
}
