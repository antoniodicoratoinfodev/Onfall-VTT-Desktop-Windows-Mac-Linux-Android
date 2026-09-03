package app.d6d.ui.images

import app.d6d.sheet.ImageStore
import app.d6d.sheet.PortraitFraming
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO

class PortraitRepositoryTest {

    @TempDir
    lateinit var directory: Path

    private fun sampleImage(name: String = "sorgente.png"): Path = directory.resolve(name).also { source ->
        // PNG completo 1×1: oltre ai controlli dell'archivio può essere decodificato
        // davvero dai test della cache Compose.
        ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", source.toFile())
    }

    @Test
    fun `il cambio lingua scarta un messaggio gia risolto`() {
        val repository = PortraitRepository(ImageStore(directory.resolve("dati")))
        repository.message = "Ritratto assegnato."

        repository.onLanguageChanged()

        assertNull(repository.message)
    }

    @Test
    fun `il picker asincrono assegna il ritratto e invoca il completamento`() {
        val source = sampleImage()
        val picker = object : FilePicker {
            override fun pick(): Path? = null

            override fun pickAsync(onPicked: (Path?) -> Unit, onError: (Throwable) -> Unit) {
                onPicked(source)
            }
        }
        val repository = PortraitRepository(ImageStore(directory.resolve("dati")), picker)
        var completed = false

        repository.assignPortraitAsync("eroe") { completed = it }

        assertTrue(completed)
        assertNotNull(repository.library.portraits["eroe"])
        assertEquals("Ritratto assegnato.", repository.message)
    }

    @Test
    fun `annullare il picker produce un feedback senza modificare la libreria`() {
        val repository = PortraitRepository(
            ImageStore(directory.resolve("dati")),
            object : FilePicker {
                override fun pick(): Path? = null
                override fun pickAsync(onPicked: (Path?) -> Unit, onError: (Throwable) -> Unit) {
                    onPicked(null)
                }
            },
        )
        var completed = true

        repository.assignPortraitAsync("eroe") { completed = it }

        assertFalse(completed)
        assertTrue(repository.library.portraits.isEmpty())
        assertEquals("Selezione immagine annullata.", repository.message)
    }

    @Test
    fun `un errore del provider diventa un messaggio leggibile`() {
        val repository = PortraitRepository(
            ImageStore(directory.resolve("dati")),
            object : FilePicker {
                override fun pick(): Path? = null
                override fun pickAsync(onPicked: (Path?) -> Unit, onError: (Throwable) -> Unit) {
                    onError(IOException("provider non disponibile"))
                }
            },
        )

        repository.pickBackgroundAsync { assertEquals(null, it) }

        assertTrue(repository.message.orEmpty().contains("provider non disponibile"))
    }

    @Test
    fun `il default asincrono mantiene compatibile il picker desktop`() {
        val source = sampleImage()
        var result: Path? = null

        FilePicker { source }.pickAsync(onPicked = { result = it })

        assertEquals(source, result)
    }

    @Test
    fun `caricare una mappa la aggiunge all'archivio col nome del file`() {
        val source = sampleImage()
        val store = ImageStore(directory.resolve("dati"))
        val repository = PortraitRepository(store, pickerReturning(source))
        var created: app.d6d.sheet.StoredMap? = null

        repository.importMapAsync { created = it }

        assertNotNull(created)
        assertEquals(1, repository.maps.size)
        assertEquals("sorgente", repository.maps.single().name)
        assertNotNull(store.resolve(repository.maps.single().image))
        // L'archivio è persistito: una nuova istanza lo ritrova su disco.
        assertEquals(1, PortraitRepository(store).maps.size)
    }

    @Test
    fun `rinominare una mappa ne cambia il nome ma non il file`() {
        val store = ImageStore(directory.resolve("dati"))
        val repository = PortraitRepository(store, pickerReturning(sampleImage()))
        repository.importMapAsync()
        val map = repository.maps.single()

        repository.renameMap(map.id, "Cripta dei predoni")

        assertEquals("Cripta dei predoni", repository.maps.single().name)
        assertEquals(map.image, repository.maps.single().image)
    }

