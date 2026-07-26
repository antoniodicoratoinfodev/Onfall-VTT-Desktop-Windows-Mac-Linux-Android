package app.d6d.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val BURST_DURATION_SECONDS = 0.72f
private const val MAX_BURSTS = 4

private val EmberYellow = Color(0xFFFFD56A)
private val EmberOrange = Color(0xFFFF7A1A)
private val EmberRed = Color(0xFFD93A18)

internal data class ClickFlameParticle(
    val delaySeconds: Float,
    val lifeSeconds: Float,
    val velocityX: Float,
    val velocityY: Float,
    val radiusDp: Float,
    val swayDp: Float,
    val phase: Float,
    val heat: Float,
)

internal data class ClickFlameBurst(
    val origin: Offset,
    val startedAtNanos: Long,
    val particles: List<ClickFlameParticle>,
)

@Stable
internal class ClickFlameState {
    internal val bursts = mutableStateListOf<ClickFlameBurst>()
    private var sequence = 0

    internal fun emit(origin: Offset) {
        val random = Random(0x6F_6E_46_61 + sequence++)
        val particles = List(8) { index ->
            ClickFlameParticle(
                delaySeconds = if (index == 0) 0f else {
                    index * 0.01f + random.nextFloat() * 0.025f
                },
                lifeSeconds = 0.38f + random.nextFloat() * 0.24f,
                velocityX = -18f + random.nextFloat() * 36f,
                velocityY = 34f + random.nextFloat() * 34f,
                radiusDp = 0.85f + random.nextFloat() * 1.05f,
                swayDp = 1.2f + random.nextFloat() * 2.8f,
                phase = random.nextFloat(),
                heat = random.nextFloat(),
            )
        }
        bursts += ClickFlameBurst(origin, System.nanoTime(), particles)
        while (bursts.size > MAX_BURSTS) bursts.removeAt(0)
    }
}

@Composable
internal fun rememberClickFlameState(): ClickFlameState = remember { ClickFlameState() }

/**
 * Osserva la pressione nella fase finale senza consumarla. Il fuoco nasce sul
 * mouse-down, non al rilascio, così la risposta visiva è immediata.
 */
internal fun Modifier.clickFlameBursts(state: ClickFlameState): Modifier =
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.type == PointerEventType.Press) {
                    event.changes.firstOrNull()?.position?.let(state::emit)
                }
            }
        }
    }

/**
 * Piccolo fuoco di conferma al clic: un bagliore breve e otto braci che salgono.
 * Il Canvas non riceve input e resta visivamente sotto al cursore di sistema.
 */
@Composable
internal fun ClickFlameOverlay(
    state: ClickFlameState,
    modifier: Modifier = Modifier,
) {
    var nowNanos by remember { mutableStateOf(System.nanoTime()) }

    LaunchedEffect(state.bursts.size) {
        while (state.bursts.isNotEmpty()) {
            withFrameNanos { nowNanos = System.nanoTime() }
            val oldest = state.bursts.firstOrNull() ?: continue
            val elapsed = (nowNanos - oldest.startedAtNanos) / 1_000_000_000f
            if (elapsed >= BURST_DURATION_SECONDS) state.bursts.removeAt(0)
        }
    }

    Canvas(modifier) {
        state.bursts.forEach { burst ->
            val elapsed = (nowNanos - burst.startedAtNanos) / 1_000_000_000f
            if (elapsed !in 0f..BURST_DURATION_SECONDS) return@forEach

            val burstLife = (elapsed / BURST_DURATION_SECONDS).coerceIn(0f, 1f)
            val glowRadius = (7f + 11f * burstLife) * density
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        EmberOrange.copy(alpha = 0.16f * (1f - burstLife)),
                        EmberRed.copy(alpha = 0.07f * (1f - burstLife)),
                        Color.Transparent,
                    ),
                    center = burst.origin,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = burst.origin,
            )

            burst.particles.forEach { particle ->
                val ageSeconds = elapsed - particle.delaySeconds
                if (ageSeconds <= 0f || ageSeconds >= particle.lifeSeconds) return@forEach

                val life = ageSeconds / particle.lifeSeconds
                val fade = sin(PI * life).toFloat() * (1f - life * 0.35f)
                val cooling = (life * 0.78f + particle.heat * 0.22f).coerceIn(0f, 1f)
                val color = if (cooling < 0.5f) {
                    lerp(EmberYellow, EmberOrange, cooling * 2f)
                } else {
                    lerp(EmberOrange, EmberRed, (cooling - 0.5f) * 2f)
                }
                val drift = particle.swayDp * density *
                    sin((particle.phase + life) * 2f * PI).toFloat()
                val center = Offset(
                    x = burst.origin.x + particle.velocityX * density * ageSeconds + drift,
                    y = burst.origin.y -
                        particle.velocityY * density * ageSeconds +
                        12f * density * ageSeconds * ageSeconds,
                )
                val radius = particle.radiusDp * density * (1f - life * 0.38f)
                val glow = radius * 4.2f

                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = fade * 0.38f), Color.Transparent),
                        center = center,
                        radius = glow,
                    ),
                    radius = glow,
                    center = center,
                )
                drawLine(
                    color = color.copy(alpha = fade * 0.48f),
                    start = center,
                    end = Offset(center.x, center.y + radius * 2.1f),
                    strokeWidth = radius * 0.55f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = lerp(color, EmberYellow, 0.45f).copy(alpha = fade * 0.82f),
                    radius = radius * 0.62f,
                    center = center,
                )
            }
        }
    }
}
