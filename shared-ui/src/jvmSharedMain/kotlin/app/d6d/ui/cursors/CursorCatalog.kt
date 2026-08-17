package app.d6d.ui.cursors

import app.d6d.ui.i18n.Strings

import androidx.compose.ui.graphics.ImageBitmap

/** Le finiture disponibili per il guanto-cursore del desktop. */
enum class CursorPair {
    COLD,
    WARM,
    CLASSIC,
    RUNIC,
    STEEL,
}

/**
 * Dimensione visiva del cursore dentro il canvas nativo del sistema.
 *
 * Il canvas non cambia: a scalare sono guanto e hotspot, così il punto attivo
 * resta sulla stessa parte del dito a qualunque dimensione.
 */
enum class CursorSize(val scale: Float) {
    SMALL(0.65f),
    MEDIUM(0.82f),
    ORIGINAL(1f),
}

fun CursorSize.label(strings: Strings): String = when (this) {
    CursorSize.SMALL -> strings.cursors.sizeSmallName
    CursorSize.MEDIUM -> strings.cursors.sizeMediumName
    CursorSize.ORIGINAL -> strings.cursors.sizeOriginalName
}

fun CursorSize.description(strings: Strings): String = when (this) {
    CursorSize.SMALL -> strings.cursors.sizeSmall
    CursorSize.MEDIUM -> strings.cursors.sizeMedium
    CursorSize.ORIGINAL -> strings.cursors.sizeLarge
}

/**
 * Le due pose di una coppia, decodificate dagli stessi PNG usati dal cursore AWT.
 *
 * Le immagini arrivano dalla shell desktop: Android non deve incorporare o
 * mostrare un'impostazione che non può applicare.
 */
data class CursorPairPreview(
    val pair: CursorPair,
    val pointer: ImageBitmap,
    val grab: ImageBitmap,
)

/**
 * Preferenza del cursore offerta dalla piattaforma.
 *
 * `AppRoot` la riceve solo sul desktop. Un valore nullo nasconde completamente
 * la sezione Cursori dalle Impostazioni condivise.
 */
data class CursorPreferences(
    val selected: CursorPair,
    val size: CursorSize,
    val previews: List<CursorPairPreview>,
    val onSelect: (CursorPair) -> Unit,
    val onSizeChange: (CursorSize) -> Unit,
)
