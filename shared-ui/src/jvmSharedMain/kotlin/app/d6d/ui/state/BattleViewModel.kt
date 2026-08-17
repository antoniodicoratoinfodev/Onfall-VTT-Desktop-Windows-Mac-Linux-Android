package app.d6d.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.domain.combat.AbilityDefinition
import app.d6d.domain.combat.AbilityEffect
import app.d6d.domain.combat.AreaSpellResult
import app.d6d.domain.combat.AttackRequest
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.CombatState
import app.d6d.domain.combat.CombatStatus
import app.d6d.domain.combat.CombatantSnapshot
import app.d6d.domain.combat.CombatantState
import app.d6d.domain.combat.CombatResourceState
import app.d6d.domain.combat.ConditionDuration
import app.d6d.domain.combat.ConditionType
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.combat.D20RollInput
import app.d6d.domain.combat.DamageComponent
import app.d6d.domain.combat.DamageType
import app.d6d.domain.combat.HealingTarget
import app.d6d.domain.combat.ResolutionMethod
import app.d6d.domain.combat.SaveAbility
import app.d6d.domain.combat.SpellSlotResourceId
import app.d6d.domain.combat.TurnBudget
import app.d6d.domain.space.BattleMap
import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.MapGrid
import app.d6d.domain.space.TokenPlacement
import app.d6d.engine.CombatRuleException
import app.d6d.engine.CombatSession
import app.d6d.engine.ai.EnemyCpuActionReport
import app.d6d.engine.ai.EnemyCpuActionType
import app.d6d.engine.ai.EnemyCpuDifficulty
import app.d6d.engine.ai.EnemyCpuResult
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.ui.components.FloatKind
import app.d6d.ui.components.FloatingNumber
import app.d6d.i18n.label
import app.d6d.ui.encounter.EncounterMode
import app.d6d.ui.maps.PendingToken
import app.d6d.ui.maps.arrangeTokens
import app.d6d.ui.i18n.AppLocale
import app.d6d.ui.i18n.LocalizedText
import app.d6d.ui.i18n.Strings
import app.d6d.ui.i18n.literalText
import app.d6d.ui.maps.gridTooSmallMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

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

/** Propaga alla scheda gli usi di risorsa consumati o ripristinati in battaglia. */
fun interface CombatResourceSink {
    fun onChanged(definitionId: String, resources: List<CombatResourceState>)
}

/**
 * Fotografia autonoma usata dai writer di sessione e recovery.
 *
 * [session] non e' quella viva del tavolo: viene ricostruita dallo stesso stato
 * e dallo stesso audit catturati sotto il lock della sessione originale. In
 * questo modo il codec puo' lavorare su I/O mentre il tavolo continua a mutare.
 */
