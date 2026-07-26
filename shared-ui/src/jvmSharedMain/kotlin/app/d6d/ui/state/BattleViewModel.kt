package app.d6d.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.AreaSpellResult
import app.d6d.domain.combat.AttackRequest
import app.d6d.domain.combat.CombatEvent
import app.d6d.domain.combat.CombatState
import app.d6d.domain.combat.CombatStatus
import app.d6d.domain.combat.CombatantSnapshot
import app.d6d.domain.combat.CombatantState
import app.d6d.domain.combat.ConditionDuration
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.combat.D20RollInput
import app.d6d.domain.combat.DamageComponent
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.TurnBudget
import app.d6d.domain.space.BattleMap
import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.MapGrid
import app.d6d.domain.space.TokenPlacement
import app.d6d.engine.CombatRuleException
import app.d6d.engine.CombatSession
import app.d6d.sheet.feetWithMetres
import app.d6d.ui.components.FloatKind
import app.d6d.ui.components.FloatingNumber
import app.d6d.ui.components.italianLabel
import app.d6d.ui.encounter.EncounterMode

/**
 * Riceve le correzioni fatte durante lo scontro perche' aggiornino anche il
 * catalogo, cosi' la scheda del personaggio non resta indietro rispetto al tavolo.
 */
fun interface CombatantEditSink {
    fun onEdited(definitionId: String, snapshot: CombatantSnapshot)

    /**
     * Risincronizza la fonte autorevole dopo un Undo. Il comportamento predefinito
     * riusa lo stesso upsert dell'edit, mantenendo compatibili i sink a lambda.
     */
    fun onResynced(definitionId: String, snapshot: CombatantSnapshot) {
        onEdited(definitionId, snapshot)
    }
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

    /** Cambia ogni volta che [adopt] sostituisce l'istanza della sessione. */
    var sessionGeneration by mutableStateOf(0L)
        private set

    /** Messaggio dell'ultima regola violata; il livello Guided lo mostra senza bloccare il tavolo. */
    var message by mutableStateOf<String?>(null)

    private var targetSelection by mutableStateOf<String?>(null)

    /**
     * Bersaglio scelto per l'azione. Puo' appartenere a qualunque schieramento:
     * il combattimento consente esplicitamente il fuoco amico.
     */
    var selectedTargetId: String?
        get() = targetSelection
        set(value) {
            targetSelection = sanitizeTarget(value)
        }

    private var activeActorSelection by mutableStateOf<String?>(null)

    /** Combattente di cui la barra mostra informazioni e capacita', senza cambiarne il turno. */
    private var inspectionSelection by mutableStateOf<String?>(null)

    /**
     * Capacita' singola in attesa che l'utente scelga esplicitamente il bersaglio.
     *
     * Conserva anche l'attore: in un turno simultaneo non si deve rischiare di
     * lanciare la capacita' del secondo membro usando il primo.
     */
    var singleTargeting by mutableStateOf<SingleTargeting?>(null)
        private set

    var rollMode by mutableStateOf(D20Mode.NORMAL)

    var floating by mutableStateOf<Map<String, List<FloatingNumber>>>(emptyMap())
        private set

    /**
     * Esito persistente dell'ultima capacita' risolta.
     *
     * I numeri sulla mappa sono volutamente brevi; questa nota rende invece
     * inequivocabile che il clic sul bersaglio ha gia' applicato l'attacco, anche
     * quando il tiro manca e quindi i PF restano invariati.
     */
    var actionResolution by mutableStateOf<ActionResolution?>(null)
        private set

    private var floatSequence = 0L

    /**
     * Incantesimo ad area in fase di mira.
     *
     * Finche' non e' nullo l'interfaccia disegna un cerchio grande quanto il raggio
     * che segue il mouse, e un clic sulla mappa lo fa detonare invece di spostare un
     * segnaposto.
     */
    var areaTargeting by mutableStateOf<AreaTargeting?>(null)
        private set

    /**
     * Risoluzione manuale di un'area, in attesa che il tavolo decida i tiri salvezza.
     *
     * Compare solo con la modalità modifica attiva: elenca i bersagli nell'area e
     * lascia scegliere chi supera il tiro prima di applicare i danni.
     */
    var pendingArea by mutableStateOf<PendingArea?>(null)
        private set

    // --- proiezioni di sola lettura -------------------------------------------------

    val status: CombatStatus get() = state.status()
    val round: Int get() = state.round()
    val canUndo: Boolean get() = session.canUndo()
    val encounterId: String get() = state.encounterId()
    val displayName: String get() = encounterId

    /** Quando e' attiva, un doppio clic su un campo lo rende modificabile. */
    var editMode by mutableStateOf(false)

    /**
     * Sotto-modalità della modifica: sposta e stira lo sfondo della mappa.
     *
     * Vive solo dentro [editMode]: chi la spegne la porta con se'. In questo stato
     * il trascinamento sulla mappa afferra l'immagine invece di scorrere la camera.
     */
    var mapEditMode by mutableStateOf(false)

    /** Modalità scelta nella procedura Nuova partita, salvata con la presentazione. */
    var encounterMode by mutableStateOf(EncounterMode.ROLEPLAY_FIGHT_EXPLORATION)
        private set

    /** Attore scelto nel gruppo simultaneo; altrimenti il primo del turno. */
    val activeActorId: String?
        get() = activeActorSelection?.takeIf { it in activeCombatantIds }
            ?: state.currentCombatantId().orElse(null)

    /** Alias storico usato dalle schermate esistenti. */
    val activeCombatantId: String? get() = activeActorId

    /**
     * Tutti i combattenti che possono agire nel turno corrente: piu' di uno se
     * hanno pareggiato l'iniziativa. I membri a 0 PF restano nella striscia, ma il
     * motore li esclude da questo elenco.
     */
    val activeCombatantIds: List<String> get() = state.currentCombatantIds()

    val isSimultaneousTurn: Boolean get() = state.currentTurnIsSimultaneous()

    /** L'ordine dei turni raggruppato, per la striscia in cima alla schermata. */
    val turnGroups: List<List<String>> get() = state.turnGroups()

    val turnIndex: Int get() = state.turnIndex()

    var simultaneousTies: Boolean
        get() = state.simultaneousTies()
        set(value) {
            if (status != CombatStatus.DRAFT && status != CombatStatus.READY) {
                message = "La gestione delle parità non si può cambiare durante il combattimento."
            } else {
                command { session.setSimultaneousTies(value) }
            }
        }

    fun selectActiveActor(combatantId: String) {
        if (combatantId !in activeCombatantIds) {
            message = "Il combattente scelto non appartiene al turno corrente."
            return
        }
        if (activeActorId != combatantId) {
            singleTargeting = null
            areaTargeting = null
            pendingArea = null
            targetSelection = null
        }
        activeActorSelection = combatantId
        inspectionSelection = combatantId
        targetSelection = sanitizeTarget(targetSelection)
        message = null
    }

    /** Combattente mostrato nei comandi; in assenza di una scelta segue il turno reale. */
    val inspectedCombatantId: String?
        get() = inspectionSelection?.takeIf { combatant(it) != null } ?: activeActorId

    /** Ispeziona una scheda senza trasformarla ne' nell'attore del turno ne' nel bersaglio. */
    fun inspectCombatant(combatantId: String) {
        if (combatant(combatantId) == null) return
        if (combatantId in activeCombatantIds) {
            // In un pareggio simultaneo scegliere un membro attivo significa anche
            // scegliere quale dei due sta usando i propri comandi.
            activeActorSelection = combatantId
        }
        inspectionSelection = combatantId
        message = null
    }

    /** Vero soltanto quando le capacita' mostrate appartengono all'attore che puo' agire ora. */
    fun canUseAbilitiesOf(combatantId: String): Boolean =
        status == CombatStatus.ACTIVE &&
            combatantId == activeActorId &&
            combatant(combatantId)?.defeated() == false

    /**
     * Punto unico per tutti i clic su una creatura.
     *
     * Fuori dalla mira il clic ispeziona. Durante la mira conferma invece il
     * bersaglio, senza cambiare la scheda che si sta guardando.
     */
    fun onCombatantClicked(combatantId: String) {
        when {
            singleTargeting != null -> confirmSingleTarget(combatantId)
            areaTargeting != null -> {
                message = "Stai mirando un'area: scegli un punto sulla mappa oppure annulla."
            }
            else -> inspectCombatant(combatantId)
        }
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

    /** Almeno un combattente puo' ancora ricevere un turno. */
    val hasStandingCombatants: Boolean
        get() = state.combatants().values.any { !it.defeated() }

    /** Bersaglio corrente, ripiegando sul primo avversario ancora in piedi. */
    fun effectiveTargetId(): String? {
        val active = activeActorId ?: return null
        val chosen = sanitizeTarget(targetSelection)
        if (chosen != null) return chosen
        val hostile = if (isParty(active)) enemyIds else partyIds
        return hostile.firstOrNull { it != active && combatant(it)?.defeated() == false }
    }

    // --- comandi ---------------------------------------------------------------------

    fun start() = command { session.start() }

    fun attack(abilityId: String) {
        val attacker = activeCombatantId ?: return
        val target = effectiveTargetId() ?: run {
            message = "Nessun bersaglio valido."
            return
        }
        attack(attacker, target, abilityId)
    }

    /** Esegue un attacco sul bersaglio esplicito, senza alcun ripiego automatico. */
    private fun attack(attacker: String, target: String, abilityId: String) {
        command {
            val abilityName = abilities(attacker)
                .firstOrNull { it.id() == abilityId }
                ?.name()
                ?: abilityId
            val targetName = name(target)
            val targetArmorClass = combatant(target)?.snapshot()?.armorClass()
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
                    actionResolution = ActionResolution(
                        text = buildString {
                            append('«').append(abilityName).append("»: ")
                            if (critical) append("colpo critico, ")
                            append(targetName)
                                .append(" subisce ")
                                .append(applied.totalAdjustedDamage())
                                .append(" danni; ")
                                .append(applied.targetHitPointsAfter())
                                .append(" PF rimasti")
                            targetArmorClass?.let {
                                append(" (").append(result.attackRoll().total()).append(" contro CA ").append(it).append(')')
                            }
                            append('.')
                        },
                        isHit = true,
                    )
                    if (applied.concentrationCheck().isPresent &&
                        !applied.concentrationCheck().get().maintained()
                    ) {
                        push(target, floatInfo("Concentrazione persa"))
                    }
                }

                else -> {
                    push(target, FloatingNumber(++floatSequence, "Mancato", FloatKind.MISS))
                    actionResolution = ActionResolution(
                        text = buildString {
                            append('«').append(abilityName).append("»: ")
                                .append(targetName)
                                .append(" mancato")
                            targetArmorClass?.let {
                                append(" (").append(result.attackRoll().total()).append(" contro CA ").append(it).append(')')
                            }
                            append("; 0 danni.")
                        },
                        isHit = false,
                    )
                }
            }
        }
    }

    /**
     * Seleziona una capacita' utilizzabile.
     *
     * Le aree passano alla mira sulla griglia; le capacita' singole aspettano il
     * clic esplicito su una creatura. Se la scheda ispezionata non e' davvero in
     * turno non parte alcuna mira.
     */
    fun beginAbilityTargeting(abilityId: String) {
        val attacker = activeActorId ?: run {
            message = "Nessun attore di turno."
            return
        }
        if (inspectedCombatantId != attacker || !canUseAbilitiesOf(attacker)) {
            message = "Le capacità della scheda in esame sono solo consultabili: non è il suo turno."
            return
        }
        val ability = abilities(attacker).firstOrNull { it.id() == abilityId } ?: run {
            message = "Capacità non trovata."
            return
        }
        if (singleTargeting?.let { it.abilityId == abilityId && it.attackerId == attacker } == true) {
            cancelSingleTargeting()
            return
        }
        if (areaTargeting?.let { it.abilityId == abilityId && it.casterId == attacker } == true) {
            cancelAreaTargeting()
            return
        }

        actionResolution = null
        message = null
        if (ability.isArea) {
            beginAreaTargeting(abilityId)
            return
        }

        areaTargeting = null
        pendingArea = null
        targetSelection = null
        singleTargeting = SingleTargeting(ability.id(), attacker, ability.name())
    }

    /** Annulla la scelta del bersaglio di una capacita' singola. */
    fun cancelSingleTargeting() {
        singleTargeting = null
        message = null
    }

    /** Conferma il bersaglio senza mai sostituirlo con il primo nemico disponibile. */
    private fun confirmSingleTarget(targetId: String) {
        val targeting = singleTargeting ?: return
        if (targeting.attackerId !in activeCombatantIds || activeActorId != targeting.attackerId) {
            singleTargeting = null
            message = "Il turno è cambiato: seleziona di nuovo la capacità."
            return
        }
        if (sanitizeTargetFor(targeting.attackerId, targetId) == null) {
            message = when {
                targetId == targeting.attackerId -> "L'attore non può essere il proprio bersaglio."
                combatant(targetId)?.defeated() == true -> "Il bersaglio è già a 0 punti ferita."
                else -> "Bersaglio non valido."
            }
            return
        }

        targetSelection = targetId
        val revisionBefore = state.revision()
        attack(targeting.attackerId, targetId, targeting.abilityId)
        if (state.revision() != revisionBefore) {
            singleTargeting = null
        }
    }

    // --- incantesimi ad area ---------------------------------------------------------

    /**
     * Comincia a mirare un incantesimo ad area con l'attore di turno.
     *
     * Non infligge nulla: sara' un clic sulla mappa a far detonare l'area sul punto
     * scelto.
     */
    fun beginAreaTargeting(abilityId: String) {
        actionResolution = null
        val caster = activeCombatantId ?: run {
            message = "Nessun attore di turno."
            return
        }
        if (inspectedCombatantId != caster || !canUseAbilitiesOf(caster)) {
            message = "Le capacità della scheda in esame sono solo consultabili: non è il suo turno."
            return
        }
        val ability = abilities(caster).firstOrNull { it.id() == abilityId } ?: return
        if (!ability.isArea) return
        singleTargeting = null
        pendingArea = null
        message = "Mira «${ability.name()}»: clicca sulla mappa per centrare l'area. Esc per annullare."
        areaTargeting = AreaTargeting(abilityId, caster, ability.name(), ability.areaRadiusFeet(), ability.rangeFeet())
    }

    /** Abbandona la mira o la risoluzione manuale in corso. */
    fun cancelAreaTargeting() {
        areaTargeting = null
        pendingArea = null
        message = null
    }

    /**
     * Fa detonare l'area in mira sulla casella scelta.
     *
     * In gioco normale il motore tira i tiri salvezza e applica subito i danni. Con
     * la modalità modifica attiva prepara invece la risoluzione manuale: elenca chi
     * e' nell'area e lascia decidere al tavolo chi supera il tiro.
     */
    fun resolveAreaAt(column: Int, row: Int) {
        val targeting = areaTargeting ?: return
        val center = GridPosition(column, row)
        if (editMode) {
            val dc = combatant(targeting.casterId)?.snapshot()?.spellSaveDc() ?: 0
            val ids = try {
                session.areaTargets(targeting.casterId, center, targeting.abilityId)
            } catch (failure: RuntimeException) {
                message = failure.message
                return
            }
            pendingArea = PendingArea(
                abilityId = targeting.abilityId,
                casterId = targeting.casterId,
                spellName = targeting.name,
                center = center,
                radiusFeet = targeting.radiusFeet,
                saveDc = dc,
                targets = ids.map { AreaSaveChoice(it, name(it), saved = false) },
            )
            areaTargeting = null
            message = if (ids.isEmpty()) {
                "Nessuna creatura nell'area."
            } else {
                "Segna chi supera il tiro salvezza, poi applica."
            }
        } else {
            command {
                pushAreaResult(session.castArea(targeting.casterId, center, targeting.abilityId))
            }
            areaTargeting = null
        }
    }

    /** Cambia l'esito del tiro salvezza di un bersaglio nella risoluzione manuale. */
    fun toggleAreaSave(combatantId: String) {
        val current = pendingArea ?: return
        pendingArea = current.copy(
            targets = current.targets.map {
                if (it.combatantId == combatantId) it.copy(saved = !it.saved) else it
            },
        )
    }

    /** Applica la risoluzione manuale con gli esiti dei tiri salvezza decisi al tavolo. */
    fun applyPendingArea() {
        val pending = pendingArea ?: return
        command {
            val saved = pending.targets.associate { it.combatantId to it.saved }
            pushAreaResult(session.castAreaManual(pending.casterId, pending.center, pending.abilityId, saved))
        }
        pendingArea = null
    }

    private fun pushAreaResult(result: AreaSpellResult) {
        result.targets().forEach { outcome ->
            val damage = outcome.damage()
            if (damage.isPresent) {
                push(
                    outcome.targetId(),
                    FloatingNumber(
                        id = ++floatSequence,
                        text = "-${damage.get().totalAdjustedDamage()}",
                        // Un TS superato dimezza: si distingue dal colpo pieno.
                        kind = if (outcome.saved()) FloatKind.INFO else FloatKind.DAMAGE,
                    ),
                )
            } else if (outcome.saved()) {
                push(outcome.targetId(), floatInfo("Salvo"))
            }
        }
    }

    fun endTurn() = command { session.endTurn() }

    /** Sposta a mano il turno corrente su un combattente scelto (correzione da tavolo). */
    fun setCurrentTurn(combatantId: String) = command { session.setCurrentTurn(combatantId) }

    /** Riordina i turni a scontro gia' avviato, tenendo corrente chi sta agendo. */
    fun reorderTurns(order: List<String>) = command { session.reorderTurns(order) }

    /**
     * Sposta un combattente di [delta] posizioni nell'ordine dei turni: negativo
     * lo anticipa, positivo lo posticipa. Comodo per i comandi ◀ ▶ della striscia.
     */
    fun moveTurn(combatantId: String, delta: Int) {
        val order = state.initiativeOrder().toMutableList()
        val index = order.indexOf(combatantId)
        if (index < 0) return
        val target = (index + delta).coerceIn(0, order.size - 1)
        if (target == index) return
        order.removeAt(index)
        order.add(target, combatantId)
        reorderTurns(order)
    }

    /**
     * Cambia il punteggio d'iniziativa di un combattente (quello mostrato nelle
     * barre) riordinando di conseguenza la coda. A scontro avviato passa
     * dall'override; in fase di preparazione usa il comando ordinario.
     */
    fun overrideInitiative(combatantId: String, total: Int) = command {
        if (status == CombatStatus.ACTIVE || status == CombatStatus.PAUSED) {
            session.overrideInitiative(combatantId, total)
        } else {
            session.setInitiative(combatantId, total)
        }
    }

    fun undo() {
        message = null
        actionResolution = null
        val effect = undoEffects.lastOrNull() ?: UndoEffect.None
        try {
            if (!session.undo()) {
                message = "Niente da annullare."
                return
            }
            if (undoEffects.isNotEmpty()) undoEffects.removeLast()
            sync()
            if (effect is UndoEffect.CombatantEdit) {
                combatant(effect.combatantId)?.snapshot()?.let { restored ->
                    editSink.onResynced(effect.definitionId, restored)
                }
            }
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

    /** Danno inserito manualmente dal tavolo, comunque risolto dal motore. */
    fun applyManualDamage(
        targetId: String,
        amount: Int,
        damageType: DamageType = DamageType.FORCE,
    ) = command {
        val result = session.applyDamage(
            activeActorId.orEmpty(),
            targetId,
            listOf(DamageComponent(damageType, amount)),
            false,
        )
        if (result.totalAdjustedDamage() > 0) {
            push(targetId, FloatingNumber(++floatSequence, "-${result.totalAdjustedDamage()}", FloatKind.DAMAGE))
        }
    }

    fun rollDeathSave(targetId: String) = command {
        val result = session.rollDeathSave(targetId, D20RollInput.digital())
        val label = when {
            result.dead() -> "Morto"
            result.stable() -> "Stabile"
            else -> "Morte ${result.successes()}/${result.failures()}"
        }
        push(targetId, floatInfo(label))
    }

    fun stabilize(targetId: String) = command {
        session.stabilize(targetId, "manuale")
        push(targetId, floatInfo("Stabile"))
    }

    fun setExhaustion(targetId: String, level: Int) = command {
        session.setExhaustion(targetId, level)
        push(targetId, floatInfo("Sfinimento $level"))
    }

    fun pause() = command { session.pause() }

    fun resume() = command { session.resume() }

    fun resolve(outcome: String = "Concluso dal tavolo") = command { session.resolve(outcome) }

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
        command(UndoEffect.CombatantEdit(combatantId, previous.definitionId())) {
            session.editCombatant(combatantId, updated)
            try {
                editSink.onEdited(previous.definitionId(), updated)
            } catch (failure: Exception) {
                // La scheda è autorevole: se la sua scrittura atomica fallisce,
                // annulla anche la modifica appena registrata nel combattimento.
                session.undo()
                throw IllegalStateException(
                    failure.message ?: "La correzione non è stata salvata nella scheda.",
                    failure,
                )
            }
        }
    }

    // --- mappa tattica ---------------------------------------------------------------

    val battleMap: BattleMap get() = state.battleMap()

    val mapConfigured: Boolean get() = battleMap.configured()

    fun placementOf(combatantId: String): TokenPlacement? =
        battleMap.placementOf(combatantId).orElse(null)

    /**
     * Caselle di movimento ancora disponibili nel turno del combattente.
     *
     * Zero quando il budget e' esaurito o quando il combattente non ne ha uno
     * (non e' il suo turno). E' la stessa misura dell'alone di movimento, cosi' il
     * trascinamento sulla mappa e il raggio disegnato coincidono sempre.
     */
    fun movementSquaresRemaining(combatantId: String): Int {
        if (!mapConfigured) return 0
        val budget = budget(combatantId) ?: return 0
        val feetPerSquare = battleMap.grid().feetPerSquare()
        if (feetPerSquare <= 0) return 0
        return budget.movementRemainingFeet() / feetPerSquare
    }

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
        // Se e' gia' sulla mappa va ricollocato con il nuovo ingombro; il motore
        // rifiuta se il nuovo spazio non entra o e' occupato.
        val existing = placementOf(combatantId)
        if (existing == null) {
            footprints = footprints + (combatantId to squares)
        } else {
            val revisionBefore = state.revision()
            command { session.placeCombatant(combatantId, existing.origin(), squares) }
            if (state.revision() != revisionBefore) {
                footprints = footprints + (combatantId to squares)
            }
        }
    }

    /** Distanza dichiarabile solo con entrambe le posizioni note. */
    fun distanceFeet(first: String, second: String): Int? =
        battleMap.distanceFeet(first, second).orElse(null)

    fun configureMap(columns: Int, rows: Int, feetPerSquare: Int) = command {
        session.configureMap(MapGrid(columns.coerceAtLeast(1), rows.coerceAtLeast(1), feetPerSquare.coerceAtLeast(1)))
    }

    fun setMapBackground(imageName: String) {
        if (imageName.isBlank()) mapEditMode = false
        command { session.setMapBackground(imageName) }
    }

    /** Colloca lo sfondo sulla griglia (misure in caselle). Un passo annullabile. */
    fun setMapBackgroundTransform(offsetX: Double, offsetY: Double, width: Double, height: Double) = command {
        session.setMapBackgroundTransform(offsetX, offsetY, width, height)
    }

    fun place(combatantId: String, column: Int, row: Int, squaresPerSide: Int) = command {
        session.placeCombatant(combatantId, GridPosition(column, row), squaresPerSide)
    }

    /** Riposizionamento di preparazione: non consuma il movimento del turno. */
    fun reposition(combatantId: String, column: Int, row: Int) {
        place(combatantId, column, row, squaresPerSideFor(combatantId))
    }

    fun move(combatantId: String, column: Int, row: Int) = command {
        val feet = session.moveCombatant(combatantId, GridPosition(column, row))
        push(combatantId, FloatingNumber(++floatSequence, feetWithMetres(feet, "ft"), FloatKind.INFO))
    }

    fun moveActive(column: Int, row: Int) {
        val actor = activeActorId ?: run {
            message = "Nessun attore attivo."
            return
        }
        move(actor, column, row)
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
     * bersaglio, chi si sta soltanto ispezionando, come si tirano i dadi e
     * l'ingombro dei segnaposti non ancora collocati sulla mappa.
     */
    fun presentationState(): Map<String, String> = buildMap {
        selectedTargetId?.let { put("selectedTargetId", it) }
        activeActorSelection?.takeIf { it in activeCombatantIds }?.let { put("activeActorId", it) }
        inspectionSelection?.takeIf { combatant(it) != null }?.let { put("inspectedCombatantId", it) }
        put("rollMode", rollMode.name)
        put("encounterMode", encounterMode.name)
        put("editMode", editMode.toString())
        put("mapEditMode", mapEditMode.toString())
        if (footprints.isNotEmpty()) {
            put("footprints", footprints.entries.joinToString(",") { "${it.key}=${it.value}" })
        }
    }

    /** Sostituisce il combattimento con quello caricato dal disco. */
    fun adopt(loaded: CombatSession, presentation: Map<String, String>) {
        session = loaded
        sessionGeneration++
        undoEffects.clear()
        floating = emptyMap()
        actionResolution = null
        areaTargeting = null
        pendingArea = null
        singleTargeting = null
        message = null
        editMode = presentation["editMode"] == "true"
        mapEditMode = editMode && presentation["mapEditMode"] == "true"
        activeActorSelection = null
        inspectionSelection = null
        targetSelection = null
        sync(forceTurnReset = true)
        presentation["activeActorId"]?.takeIf { it in activeCombatantIds }?.let {
            activeActorSelection = it
        }
        presentation["inspectedCombatantId"]?.takeIf { combatant(it) != null }?.let {
            inspectionSelection = it
        }
        selectedTargetId = presentation["selectedTargetId"]
        rollMode = presentation["rollMode"]
            ?.let { name -> runCatching { D20Mode.valueOf(name) }.getOrNull() }
            ?: D20Mode.NORMAL
        encounterMode = presentation["encounterMode"]
            ?.let { name -> runCatching { EncounterMode.valueOf(name) }.getOrNull() }
            ?: EncounterMode.ROLEPLAY_FIGHT_EXPLORATION
        footprints = presentation["footprints"]
            ?.split(',')
            ?.mapNotNull { entry ->
                val parts = entry.split('=')
                val squares = parts.getOrNull(1)?.toIntOrNull()
                if (parts.size == 2 && squares != null && squares in 1..4 && combatant(parts[0]) != null) {
                    parts[0] to squares
                } else {
                    null
                }
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

    fun dismissActionResolution() {
        actionResolution = null
    }

    /** Mostra una nota guidata senza inviare alcun comando al motore. */
    fun showMessage(text: String) {
        message = text.takeIf { it.isNotBlank() }
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
    private fun command(
        undoEffect: UndoEffect = UndoEffect.None,
        block: () -> Unit,
    ) {
        val revisionBefore = state.revision()
        try {
            message = null
            actionResolution = null
            block()
        } catch (failure: CombatRuleException) {
            message = failure.message
        } catch (failure: IllegalArgumentException) {
            message = failure.message
        } catch (failure: IllegalStateException) {
            message = failure.message
        } finally {
            sync()
            if (state.revision() != revisionBefore) undoEffects.addLast(undoEffect)
        }
    }

    private fun sync(forceTurnReset: Boolean = false) {
        val previousTurn = turnIdentity(state)
        val updated = session.currentState()
        state = updated
        events = session.auditTrail()
        if (forceTurnReset || previousTurn != turnIdentity(updated)) {
            activeActorSelection = null
            inspectionSelection = null
            targetSelection = null
            singleTargeting = null
            areaTargeting = null
            pendingArea = null
        } else {
            activeActorSelection = activeActorSelection?.takeIf { it in activeCombatantIds }
            inspectionSelection = inspectionSelection?.takeIf { combatant(it) != null }
            targetSelection = sanitizeTarget(targetSelection)
            singleTargeting = singleTargeting?.takeIf {
                it.attackerId == activeActorId &&
                    it.attackerId in activeCombatantIds &&
                    combatant(it.attackerId)?.defeated() == false &&
                    abilities(it.attackerId).any { ability -> ability.id() == it.abilityId }
            }
        }
    }

    private fun sanitizeTarget(candidate: String?): String? {
        val active = activeActorId ?: return null
        return sanitizeTargetFor(active, candidate)
    }

    private fun sanitizeTargetFor(attacker: String, candidate: String?): String? {
        return candidate?.takeIf {
            it != attacker &&
                combatant(it)?.defeated() == false
        }
    }

    private fun turnIdentity(snapshot: CombatState): String? {
        if (snapshot.status() != CombatStatus.ACTIVE && snapshot.status() != CombatStatus.PAUSED) return null
        return "${snapshot.round()}:${snapshot.turnIndex()}:${snapshot.currentCombatantIds().joinToString(",")}"
    }

    private sealed interface UndoEffect {
        data object None : UndoEffect
        data class CombatantEdit(val combatantId: String, val definitionId: String) : UndoEffect
    }

    private val undoEffects = ArrayDeque<UndoEffect>()
}

/** Incantesimo ad area che l'attore di turno sta mirando sulla mappa. */
data class AreaTargeting(
    val abilityId: String,
    val casterId: String,
    val name: String,
    val radiusFeet: Int,
    val rangeFeet: Int,
)

/** Capacita' singola selezionata, in attesa del clic esplicito sul bersaglio. */
data class SingleTargeting(
    val abilityId: String,
    val attackerId: String,
    val name: String,
)

/** Conferma visiva persistente di un attacco gia' risolto al clic sul bersaglio. */
data class ActionResolution(
    val text: String,
    val isHit: Boolean,
)

/** Un bersaglio nell'area e l'esito, ancora modificabile, del suo tiro salvezza. */
data class AreaSaveChoice(
    val combatantId: String,
    val name: String,
    val saved: Boolean,
)

/** Risoluzione manuale di un'area in attesa che il tavolo decida i tiri salvezza. */
data class PendingArea(
    val abilityId: String,
    val casterId: String,
    val spellName: String,
    val center: GridPosition,
    val radiusFeet: Int,
    val saveDc: Int,
    val targets: List<AreaSaveChoice>,
)

/** Ultimi eventi in ordine cronologico inverso, per il registro a schermo. */
fun List<CombatEvent>.latest(count: Int): List<CombatEvent> =
    asReversed().take(count)
