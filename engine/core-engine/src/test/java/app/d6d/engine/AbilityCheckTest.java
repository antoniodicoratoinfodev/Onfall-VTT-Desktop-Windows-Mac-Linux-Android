package app.d6d.engine;

import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.CombatEvent;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.D20RollInput;
import app.d6d.domain.combat.D20RollResult;
import app.d6d.domain.combat.EventType;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.domain.combat.TurnBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityCheckTest {

    private CombatSession activeSession(long seed, boolean armorPenalty) {
        ActorDefinition actor = ActorDefinition.builder("hero-definition", "Hero")
                .maxHitPoints(20)
                .initiativeScore(20)
                .strengthDexterityD20Disadvantage(armorPenalty)
                .build();
        ActorDefinition other = ActorDefinition.builder("other-definition", "Other")
                .maxHitPoints(20)
                .initiativeScore(10)
                .build();
        CombatSession session = CombatSession.create("ability-check", seed);
        session.addCombatant("hero", actor);
        session.addCombatant("other", other);
        session.setInitiative("hero", 20);
        session.setInitiative("other", 10);
        session.markReady();
        session.start();
        return session;
    }

    @Test
    void armorDisadvantageAppliesOnlyToStrengthAndDexterityAndCancelsAdvantage() {
        CombatSession session = activeSession(71L, true);

        D20RollResult strength = session.rollAbilityCheck(
                "hero",
                SaveAbility.STRENGTH,
                3,
                D20RollInput.digital(D20Mode.NORMAL));
        D20RollResult dexterity = session.rollAbilityCheck(
                "hero",
                SaveAbility.DEXTERITY,
                3,
                D20RollInput.digital(D20Mode.ADVANTAGE));
        D20RollResult wisdom = session.rollAbilityCheck(
                "hero",
                SaveAbility.WISDOM,
                3,
                D20RollInput.digital(D20Mode.NORMAL));

        assertEquals(D20Mode.DISADVANTAGE, strength.mode());
        assertEquals(2, strength.dice().size());
        assertEquals(D20Mode.NORMAL, dexterity.mode());
        assertEquals(1, dexterity.dice().size());
        assertEquals(D20Mode.NORMAL, wisdom.mode());
        assertEquals(1, wisdom.dice().size());
    }

    @Test
    void exhaustionAdjustsTheModifierAndTheCheckDoesNotSpendAnAction() {
        CombatSession session = activeSession(72L, true);
        session.setExhaustion("hero", 2);
        TurnBudget before = session.currentState().turnBudgets().get("hero");

        D20RollResult result = session.rollAbilityCheck(
                "hero",
                SaveAbility.WISDOM,
                5,
                D20RollInput.manual(12));

        assertEquals(1, result.modifier(), "5 from the check and -4 from Exhaustion");
        assertEquals(13, result.total());
        assertEquals(before, session.currentState().turnBudgets().get("hero"));

        CombatEvent event = session.auditTrail().stream()
                .filter(candidate -> candidate.type() == EventType.ABILITY_CHECK_ROLLED)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals("WISDOM", event.details().get("ability"));
        assertEquals("5", event.details().get("requestedModifier"));
        assertEquals("1", event.details().get("modifier"));
        assertEquals("13", event.details().get("total"));
    }

    @Test
    void abilityCheckIsAuditedUndoableAndRestoresTheExactRandomSequence() {
        CombatSession session = activeSession(73L, false);
        CombatState before = session.currentState();

        D20RollResult first = session.rollAbilityCheck(
                "hero",
                SaveAbility.CHARISMA,
                -1,
                D20RollInput.digital(D20Mode.ADVANTAGE));

        assertTrue(session.auditTrail().stream()
                .anyMatch(event -> event.type() == EventType.ABILITY_CHECK_ROLLED));
        assertTrue(session.undo());
        assertEquals(before.randomState(), session.currentState().randomState());
        assertEquals(before.turnBudgets().get("hero"), session.currentState().turnBudgets().get("hero"));

        D20RollResult repeated = session.rollAbilityCheck(
                "hero",
                SaveAbility.CHARISMA,
                -1,
                D20RollInput.digital(D20Mode.ADVANTAGE));
        assertEquals(first, repeated);
    }
}
