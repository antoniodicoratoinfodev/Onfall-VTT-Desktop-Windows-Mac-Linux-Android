package app.d6d.ui.images

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.d6d.sheet.PortraitFraming
import app.d6d.ui.battle.GameButton
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.Palette
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Rettangolo dell'immagine gia' scalata, nello spazio del riquadro del token. */
internal data class FramedImagePlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** Calcolo puro condiviso da resa e gesto, tenuto separato per poterlo verificare. */
internal fun framedImagePlacement(
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
    framing: PortraitFraming,
): FramedImagePlacement {
    if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0f || viewportHeight <= 0f) {
        return FramedImagePlacement(0f, 0f, 0f, 0f)
    }
    val safe = framing.normalized()
    val coverScale = max(viewportWidth / imageWidth, viewportHeight / imageHeight)
    val scale = coverScale * safe.zoom
    val width = imageWidth * scale
    val height = imageHeight * scale
    return FramedImagePlacement(
        left = -(width - viewportWidth).coerceAtLeast(0f) * safe.focusX,
        top = -(height - viewportHeight).coerceAtLeast(0f) * safe.focusY,
        width = width,
        height = height,
    )
}

/** Disegna il file intero secondo l'inquadratura, senza creare copie ritagliate. */
private fun DrawScope.drawFramedPortrait(image: ImageBitmap, framing: PortraitFraming) {
    val placement = framedImagePlacement(image.width, image.height, size.width, size.height, framing)
    if (placement.width <= 0f || placement.height <= 0f) return
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(floor(placement.left).toInt(), floor(placement.top).toInt()),
        dstSize = IntSize(ceil(placement.width).toInt(), ceil(placement.height).toInt()),
        filterQuality = FilterQuality.High,
    )
}

/** Immagine pronta per un riquadro di qualunque misura; il contenitore decide la forma. */
@Composable
fun FramedPortraitImage(
    image: ImageBitmap,
    framing: PortraitFraming,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val semantics = if (description == null) Modifier else Modifier.semantics {
        contentDescription = description
    }
    Canvas(modifier.then(semantics)) { drawFramedPortrait(image, framing) }
}

/**
 * Editor touch/mouse dell'inquadratura. Supporta trascinamento e pinch; lo slider
 * rende lo zoom preciso e accessibile anche senza gesto multitouch.
 */
@Composable
fun PortraitFramingEditor(
    image: ImageBitmap,
    framing: PortraitFraming,
    onFramingChange: (PortraitFraming) -> Unit,
    modifier: Modifier = Modifier,
    previewSize: Dp = 176.dp,
) {
    val words = strings.maps
    val currentOnChange by rememberUpdatedState(onFramingChange)
    // Anche se due eventi del puntatore arrivano prima della ricomposizione, il
    // secondo deve partire dall'esito del primo invece di perdere parte del drag.
    val liveFraming = remember(image) { mutableStateOf(framing.normalized()) }
    SideEffect { liveFraming.value = framing.normalized() }

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(previewSize)
                .clip(CircleShape)
                .background(Palette.SurfaceHigh)
                .border(2.dp, Palette.Gold, CircleShape)
                .pointerInput(image) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        val current = liveFraming.value
                        val nextZoom = (current.zoom * zoomChange)
                            .coerceIn(PortraitFraming.MIN_ZOOM, PortraitFraming.MAX_ZOOM)
                        val placement = framedImagePlacement(
                            image.width,
                            image.height,
                            size.width.toFloat(),
                            size.height.toFloat(),
                            current.copy(zoom = nextZoom),
                        )
                        val overflowX = (placement.width - size.width).coerceAtLeast(0f)
                        val overflowY = (placement.height - size.height).coerceAtLeast(0f)
                        val updated = PortraitFraming(
                            focusX = if (overflowX > 0f) {
                                (current.focusX - pan.x / overflowX).coerceIn(0f, 1f)
                            } else {
                                0.5f
                            },
                            focusY = if (overflowY > 0f) {
                                (current.focusY - pan.y / overflowY).coerceIn(0f, 1f)
                            } else {
                                0.5f
                            },
                            zoom = nextZoom,
                        )
                        liveFraming.value = updated
                        currentOnChange(updated)
                    }
                },
        ) {
            FramedPortraitImage(image, framing, Modifier.fillMaxSize())
        }

        Text(
            words.frameImageHint,
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(words.imageZoom, color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = framing.normalized().zoom,
                onValueChange = { onFramingChange(framing.normalized().copy(zoom = it)) },
                valueRange = PortraitFraming.MIN_ZOOM..PortraitFraming.MAX_ZOOM,
                colors = SliderDefaults.colors(
                    thumbColor = Palette.Gold,
                    activeTrackColor = Palette.Gold,
                    inactiveTrackColor = Palette.Line,
                ),
                modifier = Modifier.width((previewSize - 58.dp).coerceAtLeast(80.dp)),
            )
        }
        GameButton(
            label = strings.common.reset,
            accent = Palette.TextFaint,
            dense = true,
            onClick = { onFramingChange(PortraitFraming.DEFAULT) },
        )
    }
}
