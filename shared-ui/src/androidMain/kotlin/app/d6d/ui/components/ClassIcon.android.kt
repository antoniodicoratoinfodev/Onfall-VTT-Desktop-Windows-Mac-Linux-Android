package app.d6d.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.roundToInt

internal actual fun decodeClassIcon(bytes: ByteArray, maximumSide: Int): ImageBitmap? {
    if (maximumSide <= 0) return null
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val scale = minOf(1f, maximumSide.toFloat() / max(source.width, source.height))
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    val reduced = Bitmap.createScaledBitmap(source, width, height, true)
        .copy(Bitmap.Config.ARGB_8888, true)
    if (reduced !== source) source.recycle()
    val pixels = IntArray(width * height)
    reduced.getPixels(pixels, 0, width, 0, 0, width, height)
    pixels.indices.forEach { index -> pixels[index] = withoutPreviewGrid(pixels[index]) }
    reduced.setPixels(pixels, 0, width, 0, 0, width, height)
    return reduced.asImageBitmap()
}

private fun withoutPreviewGrid(argb: Int): Int {
    val red = argb ushr 16 and 0xff
    val green = argb ushr 8 and 0xff
    val blue = argb and 0xff
    val lightest = maxOf(red, green, blue)
    val darkest = minOf(red, green, blue)
    return if (darkest >= 242 && lightest - darkest <= 5) argb and 0x00ffffff else argb
}
