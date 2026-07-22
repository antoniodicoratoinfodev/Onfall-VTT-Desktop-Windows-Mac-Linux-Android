package app.d6d.desktop

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.d6d.ui.AppIdentity
import app.d6d.ui.AppRoot
import app.d6d.ui.images.FilePicker
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files
import java.nio.file.Path

/**
 * Cartella dati locale.
 *
 * L'applicazione e' offline-first: il disco locale e' la fonte di verita'.
 * Il percorso e' sovrascrivibile con `-Donfall.dataDir=...` per test e sviluppo.
 */
private fun dataDirectory(): Path {
    val override = System.getProperty("onfall.dataDir")
    val directory = if (!override.isNullOrBlank()) {
        Path.of(override)
    } else {
        Path.of(System.getProperty("user.home"), ".onfall")
    }
    Files.createDirectories(directory)
    return directory
}

/**
 * Selettore di immagini del desktop.
 *
 * Usa il dialogo nativo AWT invece di Swing: su macOS e' quello che l'utente si
 * aspetta di vedere. Il filtro sulle estensioni e' un aiuto, non una garanzia:
 * la validazione vera la fa l'archivio delle immagini.
 */
private fun desktopFilePicker() = FilePicker {
    val dialog = FileDialog(null as Frame?, "Scegli un'immagine", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        val lower = name.lowercase()
        listOf(".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif").any(lower::endsWith)
    }
    dialog.isVisible = true
    val chosen = dialog.file
    val directory = dialog.directory
    if (chosen == null || directory == null) null else Path.of(directory, chosen)
}

fun main() = application {
    val dataDirectory = dataDirectory()

    Window(
        onCloseRequest = ::exitApplication,
        title = AppIdentity.windowTitle,
        state = rememberWindowState(width = 1480.dp, height = 940.dp),
    ) {
        // La shell densa e' quella predefinita sul desktop, ma se la finestra
        // viene stretta molto si passa al layout compatto invece di comprimere
        // tre pannelli in uno spazio illeggibile.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            AppRoot(
                dataDirectory = dataDirectory,
                compact = maxWidth < 1000.dp,
                modifier = Modifier.fillMaxSize(),
                filePicker = desktopFilePicker(),
            )
        }
    }
}
