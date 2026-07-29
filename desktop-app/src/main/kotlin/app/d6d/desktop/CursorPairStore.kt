package app.d6d.desktop

import app.d6d.ui.cursors.CursorPair
import app.d6d.ui.cursors.CursorSize
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Preferenza locale della coppia di cursori.
 *
 * È volutamente separata dal layout condiviso: il cursore esiste soltanto sul
 * desktop e deve essere noto alla finestra prima che `AppRoot` costruisca la UI.
 */
internal class CursorPairStore(private val file: Path) {

    fun load(): CursorPair {
        val stored = runCatching {
            String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim().lowercase()
        }.getOrNull()
        return when (stored) {
            "warm" -> CursorPair.WARM
            "classic" -> CursorPair.CLASSIC
            "runic" -> CursorPair.RUNIC
            "steel" -> CursorPair.STEEL
            else -> CursorPair.COLD
        }
    }

    /**
     * Salva con sostituzione atomica quando il filesystem la supporta.
     *
     * Un errore non interrompe l'app: la scelta è già applicata allo stato vivo e
     * resterà valida fino alla chiusura della finestra.
     */
    fun save(pair: CursorPair): Boolean = runCatching {
        file.parent?.let(Files::createDirectories)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        val value = when (pair) {
            CursorPair.COLD -> "cold"
            CursorPair.WARM -> "warm"
            CursorPair.CLASSIC -> "classic"
            CursorPair.RUNIC -> "runic"
            CursorPair.STEEL -> "steel"
        }
        writeAtomically(temporary, value)
    }.isSuccess

    private fun writeAtomically(temporary: Path, value: String) {
        Files.write(temporary, value.toByteArray(StandardCharsets.UTF_8))
        runCatching {
            Files.move(
                temporary,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/** Preferenza locale della dimensione visiva dei cursori. */
internal class CursorSizeStore(private val file: Path) {

    fun load(): CursorSize {
        val stored = runCatching {
            String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim().lowercase()
        }.getOrNull()
        return when (stored) {
            "small" -> CursorSize.SMALL
            "medium" -> CursorSize.MEDIUM
            else -> CursorSize.ORIGINAL
        }
    }

    fun save(size: CursorSize): Boolean = runCatching {
        file.parent?.let(Files::createDirectories)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.write(
            temporary,
            size.name.lowercase().toByteArray(StandardCharsets.UTF_8),
        )
        runCatching {
            Files.move(
                temporary,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }.isSuccess
}
