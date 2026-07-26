package app.d6d.engine;

import app.d6d.domain.combat.CombatEvent;
import app.d6d.domain.combat.CombatantSnapshot;
import app.d6d.domain.combat.DamageComponent;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.EventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correzione di una scheda durante lo scontro.
 *
 * <p>Il paragrafo 3 del documento vuole che un combattimento usi una fotografia e
 * che le modifiche alla scheda non alterino retroattivamente uno scontro giocato.
 * La correzione al tavolo va nel verso opposto e consentito, ma deve restare
 * tracciabile: revisione nuova, evento completo, annullamento possibile.</p>
 */
class CombatantEditTest {

    private CombatantSnapshot snapshotOf(CombatSession session, String id) {
        return session.currentState().combatants().get(id).snapshot();
    }

    private CombatEvent lastEdit(CombatSession session) {
        return session.auditTrail().stream()
                .filter(event -> event.type() == EventType.COMBATANT_EDITED)
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    @Test
    void laCorrezioneProduceUnaNuovaRevisione() {
        CombatSession session = CombatFixtures.active(3L);
        CombatantSnapshot before = snapshotOf(session, "hero");
        assertFalse(before.tableEdited());

        session.editCombatant("hero", before.withStats("Nome Nuovo", 18, 40, 30, 3, 13, 2));

        CombatantSnapshot after = snapshotOf(session, "hero");
        assertTrue(after.tableEdited());
        // La revisione originale non viene sovrascritta: ne nasce una derivata.
        assertEquals(before.definitionVersion() + "+tavolo.1", after.definitionVersion());
    }

    @Test
    void correzioniRipetuteIncrementanoLaRevisione() {
        CombatSession session = CombatFixtures.active(3L);

        session.editCombatant("hero", snapshotOf(session, "hero").withStats("A", 18, 40, 30, 3, 13, 2));
        session.editCombatant("hero", snapshotOf(session, "hero").withStats("B", 19, 40, 30, 3, 13, 2));

        assertTrue(snapshotOf(session, "hero").definitionVersion().endsWith("+tavolo.2"));
    }

    @Test
    void abbassareIPuntiFeritaMassimiNonInvalidaLaFotografia() {
        CombatSession session = CombatFixtures.active(3L);
        CombatantSnapshot before = snapshotOf(session, "hero");
        assertEquals(40, before.initialHitPoints());

        // Il tetto scende sotto i punti ferita iniziali: vanno riportati entro il limite.
        CombatantSnapshot reduced = before.withStats(before.name(), 16, 5, 30, 3, 13, 2);

        assertEquals(5, reduced.maxHitPoints());
        assertEquals(5, reduced.initialHitPoints());
    }

    @Test
    void abbassareIlTettoRiportaAncheIPuntiFeritaCorrenti() {
        CombatSession session = CombatFixtures.active(3L);
        CombatantSnapshot before = snapshotOf(session, "hero");

        session.editCombatant("hero", before.withStats(before.name(), 16, 7, 30, 3, 13, 2));

        assertEquals(7, session.currentState().combatants().get("hero").currentHitPoints());
    }

    @Test
    void unaCorrezioneNonAlzaIPuntiFeritaGiaPersi() {
        CombatSession session = CombatFixtures.active(3L);
        session.applyDamage("goblin", "hero", List.of(new DamageComponent(DamageType.SLASHING, 30)), false);
        assertEquals(10, session.currentState().combatants().get("hero").currentHitPoints());

        CombatantSnapshot before = snapshotOf(session, "hero");
        session.editCombatant("hero", before.withStats(before.name(), 16, 60, 30, 3, 13, 2));

        // Alzare il massimo non cura: i punti ferita correnti restano quelli.
        assertEquals(10, session.currentState().combatants().get("hero").currentHitPoints());
    }

    @Test
    void impostareManualmenteZeroPuntiFeritaDichiaraLaCreaturaMorta() {
        CombatSession session = CombatFixtures.active(3L);

        session.setCurrentHitPoints("hero", 0);

        assertEquals(0, session.currentState().combatants().get("hero").currentHitPoints());
        assertTrue(session.currentState().combatants().get("hero").dead());
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.CURRENT_HIT_POINTS_SET));
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.DIED));

        assertTrue(session.undo());
        assertEquals(40, session.currentState().combatants().get("hero").currentHitPoints());
        assertFalse(session.currentState().combatants().get("hero").dead());
    }

    @Test
    void ilRegistroConservaOgniStatisticaPrimaEDopo() {
        CombatSession session = CombatFixtures.active(3L);
        CombatantSnapshot before = snapshotOf(session, "hero");

        session.editCombatant("hero", before.withStats("Corretto", 21, 44, 25, 5, 15, 4));

        Map<String, String> details = lastEdit(session).details();
        assertEquals(before.name(), details.get("previousName"));
        assertEquals("Corretto", details.get("name"));
        assertEquals("16", details.get("previousArmorClass"));
        assertEquals("21", details.get("armorClass"));
        assertEquals("40", details.get("previousMaxHitPoints"));
        assertEquals("44", details.get("maxHitPoints"));
        assertEquals("30", details.get("previousSpeedFeet"));
        assertEquals("25", details.get("speedFeet"));
        // La revisione prima e dopo dice quale fotografia era in uso a quel punto.
        assertEquals(before.definitionVersion(), details.get("previousVersion"));
        assertEquals(before.definitionVersion() + "+tavolo.1", details.get("version"));
    }

    @Test
    void annullareRipristinaFotografiaERevisione() {
        CombatSession session = CombatFixtures.active(3L);
        CombatantSnapshot before = snapshotOf(session, "hero");

        session.editCombatant("hero", before.withStats("Temporaneo", 21, 40, 30, 3, 13, 2));
        session.undo();

        CombatantSnapshot restored = snapshotOf(session, "hero");
        assertEquals(before.name(), restored.name());
        assertEquals(before.armorClass(), restored.armorClass());
        assertEquals(before.definitionVersion(), restored.definitionVersion());
        assertFalse(restored.tableEdited());
    }

    @Test
    void unaCorrezioneNonPuoCambiareIdentita() {
        CombatSession session = CombatFixtures.active(3L);
        CombatantSnapshot altro = snapshotOf(session, "goblin");

        // La fotografia di un altro combattente non puo' essere applicata a questo.
        assertThrows(CombatRuleException.class, () -> session.editCombatant("hero", altro));
    }

    @Test
    void laCorrezioneRestaNelRegistroDopoUnAnnullamento() {
        CombatSession session = CombatFixtures.active(3L);
        session.editCombatant("hero", snapshotOf(session, "hero").withStats("X", 18, 40, 30, 3, 13, 2));
        session.undo();

        // Il registro e' append-only: la correzione annullata resta scritta.
        assertTrue(session.auditTrail().stream().anyMatch(e -> e.type() == EventType.COMBATANT_EDITED));
        assertTrue(session.auditTrail().stream().anyMatch(e -> e.type() == EventType.UNDO_PERFORMED));
    }

    @Test
    void unaRevisioneIllegibileNonFaFallireLaCorrezione() {
        // Un suffisso non numerico non deve impedire di correggere la scheda.
        assertEquals("1.0.0+tavolo.1", CombatantSnapshot.nextTableRevision("1.0.0+tavolo.abc"));
        assertEquals("2.1.0+tavolo.1", CombatantSnapshot.nextTableRevision("2.1.0"));
    }
}
