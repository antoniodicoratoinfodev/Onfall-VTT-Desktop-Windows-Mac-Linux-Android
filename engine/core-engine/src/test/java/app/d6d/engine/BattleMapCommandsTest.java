package app.d6d.engine;

import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.MapGrid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Comandi della mappa: configurazione, posizionamento, movimento e gittata. */
class BattleMapCommandsTest {

    /** Incontro con mappa 20x15 e i due combattenti posizionati. */
    private CombatSession mapped() {
        CombatSession session = CombatFixtures.active(5L);
        session.configureMap(MapGrid.standard(20, 15));
        session.placeCombatant("hero", new GridPosition(2, 2), 1);
        session.placeCombatant("goblin", new GridPosition(10, 10), 1);
        return session;
    }

    private CombatState state(CombatSession session) {
        return session.currentState();
    }

    @Test
    void senzaMappaLIncontroRestaAstratto() {
        CombatSession session = CombatFixtures.active(5L);

        assertFalse(state(session).battleMap().configured());
        // Nulla cambia rispetto al gioco senza mappa: gli attacchi non hanno vincoli spaziali.
        assertTrue(state(session).distanceFeet("hero", "goblin").isEmpty());
    }

    @Test
    void configurareLaMappaLaRendeEsatta() {
        CombatSession session = mapped();

        CombatState state = state(session);
        assertTrue(state.battleMap().configured());
        assertEquals(20, state.battleMap().grid().columns());
        assertEquals(5, state.battleMap().grid().feetPerSquare());
    }

    @Test
    void ladistanzaSiCalcolaSullaGriglia() {
        CombatSession session = mapped();

        // Da 2,2 a 10,10: otto caselle in diagonale, quaranta piedi.
        assertEquals(40, state(session).distanceFeet("hero", "goblin").orElseThrow());
    }

    @Test
    void muoversiConsumaIlBudgetDiMovimento() {
        CombatSession session = mapped();
        int before = state(session).turnBudgets().get("hero").movementRemainingFeet();

        int spent = session.moveCombatant("hero", new GridPosition(4, 2));

        assertEquals(10, spent);
        assertEquals(before - 10, state(session).turnBudgets().get("hero").movementRemainingFeet());
    }

    @Test
    void laDiagonaleCostaComeUnaCasella() {
        CombatSession session = mapped();

        // Tre caselle in diagonale sono quindici piedi, non ventuno.
        int spent = session.moveCombatant("hero", new GridPosition(5, 5));

        assertEquals(15, spent);
    }

    @Test
    void nonSiPuoSuperareIlBudgetDiMovimento() {
        CombatSession session = mapped();

        // La velocita' della fixture e' 30 piedi: sei caselle, non di piu'.
        assertThrows(
                CombatRuleException.class,
                () -> session.moveCombatant("hero", new GridPosition(2, 12)));
    }

    @Test
    void nonSiPuoFinireSuUnaCasellaOccupata() {
        CombatSession session = mapped();
        session.moveCombatant("hero", new GridPosition(7, 7));

        assertThrows(
                CombatRuleException.class,
                () -> session.moveCombatant("hero", new GridPosition(10, 10)));
    }

    @Test
    void nonSiPuoUscireDallaGriglia() {
        CombatSession session = mapped();

        assertThrows(
                CombatRuleException.class,
                () -> session.moveCombatant("hero", new GridPosition(50, 50)));
    }

    @Test
    void soloIlCombattenteDiTurnoSiMuove() {
        CombatSession session = mapped();

        assertThrows(
                CombatRuleException.class,
                () -> session.moveCombatant("goblin", new GridPosition(11, 10)));
    }

    @Test
    void unAttaccoInMischiaOltreLaPortataVieneRifiutato() {
        // La spada della fixture ha portata 5 piedi: una casella. A quaranta e' fuori.
        CombatSession session = mapped();

        assertThrows(
                CombatRuleException.class,
                () -> session.attack(AttackRequest.manual("hero", "goblin", "sword", 18, List.of(4))));
    }

    @Test
    void unAttaccoEntroLaPortataPassa() {
        CombatSession session = CombatFixtures.active(5L);
        session.configureMap(MapGrid.standard(20, 15));
        session.placeCombatant("hero", new GridPosition(4, 4), 1);
        // Adiacente in diagonale: una casella, cinque piedi, dentro la portata.
        session.placeCombatant("goblin", new GridPosition(5, 5), 1);

        session.attack(AttackRequest.manual("hero", "goblin", "sword", 18, List.of(4)));

        assertFalse(state(session).turnBudgets().get("hero").actionAvailable());
    }

    @Test
    void senzaPosizionamentoLaPortataNonVieneImposta() {
        CombatSession session = CombatFixtures.active(5L);
        session.configureMap(MapGrid.standard(40, 40));
        session.placeCombatant("hero", new GridPosition(0, 0), 1);
        // Il goblin resta fuori mappa: il motore non inventa una distanza.

        session.attack(AttackRequest.manual("hero", "goblin", "sword", 18, List.of(4)));

        assertFalse(state(session).turnBudgets().get("hero").actionAvailable());
    }

