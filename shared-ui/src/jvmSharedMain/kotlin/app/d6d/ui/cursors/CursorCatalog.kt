package app.d6d.ui.cursors

import androidx.compose.ui.graphics.ImageBitmap

/** Le due finiture disponibili per il guanto-cursore del desktop. */
enum class CursorPair {
    COLD,
    WARM,
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
 * la sezione Cursori dal Compendio condiviso.
 */
data class CursorPreferences(
    val selected: CursorPair,
    val previews: List<CursorPairPreview>,
    val onSelect: (CursorPair) -> Unit,
)
