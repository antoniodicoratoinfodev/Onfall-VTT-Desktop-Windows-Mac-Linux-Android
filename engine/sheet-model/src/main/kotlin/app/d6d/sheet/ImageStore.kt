package app.d6d.sheet

import app.d6d.persistence.json.AtomicFiles
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.absoluteValue

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

    private val libraryBackup: Path get() = dataDirectory.resolve("ritratti.json.bak")

    private val mapLibraryFile: Path get() = dataDirectory.resolve("mappe.json")

    private val mapLibraryBackup: Path get() = dataDirectory.resolve("mappe.json.bak")

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
        val format = sniffFormat(source)
        require(format != null) {
            "Il file non e' un'immagine valida o e' danneggiato. Formati accettati: $acceptedFormatsLabel."
        }
        val dimensions = imageDimensions(source, format)
        require(dimensions != null) {
            "Non è possibile leggere le dimensioni dell'immagine: il file è incompleto o danneggiato."
        }
        val pixels = dimensions.width.toLong() * dimensions.height.toLong()
        require(
            dimensions.width in 1..MAX_IMAGE_SIDE &&
                dimensions.height in 1..MAX_IMAGE_SIDE &&
                pixels <= MAX_IMAGE_PIXELS,
        ) {
            "Immagine troppo grande (${dimensions.width}×${dimensions.height} pixel): " +
                "il limite è $maxPixelSizeLabel."
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

        AtomicFiles.copy(source, imagesDirectory.resolve(candidate))
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
        if (!Files.exists(libraryFile) && !Files.exists(libraryBackup)) return PortraitLibrary()
        return loadRecovering(libraryFile, libraryBackup, ::PortraitLibrary) { text ->
            json.decodeFromString(PortraitLibrary.serializer(), text)
        }
    }

    fun saveLibrary(library: PortraitLibrary) {
        AtomicFiles.writeUtf8WithBackup(
            libraryFile,
            libraryBackup,
            json.encodeToString(PortraitLibrary.serializer(), library),
        )
    }

    /** Elimina un'immagine dall'archivio; nessun effetto se non esiste. */
    fun deleteImage(name: String) {
        resolve(name)?.let { Files.deleteIfExists(it) }
    }

    fun loadMapLibrary(): MapLibrary {
        if (!Files.exists(mapLibraryFile) && !Files.exists(mapLibraryBackup)) return MapLibrary()
        return loadRecovering(mapLibraryFile, mapLibraryBackup, ::MapLibrary) { text ->
            json.decodeFromString(MapLibrary.serializer(), text)
        }
    }

    fun saveMapLibrary(library: MapLibrary) {
        AtomicFiles.writeUtf8WithBackup(
            mapLibraryFile,
            mapLibraryBackup,
            json.encodeToString(MapLibrary.serializer(), library),
        )
    }

    private fun <T> loadRecovering(
        primary: Path,
        backup: Path,
        defaultValue: () -> T,
        decode: (String) -> T,
    ): T = try {
        Files.readString(primary).takeUnless(String::isBlank)?.let(decode) ?: defaultValue()
    } catch (failure: Exception) {
        if (!Files.isRegularFile(backup)) throw failure
        try {
            Files.readString(backup).takeUnless(String::isBlank)?.let(decode) ?: defaultValue()
        } catch (backupFailure: Exception) {
            failure.addSuppressed(backupFailure)
            throw failure
        }
    }

    /**
     * Riconosce il formato dai primi byte del file, non dall'estensione.
     *
     * Restituisce il nome del formato quando la firma corrisponde a uno di quelli
     * supportati, altrimenti null. Legge solo l'intestazione: non serve caricare in
     * memoria un file da centinaia di megabyte per sapere se e' davvero un'immagine.
     */
    private fun sniffFormat(source: Path): String? {
        val (header, read) = readHeader(source)
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

    private data class ImageDimensions(val width: Int, val height: Int)

    private fun imageDimensions(source: Path, format: String): ImageDimensions? = when (format) {
        "png" -> {
            val (header, read) = readHeader(source)
            if (read < 24) null else dimensions(bigEndianInt(header, 16), bigEndianInt(header, 20))
        }
        "gif" -> {
            val (header, read) = readHeader(source)
            if (read < 10) null else dimensions(littleEndianShort(header, 6), littleEndianShort(header, 8))
        }
        "bmp" -> bmpDimensions(source)
        "jpg" -> jpegDimensions(source)
        "webp" -> webpDimensions(source)
        else -> null
    }

    private fun bmpDimensions(source: Path): ImageDimensions? {
        val (header, read) = readHeader(source)
        if (read < 26) return null
        val dibSize = littleEndianInt(header, 14)
        return if (dibSize == 12) {
            dimensions(littleEndianShort(header, 18), littleEndianShort(header, 20))
        } else {
            dimensions(littleEndianInt(header, 18).absoluteValue, littleEndianInt(header, 22).absoluteValue)
        }
    }

    private fun jpegDimensions(source: Path): ImageDimensions? = Files.newInputStream(source).buffered().use { input ->
        if (input.read() != 0xFF || input.read() != 0xD8) return@use null
        while (true) {
            var markerStart = input.read()
            while (markerStart >= 0 && markerStart != 0xFF) markerStart = input.read()
            if (markerStart < 0) return@use null
            var marker = input.read()
            while (marker == 0xFF) marker = input.read()
            if (marker < 0 || marker == 0xD9 || marker == 0xDA) return@use null
            if (marker == 0x01 || marker in 0xD0..0xD7) continue
            val segmentLength = readUnsignedShort(input)
            if (segmentLength < 2) return@use null
            if (marker in JPEG_START_OF_FRAME_MARKERS) {
                if (segmentLength < 7) return@use null
                if (input.read() < 0) return@use null
                val height = readUnsignedShort(input)
                val width = readUnsignedShort(input)
                return@use dimensions(width, height)
            }
            if (!skipFully(input, segmentLength - 2)) return@use null
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    private fun webpDimensions(source: Path): ImageDimensions? {
        val (header, read) = readHeader(source)
        if (read < 30) return null
        val chunk = String(header, 12, 4, Charsets.US_ASCII)
        return when (chunk) {
            "VP8X" -> dimensions(
                1 + littleEndian24(header, 24),
                1 + littleEndian24(header, 27),
            )
            "VP8L" -> {
                if (header[20].toInt() and 0xFF != 0x2F) return null
                val bits = littleEndianInt(header, 21)
                dimensions((bits and 0x3FFF) + 1, ((bits ushr 14) and 0x3FFF) + 1)
            }
            "VP8 " -> {
                if (
                    header[23].toInt() and 0xFF != 0x9D ||
                    header[24].toInt() and 0xFF != 0x01 ||
                    header[25].toInt() and 0xFF != 0x2A
                ) return null
                dimensions(
                    littleEndianShort(header, 26) and 0x3FFF,
                    littleEndianShort(header, 28) and 0x3FFF,
                )
            }
            else -> null
        }
    }

    private fun readHeader(source: Path): Pair<ByteArray, Int> {
        val header = ByteArray(HEADER_BYTES)
        val read = Files.newInputStream(source).use { input ->
            var total = 0
            while (total < header.size) {
                val count = input.read(header, total, header.size - total)
                if (count < 0) break
                total += count
            }
            total
        }
        return header to read
    }

    private fun dimensions(width: Int, height: Int): ImageDimensions? =
        if (width > 0 && height > 0) ImageDimensions(width, height) else null

    private fun bigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun littleEndian24(bytes: ByteArray, offset: Int): Int =
        littleEndianShort(bytes, offset) or ((bytes[offset + 2].toInt() and 0xFF) shl 16)

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        littleEndian24(bytes, offset) or ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readUnsignedShort(input: java.io.InputStream): Int {
        val high = input.read()
        val low = input.read()
        return if (high < 0 || low < 0) -1 else (high shl 8) or low
    }

    private fun skipFully(input: java.io.InputStream, bytes: Int): Boolean {
        var remaining = bytes.toLong()
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (input.read() < 0) {
                return false
            } else {
                remaining--
            }
        }
        return true
    }

    companion object {
        /** Limite del file codificato; la dimensione in pixel viene verificata a parte. */
        const val MAX_IMAGE_BYTES: Long = 32L * 1024 * 1024

        const val MAX_IMAGE_PIXELS: Long = 16_000_000L

        const val MAX_IMAGE_SIDE: Int = 8_192

        /** Estensioni riconosciute; `decodeToImageBitmap` gestisce questi formati. */
        val SUPPORTED_FORMATS: Set<String> = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

        /** Testo pronto da mostrare all'utente: formati accettati e limite. */
        const val acceptedFormatsLabel: String = "PNG, JPG, WEBP, BMP, GIF"
        const val maxSizeLabel: String = "32 MB"

        const val maxPixelSizeLabel: String = "16 megapixel e 8192 pixel per lato"

        private const val HEADER_BYTES = 32

        // Firme dei formati supportati. -1 significa "byte qualsiasi".
        private val PNG = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val JPEG = intArrayOf(0xFF, 0xD8, 0xFF)
        private val GIF87 = intArrayOf(0x47, 0x49, 0x46, 0x38, 0x37, 0x61)
        private val GIF89 = intArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)
        private val BMP = intArrayOf(0x42, 0x4D)
        private val RIFF = intArrayOf(0x52, 0x49, 0x46, 0x46)

        private val JPEG_START_OF_FRAME_MARKERS = setOf(
            0xC0, 0xC1, 0xC2, 0xC3,
            0xC5, 0xC6, 0xC7,
            0xC9, 0xCA, 0xCB,
            0xCD, 0xCE, 0xCF,
        )

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
