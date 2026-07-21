package app.d6d.engine;

import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.DamageComponent;
import app.d6d.domain.combat.DamageResult;
import app.d6d.domain.combat.DamageType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageAndHealingTest {
    @Test
    void defensesAreAppliedPerComponentBeforeTemporaryHitPoints() {
        CombatSession session = activeWithDefendedTarget();

        DamageResult result = session.applyDamage("hero", "ward", List.of(
                new DamageComponent(DamageType.FIRE, 5),
                new DamageComponent(DamageType.COLD, 3),
                new DamageComponent(DamageType.POISON, 100)), false);

        assertEquals(108, result.totalRawDamage());
        assertEquals(8, result.totalAdjustedDamage());
        assertEquals(5, result.temporaryHitPointsAbsorbed());
        assertEquals(3, result.hitPointsLost());
        assertEquals(27, result.targetHitPointsAfter());
        assertEquals(2, result.components().get(0).adjustedAmount());
        assertTrue(result.components().get(0).resistant());
        assertEquals(6, result.components().get(1).adjustedAmount());
        assertTrue(result.components().get(1).vulnerable());
        assertEquals(0, result.components().get(2).adjustedAmount());
        assertTrue(result.components().get(2).immune());
        assertEquals(0, session.currentState().combatant("ward").temporaryHitPoints());
    }

    @Test
    void healingIsCappedAndTemporaryHitPointsNeverStack() {
        CombatSession session = activeWithDefendedTarget();
        session.applyDamage("hero", "ward", List.of(new DamageComponent(DamageType.UNTYPED, 10)), false);

        assertEquals(0, session.currentState().combatant("ward").temporaryHitPoints());
        assertEquals(25, session.currentState().combatant("ward").currentHitPoints());
        assertEquals(5, session.heal("ward", 100));
        assertEquals(30, session.currentState().combatant("ward").currentHitPoints());

        assertEquals(5, session.grantTemporaryHitPoints("ward", 5));
        assertEquals(5, session.grantTemporaryHitPoints("ward", 4));
        assertEquals(5, session.grantTemporaryHitPoints("ward", 2));
        assertEquals(7, session.grantTemporaryHitPoints("ward", 7));
    }

    @Test
    void oversizedDamageRollsBackStateAuditAndUndoCheckpoint() {
        CombatSession session = activeWithDefendedTarget();
        var before = session.currentState();
        var auditBefore = session.auditTrail();
        assertFalse(session.canUndo(), "start establishes the live-play undo baseline");

        assertThrows(CombatRuleException.class, () -> session.applyDamage("hero", "ward", List.of(
                new DamageComponent(DamageType.FORCE, Integer.MAX_VALUE),
                new DamageComponent(DamageType.COLD, Integer.MAX_VALUE)), false));

        assertEquals(before, session.currentState());
        assertEquals(auditBefore, session.auditTrail());
        assertFalse(session.canUndo());
        assertEquals(0, session.heal("ward", Integer.MAX_VALUE));
        assertEquals(30, session.currentState().combatant("ward").currentHitPoints());
    }

    @Test
    void simultaneousResistanceAndVulnerabilityAreBothRecorded() {
        ActorDefinition target = ActorDefinition.builder("mixed", "Mixed")
                .maxHitPoints(20)
                .resistances(Set.of(DamageType.FORCE))
                .vulnerabilities(Set.of(DamageType.FORCE))
                .build();
        CombatSession session = customActive(target, "mixed");

        DamageResult result = session.applyDamage("hero", "mixed",
                List.of(new DamageComponent(DamageType.FORCE, 5)), false);

        assertEquals(4, result.totalAdjustedDamage(), "floor(5 / 2) * 2");
        assertTrue(result.components().get(0).resistant());
        assertTrue(result.components().get(0).vulnerable());
        assertFalse(result.components().get(0).immune());
    }

    private static CombatSession activeWithDefendedTarget() {
        ActorDefinition target = ActorDefinition.builder("ward", "Ward")
                .maxHitPoints(30)
                .temporaryHitPoints(5)
                .resistances(Set.of(DamageType.FIRE))
                .vulnerabilities(Set.of(DamageType.COLD))
                .damageImmunities(Set.of(DamageType.POISON))
                .build();
        return customActive(target, "ward");
    }

    private static CombatSession customActive(ActorDefinition target, String targetId) {
        CombatSession session = CombatSession.create("damage", 20L);
        session.addCombatant("hero", CombatFixtures.hero());
        session.addCombatant(targetId, target);
        session.setInitiative("hero", 20);
        session.setInitiative(targetId, 10);
        session.markReady();
        session.start();
        return session;
    }
}
