package app.d6d.ui.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Le tre visualizzazioni attraversate cliccando l'etichetta dell'ordine. */
enum class TurnOrderDisplayMode {
    HIDDEN,
    ORDER_ONLY,
    WITH_INITIATIVE,
}

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
    var turnsShowInitiative by mutableStateOf(initial.turnsShowInitiative)
    var topBarHeight by mutableStateOf(initial.topBarHeightDp.dp)
    var commandBarHeight by mutableStateOf(initial.commandBarHeightDp.dp)
    var commandsCollapsed by mutableStateOf(initial.commandsCollapsed)
    var mapCellSize by mutableStateOf(initial.mapCellSizeDp.dp)
    var mapShowGrid by mutableStateOf(initial.mapShowGrid)
    var mapGridBrightness by mutableStateOf(initial.mapGridBrightness)
    var targetPlate by mutableStateOf(initial.targetPlate?.toOffset())
    var activePlate by mutableStateOf(initial.activePlate?.toOffset())
    var targetPlateScale by mutableStateOf(initial.targetPlateScale)
    var activePlateScale by mutableStateOf(initial.activePlateScale)

    val turnOrderDisplayMode: TurnOrderDisplayMode
        get() = when {
            turnsCollapsed -> TurnOrderDisplayMode.HIDDEN
            turnsShowInitiative -> TurnOrderDisplayMode.WITH_INITIATIVE
            else -> TurnOrderDisplayMode.ORDER_ONLY
        }

    /**
     * Attraversa le tre modalita' nell'ordine mostrato all'utente:
     * nascosto -> solo nomi -> nomi e iniziativa -> nascosto.
     */
    fun cycleTurnOrderDisplayMode() {
        when (turnOrderDisplayMode) {
            TurnOrderDisplayMode.HIDDEN -> {
                turnsCollapsed = false
                turnsShowInitiative = false
            }
            TurnOrderDisplayMode.ORDER_ONLY -> turnsShowInitiative = true
            TurnOrderDisplayMode.WITH_INITIATIVE -> turnsCollapsed = true
        }
    }

    /**
     * Porta direttamente a una delle tre modalita'.
     *
     * La usa chi le presenta tutte insieme — le Impostazioni — invece di farle
     * attraversare a giri come l'etichetta sulla fascia dei turni.
     */
    fun applyTurnOrderDisplayMode(mode: TurnOrderDisplayMode) {
        turnsCollapsed = mode == TurnOrderDisplayMode.HIDDEN
        // Fuori dalla modalita' nascosta il flag distingue le altre due; dentro,
        // conserva quale delle due si ritrovera' riaprendo la fascia.
        if (mode != TurnOrderDisplayMode.HIDDEN) {
            turnsShowInitiative = mode == TurnOrderDisplayMode.WITH_INITIATIVE
        }
    }

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
        turnsShowInitiative = turnsShowInitiative,
        topBarHeightDp = topBarHeight.value,
        commandBarHeightDp = commandBarHeight.value,
        commandsCollapsed = commandsCollapsed,
        mapCellSizeDp = mapCellSize.value,
        mapShowGrid = mapShowGrid,
        mapGridBrightness = mapGridBrightness,
        targetPlate = targetPlate?.toFraction(),
        activePlate = activePlate?.toFraction(),
        targetPlateScale = targetPlateScale,
        activePlateScale = activePlateScale,
    ).sanitized()

    /** Scrive su disco solo se qualcosa e' davvero cambiato dall'ultimo salvataggio. */
    fun persist() {
        val current = snapshot()
        if (current == lastSaved) return
        store?.save(current)
        lastSaved = current
    }

    /**
     * Riporta ogni pannello alla misura di fabbrica.
     *
     * Non passa dal disco: cambia gli stati vivi, e il salvataggio con ritardo che
     * gia' osserva [snapshot] scrive il risultato come per qualsiasi altra
     * modifica. Cosi' un ripristino si puo' ancora annullare a mano, riaggiustando
     * i pannelli, prima che il file venga toccato.
     */
    fun resetToDefaults() {
        val defaults = UiLayout()
        railWidth = defaults.railWidthDp.dp
        railOpen = defaults.railOpen
        squadWidth = defaults.squadWidthDp.dp
        enemyWidth = defaults.enemyWidthDp.dp
        logHeight = defaults.logHeightDp.dp
        logCollapsed = defaults.logCollapsed
        turnsCollapsed = defaults.turnsCollapsed
        turnsShowInitiative = defaults.turnsShowInitiative
        topBarHeight = defaults.topBarHeightDp.dp
        commandBarHeight = defaults.commandBarHeightDp.dp
        commandsCollapsed = defaults.commandsCollapsed
        mapCellSize = defaults.mapCellSizeDp.dp
        mapShowGrid = defaults.mapShowGrid
        mapGridBrightness = defaults.mapGridBrightness
        targetPlate = defaults.targetPlate?.toOffset()
        activePlate = defaults.activePlate?.toOffset()
        targetPlateScale = defaults.targetPlateScale
        activePlateScale = defaults.activePlateScale
    }

    /** Applica una disposizione caricata in background senza risalvarla. */
    fun restore(loaded: UiLayout) {
        val value = loaded.sanitized()
        railWidth = value.railWidthDp.dp
        railOpen = value.railOpen
        squadWidth = value.squadWidthDp.dp
        enemyWidth = value.enemyWidthDp.dp
        logHeight = value.logHeightDp.dp
        logCollapsed = value.logCollapsed
        turnsCollapsed = value.turnsCollapsed
        turnsShowInitiative = value.turnsShowInitiative
        topBarHeight = value.topBarHeightDp.dp
        commandBarHeight = value.commandBarHeightDp.dp
        commandsCollapsed = value.commandsCollapsed
        mapCellSize = value.mapCellSizeDp.dp
        mapShowGrid = value.mapShowGrid
        mapGridBrightness = value.mapGridBrightness
        targetPlate = value.targetPlate?.toOffset()
        activePlate = value.activePlate?.toOffset()
        targetPlateScale = value.targetPlateScale
        activePlateScale = value.activePlateScale
        lastSaved = value
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
