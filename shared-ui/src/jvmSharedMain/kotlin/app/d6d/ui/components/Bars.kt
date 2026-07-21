package app.d6d.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.healthColor

/**
 * Barra dei punti ferita.
 *
 * I PF temporanei sono disegnati come segmento distinto perche' il documento
 * chiede che restino concettualmente separati: assorbono il danno per primi,
 * non si sommano ai PF e non stabilizzano a 0 PF.
 */
@Composable
fun HealthBar(
    current: Int,
    max: Int,
    temporary: Int = 0,
    modifier: Modifier = Modifier,
    height: Dp = 9.dp,
) {
    val safeMax = max.coerceAtLeast(1)
    val target = (current.toFloat() / safeMax).coerceIn(0f, 1f)
    val ratio by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "hitPoints",
    )
    val tempTarget = (temporary.toFloat() / safeMax).coerceIn(0f, 1f)
    val tempRatio by animateFloatAsState(
        targetValue = tempTarget,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "temporaryHitPoints",
    )
    val fill = healthColor(current, safeMax)

    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)

        drawRoundRect(color = Palette.Abyss, cornerRadius = radius)

        if (ratio > 0f) {
            drawRoundRect(
                color = fill,
                size = Size(size.width * ratio, size.height),
                cornerRadius = radius,
            )
        }

        if (tempRatio > 0f) {
            val start = size.width * ratio
            val width = (size.width * tempRatio).coerceAtMost(size.width - start)
            if (width > 0f) {
                drawRoundRect(
                    color = Palette.Temporary,
                    topLeft = Offset(start, 0f),
                    size = Size(width, size.height),
                    cornerRadius = radius,
                )
            }
        }

        drawRoundRect(
            color = Palette.Line,
            cornerRadius = radius,
            style = Stroke(width = 1f),
        )
    }
}

/**
 * Indicatori del budget del turno.
 *
 * Azione, Azione Bonus e Reazione restano tre risorse separate: il documento
 * vieta esplicitamente di convertirle liberamente fra loro.
 */
@Composable
fun ResourcePips(
    actionAvailable: Boolean,
    bonusAvailable: Boolean,
    reactionAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pip("A", actionAvailable, Palette.Gold, "Azione")
        Pip("B", bonusAvailable, Palette.Party, "Azione Bonus")
        Pip("R", reactionAvailable, Palette.Heal, "Reazione")
    }
}

@Composable
private fun Pip(letter: String, available: Boolean, activeColor: Color, label: String) {
    val color = if (available) activeColor else Palette.TextFaint
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(7.dp)) {
            if (available) {
                drawCircle(color = color)
            } else {
                drawCircle(color = color, style = Stroke(width = 1.2f))
            }
        }
        Text(
            text = " $letter",
            color = color,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
    }
}
