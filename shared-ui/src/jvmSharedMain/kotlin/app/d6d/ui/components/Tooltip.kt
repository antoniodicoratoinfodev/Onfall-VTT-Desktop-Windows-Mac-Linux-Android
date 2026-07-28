package app.d6d.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Colloca il fumetto centrato sopra l'ancora; se in cima non entra, lo mette sotto.
 *
 * Il fumetto non deve mai finire *sopra* l'elemento che lo ha evocato: coprirlo
 * significa rubargli il passaggio del mouse, e il fumetto si spegnerebbe e
 * riaccenderebbe a ripetizione. Per questo la posizione parte dai limiti misurati
 * dell'ancora invece che da uno scostamento fisso, e lo stacco e' in dp — in
 * pixel grezzi sarebbe meta' dello stacco voluto sugli schermi a densita' doppia.
 */
internal class TooltipBesideAnchor(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val below = anchorBounds.bottom + gapPx
        // Sotto e' la scelta di ripiego: si accorcia solo fino al bordo dell'ancora,
        // cosi' un fumetto piu' alto della finestra non torna a coprirla.
        val y = if (above >= 0) {
            above
        } else {
            below.coerceAtMost(
                (windowSize.height - popupContentSize.height).coerceAtLeast(below.coerceAtMost(anchorBounds.bottom)),
            )
        }
        return IntOffset(x, y)
    }
}

/** Posizione stabile per un fumetto d'aiuto, con lo stacco espresso in dp. */
@Composable
internal fun rememberTooltipPosition(gap: Dp = 4.dp): PopupPositionProvider {
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }
    return remember(gapPx) { TooltipBesideAnchor(gapPx) }
}
