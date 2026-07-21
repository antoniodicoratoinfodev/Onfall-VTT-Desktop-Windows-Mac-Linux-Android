package app.d6d.sheet

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Contenuto salvato: schede dei personaggi e stat block dei mostri. */
@Serializable
data class SheetLibrary(
    val schemaVersion: Int = SCHEMA_VERSION,
    val characters: List<CharacterSheet> = emptyList(),
    val monsters: List<MonsterStatBlock> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/**
 * Archivio locale delle schede.
 *
 * Scrive in modo atomico — file temporaneo e sostituzione — cosi' un'interruzione
 * non lascia mai un archivio troncato, e tiene una copia della versione precedente.
 * Stesso principio dello store del catalogo gia' presente nel motore.
 */
class SheetStore(private val file: Path) {

    private val json = Json {
        prettyPrint = true
        // Un campo aggiunto in futuro non deve impedire di leggere un file vecchio.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exists(): Boolean = Files.exists(file)

    fun load(): SheetLibrary {
        if (!Files.exists(file)) return SheetLibrary()
        val text = Files.readString(file)
        if (text.isBlank()) return SheetLibrary()
        return json.decodeFromString(SheetLibrary.serializer(), text)
    }

    fun save(library: SheetLibrary) {
        file.parent?.let { Files.createDirectories(it) }

        if (Files.exists(file)) {
            val backup = file.resolveSibling("${file.fileName}.bak")
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING)
        }

        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(SheetLibrary.serializer(), library))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
    }
}
