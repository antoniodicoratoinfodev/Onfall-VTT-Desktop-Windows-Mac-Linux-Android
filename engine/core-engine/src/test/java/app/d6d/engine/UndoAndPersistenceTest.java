package app.d6d.engine;

import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.AttackResult;
import app.d6d.domain.combat.CombatEvent;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.DamageComponent;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.EventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndoAndPersistenceTest {
    @Test
    void undoRestoresHitPointsTurnBudgetAndExactRngState() {
        CombatSession session = CombatFixtures.active(123456L);
        CombatState before = session.currentState();
        assertFalse(session.canUndo(), "setup/start commands are outside the live-play undo history");

        AttackResult first = session.attack(AttackRequest.digital(
                "hero", "goblin", "sword", D20Mode.ADVANTAGE));
        long stateAfterFirstAttack = session.currentState().randomState();
        int auditAfterFirstAttack = session.auditTrail().size();

        assertTrue(session.undo());
        CombatState restored = session.currentState();
        assertEquals(before.randomState(), restored.randomState());
        assertEquals(before.combatant("goblin").currentHitPoints(),
                restored.combatant("goblin").currentHitPoints());
        assertEquals(before.turnBudgets().get("hero"), restored.turnBudgets().get("hero"));
        assertEquals(auditAfterFirstAttack + 1, session.auditTrail().size());
        assertEquals(EventType.UNDO_PERFORMED,
                session.auditTrail().get(session.auditTrail().size() - 1).type());
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.ATTACK_ROLLED));

        AttackResult repeated = session.attack(AttackRequest.digital(
                "hero", "goblin", "sword", D20Mode.ADVANTAGE));
        assertEquals(first, repeated);
        assertEquals(stateAfterFirstAttack, session.currentState().randomState());
    }

    @Test
    void undoToRevisionRollsBackAWholeGroupOfCommandsWithoutCountingThem() {
        CombatSession session = CombatFixtures.active(123456L);
        CombatState before = session.currentState();
        long startingRevision = before.revision();

        session.attack(AttackRequest.digital("hero", "goblin", "sword", D20Mode.NORMAL));
        session.endTurn();
        session.endTurn();
        assertTrue(session.currentState().revision() > startingRevision);

        assertTrue(session.undoTo(startingRevision));

        CombatState restored = session.currentState();
        assertEquals(before.randomState(), restored.randomState());
        assertEquals(before.combatant("goblin").currentHitPoints(),
                restored.combatant("goblin").currentHitPoints());
        assertEquals(before.round(), restored.round());
        assertEquals(before.turnIndex(), restored.turnIndex());
        assertFalse(session.canUndo(), "the group was the whole live-play history");
    }

    @Test
    void undoToRevisionLeavesEarlierCommandsAloneAndReportsAnEmptyRollback() {
        CombatSession session = CombatFixtures.active(123456L);
        session.attack(AttackRequest.digital("hero", "goblin", "sword", D20Mode.NORMAL));
        CombatState afterFirst = session.currentState();
        long boundary = afterFirst.revision();

        session.endTurn();
        assertTrue(session.undoTo(boundary));

        assertEquals(afterFirst.combatant("goblin").currentHitPoints(),
                session.currentState().combatant("goblin").currentHitPoints());
        assertEquals(afterFirst.turnIndex(), session.currentState().turnIndex());
        assertTrue(session.canUndo(), "the command before the boundary survives");

        // Una revisione mai raggiunta non deve svuotare la pila per eccesso di zelo.
        assertFalse(session.undoTo(session.currentState().revision() + 100L));
    }

    @Test
    void anOverflowingAttackIsRejectedAtomically() {
        var oversized = app.d6d.domain.combat.AbilityDefinition.attack(
                "oversized", "Oversized", app.d6d.domain.combat.ActivationCost.ACTION,
                Integer.MAX_VALUE,
                app.d6d.domain.combat.DamageFormula.fixed(DamageType.FORCE, 1));
        var attacker = app.d6d.domain.combat.ActorDefinition.builder("attacker", "Attacker")
                .abilities(List.of(oversized)).build();
        CombatSession session = CombatSession.create("overflow-attack", 71L);
        session.addCombatant("attacker", attacker);
        session.addCombatant("target", CombatFixtures.goblin());
        session.setInitiative("attacker", 20);
        session.setInitiative("target", 10);
        session.markReady();
        session.start();
        CombatState before = session.currentState();
        List<CombatEvent> auditBefore = session.auditTrail();

        assertThrows(CombatRuleException.class, () -> session.attack(
                AttackRequest.manual("attacker", "target", "oversized", 2, List.of(1))));

        assertEquals(before, session.currentState());
        assertEquals(auditBefore, session.auditTrail());
        assertFalse(session.canUndo());
    }

    @Test
    void rejectedCommandsDoNotCreateEventsCheckpointsOrRandomness() {
        CombatSession session = CombatFixtures.active(99L);
        session.attack(AttackRequest.digital("hero", "goblin", "sword", D20Mode.NORMAL));
        CombatState beforeRejection = session.currentState();
        List<CombatEvent> auditBeforeRejection = session.auditTrail();

        assertThrows(CombatRuleException.class, () -> session.attack(
                AttackRequest.digital("hero", "goblin", "sword", D20Mode.NORMAL)));

        assertEquals(beforeRejection, session.currentState());
        assertEquals(auditBeforeRejection, session.auditTrail());
    }

    @Test
    void persistenceSnapshotRestoresStateAuditAndFutureRandomSequence() {
        CombatSession original = CombatFixtures.active(2024L);
        original.applyDamage("hero", "goblin",
                List.of(new DamageComponent(DamageType.FIRE, 3)), false);
        CombatState savedState = original.currentState();
        List<CombatEvent> savedAudit = original.auditTrail();

        CombatSession restored = CombatSession.restore(savedState, savedAudit);

        assertEquals(savedState, restored.currentState());
        assertEquals(savedAudit, restored.auditTrail());
        assertFalse(restored.canUndo(), "Checkpoints are intentionally session-local");
        AttackResult originalNext = original.attack(AttackRequest.digital(
                "hero", "goblin", "sword", D20Mode.NORMAL));
        AttackResult restoredNext = restored.attack(AttackRequest.digital(
                "hero", "goblin", "sword", D20Mode.NORMAL));
        assertEquals(originalNext, restoredNext);
        assertEquals(original.currentState().randomState(), restored.currentState().randomState());
    }
}
