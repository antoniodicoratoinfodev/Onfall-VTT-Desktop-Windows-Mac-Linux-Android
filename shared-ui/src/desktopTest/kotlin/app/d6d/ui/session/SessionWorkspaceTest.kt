package app.d6d.ui.session

import app.d6d.domain.combat.D20Mode
import app.d6d.engine.CombatSession
import app.d6d.engine.ai.EnemyCpuDifficulty
import app.d6d.persistence.session.SessionArchiveStore
import app.d6d.ui.content.SampleEncounter
import app.d6d.ui.state.BattleViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SessionWorkspaceTest {

    @TempDir
    lateinit var directory: Path

    private fun store() = SessionArchiveStore(directory.resolve("sessions"))

    private fun mixedCpuSession(seed: Long): CombatSession {
        val hero = SampleEncounter.party().first()
        val enemy = SampleEncounter.enemies().first()
        return CombatSession.create("mixed-persistence", seed).also { session ->
            session.addCombatant("enemy", enemy)
            session.addCombatant("hero", hero)
            session.setPartyCombatants(listOf("hero"))
            session.setInitiative("enemy", 20)
            session.setInitiative("hero", 20)
            session.setInitiativeOrder(listOf("enemy", "hero"))
            session.setSimultaneousTies(true)
            session.markReady()
            session.start()
        }
    }

    private fun workspace(store: SessionArchiveStore = store()) = SessionWorkspace(
        store = store,
        initialSession = SampleEncounter.startedSession(seed = 11L),
        initialDisplayName = "Prima",
    )

    @Test
    fun `due schede mantengono motore undo e stato sporco indipendenti`() {
        val workspace = workspace()
        val first = workspace.activeSession
        val second = workspace.openNew(
            SampleEncounter.startedSession(seed = 22L),
            "Seconda",
        )
        assertEquals(SessionSaveResult.SAVED, first.manager.save("Prima"))
        assertEquals(SessionSaveResult.SAVED, second.manager.save("Seconda"))
        assertFalse(first.manager.hasUnsavedChanges)
        assertFalse(second.manager.hasUnsavedChanges)
        val firstTurn = first.battle.turnIndex
        val secondTurn = second.battle.turnIndex
        val secondCanUndo = second.battle.canUndo

        workspace.activate(first.id)
        first.battle.endTurn()

        assertTrue(first.manager.hasUnsavedChanges)
        assertFalse(second.manager.hasUnsavedChanges)
        assertTrue(first.battle.turnIndex != firstTurn)
        assertEquals(secondTurn, second.battle.turnIndex)
        assertTrue(first.battle.canUndo)

        first.battle.undo()

        assertEquals(firstTurn, first.battle.turnIndex)
        assertEquals(secondTurn, second.battle.turnIndex)
        assertEquals(secondCanUndo, second.battle.canUndo)
    }

    @Test
    fun `la presentazione iniziale viene adottata dalla prima scheda`() {
        val workspace = SessionWorkspace(
            store = store(),
            initialSession = SampleEncounter.startedSession(seed = 12L),
            initialDisplayName = "Con configurazione",
            initialPresentation = mapOf("rollMode" to D20Mode.ADVANTAGE.name),
        )

        assertEquals(D20Mode.ADVANTAGE, workspace.activeSession.battle.rollMode)
    }

    @Test
    fun `save e open ripristinano difficolta e sospensione cpu senza replay`() {
        val store = store()
        val source = SessionWorkspace(
            store = store,
            initialSession = mixedCpuSession(seed = 13L),
            initialDisplayName = "CPU persistita",
            initialPresentation = mapOf(
                "enemyCpuDifficulty" to EnemyCpuDifficulty.SORRY_FOR_YOU.name,
            ),
        )
        val sourceBattle = source.activeSession.battle
        sourceBattle.playEnemyCpuTurn()
        sourceBattle.undo()
        assertTrue(sourceBattle.enemyCpuTurnSuppressed)
        assertEquals(SessionSaveResult.SAVED, source.activeSession.manager.save("CPU persistita"))

        val target = workspace(store)
        val summary = store.list().single { it.slug == "cpu-persistita" }
        assertEquals(WorkspaceOpenResult.OPENED, target.openSaved(summary))

        val restored = target.activeSession.battle
        assertEquals(EnemyCpuDifficulty.SORRY_FOR_YOU, restored.enemyCpuDifficulty)
        assertTrue(restored.enemyCpuTurnSuppressed)
        assertFalse(restored.shouldScheduleEnemyCpu)
    }

    @Test
    fun `save e open conservano il guard del batch mixed completato`() {
        val store = store()
        val source = SessionWorkspace(
            store = store,
            initialSession = mixedCpuSession(14L),
            initialDisplayName = "CPU mixed",
            initialPresentation = mapOf(
                "enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name,
            ),
        )
        source.activeSession.battle.playEnemyCpuTurn()
        assertTrue(source.activeSession.battle.enemyCpuBatchCompleted)
        assertEquals(SessionSaveResult.SAVED, source.activeSession.manager.save("CPU mixed"))

        val target = workspace(store)
        val summary = store.list().single { it.slug == "cpu-mixed" }
        assertEquals(WorkspaceOpenResult.OPENED, target.openSaved(summary))

        val restored = target.activeSession.battle
        assertTrue(restored.enemyCpuBatchCompleted)
        assertFalse(restored.shouldScheduleEnemyCpu)
        assertEquals("hero", restored.activeActorId)
    }

    @Test
    fun `aprire un file non sostituisce la bozza attiva`() {
        val store = store()
        store.save("Archivio", SampleEncounter.startedSession(seed = 31L), emptyMap())
        val summary = store.list().single()
        val workspace = workspace(store)
        val draft = workspace.activeSession

        val result = workspace.openSaved(summary)

        assertEquals(WorkspaceOpenResult.OPENED, result)
        assertEquals(2, workspace.openSessions.size)
        assertTrue(workspace.openSessions.any { it === draft })
        assertTrue(draft.manager.hasUnsavedChanges)
        assertEquals(summary.slug, workspace.activeSession.manager.currentSlug)
    }

    @Test
    fun `lo stesso slug viene attivato invece di essere aperto due volte`() {
        val store = store()
        store.save("Unica", SampleEncounter.startedSession(seed = 41L), emptyMap())
        val summary = store.list().single()
        val workspace = workspace(store)

        assertEquals(WorkspaceOpenResult.OPENED, workspace.openSaved(summary))
        val opened = workspace.activeSession
        val count = workspace.openSessions.size
        workspace.activate(workspace.openSessions.first { it.id != opened.id }.id)

        assertEquals(WorkspaceOpenResult.ALREADY_OPEN, workspace.openSaved(summary))
        assertEquals(count, workspace.openSessions.size)
        assertSame(opened, workspace.activeSession)
    }

    @Test
    fun `una scheda non puo sovrascrivere il file posseduto da un'altra`() {
        val workspace = workspace()
        val first = workspace.activeSession
        assertEquals(SessionSaveResult.SAVED, first.manager.save("Prima"))
        val second = workspace.openNew(
            SampleEncounter.startedSession(seed = 52L),
            "Seconda",
        )
        assertEquals(SessionSaveResult.SAVED, second.manager.save("Seconda"))

        val result = second.manager.save("Prima", overwriteExisting = true)
        second.manager.delete(store().list().first { it.slug == "prima" })

        assertEquals(SessionSaveResult.OPEN_IN_ANOTHER_TAB, result)
        assertEquals("seconda", second.manager.currentSlug)
        assertTrue(second.manager.status!!.contains("Chiudi prima"))
        assertEquals("Prima", store().load("prima").summary().displayName)
    }

    @Test
    fun `il caricamento diretto non puo sottrarre il file a un'altra scheda`() {
        val workspace = workspace()
        val first = workspace.activeSession
        assertEquals(SessionSaveResult.SAVED, first.manager.save("Prima"))
        val firstSummary = store().list().single { it.slug == "prima" }
        val second = workspace.openNew(
            SampleEncounter.startedSession(seed = 57L),
            "Seconda",
        )
        assertEquals(SessionSaveResult.SAVED, second.manager.save("Seconda"))
        val secondEncounterId = second.battle.state.encounterId()

        val result = second.manager.requestLoad(firstSummary)

        assertEquals(SessionLoadResult.FAILED, result)
        assertEquals("seconda", second.manager.currentSlug)
        assertEquals(secondEncounterId, second.battle.state.encounterId())
        assertTrue(second.manager.status!!.contains("altra scheda"))
    }

    @Test
    fun `la chiusura protegge le modifiche e non elimina il salvataggio`() {
        val workspace = workspace()
        val first = workspace.activeSession
        assertEquals(SessionSaveResult.SAVED, first.manager.save("Prima"))
        workspace.openNew(SampleEncounter.startedSession(seed = 62L), "Seconda")
        first.battle.endTurn()

        assertEquals(WorkspaceCloseResult.UNSAVED_CHANGES, workspace.requestClose(first.id))
        assertTrue(workspace.openSessions.any { it.id == first.id })
        assertEquals(
            WorkspaceCloseResult.CLOSED,
            workspace.requestClose(first.id, discardUnsavedChanges = true),
        )
        assertTrue(store().exists("prima"))
        assertTrue(workspace.openSessions.none { it.id == first.id })
    }

    @Test
    fun `l'ultima scheda resta disponibile al workspace`() {
        val workspace = workspace()

        assertEquals(
            WorkspaceCloseResult.LAST_SESSION,
            workspace.requestClose(workspace.activeSession.id, discardUnsavedChanges = true),
        )
        assertEquals(1, workspace.openSessions.size)
    }

    @Test
    fun `la taglia del compendio viene fotografata nella nuova sessione`() {
        var footprint = 2
        val workspace = SessionWorkspace(
            store = store(),
            initialSession = SampleEncounter.startedSession(seed = 71L),
            initialDisplayName = "Taglie",
            battleFactory = { session ->
                BattleViewModel(session, footprintProvider = { footprint })
            },
        )
        val battle = workspace.activeSession.battle
        val unplaced = battle.state.combatants().keys.first { battle.placementOf(it) == null }

        footprint = 4

        assertEquals(2, battle.squaresPerSideFor(unplaced))
        assertTrue(battle.presentationState()["footprints"]!!.contains("$unplaced=2"))
    }

    @Test
    fun `flush autosave salva anche le schede non attive`() {
        val store = store()
        val workspace = workspace(store)
        val first = workspace.activeSession
        first.manager.save("Prima")
        val second = workspace.openNew(SampleEncounter.startedSession(seed = 82L), "Seconda")
        second.manager.save("Seconda")
        first.battle.endTurn()
        second.battle.endTurn()
        workspace.activate(first.id)

        workspace.flushAutosaves()

        assertFalse(first.manager.hasUnsavedChanges)
        assertFalse(second.manager.hasUnsavedChanges)
        assertEquals(first.battle.turnIndex, store.load("prima").session.currentState().turnIndex())
        assertEquals(second.battle.turnIndex, store.load("seconda").session.currentState().turnIndex())
    }

    @Test
    fun `un errore autosave resta visibile passando a un'altra scheda`() {
        val workspace = workspace()
        val first = workspace.activeSession
        assertEquals(SessionSaveResult.SAVED, first.manager.save("Prima"))
        val second = workspace.openNew(SampleEncounter.startedSession(seed = 91L), "Seconda")
        assertEquals(SessionSaveResult.SAVED, second.manager.save("Seconda"))

        first.manager.currentName = "Prima rinominata"
        assertEquals(SessionSaveResult.NAME_COLLISION, workspace.flushAutosave(first))
        workspace.activate(second.id)
        assertTrue(workspace.autosaveWarning!!.contains("Prima"))

        second.manager.currentName = "Seconda rinominata"
        assertEquals(SessionSaveResult.NAME_COLLISION, workspace.flushAutosave(second))
        assertTrue(workspace.autosaveWarning!!.contains("2 autosave"))

        assertEquals(SessionSaveResult.SAVED, first.manager.save("Prima"))
        workspace.reconcileAutosaveWarning()
        assertTrue(workspace.autosaveWarning!!.contains("Seconda"))

        assertEquals(SessionSaveResult.SAVED, second.manager.save("Seconda"))
        workspace.reconcileAutosaveWarning()
        assertEquals(null, workspace.autosaveWarning)
    }
}
