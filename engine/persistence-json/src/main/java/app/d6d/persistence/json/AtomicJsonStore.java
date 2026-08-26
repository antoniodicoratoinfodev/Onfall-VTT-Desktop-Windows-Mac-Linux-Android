package app.d6d.persistence.json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Stores one JSON object in a local file using replace-by-rename writes.
 *
 * <p>The current file is {@code <baseName>.json} inside {@code dataDirectory}
 * (an already present {@code .json} suffix is not duplicated). Before an
 * existing file is replaced, its bytes are copied to the {@code backups}
 * subdirectory. Backup names contain a UTC timestamp and only the newest
 * configured number are retained.</p>
 *
 * <p>Public operations are synchronized so a single store instance can be
 * shared by autosave and UI threads. A temporary file is always created next
 * to its destination, flushed on a best-effort basis, and renamed with
 * {@link StandardCopyOption#ATOMIC_MOVE}; filesystems without atomic rename
 * transparently use a normal replacing move.</p>
 */
public final class AtomicJsonStore {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("uuuuMMdd'T'HHmmss")
            .appendLiteral('.')
            .appendFraction(ChronoField.NANO_OF_SECOND, 9, 9, false)
            .appendLiteral('Z')
            .toFormatter(Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final Path dataDirectory;
    private final Path dataFile;
    private final Path backupDirectory;
    private final String fileStem;
    private final int maxBackups;

    /**
     * Creates a store configuration without touching the filesystem.
     *
     * @param dataDirectory directory containing the current file
     * @param baseName safe filename, with or without the {@code .json} suffix
     * @param maxBackups maximum retained backups; zero disables retention
     */
    public AtomicJsonStore(Path dataDirectory, String baseName, int maxBackups) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        validateBaseName(baseName);
        if (maxBackups < 0) {
            throw new IllegalArgumentException("maxBackups must be zero or greater");
        }

        String fileName = baseName.endsWith(".json") ? baseName : baseName + ".json";
        this.fileStem = fileName.substring(0, fileName.length() - ".json".length());
        if (fileStem.isEmpty()) {
            throw new IllegalArgumentException("baseName must include a name before .json");
        }
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.dataFile = this.dataDirectory.resolve(fileName);
        this.backupDirectory = this.dataDirectory.resolve("backups");
        this.maxBackups = maxBackups;
    }

    /** Returns whether the current JSON file exists as a regular file. */
    public boolean exists() {
        return Files.isRegularFile(dataFile);
    }

    /**
     * Encodes and saves an object. An existing version is backed up before it
     * is replaced.
     */
    public synchronized void save(Map<String, Object> value) throws IOException {
        Objects.requireNonNull(value, "value");
        String json = Json.encode(value);

        Files.createDirectories(dataDirectory);
        if (Files.exists(dataFile) && !Files.isRegularFile(dataFile)) {
            throw new IOException("JSON data path is not a regular file: " + dataFile);
        }

        Path temporaryFile = AtomicFiles.createTemporaryFile(dataDirectory, fileStem);
        try {
            AtomicFiles.writeUtf8AndForce(temporaryFile, json);

            if (Files.isRegularFile(dataFile) && maxBackups > 0) {
                createVersionedBackup();
            }
            pruneBackups();

            AtomicFiles.moveReplacing(temporaryFile, dataFile);
            AtomicFiles.forceDirectoryBestEffort(dataDirectory);
        } finally {
            AtomicFiles.deleteTemporaryBestEffort(temporaryFile);
        }
    }

    /** Loads and validates the current document as a JSON object. */
    public synchronized Map<String, Object> loadObject() throws IOException {
        String json = Files.readString(dataFile, StandardCharsets.UTF_8);
        return Json.parseObject(json);
    }

    /**
     * Deletes the current document, leaving versioned backups untouched.
     *
     * <p>The path is the same validated path used by every other operation on
     * this store. Callers therefore do not need to rebuild it from an unchecked
     * base name.</p>
     */
    public synchronized boolean deleteCurrent() throws IOException {
        boolean deleted = Files.deleteIfExists(dataFile);
        if (deleted) {
            AtomicFiles.forceDirectoryBestEffort(dataDirectory);
        }
        return deleted;
    }

    /**
     * Exports the validated current object to {@code destination}. The export
     * itself also uses a same-directory temporary file and replacing move.
     */
    public synchronized void exportTo(Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        if (normalizedDestination.equals(dataFile)) {
            throw new IllegalArgumentException("Export destination must differ from the store file");
        }

        Map<String, Object> value = loadObject();
        AtomicFiles.writeUtf8(normalizedDestination, Json.encode(value));
    }

    /**
     * Imports a UTF-8 JSON object. Validation completes before the current
     * file or its backups are changed.
     */
    public synchronized void importFrom(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        String json = Files.readString(source, StandardCharsets.UTF_8);
        Map<String, Object> value = Json.parseObject(json);
        save(value);
    }

    private void createVersionedBackup() throws IOException {
        Files.createDirectories(backupDirectory);
        Path destination = nextBackupPath();
        Path temporaryBackup = AtomicFiles.createTemporaryFile(backupDirectory, fileStem + "-backup");
        try {
            Files.copy(
                    dataFile,
                    temporaryBackup,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            AtomicFiles.forceFileBestEffort(temporaryBackup);
            AtomicFiles.moveReplacing(temporaryBackup, destination);
            AtomicFiles.forceDirectoryBestEffort(backupDirectory);
        } finally {
            AtomicFiles.deleteTemporaryBestEffort(temporaryBackup);
        }
    }

    private Path nextBackupPath() {
        String prefix = fileStem + '-' + BACKUP_TIMESTAMP.format(Instant.now());
        Path candidate = backupDirectory.resolve(prefix + ".json");
        int collision = 1;
        while (Files.exists(candidate)) {
            candidate = backupDirectory.resolve(prefix + '-' + collision + ".json");
            collision++;
        }
        return candidate;
    }

    private void pruneBackups() throws IOException {
        if (!Files.exists(backupDirectory)) {
            return;
        }
        if (!Files.isDirectory(backupDirectory)) {
            throw new IOException("Backup path is not a directory: " + backupDirectory);
        }

        String prefix = fileStem + '-';
        List<Path> backups = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(backupDirectory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (Files.isRegularFile(entry)
                        && name.startsWith(prefix)
                        && name.endsWith(".json")) {
                    backups.add(entry);
                }
            }
        }

        backups.sort(Comparator.comparing(
                path -> path.getFileName().toString(),
                Comparator.reverseOrder()));
        for (int index = maxBackups; index < backups.size(); index++) {
            Files.deleteIfExists(backups.get(index));
        }
        AtomicFiles.forceDirectoryBestEffort(backupDirectory);
    }

    private static void validateBaseName(String baseName) {
        Objects.requireNonNull(baseName, "baseName");
        if (baseName.isBlank()) {
            throw new IllegalArgumentException("baseName must not be blank");
        }
        if (baseName.equals(".")
                || baseName.equals("..")
                || baseName.indexOf('/') >= 0
                || baseName.indexOf('\\') >= 0
                || baseName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("baseName must be a filename, not a path: " + baseName);
        }
    }
}
