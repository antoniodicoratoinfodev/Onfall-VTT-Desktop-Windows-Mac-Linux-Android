package app.d6d.ui.encounter

import app.d6d.domain.combat.CombatStatus
import app.d6d.persistence.catalog.ActorCatalogStore
import app.d6d.sheet.SheetStore
import app.d6d.ui.roster.RosterKind
import app.d6d.ui.roster.RosterViewModel
import app.d6d.ui.sheet.SheetKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class EncounterBuilderViewModelTest {

    @TempDir
    lateinit var directory: Path

    private fun roster() = RosterViewModel(
        ActorCatalogStore(directory),
        SheetStore(directory.resolve("schede.json")),
    )

    @Test
    fun `il preset propone i personaggi come alleati e lascia opzionali le creature`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 42L })

        val characters = builder.participants.filter { it.kind == RosterKind.PERSONAGGIO }
        val creatures = builder.participants.filter { it.kind == RosterKind.CREATURA }

        assertTrue(characters.isNotEmpty())
        assertTrue(characters.all { it.selected && it.faction == EncounterFaction.ALLEATI })
        assertTrue(creatures.isNotEmpty())
        assertTrue(creatures.all { !it.selected && it.faction == EncounterFaction.AVVERSARI })
        assertEquals(characters.size, builder.selectedCount)
    }

    @Test
    fun `crea una sessione attiva con fazioni quantita e iniziativa statica`() {
        val roster = roster()
        val builder = EncounterBuilderViewModel(roster, seedProvider = { 73L })
        val hero = builder.participants.first { it.kind == RosterKind.PERSONAGGIO }
        val creature = builder.participants.first { it.kind == RosterKind.CREATURA }
        builder.clearSelection()
        builder.encounterName = "Assalto al ponte"
        builder.setSelected(hero.id, true)
        builder.setFaction(hero.id, EncounterFaction.ALLEATI)
        builder.setSelected(creature.id, true)
        builder.setFaction(creature.id, EncounterFaction.AVVERSARI)
        builder.setQuantity(creature.id, 2)

        val state = builder.startedSession().currentState()

        assertEquals("Assalto al ponte", state.encounterId())
        assertEquals(CombatStatus.ACTIVE, state.status())
        assertEquals(1, state.round())
        assertEquals(3, state.combatants().size)
        assertEquals(setOf(hero.id), state.partyCombatantIds())
        assertEquals(73L, state.randomSeed())

        val creatureInstances = state.combatants().values
            .filter { it.snapshot().definitionId() == creature.id }
        assertEquals(2, creatureInstances.size)
        assertEquals(setOf("${creature.name} 1", "${creature.name} 2"), creatureInstances.map { it.snapshot().name() }.toSet())

        // Il suffisso distingue le pedine, ma non deve rinominare lo stat block
        // autorevole se una di esse viene corretta durante il combattimento.
        roster.applyCombatEdit(creature.id, creatureInstances.first().snapshot())
        assertEquals(creature.name, roster.definitionFor(creature.id)!!.name())

        state.combatants().forEach { (instanceId, combatant) ->
            assertEquals(combatant.snapshot().initiativeScore(), state.initiativeScores()[instanceId])
        }
    }

    @Test
    fun `la sessione usa le statistiche della scheda appena salvata`() {
        val roster = roster()
        val hero = roster.items.first { it.kind == RosterKind.PERSONAGGIO }
        roster.sheets.kind = SheetKind.PERSONAGGIO
        roster.sheets.selectCharacter(hero.id)
        roster.sheets.character = roster.sheets.character.copy(armorClass = 23, maxHitPoints = 47)
        roster.sheets.save()

        val builder = EncounterBuilderViewModel(roster, seedProvider = { 1L })
        builder.clearSelection()
        builder.setSelected(hero.id, true)

        val snapshot = builder.startedSession().currentState().combatants()[hero.id]?.snapshot()

        assertNotNull(snapshot)
        assertEquals(23, snapshot!!.armorClass())
        assertEquals(47, snapshot.maxHitPoints())
    }

    @Test
    fun `quantita non scende mai sotto uno e una selezione vuota non parte`() {
        val builder = EncounterBuilderViewModel(roster(), seedProvider = { 1L })
        val first = builder.participants.first()

        builder.setQuantity(first.id, -20)
        assertEquals(1, builder.participants.first { it.id == first.id }.quantity)

        builder.clearSelection()
        assertFalse(builder.canStart)
        assertNull(builder.tryStart())
        assertEquals("Seleziona almeno un partecipante.", builder.status)
    }
}
