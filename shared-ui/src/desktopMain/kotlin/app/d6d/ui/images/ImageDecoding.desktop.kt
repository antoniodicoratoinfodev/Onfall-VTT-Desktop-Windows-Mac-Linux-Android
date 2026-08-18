package app.d6d.ui.images

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

internal actual fun decodeSampled(path: Path, maxPixels: Long): ImageBitmap? =
    decodeSampledImage(path, maxPixels)?.toComposeImageBitmap()

/**
 * La lettura sottocampionata vera e propria, prima della conversione a Skia.
 *
 * Sta a parte perche' e' l'unico pezzo che contenga una decisione — di quanto
 * ridurre — e perche' Skia non si inizializza in una JVM senza schermo: separandola,
 * quella decisione resta verificabile da un test.
 */
internal fun decodeSampledImage(path: Path, maxPixels: Long): BufferedImage? =
    ImageIO.createImageInputStream(path.toFile())?.use { stream ->
        val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return null
        try {
            reader.input = stream
            val step = sampleStep(reader.getWidth(0), reader.getHeight(0), maxPixels)
            val parameters = reader.defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) }
            reader.read(0, parameters)
        } finally {
            reader.dispose()
        }
    }
