package app.d6d.engine;

import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.CombatStatus;
import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.D20RollResult;
import app.d6d.domain.combat.EventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleAndInitiativeTest {
    @Test
    void encounterFollowsTheFiveExplicitStatuses() {
        CombatSession session = CombatSession.create("lifecycle", 11L);
        assertEquals(CombatStatus.DRAFT, session.currentState().status());
        session.addCombatant("hero", CombatFixtures.hero());
        session.setInitiative("hero", 15);
        session.markReady();
        assertEquals(CombatStatus.READY, session.currentState().status());

        session.start();
        assertEquals(CombatStatus.ACTIVE, session.currentState().status());
        assertEquals(1, session.currentState().round());
        assertEquals("hero", session.currentState().currentCombatantId().orElseThrow());
        session.pause();
        assertEquals(CombatStatus.PAUSED, session.currentState().status());
        assertThrows(CombatRuleException.class, session::endTurn);
        session.resume();
        session.resolve("victory");
        assertEquals(CombatStatus.RESOLVED, session.currentState().status());
        assertEquals("victory", session.auditTrail().get(session.auditTrail().size() - 1).details().get("outcome"));
    }

    @Test
    void equalSeedsProduceEqualInitiativeAndRngState() {
        CombatSession first = CombatSession.create("first", 987654321L);
        CombatSession second = CombatSession.create("second", 987654321L);
        first.addCombatant("hero", CombatFixtures.hero());
        second.addCombatant("hero", CombatFixtures.hero());

        D20RollResult firstRoll = first.rollInitiative("hero", D20Mode.ADVANTAGE);
        D20RollResult secondRoll = second.rollInitiative("hero", D20Mode.ADVANTAGE);

        assertEquals(firstRoll, secondRoll);
        assertEquals(first.currentState().randomState(), second.currentState().randomState());
        assertEquals(2, firstRoll.dice().size());
        assertEquals(firstRoll.dice().stream().mapToInt(Integer::intValue).max().orElseThrow(),
                firstRoll.naturalRoll());
    }

    @Test
    void sharedAndStaticInitiativeApplyTheirSpecificRules() {
        CombatSession session = CombatSession.create("initiative", 44L);
        session.addCombatant("hero", CombatFixtures.hero());
        session.addCombatant("goblin", CombatFixtures.goblin());

        Map<String, D20RollResult> shared = session.rollSharedInitiative(
                List.of("hero", "goblin"), D20Mode.DISADVANTAGE);
        assertEquals(shared.get("hero").naturalRoll(), shared.get("goblin").naturalRoll());
        assertEquals(1, shared.get("hero").total() - shared.get("goblin").total());
        assertEquals(2, shared.get("hero").dice().size());

        int staticTotal = session.useStaticInitiative("hero", D20Mode.ADVANTAGE);
        assertEquals(18, staticTotal);
    }

    @Test
    void armorTrainingPenaltyAppliesToRolledSharedAndStaticInitiative() {
        ActorDefinition penalized = ActorDefinition.builder("penalized", "Penalized")
                .maxHitPoints(10)
                .initiativeModifier(3)
                .strengthDexterityD20Disadvantage(true)
                .build();
        CombatSession session = CombatSession.create("armor-initiative", 45L);
        session.addCombatant("penalized", penalized);
        session.addCombatant("normal", CombatFixtures.goblin());

        D20RollResult rolled = session.rollInitiative("penalized", D20Mode.NORMAL);
        assertEquals(D20Mode.DISADVANTAGE, rolled.mode());
        assertEquals(2, rolled.dice().size());

        Map<String, D20RollResult> shared = session.rollSharedInitiative(
                List.of("penalized", "normal"), D20Mode.ADVANTAGE);
        assertEquals(D20Mode.NORMAL, shared.get("penalized").mode());
        assertEquals(D20Mode.ADVANTAGE, shared.get("normal").mode());
        assertEquals(2, shared.get("normal").dice().size());

        assertEquals(8, session.useStaticInitiative("penalized", D20Mode.NORMAL));
        assertEquals(13, session.useStaticInitiative("penalized", D20Mode.ADVANTAGE));
    }

    @Test
    void explicitOrderResolvesTiesAndIsUsedAtStart() {
        CombatSession session = CombatSession.create("ties", 5L);
        session.addCombatant("hero", CombatFixtures.hero());
        session.addCombatant("goblin", CombatFixtures.goblin());
        session.setInitiative("hero", 15);
        session.setInitiative("goblin", 15);
        session.setInitiativeOrder(List.of("goblin", "hero"));
        session.markReady();
        session.start();

        assertEquals(List.of("goblin", "hero"), session.currentState().initiativeOrder());
        assertEquals("goblin", session.currentState().currentCombatantId().orElseThrow());
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.INITIATIVE_ORDER_SET));
    }

    @Test
    void auditViewsCannotBeMutated() {
        CombatSession session = CombatSession.create("audit", 1L);
        assertThrows(UnsupportedOperationException.class, () -> session.auditTrail().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> session.auditTrail().get(0).details().put("x", "y"));
    }
}
