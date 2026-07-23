package app.d6d.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import app.d6d.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * Vero quando una maniglia puo' reagire al trascinamento.
 *
 * Difesa da un problema noto di Compose Desktop: quando la finestra torna in primo
 * piano con il puntatore gia' sopra una maniglia, il sistema puo' consegnare un
 * trascinamento "fantasma" che nessuno ha compiuto. Ignorando ogni trascinamento
 * nei primi istanti dopo che la finestra ha ripreso il fuoco, quell'evento non
 * fa collassare il pannello, mentre i trascinamenti veri (piu' tardi) passano.
 */
@Composable
private fun rememberDragArmed(): Boolean {
    val focused = LocalWindowInfo.current.isWindowFocused
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        armed = false
        if (focused) {
            delay(400)
            armed = true
        }
    }
    return armed
}

/**
 * Cursore di ridimensionamento orizzontale dove la piattaforma lo espone
 * (desktop). Su Android e' un'operazione a vuoto: il tocco non ha cursore.
 */
expect fun Modifier.horizontalResizeCursor(): Modifier

/**
 * Cursore di ridimensionamento verticale dove la piattaforma lo espone (desktop).
 * Su Android e' un'operazione a vuoto: il tocco non ha cursore.
 */
expect fun Modifier.verticalResizeCursor(): Modifier

/**
 * Barra verticale sottile e trascinabile fra due pannelli affiancati.
 *
 * `onDrag` riceve lo spostamento orizzontale in pixel (positivo verso destra);
 * chi la usa lo converte in una nuova larghezza applicando i propri limiti. La
 * barra si illumina al passaggio del mouse e durante il trascinamento, e mostra
 * il cursore di ridimensionamento sul desktop.
 */
@Composable
fun VerticalResizeHandle(
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging
    // Letto in modo aggiornato: il gestore del trascinamento viene creato una sola
    // volta, quindi deve vedere il valore piu' recente, non quello di partenza.
    val armed by rememberUpdatedState(rememberDragArmed())

    Box(
        modifier
            .fillMaxHeight()
            .width(9.dp)
            .horizontalResizeCursor()
            .hoverable(interaction)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (armed) onDrag(dragAmount)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(if (active) 3.dp else 1.dp)
                .background(if (active) Palette.Gold else Palette.Line),
        )
    }
}

/**
 * Barra orizzontale sottile e trascinabile fra due pannelli impilati.
 *
 * La controparte verticale della barra fra le colonne: `onDrag` riceve lo
 * spostamento verticale in pixel (positivo verso il basso); chi la usa lo
 * converte in una nuova altezza applicando i propri limiti. Si illumina al
 * passaggio del mouse e durante il trascinamento, con il cursore nord-sud.
 */
@Composable
fun HorizontalResizeHandle(
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging
    val armed by rememberUpdatedState(rememberDragArmed())

    Box(
        modifier
            .fillMaxWidth()
            .height(9.dp)
            .verticalResizeCursor()
            .hoverable(interaction)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (armed) onDrag(dragAmount)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (active) 3.dp else 1.dp)
                .background(if (active) Palette.Gold else Palette.Line),
        )
    }
}
