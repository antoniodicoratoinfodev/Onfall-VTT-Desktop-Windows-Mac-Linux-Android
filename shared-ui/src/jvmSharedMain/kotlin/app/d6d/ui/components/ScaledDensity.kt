package app.d6d.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Ingrandisce o rimpicciolisce in blocco il contenuto sovrascrivendo la densita'.
 *
 * Con una densita' piu' alta ogni `dp` e ogni `sp` valgono piu' pixel, quindi
 * testo, spaziature e riquadri crescono insieme e — a differenza di una semplice
 * trasformazione grafica — anche il layout ne tiene conto e occupa piu' spazio.
 *
 * Serve a far reagire davvero il contenuto di una fascia alla sua altezza: piu' la
 * fascia e' alta, maggiore lo `scale`, piu' i riquadri e i pulsanti diventano
 * grandi, riempiendola invece di lasciarla vuota.
 */
@Composable
fun ScaledDensity(scale: Float, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale, base.fontScale),
        content = content,
    )
}
