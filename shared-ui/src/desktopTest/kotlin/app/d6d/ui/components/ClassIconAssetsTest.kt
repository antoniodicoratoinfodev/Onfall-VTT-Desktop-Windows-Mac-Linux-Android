package app.d6d.ui.components

import app.d6d.rules.character.CharacterClassId
import org.junit.jupiter.api.Assertions.assertArrayEquals
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
}
