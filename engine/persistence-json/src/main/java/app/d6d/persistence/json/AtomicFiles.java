package app.d6d.persistence.json;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Low-level, reusable replace-by-rename file operations.
 *
 * <p>The temporary file is always unique and lives next to its destination,
 * so a successful atomic rename cannot cross filesystems. Bytes and directory
 * metadata are flushed on a best-effort basis; platforms that do not expose
 * {@code fsync} or atomic moves transparently fall back to a normal replacing
 * move.</p>
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /** Replaces a UTF-8 text file atomically when the filesystem supports it. */
    public static void writeUtf8(Path destination, String text) throws IOException {
        Objects.requireNonNull(text, "text");
        write(destination, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Replaces a UTF-8 text file and first copies the previous version to a
     * stable backup path.
     */
    public static void writeUtf8WithBackup(Path destination, Path backup, String text) throws IOException {
        Objects.requireNonNull(text, "text");
        writeWithBackup(destination, backup, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Replaces a binary file atomically when the filesystem supports it. */
    public static void write(Path destination, byte[] bytes) throws IOException {
        replace(destination, bytes, null);
    }

    /** Copies a file through a same-directory temporary and replacing rename. */
    public static void copy(Path source, Path destination) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (!Files.isRegularFile(source)) {
            throw new IOException("Source is not a regular file: " + source);
        }
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        Path parent = normalizedDestination.getParent();
        if (parent == null) {
            throw new IOException("Destination has no parent directory: " + destination);
        }
        Files.createDirectories(parent);
        requireRegularFileOrMissing(normalizedDestination, "Destination");
        Path temporary = createTemporaryFile(parent, normalizedDestination.getFileName().toString());
        try {
            Files.copy(
                    source,
                    temporary,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            forceFileBestEffort(temporary);
            moveReplacing(temporary, normalizedDestination);
            forceDirectoryBestEffort(parent);
        } finally {
            deleteTemporaryBestEffort(temporary);
        }
    }

    /** Replaces a binary file after preserving the previous version. */
    public static void writeWithBackup(Path destination, Path backup, byte[] bytes) throws IOException {
        replace(destination, bytes, Objects.requireNonNull(backup, "backup"));
    }

    private static void replace(Path destination, byte[] bytes, Path backup) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(bytes, "bytes");
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        Path parent = normalizedDestination.getParent();
        if (parent == null) {
            throw new IOException("Destination has no parent directory: " + destination);
        }
        Files.createDirectories(parent);
        requireRegularFileOrMissing(normalizedDestination, "Destination");

        Path temporary = createTemporaryFile(parent, normalizedDestination.getFileName().toString());
        try {
            writeAndForce(temporary, bytes);
            if (backup != null && Files.isRegularFile(normalizedDestination)) {
                copyReplacing(normalizedDestination, backup.toAbsolutePath().normalize());
            }
            moveReplacing(temporary, normalizedDestination);
            forceDirectoryBestEffort(parent);
        } finally {
            deleteTemporaryBestEffort(temporary);
        }
    }

    private static void copyReplacing(Path source, Path destination) throws IOException {
        Path parent = destination.getParent();
        if (parent == null) {
            throw new IOException("Backup has no parent directory: " + destination);
        }
        Files.createDirectories(parent);
        requireRegularFileOrMissing(destination, "Backup");
        Path temporary = createTemporaryFile(parent, destination.getFileName().toString());
        try {
            Files.copy(
                    source,
                    temporary,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            forceFileBestEffort(temporary);
            moveReplacing(temporary, destination);
            forceDirectoryBestEffort(parent);
        } finally {
            deleteTemporaryBestEffort(temporary);
        }
    }

    private static void requireRegularFileOrMissing(Path path, String label) throws IOException {
        if (Files.exists(path) && !Files.isRegularFile(path)) {
            throw new IOException(label + " is not a regular file: " + path);
        }
    }

    static Path createTemporaryFile(Path directory, String hint) throws IOException {
        String safeHint = hint.replaceAll("[^A-Za-z0-9._-]", "_");
        String prefix = '.' + safeHint + '-';
        if (prefix.length() < 3) {
            prefix = ".d6d-";
        }
        return Files.createTempFile(directory, prefix, ".tmp");
    }

    static void writeUtf8AndForce(Path file, String text) throws IOException {
        writeAndForce(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeAndForce(Path file, byte[] bytes) throws IOException {
        Files.write(
                file,
                bytes,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        forceFileBestEffort(file);
    }

    static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void forceFileBestEffort(Path file) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Some filesystems/providers do not expose fsync. Rename still works.
        }
    }

    static void forceDirectoryBestEffort(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Directory channels are not available on every supported platform.
        }
    }

    static void deleteTemporaryBestEffort(Path temporaryFile) {
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            // A stale temp file is safer than masking the original write error.
        }
    }
}
