package app.d6d.persistence.combat;

import app.d6d.engine.CombatSession;
import app.d6d.persistence.json.AtomicJsonStore;
import app.d6d.persistence.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Type-safe persistence adapter for a complete {@link CombatSession}. */
public final class CombatSessionStore {
    private static final String DEFAULT_BASE_NAME = "active-combat";
    private static final int DEFAULT_MAX_BACKUPS = 20;

    private final AtomicJsonStore store;
    private final CombatSessionJsonCodec codec;

    public CombatSessionStore(AtomicJsonStore store) {
        this(store, new CombatSessionJsonCodec());
    }

    public CombatSessionStore(AtomicJsonStore store, CombatSessionJsonCodec codec) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public CombatSessionStore(Path dataDirectory, String baseName, int maxBackups) {
        this(new AtomicJsonStore(dataDirectory, baseName, maxBackups));
    }

    public CombatSessionStore(Path dataDirectory) {
        this(dataDirectory, DEFAULT_BASE_NAME, DEFAULT_MAX_BACKUPS);
    }

    public CombatSessionStore(Path dataDirectory, String baseName) {
        this(dataDirectory, baseName, DEFAULT_MAX_BACKUPS);
    }

    /** Encodes fully before the atomic store is allowed to replace the current file. */
    public synchronized void save(CombatSession session) throws IOException {
        store.save(codec.encode(session));
    }

    public synchronized CombatSession load() throws IOException {
        return codec.decode(store.loadObject());
    }

    public boolean exists() {
        return store.exists();
    }

    /** Exports only a document that is both valid JSON and a valid combat session. */
    public synchronized void exportTo(Path destination) throws IOException {
        codec.decode(store.loadObject());
        store.exportTo(destination);
    }

    public synchronized void export(Path destination) throws IOException {
        exportTo(destination);
    }

    /**
     * Validates syntax, schema and domain invariants before changing the current
     * file, so a corrupt or incompatible import leaves the previous save intact.
     */
    public synchronized CombatSession importFrom(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        String json = Files.readString(source, StandardCharsets.UTF_8);
        Map<String, Object> document = Json.parseObject(json);
        CombatSession imported = codec.decode(document);
        store.save(document);
        return imported;
    }

    public synchronized CombatSession importSession(Path source) throws IOException {
        return importFrom(source);
    }
}
