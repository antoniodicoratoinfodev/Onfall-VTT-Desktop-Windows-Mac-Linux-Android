package app.d6d.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.d6d.ui.theme.Palette

/**
 * Cursore di ridimensionamento orizzontale dove la piattaforma lo espone
 * (desktop). Su Android e' un'operazione a vuoto: il tocco non ha cursore.
 */
expect fun Modifier.horizontalResizeCursor(): Modifier

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
                        onDrag(dragAmount)
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
