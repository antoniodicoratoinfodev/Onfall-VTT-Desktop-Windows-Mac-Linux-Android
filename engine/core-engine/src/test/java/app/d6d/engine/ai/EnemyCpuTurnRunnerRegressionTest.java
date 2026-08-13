package app.d6d.engine.ai;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AutomationStatus;
import app.d6d.domain.combat.CombatStatus;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.engine.CombatSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyCpuTurnRunnerRegressionTest {

    @Test
    void finalAttackAndEnemyVictoryAreTwoSeparateAdvances() {
        CombatSession atomicSession = battle(1, 5, 101L);
        EnemyCpuResult atomic = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .actCurrentGroup(atomicSession);

        CombatSession steppedSession = battle(1, 5, 101L);
        EnemyCpuTurnRunner runner = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .startCurrentGroup(steppedSession);

        long beforeAttack = steppedSession.currentState().revision();
        assertTrue(runner.advance(), "il primo passo applica soltanto l'attacco");
        long afterAttack = steppedSession.currentState().revision();

        assertTrue(afterAttack > beforeAttack);
        assertEquals(CombatStatus.ACTIVE, steppedSession.currentState().status());
        assertEquals(0, steppedSession.currentState().combatant("hero").currentHitPoints());
        assertFalse(runner.finished());
        assertEquals(1, runner.reports().size());
        assertEquals(EnemyCpuActionType.ATTACK, runner.reports().get(0).type());

        assertFalse(runner.advance(), "la risoluzione conclude il runner nel passo successivo");
        long afterResolution = steppedSession.currentState().revision();

        assertTrue(afterResolution > afterAttack);
        assertTrue(runner.finished());
        assertEquals(CombatStatus.RESOLVED, steppedSession.currentState().status());
        assertEquals(List.of(EnemyCpuActionType.ATTACK, EnemyCpuActionType.ENCOUNTER_RESOLVED),
                runner.reports().stream().map(EnemyCpuActionReport::type).toList());
        assertEquals(runner.reports(), runner.result().actions());
        assertEquals(2, runner.result().checkpointCount());
        assertEquals(EnemyCpuOutcome.ENEMY_VICTORY, runner.result().outcome());

        assertEquals(atomic.actions(), runner.result().actions());
        assertEquals(atomic.outcome(), runner.result().outcome());
        assertEquals(atomic.checkpointCount(), runner.result().checkpointCount());
        assertEquals(atomic.endingRevision(), runner.result().endingRevision());
    }

    @Test
    void anExternalRevisionStopsTheRunnerWithoutClosingTheEnemyTurn() {
        CombatSession session = battle(20, 3, 102L);
        EnemyCpuTurnRunner runner = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .startCurrentGroup(session);

        assertTrue(runner.advance());
        long lastCpuRevision = session.currentState().revision();
        assertEquals(EnemyCpuActionType.ATTACK, runner.reports().get(0).type());

        session.grantTemporaryHitPoints("hero", 4);
        long externalRevision = session.currentState().revision();
        assertTrue(externalRevision > lastCpuRevision);

        assertFalse(runner.advance());

        assertTrue(runner.finished());
        assertEquals(EnemyCpuOutcome.REVISION_GUARD_TRIGGERED, runner.result().outcome());
        assertFalse(runner.result().turnAdvanced());
        assertEquals(lastCpuRevision, runner.result().endingRevision(),
                "il batch non deve attribuirsi la revisione esterna");
        assertEquals(externalRevision, session.currentState().revision());
        assertEquals(4, session.currentState().combatant("hero").temporaryHitPoints());
        assertEquals(List.of("enemy"), session.currentState().currentCombatantIds());
        assertEquals(runner.reports(), runner.result().actions());
        assertEquals(1, runner.result().checkpointCount());
        assertFalse(runner.reports().stream().anyMatch(report ->
                report.type() == EnemyCpuActionType.TURN_ENDED
                        || report.type() == EnemyCpuActionType.ENCOUNTER_RESOLVED));
    }

    @Test
    void anExternalRevisionAlsoCancelsAPendingEnemyVictory() {
        CombatSession session = battle(1, 5, 103L);
        EnemyCpuTurnRunner runner = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .startCurrentGroup(session);

        assertTrue(runner.advance());
        long lastCpuRevision = session.currentState().revision();
        assertEquals(0, session.currentState().combatant("hero").currentHitPoints());

        session.grantTemporaryHitPoints("hero", 2);
        assertFalse(runner.advance());

        assertEquals(EnemyCpuOutcome.REVISION_GUARD_TRIGGERED, runner.result().outcome());
        assertFalse(runner.result().encounterResolved());
        assertEquals(lastCpuRevision, runner.result().endingRevision());
        assertEquals(CombatStatus.ACTIVE, session.currentState().status());
        assertEquals(List.of(EnemyCpuActionType.ATTACK),
                runner.result().actions().stream().map(EnemyCpuActionReport::type).toList());
    }

    @Test
    void repeatedAdvanceAfterFinishIsAnIdempotentNoOp() {
        CombatSession session = battle(1, 5, 104L);
        EnemyCpuTurnRunner runner = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .startCurrentGroup(session);

        assertTrue(runner.advance());
        assertFalse(runner.advance());
        EnemyCpuResult completed = runner.result();
        List<EnemyCpuActionReport> completedReports = runner.reports();
        long completedRevision = session.currentState().revision();

        assertFalse(runner.advance());
        assertFalse(runner.advance());

        assertTrue(runner.finished());
        assertEquals(completed, runner.result());
        assertEquals(completedReports, runner.reports());
        assertEquals(completedRevision, session.currentState().revision());
    }

    @Test
    void reportsReturnsAnImmutableDetachedSnapshot() {
        CombatSession session = battle(1, 5, 105L);
        EnemyCpuTurnRunner runner = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .startCurrentGroup(session);

        assertTrue(runner.advance());
        List<EnemyCpuActionReport> afterAttack = runner.reports();

        assertEquals(1, afterAttack.size());
        assertThrows(UnsupportedOperationException.class, afterAttack::clear);

        assertFalse(runner.advance());
        assertEquals(1, afterAttack.size(), "lo snapshot precedente non diventa una vista live");
        assertEquals(2, runner.reports().size());
        assertEquals(runner.reports(), runner.result().actions());
    }

    @Test
    void anExternalUndoBetweenStepsStopsTheRunnerWithoutReplayingOrClosing() {
        CombatSession session = battle(20, 3, 106L);
        EnemyCpuTurnRunner runner = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .startCurrentGroup(session);
        int hitPointsBefore = session.currentState().combatant("hero").currentHitPoints();

        assertTrue(runner.advance());
        long lastCpuRevision = session.currentState().revision();
        assertTrue(session.undo());
        long undoRevision = session.currentState().revision();

        assertTrue(undoRevision > lastCpuRevision);
        assertEquals(hitPointsBefore, session.currentState().combatant("hero").currentHitPoints());
        assertFalse(runner.advance());

        assertTrue(runner.finished());
        assertEquals(EnemyCpuOutcome.REVISION_GUARD_TRIGGERED, runner.result().outcome());
        assertEquals(lastCpuRevision, runner.result().endingRevision());
        assertEquals(undoRevision, session.currentState().revision());
        assertEquals(List.of("enemy"), session.currentState().currentCombatantIds());
        assertFalse(runner.result().turnAdvanced());
        assertFalse(runner.reports().stream().anyMatch(report ->
                report.type() == EnemyCpuActionType.TURN_ENDED
                        || report.type() == EnemyCpuActionType.ENCOUNTER_RESOLVED));
    }

    @Test
    void ownedRollbackRefusesToRemoveAnExternalCommand() {
        CombatSession session = battle(20, 3, 107L);
        EnemyCpuTurnRunner runner = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .startCurrentGroup(session);

        assertTrue(runner.advance());
        int hitPointsAfterCpu = session.currentState().combatant("hero").currentHitPoints();
        assertEquals(session.currentState().revision(), runner.ownedRevision());
        session.grantTemporaryHitPoints("hero", 4);

        assertFalse(runner.rollbackOwnedCommandsIfCurrent());
        assertEquals(4, session.currentState().combatant("hero").temporaryHitPoints());
        assertEquals(hitPointsAfterCpu, session.currentState().combatant("hero").currentHitPoints());
    }

    @Test
    void ownedRollbackAtomicallyRestoresTheStartWhenNothingExternalIntervened() {
        CombatSession session = battle(20, 3, 108L);
        EnemyCpuTurnRunner runner = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .startCurrentGroup(session);
        long startingRevision = session.currentState().revision();

        assertTrue(runner.advance());
        assertTrue(runner.rollbackOwnedCommandsIfCurrent());

        assertEquals(20, session.currentState().combatant("hero").currentHitPoints());
        assertEquals(session.currentState(), runner.ownedState());
        assertEquals(session.currentState().revision(), runner.ownedRevision());
        assertTrue(runner.ownedRevision() > startingRevision,
                "l'Undo e' esso stesso una revisione, ma ripristina lo stato iniziale");
        assertTrue(runner.finished());
        assertEquals(EnemyCpuOutcome.ROLLED_BACK, runner.result().outcome());
        assertTrue(runner.reports().isEmpty());
        long rollbackRevision = session.currentState().revision();
        assertFalse(runner.advance(), "un runner riavvolto non deve ripianificare azioni");
        assertEquals(rollbackRevision, session.currentState().revision());
    }

    @Test
    void rollingBackAFinalAttackCannotResolveTheCancelledVictory() {
        CombatSession session = battle(1, 5, 109L);
        EnemyCpuTurnRunner runner = new EnemyCpu(EnemyCpuDifficulty.MEDIUM)
                .startCurrentGroup(session);

        assertTrue(runner.advance());
        assertEquals(0, session.currentState().combatant("hero").currentHitPoints());
        assertTrue(runner.rollbackOwnedCommandsIfCurrent());
        long rollbackRevision = session.currentState().revision();

        assertEquals(1, session.currentState().combatant("hero").currentHitPoints());
        assertEquals(CombatStatus.ACTIVE, session.currentState().status());
        assertEquals(EnemyCpuOutcome.ROLLED_BACK, runner.result().outcome());
        assertTrue(runner.result().actions().isEmpty());
        assertFalse(runner.advance());
        assertEquals(rollbackRevision, session.currentState().revision());
        assertEquals(CombatStatus.ACTIVE, session.currentState().status());
    }

    private static CombatSession battle(int heroHitPoints, int damage, long seed) {
        AbilityDefinition strike = AbilityDefinition.builder("strike", "Strike")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .automationStatus(AutomationStatus.AUTOMATED)
                .attackBonus(100)
                .rangeFeet(60)
                .damage(List.of(DamageFormula.fixed(DamageType.FORCE, damage)))
                .build();
        ActorDefinition enemy = ActorDefinition.builder("enemy-definition", "Enemy")
                .maxHitPoints(20)
                .armorClass(12)
                .abilities(List.of(strike))
                .build();
        ActorDefinition hero = ActorDefinition.builder("hero-definition", "Hero")
                .maxHitPoints(heroHitPoints)
                .armorClass(12)
                .build();

        CombatSession session = CombatSession.create("cpu-runner-regression", seed);
        session.addCombatant("enemy", enemy);
        session.addCombatant("hero", hero);
        session.setPartyCombatants(List.of("hero"));
        session.setInitiative("enemy", 20);
        session.setInitiative("hero", 10);
        session.markReady();
        session.start();
        return session;
    }
}
