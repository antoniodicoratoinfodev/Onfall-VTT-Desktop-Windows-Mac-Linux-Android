package app.d6d.ui.images

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Una mappa enorme non deve entrare in memoria per intero.
 *
 * Il limite d'importazione e' un gigabyte e non c'e' piu' un tetto sui pixel: e'
 * la decodifica, ora, l'unica cosa che sta fra una battlemap in alta risoluzione e
 * mezzo gigabyte di pixel allocati per disegnarla su uno schermo che non li ha.
 */
class ImageDecodingTest {

    @TempDir
    lateinit var directory: Path

    private fun image(name: String, width: Int, height: Int): Path =
        directory.resolve(name).also { file ->
            ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY), "png", file.toFile())
        }

    @Test
    fun `il passo di campionamento rispetta il budget`() {
        assertEquals(1, sampleStep(1_000, 1_000, 16_000_000L))
        // 12.000 × 12.000 sono 144 megapixel: serve dividere per quattro per lato.
        assertEquals(4, sampleStep(12_000, 12_000, 16_000_000L))
        assertEquals(2, sampleStep(6_000, 6_000, 16_000_000L))
    }

    @Test
    fun `il passo e' sempre una potenza di due`() {
        // E' l'unica cosa che `BitmapFactory` accetta davvero, e tenere la stessa
        // regola sulle due piattaforme fa leggere la stessa mappa alla stessa scala.
        (1..40).forEach { side ->
            val step = sampleStep(side * 1_000, side * 1_000, 4_000_000L)
            assertTrue(step > 0 && step and (step - 1) == 0, "passo non potenza di due: $step")
        }
    }

    @Test
    fun `un'immagine oltre il budget viene letta ridotta`() {
        // 5.000 × 4.000 sono venti megapixel: oltre il budget, quindi dimezzata.
        val decoded = decodeSampledImage(image("grande.png", 5_000, 4_000), 16_000_000L)

        assertNotNull(decoded)
        assertEquals(2_500, decoded!!.width)
        assertEquals(2_000, decoded.height)
    }

    @Test
    fun `un'immagine dentro il budget si legge intera`() {
        val decoded = decodeSampledImage(image("piccola.png", 800, 600), 16_000_000L)

        assertNotNull(decoded)
        assertEquals(800, decoded!!.width)
        assertEquals(600, decoded.height)
    }

    @Test
    fun `un file che non e' un'immagine non fa esplodere la lettura`() {
        val broken = directory.resolve("bugiardo.png")
        Files.writeString(broken, "non sono un PNG")

        assertNull(runCatching { decodeSampledImage(broken, 16_000_000L) }.getOrNull())
    }
}