    @Test
    fun `eliminare una mappa la toglie dall'archivio e cancella il file`() {
        val store = ImageStore(directory.resolve("dati"))
        val repository = PortraitRepository(store, pickerReturning(sampleImage()))
        repository.importMapAsync()
        val map = repository.maps.single()

        repository.deleteMap(map.id)

        assertTrue(repository.maps.isEmpty())
        assertEquals(null, store.resolve(map.image))
    }

    @Test
    fun `sostituire e poi togliere un ritratto elimina i file non piu referenziati`() {
        var source = sampleImage("primo.png")
        val picker = object : FilePicker {
            override fun pick(): Path? = source
            override fun pickAsync(onPicked: (Path?) -> Unit, onError: (Throwable) -> Unit) {
                onPicked(source)
            }
        }
        val store = ImageStore(directory.resolve("dati"))
        val repository = PortraitRepository(store, picker)
        repository.assignPortraitAsync("eroe")
        val firstStored = requireNotNull(repository.portraitName("eroe"))
        assertNotNull(store.resolve(firstStored))

        source = sampleImage("secondo.png")
        repository.assignPortraitAsync("eroe")
        val secondStored = requireNotNull(repository.portraitName("eroe"))

        assertEquals(null, store.resolve(firstStored))
        assertNotNull(store.resolve(secondStored))
        repository.clearPortrait("eroe")
        assertEquals(null, store.resolve(secondStored))
    }

    @Test
    fun `l'inquadratura viene salvata e una nuova immagine la azzera`() {
        var source = sampleImage("primo.png")
        val store = ImageStore(directory.resolve("dati"))
        val repository = PortraitRepository(store, pickerReturning(source))
        repository.assignPortraitAsync("eroe")
        val framing = PortraitFraming(0.15f, 0.8f, 2.25f)

        repository.setPortraitFraming("eroe", framing)

        assertEquals(framing, repository.portraitFraming("eroe"))
        assertEquals(framing, store.loadLibrary().framings["eroe"])

        source = sampleImage("secondo.png")
        val replacement = PortraitRepository(store, pickerReturning(source))
        replacement.assignPortraitAsync("eroe")

        assertEquals(PortraitFraming.DEFAULT, replacement.portraitFraming("eroe"))
        assertFalse(store.loadLibrary().framings.containsKey("eroe"))
    }

    @Test
    fun `togliere il ritratto elimina anche la sua inquadratura`() {
        val store = ImageStore(directory.resolve("dati"))
        val repository = PortraitRepository(store, pickerReturning(sampleImage()))
        repository.assignPortraitAsync("eroe")
        repository.setPortraitFraming("eroe", PortraitFraming(0f, 1f, 3f))

        repository.clearPortrait("eroe")

        assertFalse(repository.library.framings.containsKey("eroe"))
        assertFalse(store.loadLibrary().framings.containsKey("eroe"))
    }

    @Test
    fun `il file temporaneo del picker viene rilasciato dopo l'importazione`() {
        val source = sampleImage()
        var released: Path? = null
        val repository = PortraitRepository(
            ImageStore(directory.resolve("dati")),
            object : FilePicker {
                override fun pick(): Path? = source
                override fun pickAsync(onPicked: (Path?) -> Unit, onError: (Throwable) -> Unit) {
                    onPicked(source)
                }

                override fun release(path: Path) {
                    released = path
                }
            },
        )

        repository.assignPortraitAsync("eroe")

        assertEquals(source, released)
        assertNotNull(repository.portraitName("eroe"))
    }

    @Test
    fun `la cache decodificata rimuove la voce meno recente oltre il limite`() {
        val store = ImageStore(directory.resolve("dati"))
        val first = store.importImage(sampleImage("uno.png"))
        val second = store.importImage(sampleImage("due.png"))
        val repository = PortraitRepository(store, maxDecodedEntries = 1)

        repository.bitmap(first)
        repository.bitmap(second)

        assertEquals(1, repository.decodedCacheSize)
        assertTrue(repository.decodedCacheBytes <= 4L)
    }

    private fun pickerReturning(source: Path) = object : FilePicker {
        override fun pick(): Path? = source
        override fun pickAsync(onPicked: (Path?) -> Unit, onError: (Throwable) -> Unit) {
            onPicked(source)
        }
    }

}
