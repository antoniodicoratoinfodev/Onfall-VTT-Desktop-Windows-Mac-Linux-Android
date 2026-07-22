package app.d6d.ui.encounter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.d6d.domain.combat.ActorDefinition
import app.d6d.domain.combat.CombatantSetup
import app.d6d.domain.combat.D20Mode
import app.d6d.engine.CombatSession
import app.d6d.ui.roster.RosterItem
import app.d6d.ui.roster.RosterKind
import app.d6d.ui.roster.RosterViewModel

/** I due schieramenti che il motore conserva nella sessione portabile. */
enum class EncounterFaction(val label: String) {
    ALLEATI("Alleati"),
    AVVERSARI("Avversari"),
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

    var encounterName by mutableStateOf("Nuovo incontro")

    var status by mutableStateOf<String?>(null)
        private set

    private var choices by mutableStateOf<Map<String, ParticipantChoice>>(emptyMap())

    val participants: List<EncounterParticipant>
        get() = roster.items.map { item ->
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
            }
        }

        val session = CombatSession.fromCombatants(name, seedProvider(), setups)
        session.setPartyCombatants(allies)
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
    )

    private companion object {
        const val MIN_QUANTITY = 1
        const val MAX_QUANTITY = 99
    }
}
