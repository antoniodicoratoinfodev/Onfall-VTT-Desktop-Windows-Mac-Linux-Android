package app.d6d.persistence.session;

import app.d6d.board.BoardDocument;
import app.d6d.board.GridPoint;
import app.d6d.board.Label;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.MapGrid;
import app.d6d.engine.CombatSession;
import app.d6d.persistence.json.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Salvataggio e ricarica di sessioni con nome. */
class SessionArchiveStoreTest {

    @TempDir
    Path directory;

    private SessionArchiveStore store() {
        return new SessionArchiveStore(directory.resolve("sessions"));
    }

    private CombatSession session(long seed) {
        CombatSession session = CombatSession.create("cripta", seed);
        session.addCombatant("hero", ActorDefinition.builder("hero-def", "Eroe")
                .armorClass(16).maxHitPoints(30).speedFeet(30).build());
        session.addCombatant("goblin", ActorDefinition.builder("goblin-def", "Goblin")
                .armorClass(13).maxHitPoints(20).speedFeet(30).build());
        session.setPartyCombatants(List.of("hero"));
        session.setInitiative("hero", 18);
        session.setInitiative("goblin", 9);
        session.markReady();
        session.start();
        session.configureMap(MapGrid.standard(20, 15));
        session.placeCombatant("hero", new GridPosition(2, 2), 1);
        session.placeCombatant("goblin", new GridPosition(8, 8), 2);
        return session;
    }

    @Test
    void unaSessioneSalvataSiRicaricaIdentica() throws IOException {
        SessionArchiveStore store = store();
        CombatSession original = session(7L);
        CombatState before = original.currentState();

        String slug = store.save("Cripta dei Predoni", original, Map.of());
        CombatArchiveAssertions.assertSameState(before, store.load(slug).session().currentState());
    }

    @Test
    void ilNomeLeggibileSopravviveAlNomeDiFile() throws IOException {
        SessionArchiveStore store = store();

        String slug = store.save("Cripta dei Predoni — sera 3", session(7L), Map.of());

        assertEquals("cripta-dei-predoni-sera-3", slug);
        assertEquals("Cripta dei Predoni — sera 3", store.load(slug).summary().displayName());
    }

    @Test
    void laMappaEISegnapostiSopravvivono() throws IOException {
        SessionArchiveStore store = store();
        String slug = store.save("con mappa", session(7L), Map.of());

        CombatState reloaded = store.load(slug).session().currentState();

        assertTrue(reloaded.battleMap().configured());
        assertEquals(new GridPosition(2, 2), reloaded.battleMap().placementOf("hero").orElseThrow().origin());
        assertEquals(2, reloaded.battleMap().placementOf("goblin").orElseThrow().squaresPerSide());
    }

    @Test
    void loStatoDiPresentazioneSopravvive() throws IOException {
        SessionArchiveStore store = store();
        Map<String, String> presentation = Map.of(
                "selectedTargetId", "goblin",
                "rollMode", "ADVANTAGE");

        String slug = store.save("con presentazione", session(7L), presentation);

        assertEquals(presentation, store.load(slug).presentation());
    }

    @Test
    void ilLucidoStrutturatoSopravvive() throws IOException {
        SessionArchiveStore store = store();
        BoardDocument board = BoardDocument.empty().withObjects(List.of(
                new Label("sala", new GridPoint(4.5, 3.5), "Sala del trono", 0xffccaa44, 16, 0)));

        String slug = store.save("con lucido", session(7L), Map.of(), board);

        assertEquals(board, store.load(slug).board());
    }

    @Test
    void unArchivioSchemaUnoSenzaLucidoRestaLeggibile() throws IOException {
        SessionArchiveStore store = store();
        String slug = store.save("vecchia", session(7L), Map.of());
        Path file = directory.resolve("sessions").resolve(slug + ".json");
        Map<String, Object> document = new java.util.LinkedHashMap<>(Json.parseObject(Files.readString(file)));
        document.put("schemaVersion", 1);
        document.remove("board");
        Files.writeString(file, Json.encode(document));

        assertEquals(BoardDocument.empty(), store.load(slug).board());
    }

