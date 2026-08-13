package app.d6d.ui.maps

/**
 * Misure ammesse per la griglia, condivise fra chi la crea e chi la corregge.
 *
 * La procedura Nuova partita e i comandi mappa della battaglia devono accordarsi
 * sul minimo: non ha senso che il primo rifiuti una griglia sotto le cinque
 * caselle e il secondo permetta di scendere fino a una, cancellando per strada i
 * segnaposti rimasti fuori bordo. Il massimo e' invece diverso di proposito — la
 * procedura guidata ferma le manopole molto prima del limite del motore, che
 * resta [app.d6d.domain.space.MapGrid.MAX_SIDE] per chi allarga una mappa gia'
 * aperta.
 */
object GridLimits {

    /** Sotto questa misura la griglia non ospita due schieramenti contrapposti. */
    const val MIN_SIDE = 5

    /** Lato massimo proposto dalle manopole della procedura Nuova partita. */
    const val MAX_BUILDER_SIDE = 100
}
