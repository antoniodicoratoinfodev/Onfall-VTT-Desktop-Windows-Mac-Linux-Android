package app.d6d.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.healthColor

enum class Faction { PARTY, ENEMY }

val Faction.color: Color
    get() = if (this == Faction.PARTY) Palette.Party else Palette.Enemy

/**
 * Ritratto del combattente: medaglione disegnato a vettori, con anello dei PF.
 *
 * E' generato dal codice e non da immagini, sia per restare entro i vincoli di
 * licenza del paragrafo 17, sia perche' cosi' ogni creatura inserita dall'utente
 * ha subito una rappresentazione visiva senza dover fornire un'illustrazione.
 */
@Composable
fun CombatantPortrait(
    name: String,
    currentHitPoints: Int,
    maxHitPoints: Int,
    faction: Faction,
    active: Boolean = false,
    diameter: Dp = 54.dp,
    modifier: Modifier = Modifier,
) {
    val defeated = currentHitPoints <= 0
    val safeMax = maxHitPoints.coerceAtLeast(1)
    val target = (currentHitPoints.toFloat() / safeMax).coerceIn(0f, 1f)
    val ratio by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "portraitHitPoints",
    )

    // Pulsazione solo sul turno attivo: segnala di chi e' il turno senza
    // aggiungere testo, che a colpo d'occhio sarebbe piu' lento da leggere.
    val pulse = if (active && !defeated) {
        val animated by rememberInfiniteTransition(label = "activePulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "activePulseValue",
        )
        animated
    } else {
        0f
    }

    val accent = when {
        defeated -> Palette.TextFaint
        active -> Palette.Turn
        else -> faction.color
    }
    val ringColor = if (defeated) Palette.TextFaint else healthColor(currentHitPoints, safeMax)

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = size.minDimension * 0.075f
            val inset = stroke * 1.6f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            if (active && !defeated) {
                drawCircle(
                    color = accent.copy(alpha = 0.10f + 0.16f * pulse),
                    radius = size.minDimension / 2f,
                )
            }

            // Disco interno bombato: piu' chiaro verso l'alto, come metallo
            // battuto colpito dalla luce. Da' profondita' senza usare immagini.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Palette.SurfaceHigh, Palette.Surface),
                    center = Offset(center.x, center.y - size.minDimension * 0.12f),
                    radius = size.minDimension * 0.72f,
                ),
                radius = size.minDimension / 2f - inset,
            )

            // Traccia completa dell'anello.
            drawArc(
                color = Palette.Abyss,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Porzione corrispondente ai PF residui, in senso orario da in alto.
            if (ratio > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            drawCircle(
                color = accent.copy(alpha = if (active) 0.9f else 0.45f),
                radius = size.minDimension / 2f - inset,
                style = Stroke(width = 1.2f),
            )
        }

        Text(
            text = if (defeated) "✕" else initials(name),
            color = if (defeated) Palette.TextFaint else Palette.Text,
            fontWeight = FontWeight.Black,
            fontSize = (diameter.value * 0.30f).sp,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Iniziali usate nel medaglione: una parola sola da' una lettera, due o piu' ne danno due. */
internal fun initials(value: String): String {
    val words = value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}
