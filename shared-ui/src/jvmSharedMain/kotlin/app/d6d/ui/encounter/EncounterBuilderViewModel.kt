package app.d6d.ui.encounter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.CombatantSetup
import app.d6d.domain.combat.D20Mode
import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.MapGrid
import app.d6d.engine.CombatSession
import app.d6d.ui.roster.RosterItem
import app.d6d.ui.roster.RosterKind
import app.d6d.ui.roster.RosterViewModel

/** I due schieramenti che il motore conserva nella sessione portabile. */
enum class EncounterFaction(val label: String) {
    ALLEATI("Alleati"),
    AVVERSARI("Avversari"),
}

/** Passaggi espliciti della procedura Nuova partita. */
enum class NewGameStep {
    TEMPLATE,
    PARTECIPANTI,
    GRIGLIA,
    MODALITA,
}

/** Da dove arrivano personaggi e creature della nuova partita. */
enum class TemplateSource(val label: String) {
    ESISTENTI("Template già creati"),
    DA_ZERO("Crea da zero"),
}

/** Esperienza scelta per la nuova partita. */
enum class EncounterMode(val label: String, val description: String) {
    FIGHT(
        "Modalità Fight",
        "Apre la mappa tattica e dispone automaticamente alleati e nemici vicini, pronti allo scontro.",
    ),
    ROLEPLAY_FIGHT_EXPLORATION(
        "Roleplay & Fight & Exploration",
        "Apre la schermata normale con la griglia pronta, lasciando libero il posizionamento dei token.",
    ),
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
 * I personaggi sono proposti come alleati, le creature come avversari. La scelta
 * resta modificabile per ogni voce e le quantita' producono istanze distinte della
 * stessa definizione, senza duplicare o alterare la scheda originale.
 */
class EncounterBuilderViewModel(
    private val roster: RosterViewModel,
    private val seedProvider: () -> Long = { System.currentTimeMillis() },
) {

    var step by mutableStateOf(NewGameStep.TEMPLATE)
        private set

    var templateSource by mutableStateOf<TemplateSource?>(null)
        private set

    var mode by mutableStateOf(EncounterMode.ROLEPLAY_FIGHT_EXPLORATION)

    var gridColumns by mutableStateOf(DEFAULT_COLUMNS)
        private set

    var gridRows by mutableStateOf(DEFAULT_ROWS)
        private set

    /** Il motore conserva i piedi; la procedura li presenta sempre anche in metri. */
    var feetPerSquare by mutableStateOf(DEFAULT_FEET_PER_SQUARE)
        private set

    private var scratchBaselineIds: Set<String> = emptySet()

    var encounterName by mutableStateOf("Partita")

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

    val canStart: Boolean
        get() = encounterName.isNotBlank() && selectedCount > 0

    /** Riparte dal primo passaggio dopo che una nuova sessione è stata adottata davvero. */
    fun restartWizard() {
        step = NewGameStep.TEMPLATE
        templateSource = null
        scratchBaselineIds = emptySet()
        choices = emptyMap()
        encounterName = "Partita"
        gridColumns = DEFAULT_COLUMNS
        gridRows = DEFAULT_ROWS
        feetPerSquare = DEFAULT_FEET_PER_SQUARE
        mode = EncounterMode.ROLEPLAY_FIGHT_EXPLORATION
        status = null
    }

    fun useExistingTemplates() {
        templateSource = TemplateSource.ESISTENTI
        scratchBaselineIds = emptySet()
        resetRecommended()
        step = NewGameStep.PARTECIPANTI
    }

    /** Conserva l'archivio esistente, ma per questa partita mostra solo le nuove schede. */
    fun createFromScratch() {
        templateSource = TemplateSource.DA_ZERO
        scratchBaselineIds = roster.items.mapTo(mutableSetOf()) { it.id }
        choices = emptyMap()
        status = null
        step = NewGameStep.PARTECIPANTI
    }

    fun back() {
        status = null
        step = when (step) {
            NewGameStep.TEMPLATE -> NewGameStep.TEMPLATE
            NewGameStep.PARTECIPANTI -> NewGameStep.TEMPLATE
            NewGameStep.GRIGLIA -> NewGameStep.PARTECIPANTI
            NewGameStep.MODALITA -> NewGameStep.GRIGLIA
        }
    }

    fun continueFromParticipants() {
        status = when {
            encounterName.isBlank() -> "Dai un nome alla partita."
            selectedCount == 0 -> "Seleziona almeno un partecipante."
            else -> null
        }
        if (status == null) step = NewGameStep.GRIGLIA
    }

    fun continueFromGrid() {
        status = null
        step = NewGameStep.MODALITA
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
        require(name.isNotEmpty()) { "Dai un nome all'incontro." }

        val selected = participants.filter { it.selected }
        require(selected.isNotEmpty()) { "Seleziona almeno un partecipante." }

        val usedInstanceIds = mutableSetOf<String>()
        val setups = mutableListOf<CombatantSetup>()
        val prepared = mutableListOf<PreparedCombatant>()
        val allies = mutableListOf<String>()

        selected.forEach { participant ->
            val source = requireNotNull(roster.definitionFor(participant.id)) {
                "La scheda «${participant.name}» non è più disponibile."
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

        val session = CombatSession.fromCombatants(name, seedProvider(), setups)
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
            status = failure.message ?: "Configurazione dell'incontro non valida."
            null
        } catch (failure: IllegalStateException) {
            status = failure.message ?: "Impossibile avviare l'incontro."
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
    )

    /** Posiziona i due schieramenti attorno al centro, rispettando ingombri e collisioni. */
    private fun autoPlaceForFight(
        session: CombatSession,
        grid: MapGrid,
        combatants: List<PreparedCombatant>,
    ) {
        val occupied = mutableSetOf<Pair<Int, Int>>()
        val middle = grid.columns() / 2
        val centerRow = grid.rows() / 2

        combatants.sortedBy { it.faction.ordinal }.forEach { combatant ->
            val side = combatant.squaresPerSide
            val candidates = buildList {
                for (row in 0..grid.rows() - side) {
                    for (column in 0..grid.columns() - side) {
                        add(GridPosition(column, row))
                    }
                }
            }.sortedBy { position ->
                val wrongHalf = when (combatant.faction) {
                    EncounterFaction.ALLEATI -> if (position.column() + side <= middle) 0 else 1_000
                    EncounterFaction.AVVERSARI -> if (position.column() >= middle) 0 else 1_000
                }
                val anchorColumn = when (combatant.faction) {
                    EncounterFaction.ALLEATI -> (middle - side - 1).coerceAtLeast(0)
                    EncounterFaction.AVVERSARI -> (middle + 1).coerceAtMost(grid.columns() - side)
                }
                wrongHalf + kotlin.math.abs(position.column() - anchorColumn) * 4 +
                    kotlin.math.abs(position.row() - centerRow)
            }

            val origin = candidates.firstOrNull { position ->
                (position.column() until position.column() + side).all { column ->
                    (position.row() until position.row() + side).all { row -> column to row !in occupied }
                }
            } ?: throw IllegalStateException(
                "La griglia ${grid.columns()}×${grid.rows()} è troppo piccola per tutti i token selezionati.",
            )

            session.placeCombatant(combatant.instanceId, origin, side)
            for (column in origin.column() until origin.column() + side) {
                for (row in origin.row() until origin.row() + side) occupied += column to row
            }
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
        const val MIN_GRID_SIDE = 5
        const val MAX_GRID_SIDE = 100
        const val DEFAULT_COLUMNS = 20
        const val DEFAULT_ROWS = 15
        const val DEFAULT_FEET_PER_SQUARE = 5
        const val MIN_FEET_PER_SQUARE = 1
        const val MAX_FEET_PER_SQUARE = 500
    }
}
