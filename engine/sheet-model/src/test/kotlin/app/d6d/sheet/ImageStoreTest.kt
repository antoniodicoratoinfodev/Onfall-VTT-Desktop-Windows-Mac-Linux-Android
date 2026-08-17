package app.d6d.sheet

import app.d6d.i18n.AppLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Archivio locale delle immagini: copia, nomi univoci e limiti di accesso. */
class ImageStoreTest {

    @TempDir
    lateinit var dataDirectory: Path

    private fun store() = ImageStore(dataDirectory)

    /** Un PNG minimo con intestazione IHDR e dimensioni 1×1. */
    private fun sampleImage(name: String): Path {
        val source = Files.createTempDirectory("sorgente").resolve(name)
        Files.write(source, pngHeader(1, 1))
        return source
    }

    private fun pngHeader(width: Int, height: Int): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        (width ushr 24).toByte(), (width ushr 16).toByte(), (width ushr 8).toByte(), width.toByte(),
        (height ushr 24).toByte(), (height ushr 16).toByte(), (height ushr 8).toByte(), height.toByte(),
        0x08, 0x06, 0x00, 0x00, 0x00,
    )

    @Test
    fun `l'immagine viene copiata nell'archivio`() {
        val source = sampleImage("ritratto.png")

        val stored = store().importImage(source)

        assertEquals("ritratto.png", stored)
        assertTrue(Files.exists(dataDirectory.resolve("images").resolve(stored)))
        // L'originale resta dov'era: si copia, non si sposta.
        assertTrue(Files.exists(source))
    }

    @Test
    fun `cancellare l'originale non rompe l'archivio`() {
        val source = sampleImage("ritratto.png")
        val stored = store().importImage(source)

        Files.delete(source)

        // E' il motivo per cui si copia invece di referenziare il percorso.
        assertNotEquals(null, store().readBytes(stored))
    }

    @Test
    fun `due file omonimi non si sovrascrivono`() {
        val first = store().importImage(sampleImage("eroe.png"))
        val second = store().importImage(sampleImage("eroe.png"))

        assertEquals("eroe.png", first)
        assertNotEquals(first, second)
        assertTrue(Files.exists(dataDirectory.resolve("images").resolve(second)))
    }

    @Test
    fun `un formato non supportato viene rifiutato`() {
        val source = sampleImage("documento.pdf")

        assertThrows(IllegalArgumentException::class.java) { store().importImage(source) }
    }

    @Test
    fun `un file oltre il limite di dimensione viene rifiutato`() {
        // File sparso: la lunghezza logica supera il limite senza scrivere davvero
        // 200 MB su disco, cosi' il test resta istantaneo.
        val big = Files.createTempDirectory("grande").resolve("enorme.png")
        java.io.RandomAccessFile(big.toFile(), "rw").use { it.setLength(ImageStore.MAX_IMAGE_BYTES + 1) }

        val error = assertThrows(IllegalArgumentException::class.java) { store().importImage(big) }
        assertTrue(error.message!!.contains(ImageStore.maxSizeLabel))
    }

    @Test
    fun `un'estensione immagine ma contenuto non-immagine viene rifiutato`() {
        // La difesa vera: un file rinominato .png ma pieno d'altro non deve entrare
        // nell'archivio solo perche' l'estensione sembra giusta.
        val travestito = Files.createTempDirectory("finto").resolve("malevolo.png")
        Files.write(travestito, "MZ questo non e' un'immagine".toByteArray())

        assertThrows(IllegalArgumentException::class.java) { store().importImage(travestito) }
    }

    @Test
    fun `un jpeg reale viene accettato`() {
        val jpeg = Files.createTempDirectory("foto").resolve("scatto.jpg")
        // SOI e segmento SOF0 da 1×1 pixel.
        Files.write(
            jpeg,
            byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(),
                0xFF.toByte(), 0xC0.toByte(), 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01,
                0x01, 0x01, 0x11, 0x00,
            ),
        )

        val stored = store().importImage(jpeg)

        assertEquals("scatto.jpg", stored)
    }

    @Test
    fun `un'immagine con troppi pixel viene rifiutata prima della copia`() {
        val huge = Files.createTempDirectory("pixel").resolve("enorme.png")
        Files.write(huge, pngHeader(8_000, 8_000))

        val error = assertThrows(IllegalArgumentException::class.java) { store().importImage(huge) }

        assertTrue(error.message!!.contains(ImageStore.maxPixelSizeLabel))
    }

    @Test
    fun `un file inesistente viene rifiutato`() {
        assertThrows(IllegalArgumentException::class.java) {
            store().importImage(dataDirectory.resolve("mai-esistito.png"))
        }
    }

    @Test
    fun `gli errori di importazione seguono la lingua richiesta`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            store().importImage(
                dataDirectory.resolve("missing.png"),
                AppLanguage.ENGLISH,
            )
        }

        assertTrue(error.message!!.startsWith("The file does not exist:"))
    }

    @Test
    fun `un nome con percorso non puo' uscire dall'archivio`() {
        // Senza questa guardia un nome salvato in un file di dati potrebbe far
        // leggere qualunque file del disco.
        assertNull(store().resolve("../../../etc/passwd"))
        assertNull(store().resolve("sottocartella/immagine.png"))
        assertNull(store().resolve("..\\windows\\system32"))
        assertNull(store().resolve(""))
    }

    @Test
    fun `un nome che non esiste restituisce null invece di fallire`() {
        assertNull(store().resolve("assente.png"))
        assertNull(store().readBytes("assente.png"))
    }

    @Test
    fun `i caratteri strani nel nome vengono normalizzati`() {
        val stored = store().importImage(sampleImage("ritratto strano&%.png"))

        assertFalse(stored.contains(' '))
        assertFalse(stored.contains('&'))
        assertTrue(stored.endsWith(".png"))
    }

    @Test
    fun `la libreria dei ritratti si salva e si rilegge`() {
        val store = store()
        store.saveLibrary(PortraitLibrary(portraits = mapOf("pg-kaelen" to "kaelen.png")))

        val reloaded = ImageStore(dataDirectory).loadLibrary()

        assertEquals("kaelen.png", reloaded.portraits["pg-kaelen"])
    }

    @Test
    fun `una libreria ritratti corrotta ricade sul backup valido`() {
        val store = store()
        store.saveLibrary(PortraitLibrary(portraits = mapOf("prima" to "prima.png")))
        store.saveLibrary(PortraitLibrary(portraits = mapOf("dopo" to "dopo.png")))
        Files.writeString(dataDirectory.resolve("ritratti.json"), "{ non valido")

        val recovered = ImageStore(dataDirectory).loadLibrary()

        assertEquals(mapOf("prima" to "prima.png"), recovered.portraits)
    }

    @Test
    fun `una libreria ritratti mancante ricade sul backup valido`() {
        val store = store()
        store.saveLibrary(PortraitLibrary(portraits = mapOf("prima" to "prima.png")))
        store.saveLibrary(PortraitLibrary(portraits = mapOf("dopo" to "dopo.png")))
        Files.delete(dataDirectory.resolve("ritratti.json"))

        val recovered = ImageStore(dataDirectory).loadLibrary()

        assertEquals(mapOf("prima" to "prima.png"), recovered.portraits)
    }

    @Test
    fun `una libreria assente non fa fallire il caricamento`() {
        assertTrue(store().loadLibrary().portraits.isEmpty())
    }

    @Test
    fun `eliminare un'immagine non lascia il file`() {
        val store = store()
        val stored = store.importImage(sampleImage("temporanea.png"))

        store.deleteImage(stored)

        assertNull(store.resolve(stored))
        // Eliminare due volte non deve sollevare eccezioni.
        store.deleteImage(stored)
    }

    @Test
    fun `il formato si riconosce senza distinzione di maiuscole`() {
        val store = store()

        assertTrue(store.isSupported(Path.of("A.PNG")))
        assertTrue(store.isSupported(Path.of("b.JpEg")))
        assertFalse(store.isSupported(Path.of("c.exe")))
        assertFalse(store.isSupported(Path.of("senza-estensione")))
    }
}
