package app.d6d.engine;

import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.ConditionDuration;
import app.d6d.domain.combat.ConditionExpiry;
import app.d6d.domain.combat.ConditionInstance;
import app.d6d.domain.combat.ConditionType;
import app.d6d.domain.combat.D20RollInput;
import app.d6d.domain.combat.DamageComponent;
import app.d6d.domain.combat.DamageResult;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.EventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionsAndConcentrationTest {
    @Test
    void duplicateConditionsKeepIndependentSourcesAndDurations() {
        CombatSession session = CombatFixtures.active(30L);
        session.applyCondition("goblin", new ConditionInstance(
                "poison-a", ConditionType.POISONED, "hero", "focus-1", 1,
                ConditionDuration.rounds(1), "", "first"));
        session.applyCondition("goblin", new ConditionInstance(
                "poison-b", ConditionType.POISONED, "goblin", "", 1,
                ConditionDuration.manual(), "", "second"));

        assertEquals(2, session.currentState().combatant("goblin").conditions().size());
        session.endTurn(); // hero -> goblin; target-end duration has not fired yet
        assertEquals(2, session.currentState().combatant("goblin").conditions().size());
        session.endTurn(); // goblin's end expires poison-a only

        List<ConditionInstance> remaining = session.currentState().combatant("goblin").conditions();
        assertEquals(1, remaining.size());
        assertEquals("poison-b", remaining.get(0).id());
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.CONDITION_EXPIRED));
    }

    @Test
    void sourceTurnDurationsExpireOnTheConfiguredBoundary() {
        CombatSession session = CombatFixtures.active(31L);
        session.applyCondition("goblin", new ConditionInstance(
                "fear", ConditionType.FRIGHTENED, "hero", "focus-1", 1,
                ConditionDuration.until(ConditionExpiry.END_OF_SOURCE_TURN, 1), "", ""));

        session.endTurn();

        assertTrue(session.currentState().combatant("goblin").conditions().isEmpty());
    }

    @Test
    void conditionImmunityRejectsApplicationButAuditsTheDecision() {
        ActorDefinition immuneActor = ActorDefinition.builder("immune", "Immune")
                .maxHitPoints(10)
                .conditionImmunities(Set.of(ConditionType.POISONED))
                .build();
        CombatSession session = CombatSession.create("immunity", 32L);
        session.addCombatant("hero", CombatFixtures.hero());
        session.addCombatant("immune", immuneActor);
        session.setInitiative("hero", 20);
        session.setInitiative("immune", 10);
        session.markReady();
        session.start();

        boolean applied = session.applyCondition("immune", ConditionInstance.manual(
                "poison", ConditionType.POISONED, "hero", "focus-1", 1));

        assertFalse(applied);
        assertTrue(session.currentState().combatant("immune").conditions().isEmpty());
        assertEquals(EventType.CONDITION_IMMUNE,
                session.auditTrail().get(session.auditTrail().size() - 1).type());
    }

    @Test
    void aConditionCannotReferenceAnUnknownSource() {
        CombatSession session = CombatFixtures.active(320L);
        var before = session.currentState();
        var auditBefore = session.auditTrail();

        assertThrows(CombatRuleException.class, () -> session.applyCondition("goblin",
                ConditionInstance.manual("ghost-condition", ConditionType.PRONE,
                        "missing-source", "", 1)));

        assertEquals(before, session.currentState());
        assertEquals(auditBefore, session.auditTrail());
        assertFalse(session.canUndo());
    }

    @Test
    void replacingConcentrationExpiresEveryDependentCondition() {
        CombatSession session = CombatFixtures.active(33L);
        session.beginConcentration("hero", "focus-1");
        session.applyCondition("goblin", new ConditionInstance(
                "held", ConditionType.RESTRAINED, "hero", "focus-1", 1,
                ConditionDuration.concentration(), "hero", ""));

        session.beginConcentration("hero", "focus-2");

        assertEquals("focus-2", session.currentState().combatant("hero")
                .activeConcentration().orElseThrow().abilityId());
        assertTrue(session.currentState().combatant("goblin").conditions().isEmpty());
        assertTrue(session.auditTrail().stream().anyMatch(event ->
                event.type() == EventType.CONCENTRATION_ENDED
                        && "replaced".equals(event.details().get("reason"))));
    }

    @Test
    void concentrationUsesCappedHalfDamageDcAndAcceptsManualSaves() {
        ActorDefinition durableHero = ActorDefinition.builder("durable", "Durable hero")
                .maxHitPoints(100)
                .constitutionSaveBonus(2)
                .abilities(CombatFixtures.hero().abilities())
                .build();
        CombatSession failed = activeWithHero(durableHero, 34L);
        failed.beginConcentration("hero", "focus-1");
        failed.applyCondition("goblin", new ConditionInstance(
                "held", ConditionType.RESTRAINED, "hero", "focus-1", 1,
                ConditionDuration.concentration(), "hero", ""));
        DamageResult failedResult = failed.applyDamage("goblin", "hero",
                List.of(new DamageComponent(DamageType.FORCE, 60)), false, D20RollInput.manual(1));

        assertEquals(30, failedResult.concentrationCheck().orElseThrow().difficultyClass());
        assertFalse(failedResult.concentrationCheck().orElseThrow().maintained());
        assertTrue(failed.currentState().combatant("hero").activeConcentration().isEmpty());
        assertTrue(failed.currentState().combatant("goblin").conditions().isEmpty());

        CombatSession maintained = CombatFixtures.active(35L);
        maintained.beginConcentration("hero", "focus-1");
        DamageResult maintainedResult = maintained.applyDamage("goblin", "hero",
                List.of(new DamageComponent(DamageType.FORCE, 4)), false, D20RollInput.manual(20));

        assertEquals(10, maintainedResult.concentrationCheck().orElseThrow().difficultyClass());
        assertTrue(maintainedResult.concentrationCheck().orElseThrow().maintained());
        assertTrue(maintained.currentState().combatant("hero").activeConcentration().isPresent());
    }

    @Test
    void zeroHitPointsAndIncapacitatingConditionsEndConcentrationWithoutASave() {
        CombatSession zero = CombatFixtures.active(36L);
        zero.beginConcentration("hero", "focus-1");
        DamageResult result = zero.applyDamage("goblin", "hero",
                List.of(new DamageComponent(DamageType.FORCE, 100)), false, D20RollInput.manual(20));
        assertTrue(result.concentrationCheck().isEmpty());
        assertTrue(zero.currentState().combatant("hero").activeConcentration().isEmpty());

        CombatSession stunned = CombatFixtures.active(37L);
        stunned.beginConcentration("hero", "focus-1");
        stunned.applyCondition("hero", ConditionInstance.manual(
                "stun", ConditionType.STUNNED, "goblin", "", 1));
        assertTrue(stunned.currentState().combatant("hero").activeConcentration().isEmpty());
    }

    private static CombatSession activeWithHero(ActorDefinition hero, long seed) {
        CombatSession session = CombatSession.create("concentration", seed);
        session.addCombatant("hero", hero);
        session.addCombatant("goblin", CombatFixtures.goblin());
        session.setInitiative("hero", 20);
        session.setInitiative("goblin", 10);
        session.markReady();
        session.start();
        return session;
    }
}
