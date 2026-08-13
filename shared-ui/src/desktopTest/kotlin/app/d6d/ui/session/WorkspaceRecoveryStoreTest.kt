package app.d6d.ui.session

import app.d6d.persistence.session.SessionArchiveStore
import app.d6d.engine.CombatSession
import app.d6d.engine.ai.EnemyCpuDifficulty
import app.d6d.ui.content.SampleEncounter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class WorkspaceRecoveryStoreTest {

    @TempDir
    lateinit var directory: Path

    private fun mixedCpuSession(seed: Long): CombatSession {
        val hero = SampleEncounter.party().first()
        val enemy = SampleEncounter.enemies().first()
        return CombatSession.create("mixed-recovery", seed).also { session ->
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

    @Test
    fun `la bozza atomica conserva sessioni presentazione e scheda attiva`() {
        val first = SampleEncounter.startedSession(seed = 301L)
        first.endTurn()
        val second = SampleEncounter.startedSession(seed = 302L)
        val store = WorkspaceRecoveryStore(directory)
        store.save(
            WorkspaceRecovery(
                activeIndex = 1,
                sessions = listOf(
                    RecoveredGameSession("Prima", "prima", first, mapOf("rollMode" to "MANUAL")),
                    RecoveredGameSession("Bozza", null, second, mapOf("editMode" to "true")),
                ),
            ),
        )

        val loaded = requireNotNull(store.load())
        assertNotNull(loaded)

        assertEquals(1, loaded.activeIndex)
        assertEquals(listOf("Prima", "Bozza"), loaded.sessions.map { it.displayName })
        assertEquals("prima", loaded.sessions.first().currentSlug)
        assertNull(loaded.sessions.last().currentSlug)
        assertEquals(first.currentState(), loaded.sessions.first().session.currentState())
        assertEquals(mapOf("editMode" to "true"), loaded.sessions.last().presentation)

        store.clear()
        assertNull(store.load())
        val recoveryBackups = directory.resolve("backups")
        if (Files.isDirectory(recoveryBackups)) {
            Files.list(recoveryBackups).use { files ->
                assertFalse(files.anyMatch { it.fileName.toString().startsWith("workspace-recovery-") })
            }
        }
    }

    @Test
    fun `il workspace riparte da tutte le schede recuperate senza dichiararle salvate`() {
        val archive = SessionArchiveStore(directory.resolve("sessions"))
        val source = SessionWorkspace(
            store = archive,
            refreshManagersOnCreate = false,
        ).apply {
            openNew(SampleEncounter.startedSession(seed = 311L), "Prima")
        }
        val first = source.active
        assertEquals(SessionSaveResult.SAVED, first.manager.save("Prima"))
        first.battle.endTurn()
        val firstTurn = first.battle.turnIndex
        val draft = source.openNew(SampleEncounter.startedSession(seed = 312L), "Bozza")
        source.activate(draft.id)

        val recoveryStore = WorkspaceRecoveryStore(directory)
        recoveryStore.save(source.recoverySnapshot())
        val recovered = requireNotNull(recoveryStore.load())
        assertNotNull(recovered)
        val target = SessionWorkspace(
            store = archive,
            refreshManagersOnCreate = false,
        ).apply {
            openNew(SampleEncounter.startedSession(seed = 999L), "Tavolo temporaneo")
        }

        assertTrue(target.restoreRecovery(recovered))

        assertEquals(2, target.openSessions.size)
        assertEquals("Bozza", target.active.displayName)
        val restoredFirst = target.openSessions.first { it.displayName == "Prima" }
        assertEquals("prima", restoredFirst.manager.currentSlug)
        assertEquals(firstTurn, restoredFirst.battle.turnIndex)
        assertTrue(restoredFirst.manager.hasCurrentSave)
        assertTrue(restoredFirst.manager.hasUnsavedChanges)
        assertNull(target.active.manager.currentSlug)
        assertTrue(target.active.manager.hasUnsavedChanges)
        assertFalse(target.status.isNullOrBlank())
    }

    @Test
    fun `il recovery serializza il clone catturato anche se il tavolo poi avanza`() {
        val source = SessionWorkspace(
            store = SessionArchiveStore(directory.resolve("sessions-frozen-recovery")),
            refreshManagersOnCreate = false,
        ).apply {
            openNew(SampleEncounter.startedSession(seed = 313L), "Recovery congelato")
        }
        val snapshot = source.recoverySnapshot()
        val capturedState = snapshot.sessions.single().session.currentState()

        // Prima del fix RecoveredGameSession conteneva la CombatSession viva e il
        // codec, eseguito dopo su I/O, avrebbe visto questa revisione successiva.
        source.active.battle.endTurn()
        val liveState = source.active.battle.state
        val recoveryStore = WorkspaceRecoveryStore(directory.resolve("frozen-recovery"))
        recoveryStore.save(snapshot)

        val restored = requireNotNull(recoveryStore.load()).sessions.single().session.currentState()
        assertEquals(capturedState, restored)
        assertTrue(restored != liveState)
    }

    @Test
    fun `il recovery conserva difficolta e sospensione cpu senza rieseguire il batch`() {
        val archive = SessionArchiveStore(directory.resolve("sessions-cpu"))
        val source = SessionWorkspace(
            store = archive,
            refreshManagersOnCreate = false,
        ).apply {
            openNew(
                mixedCpuSession(seed = 321L),
                "Recovery CPU",
                mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.EASY.name),
            )
        }
        val sourceBattle = source.active.battle
        sourceBattle.playEnemyCpuTurn()
        sourceBattle.undo()
        assertTrue(sourceBattle.enemyCpuTurnSuppressed)

        val recoveryStore = WorkspaceRecoveryStore(directory.resolve("recovery-cpu"))
        recoveryStore.save(source.recoverySnapshot())
        val recovered = requireNotNull(recoveryStore.load())
        val target = SessionWorkspace(
            store = archive,
            refreshManagersOnCreate = false,
        ).apply {
            openNew(SampleEncounter.startedSession(seed = 999L), "Temporanea")
        }

        assertTrue(target.restoreRecovery(recovered))
        val restored = target.active.battle
        assertEquals(EnemyCpuDifficulty.EASY, restored.enemyCpuDifficulty)
        assertTrue(restored.enemyCpuTurnSuppressed)
        assertFalse(restored.shouldScheduleEnemyCpu)
    }

    @Test
    fun `il recovery conserva il guard mixed completato senza replay`() {
        val archive = SessionArchiveStore(directory.resolve("sessions-cpu-mixed"))
        val source = SessionWorkspace(
            store = archive,
            refreshManagersOnCreate = false,
        ).apply {
            openNew(
                mixedCpuSession(322L),
                "Recovery CPU mixed",
                mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name),
            )
        }
        source.active.battle.playEnemyCpuTurn()
        assertTrue(source.active.battle.enemyCpuBatchCompleted)

        val recoveryStore = WorkspaceRecoveryStore(directory.resolve("recovery-cpu-mixed"))
        recoveryStore.save(source.recoverySnapshot())
        val recovered = requireNotNull(recoveryStore.load())
        val target = SessionWorkspace(
            store = archive,
            refreshManagersOnCreate = false,
        ).apply {
            openNew(SampleEncounter.startedSession(seed = 999L), "Temporanea")
        }

        assertTrue(target.restoreRecovery(recovered))
        val restored = target.active.battle
        assertTrue(restored.enemyCpuBatchCompleted)
        assertFalse(restored.shouldScheduleEnemyCpu)
        assertEquals("hero", restored.activeActorId)
    }
}

/** Nei test la sessione attiva e' sempre attesa: qui l'assenza e' un fallimento. */
private val SessionWorkspace.active: OpenGameSession
    get() = requireNotNull(activeSession) { "Il workspace non ha alcuna sessione aperta" }
