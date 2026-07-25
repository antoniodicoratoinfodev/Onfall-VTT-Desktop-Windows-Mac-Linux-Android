package app.d6d.ui.session

import app.d6d.domain.combat.D20Mode
import app.d6d.domain.space.GridPosition
import app.d6d.persistence.session.SessionArchiveStore
import app.d6d.ui.content.SampleEncounter
import app.d6d.ui.state.BattleViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Ciclo completo di salvataggio e ricarica visto dall'interfaccia.
 *
 * Non basta che il file si scriva: dopo la ricarica il tavolo deve ritrovare la
 * stessa partita, comprese le scelte che il motore non conosce.
 */
class SessionManagerTest {

    @TempDir
    lateinit var directory: Path

    private fun manager(battle: BattleViewModel) =
        SessionManager(SessionArchiveStore(directory.resolve("sessions")), battle)

    private fun battle() = BattleViewModel(SampleEncounter.startedSession(seed = 31L))

    @Test
    fun `una sessione salvata compare nell'elenco con i propri dati`() {
        val battle = battle()
        val manager = manager(battle)

        manager.save("Cripta — sera 1")

        assertEquals(1, manager.sessions.size)
        val saved = manager.sessions.first()
        assertEquals("Cripta — sera 1", saved.displayName)
        assertEquals(8, saved.combatantCount)
        assertEquals("ACTIVE", saved.status)
        assertEquals(1, saved.round)
    }

    @Test
    fun `ricaricare riporta il combattimento allo stato salvato`() {
        val battle = battle()
        val manager = manager(battle)
        val target = battle.effectiveTargetId()!!
        val hitPointsAtSave = battle.combatant(target)!!.currentHitPoints()

        manager.save("prima del colpo")

        // Si continua a giocare: il bersaglio incassa danni.
        battle.attack(battle.abilities(battle.activeCombatantId!!).first().id())
        battle.endTurn()

        manager.load(manager.sessions.first())

        assertEquals(hitPointsAtSave, battle.combatant(target)!!.currentHitPoints())
        assertEquals(1, battle.round)
    }

    @Test
    fun `la mappa e i segnaposti sopravvivono alla ricarica`() {
        val battle = battle()
        val manager = manager(battle)
        battle.configureMap(20, 15, 5)
        battle.place(battle.partyIds.first(), 4, 6, 2)

        manager.save("con mappa")
        battle.configureMap(6, 6, 20)
        manager.load(manager.sessions.first())

        assertTrue(battle.mapConfigured)
        assertEquals(20, battle.battleMap.grid().columns())
        assertEquals(5, battle.battleMap.grid().feetPerSquare())
        val placement = battle.placementOf(battle.partyIds.first())
        assertNotNull(placement)
        assertEquals(GridPosition(4, 6), placement!!.origin())
        assertEquals(2, placement.squaresPerSide())
    }

    @Test
    fun `le scelte di presentazione tornano come erano`() {
        val battle = battle()
        val manager = manager(battle)
        val chosen = battle.enemyIds.last()
        val inspected = (battle.partyIds + battle.enemyIds).first { it != battle.activeCombatantId }
        battle.selectedTargetId = chosen
        battle.inspectCombatant(inspected)
        battle.rollMode = D20Mode.DISADVANTAGE
        battle.editMode = true
        battle.mapEditMode = true

        manager.save("con presentazione")
        battle.selectedTargetId = null
        battle.inspectCombatant(battle.activeCombatantId!!)
        battle.rollMode = D20Mode.ADVANTAGE
        battle.editMode = false
        battle.mapEditMode = false
        manager.load(manager.sessions.first())

        assertEquals(chosen, battle.selectedTargetId)
        assertEquals(inspected, battle.inspectedCombatantId)
        assertEquals(D20Mode.DISADVANTAGE, battle.rollMode)
        assertTrue(battle.editMode)
        assertTrue(battle.mapEditMode)
    }

