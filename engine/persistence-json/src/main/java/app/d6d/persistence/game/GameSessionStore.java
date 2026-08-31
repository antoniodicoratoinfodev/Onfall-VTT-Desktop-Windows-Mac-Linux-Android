package app.d6d.persistence.game;

import app.d6d.engine.GameSession;
import app.d6d.persistence.json.AtomicJsonStore;
import app.d6d.persistence.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Archivio atomico e importabile di una sessione generale. */
public final class GameSessionStore {
    private static final int DEFAULT_MAX_BACKUPS = 20;
    private final AtomicJsonStore store;
    private final GameSessionJsonCodec codec;

    public GameSessionStore(Path directory, String baseName) {
        this(new AtomicJsonStore(directory, baseName, DEFAULT_MAX_BACKUPS), new GameSessionJsonCodec());
    }

    public GameSessionStore(AtomicJsonStore store, GameSessionJsonCodec codec) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public synchronized void save(GameSession session) throws IOException {
        store.save(codec.encode(session));
    }

    public synchronized GameSession load() throws IOException {
        return codec.decode(store.loadObject());
    }

    public synchronized void exportTo(Path destination) throws IOException {
        codec.decode(store.loadObject());
        store.exportTo(destination);
    }

    public synchronized GameSession importFrom(Path source) throws IOException {
        String json = Files.readString(Objects.requireNonNull(source, "source"), StandardCharsets.UTF_8);
        Map<String, Object> document = Json.parseObject(json);
        GameSession imported = codec.decode(document);
        store.save(document);
        return imported;
    }

    public boolean exists() {
        return store.exists();
    }
}
