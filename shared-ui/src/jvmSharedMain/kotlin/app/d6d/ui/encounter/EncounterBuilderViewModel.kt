package app.d6d.ui.encounter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.CombatantSetup
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.space.MapGrid
import app.d6d.engine.CombatSession
import app.d6d.engine.ai.EnemyCpuDifficulty
import app.d6d.ui.state.EnemyCpuSpeed
import app.d6d.ui.content.SessionTemplate
import app.d6d.ui.content.SessionTemplates
import app.d6d.ui.maps.GridLimits
import app.d6d.ui.maps.PendingToken
import app.d6d.ui.maps.arrangeTokens
import app.d6d.ui.i18n.AppLocale
import app.d6d.ui.i18n.Strings
import app.d6d.ui.maps.gridTooSmallMessage
import app.d6d.ui.roster.RosterItem
import app.d6d.ui.roster.RosterKind
import app.d6d.ui.roster.RosterViewModel
import app.d6d.sheet.NpcDisposition
import app.d6d.content.srd521it.Srd521Ruleset
import app.d6d.rules.model.RulesetRevision

/** I due schieramenti che il motore conserva nella sessione portabile. */
enum class EncounterFaction {
    ALLEATI,
    AVVERSARI,
}

fun EncounterFaction.label(strings: Strings): String = when (this) {
    EncounterFaction.ALLEATI -> strings.encounter.alliesLabel
    EncounterFaction.AVVERSARI -> strings.encounter.opponentsLabel
}

/** Passaggi espliciti della procedura Nuova partita. */
enum class NewGameStep {
    TEMPLATE,
    REGOLAMENTO,
    PARTECIPANTI,
    GRIGLIA,
    MODALITA,
    DIFFICOLTA,
}

/** Da dove arrivano personaggi e creature della nuova partita. */
enum class TemplateSource {
    INCLUSA,
    ESISTENTI,
    DA_ZERO,
}

fun TemplateSource.label(strings: Strings): String = when (this) {
    TemplateSource.INCLUSA -> strings.encounter.sourceIncludedGame
    TemplateSource.ESISTENTI -> strings.encounter.sourceExistingTemplates
    TemplateSource.DA_ZERO -> strings.encounter.sourceFromScratch
}

/** Esperienza scelta per la nuova partita. */
enum class EncounterMode {
    FIGHT,
    ROLEPLAY_FIGHT_EXPLORATION,
}

fun EncounterMode.label(strings: Strings): String = when (this) {
    EncounterMode.FIGHT -> strings.encounter.modeFight
    EncounterMode.ROLEPLAY_FIGHT_EXPLORATION -> strings.encounter.modeFull
}

fun EncounterMode.description(strings: Strings): String = when (this) {
    EncounterMode.FIGHT -> strings.encounter.modeFightHint
    EncounterMode.ROLEPLAY_FIGHT_EXPLORATION -> strings.encounter.modeFullHint
}

/**
 * Presentazione iniziale di una partita appena creata.
 *
 * In sandbox la chiave della difficolta' non viene scritta affatto: e' esattamente
 * il modo in cui il combattimento riconosce le partite senza automazione, quindi
 * non prende il controllo di nessuno schieramento e il tavolo muove anche gli
 * avversari.
 */
internal fun newEncounterPresentation(
    mode: EncounterMode,
    difficulty: EnemyCpuDifficulty?,
    speed: EnemyCpuSpeed = EnemyCpuSpeed.NORMAL,
): Map<String, String> = buildMap {
    put("encounterMode", mode.name)
    difficulty?.let {
        put("enemyCpuDifficulty", it.name)
        put("enemyCpuSpeed", speed.name)
    }
}

/** Una voce del Compendio con le scelte specifiche del prossimo incontro. */
data class EncounterParticipant(
    val item: RosterItem,
    val selected: Boolean,
    val quantity: Int,
    val faction: EncounterFaction,
) {
    val id: String get() = item.id
    val name: String get() = item.name
    val kind: RosterKind get() = item.kind
    val subtitle: String get() = item.subtitle
}

private data class ParticipantChoice(
    val selected: Boolean,
    val quantity: Int,
    val faction: EncounterFaction,
)

/**
 * Configura un incontro partendo esclusivamente dalle schede del Compendio.
 *
 * I personaggi sono proposti come alleati, le creature come avversari e i PNG
 * seguono la loro disposizione abituale. La scelta resta modificabile per ogni
 * voce e le quantita' producono istanze distinte della stessa definizione, senza
 * duplicare o alterare la scheda originale.
 */
