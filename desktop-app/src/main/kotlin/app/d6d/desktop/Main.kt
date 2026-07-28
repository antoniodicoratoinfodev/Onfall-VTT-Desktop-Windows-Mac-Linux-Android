package app.d6d.desktop

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.d6d.ui.AppIdentity
import app.d6d.ui.AppRoot
import app.d6d.ui.components.LocalMapDragCursor
import app.d6d.ui.cursors.CursorPair
import app.d6d.ui.cursors.CursorPairPreview
import app.d6d.ui.cursors.CursorPreferences
import app.d6d.ui.images.FilePicker
import java.awt.Cursor
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.roundToInt

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
 * Carica un cursore fantasy e lo adatta alla dimensione preferita dal sistema.
 * Anche il punto attivo viene scalato: il guanto che afferra mantiene quindi la
 * presa sulla stessa coordinata della mappa su monitor e piattaforme differenti.
 */
private fun fantasyCursor(
    imageBytes: ByteArray,
    hotspotAt64Px: Point,
    name: String,
    fallback: Int,
): Cursor = runCatching {
    val toolkit = Toolkit.getDefaultToolkit()
    val cursorSize = toolkit.getBestCursorSize(64, 64)
    check(cursorSize.width > 0 && cursorSize.height > 0)

    val source = ImageIO.read(ByteArrayInputStream(imageBytes))
    checkNotNull(source) { "Immagine del cursore non valida" }
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

    val hotspot = Point(
        (hotspotAt64Px.x * cursorSize.width / 64f)
            .roundToInt()
            .coerceIn(0, cursorSize.width - 1),
        (hotspotAt64Px.y * cursorSize.height / 64f)
            .roundToInt()
            .coerceIn(0, cursorSize.height - 1),
    )
    toolkit.createCustomCursor(image, hotspot, name)
}.getOrElse {
    Cursor.getPredefinedCursor(fallback)
}

private object CursorResourceAnchor

private fun cursorResourceBytes(resource: String): ByteArray =
    requireNotNull(CursorResourceAnchor::class.java.getResourceAsStream(resource)) {
        "Risorsa del cursore non trovata: $resource"
    }.use { it.readBytes() }

/**
 * Icona dell'applicazione.
 *
 * Un'icona mancante non e' un motivo per non aprire il programma: se la risorsa
 * non c'e' si torna a quella predefinita del sistema invece di far cadere l'avvio.
 */
private fun appIconBytes(): ByteArray? = runCatching {
    CursorResourceAnchor::class.java.getResourceAsStream("/icons/onfall-icon.png")
        ?.use { it.readBytes() }
}.getOrNull()

/**
 * Icona nel Dock di macOS e nella barra delle applicazioni.
 *
 * La finestra da sola non basta: fuori da un pacchetto nativo il Dock mostra
 * l'icona di Java finche' non gliene si passa una a mano. La Taskbar non e'
 * disponibile ovunque, quindi ogni fallimento resta silenzioso.
 */
private fun applyTaskbarIcon(bytes: ByteArray) {
    runCatching {
        if (!Taskbar.isTaskbarSupported()) return
        val taskbar = Taskbar.getTaskbar()
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
        taskbar.iconImage = ImageIO.read(ByteArrayInputStream(bytes))
    }
}

private data class DesktopCursorPair(
    val pointer: Cursor,
    val grab: Cursor,
    val preview: CursorPairPreview,
)

/**
 * Costruisce cursori e anteprime dagli stessi byte: il Compendio mostra quindi
 * esattamente i PNG che la finestra usa per puntare e afferrare la mappa.
 */
