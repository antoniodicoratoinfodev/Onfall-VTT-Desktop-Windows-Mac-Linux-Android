package app.d6d.desktop

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.d6d.ui.AppIdentity
import app.d6d.ui.AppRoot
import app.d6d.ui.components.LocalMapDragCursor
import app.d6d.ui.cursors.CursorPair
import app.d6d.ui.cursors.CursorPairPreview
import app.d6d.ui.cursors.CursorPreferences
import app.d6d.ui.cursors.CursorSize
import app.d6d.ui.images.FilePicker
import java.awt.Cursor
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import org.jetbrains.skiko.GraphicsApi
import app.d6d.ui.i18n.AppLocale

/**
 * Il sistema operativo, per le poche decisioni che dipendono davvero da lui.
 *
 * Sono due: quanto puo' costare il fondale animato, e come si apre la finestra.
 */
private val operatingSystem: String get() = System.getProperty("os.name", "")

private val runningOnWindows: Boolean
    get() = operatingSystem.startsWith("Windows", ignoreCase = true)

private val runningOnMacOs: Boolean
    get() = operatingSystem.startsWith("Mac", ignoreCase = true)

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
 * Il fondale e' volutamente piu' leggero su Windows, dove DPI e monitor ad alto
 * refresh moltiplicano il costo dei gradienti. Il limite a circa 60 FPS evita
 * il lavoro extra dei display a 120/144 Hz; Metal/macOS conserva la frequenza nativa.
 */
internal fun atmosphericFrameIntervalMillis(renderApi: GraphicsApi, windows: Boolean): Long? = when {
    windows -> 17L
    renderApi == GraphicsApi.SOFTWARE_FAST || renderApi == GraphicsApi.SOFTWARE_COMPAT -> 67L
    else -> null
}

/**
 * Selettore di immagini del desktop.
 *
 * Usa il dialogo nativo AWT invece di Swing: su macOS e' quello che l'utente si
 * aspetta di vedere. Il filtro sulle estensioni e' un aiuto, non una garanzia:
 * la validazione vera la fa l'archivio delle immagini.
 */
