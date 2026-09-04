package app.d6d.ui.dice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.d6d.ui.theme.AppTheme
import app.d6d.ui.theme.Palette
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

@OptIn(ExperimentalTestApi::class)
class DiceRenderPreviewTest {

    @Test
    fun `genera la tavola di controllo visivo dei solidi`() = runComposeUiTest {
        val sides = listOf(4, 6, 8, 10, 12, 20, 100)
        setContent {
            AppTheme {
                Column(
                    Modifier
                        .testTag("dice-preview")
                        .width(900.dp)
                        .height(410.dp)
                        .background(Palette.Abyss)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(190.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        sides.forEachIndexed { index, dieSides ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(105.dp), contentAlignment = Alignment.Center) {
                                    PolyhedralDie(
                                        sides = dieSides,
                                        faceLabels = (1..dieSides).map(Int::toString),
                                        targetFaceIndex = dieSides - 1,
                                        progress = 1f,
                                        phaseOffset = index,
                                        reducedEffects = false,
                                        palette = skinPalette(DiceSkinId.DRAGONFORGE),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Text("d$dieSides = $dieSides", color = Color.White)
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(190.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChoicePreview("Vantaggio · 7 / 18 → 18", keepFirst = false, animationId = 101)
                        ChoicePreview("Svantaggio · 7 / 18 → 7", keepFirst = true, animationId = 102)
                    }
                }
            }
        }

        waitForIdle()
        val bitmap = onNodeWithTag("dice-preview").captureToImage()
        val pixels = bitmap.toPixelMap()
        // La tavola ha gia' un fondale opaco: eliminare l'alfa evita che alcuni
        // visualizzatori interpretino i pixel Compose premoltiplicati come trasparenti.
        val output = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = pixels[x, y]
                output.setRGB(x, y, color.toArgb())
            }
        }
        // La tavola e' un artefatto di ispezione: va nella cartella temporanea
        // di sistema, cosi' il test gira su Windows, macOS e Linux.
        val preview = File(System.getProperty("java.io.tmpdir"), "onfall-dice-preview.png")
        ImageIO.write(output, "png", preview)
    }
}

@Composable
private fun ChoicePreview(label: String, keepFirst: Boolean, animationId: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(7, 18).forEachIndexed { index, value ->
                CinematicDie(
                    spec = CinematicDieSpec(
                        sides = 20,
                        faceLabels = (1..20).map(Int::toString),
                        targetFaceIndex = value - 1,
                        kept = (index == 0) == keepFirst,
                        competing = true,
                    ),
                    skin = DiceSkinId.DRAGONFORGE,
                    rollPhase = ForegroundRollPhase.RESULT,
                    reducedEffects = false,
                    animationId = animationId,
                    phase = index,
                    dieSize = 110.dp,
                )
            }
        }
        Text(label, color = Color.White)
    }
}
