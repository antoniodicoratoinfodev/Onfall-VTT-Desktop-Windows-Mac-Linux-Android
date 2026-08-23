package app.d6d.ui.battle

import androidx.compose.ui.graphics.Color
import app.d6d.ui.board.VisionPresentation
import app.d6d.ui.theme.Palette
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VisionFogStyleTest {

    @Test
    fun `le tre rese risolvono i colori finali in un solo posto`() {
        assertNull(VisionPresentation.ALL.fogColors())
        assertEquals(
            VisionFogColors(Palette.Abyss.copy(alpha = 0.78f), Color.Black),
            VisionPresentation.MEMORY_BLACK.fogColors(),
        )
        assertEquals(
            VisionFogColors(Palette.Abyss.copy(alpha = 0.32f), Palette.Abyss.copy(alpha = 0.62f)),
            VisionPresentation.MEMORY_DIM.fogColors(),
        )

        VisionPresentation.entries.mapNotNull { it.fogColors() }.forEach { colors ->
            assertTrue(colors.explored.alpha < colors.unseen.alpha)
        }
    }
}
