package app.d6d.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as SkiaFont

/** Skia costruisce un carattere direttamente dai byte. */
internal actual fun themeFont(identity: String, data: ByteArray, weight: FontWeight): Font =
    SkiaFont(identity = identity, data = data, weight = weight)
