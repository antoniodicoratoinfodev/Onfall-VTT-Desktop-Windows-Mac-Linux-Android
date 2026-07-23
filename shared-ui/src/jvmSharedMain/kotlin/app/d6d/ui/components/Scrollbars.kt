package app.d6d.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Barra di scorrimento sottile per gli elenchi (registro, colonne, Compendio).
 *
 * Sul desktop e' visibile: bronzo discreto che si accende d'oro al passaggio del
 * mouse, cosi' si vede quanta cronaca c'e' sotto. Su Android non disegna nulla:
 * il gesto di scorrimento basta e la convenzione della piattaforma e' non
 * mostrare barre persistenti.
 */
@Composable
expect fun PanelScrollbar(state: LazyListState, modifier: Modifier = Modifier)
