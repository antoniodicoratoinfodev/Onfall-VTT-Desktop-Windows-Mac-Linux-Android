package app.d6d.ui.images

import app.d6d.sheet.ImageStore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * L'archivio delle mappe e la cartella su disco devono raccontare la stessa cosa.
 *
 * Sono due sorgenti — un indice in JSON e una cartella di file — e la sola ragione
 * per cui e' accettabile averne due e' che una le riconcilia. Cio' che si prova qui
 * e' proprio quella riconciliazione: le mappe incluse arrivano da sole, quelle
 * copiate a mano vengono adottate, e nessuna delle due torna dopo che l'utente
 * l'ha eliminata.
 */
class MapArchiveSyncTest {

    @TempDir
    lateinit var directory: Path

    private fun pngBytes(): ByteArray = ByteArrayOutputStream().also { output ->
        ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", output)
    }.toByteArray()

    private fun seed(id: String, name: String, fileName: String) =
        MapSeed(id, name, fileName) { pngBytes() }

    private fun repositoryOn(data: Path) = PortraitRepository(ImageStore(data))

    /** Un file copiato nella cartella delle mappe senza passare dall'applicazione. */
    private fun dropInMapsFolder(data: Path, fileName: String) {
        val maps = data.resolve("mappe")
        Files.createDirectories(maps)
        Files.write(maps.resolve(fileName), pngBytes())
    }

    @Test
    fun `le mappe incluse popolano un archivio vuoto`() = runTest {
        val data = directory.resolve("dati")
        val repository = repositoryOn(data)

        repository.syncMaps(listOf(seed("map-bundled-cripta", "Cripta", "cripta.png")))

        assertEquals(listOf("Cripta"), repository.maps.map { it.name })
        assertTrue(Files.isRegularFile(data.resolve("mappe/cripta.png")))
        // E si ritrova dal solo nome interno, che e' come lo sfondo lo cerca: senza,
        // l'archivio elencherebbe una mappa che la partita non sa poi caricare.
        assertNotNull(ImageStore(data).resolve("cripta.png"))
    }

    @Test
    fun `installarle due volte non le duplica`() = runTest {
        val repository = repositoryOn(directory.resolve("dati"))
        val seeds = listOf(seed("map-bundled-cripta", "Cripta", "cripta.png"))

        repository.syncMaps(seeds)
        repository.syncMaps(seeds)

        assertEquals(1, repository.maps.size)
    }

    @Test
    fun `una mappa inclusa eliminata non torna al riavvio`() = runTest {
        val data = directory.resolve("dati")
        val seeds = listOf(seed("map-bundled-cripta", "Cripta", "cripta.png"))
        val first = repositoryOn(data)
        first.syncMaps(seeds)
        first.deleteMap("map-bundled-cripta")
        assertTrue(first.maps.isEmpty())

        // Riavvio: nuovo repository sulla stessa cartella dati.
        val reopened = repositoryOn(data)
        reopened.syncMaps(seeds)

        assertTrue(
            reopened.maps.isEmpty(),
            "la mappa eliminata e' tornata: ${reopened.maps.map { it.name }}",
        )
    }

    @Test
    fun `un file copiato a mano nella cartella entra nell'archivio`() = runTest {
        val data = directory.resolve("dati")
        dropInMapsFolder(data, "Cripta di Ghiaccio.png")
        val repository = repositoryOn(data)

        repository.syncMaps()

        // Il nome iniziale e' quello del file senza estensione: e' l'unica cosa che
        // chi ha copiato il file ci ha detto di volerla chiamare.
        assertEquals(listOf("Cripta di Ghiaccio"), repository.maps.map { it.name })
    }

    @Test
    fun `adottare non ripete cio' che l'archivio conosce gia'`() = runTest {
        val data = directory.resolve("dati")
        val repository = repositoryOn(data)
        repository.syncMaps(listOf(seed("map-bundled-cripta", "Cripta", "cripta.png")))
        repository.renameMap("map-bundled-cripta", "La mia cripta")

        repository.syncMaps()

        assertEquals(listOf("La mia cripta"), repository.maps.map { it.name })
    }

