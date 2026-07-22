package app.d6d.ui.images

import app.d6d.sheet.ImageStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class PortraitRepositoryTest {

    @TempDir
    lateinit var directory: Path

    private fun sampleImage(): Path = directory.resolve("sorgente.png").also { source ->
        Files.write(source, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
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
}
