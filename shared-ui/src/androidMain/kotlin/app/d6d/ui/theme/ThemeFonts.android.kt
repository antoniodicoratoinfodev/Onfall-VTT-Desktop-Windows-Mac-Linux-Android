package app.d6d.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/**
 * Android carica un carattere da un file, non da un array di byte.
 *
 * Il file si scrive nella cartella temporanea dell'applicazione — su Android e' la
 * sua cache privata — e ci resta per la durata del processo. E' una scrittura per
 * carattere all'avvio, quattro in tutto, ed e' il prezzo per avere gli stessi
 * caratteri del desktop invece di quelli che capita di trovare sul telefono.
 */
internal actual fun themeFont(identity: String, data: ByteArray, weight: FontWeight): Font {
    val file = File.createTempFile(identity.substringBeforeLast('.'), ".ttf").apply {
        deleteOnExit()
        writeBytes(data)
    }
    return Font(file = file, weight = weight)
}
