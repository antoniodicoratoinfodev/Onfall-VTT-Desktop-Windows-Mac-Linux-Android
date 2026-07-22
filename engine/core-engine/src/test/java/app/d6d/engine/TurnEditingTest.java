package app.d6d.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Correzioni manuali dei turni durante il gioco: riordino e scelta del turno corrente. */
class TurnEditingTest {

    @Test
    void setCurrentTurnJumpsToChosenCombatantWithFreshBudget() {
        CombatSession session = CombatFixtures.active(50L);
        assertEquals("hero", session.currentState().currentCombatantId().orElseThrow());
        session.spendMovement("hero", 10);

        session.setCurrentTurn("goblin");

        assertEquals("goblin", session.currentState().currentCombatantId().orElseThrow());
        assertEquals(30, session.currentState().turnBudgets().get("goblin").movementRemainingFeet());
    }

    @Test
    void setCurrentTurnOnAlreadyCurrentIsANoOp() {
        CombatSession session = CombatFixtures.active(51L);
        session.setCurrentTurn("hero"); // already current
        assertEquals("hero", session.currentState().currentCombatantId().orElseThrow());
    }

    @Test
    void reorderTurnsKeepsTheActingCombatantCurrent() {
        CombatSession session = CombatFixtures.active(52L);
        assertEquals(List.of("hero", "goblin"), session.currentState().initiativeOrder());

        session.reorderTurns(List.of("goblin", "hero"));

        assertEquals(List.of("goblin", "hero"), session.currentState().initiativeOrder());
        assertEquals("hero", session.currentState().currentCombatantId().orElseThrow());
        assertEquals(1, session.currentState().turnIndex());
    }

    @Test
    void reorderTurnsRejectsAnIncompleteOrder() {
        CombatSession session = CombatFixtures.active(53L);
        assertThrows(CombatRuleException.class, () -> session.reorderTurns(List.of("hero")));
    }
}
