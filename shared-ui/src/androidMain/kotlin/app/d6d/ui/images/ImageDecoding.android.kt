package app.d6d.ui.images

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.nio.file.Path

/**
 * BitmapFactory sottocampiona con `inSampleSize`, e le dimensioni si leggono prima
 * con `inJustDecodeBounds`: una passata sulla sola intestazione, senza allocare nulla.
 */
internal actual fun decodeSampled(path: Path, maxPixels: Long): ImageBitmap? {
    val file = path.toFile().path
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleStep(bounds.outWidth, bounds.outHeight, maxPixels)
    }
    return BitmapFactory.decodeFile(file, options)?.asImageBitmap()
}
