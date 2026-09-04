package app.d6d.ui.components

import androidx.compose.ui.graphics.toPixelMap
import app.d6d.rules.character.CharacterClassId
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClassIconAssetsTest {
    @Test
    fun `ogni classe ha una risorsa png valida`() {
        CharacterClassId.entries.forEach { classId ->
            val bytes = requireNotNull(ClassIconAssets.bytesOf(classId)) {
                "Icona mancante per ${classId.contentId}"
            }
            assertTrue(bytes.size > 1_000, "Icona vuota per ${classId.contentId}")
            assertArrayEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
                bytes.copyOfRange(0, 4),
                "Firma PNG non valida per ${classId.contentId}",
            )
        }
    }

    @Test
    fun `la scacchiera diventa trasparente senza cancellare i simboli`() {
        CharacterClassId.entries.forEach { classId ->
            val image = requireNotNull(
                decodeClassIcon(ClassIconAssets.bytesOf(classId), maximumSide = 192),
            )
            val pixels = image.toPixelMap()

            listOf(
                0 to 0,
                pixels.width - 1 to 0,
                0 to pixels.height - 1,
                pixels.width - 1 to pixels.height - 1,
            ).forEach { (x, y) ->
                assertEquals(0f, pixels[x, y].alpha, "sfondo ${classId.contentId} in $x,$y")
            }
            var visiblePixels = 0
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    if (pixels[x, y].alpha > 0.5f) visiblePixels++
                }
            }
            assertTrue(
                visiblePixels > pixels.width * pixels.height / 10,
                "simbolo ${classId.contentId} cancellato",
            )
        }
    }
}
