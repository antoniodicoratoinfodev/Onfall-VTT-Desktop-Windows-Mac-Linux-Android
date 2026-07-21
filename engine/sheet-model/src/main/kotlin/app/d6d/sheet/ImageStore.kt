package app.d6d.sheet

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * Associazione fra una definizione di attore e la sua immagine.
 *
 * <p>Le chiavi sono identificatori stabili di definizione, non nomi visualizzati:
 * rinominare un personaggio non deve fargli perdere il ritratto.</p>
 */
@Serializable
data class PortraitLibrary(
    val schemaVersion: Int = 1,
    /** definitionId → nome del file nell'archivio locale. */
    val portraits: Map<String, String> = emptyMap(),
)

/**
 * Archivio locale delle immagini caricate dall'utente.
 *
 * Le immagini vengono **copiate** dentro la cartella dati, non referenziate dove
 * si trovano: spostare o cancellare il file originale non deve rompere una scheda.
 *
 * Restano deliberatamente locali. Il paragrafo 11 del documento stabilisce che gli
 * asset posseduti privatamente dall'utente sono esclusi per impostazione predefinita
 * dagli export condivisibili — copiare l'illustrazione di un manuale non la rende
 * distribuibile — quindi questo archivio vive fuori dal pacchetto di esportazione e
 * va allegato solo con una scelta esplicita.
 */
class ImageStore(private val dataDirectory: Path) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private val libraryFile: Path get() = dataDirectory.resolve("ritratti.json")

    val imagesDirectory: Path get() = dataDirectory.resolve("images")

    /** Estensioni riconosciute; `decodeToImageBitmap` gestisce questi formati. */
    private val supported = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    fun isSupported(source: Path): Boolean =
        source.fileName?.toString()?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in supported

    /**
     * Copia un'immagine nell'archivio e ne restituisce il nome interno.
     *
     * Il nome e' reso univoco con un contatore invece di sovrascrivere: due
     * personaggi diversi possono avere file di partenza omonimi.
     */
    fun importImage(source: Path): String {
        require(Files.isRegularFile(source)) { "Il file non esiste: $source" }
        require(isSupported(source)) { "Formato immagine non supportato: ${source.fileName}" }

        Files.createDirectories(imagesDirectory)
        val original = source.fileName.toString()
        val extension = original.substringAfterLast('.', "png")
        val base = original.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._-]"), "_")

        var candidate = "$base.$extension"
        var counter = 1
        while (Files.exists(imagesDirectory.resolve(candidate))) {
            candidate = "$base-$counter.$extension"
            counter++
        }

        Files.copy(source, imagesDirectory.resolve(candidate), StandardCopyOption.COPY_ATTRIBUTES)
        return candidate
    }

    /** Percorso di un'immagine dell'archivio, o null se non c'e'. */
    fun resolve(name: String): Path? {
        if (name.isBlank()) return null
        // Solo nomi semplici: un percorso relativo non deve poter uscire dall'archivio.
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return null
        val path = imagesDirectory.resolve(name)
        return if (Files.isRegularFile(path)) path else null
    }

    fun readBytes(name: String): ByteArray? = resolve(name)?.let { Files.readAllBytes(it) }

    fun loadLibrary(): PortraitLibrary {
        if (!Files.exists(libraryFile)) return PortraitLibrary()
        val text = Files.readString(libraryFile)
        if (text.isBlank()) return PortraitLibrary()
        return json.decodeFromString(PortraitLibrary.serializer(), text)
    }

    fun saveLibrary(library: PortraitLibrary) {
        Files.createDirectories(dataDirectory)
        val temporary = libraryFile.resolveSibling("${libraryFile.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(PortraitLibrary.serializer(), library))
        Files.move(temporary, libraryFile, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Elimina un'immagine dall'archivio; nessun effetto se non esiste. */
    fun deleteImage(name: String) {
        resolve(name)?.let { Files.deleteIfExists(it) }
    }
}
