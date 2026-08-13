package app.d6d.ui.state

import app.d6d.engine.ai.EnemyCpuDifficulty
import app.d6d.ui.content.SampleEncounter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Il turno CPU come lo gioca davvero l'applicazione.
 *
 * Le altre prove usano [BattleViewModel.playEnemyCpuTurn], che risolve tutto in
 * un fotogramma: comoda, ma non e' la strada dell'interfaccia. Qui si esercita
 * [BattleViewModel.playEnemyCpuTurnPaced] — pause fra un comando e il successivo,
 * annullamento a meta' riproduzione, consolidamento nel `finally` — e si verifica
 * che arrivi esattamente dove arriva quella immediata.
 */
class EnemyCpuPacedPlaybackTest {

    /** Due modelli nati dallo stesso seme devono restare confrontabili passo per passo. */
    private fun cpuReadyModel(seed: Long = 812L): BattleViewModel {
        val session = SampleEncounter.startedSession(seed = seed)
        val model = BattleViewModel(session)
        model.adopt(session, mapOf("enemyCpuDifficulty" to EnemyCpuDifficulty.MEDIUM.name))
        var advances = 0
        while (!model.shouldScheduleEnemyCpu && advances < model.turnGroups.size + 1) {
            model.endTurn()
            advances++
        }
        assertTrue(model.shouldScheduleEnemyCpu, "Il modello di prova non arriva a un turno CPU")
        return model
    }

    private fun hitPoints(model: BattleViewModel): Map<String, Int> =
        model.state.combatants().mapValues { (_, combatant) -> combatant.currentHitPoints() }

    @Test
    fun `il turno ritmato arriva dove arriva quello immediato`() = runTest {
        val immediato = cpuReadyModel()
        immediato.playEnemyCpuTurn()

        val ritmato = cpuReadyModel()
        ritmato.playEnemyCpuTurnPaced()

        assertEquals(immediato.state.revision(), ritmato.state.revision())
        assertEquals(immediato.round, ritmato.round)
        assertEquals(immediato.turnIndex, ritmato.turnIndex)
        assertEquals(hitPoints(immediato), hitPoints(ritmato))
    }

    @Test
    fun `annullare a meta' riproduzione non lascia il gruppo a meta' turno`() = runTest {
        val riferimento = cpuReadyModel()
        riferimento.playEnemyCpuTurn()

        val interrotto = cpuReadyModel()
        // UNDISPATCHED: la riproduzione parte subito e si ferma alla prima pausa
        // fra due comandi, che e' il punto in cui l'annullamento puo' arrivare.
        val job = launch(start = CoroutineStart.UNDISPATCHED) { interrotto.playEnemyCpuTurnPaced() }
        job.cancelAndJoin()

        // Chi annulla l'attesa smette di aspettare, non di giocare: il gruppo
        // nemico deve comunque concludere il proprio turno.
        assertEquals(riferimento.state.revision(), interrotto.state.revision())
        assertEquals(hitPoints(riferimento), hitPoints(interrotto))
    }

    @Test
    fun `la riproduzione rilascia sempre busy e il riferimento al playback`() = runTest {
        val model = cpuReadyModel()

        model.playEnemyCpuTurnPaced()

        assertFalse(model.enemyCpuBusy, "busy è rimasto acceso a turno concluso")
        assertNull(model.enemyCpuPlayback, "il playback concluso è rimasto agganciato al modello")
        assertNull(model.enemyCpuActingCombatantId)
    }

    @Test
    fun `anche dopo un annullamento busy viene rilasciato`() = runTest {
        val model = cpuReadyModel()

        val job = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }
        job.cancelAndJoin()

        assertFalse(model.enemyCpuBusy)
        assertNull(model.enemyCpuPlayback)
    }

    @Test
    fun `il turno giocato non resta in attesa di essere rigiocato`() = runTest {
        val model = cpuReadyModel()
        val turnoPrima = model.enemyCpuTurnKey

        model.playEnemyCpuTurnPaced()

        assertNotEquals(
            turnoPrima,
            model.enemyCpuTurnKey,
            "Il turno CPU appena giocato è ancora quello corrente: verrebbe rigiocato",
        )
    }

    @Test
    fun `una riproduzione istantanea non attende e resta equivalente`() = runTest {
        val riferimento = cpuReadyModel()
        riferimento.playEnemyCpuTurn()

        val istantaneo = cpuReadyModel()
        istantaneo.enemyCpuSpeed = EnemyCpuSpeed.INSTANT
        istantaneo.playEnemyCpuTurnPaced()

        assertEquals(riferimento.state.revision(), istantaneo.state.revision())
        assertEquals(hitPoints(riferimento), hitPoints(istantaneo))
        assertFalse(istantaneo.enemyCpuBusy)
    }

    @Test
    fun `settle conclude un turno lasciato fra due pause`() = runTest {
        val riferimento = cpuReadyModel()
        riferimento.playEnemyCpuTurn()

        val model = cpuReadyModel()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { model.playEnemyCpuTurnPaced() }

        // Chi sta per salvare in modo definitivo non puo' aspettare il ritmo:
        // conclude il gruppo e fotografa un turno intero.
        model.settleEnemyCpuTurn()

        assertFalse(model.enemyCpuBusy)
        assertEquals(riferimento.state.revision(), model.state.revision())
        job.cancelAndJoin()
    }
}
