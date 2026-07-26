package app.d6d.desktop

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.d6d.ui.AppIdentity
import app.d6d.ui.AppRoot
import app.d6d.ui.images.FilePicker
import java.awt.Cursor
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

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

/**
 * Puntatore predefinito del desktop, originale ma coerente con il dark fantasy
 * dell'interfaccia. Il ridimensionamento dei pannelli continua a usare i cursori
 * di sistema specifici, applicati dai rispettivi componenti Compose.
 */
private fun fantasyPointerCursor(): Cursor = runCatching {
    val toolkit = Toolkit.getDefaultToolkit()
    val cursorSize = toolkit.getBestCursorSize(64, 64)
    check(cursorSize.width > 0 && cursorSize.height > 0)

    val source = ImageIO.read(
        requireNotNull(
            object {}.javaClass.getResourceAsStream("/cursors/fantasy-pointer.png"),
        ) { "Risorsa del cursore non trovata" },
    )
    val image = if (source.width == cursorSize.width && source.height == cursorSize.height) {
        source
    } else {
        BufferedImage(cursorSize.width, cursorSize.height, BufferedImage.TYPE_INT_ARGB).also { scaled ->
            scaled.createGraphics().apply {
                setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                )
                drawImage(source, 0, 0, cursorSize.width, cursorSize.height, null)
                dispose()
            }
        }
    }

    toolkit.createCustomCursor(image, Point(1, 1), "Onfall fantasy pointer")
}.getOrElse {
    Cursor.getDefaultCursor()
}

fun main() = application {
    val dataDirectory = dataDirectory()
    var exitRequested by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = { exitRequested = true },
        title = AppIdentity.windowTitle,
        state = rememberWindowState(width = 1480.dp, height = 940.dp),
    ) {
        val fantasyPointer = remember { fantasyPointerCursor() }
        val clickFlames = rememberClickFlameState()

        // La shell densa e' quella predefinita sul desktop, ma se la finestra
        // viene stretta molto si passa al layout compatto invece di comprimere
        // tre pannelli in uno spazio illeggibile.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerHoverIcon(PointerIcon(fantasyPointer))
                .clickFlameBursts(clickFlames),
        ) {
            AppRoot(
                dataDirectory = dataDirectory,
                compact = maxWidth < 1000.dp,
                modifier = Modifier.fillMaxSize(),
                filePicker = desktopFilePicker(),
                exitRequested = exitRequested,
                onExitRequestHandled = { exitRequested = false },
                onExitConfirmed = ::exitApplication,
            )
            ClickFlameOverlay(clickFlames, Modifier.fillMaxSize())
        }
    }
}
