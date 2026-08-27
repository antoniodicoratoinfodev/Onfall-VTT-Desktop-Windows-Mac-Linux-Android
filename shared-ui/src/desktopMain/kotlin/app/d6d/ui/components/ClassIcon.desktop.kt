package app.d6d.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

internal actual fun decodeClassIcon(bytes: ByteArray, maximumSide: Int): ImageBitmap? {
    if (maximumSide <= 0) return null
    val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
    val scale = minOf(1.0, maximumSide.toDouble() / max(source.width, source.height))
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    val reduced = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    reduced.createGraphics().use { graphics ->
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(source, 0, 0, width, height, null)
    }
    val pixels = IntArray(width * height)
    reduced.getRGB(0, 0, width, height, pixels, 0, width)
    pixels.indices.forEach { index -> pixels[index] = withoutPreviewGrid(pixels[index]) }
    reduced.setRGB(0, 0, width, height, pixels, 0, width)
    return reduced.toComposeImageBitmap()
}

/** Le due tinte della scacchiera sono quasi bianche e perfettamente neutre. */
private fun withoutPreviewGrid(argb: Int): Int {
    val red = argb ushr 16 and 0xff
    val green = argb ushr 8 and 0xff
    val blue = argb and 0xff
    val lightest = maxOf(red, green, blue)
    val darkest = minOf(red, green, blue)
    return if (darkest >= 242 && lightest - darkest <= 5) argb and 0x00ffffff else argb
}

private inline fun <T : java.awt.Graphics2D, R> T.use(block: (T) -> R): R =
    try {
        block(this)
    } finally {
        dispose()
    }
