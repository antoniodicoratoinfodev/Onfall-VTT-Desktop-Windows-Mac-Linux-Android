package app.d6d.ui.battle

import app.d6d.domain.combat.CombatEvent
import app.d6d.domain.combat.EventType
import app.d6d.ui.content.SampleEncounter
import app.d6d.ui.state.BattleViewModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BattleLogTest {

    private fun model() = BattleViewModel(SampleEncounter.startedSession(seed = 4242L))

    private fun event(
        type: EventType,
        actorId: String,
        targetId: String = "",
        details: Map<String, String>,
    ) = CombatEvent(1, 1, type, 1, actorId, targetId, details)

    @Test
    fun `un attacco mancato mostra nomi abilita dado modificatore totale e CA`() {
        val model = model()
        val actor = model.activeCombatantId!!
        val target = model.effectiveTargetId()!!
        val ability = model.abilities(actor).first { !it.isArea }
        val description = event(
            EventType.ATTACK_MISSED,
            actor,
            target,
            mapOf(
                "abilityId" to ability.id(),
                "abilityName" to ability.name(),
                "source" to "DIGITAL",
                "mode" to "NORMAL",
                "dice" to "[3]",
                "natural" to "3",
                "modifier" to "7",
                "total" to "10",
                "armorClass" to "13",
            ),
        ).describeInItalian(model)

        assertEquals(
            "${model.name(actor)} manca ${model.name(target)} con «${ability.name()}»: " +
                "d20 3 + 7 = 10 contro CA 13",
            description,
        )
    }

    @Test
    fun `il tiro danni mostra formula valori dei dadi modificatore e tipo`() {
        val model = model()
        val actor = model.activeCombatantId!!
        val target = model.effectiveTargetId()!!
        val description = event(
            EventType.DAMAGE_ROLLED,
            actor,
            target,
            mapOf(
                "abilityName" to "Arco lungo",
                "type" to "PIERCING",
                "formula" to "1d8+3",
                "dice" to "[5]",
                "modifier" to "3",
                "amount" to "8",
                "total" to "8",
            ),
        ).describeInItalian(model)

        assertTrue(description.contains("1d8+3 · dadi [5] + 3 = 8"))
        assertTrue(description.contains("perforante"))
        assertTrue(description.contains(model.name(target)))
    }

    @Test
    fun `condizioni e concentrazione leggono le chiavi registrate dal motore`() {
        val model = model()
        val actor = model.activeCombatantId!!
        val target = model.effectiveTargetId()!!

        val condition = event(
            EventType.CONDITION_APPLIED,
            actor,
            target,
            mapOf(
                "condition" to "PRONE",
                "remaining" to "2",
                "expiry" to "END_OF_TARGET_TURN",
            ),
        ).describeInItalian(model)
        val concentration = event(
            EventType.CONCENTRATION_CHECKED,
            target,
            details = mapOf(
                "source" to "DIGITAL",
                "mode" to "NORMAL",
                "dice" to "[12]",
                "natural" to "12",
                "modifier" to "2",
                "total" to "14",
                "difficultyClass" to "10",
                "maintained" to "true",
            ),
        ).describeInItalian(model)

        assertTrue(condition.contains("prono"))
        assertTrue(condition.contains("durata residua 2"))
        assertTrue(concentration.contains("d20 12 + 2 = 14 contro CD 10"))
        assertTrue(concentration.endsWith("mantenuta"))
    }
}