    @Test
    fun `gli ingombri dei segnaposti non collocati sopravvivono`() {
        val battle = battle()
        val manager = manager(battle)
        val id = battle.enemyIds.first()
        battle.setFootprint(id, 3)

        manager.save("con ingombri")
        battle.setFootprint(id, 1)
        manager.load(manager.sessions.first())

        assertEquals(3, battle.squaresPerSideFor(id))
    }

    @Test
    fun `il registro completo sopravvive alla ricarica`() {
        val battle = battle()
        val manager = manager(battle)
        battle.attack(battle.abilities(battle.activeCombatantId!!).first().id())
        val events = battle.events.size

        manager.save("con registro")
        battle.endTurn()
        manager.load(manager.sessions.first())

        assertEquals(events, battle.events.size)
    }

    @Test
    fun `ricaricare azzera gli effetti visivi in corso`() {
        val battle = battle()
        val manager = manager(battle)
        manager.save("pulizia")
        battle.attack(battle.abilities(battle.activeCombatantId!!).first().id())
        assertTrue(battle.floating.isNotEmpty())

        manager.load(manager.sessions.first())

        // Numeri fluttuanti di un'altra partita non devono restare a schermo.
        assertTrue(battle.floating.isEmpty())
    }

    @Test
    fun `eliminare toglie la sessione dall'elenco`() {
        val battle = battle()
        val manager = manager(battle)
        manager.save("da eliminare")

        manager.delete(manager.sessions.first())

        assertTrue(manager.sessions.isEmpty())
    }

    @Test
    fun `salvare due volte con lo stesso nome non duplica`() {
        val manager = manager(battle())

        manager.save("unica")
        manager.save("unica")

        assertEquals(1, manager.sessions.size)
    }

    @Test
    fun `dirty state e flush autosave seguono lo stato realmente persistito`() {
        val battle = battle()
        val manager = manager(battle)
        assertTrue(manager.hasUnsavedChanges)

        assertEquals(SessionSaveResult.SAVED, manager.save("autosave"))
        assertFalse(manager.hasUnsavedChanges)

        battle.endTurn()
        assertTrue(manager.currentDirty)
        assertEquals(SessionSaveResult.SAVED, manager.flushAutosave())
        assertFalse(manager.hasUnsavedChanges)
        assertEquals(SessionSaveResult.NOT_NEEDED, manager.flushAutosave())
    }

    @Test
    fun `una collisione di slug non sovrascrive una sessione estranea`() {
        val first = manager(battle())
        assertEquals(SessionSaveResult.SAVED, first.save("Cripta!"))

        val otherBattle = battle()
        otherBattle.endTurn()
        val second = manager(otherBattle)

        assertEquals(SessionSaveResult.NAME_COLLISION, second.save("Cripta?"))
        val stored = SessionArchiveStore(directory.resolve("sessions")).load("cripta")
        assertEquals("Cripta!", stored.summary().displayName)
        assertEquals(0, stored.session.currentState().turnIndex())
    }

    @Test
    fun `request load segnala prima le modifiche non salvate`() {
        val battle = battle()
        val manager = manager(battle)
        manager.save("checkpoint")
        val summary = manager.sessions.first()
        battle.endTurn()

        assertEquals(SessionLoadResult.UNSAVED_CHANGES, manager.requestLoad(summary))
        assertTrue(manager.currentDirty)
        assertEquals(
            SessionLoadResult.LOADED,
            manager.requestLoad(summary, discardUnsavedChanges = true),
        )
        assertFalse(manager.hasUnsavedChanges)
    }

    @Test
    fun `un errore su disco diventa un messaggio invece di un'eccezione`() {
        val battle = battle()
        // Cartella dentro un file: qualunque scrittura fallisce.
        val impossible = directory.resolve("bloccata")
        java.nio.file.Files.writeString(impossible, "non sono una cartella")
        val manager = SessionManager(SessionArchiveStore(impossible.resolve("sessions")), battle)

        manager.save("impossibile")

        assertNotNull(manager.status)
        assertFalse(manager.status!!.isBlank())
    }
}