internal data class BattlePersistenceSnapshot(
    val session: CombatSession,
    val state: CombatState,
    val presentation: Map<String, String>,
    val generation: Long,
)

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
    /**
     * Classificazione autorevole di una capacita', per identificatore: vero se e'
     * un tratto permanente, null se il Compendio non la conosce.
     *
     * Lo snapshot del combattente e' fotografato all'inizio dell'incontro, quindi
     * senza questa lettura una riclassificazione varrebbe solo dalla partita dopo.
     * Chi la cambia lo fa perche' e' finita nel posto sbagliato: deve spostarsi
     * subito, anche a tavolo aperto.
     */
    private val passiveProvider: (String) -> Boolean? = { null },
    private val actorProvider: (String) -> ActorDefinition? = { null },
    private val wildShapeProvider: (String) -> ActorDefinition? = { null },
    private val druidLevelProvider: (String) -> Int = { 0 },
    private val resourceSink: CombatResourceSink = CombatResourceSink { _, _ -> },
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

    /**
     * Messaggio dell'ultima regola violata; il livello Guided lo mostra senza
     * bloccare il tavolo.
     *
     * Non conserva la frase ma il modo di ricavarla: un avviso scritto in italiano
     * resterebbe italiano anche dopo un cambio di lingua, e ci resterebbe finche'
     * qualcosa non lo sostituisce. Cosi' invece segue la lingua come il resto
     * dello schermo, e leggerlo dentro una composizione la iscrive al cambio.
     */
    private var messageText by mutableStateOf<LocalizedText?>(null)

    val message: String? get() = messageText?.resolve(AppLocale.current)

    /** Assegna il messaggio, o lo cancella con `null`. */
    private fun say(text: LocalizedText?) {
        messageText = text
    }

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
        internal set

    /**
     * Esito persistente dell'ultima capacita' risolta.
     *
     * I numeri sulla mappa sono volutamente brevi; questa nota rende invece
     * inequivocabile che il clic sul bersaglio ha gia' applicato l'attacco, anche
     * quando il tiro manca e quindi i PF restano invariati.
     */
    var actionResolution by mutableStateOf<ActionResolution?>(null)
        internal set

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

    /** Capacita' sotto il puntatore; non viene salvata e non modifica il combattimento. */
    private var hoveredAbilityRange by mutableStateOf<AbilityRangePreview?>(null)

    /**
     * L'alone del movimento è una richiesta esplicita dell'utente.
     *
     * Non fa parte della sessione né delle regole: limita soltanto il disegno
     * dell'anteprima sulla mappa e riparte spento a ogni attore/turno.
     */
    var movementReachVisible by mutableStateOf(false)
        private set

    /**
     * Alone chiesto dal solo passaggio del mouse sul comando.
     *
     * Non e' una scelta dell'utente: sta fuori dall'interruttore perche' quando il
     * puntatore se ne va deve tornare esattamente com'era, acceso o spento che
     * fosse. Sul tocco resta sempre falso e non cambia nulla.
     */
    private var movementReachHover by mutableStateOf(false)

    /** L'alone da disegnare sulla mappa: acceso dall'interruttore o, di passaggio, dal puntatore. */
    val movementReachShown: Boolean
        get() = movementReachVisible || (movementReachHover && movementReachAvailable)

    /**
     * Portata da mostrare sulla mappa.
     *
     * Una capacita' gia' in mira ha precedenza sul semplice passaggio del mouse:
     * cosi' il suo raggio resta visibile mentre il puntatore lascia la scheda per
     * raggiungere il bersaglio o il punto d'impatto.
     */
    val abilityRangePreview: AbilityRangePreview?
        get() {
            areaTargeting?.let {
                return AbilityRangePreview(
                    combatantId = it.casterId,
                    abilityId = it.abilityId,
                    rangeFeet = it.rangeFeet,
                    targeting = true,
                )
            }
            singleTargeting?.let { targeting ->
                abilities(targeting.attackerId)
                    .firstOrNull { it.id() == targeting.abilityId }
                    ?.let { ability ->
                        return AbilityRangePreview(
                            combatantId = targeting.attackerId,
                            abilityId = ability.id(),
                            rangeFeet = ability.rangeFeet(),
                            targeting = true,
                        )
                    }
            }
            return hoveredAbilityRange
        }

    // --- proiezioni di sola lettura -------------------------------------------------

    val status: CombatStatus get() = state.status()
    val round: Int get() = state.round()
    val canUndo: Boolean get() = session.canUndo()
    val encounterId: String get() = state.encounterId()
    val displayName: String get() = encounterId

    /** Quando e' attiva, un doppio clic su un campo lo rende modificabile. */
    private var editModeState by mutableStateOf(false)

    var editMode: Boolean
        get() = editModeState
        set(value) {
            if (editModeState == value) return
            // Un runner gia' avviato ha pianificato sullo stato precedente. Le
            // correzioni manuali non possono convivere con quel piano: entrando
            // in Modifica il frammento CPU viene riavvolto e, all'uscita, sara'
            // ripianificato sul nuovo stato.
            if (value) enemyCpuPlayback?.pauseForEdit()
            editModeState = value
            if (!value) mapEditMode = false
        }

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

    /** Profilo tattico della CPU; viene applicato soltanto alle sessioni che lo dichiarano. */
    var enemyCpuDifficulty by mutableStateOf(EnemyCpuDifficulty.MEDIUM)
        private set

    /**
     * I salvataggi creati prima dell'introduzione della CPU restano manuali.
     * Alcuni documenti molto vecchi non conservano neppure lo schieramento: un
     * fallback automatico prenderebbe quindi il controllo di tutti i combattenti.
     */
    var enemyCpuEnabled by mutableStateOf(false)
        private set

    var enemyCpuBusy by mutableStateOf(false)
        internal set

    /**
     * Ritmo con cui il tavolo vede giocare il turno nemico.
     *
     * Non tocca le regole ne' le decisioni: cambia soltanto la pausa fra un
     * comando e il successivo. Vive accanto alla difficolta' perche' e' la stessa
     * scelta di presentazione, e viene salvata con la sessione.
     */
    var enemyCpuSpeed by mutableStateOf(EnemyCpuSpeed.NORMAL)

    /** Nemico che sta eseguendo il comando mostrato in questo istante. */
    var enemyCpuActingCombatantId by mutableStateOf<String?>(null)
        internal set

    /** Ultimo comando CPU in parole, per la fascia in cima alla schermata. */
    var enemyCpuActionLabel by mutableStateOf<String?>(null)
        internal set

    /**
     * Firma trasportabile del gruppo corrente. A differenza di [enemyCpuTurnKey]
     * non contiene [sessionGeneration], quindi puo' sopravvivere a save/load e al
     * recovery del workspace senza far ripetere un batch gia' concluso.
     */
    internal var suppressedEnemyCpuTurnSignature by mutableStateOf<String?>(null)

    /** Batch nemico gia' eseguito in un gruppo simultaneo che deve restare al party. */
    internal var completedEnemyCpuTurnSignature by mutableStateOf<String?>(null)

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

    internal val enemyCpuTurnSignature: String?
        get() {
            if (status != CombatStatus.ACTIVE || state.partyCombatantIds().isEmpty()) return null
            // Il gruppo strutturale conserva anche chi raggiunge 0 PF durante il
            // batch. Usarlo rende la firma stabile mentre cambia l'insieme dei vivi.
            val group = turnGroups.getOrNull(state.turnIndex()).orEmpty()
            val enemies = group.filterNot(::isParty)
            if (enemies.isEmpty()) return null
            val hasActiveEnemy = activeCombatantIds.any { !isParty(it) }
            // Un gruppo solo nemico completamente morto deve comunque essere
            // consegnato al core, che lo salta e avanza. In un gruppo misto con
            // party vivo e soli nemici morti, invece, resta soltanto il giocatore.
            if (!hasActiveEnemy && activeCombatantIds.isNotEmpty()) return null
            val structuralGroup = group.joinToString(",") { combatantId ->
                "${if (isParty(combatantId)) "P" else "E"}:${combatantId.length}:$combatantId"
            }
            return "${state.round()}:${state.turnIndex()}:$structuralGroup"
        }

    /** Identita' runtime del turno CPU: le singole azioni non la cambiano. */
    val enemyCpuTurnKey: String?
        get() = enemyCpuTurnSignature?.let { "$sessionGeneration:$it" }

    val enemyCpuTurnSuppressed: Boolean
        get() = enemyCpuTurnSignature?.let { it == suppressedEnemyCpuTurnSignature } == true

    /** Vero dopo che i nemici di un gruppo misto hanno agito e tocca ancora al party. */
    val enemyCpuBatchCompleted: Boolean
        get() = enemyCpuTurnSignature?.let { it == completedEnemyCpuTurnSignature } == true

    /**
     * Il gruppo non puo' essere chiuso finche' il breve scheduler non ha dato ai
     * nemici la loro parte di turno. Gli alleati restano comunque utilizzabili.
     */
    val enemyCpuBatchPending: Boolean
        get() = enemyCpuEnabled && !editMode && enemyCpuTurnSignature != null &&
            !enemyCpuTurnSuppressed && !enemyCpuBatchCompleted

    /** La CPU possiede soltanto gli attori nemici del gruppo, mai gli alleati in parita'. */
    fun enemyCpuControlsActor(combatantId: String): Boolean =
        enemyCpuEnabled && !editMode && !enemyCpuTurnSuppressed &&
            combatantId in activeCombatantIds && !isParty(combatantId)

    val enemyCpuControlsCurrentTurn: Boolean
        get() = activeActorId?.let(::enemyCpuControlsActor) == true

    /** La UI usa questo guard prima del breve ritardo che rende leggibile il cambio turno. */
    val shouldScheduleEnemyCpu: Boolean
        get() = !enemyCpuBusy && enemyCpuBatchPending

    fun resumeEnemyCpuTurn() {
        // L'avviso che invitava a riprendere ha esaurito il suo compito: lasciarlo
        // a schermo direbbe che l'automazione e' sospesa proprio mentre riparte.
        say(null)
        suppressedEnemyCpuTurnSignature = null
        completedEnemyCpuTurnSignature = null
    }

    /** Esegue l'intero gruppo nemico corrente senza pause, in un solo passaggio. */
    fun playEnemyCpuTurn() {
        val playback = beginEnemyCpuTurn() ?: return
        while (playback.advance()) {
            // Nessuna attesa: al tavolo arriva soltanto lo stato finale.
        }
        playback.complete()
    }

    /**
     * Gioca lo stesso turno lasciando vedere un comando alla volta.
     *
     * E' il percorso dell'interfaccia. La CPU incatena spesso spostamento e
     * attacchi nello stesso turno: risolti tutti nello stesso fotogramma, i
     * segnaposto sembrano teletrasportarsi e i numeri compaiono insieme. La pausa
     * fra un comando e il successivo e' una preferenza di presentazione,
     * [enemyCpuSpeed], riletta a ogni passo: cambiarla mentre la CPU gioca vale
     * gia' dal comando dopo.
     */
    suspend fun playEnemyCpuTurnPaced() {
        val playback = beginEnemyCpuTurn() ?: return
        var interrupted: CancellationException? = null
        try {
            while (playback.advance()) {
                val pause = enemyCpuSpeed.stepDelayMillis
                if (interrupted != null || pause <= 0L) continue
                try {
                    delay(pause)
                } catch (cancelled: CancellationException) {
                    // Chi annulla l'attesa (cambio sessione, chiusura della finestra)
                    // non deve lasciare il gruppo nemico a meta' turno: si smette di
                    // aspettare, non di giocare.
                    interrupted = cancelled
                }
            }
        } finally {
            playback.complete()
        }
        interrupted?.let { throw it }
    }

    /** Turno CPU in corso, se ce n'e' uno: serve a chi deve chiuderlo prima di salvare. */
    internal var enemyCpuPlayback: EnemyCpuTurnPlayback? = null

    /**
     * Chiude subito un turno CPU rimasto fra una pausa e l'altra.
     *
     * Chi sta per salvare in modo definitivo - la chiusura dell'applicazione - non
     * puo' aspettare il ritmo: meglio concludere il gruppo nemico e fotografare un
     * turno intero, che e' anche quello che il tavolo si ritrovera' riaprendo.
     */
    fun settleEnemyCpuTurn() {
        enemyCpuPlayback?.complete()
    }

    private fun beginEnemyCpuTurn(): EnemyCpuTurnPlayback? {
        val scheduledSignature = enemyCpuTurnSignature ?: return null
        if (!shouldScheduleEnemyCpu) return null
        return EnemyCpuTurnPlayback(this, scheduledSignature).also { enemyCpuPlayback = it }
    }

    /**
     * Porta scheda ed evidenza sull'attore indicato durante un turno CPU.
     *
     * Il bersaglio scelto dal tavolo sopravvive soltanto quando il turno torna al
     * gruppo: mentre agisce un nemico non ha piu' senso tenerlo puntato.
     */
    internal fun followEnemyCpuActor(combatantId: String, keepTarget: Boolean) {
        activeActorSelection = combatantId
        inspectionSelection = combatantId
        if (keepTarget) targetSelection = sanitizeTarget(targetSelection)
    }

    var simultaneousTies: Boolean
        get() = state.simultaneousTies()
        set(value) {
            if (status != CombatStatus.DRAFT && status != CombatStatus.READY) {
                say { it.battle.tieHandlingLocked }
            } else {
                command { session.setSimultaneousTies(value) }
            }
        }

    fun selectActiveActor(combatantId: String) {
        if (combatantId !in activeCombatantIds) {
            say { it.battle.combatantNotInTurn }
            return
        }
        if (activeActorId != combatantId) {
            movementReachVisible = false
            singleTargeting = null
            areaTargeting = null
            pendingArea = null
            targetSelection = null
        }
        activeActorSelection = combatantId
        inspectionSelection = combatantId
        targetSelection = sanitizeTarget(targetSelection)
        say(null)
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
            if (activeActorId != combatantId) movementReachVisible = false
            activeActorSelection = combatantId
        }
        inspectionSelection = combatantId
        say(null)
    }

    /** Vero soltanto quando le capacita' mostrate appartengono all'attore che puo' agire ora. */
    fun canUseAbilitiesOf(combatantId: String): Boolean =
        status == CombatStatus.ACTIVE &&
            !enemyCpuBatchPending &&
            !enemyCpuControlsActor(combatantId) &&
            combatantId == activeActorId &&
            combatant(combatantId)?.let { !it.defeated() && !it.dead() } == true

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
                say { it.battle.aimingAreaHint }
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

    /** Tratto permanente secondo il Compendio, o secondo la definizione se non la conosce. */
    private fun AbilityDefinition.isPassiveNow(): Boolean = passiveProvider(id()) ?: passive()

    /** Le capacita' giocabili: quelle che si spendono nel turno. */
    fun activeAbilities(id: String): List<AbilityDefinition> =
        abilities(id).filterNot { it.isPassiveNow() }

    /**
     * I tratti permanenti — padronanza d'armi, Incantesimi, talenti — che valgono
     * sempre e non si attivano: restano fuori dalle schede delle capacita'.
     */
    fun passiveAbilities(id: String): List<AbilityDefinition> =
        abilities(id).filter { it.isPassiveNow() }

    /** Disponibilità effettiva: budget del turno, risorsa limitata e vincoli dell'effetto. */
    fun canAffordAbility(combatantId: String, ability: AbilityDefinition): Boolean {
        if (!canUseAbilitiesOf(combatantId)) return false
        val budget = budget(combatantId) ?: return false
        val turnCostAvailable = when (ability.activationCost()) {
            app.d6d.domain.combat.ActivationCost.ACTION ->
                if (
                    !ability.spellOrCantrip() &&
                    ability.resolutionMethod() == ResolutionMethod.ATTACK_ROLL &&
                    budget.attackActionInProgress()
                ) {
                    budget.attacksRemaining() > 0
                } else {
                    budget.canUseAction(ability.spellOrCantrip())
                }
            app.d6d.domain.combat.ActivationCost.BONUS_ACTION -> budget.bonusActionAvailable()
            app.d6d.domain.combat.ActivationCost.REACTION -> budget.reactionAvailable()
            app.d6d.domain.combat.ActivationCost.NONE -> true
            app.d6d.domain.combat.ActivationCost.LEGENDARY_ACTION -> false
        }
        if (!turnCostAvailable) return false
        if (
            ability.effect() == AbilityEffect.GRANT_NON_MAGIC_ACTION &&
            budget.actionSurgeUsedThisTurn()
        ) return false
        if (ability.resourceCost() == 0) return true
        if (
            SpellSlotResourceId.parse(ability.resourceId()).isPresent &&
            budget.spellSlotSpentThisTurn()
        ) return false
        return combatant(combatantId)?.resources()
            ?.firstOrNull { it.id() == ability.resourceId() }
            ?.remaining()
            ?.let { it >= ability.resourceCost() }
            ?: false
    }

    fun abilityResourceLabel(combatantId: String, ability: AbilityDefinition): String? {
        if (ability.resourceCost() == 0) return null
        val resource = combatant(combatantId)?.resources()
            ?.firstOrNull { it.id() == ability.resourceId() }
            ?: return "0/?"
        return "${resource.remaining()}/${resource.maximum()}"
    }

    fun isParty(id: String): Boolean = id in state.partyCombatantIds()

    fun initiativeScore(id: String): Int? = state.initiativeScores()[id]

    /** Almeno un combattente puo' ancora ricevere un turno. */
    val hasStandingCombatants: Boolean
        get() = state.combatants().values.any { !it.defeated() && !it.dead() }

    /** Bersaglio corrente, ripiegando sul primo avversario ancora in piedi. */
    fun effectiveTargetId(): String? {
        val active = activeActorId ?: return null
        val chosen = sanitizeTarget(targetSelection)
        if (chosen != null) return chosen
        val hostile = if (isParty(active)) enemyIds else partyIds
        return hostile.firstOrNull { candidate ->
            candidate != active && combatant(candidate)?.let { !it.defeated() && !it.dead() } == true
        }
    }

    // --- comandi ---------------------------------------------------------------------

    fun start() = command { session.start() }

    fun addRosterCombatant(actor: ActorDefinition, party: Boolean): String? {
        if (!editMode) {
            say { it.battle.enableEditToAddCombatants }
            return null
        }
        val (instanceId, copyNumber) = nextRosterInstanceId(actor.id())
        val encounterActor = if (copyNumber == 1) actor else actor.withEncounterName("${actor.name()} $copyNumber")
        val revisionBefore = state.revision()
        command { session.addCombatantToEncounter(instanceId, encounterActor, party) }
        if (state.revision() == revisionBefore) return null
        footprints = footprints + (instanceId to footprintProvider(actor.id()).coerceIn(1, 4))
        inspectCombatant(instanceId)
        return instanceId
    }

    fun attack(abilityId: String) {
        val attacker = activeCombatantId ?: return
        val target = effectiveTargetId() ?: run {
            say { it.battle.noValidTarget }
            return
        }
        attack(attacker, target, abilityId)
    }

    /** Esegue un attacco sul bersaglio esplicito, senza alcun ripiego automatico. */
    private fun attack(attacker: String, target: String, abilityId: String) {
        val definitionId = combatant(attacker)?.snapshot()?.definitionId() ?: return
        val ability = abilities(attacker).firstOrNull { it.id() == abilityId }
        val consumesResource = ability?.resourceCost()?.let { it > 0 } == true
        val undoEffect = if (consumesResource) {
            UndoEffect.ResourceChange(attacker, definitionId)
        } else {
            UndoEffect.None
        }
        command(undoEffect) {
            val abilityName = ability?.name() ?: abilityId
            val targetName = name(target)
            val targetArmorClass = combatant(target)?.snapshot()?.armorClass()
            val result = session.attack(AttackRequest.digital(attacker, target, abilityId, rollMode))
            if (consumesResource) {
                persistCombatResourcesOrRollback(
                    attacker,
                    definitionId,
                    AppLocale.current.battle.attackResourceNotSaved,
                )
            }
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
                        text = AppLocale.current.battle.attackHitSentence(
                            ability = abilityName,
                            critical = critical,
                            target = targetName,
                            damage = applied.totalAdjustedDamage(),
                            hitPointsLeft = applied.targetHitPointsAfter(),
                            roll = result.attackRoll().total(),
                            armorClass = targetArmorClass,
                        ),
                        isHit = true,
                    )
                    if (applied.concentrationCheck().isPresent &&
                        !applied.concentrationCheck().get().maintained()
                    ) {
                        push(target, floatInfo(AppLocale.current.battle.concentrationLost))
                    }
                }

                else -> {
                    push(target, FloatingNumber(++floatSequence, AppLocale.current.battle.missed, FloatKind.MISS))
                    actionResolution = ActionResolution(
                        text = AppLocale.current.battle.attackMissSentence(
                            ability = abilityName,
                            target = targetName,
                            roll = result.attackRoll().total(),
                            armorClass = targetArmorClass,
                        ),
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
            say { it.battle.noActingCombatant }
            return
        }
        if (inspectedCombatantId != attacker || !canUseAbilitiesOf(attacker)) {
            say { it.battle.inspectedSheetReadOnly }
            return
        }
        val ability = abilities(attacker).firstOrNull { it.id() == abilityId } ?: run {
            say { it.battle.abilityNotFound }
            return
        }
        val wildShape = wildShapeProvider(abilityId)
        if (wildShape != null) {
            activateWildShape(attacker, ability, wildShape)
            return
        }
        if (ability.healing() != null && ability.healing().target() == HealingTarget.SELF) {
            useHealingAbility(attacker, attacker, ability)
            return
        }
        if (ability.effect() != AbilityEffect.NONE) {
            activateAbility(attacker, ability)
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
        say(null)
        if (ability.isArea) {
            beginAreaTargeting(abilityId)
            return
        }

        areaTargeting = null
        pendingArea = null
        targetSelection = null
        singleTargeting = SingleTargeting(ability.id(), attacker, ability.name())
    }

    /** Assume immediatamente una forma conosciuta, consumando Azione bonus e uso. */
    private fun activateWildShape(
        combatantId: String,
        ability: AbilityDefinition,
        beast: ActorDefinition,
    ) {
        val definitionId = combatant(combatantId)?.snapshot()?.definitionId() ?: return
        val druid = actorProvider(definitionId) ?: run {
            say { it.battle.druidSheetUnavailable }
            return
        }
        val druidLevel = druidLevelProvider(definitionId)
        if (druidLevel < 2) {
            say { it.battle.wildShapeNeedsTwoLevels }
            return
        }
        val transformed = CombatantSnapshot.wildShape(combatantId, druid, beast)
        var activated = false
        command(UndoEffect.ResourceChange(combatantId, definitionId)) {
            session.transformCombatant(combatantId, ability.id(), transformed, druidLevel)
            try {
                resourceSink.onChanged(
                    definitionId,
                    session.currentState().combatant(combatantId).resources(),
                )
            } catch (failure: Exception) {
                session.undo()
                throw IllegalStateException(
                    failure.message ?: AppLocale.current.battle.wildShapeNotSaved,
                    failure,
                )
            }
            activated = true
        }
        if (activated) {
            actionResolution = ActionResolution(
                text = AppLocale.current.battle.wildShapeAssumed(druid.name(), beast.name()),
                isHit = true,
            )
        }
    }

    /** Attiva una capacità automatica che non richiede bersaglio, come Azione Impetuosa. */
    private fun activateAbility(combatantId: String, ability: AbilityDefinition) {
        val definitionId = combatant(combatantId)?.snapshot()?.definitionId() ?: return
        val consumesResource = ability.resourceCost() > 0
        val undoEffect = if (consumesResource) {
            UndoEffect.ResourceChange(combatantId, definitionId)
        } else {
            UndoEffect.None
        }
        var activated = false
        command(undoEffect) {
            session.activateAbility(combatantId, ability.id())
            if (consumesResource) {
                persistCombatResourcesOrRollback(
                    combatantId,
                    definitionId,
                    AppLocale.current.battle.resourceNotSaved,
                )
            }
            activated = true
        }
        if (activated) {
            actionResolution = ActionResolution(
                text = AppLocale.current.battle.extraActionGranted(ability.name()),
                isHit = true,
            )
        }
    }

    /** Annulla la scelta del bersaglio di una capacita' singola. */
    fun cancelSingleTargeting() {
        singleTargeting = null
        say(null)
    }

    /** Conferma il bersaglio senza mai sostituirlo con il primo nemico disponibile. */
    private fun confirmSingleTarget(targetId: String) {
        val targeting = singleTargeting ?: return
        if (targeting.attackerId !in activeCombatantIds || activeActorId != targeting.attackerId) {
            singleTargeting = null
            say { it.battle.turnChangedReselect }
            return
        }
        val ability = abilities(targeting.attackerId)
            .firstOrNull { it.id() == targeting.abilityId }
            ?: run {
                singleTargeting = null
                say { it.battle.abilityNotFound }
                return
        }
        if (ability.healing() != null) {
            val target = combatant(targetId)
            val healing = ability.healing()
            val self = targeting.attackerId == targetId
            if (target == null) {
                say { it.battle.invalidTarget }
                return
            }
            if (target.dead()) {
                say { it.battle.healingCannotRaiseDead }
                return
            }
            if (isParty(targeting.attackerId) != isParty(targetId)) {
                say { it.battle.healingOwnSideOnly }
                return
            }
            if (healing.target() == HealingTarget.SELF && !self) {
                say { it.battle.healingSelfOnly }
                return
            }
            if (healing.target() == HealingTarget.ALLY && self) {
                say { it.battle.healingAllyOnly }
                return
            }
            targetSelection = targetId
            val revisionBefore = state.revision()
            useHealingAbility(targeting.attackerId, targetId, ability)
            if (state.revision() != revisionBefore) singleTargeting = null
            return
        }
        if (sanitizeTargetFor(targeting.attackerId, targetId) == null) {
            say(
                when {
                    targetId == targeting.attackerId -> LocalizedText { it.battle.cannotTargetSelf }
                    combatant(targetId)?.let { it.defeated() || it.dead() } == true ->
                        LocalizedText { it.battle.targetAlreadyDown }
                    else -> LocalizedText { it.battle.invalidTarget }
                },
            )
            return
        }

        targetSelection = targetId
        val revisionBefore = state.revision()
        attack(targeting.attackerId, targetId, targeting.abilityId)
        if (state.revision() != revisionBefore) {
            singleTargeting = null
        }
    }

    /** Cura strutturata: costo, risorsa, gittata e formula vengono risolti dal motore. */
    private fun useHealingAbility(
        healerId: String,
        targetId: String,
        ability: AbilityDefinition,
    ) {
        val definitionId = combatant(healerId)?.snapshot()?.definitionId() ?: return
        val consumesResource = ability.resourceCost() > 0
        val undoEffect = if (consumesResource) {
            UndoEffect.ResourceChange(healerId, definitionId)
        } else {
            UndoEffect.None
        }
        var restored: Int? = null
        command(undoEffect) {
            val healed = session.useHealingAbility(healerId, targetId, ability.id())
            if (consumesResource) {
                persistCombatResourcesOrRollback(
                    healerId,
                    definitionId,
                    AppLocale.current.battle.healingNotSaved,
                )
            }
            restored = healed
        }
        restored?.let { amount ->
            if (amount > 0) {
                push(targetId, FloatingNumber(++floatSequence, "+$amount", FloatKind.HEAL))
            }
            actionResolution = ActionResolution(
                text = AppLocale.current.battle.healingApplied(ability.name(), name(targetId), amount),
                isHit = true,
            )
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
            say { it.battle.noActingCombatant }
            return
        }
        if (inspectedCombatantId != caster || !canUseAbilitiesOf(caster)) {
            say { it.battle.inspectedSheetReadOnly }
            return
        }
        val ability = abilities(caster).firstOrNull { it.id() == abilityId } ?: return
        if (!ability.isArea) return
        singleTargeting = null
        pendingArea = null
        val abilityName = ability.name()
        say { it.battle.aimAbility(abilityName) }
        areaTargeting = AreaTargeting(abilityId, caster, ability.name(), ability.areaRadiusFeet(), ability.rangeFeet())
    }

    /** Abbandona la mira o la risoluzione manuale in corso. */
    fun cancelAreaTargeting() {
        areaTargeting = null
        pendingArea = null
        say(null)
    }

    /**
     * Fa detonare l'area in mira sulla casella scelta.
     *
     * In gioco normale il motore tira i tiri salvezza e applica subito i danni. Con
     * la modalità modifica attiva prepara invece la risoluzione manuale: elenca chi
     * e' nell'area e lascia decidere al tavolo chi supera il tiro.
     */
    fun resolveAreaAt(column: Int, row: Int) {
        if (rejectMutationWhileEnemyCpuPending()) return
        val targeting = areaTargeting ?: return
        val center = GridPosition(column, row)
        if (editMode) {
            val dc = combatant(targeting.casterId)?.snapshot()?.spellSaveDc() ?: 0
            val ids = try {
                session.areaTargets(targeting.casterId, center, targeting.abilityId)
            } catch (failure: RuntimeException) {
                say(ruleMessage(failure.message))
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
            say(
                if (ids.isEmpty()) {
                    LocalizedText { it.battle.noCreatureInArea }
                } else {
                    LocalizedText { it.battle.markSavesThenApply }
                },
            )
        } else {
            val definitionId = combatant(targeting.casterId)?.snapshot()?.definitionId() ?: return
            val consumesResource = abilities(targeting.casterId)
                .firstOrNull { it.id() == targeting.abilityId }
                ?.resourceCost()
                ?.let { it > 0 } == true
            val undoEffect = if (consumesResource) {
                UndoEffect.ResourceChange(targeting.casterId, definitionId)
            } else {
                UndoEffect.None
            }
            command(undoEffect) {
                val result = session.castArea(targeting.casterId, center, targeting.abilityId)
                if (consumesResource) {
                    persistCombatResourcesOrRollback(
                        targeting.casterId,
                        definitionId,
                        AppLocale.current.battle.areaResourceNotSaved,
                    )
                }
                pushAreaResult(result)
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
        if (rejectMutationWhileEnemyCpuPending()) return
        val pending = pendingArea ?: return
        val definitionId = combatant(pending.casterId)?.snapshot()?.definitionId() ?: return
        val consumesResource = abilities(pending.casterId)
            .firstOrNull { it.id() == pending.abilityId }
            ?.resourceCost()
            ?.let { it > 0 } == true
        val undoEffect = if (consumesResource) {
            UndoEffect.ResourceChange(pending.casterId, definitionId)
        } else {
            UndoEffect.None
        }
        command(undoEffect) {
            val saved = pending.targets.associate { it.combatantId to it.saved }
            val result = session.castAreaManual(pending.casterId, pending.center, pending.abilityId, saved)
            if (consumesResource) {
                persistCombatResourcesOrRollback(
                    pending.casterId,
                    definitionId,
                    AppLocale.current.battle.areaResourceNotSaved,
                )
            }
            pushAreaResult(result)
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
                push(outcome.targetId(), floatInfo(AppLocale.current.battle.floatSaved))
            }
        }
    }

    fun endTurn() {
        if (enemyCpuBatchPending) {
            say { it.battle.waitForCpuTurn }
            return
        }
        if (activeActorId?.let(::enemyCpuControlsActor) == true) {
            say { it.battle.combatantIsCpuControlled }
            return
        }
        command { session.endTurn() }
    }

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
        if (rejectMutationWhileEnemyCpuPending()) return
        say(null)
        actionResolution = null
        val effect = undoEffects.lastOrNull() ?: UndoEffect.None
        try {
            if (effect is UndoEffect.EnemyCpuBatch) {
                // Il confine di Undo, non la revisione corrente: ogni Undo ne
                // assegna comunque una nuova, mentre il confine resta sopra
                // endingRevision finche' un solo comando inserito sopra il batch
                // e' ancora da rimuovere.
                if (session.nextUndoRevision() >= effect.endingRevision) {
                    // Il checkpoint CPU resta al suo posto: questo Undo rimuove
                    // soltanto la revisione piu' recente inserita sopra di esso,
                    // una per clic. Il batch torna annullabile in un colpo solo
                    // quando anche l'ultima e' stata tolta.
                    if (!session.undo()) {
                        say { it.battle.editAfterCpuBatchNotUndoable }
                        return
                    }
                    sync()
                    say { it.battle.editAfterCpuBatchUndone }
                    return
                }
                if (!session.undoTo(effect.startingRevision)) {
                    throw IllegalStateException(AppLocale.current.battle.cpuBatchNotFullyUndoable)
                }
            } else {
                if (!session.undo()) {
                    say { it.battle.nothingToUndo }
                    return
                }
            }
            if (undoEffects.isNotEmpty()) undoEffects.removeLast()
            sync()
            when (effect) {
                is UndoEffect.CombatantEdit -> {
                    combatant(effect.combatantId)?.snapshot()?.let { restored ->
                        editSink.onResynced(effect.definitionId, restored)
                    }
                }
                is UndoEffect.ResourceChange -> {
                    if (!hasClonedInstances(effect.definitionId)) {
                        combatant(effect.combatantId)?.resources()?.let { restored ->
                            resourceSink.onChanged(effect.definitionId, restored)
                        }
                    }
                }
                is UndoEffect.EnemyCpuBatch -> {
                    completedEnemyCpuTurnSignature = null
                    suppressedEnemyCpuTurnSignature = enemyCpuTurnSignature ?: effect.turnSignature
                    val resourceFailure = effect.resourceConsumers.entries.firstNotNullOfOrNull {
                        (combatantId, definitionId) ->
                        val restored = combatant(combatantId)?.resources() ?: return@firstNotNullOfOrNull null
                        runCatching { resourceSink.onChanged(definitionId, restored) }
                            .exceptionOrNull()
                    }
                    say(
                        if (resourceFailure == null) {
                            LocalizedText { it.battle.cpuBatchUndone }
                        } else {
                            LocalizedText {
                                it.battle.cpuBatchUndoneResourcesOff(
                                    resourceFailure.message ?: it.battle.unknownError,
                                )
                            }
                        },
                    )
                }
                UndoEffect.None -> Unit
            }
        } catch (failure: CombatRuleException) {
            say(ruleMessage(failure.message))
        } catch (failure: IllegalArgumentException) {
            say(ruleMessage(failure.message))
        } catch (failure: IllegalStateException) {
            say(ruleMessage(failure.message))
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
        val words = AppLocale.current.battle
        val label = when {
            result.dead() -> words.dead
            result.stable() -> words.stable
            else -> words.deathSaves(result.successes(), result.failures())
        }
        push(targetId, floatInfo(label))
    }

    /**
     * Prova di caratteristica dichiarata dal tavolo.
     *
     * Usa la modalità d20 già scelta nella barra dei comandi; il motore aggiunge
     * Sfinimento e l'eventuale Svantaggio imposto dall'armatura.
     */
    fun rollAbilityCheck(combatantId: String, ability: SaveAbility, modifier: Int) = command {
        session.rollAbilityCheck(
            combatantId,
            ability,
            modifier,
            D20RollInput.digital(rollMode),
        )
    }

    fun stabilize(targetId: String) = command {
        session.stabilize(targetId, "manuale")
        push(targetId, floatInfo(AppLocale.current.battle.stable))
    }

    fun setExhaustion(targetId: String, level: Int) = command {
        session.setExhaustion(targetId, level)
        push(targetId, floatInfo(AppLocale.current.battle.exhaustionLevel(level)))
    }

    fun pause() = command { session.pause() }

    fun resume() = command { session.resume() }

    fun resolve(outcome: String = AppLocale.current.battle.resolvedByTable) = command { session.resolve(outcome) }

    fun heal(targetId: String, amount: Int) = command {
        val healed = session.heal(targetId, amount)
        if (healed > 0) push(targetId, FloatingNumber(++floatSequence, "+$healed", FloatKind.HEAL))
    }

    /** Correzione dei PF attuali disponibile solo ai controlli della modalità Modifica. */
    fun setCurrentHitPoints(combatantId: String, value: Int) {
        val maximum = combatant(combatantId)?.snapshot()?.maxHitPoints() ?: return
        command { session.setCurrentHitPoints(combatantId, value.coerceIn(0, maximum)) }
    }

    fun grantTemporary(targetId: String, amount: Int) = command {
        val granted = session.grantTemporaryHitPoints(targetId, amount)
        if (granted > 0) {
            push(targetId, FloatingNumber(
                    ++floatSequence,
                    AppLocale.current.battle.temporaryHitPointsGained(granted),
                    FloatKind.TEMPORARY,
                ))
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
        push(targetId, floatInfo(type.label(AppLocale.current.language)))
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
                    failure.message ?: AppLocale.current.battle.correctionNotSaved,
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

    /** L'attore corrente ha una posizione e almeno una casella ancora percorribile. */
    val movementReachAvailable: Boolean
        get() {
            val active = activeActorId ?: return false
            return status == CombatStatus.ACTIVE &&
                !enemyCpuBatchPending &&
                !enemyCpuControlsCurrentTurn &&
                placementOf(active) != null &&
                movementSquaresRemaining(active) > 0
        }

    /** Accende o spegne l'anteprima senza inviare alcun comando al motore. */
    fun toggleMovementReach() {
        movementReachVisible = movementReachAvailable && !movementReachVisible
    }

    /**
     * Anticipa l'alone mentre il puntatore sosta sul comando.
     *
     * Guardare quanto si arriva e' una domanda continua durante il turno, e
     * doverla fare accendendo e spegnendo un interruttore costa due clic per una
     * risposta che dura un istante. Il passaggio del mouse la mostra e basta; il
     * clic resta per quando la si vuole tenere ferma.
     */
    fun setMovementReachHovered(hovered: Boolean) {
        movementReachHover = hovered
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

    fun configureMap(columns: Int, rows: Int, feetPerSquare: Int) {
        val placementsBefore = state.combatants().keys.mapNotNull { combatantId ->
            placementOf(combatantId)?.let { combatantId to it }
        }.toMap()
        val revisionBefore = state.revision()
        command {
            session.configureMap(
                MapGrid(
                    columns.coerceAtLeast(1),
                    rows.coerceAtLeast(1),
                    feetPerSquare.coerceAtLeast(1),
                ),
            )
        }
        if (state.revision() != revisionBefore) {
            // Una griglia più piccola elimina automaticamente i token rimasti
            // fuori bordo. Anche in quel caso la loro taglia appartiene a questa
            // sessione e non deve tornare a dipendere dal template condiviso.
            val removedFootprints = placementsBefore
                .filterKeys { combatantId -> placementOf(combatantId) == null }
                .mapValues { (_, placement) -> placement.squaresPerSide() }
            if (removedFootprints.isNotEmpty()) footprints = footprints + removedFootprints
        }
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

    fun move(combatantId: String, column: Int, row: Int) {
        if (enemyCpuBatchPending) {
            say { it.battle.waitForCpuTurn }
            return
        }
        if (enemyCpuControlsActor(combatantId)) {
            say { it.battle.combatantIsCpuControlled }
            return
        }
        command {
            val feet = session.moveCombatant(combatantId, GridPosition(column, row))
            push(
                combatantId,
                FloatingNumber(++floatSequence, distanceLabel(feet, AppLocale.language), FloatKind.INFO),
            )
        }
    }

    fun moveActive(column: Int, row: Int) {
        if (enemyCpuBatchPending) {
            say { it.battle.waitForCpuTurn }
            return
        }
        if (enemyCpuControlsCurrentTurn) {
            say { it.battle.cpuControlsThisTurn }
            return
        }
        val actor = activeActorId ?: run {
            say { it.battle.noActiveActor }
            return
        }
        move(actor, column, row)
    }

    fun removeFromMap(combatantId: String) {
        val existing = placementOf(combatantId)
        val revisionBefore = state.revision()
        command { session.removeFromMap(combatantId) }
        // Una volta tolto dalla mappa non possiamo più leggere l'ingombro dal
        // TokenPlacement. Lo conserviamo nella presentation della sessione,
        // così modifiche successive al template nel Compendio non cambiano il
        // token quando questa stessa partita lo rimetterà sulla griglia.
        if (existing != null && state.revision() != revisionBefore) {
            footprints = footprints + (combatantId to existing.squaresPerSide())
        }
    }

    /**
     * Dispone chi non e' ancora sulla mappa in due schieramenti contrapposti.
     *
     * Serve solo a partire in fretta: e' una comodita' di preparazione, non una
     * regola, e ogni segnaposto resta poi spostabile a mano. La disposizione e' la
     * stessa della procedura Nuova partita, cosi' una mappa completata al tavolo
     * non si distingue da una preparata dalla procedura guidata.
     *
     * Chi e' gia' sulla mappa non viene toccato, ma il suo ingombro conta: il
     * piazzatore aggira i segnaposti presenti invece di sovrapporvisi.
     */
    fun autoPlaceMissing(squaresFor: (String) -> Int = { 1 }) {
        if (!mapConfigured) {
            say { it.battle.configureGridFirst }
            return
        }
        val grid = battleMap.grid()
        val pending = (partyIds + enemyIds)
            .filterNot { battleMap.isPlaced(it) }
            .map { PendingToken(it, isParty(it), squaresFor(it).coerceIn(1, 4)) }
        if (pending.isEmpty()) {
            say { it.battle.allTokensAlreadyPlaced }
            return
        }
        val occupied = battleMap.orderedPlacements().flatMap { it.occupiedSquares() }.toSet()
        val placements = arrangeTokens(grid, pending, occupied) ?: run {
            say(gridTooSmallMessage(grid))
            return
        }
        val sideOf = pending.associate { it.combatantId to it.squaresPerSide }
        placements.forEach { (combatantId, origin) ->
            place(combatantId, origin.column(), origin.row(), sideOf.getValue(combatantId))
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
        if (enemyCpuEnabled) {
            put("enemyCpuDifficulty", enemyCpuDifficulty.name)
            put("enemyCpuSpeed", enemyCpuSpeed.name)
        }
        if (enemyCpuTurnSuppressed) {
            enemyCpuTurnSignature?.let { put("enemyCpuSuppressedTurn", it) }
        }
        if (enemyCpuBatchCompleted) {
            enemyCpuTurnSignature?.let { put("enemyCpuCompletedTurn", it) }
        }
        put("editMode", editMode.toString())
        put("mapEditMode", mapEditMode.toString())
        if (footprints.isNotEmpty()) {
            put("footprints", footprints.entries.joinToString(",") { "${it.key}=${it.value}" })
        }
    }

    /**
     * Crea una sessione immutabile rispetto al documento aperto.
     *
     * `CombatSession.currentState()` e `auditTrail()` sono singolarmente
     * sincronizzati, ma il writer ha bisogno che provengano dallo stesso istante:
     * il lock esterno impedisce a un comando di inserirsi fra le due letture.
     * Il piccolo retry copre anche un eventuale [adopt] concorrente.
     */
    internal fun persistenceSnapshot(): BattlePersistenceSnapshot {
        while (true) {
            val observedSession = session
            val observedGeneration = sessionGeneration
            synchronized(observedSession) {
                if (session !== observedSession || sessionGeneration != observedGeneration) {
                    return@synchronized
                }
                val capturedState = observedSession.currentState()
                val capturedAudit = observedSession.auditTrail()
                val capturedPresentation = presentationState().toMap()
                if (session !== observedSession || sessionGeneration != observedGeneration) {
                    return@synchronized
                }
                return BattlePersistenceSnapshot(
                    session = CombatSession.restore(capturedState, capturedAudit),
                    state = capturedState,
                    presentation = capturedPresentation,
                    generation = observedGeneration,
                )
            }
        }
    }

    /** Sostituisce il combattimento con quello caricato dal disco. */
    fun adopt(loaded: CombatSession, presentation: Map<String, String>) {
        session = loaded
        sessionGeneration++
        // Il vecchio coroutine puo' ancora uscire dalla propria pausa. Staccarlo
        // ora, insieme al controllo d'identita' nel playback, gli impedisce di
        // azzerare busy o il riferimento di un turno appartenente alla sessione
        // appena adottata.
        enemyCpuPlayback = null
        undoEffects.clear()
        floating = emptyMap()
        actionResolution = null
        areaTargeting = null
        pendingArea = null
        hoveredAbilityRange = null
        singleTargeting = null
        movementReachVisible = false
        movementReachHover = false
        enemyCpuBusy = false
        enemyCpuActingCombatantId = null
        enemyCpuActionLabel = null
        suppressedEnemyCpuTurnSignature = null
        completedEnemyCpuTurnSignature = null
        say(null)
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
        val restoredCpuDifficulty = presentation["enemyCpuDifficulty"]
            ?.let { name -> runCatching { EnemyCpuDifficulty.valueOf(name) }.getOrNull() }
        enemyCpuEnabled = restoredCpuDifficulty != null
        enemyCpuDifficulty = restoredCpuDifficulty ?: EnemyCpuDifficulty.MEDIUM
        enemyCpuSpeed = presentation["enemyCpuSpeed"]
            ?.let { name -> runCatching { EnemyCpuSpeed.valueOf(name) }.getOrNull() }
            ?: EnemyCpuSpeed.NORMAL
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
        val currentCpuTurn = enemyCpuTurnSignature
        suppressedEnemyCpuTurnSignature = presentation["enemyCpuSuppressedTurn"]
            ?.takeIf { it == currentCpuTurn }
        completedEnemyCpuTurnSignature = presentation["enemyCpuCompletedTurn"]
            ?.takeIf { it == currentCpuTurn && suppressedEnemyCpuTurnSignature == null }
    }

    fun expire(combatantId: String, floatId: Long) {
        floating = floating.toMutableMap().apply {
            val remaining = this[combatantId]?.filterNot { it.id == floatId }.orEmpty()
            if (remaining.isEmpty()) remove(combatantId) else put(combatantId, remaining)
        }
    }

    fun dismissMessage() {
        say(null)
    }

    fun dismissActionResolution() {
        actionResolution = null
    }

    /**
     * Scarta il riscontro di battaglia gia' composto, che non si puo' ritradurre.
     *
     * Il racconto del turno e i rifiuti del motore conservano *come si dice* una
     * cosa e seguono la lingua da soli. Questi no: la conferma di un attacco e i
     * numeri che salgono sopra i combattenti sono testo gia' composto, spesso
     * con dentro il nome di un bersaglio. Ritradurli vorrebbe dire conservarne
     * la forma semantica per un riscontro che dura tre secondi; scartarli costa
     * niente e non lascia in giro una frase nella lingua di prima.
     */
    internal fun onLanguageChanged() {
        actionResolution = null
        floating = emptyMap()
    }

    /** Mostra o ritira l'anteprima della portata quando il mouse attraversa una capacita'. */
    fun setAbilityRangeHovered(combatantId: String, abilityId: String, hovered: Boolean) {
        if (!hovered) {
            if (
                hoveredAbilityRange?.combatantId == combatantId &&
                hoveredAbilityRange?.abilityId == abilityId
            ) {
                hoveredAbilityRange = null
            }
            return
        }
        val ability = abilities(combatantId).firstOrNull { it.id() == abilityId } ?: return
        hoveredAbilityRange = AbilityRangePreview(
            combatantId = combatantId,
            abilityId = ability.id(),
            rangeFeet = ability.rangeFeet(),
            targeting = false,
        )
    }

    /** Mostra una nota guidata senza inviare alcun comando al motore. */
    /** Mostra un testo gia' scritto: un nome, un errore del sistema, una diagnostica. */
    fun showMessage(text: String) {
        say(text.takeIf { it.isNotBlank() }?.let(::literalText))
    }

    /** Mostra un testo del vocabolario, che seguira' la lingua scelta. */
    fun showMessage(text: LocalizedText) {
        say(text)
    }

    // --- interni ---------------------------------------------------------------------

    private fun floatInfo(text: String) = FloatingNumber(++floatSequence, text, FloatKind.INFO)

    /** Traduce ogni esito CPU in feedback locale sul token interessato. */
    internal fun pushEnemyCpuActionFeedback(action: EnemyCpuActionReport) {
        when (action.type()) {
            EnemyCpuActionType.ATTACK -> {
                if (action.targetId().isNotBlank()) {
                    push(
                        action.targetId(),
                        when {
                            action.amount() > 0 ->
                                FloatingNumber(++floatSequence, "-${action.amount()}", FloatKind.DAMAGE)
                            action.hit() -> FloatingNumber(++floatSequence, "0/Immune", FloatKind.INFO)
                            else -> FloatingNumber(++floatSequence, AppLocale.current.battle.missed, FloatKind.MISS)
                        },
                    )
                }
            }
            EnemyCpuActionType.HEAL -> {
                if (action.targetId().isNotBlank() && action.amount() > 0) {
                    val slotSuffix = action.slotLevel()
                        .takeIf { it > 0 }
                        ?.let { AppLocale.current.battle.spellSlotSuffix(it) }
                        .orEmpty()
                    push(
                        action.targetId(),
                        FloatingNumber(
                            ++floatSequence,
                            "+${action.amount()}$slotSuffix",
                            FloatKind.HEAL,
                        ),
                    )
                }
            }
            EnemyCpuActionType.AREA_ATTACK -> {
                action.targets().forEach { target ->
                    when {
                        target.amount() > 0 -> push(
                            target.targetId(),
                            FloatingNumber(
                                ++floatSequence,
                                "-${target.amount()}",
                                if (target.saved()) FloatKind.INFO else FloatKind.DAMAGE,
                            ),
                        )
                        target.saved() -> push(target.targetId(), floatInfo(AppLocale.current.battle.floatSaved))
                        else -> push(target.targetId(), floatInfo(AppLocale.current.battle.floatImmune))
                    }
                }
            }
            else -> Unit
        }
    }

    private fun push(combatantId: String, number: FloatingNumber) {
        floating = floating.toMutableMap().apply {
            put(combatantId, this[combatantId].orEmpty() + number)
        }
    }

    /**
     * Vale per il tavolo la stessa regola del batch CPU: più istanze della stessa
     * definizione hanno risorse indipendenti in sessione ma una sola scheda
     * esterna, quindi nessun valore da scrivere sarebbe quello giusto.
     */
    private fun hasClonedInstances(definitionId: String): Boolean =
        session.currentState().combatants().values
            .count { it.snapshot().definitionId() == definitionId } > 1

    /** Mantiene la scheda autorevole allineata; se fallisce, annulla il comando appena eseguito. */
    private fun persistCombatResourcesOrRollback(
        combatantId: String,
        definitionId: String,
        failureMessage: String,
    ) {
        if (hasClonedInstances(definitionId)) return
        try {
            resourceSink.onChanged(
                definitionId,
                session.currentState().combatant(combatantId).resources(),
            )
        } catch (failure: Exception) {
            session.undo()
            throw IllegalStateException(failure.message ?: failureMessage, failure)
        }
    }

    /**
     * Un turno CPU produce piu' comandi nel motore, ma per il tavolo e' una sola
     * operazione. Il marcatore e' la revisione da cui il turno e' partito: Undo
     * ripristina tutto quello che e' arrivato dopo, senza dover contare i comandi
     * ne' indovinare quanti checkpoint ne abbia prodotti ciascuno.
     */
    internal fun recordEnemyCpuUndoBatch(
        result: EnemyCpuResult,
        resourceChanges: Map<String, EnemyCpuResourceDelta>,
        turnSignature: String,
    ) {
        if (result.endingRevision() == result.startingRevision()) return
        val resourceConsumers = resourceChanges.mapValues { (_, delta) -> delta.definitionId }
        undoEffects.addLast(
            UndoEffect.EnemyCpuBatch(
                result.startingRevision(),
                result.endingRevision(),
                resourceConsumers,
                turnSignature,
            ),
        )
    }

    /** Propaga una sola volta lo stato finale di ogni risorsa realmente mutata dal turno CPU. */
    internal fun persistEnemyCpuResourceChanges(
        resourceChanges: Map<String, EnemyCpuResourceDelta>,
    ): String? {
        for ((_, delta) in resourceChanges) {
            try {
                resourceSink.onChanged(delta.definitionId, delta.after)
            } catch (failure: Exception) {
                return failure.message
                    ?: AppLocale.current.battle.cpuTurnDoneResourcesNotSaved
            }
        }
        return null
    }

    /** Riallinea la scheda dopo che il runner ha gia' riavvolto atomicamente il proprio batch. */
    internal fun restoreEnemyCpuResourceChanges(
        resourceChanges: Map<String, EnemyCpuResourceDelta>,
    ): String? {
        var firstFailure: String? = null
        for ((_, delta) in resourceChanges) {
            runCatching { resourceSink.onChanged(delta.definitionId, delta.before) }
                .exceptionOrNull()
                ?.let { failure ->
                    if (firstFailure == null) {
                        firstFailure = failure.message ?: AppLocale.current.battle.resourceRestoreFailed
                    }
                }
        }
        return firstFailure
    }

    /**
     * Il breve ritardo dello scheduler è parte del batch: nessun comando del
     * tavolo deve poter cambiare lo stato che la CPU sta per valutare.
     */
    private fun rejectMutationWhileEnemyCpuPending(): Boolean {
        if (!enemyCpuBatchPending || editMode) return false
        say { it.battle.waitForCpuTurn }
        return true
    }

    private fun command(
        undoEffect: UndoEffect = UndoEffect.None,
        block: () -> Unit,
    ) {
        if (rejectMutationWhileEnemyCpuPending()) return
        val revisionBefore = state.revision()
        var completed = false
        try {
            say(null)
            actionResolution = null
            block()
            completed = true
        } catch (failure: CombatRuleException) {
            say(ruleMessage(failure.message))
        } catch (failure: IllegalArgumentException) {
            say(ruleMessage(failure.message))
        } catch (failure: IllegalStateException) {
            say(ruleMessage(failure.message))
        } finally {
            sync()
            if (completed && state.revision() != revisionBefore) undoEffects.addLast(undoEffect)
        }
    }

    internal fun sync(forceTurnReset: Boolean = false) {
        val previousTurn = turnIdentity(state)
        val updated = session.currentState()
        state = updated
        events = session.auditTrail()
        if (forceTurnReset || previousTurn != turnIdentity(updated)) {
            movementReachVisible = false
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
                    combatant(it.attackerId)?.let { actor -> !actor.defeated() && !actor.dead() } == true &&
                    abilities(it.attackerId).any { ability -> ability.id() == it.abilityId }
            }
        }
        if (movementReachVisible && !movementReachAvailable) {
            movementReachVisible = false
        }
    }

    private fun sanitizeTarget(candidate: String?): String? {
        val active = activeActorId ?: return null
        return sanitizeTargetFor(active, candidate)
    }

    private fun sanitizeTargetFor(attacker: String, candidate: String?): String? {
        return candidate?.takeIf {
            it != attacker &&
                combatant(it)?.let { target -> !target.defeated() && !target.dead() } == true
        }
    }

    private fun turnIdentity(snapshot: CombatState): String? {
        if (snapshot.status() != CombatStatus.ACTIVE && snapshot.status() != CombatStatus.PAUSED) return null
        return "${snapshot.round()}:${snapshot.turnIndex()}:${snapshot.currentCombatantIds().joinToString(",")}"
    }

    private fun nextRosterInstanceId(definitionId: String): Pair<String, Int> {
        if (combatant(definitionId) == null) return definitionId to 1
        var copyNumber = 2
        while (combatant("$definitionId-$copyNumber") != null) copyNumber += 1
        return "$definitionId-$copyNumber" to copyNumber
    }

    private fun ActorDefinition.withEncounterName(encounterName: String): ActorDefinition = ActorDefinition(
        id(),
        definitionVersion(),
        rulesetVersion(),
        encounterName,
        armorClass(),
        maxHitPoints(),
        currentHitPoints(),
        temporaryHitPoints(),
        speedFeet(),
        initiativeModifier(),
        initiativeScore(),
        constitutionSaveBonus(),
        resistances(),
        vulnerabilities(),
        damageImmunities(),
        conditionImmunities(),
        abilities(),
        savingThrowBonuses(),
        spellSaveDc(),
        attacksPerAction(),
        strengthDexterityD20Disadvantage(),
        resources(),
    )

    private sealed interface UndoEffect {
        data object None : UndoEffect
        data class CombatantEdit(val combatantId: String, val definitionId: String) : UndoEffect
        data class ResourceChange(val combatantId: String, val definitionId: String) : UndoEffect
        data class EnemyCpuBatch(
            /** Revisione precedente al primo comando del turno CPU. */
            val startingRevision: Long,
            /**
             * Ultima revisione appartenente al runner, esclusi eventuali comandi
             * esterni. Resta fissa: e' il confine con cui l'Undo riconosce se
             * sopra il batch e' rimasto qualcosa da togliere prima.
             */
            val endingRevision: Long,
            val resourceConsumers: Map<String, String>,
            val turnSignature: String,
        ) : UndoEffect
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

/** Portata di una capacita' da evidenziare attorno al relativo combattente. */
data class AbilityRangePreview(
    val combatantId: String,
    val abilityId: String,
    val rangeFeet: Int,
    val targeting: Boolean,
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

fun EnemyCpuDifficulty.label(strings: Strings): String = when (this) {
    EnemyCpuDifficulty.EASY -> strings.battle.cpuDifficultyEasy
    EnemyCpuDifficulty.MEDIUM -> strings.battle.cpuDifficultyMedium
    EnemyCpuDifficulty.SORRY_FOR_YOU -> strings.battle.cpuDifficultySorryForYou
}

/**
 * Ritmo di riproduzione del turno nemico.
 *
 * [openingDelayMillis] e' la pausa fra il passaggio del turno e il primo comando,
 * [stepDelayMillis] quella fra un comando e il successivo. A zero il turno resta
 * quello di prima: tutto risolto in un fotogramma, per chi preferisce la velocita'
 * alla leggibilita'.
 */
enum class EnemyCpuSpeed(val openingDelayMillis: Long, val stepDelayMillis: Long) {
    SLOW(700L, 1_200L),
    NORMAL(450L, 700L),
    FAST(300L, 380L),
    INSTANT(0L, 0L),
}

fun EnemyCpuSpeed.label(strings: Strings): String = when (this) {
    EnemyCpuSpeed.SLOW -> strings.settings.cpuSpeedSlow
    EnemyCpuSpeed.NORMAL -> strings.settings.cpuSpeedNormal
    EnemyCpuSpeed.FAST -> strings.settings.cpuSpeedFast
    EnemyCpuSpeed.INSTANT -> strings.settings.cpuSpeedInstant
}
