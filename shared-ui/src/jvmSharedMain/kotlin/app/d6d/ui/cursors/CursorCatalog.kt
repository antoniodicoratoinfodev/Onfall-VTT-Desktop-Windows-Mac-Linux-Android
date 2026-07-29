package app.d6d.ui.cursors

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
enum class CursorSize(
    val scale: Float,
    val label: String,
    val description: String,
) {
    SMALL(0.65f, "Piccolo", "65% · più discreto"),
    MEDIUM(0.82f, "Medio", "82% · compatto"),
    ORIGINAL(1f, "Originale", "100% · massima presenza"),
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
    val size: CursorSize,
    val previews: List<CursorPairPreview>,
    val onSelect: (CursorPair) -> Unit,
    val onSizeChange: (CursorSize) -> Unit,
)
