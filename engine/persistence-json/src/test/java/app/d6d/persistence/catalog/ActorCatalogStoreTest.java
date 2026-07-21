package app.d6d.persistence.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.d6d.domain.catalog.ActorCatalogEntry;
import app.d6d.persistence.json.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActorCatalogStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultStoreSavesLoadsExportsAndImportsCatalogs() throws IOException {
        ActorCatalogEntry rat = ActorCatalogJsonCodecTest.singleSimpleCreature();
        ActorCatalogStore store = new ActorCatalogStore(temporaryDirectory.resolve("data"));

        assertFalse(store.exists());
        store.save(List.of(rat));

        assertTrue(store.exists());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("data/catalog.json")));
        assertEquals(List.of(rat), store.load());

        Path export = temporaryDirectory.resolve("portable/catalog-export.json");
        store.exportTo(export);
        ActorCatalogStore imported = new ActorCatalogStore(
                temporaryDirectory.resolve("imported"),
                "actors.json",
                2);
        imported.importFrom(export);
        assertEquals(List.of(rat), imported.load());
    }

    @Test
    void malformedJsonAndUnknownEnumAreClearAndCannotReplaceCurrentCatalog() throws IOException {
        ActorCatalogEntry rat = ActorCatalogJsonCodecTest.singleSimpleCreature();
        Path dataDirectory = temporaryDirectory.resolve("data");
        ActorCatalogStore store = new ActorCatalogStore(dataDirectory);
        store.save(List.of(rat));

        Path malformed = temporaryDirectory.resolve("malformed.json");
        Files.writeString(malformed, "{not-json}");
        Json.JsonParseException syntax = assertThrows(
                Json.JsonParseException.class,
                () -> store.importFrom(malformed));
        assertTrue(syntax.getMessage().contains("line 1"));
        assertEquals(List.of(rat), store.load());
        assertFalse(Files.exists(dataDirectory.resolve("backups")));

        Map<String, Object> invalid = ActorCatalogJsonCodec.encode(List.of(rat));
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) invalid.get("entries");
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) entries.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) entry.get("template");
        template.put("kind", "DRAGON");
        Path unknownEnum = temporaryDirectory.resolve("unknown-enum.json");
        Files.writeString(unknownEnum, Json.encode(invalid));

        ActorCatalogJsonCodec.CatalogFormatException enumError = assertThrows(
                ActorCatalogJsonCodec.CatalogFormatException.class,
                () -> store.importFrom(unknownEnum));
        assertTrue(enumError.getMessage().contains("$.entries[0].template.kind"));
        assertTrue(enumError.getMessage().contains("unknown ActorKind value 'DRAGON'"));
        assertEquals(List.of(rat), store.load());
        assertFalse(Files.exists(dataDirectory.resolve("backups")));
    }

    @Test
    void acceptedImportUsesNormalBackupRetention() throws IOException {
        ActorCatalogEntry rat = ActorCatalogJsonCodecTest.singleSimpleCreature();
        Path dataDirectory = temporaryDirectory.resolve("data");
        ActorCatalogStore store = new ActorCatalogStore(dataDirectory, "roster", 2);
        store.save(List.of(rat));
        Path emptyCatalog = temporaryDirectory.resolve("empty.json");
        Files.writeString(emptyCatalog, Json.encode(ActorCatalogJsonCodec.encode(List.of())));

        store.importFrom(emptyCatalog);

        assertEquals(List.of(), store.load());
        try (var files = Files.list(dataDirectory.resolve("backups"))) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
    }
}
