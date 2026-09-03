package app.d6d.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.ui.theme.OnfallTheme
import app.d6d.ui.theme.Palette

enum class FloatKind { DAMAGE, CRIT, HEAL, TEMPORARY, MISS, INFO }

/** Numero effimero mostrato sopra un combattente subito dopo un evento. */
data class FloatingNumber(
    val id: Long,
    val text: String,
    val kind: FloatKind,
)

private val FloatKind.color: Color
    get() = when (this) {
        FloatKind.DAMAGE -> Palette.Critical
        FloatKind.CRIT -> Palette.Crit
        FloatKind.HEAL -> Palette.Heal
        FloatKind.TEMPORARY -> Palette.Temporary
        FloatKind.MISS -> Palette.TextMuted
        FloatKind.INFO -> Palette.Gold
    }

/**
 * Un numero che sale e svanisce.
 *
 * Serve a rendere immediatamente percepibile l'esito di un'azione: la cifra
 * autorevole resta comunque quella della barra dei PF e del registro eventi.
 */
@Composable
fun FloatingNumberView(
    number: FloatingNumber,
    onExpired: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rise = remember(number.id) { Animatable(0f) }
    val fade = remember(number.id) { Animatable(1f) }
    val pop = remember(number.id) { Animatable(if (number.kind == FloatKind.CRIT) 0.45f else 0.75f) }

    LaunchedEffect(number.id) {
        pop.animateTo(1f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
        rise.animateTo(-46f, tween(durationMillis = 780, easing = FastOutSlowInEasing))
        fade.animateTo(0f, tween(durationMillis = 240))
        onExpired(number.id)
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = number.text,
            color = number.kind.color,
            fontSize = if (number.kind == FloatKind.CRIT) 27.sp else 20.sp,
            style = OnfallTheme.typography.numberLarge,
            modifier = Modifier
                .offset(y = rise.value.dp)
                .alpha(fade.value)
                .scale(pop.value),
        )
    }
}