    @Test
    void unArchivioSchemaDueSenzaLucidoNonPerdeDatiInSilenzio() throws IOException {
        SessionArchiveStore store = store();
        String slug = store.save("incompleta", session(7L), Map.of());
        Path file = directory.resolve("sessions").resolve(slug + ".json");
        Map<String, Object> document = new java.util.LinkedHashMap<>(Json.parseObject(Files.readString(file)));
        document.remove("board");
        Files.writeString(file, Json.encode(document));

        assertThrows(IOException.class, () -> store.load(slug));
    }

    @Test
    void ilGeneratoreCasualeRiprendeDoveEraRimasto() throws IOException {
        SessionArchiveStore store = store();
        CombatSession original = session(7L);

        String slug = store.save("determinismo", original, Map.of());
        CombatSession reloaded = store.load(slug).session();

        // Stesso stato del generatore: i tiri futuri coincidono.
        assertEquals(
                original.currentState().randomState(),
                reloaded.currentState().randomState());
    }

    @Test
    void ilRegistroCompletoSopravvive() throws IOException {
        SessionArchiveStore store = store();
        CombatSession original = session(7L);
        int events = original.auditTrail().size();

        String slug = store.save("registro", original, Map.of());

        assertEquals(events, store.load(slug).session().auditTrail().size());
    }

    @Test
    void lElencoMostraLeSessioniSalvate() throws IOException {
        SessionArchiveStore store = store();
        store.save("prima", session(1L), Map.of());
        store.save("seconda", session(2L), Map.of());

        List<SessionSummary> sessions = store.list();

        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().anyMatch(s -> s.displayName().equals("prima")));
        assertTrue(sessions.stream().allMatch(s -> s.combatantCount() == 2));
        assertTrue(sessions.stream().allMatch(s -> s.status().equals("ACTIVE")));
    }

    @Test
    void unaCartellaVuotaNonFaFallireLElenco() throws IOException {
        assertTrue(store().list().isEmpty());
    }

    @Test
    void salvareDueVolteSovrascriveLaStessaSessione() throws IOException {
        SessionArchiveStore store = store();
        store.save("unica", session(1L), Map.of());
        store.save("unica", session(2L), Map.of());

        assertEquals(1, store.list().size());
    }

    @Test
    void unaSessioneSiPuoEliminare() throws IOException {
        SessionArchiveStore store = store();
        String slug = store.save("da eliminare", session(1L), Map.of());

        store.delete(slug);

        assertFalse(store.exists(slug));
        assertTrue(store.list().isEmpty());
        // Eliminare due volte non deve sollevare eccezioni.
        store.delete(slug);
    }

    @Test
    void eliminareRifiutaUnPercorsoInveceDiUscireDallArchivio() throws IOException {
        SessionArchiveStore store = store();
        Path outside = directory.resolve("fuori.json");
        Files.writeString(outside, "da non cancellare");

        assertThrows(IllegalArgumentException.class, () -> store.delete("../fuori"));

        assertTrue(Files.exists(outside));
    }

    @Test
    void unFileDanneggiatoNonImpedisceDiVedereGliAltri() throws IOException {
        SessionArchiveStore store = store();
        store.save("buona", session(1L), Map.of());
        Files.writeString(directory.resolve("sessions").resolve("rotta.json"), "{ non json valido");

        List<SessionSummary> sessions = store.list();

        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().anyMatch(s -> s.status().equals("ILLEGGIBILE")));
        assertTrue(sessions.stream().anyMatch(s -> s.displayName().equals("buona")));
    }

    @Test
    void unNomeVuotoRicevoUnNomeDiRipiego() {
        assertEquals("sessione", SessionArchiveStore.slugify("   "));
        assertEquals("sessione", SessionArchiveStore.slugify(null));
    }

    @Test
    void unNomeConPercorsoNonPuoUscireDallaCartella() throws IOException {
        SessionArchiveStore store = store();

        // La sanificazione toglie le barre prima che arrivino al filesystem.
        String slug = store.save("../../fuga", session(1L), Map.of());

        assertEquals("fuga", slug);
        assertTrue(Files.exists(directory.resolve("sessions").resolve("fuga.json")));
    }

    @Test
    void caricareUnaSessioneInesistenteFallisceInModoEsplicito() {
        assertThrows(IOException.class, () -> store().load("mai-salvata"));
    }
}

/** Confronto di stato estratto per tenere leggibile il test principale. */
final class CombatArchiveAssertions {
    private CombatArchiveAssertions() { }

    static void assertSameState(CombatState expected, CombatState actual) {
        assertEquals(expected, actual);
    }
}
