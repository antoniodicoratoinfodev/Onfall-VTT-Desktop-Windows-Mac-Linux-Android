package app.d6d.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.healthColor
import app.d6d.ui.theme.shaded
import kotlinx.coroutines.delay

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
    height: Dp = 5.dp,
) {
    val safeMax = max.coerceAtLeast(1)
    val target = (current.toFloat() / safeMax).coerceIn(0f, 1f)
    val ratio by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "hitPoints",
    )
    // Scia del danno: una barra fantasma che insegue quella vera con ritardo,
    // cosi' il colpo appena subito resta visibile per un istante come segmento
    // ambrato che si ritira. Quando si guarisce sale insieme alla barra vera e
    // non si vede.
    val ghost = remember { Animatable(target) }
    LaunchedEffect(target) {
        if (target < ghost.value) {
            delay(280)
            ghost.animateTo(target, tween(durationMillis = 700, easing = FastOutSlowInEasing))
        } else {
            ghost.animateTo(target, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
    }
    val tempTarget = (temporary.toFloat() / safeMax).coerceIn(0f, 1f)
    val tempRatio by animateFloatAsState(
        targetValue = tempTarget,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "temporaryHitPoints",
    )
    val fill = healthColor(current, safeMax)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = "Punti ferita"
                stateDescription = "$current su $safeMax" +
                    if (temporary > 0) ", più $temporary temporanei" else ""
                progressBarRangeInfo = ProgressBarRangeInfo(ratio, 0f..1f)
            },
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)

        // Traccia incassata: quasi nera con un filo di bordo, come una scanalatura
        // nel legno del tavolo. Il riempimento e' un liquido: piu' chiaro in cima.
        drawRoundRect(color = Palette.Abyss, cornerRadius = radius)
        drawRoundRect(
            color = Palette.Line,
            cornerRadius = radius,
            style = Stroke(width = 1f),
        )

        // La scia del danno sta sotto il riempimento: spunta solo la parte che
        // eccede la barra vera, cioe' i PF appena persi.
        if (ghost.value > ratio) {
            drawRoundRect(
                color = Palette.Bloodied.copy(alpha = 0.55f),
                size = Size(size.width * ghost.value, size.height),
                cornerRadius = radius,
            )
        }

        if (ratio > 0f) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(fill, fill.shaded(0.28f))),
                size = Size(size.width * ratio, size.height),
                cornerRadius = radius,
            )
        }

        if (tempRatio > 0f) {
            val start = size.width * ratio
            val width = (size.width * tempRatio).coerceAtMost(size.width - start)
            if (width > 0f) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Palette.Temporary, Palette.Temporary.shaded(0.28f)),
                    ),
                    topLeft = Offset(start, 0f),
                    size = Size(width, size.height),
                    cornerRadius = radius,
                )
            }
        }
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
        modifier = modifier.semantics {
            stateDescription = buildString {
                append(if (actionAvailable) "Azione disponibile" else "Azione spesa")
                append(", ")
                append(if (bonusAvailable) "azione bonus disponibile" else "azione bonus spesa")
                append(", ")
                append(if (reactionAvailable) "reazione disponibile" else "reazione spesa")
            }
        },
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
    HoverLabel(label) {
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
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Rivela un'etichetta quando il puntatore sosta sull'elemento per un istante.
 * Serve ai segnalini A/B/R per dire per esteso "Azione", "Azione Bonus" e
 * "Reazione". Di fatto e' un aiuto da desktop — sul tocco il passaggio del
 * mouse non esiste — ma l'API e' condivisa e resta innocua su Android.
 */
@Composable
private fun HoverLabel(label: String, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var visible by remember { mutableStateOf(false) }
    val gapPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    val position = remember(gapPx) { TooltipAbove(gapPx) }

    // Breve sosta prima di mostrare il testo: passando veloci sui tre segnalini
    // le etichette non devono lampeggiare una dietro l'altra.
    LaunchedEffect(hovered) {
        visible = false
        if (hovered) {
            delay(350)
            visible = true
        }
    }

    Box(Modifier.hoverable(interaction)) {
        content()
        if (visible) {
            Popup(popupPositionProvider = position) {
                Text(
                    text = label,
                    color = Palette.Text,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(Palette.SurfaceHigh, RoundedCornerShape(6.dp))
                        .border(1.dp, Palette.Line, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Colloca il fumetto centrato sopra l'ancora; se in cima non entra, lo mette sotto. */
private class TooltipAbove(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val y = if (above >= 0) above else anchorBounds.bottom + gapPx
        return IntOffset(x, y)
    }
}
