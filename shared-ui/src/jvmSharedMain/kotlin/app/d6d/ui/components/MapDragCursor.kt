package app.d6d.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Comunica alla shell desktop che la mappa e' stata afferrata.
 *
 * Su Android il valore predefinito non fa nulla: il tocco non ha cursore. La
 * shell desktop lo sostituisce con il cambio fra guanto puntatore e guanto chiuso.
 */
val LocalMapDragCursor = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