    @Test
    void unaCreaturaGrandeOccupaPiuCaselleEBloccaIlPassaggio() {
        CombatSession session = CombatFixtures.active(5L);
        session.configureMap(MapGrid.standard(20, 20));
        session.placeCombatant("hero", new GridPosition(0, 0), 1);
        session.placeCombatant("goblin", new GridPosition(3, 3), 2);

        // 4,4 e' dentro l'ingombro della creatura Grande che parte da 3,3.
        assertThrows(
                CombatRuleException.class,
                () -> session.moveCombatant("hero", new GridPosition(4, 4)));
    }

    @Test
    void restringereLaMappaScartaISegnapostiFuoriBordo() {
        CombatSession session = mapped();

        session.configureMap(MapGrid.standard(5, 5));

        CombatState state = state(session);
        assertTrue(state.battleMap().isPlaced("hero"));
        // Il goblin era a 10,10: fuori dalla nuova griglia.
        assertFalse(state.battleMap().isPlaced("goblin"));
    }

    @Test
    void laScalaSiPuoCambiarePerMappeGrandi() {
        CombatSession session = mapped();

        session.configureMap(new MapGrid(20, 15, 10));

        // Stessa griglia, passo doppio: la stessa diagonale vale il doppio dei piedi.
        assertEquals(80, state(session).distanceFeet("hero", "goblin").orElseThrow());
    }

    @Test
    void annullareRipristinaLaPosizione() {
        CombatSession session = mapped();
        session.moveCombatant("hero", new GridPosition(4, 4));

        session.undo();

        assertEquals(
                new GridPosition(2, 2),
                state(session).battleMap().placementOf("hero").orElseThrow().origin());
    }

    @Test
    void laMappaSopravviveAllaRipresa() {
        CombatSession session = mapped();
        session.setMapBackground("cripta.png");

        CombatSession restored = CombatSession.restore(session.currentState(), session.auditTrail());

        assertEquals("cripta.png", restored.currentState().battleMap().backgroundImage());
        assertEquals(40, restored.currentState().distanceFeet("hero", "goblin").orElseThrow());
    }

    @Test
    void posizionareNonConsumaMovimento() {
        CombatSession session = CombatFixtures.active(5L);
        session.configureMap(MapGrid.standard(20, 15));
        int before = state(session).turnBudgets().get("hero").movementRemainingFeet();

        session.placeCombatant("hero", new GridPosition(9, 9), 1);

        assertEquals(before, state(session).turnBudgets().get("hero").movementRemainingFeet());
    }

    @Test
    void nonSiPuoTerminareIlMovimentoSuUnMuro() {
        CombatSession session = mapped();
        session.setBlockedCells(List.of(new GridPosition(4, 2)));

        CombatRuleException failure = assertThrows(
                CombatRuleException.class,
                () -> session.moveCombatant("hero", new GridPosition(4, 2)));

        assertEquals("The destination is blocked by a wall", failure.getMessage());
        assertEquals(new GridPosition(2, 2), state(session).battleMap().placementOf("hero").orElseThrow().origin());
    }

    @Test
    void aggirareUnMuroConsumaIlPercorsoReale() {
        CombatSession session = mapped();
        session.setBlockedCells(List.of(new GridPosition(3, 2)));

        int spent = session.moveCombatant("hero", new GridPosition(4, 2));

        assertEquals(20, spent, "il muro impedisce sia il passaggio diretto sia il taglio diagonale dell'angolo");
    }

    @Test
    void unaBarrieraDaBordoABordoRendeLaDestinazioneIrraggiungibile() {
        CombatSession session = mapped();
        List<GridPosition> barrier = new ArrayList<>();
        for (int row = 0; row < 15; row++) barrier.add(new GridPosition(3, row));
        session.setBlockedCells(barrier);

        CombatRuleException failure = assertThrows(
                CombatRuleException.class,
                () -> session.moveCombatant("hero", new GridPosition(4, 2)));

        assertEquals("A wall blocks every path to the destination", failure.getMessage());
    }

    @Test
    void unMuroBloccaUnAttaccoSenzaConsumareLAzione() {
        AbilityDefinition bow = AbilityDefinition.builder("bow", "Bow")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackAbility(SaveAbility.DEXTERITY)
                .attackBonus(100)
                .rangeFeet(60)
                .damage(List.of(DamageFormula.fixed(DamageType.PIERCING, 5)))
                .build();
        ActorDefinition archer = ActorDefinition.builder("archer", "Archer")
                .maxHitPoints(30)
                .speedFeet(30)
                .abilities(List.of(bow))
                .build();
        CombatSession session = CombatSession.create("walls", 77L);
        session.addCombatant("hero", archer);
        session.addCombatant("goblin", CombatFixtures.goblin());
        session.setInitiative("hero", 20);
        session.setInitiative("goblin", 10);
        session.markReady();
        session.start();
        session.configureMap(MapGrid.standard(10, 10));
        session.placeCombatant("hero", new GridPosition(1, 2), 1);
        session.placeCombatant("goblin", new GridPosition(5, 2), 1);
        session.setBlockedCells(List.of(new GridPosition(3, 2)));

        CombatRuleException failure = assertThrows(
                CombatRuleException.class,
                () -> session.attack(AttackRequest.manual("hero", "goblin", "bow", 18, List.of(5))));

        assertEquals("A wall blocks line of effect to the target", failure.getMessage());
        assertTrue(state(session).turnBudgets().get("hero").actionAvailable());
    }
}
