package app.d6d.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity

/** Un PNG del cursore e il suo punto attivo, entrambi nello spazio dell'immagine. */
internal data class DrawnCursor(
    val image: ImageBitmap,
    val hotspot: Offset,
    val scale: Float,
)

/** Posizione del mouse nella scena; nulla quando il puntatore non e' nella finestra. */
@Stable
internal class PointerTrail {
    internal var position by mutableStateOf<Offset?>(null)
}

/**
 * Osserva il mouse nella fase finale senza consumare gli eventi: mappa, pannelli e
 * gesti continuano a ricevere esattamente lo stesso input.
 */
internal fun Modifier.followPointer(trail: PointerTrail): Modifier = pointerInput(trail) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse } ?: continue
            trail.position = if (event.type == PointerEventType.Exit) null else mouse.position
        }
    }
}

/**
 * Posiziona il disegno affinche' l'hotspot cada sulla coordinata consegnata da
 * Compose. Dimensione e hotspot seguono la stessa scala anche ad alta densita'.
 */
internal fun drawnCursorBounds(
    pointerPosition: Offset,
    imageSize: Size,
    hotspot: Offset,
    scale: Float,
    density: Float,
): Rect {
    require(imageSize.width > 0f && imageSize.height > 0f)
    require(density > 0f)
    val renderScale = scale.coerceIn(0.5f, 1f) * density
    val topLeft = pointerPosition - hotspot * renderScale
    return Rect(topLeft, imageSize * renderScale)
}

/**
 * Disegna il PNG sopra la scena senza partecipare all'hit test. La posizione viene
 * letta nel disegno, quindi un movimento invalida questo Canvas e non l'intera UI.
 */
@Composable
internal fun DrawnCursorOverlay(
    cursor: DrawnCursor,
    trail: PointerTrail,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    Canvas(modifier) {
        val pointerPosition = trail.position ?: return@Canvas
        val imageSize = Size(cursor.image.width.toFloat(), cursor.image.height.toFloat())
        val bounds = drawnCursorBounds(
            pointerPosition = pointerPosition,
            imageSize = imageSize,
            hotspot = cursor.hotspot,
            scale = cursor.scale,
            density = density,
        )
        val scaleX = bounds.width / imageSize.width
        val scaleY = bounds.height / imageSize.height
        withTransform({
            translate(bounds.left, bounds.top)
            scale(scaleX, scaleY, pivot = Offset.Zero)
        }) {
            drawImage(cursor.image)
        }
    }
}
