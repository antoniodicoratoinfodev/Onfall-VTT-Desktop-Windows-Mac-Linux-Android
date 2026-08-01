package app.d6d.persistence.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {
    @TempDir
    Path directory;

    @Test
    void replacingTextPreservesThePreviousVersionAndLeavesNoTemporaryFiles() throws IOException {
        Path current = directory.resolve("nested/preferences.json");
        Path backup = directory.resolve("nested/preferences.json.bak");

        AtomicFiles.writeUtf8WithBackup(current, backup, "prima");
        AtomicFiles.writeUtf8WithBackup(current, backup, "dopo");

        assertEquals("dopo", Files.readString(current));
        assertEquals("prima", Files.readString(backup));
        try (Stream<Path> files = Files.list(current.getParent())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void copyingUsesAnIndependentDestination() throws IOException {
        Path source = directory.resolve("source.png");
        Path destination = directory.resolve("archive/image.png");
        Files.write(source, new byte[]{1, 2, 3, 4});

        AtomicFiles.copy(source, destination);
        Files.write(source, new byte[]{9});

        assertTrue(Files.isRegularFile(destination));
        assertEquals("1,2,3,4", byteList(Files.readAllBytes(destination)));
    }

    private static String byteList(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) {
            if (!result.isEmpty()) result.append(',');
            result.append(Byte.toUnsignedInt(value));
        }
        return result.toString();
    }
}
