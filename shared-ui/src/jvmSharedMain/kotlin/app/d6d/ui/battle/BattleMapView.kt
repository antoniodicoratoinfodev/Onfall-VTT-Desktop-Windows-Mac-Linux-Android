package app.d6d.ui.battle

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.d6d.sheet.feetWithMetres
import app.d6d.ui.components.Chip
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.domain.space.MapBackground
import app.d6d.domain.space.TokenPlacement
import app.d6d.ui.components.Faction
import app.d6d.ui.components.FloatKind
import app.d6d.ui.components.FloatingNumberView
import app.d6d.ui.components.color
import app.d6d.ui.components.initials
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.images.rememberBitmap
import app.d6d.ui.images.rememberPortrait
import app.d6d.ui.state.AreaTargeting
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.state.PendingArea
import app.d6d.ui.theme.OrnateDivider
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.Vignette
import app.d6d.ui.theme.healthColor
import app.d6d.ui.theme.ornateFrame
import app.d6d.ui.theme.panelBrush
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Mappa tattica a griglia.
 *
 * Ogni casella vale la distanza dichiarata dalla griglia — cinque piedi di
 * predefinito — e i segnaposti occupano tante caselle quanto la taglia della
 * creatura. Il movimento passa dal motore, quindi consuma il budget vero e
 * rispetta i limiti: la mappa non e' un disegno separato dalle regole.
 */