private fun desktopFilePicker() = FilePicker {
    val dialog = FileDialog(
        null as Frame?,
        AppLocale.current.maps.chooseImageDialogTitle,
        FileDialog.LOAD,
    )
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
 *
 * `getBestCursorSize` dice quanto grande **puo'** essere un cursore, non quanto
 * grande debba essere disegnato questo. Su macOS e Windows i due numeri coincidono
 * con la misura dei nostri PNG e la differenza non si vedeva; su X11 il server
 * risponde spesso con il massimo che sopporta — molto oltre i sessantaquattro pixel
 * del disegno — e il cursore veniva ingrandito fin li'. Ne uscivano puntatori enormi
 * e sfocati, con il punto attivo lontano dalla punta: strani qualunque coppia si
 * scegliesse, perche' il difetto non era nei disegni ma in quanto venivano stirati.
 *
 * Il disegno non si ingrandisce mai oltre la propria misura naturale. La tela invece
 * resta quella che il sistema ha chiesto, perche' e' quella che si aspetta di
 * ricevere: il cursore ci sta dentro in alto a sinistra, dove sta il punto attivo.
 */
private fun fantasyCursor(
    imageBytes: ByteArray,
    hotspotAt64Px: Point,
    scale: Float,
    name: String,
    fallback: Int,
): Cursor = runCatching {
    val toolkit = Toolkit.getDefaultToolkit()
    val cursorSize = toolkit.getBestCursorSize(64, 64)
    check(cursorSize.width > 0 && cursorSize.height > 0)

    val source = ImageIO.read(ByteArrayInputStream(imageBytes))
    checkNotNull(source) { "Immagine del cursore non valida" }
    check(source.width > 0 && source.height > 0)
    val (renderedWidth, renderedHeight) = cursorDrawSize(
        canvasWidth = cursorSize.width,
        canvasHeight = cursorSize.height,
        sourceWidth = source.width,
        sourceHeight = source.height,
        scale = scale,
    )
    val image = BufferedImage(
        cursorSize.width,
        cursorSize.height,
        BufferedImage.TYPE_INT_ARGB,
    ).also { canvas ->
        canvas.createGraphics().apply {
            setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            drawImage(source, 0, 0, renderedWidth, renderedHeight, null)
            dispose()
        }
    }

    // Il punto attivo e' dato sul disegno originale, quindi segue lo stesso rapporto
    // con cui il disegno e' stato ridotto — non la misura della tela.
    val hotspot = Point(
        (hotspotAt64Px.x * renderedWidth / source.width.toFloat())
            .roundToInt()
            .coerceIn(0, cursorSize.width - 1),
        (hotspotAt64Px.y * renderedHeight / source.height.toFloat())
            .roundToInt()
            .coerceIn(0, cursorSize.height - 1),
    )
    toolkit.createCustomCursor(image, hotspot, name)
}.getOrElse {
    Cursor.getPredefinedCursor(fallback)
}

/**
 * Quanto grande disegnare il cursore dentro la tela che il sistema ha chiesto.
 *
 * Due limiti, per due guasti diversi. Oltre la tela il cursore verrebbe **tagliato**;
 * oltre il disegno verrebbe **inventato**, ed e' quest'ultimo che rovinava i cursori
 * su Linux — il server X dichiara di sopportare cursori molto piu' grandi dei nostri
 * sessantaquattro pixel, e ci si disegnava dentro fino a riempirla.
 *
 * Le proporzioni del disegno si mantengono: i nostri sono quadrati, ma un cursore
 * che diventa ovale sarebbe un difetto silenzioso, e costa una moltiplicazione.
 */
internal fun cursorDrawSize(
    canvasWidth: Int,
    canvasHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    scale: Float,
): Pair<Int, Int> {
    if (canvasWidth < 1 || canvasHeight < 1 || sourceWidth < 1 || sourceHeight < 1) return 1 to 1
    val visualScale = scale.coerceIn(0.5f, 1f)
    // Il fattore che fa stare il disegno nella tela senza mai ingrandirlo.
    val fit = minOf(
        1f,
        canvasWidth / sourceWidth.toFloat(),
        canvasHeight / sourceHeight.toFloat(),
    ) * visualScale
    return (sourceWidth * fit).roundToInt().coerceIn(1, canvasWidth) to
        (sourceHeight * fit).roundToInt().coerceIn(1, canvasHeight)
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
    pointerHotspot: Point,
    grabHotspot: Point,
    size: CursorSize,
): DesktopCursorPair {
    val pointerBytes = cursorResourceBytes(pointerResource)
    val grabBytes = cursorResourceBytes(grabResource)
    val suffix = pair.name.lowercase()
    return DesktopCursorPair(
        pointer = fantasyCursor(
            imageBytes = pointerBytes,
            hotspotAt64Px = pointerHotspot,
            scale = size.scale,
            name = "Onfall fantasy pointer $suffix",
            fallback = Cursor.DEFAULT_CURSOR,
        ),
        grab = fantasyCursor(
            imageBytes = grabBytes,
            hotspotAt64Px = grabHotspot,
            scale = size.scale,
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
    val dataDirectory = remember { dataDirectory() }
    val filePicker = remember { desktopFilePicker() }
    var exitRequested by remember { mutableStateOf(false) }
    val cursorStore = remember(dataDirectory) {
        CursorPairStore(dataDirectory.resolve("cursor-pair.txt"))
    }
    val cursorSizeStore = remember(dataDirectory) {
        CursorSizeStore(dataDirectory.resolve("cursor-size.txt"))
    }
    var selectedCursorPair by remember(cursorStore) { mutableStateOf(cursorStore.load()) }
    var selectedCursorSize by remember(cursorSizeStore) {
        mutableStateOf(cursorSizeStore.load())
    }
    val selectCursorPair = remember(cursorStore) {
        { pair: CursorPair ->
            // Prima aggiorna lo stato: anche se il disco non fosse scrivibile, il
            // cambio nella sessione corrente deve essere immediato.
            selectedCursorPair = pair
            cursorStore.save(pair)
            Unit
        }
    }
    val selectCursorSize = remember(cursorSizeStore) {
        { size: CursorSize ->
            selectedCursorSize = size
            cursorSizeStore.save(size)
            Unit
        }
    }

    val windowIcon = remember(iconBytes) {
        iconBytes?.let { runCatching { BitmapPainter(it.decodeToImageBitmap()) }.getOrNull() }
    }

    // Massimizzata, non a schermo intero: la finestra riempie lo spazio utile e
    // lascia barra del titolo e barra delle applicazioni dove sono, cosi' si puo'
    // ancora passare a un'altra finestra. Su Windows e Linux e' cio' che ci si
    // aspetta da un programma con cui si lavora per ore; su macOS no — li' lo zoom
    // e' un gesto diverso e la finestra si apre come sempre.
    //
    // Le misure restano: sono quelle a cui la finestra torna quando la si riduce.
    val windowState = rememberWindowState(
        placement = if (runningOnMacOs) WindowPlacement.Floating else WindowPlacement.Maximized,
        width = 1480.dp,
        height = 940.dp,
    )
    Window(
        onCloseRequest = { exitRequested = true },
        title = AppIdentity.windowTitle(AppLocale.language),
        state = windowState,
        icon = windowIcon,
    ) {
        var windowFocused by remember(window) { mutableStateOf(window.isFocused) }
        var renderApi by remember(window) { mutableStateOf(window.renderApi) }
        DisposableEffect(window) {
            val focusListener = object : WindowAdapter() {
                override fun windowGainedFocus(event: WindowEvent) {
                    windowFocused = true
                }

                override fun windowLostFocus(event: WindowEvent) {
                    windowFocused = false
                }
            }
            window.addWindowFocusListener(focusListener)
            window.onRenderApiChanged { renderApi = window.renderApi }
            onDispose { window.removeWindowFocusListener(focusListener) }
        }
        val windows = remember { runningOnWindows }
        val backgroundFrameInterval = atmosphericFrameIntervalMillis(renderApi, windows)
        val cursorPairs = remember(selectedCursorSize) {
            mapOf(
                CursorPair.COLD to desktopCursorPair(
                    pair = CursorPair.COLD,
                    pointerResource = "/cursors/fantasy-pointer-knight-a-cold.png",
                    grabResource = "/cursors/fantasy-grab-knight-a-cold.png",
                    pointerHotspot = Point(8, 6),
                    grabHotspot = Point(23, 18),
                    size = selectedCursorSize,
                ),
                CursorPair.WARM to desktopCursorPair(
                    pair = CursorPair.WARM,
                    pointerResource = "/cursors/fantasy-pointer-knight-b-warm.png",
                    grabResource = "/cursors/fantasy-grab-knight-b-warm.png",
                    pointerHotspot = Point(8, 6),
                    grabHotspot = Point(23, 18),
                    size = selectedCursorSize,
                ),
                CursorPair.CLASSIC to desktopCursorPair(
                    pair = CursorPair.CLASSIC,
                    pointerResource = "/cursors/fantasy-pointer.png",
                    grabResource = "/cursors/fantasy-grab.png",
                    pointerHotspot = Point(6, 6),
                    grabHotspot = Point(20, 17),
                    size = selectedCursorSize,
                ),
                CursorPair.RUNIC to desktopCursorPair(
                    pair = CursorPair.RUNIC,
                    pointerResource = "/cursors/fantasy-pointer-alt.png",
                    grabResource = "/cursors/fantasy-grab-alt.png",
                    pointerHotspot = Point(6, 6),
                    grabHotspot = Point(20, 17),
                    size = selectedCursorSize,
                ),
                CursorPair.STEEL to desktopCursorPair(
                    pair = CursorPair.STEEL,
                    pointerResource = "/cursors/fantasy-pointer-alt-2.png",
                    grabResource = "/cursors/fantasy-grab-alt-2.png",
                    pointerHotspot = Point(6, 6),
                    grabHotspot = Point(20, 17),
                    size = selectedCursorSize,
                ),
            )
        }
        val activeCursors = cursorPairs.getValue(selectedCursorPair)
        val cursorPreferences = CursorPreferences(
            selected = selectedCursorPair,
            size = selectedCursorSize,
            previews = CursorPair.entries.map { cursorPairs.getValue(it).preview },
            onSelect = selectCursorPair,
            onSizeChange = selectCursorSize,
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
                    filePicker = filePicker,
                    cursorPreferences = cursorPreferences,
                    atmosphericAnimationFrameMillis = backgroundFrameInterval,
                    atmosphericAnimationRunning = windowFocused && !windowState.isMinimized,
                    exitRequested = exitRequested,
                    onExitRequestHandled = { exitRequested = false },
                    onExitConfirmed = ::exitApplication,
                )
            }
            ClickFlameOverlay(clickFlames, Modifier.fillMaxSize())
        }
    }
}
