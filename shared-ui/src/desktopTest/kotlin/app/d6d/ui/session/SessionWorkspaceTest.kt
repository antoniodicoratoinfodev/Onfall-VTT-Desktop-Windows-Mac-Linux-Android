package app.d6d.ui.session

import app.d6d.domain.combat.D20Mode
import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.MapGrid
import app.d6d.engine.CombatSession
import app.d6d.engine.ai.EnemyCpuDifficulty
import app.d6d.persistence.session.SessionArchiveStore
import app.d6d.ui.content.SampleEncounter
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.state.EnemyCpuSpeed
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotEquals
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
    ).apply {
        openNew(SampleEncounter.startedSession(seed = 11L), "Prima")
    }

    @Test
    fun `due schede mantengono motore undo e stato sporco indipendenti`() {
        val workspace = workspace()
        val first = workspace.active
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
        ).apply {
            openNew(SampleEncounter.startedSession(seed = 12L), "Con configurazione", mapOf("rollMode" to D20Mode.ADVANTAGE.name))
        }

        assertEquals(D20Mode.ADVANTAGE, workspace.active.battle.rollMode)
    }

    @Test
    fun `save e open ripristinano difficolta e sospensione cpu senza replay`() {
        val store = store()
        val source = SessionWorkspace(store = store).apply {
            openNew(
                mixedCpuSession(seed = 13L),
                "CPU persistita",
                mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.SORRY_FOR_YOU.name),
            )
        }
        val sourceBattle = source.active.battle
        sourceBattle.playEnemyCpuTurn()
        sourceBattle.undo()
        assertTrue(sourceBattle.enemyCpuTurnSuppressed)
        assertEquals(SessionSaveResult.SAVED, source.active.manager.save("CPU persistita"))

        val target = workspace(store)
        val summary = store.list().single { it.slug == "cpu-persistita" }
        assertEquals(WorkspaceOpenResult.OPENED, target.openSaved(summary))

        val restored = target.active.battle
        assertEquals(EnemyCpuDifficulty.SORRY_FOR_YOU, restored.enemyCpuDifficulty)
        assertTrue(restored.enemyCpuTurnSuppressed)
        assertFalse(restored.shouldScheduleEnemyCpu)
    }

    @Test
    fun `save e open conservano il guard del batch mixed completato`() {
        val store = store()
        val source = SessionWorkspace(store = store).apply {
            openNew(
                mixedCpuSession(14L),
                "CPU mixed",
                mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name),
            )
        }
        source.active.battle.playEnemyCpuTurn()
        assertTrue(source.active.battle.enemyCpuBatchCompleted)
        assertEquals(SessionSaveResult.SAVED, source.active.manager.save("CPU mixed"))

        val target = workspace(store)
        val summary = store.list().single { it.slug == "cpu-mixed" }
        assertEquals(WorkspaceOpenResult.OPENED, target.openSaved(summary))

        val restored = target.active.battle
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
        val draft = workspace.active

        val result = workspace.openSaved(summary)

        assertEquals(WorkspaceOpenResult.OPENED, result)
        assertEquals(2, workspace.openSessions.size)
        assertTrue(workspace.openSessions.any { it === draft })
        assertTrue(draft.manager.hasUnsavedChanges)
        assertEquals(summary.slug, workspace.active.manager.currentSlug)
    }

    @Test
    fun `lo stesso slug viene attivato invece di essere aperto due volte`() {
        val store = store()
        store.save("Unica", SampleEncounter.startedSession(seed = 41L), emptyMap())
        val summary = store.list().single()
        val workspace = workspace(store)

        assertEquals(WorkspaceOpenResult.OPENED, workspace.openSaved(summary))
        val opened = workspace.active
        val count = workspace.openSessions.size
        workspace.activate(workspace.openSessions.first { it.id != opened.id }.id)

        assertEquals(WorkspaceOpenResult.ALREADY_OPEN, workspace.openSaved(summary))
        assertEquals(count, workspace.openSessions.size)
        assertSame(opened, workspace.active)
    }

    @Test
    fun `una scheda non puo sovrascrivere il file posseduto da un'altra`() {
        val workspace = workspace()
        val first = workspace.active
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
        val first = workspace.active
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
        val first = workspace.active
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
    fun `un workspace appena creato non ha alcuna partita aperta`() {
        val workspace = SessionWorkspace(store = store())

        assertNull(workspace.activeSession)
        assertFalse(workspace.hasOpenSessions)
        assertTrue(workspace.openSessions.isEmpty())
    }

    @Test
    fun `chiudere l'ultima scheda riporta il workspace a vuoto`() {
        val workspace = workspace()

        assertEquals(
            WorkspaceCloseResult.CLOSED,
            workspace.requestClose(workspace.active.id, discardUnsavedChanges = true),
        )
        assertNull(workspace.activeSession)
        assertFalse(workspace.hasOpenSessions)
    }

    @Test
    fun `da vuoto si riapre una partita e torna a esserci una sessione attiva`() {
        val workspace = SessionWorkspace(store = store())

        val aperta = workspace.openNew(SampleEncounter.startedSession(seed = 31L), "Ripartenza")

        assertSame(aperta, workspace.active)
        assertTrue(workspace.hasOpenSessions)
    }

    @Test
    fun `l'archivio e' sfogliabile anche senza partite aperte`() {
        val store = store()
        val preparato = SessionWorkspace(store = store)
            .apply { openNew(SampleEncounter.startedSession(seed = 32L), "Salvata") }
        assertEquals(SessionSaveResult.SAVED, preparato.active.manager.save("Salvata"))

        val vuoto = SessionWorkspace(store = store)

        assertNull(vuoto.activeSession)
        assertEquals(listOf("Salvata"), vuoto.savedSessions.map { it.displayName })
    }

    @Test
    fun `la taglia del compendio viene fotografata nella nuova sessione`() {
        var footprint = 2
        val workspace = SessionWorkspace(
            store = store(),
            battleFactory = { session ->
                BattleViewModel(session, footprintProvider = { footprint })
            },
        ).apply {
            openNew(SampleEncounter.startedSession(seed = 71L), "Taglie")
        }
        val battle = workspace.active.battle
        val unplaced = battle.state.combatants().keys.first { battle.placementOf(it) == null }

        footprint = 4

        assertEquals(2, battle.squaresPerSideFor(unplaced))
        assertTrue(battle.presentationState()["footprints"]!!.contains("$unplaced=2"))
    }

    @Test
    fun `flush autosave salva anche le schede non attive`() {
        val store = store()
        val workspace = workspace(store)
        val first = workspace.active
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
    fun `persistenza rifiuta mezzo playback e prepare lo consolida prima del disco`() = runTest {
        val store = store()
        val workspace = SessionWorkspace(
            store = store,
        ).apply {
            openNew(mixedCpuSession(seed = 83L), "CPU autosave", mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        }
        val opened = workspace.active
        assertEquals(SessionSaveResult.SAVED, opened.manager.save("CPU autosave"))
        opened.battle.enemyCpuSpeed = EnemyCpuSpeed.SLOW

        val turn = launch(start = CoroutineStart.UNDISPATCHED) {
            opened.battle.playEnemyCpuTurnPaced()
        }
        assertTrue(opened.battle.enemyCpuBusy)

        // Il solo writer I/O non deve mai avanzare il modello dal proprio thread.
        assertEquals(SessionSaveResult.FAILED, opened.manager.flushAutosave())
        assertTrue(opened.battle.enemyCpuBusy)

        val prepared = workspace.prepareForPersistence()
        assertFalse(opened.battle.enemyCpuBusy)
        workspace.flushAutosaves(prepared)
        assertFalse(opened.manager.hasUnsavedChanges)
        turn.cancelAndJoin()

        val archived = store.load("cpu-autosave")
        assertEquals(opened.battle.state, archived.session.currentState())
        assertEquals(opened.battle.presentationState(), archived.presentation)
    }

    @Test
    fun `un errore autosave resta visibile passando a un'altra scheda`() {
        val workspace = workspace()
        val first = workspace.active
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

    @Test
    fun `la dormienza senza revisione invalida comunque la bozza di recupero`() {
        val session = SampleEncounter.startedSession(seed = 101L)
        val hero = session.currentState().partyCombatantIds().first()
        val enemy = session.currentState().rosterOrder().first { it !in session.currentState().partyCombatantIds() }
        session.configureMap(MapGrid.standard(40, 12))
        session.placeCombatant(hero, GridPosition(0, 0), 1)
        session.placeCombatant(enemy, GridPosition(30, 0), 1)
        session.setDormantCombatants(listOf(enemy))
        val workspace = SessionWorkspace(store = store()).apply {
            openNew(session, "Recupero attivazione")
        }
        val before = workspace.recoveryKey()

        workspace.active.battle.setDormant(enemy, false)

        val after = workspace.recoveryKey()
        val beforeSession = before.sessions.single()
        val afterSession = after.sessions.single()
        assertNotEquals(before, after)
        assertEquals(beforeSession.stateRevision, afterSession.stateRevision)
        assertTrue(enemy in beforeSession.dormantCombatantIds)
        assertFalse(enemy in afterSession.dormantCombatantIds)
    }
}

/** Nei test la sessione attiva e' sempre attesa: qui l'assenza e' un fallimento. */
private val SessionWorkspace.active: OpenGameSession
    get() = requireNotNull(activeSession) { "Il workspace non ha alcuna sessione aperta" }
