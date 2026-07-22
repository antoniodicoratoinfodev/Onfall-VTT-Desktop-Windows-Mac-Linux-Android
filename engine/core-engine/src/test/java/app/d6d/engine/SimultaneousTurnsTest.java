package app.d6d.engine;

import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.CombatState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Turni giocati insieme dai combattenti che hanno pareggiato l'iniziativa.
 *
 * <p>Non e' il comportamento predefinito: il regolamento fa risolvere le parita'
 * al DM, quindi giocarle simultaneamente resta una scelta dichiarata del tavolo.</p>
 */
class SimultaneousTurnsTest {

    /** Tre combattenti, due dei quali in parita' a 15. */
    private CombatSession tied(boolean simultaneous) {
        CombatSession session = CombatSession.create("parita", 11L);
        session.addCombatant("hero", CombatFixtures.hero());
        session.addCombatant("goblin", CombatFixtures.goblin());
        session.addCombatant("wolf", CombatFixtures.goblin());
        session.setInitiative("hero", 20);
        session.setInitiative("goblin", 15);
        session.setInitiative("wolf", 15);
        session.setInitiativeOrder(List.of("hero", "goblin", "wolf"));
        if (simultaneous) session.setSimultaneousTies(true);
        session.markReady();
        session.start();
        return session;
    }

    @Test
    void senzaLaBandieraOgnunoHaIlProprioTurno() {
        CombatState state = tied(false).currentState();

        assertFalse(state.simultaneousTies());
        assertEquals(3, state.turnGroups().size());
        assertEquals(List.of("hero"), state.currentCombatantIds());
        assertFalse(state.currentTurnIsSimultaneous());
    }

    @Test
    void conLaBandieraIPareggiFormanoUnGruppoUnico() {
        CombatState state = tied(true).currentState();

        assertTrue(state.simultaneousTies());
        // Hero da solo, poi goblin e wolf insieme: due gruppi invece di tre turni.
        assertEquals(2, state.turnGroups().size());
        assertEquals(List.of("goblin", "wolf"), state.turnGroups().get(1));
    }

    @Test
    void ilGruppoInParitaDiventaAttivoInsieme() {
        CombatSession session = tied(true);
        session.endTurn();

        CombatState state = session.currentState();
        assertEquals(List.of("goblin", "wolf"), state.currentCombatantIds());
        assertTrue(state.currentTurnIsSimultaneous());
    }

    @Test
    void entrambiIMembriDelGruppoPossonoAgireNelloStessoTurno() {
        CombatSession session = tied(true);
        session.endTurn();

        // Nessuno dei due deve aspettare l'altro: agiscono nello stesso turno.
        session.attack(AttackRequest.manual("goblin", "hero", "sword", 18, List.of(3)));
        session.attack(AttackRequest.manual("wolf", "hero", "sword", 18, List.of(3)));

        assertFalse(session.currentState().turnBudgets().get("goblin").actionAvailable());
        assertFalse(session.currentState().turnBudgets().get("wolf").actionAvailable());
    }

    @Test
    void chiEFuoriDalGruppoAttivoNonPuoAgire() {
        CombatSession session = tied(true);
        session.endTurn();

        assertThrows(
                CombatRuleException.class,
                () -> session.spendAction("hero", ActivationCost.ACTION));
    }

    @Test
    void ogniMembroDelGruppoRiceveIlProprioBudget() {
        CombatSession session = tied(true);
        session.endTurn();

        CombatState state = session.currentState();
        // Il budget resta individuale: agire insieme non significa condividerlo.
        assertTrue(state.turnBudgets().get("goblin").actionAvailable());
        assertTrue(state.turnBudgets().get("wolf").actionAvailable());
        assertEquals(30, state.turnBudgets().get("goblin").movementRemainingFeet());
    }

    @Test
    void ilTurnoFinisceInsiemePerTuttoIlGruppo() {
        CombatSession session = tied(true);
        session.endTurn();
        session.endTurn();

        CombatState state = session.currentState();
        // Due soli gruppi: chiuso il secondo si passa al round successivo.
        assertEquals(2, state.round());
        assertEquals(List.of("hero"), state.currentCombatantIds());
    }

    @Test
    void ilRoundConTurniSimultaneiHaMenoPassaggi() {
        CombatSession separati = tied(false);
        separati.endTurn();
        separati.endTurn();
        assertEquals(1, separati.currentState().round());

        CombatSession insieme = tied(true);
        insieme.endTurn();
        insieme.endTurn();
        // Con il gruppo unico bastano due passaggi per chiudere il round.
        assertEquals(2, insieme.currentState().round());
    }

    @Test
    void laBandieraSopravviveAlSalvataggioELaRipresa() {
        CombatSession session = tied(true);
        session.endTurn();

        CombatSession restored = CombatSession.restore(session.currentState(), session.auditTrail());

        assertTrue(restored.currentState().simultaneousTies());
        assertEquals(List.of("goblin", "wolf"), restored.currentState().currentCombatantIds());
    }

    @Test
    void annullareRipristinaAncheLaBandiera() {
        CombatSession session = CombatSession.create("parita-configurazione", 11L);
        session.setSimultaneousTies(true);
        assertTrue(session.currentState().simultaneousTies());

        session.undo();

        assertFalse(session.currentState().simultaneousTies());
    }

    @Test
    void laBandieraNonCambiaDuranteIlCombattimento() {
        CombatSession session = tied(false);
        String actor = session.currentState().currentCombatantId().orElseThrow();

        assertThrows(CombatRuleException.class, () -> session.setSimultaneousTies(true));

        assertFalse(session.currentState().simultaneousTies());
        assertEquals(actor, session.currentState().currentCombatantId().orElseThrow());
    }
}