@Composable
fun BattleMapView(
    viewModel: BattleViewModel,
    portraits: PortraitRepository,
    cellSize: Dp,
    showGrid: Boolean,
    modifier: Modifier = Modifier,
    gridBrightness: Float = 0.5f,
    editingBackground: Boolean = false,
    dropTarget: TokenPlacementDrag? = null,
    onCellSizeChange: (Dp) -> Unit = {},
) {
    if (!viewModel.mapConfigured) {
        MapNotConfigured(viewModel, modifier)
        return
    }

    val map = viewModel.battleMap
    val grid = map.grid()
    val density = LocalDensity.current
    val background = portraits.rememberBitmap(map.backgroundImage())

    // Collocazione dello sfondo, in caselle. Se l'utente non l'ha ancora toccata
    // si calcola una posizione "contain": l'immagine intera, centrata, senza
    // deformarne la forma per riempire la griglia. Durante la modifica un abbozzo
    // locale guida il disegno e viene salvato una volta sola, al rilascio.
    val storedBackground = map.background()
    val defaultBackground = remember(background, grid.columns(), grid.rows()) {
        background?.let { containBackground(it.width, it.height, grid.columns(), grid.rows()) }
    }
    var backgroundDraft by remember(map.backgroundImage()) { mutableStateOf<MapBackground?>(null) }
    val shownBackground: MapBackground? =
        backgroundDraft ?: storedBackground.takeIf { it.isSet() } ?: defaultBackground
    LaunchedEffect(editingBackground) { if (!editingBackground) backgroundDraft = null }

    // Scala effettiva, aggiornata subito dal gestore della rotella. In questo modo
    // piu' eventi ricevuti prima della ricomposizione si compongono sul valore appena
    // calcolato, invece di riutilizzare ogni volta il vecchio parametro `cellSize`.
    var liveCell by remember { mutableStateOf(cellSize) }
    var appliedDensity by remember { mutableStateOf(density.density) }
    val onZoom by rememberUpdatedState(onCellSizeChange)

    // La camera vive in pixel del viewport. `pan == null` significa che la mappa non
    // e' ancora stata spostata e deve partire esattamente centrata.
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var pan by remember { mutableStateOf<Offset?>(null) }
    val cellPx = with(density) { liveCell.toPx() }

    // Casella sotto il mouse: serve a far seguire al puntatore il cerchio dell'area
    // di un incantesimo mentre lo si mira. Fuori dalla griglia resta nulla.
    var hoverCell by remember { mutableStateOf<IntOffset?>(null) }
    val areaTargeting = viewModel.areaTargeting
    val pendingArea = viewModel.pendingArea
    var manualCardBounds by remember { mutableStateOf<Rect?>(null) }
    var manualCardSize by remember { mutableStateOf(IntSize.Zero) }
    var manualCardOffset by remember(pendingArea?.spellName, pendingArea?.center) {
        mutableStateOf(Offset.Zero)
    }

    /**
     * Il pannello parte centrato in basso. La traslazione viene limitata al
     * viewport, cosi' la maniglia resta sempre recuperabile anche dopo un resize.
     */
    fun constrainedManualCardOffset(requested: Offset): Offset {
        if (viewport == IntSize.Zero || manualCardSize == IntSize.Zero) return requested
        val baseLeft = (viewport.width - manualCardSize.width) / 2f
        val baseTop = (viewport.height - manualCardSize.height).toFloat()
        val minX = -baseLeft
        val maxX = viewport.width - manualCardSize.width - baseLeft
        val minY = -baseTop
        val maxY = viewport.height - manualCardSize.height - baseTop
        return Offset(
            x = if (minX <= maxX) requested.x.coerceIn(minX, maxX) else 0f,
            y = if (minY <= maxY) requested.y.coerceIn(minY, maxY) else 0f,
        )
    }

    LaunchedEffect(pendingArea) {
        if (pendingArea == null) manualCardBounds = null
    }
    LaunchedEffect(viewport, manualCardSize) {
        manualCardOffset = constrainedManualCardOffset(manualCardOffset)
    }

    fun geometry(cell: Float): MapViewportGeometry {
        val gridNow = viewModel.battleMap.grid()
        return MapViewportGeometry(
            viewportSize = viewport,
            columns = gridNow.columns(),
            rows = gridNow.rows(),
            cellPx = cell,
        )
    }

    fun effectiveOffset(camera: MapViewportGeometry): Offset =
        camera.constrain(pan ?: camera.centeredOffset())

    // I pulsanti e lo slider aggiornano `cellSize` dall'esterno. Anche quel cambio
    // passa dalla stessa trasformazione della rotella, ancorata al centro del
    // viewport, cosi' non salta improvvisamente verso l'origine della mappa. Anche
    // un cambio di densita' (per esempio spostando la finestra su un altro monitor)
    // e' uno zoom in pixel e deve conservare lo stesso punto centrale.
    LaunchedEffect(cellSize, density.density) {
        val currentCellPx = liveCell.value * appliedDensity
        val nextCellPx = with(density) { cellSize.toPx() }
        if (nextCellPx != currentCellPx) {
            val camera = geometry(currentCellPx)
            val anchor = Offset(viewport.width / 2f, viewport.height / 2f)
            val nextPan = camera.zoomedOffset(effectiveOffset(camera), nextCellPx, anchor)
            pan = nextPan
            dropTarget?.gridOriginPx = nextPan
            dropTarget?.cellPx = nextCellPx
        }
        liveCell = cellSize
        appliedDensity = density.density
    }

    val camera = MapViewportGeometry(viewport, grid.columns(), grid.rows(), cellPx)
    val mapOffset = effectiveOffset(camera)

    // Il trascinamento dalle barre usa lo stesso viewport e lo stesso offset della
    // camera: non esiste piu' una seconda, implicita geometria del nodo griglia.
    if (dropTarget != null) {
        dropTarget.gridOriginPx = mapOffset
        dropTarget.cellPx = cellPx
        dropTarget.columns = grid.columns()
        dropTarget.rows = grid.rows()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Abyss)
            .clipToBounds()
            .onGloballyPositioned {
                val nextViewport = it.size
                if (nextViewport != viewport) {
                    pan = if (pan == null || viewport == IntSize.Zero) {
                        null
                    } else {
                        // `pan` e' ancora espresso con la densita' gia' applicata.
                        // Se resize e cambio monitor arrivano nello stesso frame, la
                        // nuova densita' verra' trasformata subito dopo dall'effect.
                        val currentCamera = geometry(liveCell.value * appliedDensity)
                        currentCamera.resizedOffset(effectiveOffset(currentCamera), nextViewport)
                    }
                    viewport = nextViewport
                    pan?.let { nextPan -> dropTarget?.gridOriginPx = nextPan }
                }
                dropTarget?.gridCoordinates = it
            }
            // Lo scorrimento viene intercettato nella fase iniziale e consumato: la
            // casella sotto il puntatore resta ferma mentre la scala cambia, cosi' si
            // ingrandisce il punto che si sta guardando e non l'angolo della mappa.
            .pointerInput(viewModel, density.density, dropTarget) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Scroll) continue
                        // Il pannello manuale e' sopra la mappa: la rotellina al suo
                        // interno appartiene alla lista dei bersagli, non allo zoom.
                        val pointer = event.changes.firstOrNull()?.position
                        if (pointer != null && manualCardBounds?.contains(pointer) == true) continue
                        val dy = event.changes.fold(0f) { acc, change -> acc + change.scrollDelta.y }
                        if (dy == 0f) continue
                        val anchor = pointer
                            ?: Offset(viewport.width / 2f, viewport.height / 2f)
                        // Un colpo di rotellina cambia la scala del dieci percento; una
                        // spinta piu' decisa (trackpad) zooma di piu', ma entro un limite
                        // cosi' un gesto ampio non fa saltare la mappa da un estremo all'altro.
                        val steps = (-dy).coerceIn(-4f, 4f)
                        val next = (liveCell * 1.10f.pow(steps)).coerceIn(MIN_CELL, MAX_CELL)
                        if (next != liveCell) {
                            val currentPx = with(density) { liveCell.toPx() }
                            val nextPx = with(density) { next.toPx() }
                            val currentCamera = geometry(currentPx)
                            val nextPan = currentCamera.zoomedOffset(
                                effectiveOffset(currentCamera),
                                nextPx,
                                anchor,
                            )
                            pan = nextPan
                            dropTarget?.gridOriginPx = nextPan
                            dropTarget?.cellPx = nextPx
                            // Aggiornata SUBITO, prima di `onZoom`: il colpo di rotella
                            // successivo nello stesso frame parte da questa scala e non
                            // da quella vecchia, cosi' l'ancoraggio non si somma due volte.
                            liveCell = next
                            onZoom(next)
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            // Premi e trascina su una zona libera per spostare la mappa, come afferrare
            // un foglio e farlo scorrere sotto il riquadro; un tocco secco senza
            // trascinamento resta un comando sulla casella. Le chiavi sono soltanto
            // dipendenze stabili, non `pan`: ogni spostamento puo' quindi ricomporre
            // senza interrompere il gesto a meta'. I segnaposti consumano da soli la
            // pressione e hanno la precedenza, percio' trascinarli non viene mai
            // scambiato per uno spostamento della mappa.
            .pointerInput(viewModel, density.density, dropTarget) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    // I gesti iniziati sul pannello sovrapposto non devono mai
                    // diventare pan o tap della mappa. I controlli del pannello
                    // continuano invece a ricevere normalmente lo stesso gesto.
                    if (manualCardBounds?.contains(down.position) == true) {
                        return@awaitEachGesture
                    }
                    var panning = false
                    var travelled = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // Rilascio senza trascinamento: comando sulla casella. Il
                            // punto torna in coordinate della griglia togliendo la
                            // traslazione, perche' la mappa puo' essere stata spostata.
                            if (!panning) {
                                val cell = with(density) { liveCell.toPx() }
                                val currentCamera = geometry(cell)
                                currentCamera.cellAt(down.position, effectiveOffset(currentCamera))?.let {
                                    onCellTapped(viewModel, it.x, it.y)
                                }
                            }
                            change.consume()
                            break
                        }
                        val movement = change.positionChange()
                        if (!panning) {
                            travelled += movement
                            if (travelled.getDistance() > viewConfiguration.touchSlop) panning = true
                        }
                        if (panning) {
                            val currentCamera = geometry(with(density) { liveCell.toPx() })
                            val nextPan = currentCamera.constrain(effectiveOffset(currentCamera) + movement)
                            pan = nextPan
                            dropTarget?.gridOriginPx = nextPan
                            change.consume()
                        }
                    }
                }
            }
            // Traccia la casella sotto il mouse senza consumare nulla: pan, tap e
            // rotella continuano a funzionare. Serve al cerchio dell'area, che segue
            // il puntatore mentre si mira un incantesimo.
            .pointerInput(viewModel, density.density) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        when (event.type) {
                            PointerEventType.Exit -> hoverCell = null
                            else -> if (change != null) {
                                val cam = geometry(with(density) { liveCell.toPx() })
                                hoverCell = cam.cellAt(change.position, effectiveOffset(cam))
                            }
                        }
                    }
                }
            },
    ) {
        // Il Canvas ha sempre la dimensione del viewport. La mappa e' un mondo
        // virtuale disegnato applicando `mapOffset + casella * cellPx`: quindi non
        // puo' essere compressa dai constraints di Compose, neppure a zoom alto o
        // con una griglia 400 x 400. Le linee fuori schermo non vengono iterate.
        Canvas(Modifier.fillMaxSize()) {
            // Il fondale resta piatto: l'effetto lume lo da' la sola vignettatura
            // (con la sua grana anti-banding). Un secondo gradiente radiale qui
            // sotto raddoppierebbe gli anelli di quantizzazione sui neri.
            if (background != null && shownBackground != null) {
                // La collocazione e' in caselle: moltiplicata per il lato-casella in
                // pixel segue zoom e pan senza schiacciare l'immagine, che mantiene le
                // proporzioni decise (o quelle scelte stirandola in modifica).
                val dstX = mapOffset.x + (shownBackground.offsetX * cellPx).toFloat()
                val dstY = mapOffset.y + (shownBackground.offsetY * cellPx).toFloat()
                val dstW = (shownBackground.width * cellPx).toFloat()
                val dstH = (shownBackground.height * cellPx).toFloat()
                drawImage(
                    image = background,
                    dstOffset = IntOffset(dstX.roundToInt(), dstY.roundToInt()),
                    dstSize = IntSize(
                        dstW.roundToInt().coerceAtLeast(1),
                        dstH.roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.Medium,
                )
            }

            if (showGrid) {
                // Il fondale col lume e' piu' scuro dei vecchi grigi: la griglia
                // deve emergere un po' di piu' per restare leggibile, quindi sopra
                // uno sfondo la luminosita' scelta viene spinta un filo piu' su.
                val alpha = (gridBrightness * if (background != null) 1.3f else 1f)
                    .coerceIn(0f, 1f)
                // La tinta segue il valore reale del cursore, non l'alpha gia'
                // amplificato dallo sfondo: in questo modo solo il vero 100%
                // raggiunge il bianco puro e resta distinguibile dai valori alti.
                val whiten = ((gridBrightness - 0.6f) / 0.4f).coerceIn(0f, 1f)
                val line = lerp(Palette.Line, Palette.LineBright, whiten).copy(alpha = alpha)
                // Nell'ultimo quarto della corsa compare un alone sottile sotto
                // il tratto principale. Il centro resta nitido, ma al 100% la
                // griglia sembra realmente piu' luminosa anche sulle mappe chiare.
                val glowAlpha = (((gridBrightness - 0.75f) / 0.25f).coerceIn(0f, 1f) * 0.28f)
                val mapRight = mapOffset.x + camera.contentSize.width
                val mapBottom = mapOffset.y + camera.contentSize.height
                if (glowAlpha > 0f) {
                    val glow = Palette.LineBright.copy(alpha = glowAlpha)
                    for (column in camera.visibleColumns(mapOffset)) {
                        val x = mapOffset.x + column * cellPx
                        drawLine(glow, Offset(x, mapOffset.y), Offset(x, mapBottom), strokeWidth = 3f)
                    }
                    for (row in camera.visibleRows(mapOffset)) {
                        val y = mapOffset.y + row * cellPx
                        drawLine(glow, Offset(mapOffset.x, y), Offset(mapRight, y), strokeWidth = 3f)
                    }
                }
                for (column in camera.visibleColumns(mapOffset)) {
                    val x = mapOffset.x + column * cellPx
                    drawLine(line, Offset(x, mapOffset.y), Offset(x, mapBottom), strokeWidth = 1f)
                }
                for (row in camera.visibleRows(mapOffset)) {
                    val y = mapOffset.y + row * cellPx
                    drawLine(line, Offset(mapOffset.x, y), Offset(mapRight, y), strokeWidth = 1f)
                }
            }
        }

        // Raggio di movimento residuo. Per l'attore corrente il resto della mappa
        // si vela d'ombra; per gli altri attivi di un turno simultaneo resta il
        // riquadro leggero, cosi' i veli non si sommano.
        viewModel.activeCombatantIds.forEach { activeId ->
            viewModel.placementOf(activeId)?.let { placement ->
                MovementReach(
                    viewModel = viewModel,
                    placement = placement,
                    cellSize = liveCell,
                    mapOffset = mapOffset,
                    contentSize = camera.contentSize,
                    veiled = activeId == viewModel.activeCombatantId,
                )
            }
        }

        viewModel.abilityRangePreview?.let { preview ->
            viewModel.placementOf(preview.combatantId)?.let { placement ->
                AbilityRangeOverlay(
                    placement = placement,
                    rangeFeet = preview.rangeFeet,
                    feetPerSquare = grid.feetPerSquare(),
                    columns = grid.columns(),
                    rows = grid.rows(),
                    cellSize = liveCell,
                    mapOffset = mapOffset,
                    targeting = preview.targeting,
                )
            }
        }

        map.orderedPlacements().forEach { placement ->
            key(placement.combatantId()) {
                MapToken(viewModel, portraits, placement, liveCell, mapOffset)
            }
        }

        if (dropTarget != null) {
            DropHighlight(dropTarget, liveCell, mapOffset)
        }

        // In modifica mappa, un velo sopra i segnaposti cattura il trascinamento per
        // spostare o stirare lo sfondo. Sta davanti a tutto (tranne la vignettatura),
        // cosi' i gesti non vengono rubati dai token e la camera non scorre.
        if (editingBackground && background != null && shownBackground != null) {
            BackgroundEditOverlay(
                shown = shownBackground,
                cellPx = cellPx,
                mapOffset = mapOffset,
                onDraft = { backgroundDraft = it },
                onCommit = { transform ->
                    viewModel.setMapBackgroundTransform(
                        transform.offsetX, transform.offsetY, transform.width, transform.height,
                    )
                    backgroundDraft = null
                },
            )
        }

        // Cerchio dell'area: segue il mouse mentre si mira, resta fisso sul centro
        // scelto durante la risoluzione manuale. E' l'ampiezza reale dell'incantesimo.
        if (areaTargeting != null || pendingArea != null) {
            val radiusFeet = areaTargeting?.radiusFeet ?: pendingArea!!.radiusFeet
            val centerCell: IntOffset? = pendingArea
                ?.let { IntOffset(it.center.column(), it.center.row()) }
                ?: hoverCell
            Canvas(Modifier.fillMaxSize()) {
                if (centerCell != null && grid.feetPerSquare() > 0) {
                    val radiusPx = (radiusFeet.toFloat() / grid.feetPerSquare()) * cellPx
                    val center = Offset(
                        mapOffset.x + (centerCell.x + 0.5f) * cellPx,
                        mapOffset.y + (centerCell.y + 0.5f) * cellPx,
                    )
                    drawCircle(Palette.Enemy.copy(alpha = 0.16f), radiusPx, center)
                    drawCircle(Palette.Enemy.copy(alpha = 0.9f), radiusPx, center, style = Stroke(width = 2f))
                    drawCircle(Palette.Enemy, 3f, center)
                }
                // In risoluzione manuale, un anello su ogni bersaglio: verde se
                // superato, ambra se fallito (danno pieno).
                pendingArea?.targets?.forEach { choice ->
                    viewModel.placementOf(choice.combatantId)?.let { placement ->
                        val side = placement.squaresPerSide()
                        val cx = mapOffset.x + (placement.origin().column() + side / 2f) * cellPx
                        val cy = mapOffset.y + (placement.origin().row() + side / 2f) * cellPx
                        val ring = if (choice.saved) Palette.Heal else Palette.Crit
                        drawCircle(ring.copy(alpha = 0.95f), side * cellPx * 0.58f, Offset(cx, cy),
                            style = Stroke(width = 2.5f))
                    }
                }
            }
        }

        // Vignettatura sopra tutto: angoli in ombra, luce dove si combatte. Non
        // ha gestori di puntatore, quindi i tocchi la attraversano.
        Vignette(strength = 0.26f)

        if (areaTargeting != null) {
            AreaTargetingBanner(
                targeting = areaTargeting,
                onCancel = { viewModel.cancelAreaTargeting() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            )
        }
        if (pendingArea != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .absoluteOffset {
                        IntOffset(manualCardOffset.x.roundToInt(), manualCardOffset.y.roundToInt())
                    }
                    .onGloballyPositioned {
                        manualCardBounds = it.boundsInParent()
                        manualCardSize = it.size
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                AreaManualCard(
                    viewModel = viewModel,
                    pending = pendingArea,
                    onDrag = { amount ->
                        manualCardOffset = constrainedManualCardOffset(manualCardOffset + amount)
                    },
                )
            }
        }
    }
}

/**
 * Velo di modifica dello sfondo: sposta trascinando il corpo dell'immagine,
 * ridimensiona in proporzione dagli angoli e stira liberamente dai lati. Un solo
 * gesto = un solo passo salvato, quindi annullabile in un colpo.
 */
@Composable
private fun BackgroundEditOverlay(
    shown: MapBackground,
    cellPx: Float,
    mapOffset: Offset,
    onDraft: (MapBackground?) -> Unit,
    onCommit: (MapBackground) -> Unit,
) {
    // Il gesto vive attraverso piu' fotogrammi: legge sempre i valori piu' recenti
    // di camera e collocazione, cosi' uno zoom con la rotella durante la modifica
    // non lo disallinea.
    val shownState = rememberUpdatedState(shown)
    val cellState = rememberUpdatedState(cellPx)
    val offsetState = rememberUpdatedState(mapOffset)
    val onDraftState = rememberUpdatedState(onDraft)
    val onCommitState = rememberUpdatedState(onCommit)

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Bersaglio tattile da 48 dp anche se la maniglia disegnata resta
                // compatta: sul desktop la piu' vicina vince nelle sovrapposizioni.
                val hitRadius = 24.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cell = cellState.value
                    val origin = offsetState.value
                    if (cell <= 0f) return@awaitEachGesture
                    val start = shownState.value

                    val left = origin.x + (start.offsetX * cell).toFloat()
                    val top = origin.y + (start.offsetY * cell).toFloat()
                    val right = left + (start.width * cell).toFloat()
                    val bottom = top + (start.height * cell).toFloat()

                    val centreX = (left + right) / 2f
                    val centreY = (top + bottom) / 2f
                    val handle = listOf(
                        BgHandle.TL to Offset(left, top),
                        BgHandle.T to Offset(centreX, top),
                        BgHandle.TR to Offset(right, top),
                        BgHandle.R to Offset(right, centreY),
                        BgHandle.BR to Offset(right, bottom),
                        BgHandle.B to Offset(centreX, bottom),
                        BgHandle.BL to Offset(left, bottom),
                        BgHandle.L to Offset(left, centreY),
                    )
                        .minByOrNull { (_, point) -> (down.position - point).getDistance() }
                        ?.takeIf { (_, point) -> (down.position - point).getDistance() <= hitRadius }
                        ?.first
                    val insideBody = handle == null &&
                        down.position.x in left..right && down.position.y in top..bottom
                    // Fuori dall'immagine, in modifica, non si fa nulla: si evita di
                    // muovere per sbaglio qualcosa che non si sta neanche toccando.
                    if (handle == null && !insideBody) return@awaitEachGesture

                    var working = start
                    var travelledXSquares = 0.0
                    var travelledYSquares = 0.0
                    onDraftState.value(working)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val move = change.positionChange()
                        val liveCellPx = cellState.value.takeIf { it > 0f } ?: cell
                        // Si accumula gia' in caselle: se la rotella cambia lo zoom
                        // durante il gesto, il tratto percorso prima non viene
                        // reinterpretato con la nuova scala.
                        travelledXSquares += (move.x / liveCellPx).toDouble()
                        travelledYSquares += (move.y / liveCellPx).toDouble()
                        working = if (handle != null) {
                            start.resizedBy(handle, travelledXSquares, travelledYSquares)
                        } else {
                            MapBackground(
                                start.offsetX + travelledXSquares,
                                start.offsetY + travelledYSquares,
                                start.width,
                                start.height,
                            )
                        }
                        onDraftState.value(working)
                        change.consume()
                    }
                    if (working != start) {
                        onCommitState.value(working)
                    } else {
                        // Un semplice tocco non crea un evento fittizio nella
                        // cronologia e non colloca definitivamente il valore di
                        // default calcolato dall'interfaccia.
                        onDraftState.value(null)
                    }
                }
            },
    ) {
        val cell = cellPx
        val left = mapOffset.x + (shown.offsetX * cell).toFloat()
        val top = mapOffset.y + (shown.offsetY * cell).toFloat()
        val w = (shown.width * cell).toFloat()
        val h = (shown.height * cell).toFloat()
        drawRect(
            color = Palette.GoldBright.copy(alpha = 0.95f),
            topLeft = Offset(left, top),
            size = Size(w, h),
            style = Stroke(width = 2f),
        )
        val centreX = left + w / 2f
        val centreY = top + h / 2f
        val cornerRadius = 7.dp.toPx()
        val sideLongRadius = 9.dp.toPx()
        val sideShortRadius = 4.dp.toPx()
        listOf(
            BgHandle.TL to Offset(left, top),
            BgHandle.T to Offset(centreX, top),
            BgHandle.TR to Offset(left + w, top),
            BgHandle.R to Offset(left + w, centreY),
            BgHandle.BR to Offset(left + w, top + h),
            BgHandle.B to Offset(centreX, top + h),
            BgHandle.BL to Offset(left, top + h),
            BgHandle.L to Offset(left, centreY),
        ).forEach { (handle, point) ->
            val radiusX = when (handle) {
                BgHandle.T, BgHandle.B -> sideLongRadius
                BgHandle.L, BgHandle.R -> sideShortRadius
                else -> cornerRadius
            }
            val radiusY = when (handle) {
                BgHandle.T, BgHandle.B -> sideShortRadius
                BgHandle.L, BgHandle.R -> sideLongRadius
                else -> cornerRadius
            }
            drawRect(
                color = Palette.Abyss.copy(alpha = 0.85f),
                topLeft = Offset(point.x - radiusX, point.y - radiusY),
                size = Size(radiusX * 2, radiusY * 2),
            )
            drawRect(
                color = Palette.GoldBright,
                topLeft = Offset(point.x - radiusX, point.y - radiusY),
                size = Size(radiusX * 2, radiusY * 2),
                style = Stroke(width = 2f),
            )
        }
    }
}

