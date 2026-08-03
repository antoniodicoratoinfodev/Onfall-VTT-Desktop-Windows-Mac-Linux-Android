package app.d6d.persistence.combat;

import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.D20RollInput;
import app.d6d.domain.combat.DamageComponent;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.RollSource;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.MapBackground;
import app.d6d.domain.space.MapGrid;
import app.d6d.engine.CombatSession;
import app.d6d.persistence.json.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistenza degli stati aggiunti dopo la prima versione.
 *
 * <p>Il round-trip generale esistente confronta gli stati, ma su una sessione che
 * lascia tutti questi campi ai valori di partenza: mappa assente, nessun Exhaustion,
 * nessun tiro contro morte, turni separati. Con i soli valori predefiniti un campo
 * dimenticato nel codec passerebbe inosservato, perche' entrambi i lati sarebbero
 * vuoti. Qui vengono valorizzati prima di salvare.</p>
 */
class NewStatePersistenceTest {

    private static ActorDefinition actor(String id, String name) {
        return ActorDefinition.builder(id, name)
                .armorClass(15)
                .maxHitPoints(30)
                .speedFeet(30)
                .initiativeModifier(2)
                .build();
    }

    /** Sessione con mappa, segnaposti, sfondo, turni simultanei, morte ed Exhaustion. */
    private CombatSession richSession() {
        CombatSession session = CombatSession.create("ricca", 99L);
        session.addCombatant("hero", actor("hero-def", "Eroe"));
        session.addCombatant("goblin", actor("goblin-def", "Goblin"));
        session.addCombatant("wolf", actor("wolf-def", "Lupo"));
        session.setPartyCombatants(List.of("hero"));
        session.setInitiative("hero", 20);
        session.setInitiative("goblin", 12);
        session.setInitiative("wolf", 12);
        session.setInitiativeOrder(List.of("hero", "goblin", "wolf"));
        session.setSimultaneousTies(true);
        session.markReady();
        session.start();

        session.configureMap(new MapGrid(24, 18, 10));
        session.setMapBackground("cripta.png");
        session.placeCombatant("hero", new GridPosition(3, 4), 1);
        session.placeCombatant("goblin", new GridPosition(9, 9), 2);
        session.placeCombatant("wolf", new GridPosition(15, 2), 1);

        session.setExhaustion("hero", 2);
        // Il goblin va a zero punti ferita e accumula un fallimento contro morte.
        session.applyDamage("hero", "goblin", List.of(new DamageComponent(DamageType.SLASHING, 30)), false);
        session.endTurn();
        session.rollDeathSave("goblin", new D20RollInput(RollSource.MANUAL, D20Mode.NORMAL, 4));
        return session;
    }

    private CombatSession roundTrip(CombatSession session) {
        CombatSessionJsonCodec codec = new CombatSessionJsonCodec();
        // Si passa davvero per il testo JSON, non per la mappa di oggetti: e' il
        // percorso che compie un salvataggio su disco.
        Map<String, Object> encoded = codec.encode(session);
        return codec.decode(Json.parseObject(Json.encode(encoded)));
    }

    @Test
    void loStatoCompletoSopravviveAlGiroCompletoDiSerializzazione() {
        CombatSession original = richSession();
        CombatState before = original.currentState();

        CombatState after = roundTrip(original).currentState();

        // L'uguaglianza del record copre ogni campo, compresi quelli aggiunti dopo.
        assertEquals(before, after);
    }

    @Test
    void laMappaMantieneGrigliaScalaSegnapostiESfondo() {
        CombatState after = roundTrip(richSession()).currentState();

        assertTrue(after.battleMap().configured());
        assertEquals(24, after.battleMap().grid().columns());
        assertEquals(18, after.battleMap().grid().rows());
        assertEquals(10, after.battleMap().grid().feetPerSquare());
        assertEquals("cripta.png", after.battleMap().backgroundImage());
        assertEquals(3, after.battleMap().placements().size());

        assertEquals(new GridPosition(3, 4), after.battleMap().placementOf("hero").orElseThrow().origin());
        // L'ingombro della creatura Grande non deve appiattirsi a una casella.
        assertEquals(2, after.battleMap().placementOf("goblin").orElseThrow().squaresPerSide());
    }

    @Test
    void laCollocazioneDelloSfondoSopravvive() {
        CombatSession session = richSession();
        // Sfondo spostato oltre il bordo e stirato: proprio il caso che il vecchio
        // "riempi la griglia" non sapeva rappresentare.
        session.setMapBackgroundTransform(-2.5, 1.0, 30.0, 22.5);

        MapBackground background = roundTrip(session).currentState().battleMap().background();

        assertTrue(background.isSet());
        assertEquals(-2.5, background.offsetX());
        assertEquals(1.0, background.offsetY());
        assertEquals(30.0, background.width());
        assertEquals(22.5, background.height());
    }

    @Test
    void leDistanzeRestanoCalcolabiliDopoLaRipresa() {
        CombatState after = roundTrip(richSession()).currentState();

        // Da 3,4 a 9,9: sei caselle di Chebyshev con passo dieci piedi.
        assertEquals(60, after.distanceFeet("hero", "goblin").orElseThrow());
    }

    @Test
    void iTurniSimultaneiSopravvivono() {
        CombatState after = roundTrip(richSession()).currentState();

        assertTrue(after.simultaneousTies());
        assertEquals(List.of("goblin", "wolf"), after.turnGroups().get(1));
    }

    @Test
    void exhaustionETiriControMorteSopravvivono() {
        CombatState after = roundTrip(richSession()).currentState();

        assertEquals(2, after.combatants().get("hero").exhaustionLevel());
        assertEquals(-4, after.combatants().get("hero").exhaustionD20Penalty());
        assertEquals(1, after.combatants().get("goblin").deathSaves().failures());
        assertTrue(after.combatants().get("goblin").unconscious());
    }

    @Test
    void unSalvataggioSenzaLeChiaviNuoveRestaLeggibile() {
        CombatSessionJsonCodec codec = new CombatSessionJsonCodec();
        Map<String, Object> encoded = codec.encode(richSession());

        // Simula un file scritto prima che questi campi esistessero.
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) encoded.get("currentState");
        state.remove("battleMap");
        state.remove("simultaneousTies");

        CombatState after = codec.decode(Json.parseObject(Json.encode(encoded))).currentState();

        // Nessuna eccezione: si torna al comportamento astratto e ai turni separati.
        assertEquals(false, after.simultaneousTies());
        assertEquals(false, after.battleMap().configured());
    }
}