class EncounterBuilderViewModel(
    private val roster: RosterViewModel,
    private val seedProvider: () -> Long = { System.currentTimeMillis() },
    private val rulesetProvider: () -> List<RulesetRevision> = { listOf(Srd521Ruleset.revision) },
) {

    var step by mutableStateOf(NewGameStep.TEMPLATE)
        private set

    var templateSource by mutableStateOf<TemplateSource?>(null)
        private set

    private var selectedRulesetHash by mutableStateOf(Srd521Ruleset.revision.canonicalHash())

    val availableRulesets: List<RulesetRevision>
        get() = rulesetProvider().ifEmpty { listOf(Srd521Ruleset.revision) }

    val selectedRuleset: RulesetRevision
        get() = availableRulesets.firstOrNull { it.canonicalHash() == selectedRulesetHash }
            ?: availableRulesets.first().also { selectedRulesetHash = it.canonicalHash() }

    val selectedRulesetSupportsEnemyCpu: Boolean
        get() = selectedRuleset.legacyCombatAutomationCompatibleWith(Srd521Ruleset.revision)

    fun selectRuleset(canonicalHash: String) {
        if (availableRulesets.any { it.canonicalHash() == canonicalHash }) {
            selectedRulesetHash = canonicalHash
            if (!selectedRulesetSupportsEnemyCpu) enemyCpuDifficulty = null
            status = null
        }
    }

    var mode by mutableStateOf(EncounterMode.ROLEPLAY_FIGHT_EXPLORATION)

    /**
     * Profilo tattico con cui la CPU controllera' lo schieramento avversario.
     *
     * `null` e' la sandbox: nessuna automazione, gli avversari restano al tavolo
     * esattamente come gli alleati.
     */
    var enemyCpuDifficulty by mutableStateOf<EnemyCpuDifficulty?>(EnemyCpuDifficulty.MEDIUM)

    /**
     * Ritmo con cui la partita mostrera' i turni della CPU.
     *
     * Non e' una scelta della procedura: e' la preferenza dell'applicazione, che la
     * shell tiene allineata qui. Continua a finire nella presentazione della partita
     * perche' quel campo resta leggibile e scrivibile da entrambe le direzioni — i
     * salvataggi vecchi si aprono, i nuovi restano apribili da una versione
     * precedente — ma a decidere e' sempre l'impostazione, anche alla riapertura.
     */
    var enemyCpuSpeed by mutableStateOf(EnemyCpuSpeed.NORMAL)

    var gridColumns by mutableStateOf(DEFAULT_COLUMNS)
        private set

    var gridRows by mutableStateOf(DEFAULT_ROWS)
        private set

    /** Il motore conserva i piedi; la procedura li presenta nella lingua corrente. */
    var feetPerSquare by mutableStateOf(DEFAULT_FEET_PER_SQUARE)
        private set

    private var scratchBaselineIds: Set<String> = emptySet()

    // Il nome proposto per una partita nuova segue la lingua di chi la crea.
    // Una volta salvato resta com'e': da li' in poi e' un dato, non un'etichetta.
    private var suggestedEncounterName = AppLocale.current.nav.game
    private var includedTemplateId: String? = null
    var encounterName by mutableStateOf(suggestedEncounterName)

    var status by mutableStateOf<String?>(null)
        private set

    private var choices by mutableStateOf<Map<String, ParticipantChoice>>(emptyMap())

    val participants: List<EncounterParticipant>
        get() = roster.items
            .filter { templateSource != TemplateSource.DA_ZERO || it.id !in scratchBaselineIds }
            .map { item ->
            val choice = choices[item.id] ?: defaultChoice(item)
            EncounterParticipant(item, choice.selected, choice.quantity, choice.faction)
        }

    val selectedCount: Int
        get() = participants.filter { it.selected }.sumOf { it.quantity }

    val allyCount: Int
        get() = participants.filter { it.selected && it.faction == EncounterFaction.ALLEATI }
            .sumOf { it.quantity }

    val opponentCount: Int
        get() = participants.filter { it.selected && it.faction == EncounterFaction.AVVERSARI }
            .sumOf { it.quantity }

    /** Le sessioni mono-fazione restano valide, ma non promettono una CPU che non puo' agire. */
    val enemyCpuInactiveReason: String?
        get() = when {
            // In sandbox non c'e' nessuna automazione da avvertire: e' spenta per scelta.
            enemyCpuDifficulty == null -> null
            allyCount == 0 -> AppLocale.current.encounter.cpuIdleNoAllies
            opponentCount == 0 -> AppLocale.current.encounter.cpuIdleNoOpponents
            else -> null
        }

    val canStart: Boolean
        get() = encounterName.isNotBlank() && selectedCount > 0

    /** Riparte dal primo passaggio dopo che una nuova sessione è stata adottata davvero. */
    fun restartWizard() {
        step = NewGameStep.TEMPLATE
        templateSource = null
        selectedRulesetHash = availableRulesets.firstOrNull {
            it.origin() == app.d6d.rules.model.RulesetOrigin.BUNDLED_STANDARD
        }?.canonicalHash() ?: availableRulesets.first().canonicalHash()
        scratchBaselineIds = emptySet()
        choices = emptyMap()
        includedTemplateId = null
        suggestedEncounterName = AppLocale.current.nav.game
        encounterName = suggestedEncounterName
        gridColumns = DEFAULT_COLUMNS
        gridRows = DEFAULT_ROWS
        feetPerSquare = DEFAULT_FEET_PER_SQUARE
        mode = EncounterMode.ROLEPLAY_FIGHT_EXPLORATION
        enemyCpuDifficulty = EnemyCpuDifficulty.MEDIUM
        // Il ritmo non si azzera: appartiene alle impostazioni, non alla procedura,
        // e ricominciare da capo una partita non e' motivo per rimetterlo a Normale.
        status = null
    }

    fun useExistingTemplates() {
        includedTemplateId = null
        templateSource = TemplateSource.ESISTENTI
        scratchBaselineIds = emptySet()
        resetRecommended()
        step = NewGameStep.REGOLAMENTO
    }

    /** Le partite gia' pronte distribuite con l'app. */
    internal val includedTemplates: List<SessionTemplate> get() = SessionTemplates.of(AppLocale.language).all

    /**
     * Compila la procedura con una partita inclusa.
     *
     * Non salta i passaggi: li riempie. Squadra, avversari, nome e griglia
     * arrivano dal template, ma restano tutti modificabili prima di avviare —
     * togliere un nemico o allargare la mappa non e' un caso particolare, e' il
     * normale funzionamento del passaggio successivo.
     */
    internal fun useIncludedTemplate(template: SessionTemplate) {
        // Un template nomina schede del Compendio: se ne mancano, si rimettono
        // prima di selezionarle, altrimenti la selezione cadrebbe nel vuoto.
        roster.installTemplateContent(template)
        templateSource = TemplateSource.INCLUSA
        scratchBaselineIds = emptySet()
        val party = template.party.associate { sheet ->
            sheet.id to ParticipantChoice(true, MIN_QUANTITY, EncounterFaction.ALLEATI)
        }
        val opponents = template.opponents.associate { opponent ->
            opponent.statBlock.id to ParticipantChoice(
                selected = true,
                quantity = opponent.quantity.coerceIn(MIN_QUANTITY, MAX_QUANTITY),
                faction = EncounterFaction.AVVERSARI,
            )
        }
        val untouched = roster.items
            .filterNot { it.id in party || it.id in opponents }
            .associate { it.id to ParticipantChoice(false, MIN_QUANTITY, defaultChoice(it).faction) }
        choices = untouched + party + opponents
        includedTemplateId = template.id
        suggestedEncounterName = template.name
        encounterName = suggestedEncounterName
        gridColumns = template.gridColumns.coerceIn(MIN_GRID_SIDE, MAX_GRID_SIDE)
        gridRows = template.gridRows.coerceIn(MIN_GRID_SIDE, MAX_GRID_SIDE)
        feetPerSquare = template.feetPerSquare.coerceIn(MIN_FEET_PER_SQUARE, MAX_FEET_PER_SQUARE)
        // Le partite incluse sono scontri pronti: la mappa si apre gia' schierata.
        mode = EncounterMode.FIGHT
        status = null
        step = NewGameStep.REGOLAMENTO
    }

    /** Conserva l'archivio esistente, ma per questa partita mostra solo le nuove schede. */
    fun createFromScratch() {
        includedTemplateId = null
        templateSource = TemplateSource.DA_ZERO
        scratchBaselineIds = roster.items.mapTo(mutableSetOf()) { it.id }
        choices = emptyMap()
        status = null
        step = NewGameStep.REGOLAMENTO
    }

    /** Riallinea soltanto il nome ancora proposto dalla procedura, non uno editato dall'utente. */
    internal fun onLanguageChanged() {
        val replacement = includedTemplateId
            ?.let { id -> includedTemplates.firstOrNull { it.id == id }?.name }
            ?: AppLocale.current.nav.game
        if (encounterName == suggestedEncounterName) encounterName = replacement
        suggestedEncounterName = replacement
        status = null
    }

    fun back() {
        status = null
        step = when (step) {
            NewGameStep.TEMPLATE -> NewGameStep.TEMPLATE
            NewGameStep.REGOLAMENTO -> NewGameStep.TEMPLATE
            NewGameStep.PARTECIPANTI -> NewGameStep.REGOLAMENTO
            NewGameStep.GRIGLIA -> NewGameStep.PARTECIPANTI
            NewGameStep.MODALITA -> NewGameStep.GRIGLIA
            NewGameStep.DIFFICOLTA -> NewGameStep.MODALITA
        }
    }

    fun continueFromRuleset() {
        status = null
        selectedRuleset
        if (!selectedRulesetSupportsEnemyCpu) enemyCpuDifficulty = null
        step = NewGameStep.PARTECIPANTI
    }

    fun continueFromParticipants() {
        status = when {
            encounterName.isBlank() -> AppLocale.current.encounter.nameTheGame
            selectedCount == 0 -> AppLocale.current.encounter.pickAtLeastOneParticipant
            else -> null
        }
        if (status == null) step = NewGameStep.GRIGLIA
    }

    fun continueFromGrid() {
        status = null
        step = NewGameStep.MODALITA
    }

    fun continueFromMode() {
        status = null
        step = NewGameStep.DIFFICOLTA
    }

    fun updateGridColumns(value: Int) {
        gridColumns = value.coerceIn(MIN_GRID_SIDE, MAX_GRID_SIDE)
        status = null
    }

    fun updateGridRows(value: Int) {
        gridRows = value.coerceIn(MIN_GRID_SIDE, MAX_GRID_SIDE)
        status = null
    }

    fun updateFeetPerSquare(value: Int) {
        feetPerSquare = value.coerceIn(MIN_FEET_PER_SQUARE, MAX_FEET_PER_SQUARE)
        status = null
    }

    fun useGridPreset(columns: Int, rows: Int) {
        updateGridColumns(columns)
        updateGridRows(rows)
    }

    fun setSelected(id: String, selected: Boolean) = update(id) { copy(selected = selected) }

    fun setFaction(id: String, faction: EncounterFaction) = update(id) { copy(faction = faction) }

    fun setQuantity(id: String, quantity: Int) = update(id) {
        copy(quantity = quantity.coerceIn(MIN_QUANTITY, MAX_QUANTITY))
    }

    fun changeQuantity(id: String, delta: Int) {
        val current = participant(id) ?: return
        setQuantity(id, current.quantity + delta)
    }

    /** Deseleziona tutto, mantenendo fazioni e quantita' gia' impostate. */
    fun clearSelection() {
        choices = participants.associate { participant ->
            participant.id to ParticipantChoice(
                selected = false,
                quantity = participant.quantity,
                faction = participant.faction,
            )
        }
        status = null
    }

    /** Ripristina il preset rapido: tutti i personaggi alleati, creature non selezionate. */
    fun resetRecommended() {
        choices = roster.items.associate { it.id to defaultChoice(it) }
        status = null
    }

    /**
     * Costruisce e avvia la sessione.
     *
     * L'iniziativa statica e' deliberatamente riproducibile: usa il punteggio
     * riportato dalla scheda/stat block e il normale ordine del Compendio per le
     * parita'. Il seed resta disponibile ai successivi tiri digitali.
     */
    fun startedSession(): CombatSession {
        val name = encounterName.trim()
        require(name.isNotEmpty()) { AppLocale.current.encounter.nameTheEncounter }

        val selected = participants.filter { it.selected }
        require(selected.isNotEmpty()) { AppLocale.current.encounter.pickAtLeastOneParticipant }

        val usedInstanceIds = mutableSetOf<String>()
        val setups = mutableListOf<CombatantSetup>()
        val prepared = mutableListOf<PreparedCombatant>()
        val allies = mutableListOf<String>()

        selected.forEach { participant ->
            val source = requireNotNull(roster.definitionFor(participant.id)) {
                AppLocale.current.encounter.sheetNoLongerAvailable(participant.name)
            }
            repeat(participant.quantity) { index ->
                val instanceId = uniqueInstanceId(
                    definitionId = source.id(),
                    ordinal = index + 1,
                    quantity = participant.quantity,
                    used = usedInstanceIds,
                )
                val actor = if (participant.quantity == 1) {
                    source
                } else {
                    source.withEncounterName("${source.name()} ${index + 1}")
                }
                setups += CombatantSetup(instanceId, actor)
                if (participant.faction == EncounterFaction.ALLEATI) allies += instanceId
                prepared += PreparedCombatant(
                    instanceId = instanceId,
                    faction = participant.faction,
                    squaresPerSide = roster.footprintFor(source.id()).coerceIn(1, 4),
                )
            }
        }

        val ruleset = selectedRuleset
        val session = CombatSession.fromCombatants(
            name,
            seedProvider(),
            setups,
            ruleset,
            "local-1",
        )
        session.setPartyCombatants(allies)
        val grid = MapGrid(gridColumns, gridRows, feetPerSquare)
        session.configureMap(grid)
        if (mode == EncounterMode.FIGHT) autoPlaceForFight(session, grid, prepared)
        session.markReady()
        setups.forEach { session.useStaticInitiative(it.instanceId(), D20Mode.NORMAL) }
        session.start()
        return session
    }

    /** Variante per la UI: un errore di configurazione diventa un messaggio leggibile. */
    fun tryStart(): CombatSession? {
        status = null
        return try {
            startedSession()
        } catch (failure: IllegalArgumentException) {
            status = failure.message ?: AppLocale.current.encounter.invalidConfiguration
            null
        } catch (failure: IllegalStateException) {
            status = failure.message ?: AppLocale.current.encounter.cannotStart
            null
        }
    }

    fun dismissStatus() {
        status = null
    }

    private fun participant(id: String): EncounterParticipant? = participants.firstOrNull { it.id == id }

    private fun update(id: String, transform: ParticipantChoice.() -> ParticipantChoice) {
        val item = roster.items.firstOrNull { it.id == id } ?: return
        val current = choices[id] ?: defaultChoice(item)
        choices = choices + (id to current.transform())
        status = null
    }

    private fun defaultChoice(item: RosterItem): ParticipantChoice = when (item.kind) {
        RosterKind.PERSONAGGIO -> ParticipantChoice(true, MIN_QUANTITY, EncounterFaction.ALLEATI)
        RosterKind.NPC -> ParticipantChoice(
            selected = false,
            quantity = MIN_QUANTITY,
            faction = if (item.npcDisposition == NpcDisposition.HOSTILE) {
                EncounterFaction.AVVERSARI
            } else {
                EncounterFaction.ALLEATI
            },
        )
        RosterKind.CREATURA -> ParticipantChoice(false, MIN_QUANTITY, EncounterFaction.AVVERSARI)
    }

    private fun uniqueInstanceId(
        definitionId: String,
        ordinal: Int,
        quantity: Int,
        used: MutableSet<String>,
    ): String {
        val preferred = if (quantity == 1) definitionId else "$definitionId-$ordinal"
        var candidate = preferred
        var collision = 2
        while (!used.add(candidate)) {
            candidate = "$preferred-$collision"
            collision += 1
        }
        return candidate
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
    )

    /** Posiziona i due schieramenti attorno al centro, rispettando ingombri e collisioni. */
    private fun autoPlaceForFight(
        session: CombatSession,
        grid: MapGrid,
        combatants: List<PreparedCombatant>,
    ) {
        val placements = arrangeTokens(
            grid = grid,
            tokens = combatants.map {
                PendingToken(it.instanceId, it.faction == EncounterFaction.ALLEATI, it.squaresPerSide)
            },
        ) ?: throw IllegalStateException(gridTooSmallMessage(grid).resolve(AppLocale.current))

        val sideOf = combatants.associate { it.instanceId to it.squaresPerSide }
        placements.forEach { (instanceId, origin) ->
            session.placeCombatant(instanceId, origin, sideOf.getValue(instanceId))
        }
    }

    private data class PreparedCombatant(
        val instanceId: String,
        val faction: EncounterFaction,
        val squaresPerSide: Int,
    )

    private companion object {
        const val MIN_QUANTITY = 1
        const val MAX_QUANTITY = 99
        const val MIN_GRID_SIDE = GridLimits.MIN_SIDE
        const val MAX_GRID_SIDE = GridLimits.MAX_BUILDER_SIDE
        const val DEFAULT_COLUMNS = 20
        const val DEFAULT_ROWS = 15
        const val DEFAULT_FEET_PER_SQUARE = 5
        const val MIN_FEET_PER_SQUARE = 1
        const val MAX_FEET_PER_SQUARE = 500
    }
}