    @Test
    fun `una mappa gia' nell'archivio con lo stesso nome non viene aggiunta due volte`() = runTest {
        // Chi aveva caricato a mano le stesse mappe prima che fossero incluse ne ha
        // una copia con un altro identificativo: due righe identiche nell'archivio
        // sono peggio di una mappa in meno.
        val data = directory.resolve("dati")
        dropInMapsFolder(data, "Cripta.png")
        val repository = repositoryOn(data)
        repository.syncMaps()
        assertEquals(listOf("Cripta"), repository.maps.map { it.name })

        repository.syncMaps(listOf(seed("map-bundled-cripta", "Cripta", "cripta-inclusa.png")))

        assertEquals(listOf("Cripta"), repository.maps.map { it.name })
        // E non ci riprova al riavvio: la saltata resta fra le installate.
        val reopened = repositoryOn(data)
        reopened.syncMaps(listOf(seed("map-bundled-cripta", "Cripta", "cripta-inclusa.png")))
        assertEquals(listOf("Cripta"), reopened.maps.map { it.name })
    }

    @Test
    fun `un'installazione fallita viene ritentata al riavvio`() = runTest {
        // Segnare come installata una mappa che non e' stata scritta la condanna a
        // non arrivare mai: un disco pieno per un minuto diventerebbe permanente.
        val data = directory.resolve("dati")
        val illeggibile = MapSeed("map-bundled-cripta", "Cripta", "cripta.png") {
            throw java.io.IOException("risorsa non leggibile")
        }
        val first = repositoryOn(data)
        first.syncMaps(listOf(illeggibile))
        assertTrue(first.maps.isEmpty())

        val reopened = repositoryOn(data)
        reopened.syncMaps(listOf(seed("map-bundled-cripta", "Cripta", "cripta.png")))

        assertEquals(listOf("Cripta"), reopened.maps.map { it.name })
    }

    @Test
    fun `una mappa inclusa non copre un ritratto omonimo`() = runTest {
        val data = directory.resolve("dati")
        val store = ImageStore(data)
        val portrait = store.importImage(
            Files.createTempDirectory("ritratto").resolve("cripta.png").also {
                Files.write(it, pngBytes())
            },
        )
        val repository = repositoryOn(data)

        repository.syncMaps(listOf(seed("map-bundled-cripta", "Cripta", "cripta.png")))

        // La mappa e' entrata, ma con un altro nome: il ritratto si risolve ancora
        // sul proprio file, dentro `images/`.
        assertEquals(1, repository.maps.size)
        assertNotEquals("cripta.png", repository.maps.single().image)
        assertEquals(data.resolve("images").resolve(portrait), store.resolve(portrait))
    }

    @Test
    fun `senza un indice letto non si riscrive nulla`() = runTest {
        // `syncMaps` riscrive `mappe.json` da cio' che ha in memoria: su un indice
        // mai letto scriverebbe un archivio vuoto sopra le mappe di chi gioca.
        val data = directory.resolve("dati")
        repositoryOn(data).syncMaps(listOf(seed("map-bundled-cripta", "Cripta", "cripta.png")))
        val salvato = Files.readString(data.resolve("mappe.json"))

        val maiLetta = PortraitRepository(ImageStore(data), loadOnCreate = false)
        maiLetta.syncMaps(listOf(seed("map-bundled-altra", "Altra", "altra.png")))

        assertEquals(salvato, Files.readString(data.resolve("mappe.json")))
        assertTrue(maiLetta.maps.isEmpty())
    }

    @Test
    fun `i file che non sono immagini restano fuori`() = runTest {
        val data = directory.resolve("dati")
        val maps = data.resolve("mappe")
        Files.createDirectories(maps)
        Files.writeString(maps.resolve("appunti.txt"), "non sono una mappa")
        val repository = repositoryOn(data)

        repository.syncMaps()

        assertTrue(repository.maps.isEmpty())
    }

    @Test
    fun `le mappe non finiscono fra i ritratti`() = runTest {
        val data = directory.resolve("dati")
        val repository = repositoryOn(data)

        repository.syncMaps(listOf(seed("map-bundled-cripta", "Cripta", "cripta.png")))

        assertTrue(Files.isRegularFile(data.resolve("mappe/cripta.png")))
        assertFalse(Files.exists(data.resolve("images/cripta.png")))
        // Ed e' la cartella che l'archivio mostra a chi vuole copiarcene altre.
        assertEquals(data.resolve("mappe"), repository.mapsDirectory)
    }
}
