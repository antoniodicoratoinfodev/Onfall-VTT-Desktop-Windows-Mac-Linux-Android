package app.d6d.engine;

import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.CombatResourceState;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.EventType;
import app.d6d.domain.combat.TurnBudget;
import app.d6d.domain.combat.TurnResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCorrectionTest {
    private static final String RESOURCE_ID = "bardic-inspiration";

    @Test
    void maximumAndRemainingUsesAreCorrectedTogetherAndUndoable() {
        CombatSession session = activeSession();

        session.setCombatResource("bard", RESOURCE_ID, 6, 4);

        CombatResourceState corrected = resource(session);
        assertEquals(6, corrected.maximum());
        assertEquals(4, corrected.remaining());
        assertEquals(2, corrected.spent());
        assertTrue(session.auditTrail().stream().anyMatch(event ->
                event.type() == EventType.COMBAT_RESOURCE_SET
                        && event.details().get("previousRemaining").equals("3")
                        && event.details().get("remaining").equals("4")
                        && event.details().get("previousMaximum").equals("4")
                        && event.details().get("maximum").equals("6")));

        assertTrue(session.undo());
        assertEquals(4, resource(session).maximum());
        assertEquals(3, resource(session).remaining());
    }

    @Test
    void invalidCorrectionDoesNotMutateStateOrAudit() {
        CombatSession session = activeSession();
        CombatState before = session.currentState();
        int eventsBefore = session.auditTrail().size();

        assertThrows(CombatRuleException.class,
                () -> session.setCombatResource("bard", RESOURCE_ID, 2, 3));

        assertEquals(before, session.currentState());
        assertEquals(eventsBefore, session.auditTrail().size());
    }

    @Test
    void turnResourcesRemainIndependentAndUndoable() {
        CombatSession session = activeSession();

        session.setTurnResourceAvailable("bard", TurnResource.BONUS_ACTION, false);

        TurnBudget corrected = session.currentState().turnBudgets().get("bard");
        assertTrue(corrected.actionAvailable());
        assertFalse(corrected.bonusActionAvailable());
        assertTrue(corrected.reactionAvailable());
        assertTrue(session.auditTrail().stream().anyMatch(event ->
                event.type() == EventType.TURN_RESOURCE_SET
                        && event.details().get("resource").equals("BONUS_ACTION")));

        assertTrue(session.undo());
        assertTrue(session.currentState().turnBudgets().get("bard").bonusActionAvailable());
    }

    private static CombatSession activeSession() {
        ActorDefinition bard = ActorDefinition.builder("bard-definition", "Bard")
                .maxHitPoints(20)
                .resources(List.of(new CombatResourceState(
                        RESOURCE_ID, "Bardic Inspiration", 4, 1)))
                .build();
        ActorDefinition target = ActorDefinition.builder("target-definition", "Target")
                .maxHitPoints(20)
                .build();
        CombatSession session = CombatSession.create("resource-correction", 91L);
        session.addCombatant("bard", bard);
        session.addCombatant("target", target);
        session.setInitiative("bard", 20);
        session.setInitiative("target", 10);
        session.markReady();
        session.start();
        return session;
    }

    private static CombatResourceState resource(CombatSession session) {
        return session.currentState().combatant("bard").resource(RESOURCE_ID).orElseThrow();
    }
}
