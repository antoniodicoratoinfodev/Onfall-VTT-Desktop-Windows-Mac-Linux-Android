package app.d6d.persistence.session;

import app.d6d.domain.combat.CombatState;
import app.d6d.engine.CombatSession;
import app.d6d.persistence.combat.CombatSessionJsonCodec;
import app.d6d.persistence.json.AtomicJsonStore;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Archivio delle sessioni salvate con un nome.
 *
 * <p>Ogni sessione e' un file JSON nella cartella indicata, scritto tramite
 * {@link AtomicJsonStore}: scrittura atomica e copie di backup, quindi
 * un'interruzione durante il salvataggio non lascia mai una sessione troncata.</p>
 *
 * <p>Il file contiene tutto cio' che serve a riprendere il tavolo: stato del
 * combattimento con mappa e segnaposti, registro completo, stato del generatore
 * casuale, e lo stato di presentazione che il motore non conosce.</p>
 */
public final class SessionArchiveStore {

    public static final int SCHEMA_VERSION = 1;
    private static final int MAX_BACKUPS = 5;

    private final Path directory;
    private final CombatSessionJsonCodec codec;

    public SessionArchiveStore(Path directory) {
        this(directory, new CombatSessionJsonCodec());
    }

    public SessionArchiveStore(Path directory, CombatSessionJsonCodec codec) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * Trasforma un nome scelto dall'utente in un nome di file sicuro.
     *
     * <p>Accenti, spazi e punteggiatura diventano trattini: il nome leggibile
     * viene comunque conservato dentro il file, quindi non si perde nulla.</p>
     */
    public static String slugify(String displayName) {
        String cleaned = displayName == null ? "" : displayName.trim().toLowerCase(Locale.ROOT);
        cleaned = cleaned.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "sessione";
        }
        // Un nome lunghissimo non deve produrre un percorso illegale.
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }

    /** Sessioni salvate, dalla piu' recente alla piu' vecchia. */
    public synchronized List<SessionSummary> list() throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<SessionSummary> summaries = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                String slug = file.getFileName().toString().replaceFirst("\\.json$", "");
                try {
                    summaries.add(readSummary(slug));
                } catch (IOException | RuntimeException unreadable) {
                    // Un file illeggibile non deve impedire di vedere gli altri:
                    // viene elencato come danneggiato invece di far fallire tutto.
                    summaries.add(new SessionSummary(slug, slug, "", 0, 0, "ILLEGGIBILE"));
                }
            }
        }
        summaries.sort(Comparator.comparing(SessionSummary::savedAt).reversed());
        return List.copyOf(summaries);
    }

    public synchronized boolean exists(String slug) {
        return store(slug).exists();
    }

    /**
     * Salva la sessione con il nome indicato.
     *
     * @return il nome di file usato, che il chiamante puo' riutilizzare per ricaricare
     */
    public synchronized String save(
            String displayName,
            CombatSession session,
            Map<String, String> presentation) throws IOException {
        Objects.requireNonNull(session, "session");
        String slug = slugify(displayName);
        String name = displayName == null || displayName.isBlank() ? slug : displayName.trim();

        CombatState state = session.currentState();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", SCHEMA_VERSION);
        document.put("slug", slug);
        document.put("displayName", name);
        document.put("savedAt", Instant.now().toString());
        document.put("round", state.round());
        document.put("combatantCount", state.combatants().size());
        document.put("status", state.status().name());
        document.put("presentation", new LinkedHashMap<String, Object>(
                presentation == null ? Map.of() : presentation));
        document.put("combat", codec.encode(session));

        Files.createDirectories(directory);
        store(slug).save(document);
        return slug;
    }

    /** Ricarica una sessione completa. */
    public synchronized SessionArchive load(String slug) throws IOException {
        Map<String, Object> document = store(slug).loadObject();
        int schemaVersion = intValue(document.get("schemaVersion"));
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IOException("Versione dell'archivio non supportata: " + schemaVersion);
        }

        Object combat = document.get("combat");
        if (!(combat instanceof Map<?, ?> combatMap)) {
            throw new IOException("La sessione salvata non contiene un combattimento");
        }

        @SuppressWarnings("unchecked")
        CombatSession session = codec.decode((Map<String, ?>) combatMap);

        Map<String, String> presentation = new LinkedHashMap<>();
        if (document.get("presentation") instanceof Map<?, ?> raw) {
            raw.forEach((key, value) -> {
                if (key != null && value != null) {
                    presentation.put(key.toString(), value.toString());
                }
            });
        }

        return new SessionArchive(summaryFrom(slug, document), session, presentation);
    }

    public synchronized void delete(String slug) throws IOException {
        Files.deleteIfExists(directory.resolve(slug + ".json"));
    }

    private SessionSummary readSummary(String slug) throws IOException {
        return summaryFrom(slug, store(slug).loadObject());
    }

    private SessionSummary summaryFrom(String slug, Map<String, Object> document) {
        return new SessionSummary(
                slug,
                stringValue(document.get("displayName"), slug),
                stringValue(document.get("savedAt"), ""),
                intValue(document.get("round")),
                intValue(document.get("combatantCount")),
                stringValue(document.get("status"), ""));
    }

    private AtomicJsonStore store(String slug) {
        return new AtomicJsonStore(directory, slug, MAX_BACKUPS);
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }
}
