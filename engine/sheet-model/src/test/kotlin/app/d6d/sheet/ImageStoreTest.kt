package app.d6d.sheet

import app.d6d.i18n.AppLanguage
import org.junit.jupiter.api.Assertions.assertArrayEquals
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

    /** Un PNG vero, scritto da ImageIO: si decodifica davvero, non solo si annusa. */
    private fun realImage(name: String, width: Int, height: Int): Path {
        val source = Files.createTempDirectory("vera").resolve(name)
        javax.imageio.ImageIO.write(
            java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_BYTE_GRAY),
            "png",
            source.toFile(),
        )
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
    fun `una mappa in alta risoluzione viene accettata`() {
        // C'era un tetto in pixel, e rifiutava proprio le battlemap che chi gioca
        // vuole caricare: in alta risoluzione superano i sedici megapixel di regola.
        // Ora l'unico limite e' il peso del file.
        //
        // L'immagine e' vera e decodificabile, non un'intestazione di ventinove byte:
        // un file finto proverebbe solo che i controlli leggono le prime righe, non
        // che una mappa autentica entra.
        val huge = realImage("enorme.png", 5_000, 4_000)

        assertEquals("enorme.png", store().importImage(huge))
    }

    @Test
    fun `un png dichiarato ma corrotto viene rifiutato`() {
        // Firma giusta, contenuto rotto: il file supera il controllo d'estensione e
        // pure quello di firma, e cade sulle dimensioni — che e' l'unica cosa che
        // possa accorgersi di un'immagine troncata a meta' scaricamento.
        val corrupt = Files.createTempDirectory("corrotto").resolve("mezza.png")
        Files.write(corrupt, Files.readAllBytes(realImage("intera.png", 64, 64)).copyOfRange(0, 16))

        assertThrows(IllegalArgumentException::class.java) { store().importImage(corrupt) }
    }

    @Test
    fun `un file rinominato png non viene adottato come mappa`() {
        // Chi copia i file nella cartella a mano non passa dai controlli di
        // importazione: l'elenco guarda dentro i file, non solo il nome.
        val store = store()
        Files.createDirectories(store.mapsDirectory)
        Files.writeString(store.mapsDirectory.resolve("bugiardo.png"), "non sono un PNG")
        Files.copy(realImage("vera.png", 32, 32), store.mapsDirectory.resolve("vera.png"))

        assertEquals(listOf("vera.png"), store.mapImageNames())
    }

    @Test
    fun `un'immagine troncata viene rifiutata`() {
        // Le dimensioni si leggono ancora, ma per capire se il file e' intero: da
        // un'intestazione mozzata non si ricavano, ed e' il sintomo di un file
        // danneggiato che non si disegnerebbe comunque.
        val truncated = Files.createTempDirectory("mozza").resolve("mezza.png")
        Files.write(truncated, pngHeader(4, 4).copyOfRange(0, 20))

        assertThrows(IllegalArgumentException::class.java) { store().importImage(truncated) }
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
    fun `una mappa va nella cartella delle mappe, non fra i ritratti`() {
        val store = store()

        val stored = store.importMapImage(sampleImage("cripta.png"))

        assertTrue(Files.isRegularFile(store.mapsDirectory.resolve(stored)))
        assertFalse(Files.exists(store.imagesDirectory.resolve(stored)))
    }

    @Test
    fun `una mappa salvata prima che le mappe avessero una cartella si ritrova ancora`() {
        // Gli archivi gia' su disco nominano le proprie mappe dentro `images/`:
        // cercare in una cartella sola le avrebbe fatte sparire tutte insieme.
        val store = store()
        val stored = store.importImage(sampleImage("vecchia-mappa.png"))

        assertEquals(store.imagesDirectory.resolve(stored), store.resolve(stored))
    }

    @Test
    fun `un ritratto e una mappa non possono chiamarsi allo stesso modo`() {
        // I nomi si risolvono senza sapere di che cartella siano, quindi due file
        // omonimi non sarebbero due immagini ma una che ne nasconde un'altra.
        val store = store()
        val portrait = store.importImage(sampleImage("torre.png"))

        val map = store.importMapImage(sampleImage("torre.png"))

        assertNotEquals(portrait, map)
    }

    @Test
    fun `una mappa inclusa si scrive senza passare dai controlli del selettore`() {
        val store = store()

        val written = store.writeMapImage("inclusa.png", pngHeader(1, 1))

        assertEquals("inclusa.png", written)
        assertEquals(listOf("inclusa.png"), store.mapImageNames())
    }

    @Test
    fun `una mappa inclusa non puo usare un percorso come nome`() {
        val outside = dataDirectory.resolve("fuga.png")
        Files.write(outside, pngHeader(1, 1))

        assertThrows(IllegalArgumentException::class.java) {
            store().writeMapImage("../fuga.png", pngHeader(1, 1))
        }

        assertArrayEquals(pngHeader(1, 1), Files.readAllBytes(outside))
    }

    @Test
    fun `una mappa inclusa non copre un ritratto che si chiama allo stesso modo`() {
        // `resolve` cerca prima fra le mappe: scritta col nome di un ritratto gia'
        // esistente, la mappa glielo nasconderebbe, e al posto della faccia di un
        // personaggio comparirebbe una battlemap.
        val store = store()
        val portrait = store.importImage(sampleImage("anubis_tomb.png"))

        val written = store.writeMapImage("anubis_tomb.png", pngHeader(2, 2))

        assertNotEquals(portrait, written)
        assertEquals(store.imagesDirectory.resolve(portrait), store.resolve(portrait))
    }

    @Test
    fun `scrivere una mappa inclusa non sovrascrive quella gia' su disco`() {
        // Chi ha ritoccato una mappa inclusa se la tiene: il riavvio non e' il
        // momento di rimetterci sopra la nostra versione.
        val store = store()
        val mine = pngHeader(2, 2)
        store.writeMapImage("inclusa.png", mine)

        store.writeMapImage("inclusa.png", pngHeader(1, 1))

        assertArrayEquals(mine, Files.readAllBytes(store.mapsDirectory.resolve("inclusa.png")))
    }

    @Test
    fun `l'elenco delle mappe su disco ignora cio' che non e' un'immagine`() {
        val store = store()
        store.writeMapImage("cripta.png", pngHeader(1, 1))
        Files.writeString(store.mapsDirectory.resolve("appunti.txt"), "non sono una mappa")

        assertEquals(listOf("cripta.png"), store.mapImageNames())
    }

    @Test
    fun `senza cartella delle mappe l'elenco e' vuoto invece di fallire`() {
        assertEquals(emptyList<String>(), store().mapImageNames())
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
