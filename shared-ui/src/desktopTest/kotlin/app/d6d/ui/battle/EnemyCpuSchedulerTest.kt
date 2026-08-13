package app.d6d.ui.battle

import app.d6d.engine.ai.EnemyCpuDifficulty
import app.d6d.ui.content.SampleEncounter
import app.d6d.ui.state.BattleViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnemyCpuSchedulerTest {

    private fun cpuReadyModel(): BattleViewModel {
        val session = SampleEncounter.startedSession(seed = 812L)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        var advances = 0
        while (!model.shouldScheduleEnemyCpu && advances < model.turnGroups.size + 1) {
            model.endTurn()
            advances++
        }
        assertTrue(model.shouldScheduleEnemyCpu)
        return model
    }

    @Test
    fun `lo scheduler esegue soltanto la chiave cpu ancora corrente`() = runTest {
        val model = cpuReadyModel()
        val revisionBefore = model.state.revision()

        scheduleEnemyCpuTurnIfStillCurrent(model, model.enemyCpuTurnKey, delayMillis = 0L)

        assertTrue(model.state.revision() > revisionBefore)
    }

    @Test
    fun `una chiave obsoleta non esegue il batch cpu`() = runTest {
        val model = cpuReadyModel()
        val revisionBefore = model.state.revision()

        scheduleEnemyCpuTurnIfStillCurrent(model, "${model.enemyCpuTurnKey}-obsoleta", delayMillis = 0L)

        assertEquals(revisionBefore, model.state.revision())
        assertTrue(model.shouldScheduleEnemyCpu)
    }

    @Test
    fun `entrare in modifica durante il delay non esegue la cpu`() = runTest {
        val model = cpuReadyModel()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduleEnemyCpuTurnIfStillCurrent(model, model.enemyCpuTurnKey, delayMillis = 50L)
        }

        // Il tavolo apre la modalita' Modifica mentre l'attesa e' ancora pendente:
        // il ricontrollo dopo il delay deve fermare il batch, non solo l'annullamento.
        model.editMode = true
        val revisionBefore = model.state.revision()
        job.join()

        assertEquals(revisionBefore, model.state.revision())
        assertFalse(model.shouldScheduleEnemyCpu)
    }

    @Test
    fun `cancellare l effetto durante il delay non esegue la cpu`() = runTest {
        val model = cpuReadyModel()
        val revisionBefore = model.state.revision()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduleEnemyCpuTurnIfStillCurrent(model, model.enemyCpuTurnKey, delayMillis = 10_000L)
        }

        job.cancelAndJoin()

        assertEquals(revisionBefore, model.state.revision())
        assertTrue(model.shouldScheduleEnemyCpu)
    }
}
