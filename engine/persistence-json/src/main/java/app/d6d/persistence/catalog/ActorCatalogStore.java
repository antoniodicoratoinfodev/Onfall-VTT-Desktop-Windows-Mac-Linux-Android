package app.d6d.persistence.catalog;

import app.d6d.domain.catalog.ActorCatalogEntry;
import app.d6d.persistence.json.AtomicJsonStore;
import app.d6d.persistence.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Atomic local storage and portable import/export for an actor catalog. */
public final class ActorCatalogStore {
    public static final String DEFAULT_BASE_NAME = "catalog";
    public static final int DEFAULT_MAX_BACKUPS = 10;

    private final AtomicJsonStore store;

    /** Creates a {@code catalog.json} store retaining ten previous versions. */
    public ActorCatalogStore(Path dataDirectory) {
        this(dataDirectory, DEFAULT_BASE_NAME, DEFAULT_MAX_BACKUPS);
    }

    /** Creates a catalog store with explicit filename and backup retention. */
    public ActorCatalogStore(Path dataDirectory, String baseName, int maxBackups) {
        this(new AtomicJsonStore(dataDirectory, baseName, maxBackups));
    }

    /** Wraps an already configured atomic JSON store. */
    public ActorCatalogStore(AtomicJsonStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Returns whether a current catalog file exists. */
    public boolean exists() {
        return store.exists();
    }

    /** Validates, encodes and atomically saves the complete catalog. */
    public synchronized void save(List<ActorCatalogEntry> catalog) throws IOException {
        store.save(ActorCatalogJsonCodec.encode(catalog));
    }

    /** Loads and fully validates the current catalog. */
    public synchronized List<ActorCatalogEntry> load() throws IOException {
        return ActorCatalogJsonCodec.decode(store.loadObject());
    }

    /** Exports a validated independent copy of the current catalog. */
    public synchronized void exportTo(Path destination) throws IOException {
        load();
        store.exportTo(destination);
    }

    /**
     * Imports a catalog only after JSON syntax, schema and all domain values
     * have been validated. A rejected import leaves the current file intact.
     */
    public synchronized void importFrom(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        String json = Files.readString(source, StandardCharsets.UTF_8);
        Map<String, Object> document = Json.parseObject(json);
        ActorCatalogJsonCodec.decode(document);
        store.save(document);
    }
}
