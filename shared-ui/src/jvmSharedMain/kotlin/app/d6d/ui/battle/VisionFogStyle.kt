package app.d6d.ui.battle

import androidx.compose.ui.graphics.Color
import app.d6d.ui.board.VisionPresentation
import app.d6d.ui.theme.Palette

/** Colori finali dei due livelli coperti della vista dinamica. */
internal data class VisionFogColors(val explored: Color, val unseen: Color)

internal fun VisionPresentation.fogColors(): VisionFogColors? = when (this) {
    VisionPresentation.ALL -> null
    VisionPresentation.MEMORY_BLACK -> VisionFogColors(
        explored = Palette.Abyss.copy(alpha = 0.78f),
        unseen = Color.Black,
    )
    VisionPresentation.MEMORY_DIM -> VisionFogColors(
        explored = Palette.Abyss.copy(alpha = 0.32f),
        unseen = Palette.Abyss.copy(alpha = 0.62f),
    )
}
