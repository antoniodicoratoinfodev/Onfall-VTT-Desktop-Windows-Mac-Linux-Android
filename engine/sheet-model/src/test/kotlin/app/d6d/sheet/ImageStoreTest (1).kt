package app.d6d.sheet

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

    /** Un PNG minimo valido: bastano i byte, non serve una vera immagine. */
    private fun sampleImage(name: String): Path {
        val source = Files.createTempDirectory("sorgente").resolve(name)
        Files.write(source, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        return source
    }

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
        // Firma JPEG (SOI + marker) seguita da qualche byte qualsiasi.
        Files.write(jpeg, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10))

        val stored = store().importImage(jpeg)

        assertEquals("scatto.jpg", stored)
    }

    @Test
    fun `un file inesistente viene rifiutato`() {
        assertThrows(IllegalArgumentException::class.java) {
            store().importImage(dataDirectory.resolve("mai-esistito.png"))
        }
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