private fun desktopCursorPair(
    pair: CursorPair,
    pointerResource: String,
    grabResource: String,
): DesktopCursorPair {
    val pointerBytes = cursorResourceBytes(pointerResource)
    val grabBytes = cursorResourceBytes(grabResource)
    val suffix = pair.name.lowercase()
    return DesktopCursorPair(
        pointer = fantasyCursor(
            imageBytes = pointerBytes,
            hotspotAt64Px = Point(1, 1),
            name = "Onfall fantasy pointer $suffix",
            fallback = Cursor.DEFAULT_CURSOR,
        ),
        grab = fantasyCursor(
            imageBytes = grabBytes,
            hotspotAt64Px = Point(23, 18),
            name = "Onfall fantasy grab $suffix",
            fallback = Cursor.HAND_CURSOR,
        ),
        preview = CursorPairPreview(
            pair = pair,
            pointer = pointerBytes.decodeToImageBitmap(),
            grab = grabBytes.decodeToImageBitmap(),
        ),
    )
}

fun main() {
    val iconBytes = appIconBytes()
    iconBytes?.let(::applyTaskbarIcon)
    application { OnfallApplication(iconBytes) }
}

@Composable
private fun ApplicationScope.OnfallApplication(iconBytes: ByteArray?) {
    val dataDirectory = dataDirectory()
    var exitRequested by remember { mutableStateOf(false) }
    val cursorStore = remember(dataDirectory) {
        CursorPairStore(dataDirectory.resolve("cursor-pair.txt"))
    }
    var selectedCursorPair by remember(cursorStore) { mutableStateOf(cursorStore.load()) }
    val selectCursorPair = remember(cursorStore) {
        { pair: CursorPair ->
            // Prima aggiorna lo stato: anche se il disco non fosse scrivibile, il
            // cambio nella sessione corrente deve essere immediato.
            selectedCursorPair = pair
            cursorStore.save(pair)
            Unit
        }
    }

    val windowIcon = remember(iconBytes) {
        iconBytes?.let { runCatching { BitmapPainter(it.decodeToImageBitmap()) }.getOrNull() }
    }

    Window(
        onCloseRequest = { exitRequested = true },
        title = AppIdentity.windowTitle,
        state = rememberWindowState(width = 1480.dp, height = 940.dp),
        icon = windowIcon,
    ) {
        val cursorPairs = remember {
            mapOf(
                CursorPair.COLD to desktopCursorPair(
                    pair = CursorPair.COLD,
                    pointerResource = "/cursors/fantasy-pointer-knight-a-cold.png",
                    grabResource = "/cursors/fantasy-grab-knight-a-cold.png",
                ),
                CursorPair.WARM to desktopCursorPair(
                    pair = CursorPair.WARM,
                    pointerResource = "/cursors/fantasy-pointer-knight-b-warm.png",
                    grabResource = "/cursors/fantasy-grab-knight-b-warm.png",
                ),
            )
        }
        val activeCursors = cursorPairs.getValue(selectedCursorPair)
        val cursorPreferences = CursorPreferences(
            selected = selectedCursorPair,
            previews = CursorPair.entries.map { cursorPairs.getValue(it).preview },
            onSelect = selectCursorPair,
        )
        val clickFlames = rememberClickFlameState()
        var grabbingMap by remember { mutableStateOf(false) }
        val setGrabbingMap = remember {
            { grabbing: Boolean -> grabbingMap = grabbing }
        }

        // La shell densa e' quella predefinita sul desktop, ma se la finestra
        // viene stretta molto si passa al layout compatto invece di comprimere
        // tre pannelli in uno spazio illeggibile.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerHoverIcon(
                    PointerIcon(if (grabbingMap) activeCursors.grab else activeCursors.pointer),
                )
                .clickFlameBursts(clickFlames),
        ) {
            CompositionLocalProvider(LocalMapDragCursor provides setGrabbingMap) {
                AppRoot(
                    dataDirectory = dataDirectory,
                    compact = maxWidth < 1000.dp,
                    modifier = Modifier.fillMaxSize(),
                    filePicker = desktopFilePicker(),
                    cursorPreferences = cursorPreferences,
                    exitRequested = exitRequested,
                    onExitRequestHandled = { exitRequested = false },
                    onExitConfirmed = ::exitApplication,
                )
            }
            ClickFlameOverlay(clickFlames, Modifier.fillMaxSize())
        }
    }
}
