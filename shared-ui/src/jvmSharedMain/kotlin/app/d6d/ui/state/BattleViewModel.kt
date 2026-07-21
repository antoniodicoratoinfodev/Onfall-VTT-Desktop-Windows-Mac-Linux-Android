package app.d6d.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.AttackRequest
import app.d6d.domain.combat.CombatEvent
import app.d6d.domain.combat.CombatState
import app.d6d.domain.combat.CombatStatus
import app.d6d.domain.combat.CombatantSnapshot
import app.d6d.domain.combat.CombatantState
import app.d6d.domain.combat.ConditionDuration
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.combat.TurnBudget
import app.d6d.domain.space.BattleMap
import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.MapGrid
import app.d6d.domain.space.TokenPlacement
import app.d6d.engine.CombatRuleException
import app.d6d.engine.CombatSession
import app.d6d.ui.components.FloatKind
import app.d6d.ui.components.FloatingNumber
import app.d6d.ui.components.italianLabel

/**
 * Riceve le correzioni fatte durante lo scontro perche' aggiornino anche il
 * catalogo, cosi' la scheda del personaggio non resta indietro rispetto al tavolo.
 */
fun interface CombatantEditSink {
    fun onEdited(definitionId: String, snapshot: CombatantSnapshot)
}

/**
 * Stato di presentazione del combattimento.
 *
 * Non contiene regole: valida e risolve sempre il motore. Qui vivono soltanto
 * selezione, messaggi ed effetti visivi. E' deliberatamente privo di dipendenze
 * Android o desktop, cosi' entrambe le shell possono riusarlo.
 */