/**
 * Riquadro sotto il puntatore mentre si trascina un personaggio dalle barre: mostra
 * la casella in cui il segnaposto verra' collocato al rilascio.
 */
@Composable
private fun DropHighlight(dropTarget: TokenPlacementDrag, cellSize: Dp, mapOffset: Offset) {
    if (dropTarget.activeId == null) return
    val cell = dropTarget.overCell ?: return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    Box(
        Modifier
            .absoluteOffset {
                IntOffset(
                    (mapOffset.x + cell.x * cellPx).roundToInt(),
                    (mapOffset.y + cell.y * cellPx).roundToInt(),
                )
            }
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(cellSize)
            .background(Palette.Gold.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
            .border(2.dp, Palette.GoldBright, RoundedCornerShape(4.dp)),
    )
}

/**
 * Un tocco su una casella libera muove il combattente attivo; su un segnaposto lo
 * ispeziona. Se prima e' stata scelta una capacita', quel medesimo tocco conferma
 * invece il bersaglio esplicito.
 */
private fun onCellTapped(viewModel: BattleViewModel, column: Int, row: Int) {
    // Un tocco proprio sul bordo destro o inferiore cade fuori griglia per un pixel:
    // va ignorato, non trasformato in un errore di regola mostrato al tavolo.
    val grid = viewModel.battleMap.grid()
    if (column < 0 || row < 0 || column >= grid.columns() || row >= grid.rows()) return

    // Mentre si mira un'area, un clic la fa detonare qui invece di spostare o
    // selezionare: e' la conferma del bersaglio.
    if (viewModel.areaTargeting != null) {
        viewModel.resolveAreaAt(column, row)
        return
    }

    val occupant = viewModel.occupantAt(column, row)
    if (viewModel.singleTargeting != null) {
        if (occupant == null) {
            viewModel.showMessage("Scegli una creatura come bersaglio, oppure annulla la mira.")
        } else {
            viewModel.onCombatantClicked(occupant)
        }
        return
    }
    if (occupant != null) {
        viewModel.onCombatantClicked(occupant)
        return
    }
    val active = viewModel.activeCombatantId ?: return
    if (viewModel.editMode) {
        viewModel.reposition(active, column, row)
    } else if (viewModel.inspectedCombatantId == active) {
        viewModel.move(active, column, row)
    } else {
        viewModel.showMessage("Stai consultando un altro combattente: seleziona quello di turno per muoverlo.")
    }
}

/**
 * Evidenzia le caselle che possono contenere il bersaglio o il centro dell'area.
 *
 * Il motore misura le distanze di griglia con Chebyshev (una diagonale vale una
 * casella), quindi la portata corretta e' un rettangolo espanso attorno all'intera
 * sagoma del combattente, non un cerchio euclideo che escluderebbe gli angoli.
 */
@Composable
private fun AbilityRangeOverlay(
    placement: TokenPlacement,
    rangeFeet: Int,
    feetPerSquare: Int,
    columns: Int,
    rows: Int,
    cellSize: Dp,
    mapOffset: Offset,
    targeting: Boolean,
) {
    if (feetPerSquare <= 0 || columns <= 0 || rows <= 0 || rangeFeet < 0) return
    val rangeSquares = rangeFeet / feetPerSquare
    val origin = placement.origin()
    val startColumn = (origin.column() - rangeSquares).coerceAtLeast(0)
    val startRow = (origin.row() - rangeSquares).coerceAtLeast(0)
    val endColumn = (origin.column() + placement.squaresPerSide() + rangeSquares).coerceAtMost(columns)
    val endRow = (origin.row() + placement.squaresPerSide() + rangeSquares).coerceAtMost(rows)
    if (endColumn <= startColumn || endRow <= startRow) return

    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val tint = if (targeting) Palette.GoldBright else Palette.Party
    Canvas(Modifier.fillMaxSize()) {
        val topLeft = Offset(
            mapOffset.x + startColumn * cellPx,
            mapOffset.y + startRow * cellPx,
        )
        val rangeSize = Size(
            (endColumn - startColumn) * cellPx,
            (endRow - startRow) * cellPx,
        )
        drawRect(
            color = tint.copy(alpha = if (targeting) 0.16f else 0.10f),
            topLeft = topLeft,
            size = rangeSize,
        )
        drawRect(
            color = tint.copy(alpha = if (targeting) 0.95f else 0.72f),
            topLeft = topLeft,
            size = rangeSize,
            style = Stroke(width = if (targeting) 2.5.dp.toPx() else 1.5.dp.toPx()),
        )
    }
}

/**
 * Alone che mostra fin dove arriva il movimento rimasto.
 *
 * E' un quadrato perche' la griglia conta la diagonale come una casella: il raggio
 * di Chebyshev e' un anello quadrato, non un cerchio.
 *
 * Con `veiled` — l'attore corrente — non si evidenzia il raggio: si oscura tutto
 * il resto della mappa, cosi' cio' che e' raggiungibile resta alla piena luce del
 * lume e cio' che non lo e' cade in ombra. Per gli altri combattenti attivi di un
 * turno simultaneo resta il riquadro leggero, che puo' sovrapporsi senza sommare
 * oscurita'.
 */
@Composable
private fun MovementReach(
    viewModel: BattleViewModel,
    placement: TokenPlacement,
    cellSize: Dp,
    mapOffset: Offset,
    contentSize: Size,
    veiled: Boolean,
) {
    val squares = viewModel.movementSquaresRemaining(placement.combatantId())
    if (squares <= 0) return
    val grid = viewModel.battleMap.grid()

    val origin = placement.origin()
    val startColumn = (origin.column() - squares).coerceAtLeast(0)
    val startRow = (origin.row() - squares).coerceAtLeast(0)
    val endColumn = (origin.column() + placement.squaresPerSide() + squares).coerceAtMost(grid.columns())
    val endRow = (origin.row() + placement.squaresPerSide() + squares).coerceAtMost(grid.rows())
    if (endColumn <= startColumn || endRow <= startRow) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }

    if (veiled) {
        Canvas(Modifier.fillMaxSize()) {
            val reachLeft = mapOffset.x + startColumn * cellPx
            val reachTop = mapOffset.y + startRow * cellPx
            val reachRight = mapOffset.x + endColumn * cellPx
            val reachBottom = mapOffset.y + endRow * cellPx
            val mapLeft = mapOffset.x
            val mapTop = mapOffset.y
            val mapRight = mapOffset.x + contentSize.width
            val mapBottom = mapOffset.y + contentSize.height
            val scrim = Palette.Abyss.copy(alpha = 0.45f)

            // Quattro fasce attorno al raggio: sopra, sotto, sinistra, destra.
            if (reachTop > mapTop) {
                drawRect(scrim, Offset(mapLeft, mapTop), Size(mapRight - mapLeft, reachTop - mapTop))
            }
            if (mapBottom > reachBottom) {
                drawRect(scrim, Offset(mapLeft, reachBottom), Size(mapRight - mapLeft, mapBottom - reachBottom))
            }
            if (reachLeft > mapLeft) {
                drawRect(scrim, Offset(mapLeft, reachTop), Size(reachLeft - mapLeft, reachBottom - reachTop))
            }
            if (mapRight > reachRight) {
                drawRect(scrim, Offset(reachRight, reachTop), Size(mapRight - reachRight, reachBottom - reachTop))
            }

            drawRect(
                color = Palette.Party.copy(alpha = 0.5f),
                topLeft = Offset(reachLeft, reachTop),
                size = Size(reachRight - reachLeft, reachBottom - reachTop),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        return
    }

    Box(
        Modifier
            .absoluteOffset {
                IntOffset(
                    (mapOffset.x + startColumn * cellPx).roundToInt(),
                    (mapOffset.y + startRow * cellPx).roundToInt(),
                )
            }
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .width(cellSize * (endColumn - startColumn))
            .height(cellSize * (endRow - startRow))
            .background(Palette.Party.copy(alpha = 0.07f), RoundedCornerShape(4.dp))
            .border(1.dp, Palette.Party.copy(alpha = 0.30f), RoundedCornerShape(4.dp)),
    )
}

/**
 * Segnaposto rotondo di un combattente.
 *
 * Il diametro segue l'ingombro, quindi una creatura Grande e' visibilmente il
 * doppio di una Media. Attorno corre l'anello dei punti ferita; dentro c'e'
 * l'immagine caricata, o le iniziali quando non ce n'e' una.
 */
@Composable
private fun MapToken(
    viewModel: BattleViewModel,
    portraits: PortraitRepository,
    placement: TokenPlacement,
    cellSize: Dp,
    mapOffset: Offset,
) {
    val id = placement.combatantId()
    val combatant = viewModel.combatant(id) ?: return
    val snapshot = combatant.snapshot()
    val faction = if (viewModel.isParty(id)) Faction.PARTY else Faction.ENEMY
    val active = viewModel.isActive(id)
    val targeted = viewModel.selectedTargetId == id
    val inspected = viewModel.inspectedCombatantId == id
    val defeated = combatant.defeated()

    val side = cellSize * placement.squaresPerSide()
    // Sotto una certa scala il token continua a occupare il corretto spazio della
    // griglia, ma conserva un piccolo badge visivo centrato. Se anche il badge si
    // riducesse fino a 1 dp, Compose comprimerebbe la riga di testo e le iniziali
    // scivolerebbero fuori dal cerchio fino a sparire.
    val compactBadge = side * 0.76f < MIN_TOKEN_BADGE
    val badgeSide = maxOf(side * 0.76f, MIN_TOKEN_BADGE)
    val labelSize = maxOf(side.value * 0.26f, MIN_TOKEN_LABEL_SP).sp
    // La posizione anima in coordinate di casella, non in pixel. Un movimento vero
    // (cambia colonna/riga) scorre dolcemente; uno zoom (cambia solo `cellSize`)
    // riallinea invece il segnaposto nello stesso frame della griglia. Animare il
    // prodotto `cellSize * coordinata` lascerebbe il token temporaneamente indietro
    // rispetto alla mappa durante ogni scatto di rotellina.
    val animColumn by animateFloatAsState(
        targetValue = placement.origin().column().toFloat(),
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "tokenColumn",
    )
    val animRow by animateFloatAsState(
        targetValue = placement.origin().row().toFloat(),
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "tokenRow",
    )
    val ratio by animateFloatAsState(
        targetValue = (combatant.currentHitPoints().toFloat() / snapshot.maxHitPoints().coerceAtLeast(1))
            .coerceIn(0f, 1f),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "tokenHitPoints",
    )
    val pulse = if (active && !defeated) {
        val animated by rememberInfiniteTransition(label = "tokenPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "tokenPulseValue",
        )
        animated
    } else {
        0f
    }

    val accent = if (defeated) Palette.TextFaint else faction.color
    val ring = if (defeated) Palette.TextFaint else healthColor(combatant.currentHitPoints(), snapshot.maxHitPoints())
    // Lampo del critico: finche' il numero dorato del colpo fluttua sul token,
    // l'anello si accende. Sparisce da solo quando il numero scade.
    val critFlash = viewModel.floating[id].orEmpty().any { it.kind == FloatKind.CRIT }
    val portrait = portraits.rememberPortrait(snapshot.definitionId())
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    var dragOffset by remember(id, placement.origin(), viewModel.editMode) { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .absoluteOffset {
                IntOffset(
                    (mapOffset.x + animColumn * cellPx + dragOffset.x).roundToInt(),
                    (mapOffset.y + animRow * cellPx + dragOffset.y).roundToInt(),
                )
            }
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(side)
            .semantics {
                role = Role.Button
                contentDescription = snapshot.name()
                stateDescription = buildString {
                    append("${combatant.currentHitPoints()} su ${snapshot.maxHitPoints()} punti ferita")
                    if (active) append(", turno attivo")
                    if (targeted) append(", bersaglio selezionato")
                    if (inspected) append(", scheda in esame")
                    if (defeated) append(", fuori combattimento")
                }
            }
            // Modalità modifica: qualunque segnaposto si trascina liberamente per
            // comporre la scena. Modalità normale: solo il combattente di turno si
            // trascina, e lo spostamento vero passa dal motore, quindi consuma il
            // budget e non supera il raggio percorribile. Gli altri segnaposti non
            // si trascinano: il clic li ispeziona, oppure li conferma come bersaglio
            // quando una capacita' e' gia' in fase di mira.
            .pointerInput(
                id,
                placement.origin(),
                cellSize,
                viewModel.editMode,
                active,
                inspected,
                viewModel.singleTargeting,
                viewModel.areaTargeting,
            ) {
                if (viewModel.singleTargeting != null || viewModel.areaTargeting != null) return@pointerInput
                if (!viewModel.editMode && (!active || !inspected)) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        dragOffset = Offset.Zero
                        if (viewModel.editMode) viewModel.inspectCombatant(id)
                    },
                    onDragCancel = { dragOffset = Offset.Zero },
                    onDragEnd = {
                        val grid = viewModel.battleMap.grid()
                        val origin = placement.origin()
                        val squares = placement.squaresPerSide()
                        if (viewModel.editMode) {
                            // Collocazione libera: riportata dentro la griglia tenendo
                            // conto dell'ingombro, cosi' trascinare oltre il bordo si
                            // ferma all'ultima casella valida invece di fallire.
                            val column = (origin.column() + (dragOffset.x / cellPx).roundToInt())
                                .coerceIn(0, (grid.columns() - squares).coerceAtLeast(0))
                            val row = (origin.row() + (dragOffset.y / cellPx).roundToInt())
                                .coerceIn(0, (grid.rows() - squares).coerceAtLeast(0))
                            dragOffset = Offset.Zero
                            if (column != origin.column() || row != origin.row()) {
                                viewModel.reposition(id, column, row)
                            }
                        } else {
                            // Movimento del turno: la destinazione e' limitata al raggio
                            // percorribile (distanza di Chebyshev) prima di finire dentro
                            // la griglia, cosi' il motore non la rifiuta per budget. Con
                            // il residuo a zero il segnaposto resta dov'e'.
                            val reach = viewModel.movementSquaresRemaining(id)
                            val column = (origin.column() + (dragOffset.x / cellPx).roundToInt().coerceIn(-reach, reach))
                                .coerceIn(0, (grid.columns() - squares).coerceAtLeast(0))
                            val row = (origin.row() + (dragOffset.y / cellPx).roundToInt().coerceIn(-reach, reach))
                                .coerceIn(0, (grid.rows() - squares).coerceAtLeast(0))
                            dragOffset = Offset.Zero
                            if (column != origin.column() || row != origin.row()) {
                                viewModel.move(id, column, row)
                            }
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val next = dragOffset + amount
                        dragOffset = if (viewModel.editMode) {
                            next
                        } else {
                            // Non si lascia trascinare oltre il raggio percorribile:
                            // il segnaposto si ferma al bordo dell'alone di movimento.
                            val reachPx = viewModel.movementSquaresRemaining(id) * cellPx
                            Offset(next.x.coerceIn(-reachPx, reachPx), next.y.coerceIn(-reachPx, reachPx))
                        }
                    },
                )
            }
            .clickable(role = Role.Button) {
                if (viewModel.areaTargeting != null) {
                    viewModel.resolveAreaAt(placement.origin().column(), placement.origin().row())
                } else {
                    viewModel.onCombatantClicked(id)
                }
            }
            .alpha(if (defeated) 0.5f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        // L'immagine viene ritagliata a cerchio dentro l'anello.
        Box(
            Modifier
                // `requiredSize` lascia che il badge minimo ecceda il piccolo box
                // della casella; Box lo centra e non lo ritaglia.
                .requiredSize(badgeSide)
                .clip(CircleShape)
                .background(Palette.SurfaceHigh)
                .then(
                    if (compactBadge) {
                        Modifier.border(1.dp, accent.copy(alpha = 0.9f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (portrait != null) {
                Image(
                    bitmap = portrait,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (portrait == null) {
            Text(
                text = if (defeated) "✕" else initials(snapshot.name()),
                color = if (defeated) Palette.TextFaint else Palette.Text,
                fontWeight = FontWeight.Black,
                fontSize = labelSize,
                lineHeight = labelSize,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                // Il testo e' sopra il badge, non dentro il suo `clip(CircleShape)`.
                // La misura senza limiti evita che il box tattico da 1–2 dp ne
                // comprima la baseline o tagli la parte bassa dei caratteri.
                modifier = Modifier.wrapContentSize(Alignment.Center, unbounded = true),
            )
        }

        Canvas(Modifier.size(side)) {
            val stroke = size.minDimension * 0.085f
            val inset = stroke * 1.1f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            if (active && !defeated) {
                // Alone caldo sotto il combattente di turno: un piccolo cerchio di
                // luce da lume, piu' intenso al centro, che pulsa piano.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Palette.Gold.copy(alpha = 0.26f + 0.16f * pulse),
                            Palette.Gold.copy(alpha = 0f),
                        ),
                        center = center,
                        radius = size.minDimension / 2f,
                    ),
                    radius = size.minDimension / 2f,
                )
            }
            drawArc(
                color = Palette.Abyss,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (ratio > 0f) {
                drawArc(
                    color = ring,
                    startAngle = -90f, sweepAngle = 360f * ratio, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            if (critFlash) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Palette.Crit.copy(alpha = 0.5f), Palette.Crit.copy(alpha = 0f)),
                        center = center,
                        radius = size.minDimension * 0.62f,
                    ),
                    radius = size.minDimension * 0.62f,
                )
            }
            drawCircle(
                color = when {
                    critFlash -> Palette.Crit
                    active -> Palette.GoldBright
                    targeted -> accent
                    inspected -> Palette.Text
                    else -> accent.copy(alpha = 0.5f)
                },
                radius = size.minDimension / 2f - inset,
                style = Stroke(width = if (active || targeted || inspected || critFlash) 2.2f else 1.2f),
            )
        }

        viewModel.floating[id].orEmpty().forEach { number ->
            FloatingNumberView(number, onExpired = { viewModel.expire(id, it) })
        }
    }
}

/** Minimi solo visivi: posizione e ingombro tattico continuano a seguire la griglia. */
private val MIN_TOKEN_BADGE = 16.dp
private const val MIN_TOKEN_LABEL_SP = 9f

/** Invito alla configurazione quando la mappa non esiste ancora. */
@Composable
private fun MapNotConfigured(viewModel: BattleViewModel, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().background(Palette.Abyss),
        contentAlignment = Alignment.Center,
    ) {
        // Stesso lume della mappa vera, con la grana che evita gli anelli.
        Vignette(strength = 0.24f)
        val panelShape = RoundedCornerShape(12.dp)
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .padding(24.dp)
                .panelBrush(panelShape)
                .border(1.dp, Palette.Bronze.copy(alpha = 0.6f), panelShape)
                .ornateFrame(accent = Palette.Gold, alpha = 0.6f)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "▦",
                color = Palette.GoldDim,
                fontSize = 46.sp,
            )
            Text(
                text = "Nessuna mappa configurata",
                color = Palette.Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            OrnateDivider(color = Palette.GoldDim)
            Text(
                text = "Senza griglia l'incontro resta una simulazione astratta: " +
                    "portate e distanze le dichiara il tavolo, non il motore.",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton("Griglia 20 × 15", accent = Palette.Gold, onClick = {
                    viewModel.configureMap(20, 15, 5)
                })
                GameButton("Griglia 40 × 30", accent = Palette.TextMuted, onClick = {
                    viewModel.configureMap(40, 30, 5)
                })
            }
        }
    }
}

/** Le otto maniglie afferrabili dello sfondo in modifica. */
internal enum class BgHandle { TL, T, TR, R, BR, B, BL, L }

/** Lato minimo dello sfondo, in caselle: non si puo' stirare fino a farlo sparire. */
private const val MIN_BG_SQUARES = 0.5

/**
 * Collocazione "contain": l'immagine intera, centrata, senza deformazioni.
 *
 * Si sceglie il lato vincolante confrontando le proporzioni dell'immagine con
 * quelle della griglia, cosi' l'altro lato resta piu' corto e nulla viene stirato.
 */
private fun containBackground(
    imageWidth: Int,
    imageHeight: Int,
    columns: Int,
    rows: Int,
): MapBackground {
    if (imageWidth <= 0 || imageHeight <= 0 || columns <= 0 || rows <= 0) {
        return MapBackground(0.0, 0.0, columns.coerceAtLeast(1).toDouble(), rows.coerceAtLeast(1).toDouble())
    }
    val imageAspect = imageWidth.toDouble() / imageHeight
    val gridAspect = columns.toDouble() / rows
    val width: Double
    val height: Double
    if (imageAspect > gridAspect) {
        width = columns.toDouble()
        height = columns / imageAspect
    } else {
        height = rows.toDouble()
        width = rows * imageAspect
    }
    return MapBackground((columns - width) / 2.0, (rows - height) / 2.0, width, height)
}

/**
 * Nuovo rettangolo dopo aver trascinato una maniglia.
 *
 * Dagli angoli il rapporto larghezza/altezza corrente rimane invariato e l'angolo
 * opposto resta fermo. Le maniglie centrali cambiano invece un solo asse, cosi'
 * permettono lo stretching intenzionale. In entrambi i casi un lato minimo evita
 * che l'immagine collassi o si ribalti oltre la propria ancora.
 */
internal fun MapBackground.resizedBy(
    handle: BgHandle,
    dxSquares: Double,
    dySquares: Double,
): MapBackground {
    val left = offsetX
    val top = offsetY
    val right = offsetX + width
    val bottom = offsetY + height
    return when (handle) {
        BgHandle.L -> {
            val newLeft = (left + dxSquares).coerceAtMost(right - MIN_BG_SQUARES)
            MapBackground(newLeft, top, right - newLeft, height)
        }
        BgHandle.R -> {
            val newWidth = (width + dxSquares).coerceAtLeast(MIN_BG_SQUARES)
            MapBackground(left, top, newWidth, height)
        }
        BgHandle.T -> {
            val newTop = (top + dySquares).coerceAtMost(bottom - MIN_BG_SQUARES)
            MapBackground(left, newTop, width, bottom - newTop)
        }
        BgHandle.B -> {
            val newHeight = (height + dySquares).coerceAtLeast(MIN_BG_SQUARES)
            MapBackground(left, top, width, newHeight)
        }
        BgHandle.TL, BgHandle.TR, BgHandle.BR, BgHandle.BL -> {
            val fromLeft = handle == BgHandle.TL || handle == BgHandle.BL
            val fromTop = handle == BgHandle.TL || handle == BgHandle.TR
            val horizontalDirection = if (fromLeft) -1.0 else 1.0
            val verticalDirection = if (fromTop) -1.0 else 1.0
            val horizontalScale = 1.0 + dxSquares * horizontalDirection / width
            val verticalScale = 1.0 + dySquares * verticalDirection / height
            // L'asse mosso di piu', in termini percentuali, guida il gesto. L'altro
            // segue la proporzione: il risultato e' prevedibile anche trascinando
            // l'angolo quasi soltanto in orizzontale o quasi soltanto in verticale.
            val requestedScale =
                if (abs(horizontalScale - 1.0) >= abs(verticalScale - 1.0)) {
                    horizontalScale
                } else {
                    verticalScale
                }
            val minimumScale = maxOf(MIN_BG_SQUARES / width, MIN_BG_SQUARES / height)
            val scale = requestedScale.coerceAtLeast(minimumScale)
            val newWidth = width * scale
            val newHeight = height * scale
            val anchorX = if (fromLeft) right else left
            val anchorY = if (fromTop) bottom else top
            MapBackground(
                if (fromLeft) anchorX - newWidth else anchorX,
                if (fromTop) anchorY - newHeight else anchorY,
                newWidth,
                newHeight,
            )
        }
    }
}

/** Fascia in cima alla mappa mentre si mira un'area: cosa, quanto ampia e come annullare. */
@Composable
private fun AreaTargetingBanner(
    targeting: AreaTargeting,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .widthIn(max = 540.dp)
            .clip(shape)
            .panelBrush(shape)
            .border(1.dp, Palette.Enemy.copy(alpha = 0.6f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                text = "Mira · ${targeting.name}",
                color = Palette.Text,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Area ${feetWithMetres(targeting.radiusFeet)} · gittata ${feetWithMetres(targeting.rangeFeet)}" +
                    " · clicca sulla mappa per centrare",
                color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        GameButton("Annulla", accent = Palette.TextMuted, dense = true, onClick = onCancel)
    }
}

/** Risoluzione manuale dell'area: il tavolo segna chi supera il TS, poi applica i danni. */
@Composable
private fun AreaManualCard(
    viewModel: BattleViewModel,
    pending: PendingArea,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(shape)
            .panelBrush(shape)
            .border(1.dp, Palette.Bronze.copy(alpha = 0.7f), shape)
            .ornateFrame(accent = Palette.Gold, alpha = 0.5f)
            // Rende tutta la superficie un vero bersaglio di input: i token
            // eventualmente coperti dal pannello non entrano nell'hit test.
            // Il gestore della mappa esclude inoltre questi bounds da tap e zoom.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        onDrag(amount)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = pending.spellName,
                    color = Palette.Text,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Tiri salvezza · CD ${pending.saveDc} · tocca un nome per cambiarne l'esito",
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Chip("${pending.targets.size} nell'area", Palette.Gold)
            Text(
                text = "⋮⋮",
                color = Palette.TextMuted,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    contentDescription = "Trascina per spostare il pannello"
                },
            )
        }
        OrnateDivider(color = Palette.GoldDim)
        if (pending.targets.isEmpty()) {
            Text(
                text = "Nessuna creatura nell'area.",
                color = Palette.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Column(
                Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                pending.targets.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(Palette.Night, RoundedCornerShape(7.dp))
                            .border(1.dp, Palette.Line, RoundedCornerShape(7.dp))
                            .clickable { viewModel.toggleAreaSave(choice.combatantId) }
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = choice.name,
                            color = Palette.Text,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Chip(
                            if (choice.saved) "TS superato · metà" else "TS fallito · pieno",
                            if (choice.saved) Palette.Heal else Palette.Crit,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameButton("Annulla", accent = Palette.TextMuted, dense = true, onClick = { viewModel.cancelAreaTargeting() })
            Box(Modifier.weight(1f))
            if (pending.targets.isEmpty()) {
                GameButton("Chiudi", accent = Palette.TextMuted, dense = true, onClick = { viewModel.cancelAreaTargeting() })
            } else {
                GameButton("Applica", accent = Palette.Enemy, dense = true, primary = true, onClick = { viewModel.applyPendingArea() })
            }
        }
    }
}
