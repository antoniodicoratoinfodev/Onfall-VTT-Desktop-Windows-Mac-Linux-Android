package app.d6d.ui.maps

import app.d6d.i18n.AppLanguage
import app.d6d.ui.content.SessionTemplates
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le mappe incluse esistono davvero, e nessuna partita ne riceve una da sola.
 *
 * Il primo controllo e' l'unico che possa accorgersi di un file rinominato o non
 * impacchettato: il resto del meccanismo lavora su nomi, e un nome sbagliato lo si
 * scopre solo provando a leggere i byte. Il secondo fissa la regola opposta e
 * altrettanto importante — le mappe ci sono, ma lo sfondo lo sceglie chi gioca.
 */
class BundledMapsTest {

    @Test
    fun `ogni mappa inclusa si legge dal pacchetto`() = runTest {
        assertTrue(BundledMaps.all.isNotEmpty(), "senza mappe incluse la prova sarebbe vuota")
        BundledMaps.all.forEach { map ->
            val bytes = BundledMaps.bytesOf(map)
            assertTrue(bytes.size > 1024, "${map.fileName} e' troppo piccola per essere una mappa")
            // Firma JPEG: se la risorsa non c'e', `readBytes` fallisce prima; se c'e'
            // ma e' il file sbagliato, se ne accorge qui.
            assertEquals(0xFF.toByte(), bytes[0], "${map.fileName} non e' un JPEG")
            assertEquals(0xD8.toByte(), bytes[1], "${map.fileName} non e' un JPEG")
        }
    }

    @Test
    fun `identificativi e file delle mappe incluse sono distinti`() {
        assertEquals(BundledMaps.all.size, BundledMaps.all.map { it.id }.distinct().size)
        assertEquals(BundledMaps.all.size, BundledMaps.all.map { it.fileName }.distinct().size)
        // L'identificativo dice da dove viene: e' cio' che rende riconoscibile una
        // mappa inclusa anche dopo che l'utente l'ha rinominata.
        assertTrue(BundledMaps.all.all { it.id.startsWith("map-bundled-") })
    }

    @Test
    fun `una partita comincia senza sfondo`() {
        // Avere delle mappe pronte non vuol dire sceglierne una: il tavolo parte
        // vuoto e lo sfondo si prende da «Scegli sfondo».
        listOf(AppLanguage.ITALIAN, AppLanguage.ENGLISH).forEach { language ->
            SessionTemplates.of(language).all.forEach { template ->
                assertEquals(
                    "",
                    template.startedSession().currentState().battleMap().backgroundImage(),
                    "«${template.name}» comincia con uno sfondo gia' messo",
                )
            }
        }
    }
}
