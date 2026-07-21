package app.d6d.persistence.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicJsonStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndLoadsAnObjectWithoutLeavingTemporaryFiles() throws IOException {
        Path dataDirectory = temporaryDirectory.resolve("nested/data");
        AtomicJsonStore store = new AtomicJsonStore(dataDirectory, "combat", 3);
        Map<String, Object> value = object(
                "schemaVersion", 1,
                "name", "Rovine 🐉",
                "combatants", List.of("Aria", "Goblin"));

        assertFalse(store.exists());
        store.save(value);

        assertTrue(store.exists());
        assertEquals(value, store.loadObject());
        assertEquals(value, Json.parseObject(Files.readString(
                dataDirectory.resolve("combat.json"),
                StandardCharsets.UTF_8)));
        assertEquals(List.of("combat.json"), regularFileNames(dataDirectory));
    }

    @Test
    void acceptsAJsonSuffixWithoutDuplicatingIt() throws IOException {
        AtomicJsonStore store = new AtomicJsonStore(temporaryDirectory, "session.json", 1);

        store.save(object("round", 2));

        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("session.json")));
        assertFalse(Files.exists(temporaryDirectory.resolve("session.json.json")));
    }

    @Test
    void createsTimestampedBackupsBeforeReplacementAndPrunesOldVersions() throws IOException {
        AtomicJsonStore store = new AtomicJsonStore(temporaryDirectory, "campaign", 2);

        store.save(object("version", 1));
        store.save(object("version", 2));
        store.save(object("version", 3));
        store.save(object("version", 4));

        assertEquals(4, store.loadObject().get("version"));
        Path backupDirectory = temporaryDirectory.resolve("backups");
        List<Path> backups;
        try (Stream<Path> entries = Files.list(backupDirectory)) {
            backups = entries.filter(Files::isRegularFile).sorted().toList();
        }

        assertEquals(2, backups.size());
        assertTrue(backups.stream().allMatch(path -> path.getFileName().toString()
                .matches("campaign-\\d{8}T\\d{6}\\.\\d{9}Z(?:-\\d+)?\\.json")));
        Set<Object> retainedVersions = new HashSet<>();
        for (Path backup : backups) {
            retainedVersions.add(Json.parseObject(Files.readString(backup)).get("version"));
        }
        assertEquals(Set.of(2, 3), retainedVersions);
        assertTrue(regularFileNames(backupDirectory).stream().noneMatch(name -> name.endsWith(".tmp")));
    }

    @Test
    void zeroBackupLimitKeepsNoHistoricalFiles() throws IOException {
        Path backupDirectory = temporaryDirectory.resolve("backups");
        Files.createDirectories(backupDirectory);
        Files.writeString(backupDirectory.resolve("state-20000101T000000.000000000Z.json"), "{}");
        AtomicJsonStore store = new AtomicJsonStore(temporaryDirectory, "state", 0);

        store.save(object("revision", 1));
        store.save(object("revision", 2));

        assertEquals(List.of(), regularFileNames(backupDirectory));
        assertEquals(2, store.loadObject().get("revision"));
    }

    @Test
    void exportsAValidatedIndependentCopyAndCreatesParentDirectories() throws IOException {
        AtomicJsonStore store = new AtomicJsonStore(temporaryDirectory.resolve("store"), "fight", 1);
        Map<String, Object> value = object(
                "schemaVersion", 1,
                "round", 7,
                "log", List.of("attack", "damage"));
        store.save(value);
        Path destination = temporaryDirectory.resolve("exports/portable/fight-export.json");

        store.exportTo(destination);

        assertEquals(value, Json.parseObject(Files.readString(destination)));
        Files.writeString(destination, "{\"changed\":true}");
        assertEquals(value, store.loadObject());
        assertEquals(List.of("fight-export.json"), regularFileNames(destination.getParent()));
    }

    @Test
    void importValidatesBeforeReplacingAndUsesTheNormalBackupPath() throws IOException {
        AtomicJsonStore store = new AtomicJsonStore(temporaryDirectory.resolve("store"), "fight", 3);
        Map<String, Object> original = object("revision", 1, "name", "original");
        Map<String, Object> imported = object("revision", 2, "name", "imported");
        store.save(original);
        Path importFile = temporaryDirectory.resolve("incoming.json");
        Files.writeString(importFile, Json.encode(imported));

        store.importFrom(importFile);

        assertEquals(imported, store.loadObject());
        List<Path> backups;
        try (Stream<Path> entries = Files.list(temporaryDirectory.resolve("store/backups"))) {
            backups = entries.filter(Files::isRegularFile).toList();
        }
        assertEquals(1, backups.size());
        assertEquals(original, Json.parseObject(Files.readString(backups.get(0))));
    }

    @Test
    void rejectedImportCannotModifyDataOrCreateABackup() throws IOException {
        Path dataDirectory = temporaryDirectory.resolve("store");
        AtomicJsonStore store = new AtomicJsonStore(dataDirectory, "fight", 3);
        Map<String, Object> original = object("revision", 1);
        store.save(original);
        Path invalidImport = temporaryDirectory.resolve("invalid.json");
        Files.writeString(invalidImport, "[1,2,3]");

        assertThrows(Json.JsonParseException.class, () -> store.importFrom(invalidImport));

        assertEquals(original, store.loadObject());
        assertFalse(Files.exists(dataDirectory.resolve("backups")));
    }

    @Test
    void loadRejectsMissingAndCorruptFilesClearly() throws IOException {
        AtomicJsonStore store = new AtomicJsonStore(temporaryDirectory, "state", 1);
        assertThrows(NoSuchFileException.class, store::loadObject);

        Files.writeString(temporaryDirectory.resolve("state.json"), "{broken}");
        assertThrows(Json.JsonParseException.class, store::loadObject);
    }

    @Test
    void failedEncodingDoesNotTouchAnExistingFile() throws IOException {
        AtomicJsonStore store = new AtomicJsonStore(temporaryDirectory, "state", 2);
        Map<String, Object> original = object("revision", 1);
        store.save(original);
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("bad", Double.NaN);

        assertThrows(IllegalArgumentException.class, () -> store.save(invalid));

        assertEquals(original, store.loadObject());
        assertFalse(Files.exists(temporaryDirectory.resolve("backups")));
    }

    @Test
    void rejectsUnsafeConfigurationAndSelfExport() throws IOException {
        assertThrows(NullPointerException.class, () -> new AtomicJsonStore(null, "state", 1));
        assertThrows(NullPointerException.class, () -> new AtomicJsonStore(temporaryDirectory, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new AtomicJsonStore(temporaryDirectory, " ", 1));
        assertThrows(IllegalArgumentException.class, () -> new AtomicJsonStore(temporaryDirectory, "../state", 1));
        assertThrows(IllegalArgumentException.class, () -> new AtomicJsonStore(temporaryDirectory, "state", -1));

        AtomicJsonStore store = new AtomicJsonStore(temporaryDirectory, "state", 1);
        store.save(object("ok", true));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.exportTo(temporaryDirectory.resolve("state.json")));
    }

    private static Map<String, Object> object(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            result.put((String) keyValues[index], keyValues[index + 1]);
        }
        return result;
    }

    private static List<String> regularFileNames(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .forEach(names::add);
        }
        return names;
    }
}
