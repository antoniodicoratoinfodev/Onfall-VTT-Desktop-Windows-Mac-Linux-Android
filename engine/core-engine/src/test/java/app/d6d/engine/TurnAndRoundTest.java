package app.d6d.engine;

import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.EventType;
import app.d6d.domain.combat.TurnBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnAndRoundTest {
    @Test
    void movementActionBonusAndSpellSlotAreIndependent() {
        CombatSession session = CombatFixtures.active(40L);

        session.spendMovement("hero", 10);
        session.spendAction("hero", ActivationCost.ACTION);
        session.spendAction("hero", ActivationCost.BONUS_ACTION);
        session.markSpellSlotSpent("hero");
        TurnBudget budget = session.currentState().turnBudgets().get("hero");

        assertEquals(20, budget.movementRemainingFeet());
        assertFalse(budget.actionAvailable());
        assertFalse(budget.bonusActionAvailable());
        assertTrue(budget.reactionAvailable());
        assertTrue(budget.spellSlotSpentThisTurn());
        assertThrows(CombatRuleException.class, () -> session.spendAction("hero", ActivationCost.ACTION));
        assertThrows(CombatRuleException.class, () -> session.spendMovement("goblin", 5));
    }

    @Test
    void reactionRefreshesAtTheStartOfOwnersNextTurn() {
        CombatSession session = CombatFixtures.active(41L);

        session.spendAction("goblin", ActivationCost.REACTION); // reaction during hero's turn
        assertFalse(session.currentState().turnBudgets().get("goblin").reactionAvailable());
        session.endTurn(); // goblin starts
        assertTrue(session.currentState().turnBudgets().get("goblin").reactionAvailable());
        session.spendAction("goblin", ActivationCost.REACTION);
        session.endTurn(); // hero starts round 2
        assertFalse(session.currentState().turnBudgets().get("goblin").reactionAvailable());
        session.endTurn(); // goblin starts round 2
        assertTrue(session.currentState().turnBudgets().get("goblin").reactionAvailable());
    }

    @Test
    void wrappingInitiativeEndsAndStartsRoundsAndRefreshesTurnFlags() {
        CombatSession session = CombatFixtures.active(42L);
        session.markSpellSlotSpent("hero");
        session.endTurn();
        assertEquals(1, session.currentState().round());
        assertEquals("goblin", session.currentState().currentCombatantId().orElseThrow());
        session.endTurn();

        assertEquals(2, session.currentState().round());
        assertEquals("hero", session.currentState().currentCombatantId().orElseThrow());
        assertFalse(session.currentState().turnBudgets().get("hero").spellSlotSpentThisTurn());
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.ROUND_ENDED));
        assertEquals(2, session.auditTrail().stream()
                .filter(event -> event.type() == EventType.ROUND_STARTED).count());
    }
}
