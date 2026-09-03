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
import androidx.compose.material3.Text
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
        val sides = listOf(4, 6, 8, 10, 12, 20)
        setContent {
            AppTheme {
                Row(
                    Modifier
                        .testTag("dice-preview")
                        .width(1_200.dp)
                        .height(250.dp)
                        .background(Palette.Abyss)
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    sides.forEachIndexed { index, dieSides ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
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
            }
        }

        waitForIdle()
        val bitmap = onNodeWithTag("dice-preview").captureToImage()
        val pixels = bitmap.toPixelMap()
        val output = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = pixels[x, y]
                output.setRGB(x, y, color.toArgb())
            }
        }
        ImageIO.write(output, "png", File("/private/tmp/onfall-dice-preview.png"))
    }
}
