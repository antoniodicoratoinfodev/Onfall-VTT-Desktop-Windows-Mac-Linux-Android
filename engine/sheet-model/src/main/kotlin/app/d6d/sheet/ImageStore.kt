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

    fun isSupported(source: Path): Boolean =
        source.fileName?.toString()?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) in SUPPORTED_FORMATS

    /**
     * Copia un'immagine nell'archivio e ne restituisce il nome interno.
     *
     * Il nome e' reso univoco con un contatore invece di sovrascrivere: due
     * personaggi diversi possono avere file di partenza omonimi.
     *
     * Prima di copiare, il file viene controllato tre volte: dimensione entro il
     * limite, estensione riconosciuta e — soprattutto — contenuto realmente di un
     * formato immagine supportato. Un file rinominato `.png` ma pieno d'altro (un
     * eseguibile, un archivio) verrebbe accettato dal solo controllo d'estensione:
     * la firma nei primi byte e' la difesa vera, perche' e' il contenuto a essere
     * poi decodificato e mostrato.
     */
    fun importImage(source: Path): String {
        require(Files.isRegularFile(source)) { "Il file non esiste: $source" }

        val size = Files.size(source)
        require(size <= MAX_IMAGE_BYTES) {
            "Immagine troppo grande (${humanBytes(size)}): il limite e' $maxSizeLabel."
        }
        require(isSupported(source)) {
            "Formato immagine non supportato: ${source.fileName}. Formati accettati: $acceptedFormatsLabel."
        }
        require(sniffFormat(source) != null) {
            "Il file non e' un'immagine valida o e' danneggiato. Formati accettati: $acceptedFormatsLabel."
        }

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

    /**
     * Riconosce il formato dai primi byte del file, non dall'estensione.
     *
     * Restituisce il nome del formato quando la firma corrisponde a uno di quelli
     * supportati, altrimenti null. Legge solo l'intestazione: non serve caricare in
     * memoria un file da centinaia di megabyte per sapere se e' davvero un'immagine.
     */
    private fun sniffFormat(source: Path): String? {
        val header = ByteArray(HEADER_BYTES)
        val read = Files.newInputStream(source).use { input ->
            var total = 0
            while (total < header.size) {
                val n = input.read(header, total, header.size - total)
                if (n < 0) break
                total += n
            }
            total
        }
        fun matches(signature: IntArray): Boolean {
            if (read < signature.size) return false
            return signature.withIndex().all { (i, b) -> b < 0 || header[i].toInt() and 0xFF == b }
        }
        return when {
            matches(PNG) -> "png"
            matches(JPEG) -> "jpg"
            matches(GIF87) || matches(GIF89) -> "gif"
            matches(BMP) -> "bmp"
            // WEBP: "RIFF" .... "WEBP"; i quattro byte della dimensione sono liberi.
            matches(RIFF) && read >= 12 &&
                header[8].toInt() == 'W'.code && header[9].toInt() == 'E'.code &&
                header[10].toInt() == 'B'.code && header[11].toInt() == 'P'.code -> "webp"
            else -> null
        }
    }

    companion object {
        /** Limite di dimensione del file scelto: 200 MB. */
        const val MAX_IMAGE_BYTES: Long = 200L * 1024 * 1024

        /** Estensioni riconosciute; `decodeToImageBitmap` gestisce questi formati. */
        val SUPPORTED_FORMATS: Set<String> = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

        /** Testo pronto da mostrare all'utente: formati accettati e limite. */
        const val acceptedFormatsLabel: String = "PNG, JPG, WEBP, BMP, GIF"
        const val maxSizeLabel: String = "200 MB"

        private const val HEADER_BYTES = 16

        // Firme dei formati supportati. -1 significa "byte qualsiasi".
        private val PNG = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val JPEG = intArrayOf(0xFF, 0xD8, 0xFF)
        private val GIF87 = intArrayOf(0x47, 0x49, 0x46, 0x38, 0x37, 0x61)
        private val GIF89 = intArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
        private val BMP = intArrayOf(0x42, 0x4D)
        private val RIFF = intArrayOf(0x52, 0x49, 0x46, 0x46)

        private fun humanBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val units = listOf("KB", "MB", "GB")
            var value = bytes.toDouble() / 1024
            var unit = 0
            while (value >= 1024 && unit < units.size - 1) {
                value /= 1024
                unit++
            }
            return "${(value * 10).toLong() / 10.0} ${units[unit]}"
        }
    }
}
