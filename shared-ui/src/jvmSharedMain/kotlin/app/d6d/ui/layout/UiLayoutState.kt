package app.d6d.ui.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Disposizione dell'interfaccia viva, condivisa da tutta la shell.
 *
 * Ogni pannello legge e scrive qui la propria larghezza, altezza o stato di
 * collasso invece di tenerli in un `remember` locale che sparisce alla chiusura.
 * Il valore parte da quello caricato dal disco e vi torna, con un breve ritardo,
 * a ogni modifica: cosi' riaprendo l'app i pannelli sono dove li avevi lasciati.
 *
 * Le posizioni delle targhe sono frazioni dello spazio libero (0..1), non pixel,
 * perche' devono restare valide anche su uno schermo di dimensioni diverse.
 */
class UiLayoutState(
    initial: UiLayout = UiLayout(),
    private val store: LayoutStore? = null,
) {
    var railWidth by mutableStateOf(initial.railWidthDp.dp)
    var railOpen by mutableStateOf(initial.railOpen)
    var squadWidth by mutableStateOf(initial.squadWidthDp.dp)
    var enemyWidth by mutableStateOf(initial.enemyWidthDp.dp)
    var logHeight by mutableStateOf(initial.logHeightDp.dp)
    var logCollapsed by mutableStateOf(initial.logCollapsed)
    var turnsCollapsed by mutableStateOf(initial.turnsCollapsed)
    var mapCellSize by mutableStateOf(initial.mapCellSizeDp.dp)
    var mapShowGrid by mutableStateOf(initial.mapShowGrid)
    var targetPlate by mutableStateOf(initial.targetPlate?.toOffset())
    var activePlate by mutableStateOf(initial.activePlate?.toOffset())

    private var lastSaved = initial.sanitized()

    /** Fotografia serializzabile dello stato attuale, gia' entro i limiti validi. */
    fun snapshot(): UiLayout = UiLayout(
        railWidthDp = railWidth.value,
        railOpen = railOpen,
        squadWidthDp = squadWidth.value,
        enemyWidthDp = enemyWidth.value,
        logHeightDp = logHeight.value,
        logCollapsed = logCollapsed,
        turnsCollapsed = turnsCollapsed,
        mapCellSizeDp = mapCellSize.value,
        mapShowGrid = mapShowGrid,
        targetPlate = targetPlate?.toFraction(),
        activePlate = activePlate?.toFraction(),
    ).sanitized()

    /** Scrive su disco solo se qualcosa e' davvero cambiato dall'ultimo salvataggio. */
    fun persist() {
        val current = snapshot()
        if (current == lastSaved) return
        store?.save(current)
        lastSaved = current
    }

    private fun PlateFraction.toOffset() = Offset(x, y)

    private fun Offset.toFraction() = PlateFraction(x, y)
}

/**
 * Disposizione ambientale della shell.
 *
 * E' `static` perche' l'istanza non cambia mai dopo l'avvio: sono i singoli stati
 * al suo interno a muoversi, e ognuno ridisegna solo chi lo legge. Il valore
 * predefinito non persiste nulla, cosi' anteprime e test funzionano senza disco.
 */
val LocalUiLayout = staticCompositionLocalOf { UiLayoutState() }