class BattleViewModel(
    session: CombatSession,
    // Taglia autorevole di un attore, per identificatore di definizione: la fornisce
    // il roster, cosi' la dimensione del segnaposto viene dal Compendio. Precede
    // editSink perche' quest'ultimo resti il parametro finale, usato come lambda.
    private val footprintProvider: (String) -> Int = { 1 },
    private val editSink: CombatantEditSink = CombatantEditSink { _, _ -> },
) {

    /**
     * Sessione in corso.
     *
     * E' sostituibile perche' caricare una partita salvata rimpiazza il
     * combattimento: ricreare l'intero modello di presentazione perderebbe
     * l'aggancio al catalogo e agli effetti visivi gia' in corso.
     */
    var session: CombatSession = session
        private set

    var state by mutableStateOf(session.currentState())
        private set

    var events by mutableStateOf(session.auditTrail())
        private set

    /** Messaggio dell'ultima regola violata; il livello Guided lo mostra senza bloccare il tavolo. */
    var message by mutableStateOf<String?>(null)

    var selectedTargetId by mutableStateOf<String?>(null)

    var rollMode by mutableStateOf(D20Mode.NORMAL)

    var floating by mutableStateOf<Map<String, List<FloatingNumber>>>(emptyMap())
        private set

    private var floatSequence = 0L

    // --- proiezioni di sola lettura -------------------------------------------------

    val status: CombatStatus get() = state.status()
    val round: Int get() = state.round()
    val canUndo: Boolean get() = session.canUndo()

    /** Quando e' attiva, un doppio clic su un campo lo rende modificabile. */
    var editMode by mutableStateOf(false)

    val activeCombatantId: String? get() = state.currentCombatantId().orElse(null)

    /** Tutti i combattenti del turno corrente: piu' di uno se hanno pareggiato l'iniziativa. */
    val activeCombatantIds: List<String> get() = state.currentCombatantIds()

    val isSimultaneousTurn: Boolean get() = state.currentTurnIsSimultaneous()

    /** L'ordine dei turni raggruppato, per la striscia in cima alla schermata. */
    val turnGroups: List<List<String>> get() = state.turnGroups()

    val turnIndex: Int get() = state.turnIndex()

    var simultaneousTies: Boolean
        get() = state.simultaneousTies()
        set(value) {
            command { session.setSimultaneousTies(value) }
        }

    /** Vero se il combattente e' fra quelli che stanno giocando ora. */
    fun isActive(combatantId: String): Boolean = combatantId in activeCombatantIds

    val partyIds: List<String>
        get() = state.initiativeOrder().filter { it in state.partyCombatantIds() }
            .ifEmpty { state.rosterOrder().filter { it in state.partyCombatantIds() } }

    val enemyIds: List<String>
        get() = state.initiativeOrder().filterNot { it in state.partyCombatantIds() }
            .ifEmpty { state.rosterOrder().filterNot { it in state.partyCombatantIds() } }

    fun combatant(id: String): CombatantState? = state.combatants()[id]

    fun name(id: String): String = combatant(id)?.snapshot()?.name() ?: id

    fun budget(id: String): TurnBudget? = state.turnBudgets()[id]

    fun abilities(id: String): List<AbilityDefinition> =
        combatant(id)?.snapshot()?.abilities().orEmpty()

    fun isParty(id: String): Boolean = id in state.partyCombatantIds()

    fun initiativeScore(id: String): Int? = state.initiativeScores()[id]

    /** Bersaglio corrente, ripiegando sul primo avversario ancora in piedi. */
    fun effectiveTargetId(): String? {
        val chosen = selectedTargetId
        if (chosen != null && combatant(chosen)?.defeated() == false) return chosen
        val active = activeCombatantId ?: return null
        val hostile = if (isParty(active)) enemyIds else partyIds
        return hostile.firstOrNull { combatant(it)?.defeated() == false }
    }

    // --- comandi ---------------------------------------------------------------------

    fun start() = command { session.start() }

    fun attack(abilityId: String) {
        val attacker = activeCombatantId ?: return
        val target = effectiveTargetId() ?: run {
            message = "Nessun bersaglio valido."
            return
        }
        command {
            val result = session.attack(AttackRequest.digital(attacker, target, abilityId, rollMode))
            val damage = result.damageResult()
            when {
                damage.isPresent -> {
                    val applied = damage.get()
                    val critical = applied.critical()
                    push(
                        target,
                        FloatingNumber(
                            id = ++floatSequence,
                            text = "-${applied.totalAdjustedDamage()}",
                            kind = if (critical) FloatKind.CRIT else FloatKind.DAMAGE,
                        ),
                    )
                    if (applied.concentrationCheck().isPresent &&
                        !applied.concentrationCheck().get().maintained()
                    ) {
                        push(target, floatInfo("Concentrazione persa"))
                    }
                }

                else -> push(target, FloatingNumber(++floatSequence, "Mancato", FloatKind.MISS))
            }
        }
    }

    fun endTurn() = command { session.endTurn() }

    fun undo() = command {
        if (!session.undo()) message = "Niente da annullare."
    }

    fun heal(targetId: String, amount: Int) = command {
        val healed = session.heal(targetId, amount)
        if (healed > 0) push(targetId, FloatingNumber(++floatSequence, "+$healed", FloatKind.HEAL))
    }

    fun grantTemporary(targetId: String, amount: Int) = command {
        val granted = session.grantTemporaryHitPoints(targetId, amount)
        if (granted > 0) {
            push(targetId, FloatingNumber(++floatSequence, "+$granted PFT", FloatKind.TEMPORARY))
        }
    }

    /**
     * `rounds` a zero significa durata manuale: la condizione resta finche' il
     * tavolo non la rimuove, che e' il caso piu' comune quando il DM improvvisa.
     */
    fun addCondition(targetId: String, type: ConditionType, rounds: Int) = command {
        val duration = if (rounds > 0) ConditionDuration.rounds(rounds) else ConditionDuration.manual()
        session.addCondition(
            "cond-${++floatSequence}",
            targetId,
            type,
            activeCombatantId ?: targetId,
            "",
            duration,
            "",
            "",
        )
        push(targetId, floatInfo(type.italianLabel))
    }

    fun removeCondition(targetId: String, conditionInstanceId: String) = command {
        session.removeCondition(targetId, conditionInstanceId)
    }

    /**
     * Corregge un campo della scheda in corso di scontro.
     *
     * La modifica passa dal motore, quindi finisce nel registro ed e' annullabile,
     * e viene inoltrata al catalogo cosi' la scheda originale resta allineata.
     */
    fun editCombatant(
        combatantId: String,
        name: String = combatant(combatantId)?.snapshot()?.name() ?: "",
        armorClass: Int? = null,
        maxHitPoints: Int? = null,
        speedFeet: Int? = null,
        initiativeModifier: Int? = null,
        constitutionSaveBonus: Int? = null,
    ) {
        val previous = combatant(combatantId)?.snapshot() ?: return
        val newInitiativeModifier = initiativeModifier ?: previous.initiativeModifier()
        // `withStats` riporta da se' i punti ferita iniziali entro il nuovo massimo
        // e incrementa la revisione: la regola vive nel dominio, non qui.
        val updated = previous.withStats(
            name.ifBlank { previous.name() },
            armorClass ?: previous.armorClass(),
            (maxHitPoints ?: previous.maxHitPoints()).coerceAtLeast(1),
            speedFeet ?: previous.speedFeet(),
            newInitiativeModifier,
            // Il punteggio statico segue il modificatore quando questo cambia,
            // ma resta un campo distinto come vuole lo stat block aggiornato.
            if (initiativeModifier != null) 10 + newInitiativeModifier else previous.initiativeScore(),
            constitutionSaveBonus ?: previous.constitutionSaveBonus(),
        )
        command {
            session.editCombatant(combatantId, updated)
            editSink.onEdited(previous.definitionId(), updated)
        }
    }

    // --- mappa tattica ---------------------------------------------------------------

    val battleMap: BattleMap get() = state.battleMap()

    val mapConfigured: Boolean get() = battleMap.configured()

    fun placementOf(combatantId: String): TokenPlacement? =
        battleMap.placementOf(combatantId).orElse(null)

    /**
     * Ingombro dei segnaposti, in caselle per lato.
     *
     * Lo snapshot da combattimento non porta la taglia — al motore serve la
     * geometria, non il vocabolario — quindi la taglia si autora nel Compendio e
     * arriva qui tramite [footprintProvider], letto per definizione. Un'eventuale
     * voce in [footprints] resta un sovrascrittura per singola istanza (per esempio
     * un effetto di ingrandimento), ma la fonte normale e' la taglia della scheda.
     */
    var footprints by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    fun squaresPerSideFor(combatantId: String): Int {
        placementOf(combatantId)?.let { return it.squaresPerSide() }
        footprints[combatantId]?.let { return it }
        val definitionId = combatant(combatantId)?.snapshot()?.definitionId()
        return definitionId?.let { footprintProvider(it) } ?: 1
    }

    /**
     * Sovrascrive l'ingombro di una singola istanza.
     *
     * Non e' esposto nella schermata di battaglia: la taglia si imposta nel
     * Compendio. Resta a disposizione per effetti temporanei e per la persistenza
     * della sessione.
     */
    fun setFootprint(combatantId: String, squaresPerSide: Int) {
        val squares = squaresPerSide.coerceIn(1, 4)
        footprints = footprints + (combatantId to squares)
        // Se e' gia' sulla mappa va ricollocato con il nuovo ingombro; il motore
        // rifiuta se il nuovo spazio non entra o e' occupato.
        placementOf(combatantId)?.let { existing ->
            command { session.placeCombatant(combatantId, existing.origin(), squares) }
        }
    }

    /** Distanza dichiarabile solo con entrambe le posizioni note. */
    fun distanceFeet(first: String, second: String): Int? =
        battleMap.distanceFeet(first, second).orElse(null)

    fun configureMap(columns: Int, rows: Int, feetPerSquare: Int) = command {
        session.configureMap(MapGrid(columns.coerceAtLeast(1), rows.coerceAtLeast(1), feetPerSquare.coerceAtLeast(1)))
    }

    fun setMapBackground(imageName: String) = command { session.setMapBackground(imageName) }

    fun place(combatantId: String, column: Int, row: Int, squaresPerSide: Int) = command {
        session.placeCombatant(combatantId, GridPosition(column, row), squaresPerSide)
    }

    fun move(combatantId: String, column: Int, row: Int) = command {
        val feet = session.moveCombatant(combatantId, GridPosition(column, row))
        push(combatantId, FloatingNumber(++floatSequence, "$feet ft", FloatKind.INFO))
    }

    fun removeFromMap(combatantId: String) = command { session.removeFromMap(combatantId) }

    /**
     * Dispone chi non e' ancora sulla mappa in due file contrapposte.
     *
     * Serve solo a partire in fretta: e' una comodita' di preparazione, non una
     * regola, e ogni segnaposto resta poi spostabile a mano.
     */
    fun autoPlaceMissing(squaresFor: (String) -> Int = { 1 }) {
        if (!mapConfigured) {
            message = "Configura prima la griglia."
            return
        }
        val grid = battleMap.grid()
        var partyColumn = 1
        var enemyColumn = 1
        partyIds.forEach { id ->
            if (battleMap.isPlaced(id)) return@forEach
            place(id, partyColumn.coerceAtMost(grid.columns() - 1), grid.rows() - 2, squaresFor(id))
            partyColumn += 2
        }
        enemyIds.forEach { id ->
            if (battleMap.isPlaced(id)) return@forEach
            place(id, enemyColumn.coerceAtMost(grid.columns() - 1), 1, squaresFor(id))
            enemyColumn += 2
        }
    }

    /** Combattente che occupa una casella, per tradurre un clic sulla griglia. */
    fun occupantAt(column: Int, row: Int): String? =
        battleMap.occupantAt(GridPosition(column, row)).orElse(null)

    /**
     * Stato di presentazione da salvare accanto al combattimento.
     *
     * Sono le scelte del tavolo che il motore non conosce: chi e' inquadrato come
     * bersaglio, come si stanno tirando i dadi, e l'ingombro dei segnaposti non
     * ancora collocati sulla mappa.
     */
    fun presentationState(): Map<String, String> = buildMap {
        selectedTargetId?.let { put("selectedTargetId", it) }
        put("rollMode", rollMode.name)
        if (footprints.isNotEmpty()) {
            put("footprints", footprints.entries.joinToString(",") { "${it.key}=${it.value}" })
        }
    }

    /** Sostituisce il combattimento con quello caricato dal disco. */
    fun adopt(loaded: CombatSession, presentation: Map<String, String>) {
        session = loaded
        floating = emptyMap()
        message = null
        selectedTargetId = presentation["selectedTargetId"]
        rollMode = presentation["rollMode"]
            ?.let { name -> runCatching { D20Mode.valueOf(name) }.getOrNull() }
            ?: D20Mode.NORMAL
        footprints = presentation["footprints"]
            ?.split(',')
            ?.mapNotNull { entry ->
                val parts = entry.split('=')
                val squares = parts.getOrNull(1)?.toIntOrNull()
                if (parts.size == 2 && squares != null) parts[0] to squares else null
            }
            ?.toMap()
            .orEmpty()
        sync()
    }

    fun expire(combatantId: String, floatId: Long) {
        floating = floating.toMutableMap().apply {
            val remaining = this[combatantId]?.filterNot { it.id == floatId }.orEmpty()
            if (remaining.isEmpty()) remove(combatantId) else put(combatantId, remaining)
        }
    }

    fun dismissMessage() {
        message = null
    }

    // --- interni ---------------------------------------------------------------------

    private fun floatInfo(text: String) = FloatingNumber(++floatSequence, text, FloatKind.INFO)

    private fun push(combatantId: String, number: FloatingNumber) {
        floating = floating.toMutableMap().apply {
            put(combatantId, this[combatantId].orEmpty() + number)
        }
    }

    /**
     * Esegue un comando del motore e risincronizza.
     *
     * Una violazione delle regole diventa un messaggio, non un errore fatale: il
     * motore ha gia' annullato il proprio comando, quindi lo stato resta coerente.
     */
    private fun command(block: () -> Unit) {
        try {
            message = null
            block()
        } catch (failure: CombatRuleException) {
            message = failure.message
        } catch (failure: IllegalArgumentException) {
            message = failure.message
        } catch (failure: IllegalStateException) {
            message = failure.message
        } finally {
            sync()
        }
    }

    private fun sync() {
        state = session.currentState()
        events = session.auditTrail()
    }
}

/** Ultimi eventi in ordine cronologico inverso, per il registro a schermo. */
fun List<CombatEvent>.latest(count: Int): List<CombatEvent> =
    asReversed().take(count)
