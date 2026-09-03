package app.d6d.ui.theme

import androidx.compose.ui.text.font.FontWeight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * I caratteri del tema devono essere davvero nel pacchetto.
 *
 * Il difetto che questa prova sorveglia era invisibile: erano risorse Compose, e su
 * Android non venivano impacchettate affatto. L'applicazione non falliva — ripiegava
 * in silenzio sui caratteri di sistema — quindi il modo di accorgersene non era
 * guardare lo schermo ma chiedere i byte e vedere se arrivano.
 */
class ThemeFontsTest {

    private val faces = listOf(
        "cinzel_bold.ttf",
        "cinzel_extrabold.ttf",
        "alegreya_medium.ttf",
        "alegreya_bold.ttf",
        "alegreya_sans_regular.ttf",
        "alegreya_sans_medium.ttf",
        "alegreya_sans_bold.ttf",
        "alegreya_sans_black.ttf",
    )

    @Test
    fun `ogni carattere del tema si legge dal pacchetto`() {
        faces.forEach { fileName ->
            val bytes = checkNotNull(javaClass.getResourceAsStream("/font/$fileName")) {
                "carattere non impacchettato: font/$fileName"
            }.use { it.readBytes() }
            assertTrue(bytes.size > 10_000, "$fileName e' troppo piccolo per essere un carattere")
            // Firma TrueType: 0x00010000. Se la risorsa c'e' ma e' il file sbagliato,
            // se ne accorge qui invece che a schermo.
            assertEquals(0x00.toByte(), bytes[0], fileName)
            assertEquals(0x01.toByte(), bytes[1], fileName)
            assertEquals(0x00.toByte(), bytes[2], fileName)
            assertEquals(0x00.toByte(), bytes[3], fileName)
        }
    }

    @Test
    fun `le famiglie del tema si costruiscono`() {
        // Costruirle e' l'unico modo di provare che il caricamento di piattaforma
        // accetta quei byte: un carattere illeggibile fallisce qui.
        assertNotNull(
            themeFontFamily(
                "cinzel_bold.ttf" to FontWeight.Bold,
                "cinzel_extrabold.ttf" to FontWeight.Black,
            ),
        )
        assertNotNull(
            themeFontFamily(
                "alegreya_medium.ttf" to FontWeight.Medium,
                "alegreya_bold.ttf" to FontWeight.Bold,
            ),
        )
        assertNotNull(
            themeFontFamily(
                "alegreya_sans_regular.ttf" to FontWeight.Normal,
                "alegreya_sans_medium.ttf" to FontWeight.Medium,
                "alegreya_sans_bold.ttf" to FontWeight.Bold,
                "alegreya_sans_black.ttf" to FontWeight.Black,
            ),
        )
    }
}
