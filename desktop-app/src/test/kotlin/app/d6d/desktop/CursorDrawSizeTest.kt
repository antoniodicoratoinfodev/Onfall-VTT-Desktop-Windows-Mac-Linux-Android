package app.d6d.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.jetbrains.skiko.GraphicsApi

/**
 * Un cursore non si ingrandisce mai oltre il proprio disegno.
 *
 * E' il difetto che rendeva strani i cursori su Linux, e nessuna delle coppie ne era
 * esente perche' non dipendeva dai disegni: `getBestCursorSize` dice quanto grande
 * *puo'* essere un cursore, il server X risponde con misure molto oltre i nostri
 * sessantaquattro pixel, e ci si disegnava dentro fino a riempirla. Su macOS e
 * Windows i due numeri coincidevano e la differenza non si vedeva.
 */
class CursorDrawSizeTest {

    @Test
    fun `su una tela piu' grande del disegno non si ingrandisce`() {
        // Il caso Linux: tela 256, disegno 64. Restano 64.
        assertEquals(64 to 64, cursorDrawSize(256, 256, 64, 64, 1f))
    }

    @Test
    fun `su una tela piu' piccola del disegno si riduce fino a starci`() {
        // Il caso Windows: tela 32, disegno 64.
        assertEquals(32 to 32, cursorDrawSize(32, 32, 64, 64, 1f))
    }

    @Test
    fun `a misura uguale il disegno resta intatto`() {
        // Il caso macOS, quello che gia' funzionava: non deve cambiare.
        assertEquals(64 to 64, cursorDrawSize(64, 64, 64, 64, 1f))
    }

    @Test
    fun `la dimensione scelta dall'utente riduce, mai ingrandisce`() {
        assertEquals(42 to 42, cursorDrawSize(256, 256, 64, 64, 0.65f))
        assertEquals(52 to 52, cursorDrawSize(64, 64, 64, 64, 0.82f))
        // Oltre l'intero non si va, qualunque cosa chieda chi chiama.
        assertEquals(64 to 64, cursorDrawSize(256, 256, 64, 64, 4f))
    }

    @Test
    fun `il disegno non diventa ovale`() {
        val (width, height) = cursorDrawSize(200, 100, 64, 32, 1f)
        assertEquals(2f, width.toFloat() / height, 0.05f)
    }

    @Test
    fun `il risultato sta sempre nella tela e non e' mai vuoto`() {
        listOf(1, 8, 24, 32, 48, 64, 128, 256, 512).forEach { canvas ->
            listOf(0.5f, 0.65f, 0.82f, 1f).forEach { scale ->
                val (width, height) = cursorDrawSize(canvas, canvas, 64, 64, scale)
                assertTrue(width in 1..canvas && height in 1..canvas, "$canvas @ $scale -> $width×$height")
                assertTrue(width <= 64 && height <= 64, "ingrandito: $canvas @ $scale -> $width×$height")
            }
        }
    }

    @Test
    fun `misure impossibili non fanno saltare il conto`() {
        assertEquals(1 to 1, cursorDrawSize(0, 0, 64, 64, 1f))
        assertEquals(1 to 1, cursorDrawSize(64, 64, 0, 0, 1f))
    }
}

/**
 * Il fondale animato costa: su Windows si limita, su rendering software di piu'.
 *
 * Era gia' scritto per essere verificabile ma non lo verificava nessuno, e ora che
 * questo modulo ha dei test tanto vale coprirlo.
 */
class AtmosphericFrameIntervalTest {

    @Test
    fun `su Windows il fondale e' limitato a circa sessanta fotogrammi`() {
        assertEquals(17L, atmosphericFrameIntervalMillis(GraphicsApi.METAL, windows = true))
        assertEquals(17L, atmosphericFrameIntervalMillis(GraphicsApi.SOFTWARE_FAST, windows = true))
    }

    @Test
    fun `il rendering software respira piu' piano`() {
        assertEquals(67L, atmosphericFrameIntervalMillis(GraphicsApi.SOFTWARE_FAST, windows = false))
        assertEquals(67L, atmosphericFrameIntervalMillis(GraphicsApi.SOFTWARE_COMPAT, windows = false))
    }

    @Test
    fun `con l'accelerazione nativa si tiene la frequenza dello schermo`() {
        assertEquals(null, atmosphericFrameIntervalMillis(GraphicsApi.METAL, windows = false))
        assertEquals(null, atmosphericFrameIntervalMillis(GraphicsApi.OPENGL, windows = false))
    }
}
