package app.d6d.ui.battle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.board.AreaTemplate
import app.d6d.board.BoardBounds
import app.d6d.board.BoardDocument
import app.d6d.board.BoardObject
import app.d6d.board.BoardLimits
import app.d6d.board.FogMask
import app.d6d.board.FloorMask
import app.d6d.board.GridPoint
import app.d6d.board.InkStroke
import app.d6d.board.Label
import app.d6d.board.Measurement
import app.d6d.board.SceneToken
import app.d6d.board.StaticStamp
import app.d6d.board.StampKind
import app.d6d.board.TemplateShape
import app.d6d.board.WallMask
import app.d6d.domain.space.GridPosition
import app.d6d.domain.space.MapGrid
import app.d6d.domain.space.TokenPlacement
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.ui.board.BoardController
import app.d6d.ui.board.BoardVisionField
import app.d6d.ui.board.VisionTier
import app.d6d.ui.board.forEachFogRun
import app.d6d.ui.board.BoardTool
import app.d6d.ui.board.BoardToolState
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.i18n.strings
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.images.rememberPortrait
import app.d6d.ui.components.initials
import app.d6d.ui.components.DialogTitle
import app.d6d.ui.settings.LocalAppPreferences
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.OnfallTheme
import app.d6d.ui.theme.Palette
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Disegno persistente del Lucido, sotto i token. */
@Composable
internal fun BoardContentLayer(
    board: BoardController,
    grid: MapGrid,
    camera: MapViewportGeometry,
    mapOffset: Offset,
    cellPx: Float,
    showMasterFog: Boolean,
    playerPreview: Boolean,
    vision: BoardVisionField,
    portraits: PortraitRepository,
    modifier: Modifier = Modifier,
) {
    val document = board.document
    val visible = camera.visibleWorld(mapOffset)
    val layers = document.layers()
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            if (layers.floorsVisible()) {
                drawFloors(document.floors(), camera, mapOffset, cellPx)
            }
            document.objects().forEach { item ->
                val bounds = item.bounds(grid.feetPerSquare())
                if (!bounds.intersects(visible)) return@forEach
                if (playerPreview && !vision.showsToPlayers(bounds)) return@forEach
                when (item) {
                    is StaticStamp -> if (layers.stampsVisible()) drawStamp(item, mapOffset, cellPx)
                    is Label, is SceneToken -> Unit
                    else -> if (layers.annotationsVisible()) drawBoardObject(item, grid.feetPerSquare(), mapOffset, cellPx)
                }
            }
            if (layers.wallsVisible()) {
                drawWalls(document.walls(), camera, mapOffset, cellPx)
            }
            if (showMasterFog && layers.fogVisible()) {
                if (!document.vision().dynamic()) {
                    // La maschera dipinta del master resta sotto le pedine. La vista
                    // dinamica viene invece stesa in cima alla scena, uguale per
                    // master e giocatori, da BoardVisionFogLayer.
                    drawFog(document.fog(), camera, mapOffset, cellPx, Palette.Abyss.copy(alpha = 0.32f))
                }
            }
        }

        if (layers.annotationsVisible()) {
            document.objects().filterIsInstance<Label>().forEach { label ->
                val bounds = labelVisualBounds(label, cellPx)
                if (!bounds.intersects(visible)) return@forEach
                if (playerPreview && !vision.showsToPlayers(bounds)) return@forEach
                val point = camera.screenAt(label.position(), mapOffset)
                Text(
                    text = label.text(),
                    color = Color(label.colorArgb()),
                    style = OnfallTheme.typography.bodyEmphasis,
                    fontSize = label.textSizeSp().toFloat().sp,
                    modifier = Modifier
                        .absoluteOffset { IntOffset(point.x.roundToInt(), point.y.roundToInt()) }
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .graphicsLayer(rotationZ = label.rotationDegrees().toFloat())
                        .background(Palette.Abyss.copy(alpha = 0.52f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            document.objects().filterIsInstance<Measurement>().forEach { measurement ->
                val bounds = measurement.bounds(grid.feetPerSquare())
                if (!bounds.intersects(visible)) return@forEach
                if (playerPreview && !vision.showsToPlayers(bounds)) return@forEach
                MeasurementTotalLabel(measurement.points(), grid.feetPerSquare(), camera, mapOffset)
            }
        }

        if (layers.sceneTokensVisible()) {
            visibleSceneTokens(document, playerPreview).forEach { token ->
                val bounds = token.bounds(grid.feetPerSquare())
                if (!bounds.intersects(visible)) return@forEach
                // Con una resa traslucida una pedina coperta si intravedrebbe: ciò
                // che gli occhi correnti non vedono non si disegna, sia per il
                // master sia per i giocatori. La memoria riguarda il terreno, non
                // chi ci stava sopra.
                if (vision.active && !vision.sees(bounds)) return@forEach
                key(token.id()) {
                    SceneTokenView(token, portraits, camera, mapOffset, cellPx, playerPreview)
                }
            }
        }
    }
}

@Composable
private fun SceneTokenView(
    token: SceneToken,
    portraits: PortraitRepository,
    camera: MapViewportGeometry,
    mapOffset: Offset,
    cellPx: Float,
    playerPreview: Boolean,
) {
    val density = LocalDensity.current
    val center = camera.screenAt(token.position(), mapOffset)
    val sidePx = (token.sizeSquares() * cellPx).toFloat().coerceAtLeast(4f)
    val sideDp = with(density) { sidePx.toDp() }
    val image = portraits.rememberPortrait(token.imageAssetId())
    val color = Color(token.colorArgb())
    val boardWords = strings.board
    val category = token.category().label(boardWords)
    val accessibilityDescription = boardWords.sceneTokenAccessibility(
        name = token.name(),
        category = category,
        collectible = token.lootable() && !playerPreview,
        quantity = token.lootQuantity(),
    )
    Column(
        Modifier
            .absoluteOffset {
                IntOffset((center.x - sidePx / 2f).roundToInt(), (center.y - sidePx / 2f).roundToInt())
            }
            .width(sideDp)
            .semantics { contentDescription = accessibilityDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(sideDp)
                .graphicsLayer(rotationZ = token.rotationDegrees().toFloat())
                .clip(CircleShape)
                .background(color.copy(alpha = 0.42f))
                .border(
                    width = if (!token.visibleToPlayers() && !playerPreview) 3.dp else 2.dp,
                    color = if (!token.visibleToPlayers() && !playerPreview) Palette.Enemy else color,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    initials(token.name()),
                    color = Palette.Text,
                    fontSize = (sideDp.value * 0.28f).coerceIn(7f, 34f).sp,
                    style = OnfallTheme.typography.tokenInitials,
                )
            }
            if (sideDp >= 34.dp) {
                Text(
                    category.take(3).uppercase(),
                    color = Palette.Text,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Palette.Abyss.copy(alpha = 0.78f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 2.dp),
                )
            }
            if (token.lootable() && !playerPreview) {
                Text(
                    if (token.lootQuantity() > 1) "◆×${token.lootQuantity()}" else "◆",
                    color = Palette.GoldBright,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Palette.Abyss.copy(alpha = 0.82f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 2.dp),
                )
            }
        }
        if (token.showLabel() && sideDp >= 18.dp) {
            Text(
                token.name(),
                color = Palette.Text,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(Palette.Abyss.copy(alpha = 0.82f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * Velo finale sopra pedine e annotazioni.
 *
 * La vista dinamica usa lo stesso profilo per master e giocatori; cambiano solo
 * gli occhi che hanno prodotto [vision]. In modalità dipinta questo livello serve
 * invece soltanto all'anteprima giocatori, come prima.
 */
@Composable
internal fun BoardVisionFogLayer(
    board: BoardController,
    playerPreview: Boolean,
    camera: MapViewportGeometry,
    mapOffset: Offset,
    cellPx: Float,
    vision: BoardVisionField,
    modifier: Modifier = Modifier,
) {
    if (!board.document.layers().fogVisible()) return
    Canvas(modifier) {
        if (vision.active) {
            val colors = vision.presentation.fogColors() ?: return@Canvas
            drawVisionFog(
                vision, camera, mapOffset, cellPx,
                exploredColor = colors.explored,
                unseenColor = colors.unseen,
            )
        } else if (playerPreview && !board.document.vision().dynamic()) {
            drawFog(board.document.fog(), camera, mapOffset, cellPx, Palette.Abyss.copy(alpha = 0.97f))
        }
    }
}

@Composable
private fun MeasurementTotalLabel(
    points: List<GridPoint>,
    feetPerSquare: Int,
    camera: MapViewportGeometry,
    mapOffset: Offset,
) {
    if (points.size < 2) return
    val feet = measurementFeet(points, feetPerSquare)
    points.zipWithNext().forEach { (first, second) ->
        val partial = measurementFeet(listOf(first, second), feetPerSquare)
        val midpoint = GridPoint((first.x() + second.x()) / 2.0, (first.y() + second.y()) / 2.0)
        val screen = camera.screenAt(midpoint, mapOffset)
        Text(
            text = distanceLabel(partial, currentLanguage),
            color = Palette.Gold,
            style = OnfallTheme.typography.numberCompact,
            modifier = Modifier
                .absoluteOffset { IntOffset(screen.x.roundToInt(), screen.y.roundToInt()) }
                .background(Palette.Abyss.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
                .padding(horizontal = 3.dp, vertical = 1.dp),
        )
    }
    val end = camera.screenAt(points.last(), mapOffset)
    Text(
        text = if (points.size > 2) "Σ ${distanceLabel(feet, currentLanguage)}" else distanceLabel(feet, currentLanguage),
        color = Palette.GoldBright,
        style = OnfallTheme.typography.numberCompact,
        modifier = Modifier
            .absoluteOffset { IntOffset(end.x.roundToInt() + 6, end.y.roundToInt() + 4) }
            .background(Palette.Abyss.copy(alpha = 0.82f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/** Velo di input sopra i token: esiste soltanto quando uno strumento lo richiede. */
@Composable
internal fun BoardInteractionOverlay(
    viewModel: BattleViewModel,
    board: BoardController,
    tools: BoardToolState,
    grid: MapGrid,
    camera: MapViewportGeometry,
    mapOffset: Offset,
    cellPx: Float,
    modifier: Modifier = Modifier,
) {
    val preferences = LocalAppPreferences.current
    val cameraState = rememberUpdatedState(camera)
    val offsetState = rememberUpdatedState(mapOffset)
    val boardState = rememberUpdatedState(board.document)
    val pings = remember { mutableStateListOf<PingMark>() }
    var draftInk by remember { mutableStateOf<List<GridPoint>>(emptyList()) }
    var draftTemplate by remember { mutableStateOf<Pair<GridPoint, GridPoint>?>(null) }
    var draftFog by remember { mutableStateOf<FogMask?>(null) }
    var draftFloors by remember { mutableStateOf<FloorMask?>(null) }
    var draftWalls by remember { mutableStateOf<WallMask?>(null) }
    var draftMoved by remember { mutableStateOf<BoardObject?>(null) }
    var labelPoint by remember { mutableStateOf<GridPoint?>(null) }
    var labelText by remember { mutableStateOf("") }
    var draftTokenPoint by remember { mutableStateOf<GridPoint?>(null) }

    LaunchedEffect(tools.active) {
        draftInk = emptyList()
        draftTemplate = null
        draftFog = null
        draftFloors = null
        draftWalls = null
        draftMoved = null
        draftTokenPoint = null
        if (tools.active != BoardTool.EDIT) tools.selectedId = null
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(
                    tools.active,
                    tools.templateShape,
                    tools.stampMode,
                    tools.stampKind,
                    tools.fogCovering,
                    tools.fogBrushSize,
                    tools.floorAdding,
                    tools.floorBrushSize,
                    tools.wallAdding,
                    tools.wallBrushSize,
                    tools.pendingToken,
                    board.document.layers().locked(),
                ) {
                    val active = tools.active
                    if (active == BoardTool.TABLE || active == BoardTool.HAND) return@pointerInput
                    if (active == BoardTool.TOKEN && tools.pendingToken == null) return@pointerInput
                    // Con la vista dinamica la maschera dipinta a mano non viene piu'
                    // disegnata: lasciar pennellare vorrebbe dire far sparire il tratto
                    // sotto le dita. Lo strumento resta selezionabile perche' e' da li'
                    // che si regola il raggio.
                    if (active == BoardTool.FOG && board.document.vision().dynamic()) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startRaw = cameraState.value.worldAt(down.position, offsetState.value)
                            ?: return@awaitEachGesture
                        val locked = boardState.value.layers().locked()
                        if (locked && active !in setOf(BoardTool.MEASURE, BoardTool.PING)) return@awaitEachGesture

                        var lastRaw = startRaw
                        var moved = false
                        val rawInk = mutableListOf(startRaw)
                        val erased = linkedSetOf<String>()
                        val selected = if (active == BoardTool.EDIT) {
                            hitTest(boardState.value, startRaw, grid.feetPerSquare(), cellPx).also {
                                tools.selectedId = it?.id()
                            }
                        } else null
                        val editStart = selected
                        var deltaX = 0.0
                        var deltaY = 0.0

                        when (active) {
                            BoardTool.PING -> pings += PingMark(UUID.randomUUID().toString(), snapCell(startRaw))
                            BoardTool.LABEL -> {
                                labelPoint = snapCell(startRaw)
                                labelText = ""
                            }
                            BoardTool.FOG -> {
                                val initial = boardState.value.fog().resized(grid.columns(), grid.rows())
                                draftFog = paintFog(initial, startRaw, tools.fogCovering, tools.fogBrushSize)
                            }
                            BoardTool.FLOOR -> {
                                val initial = boardState.value.floors().resized(grid.columns(), grid.rows())
                                draftFloors = paintFloors(
                                    initial, startRaw, tools.floorAdding, tools.floorBrushSize,
                                )
                                if (tools.floorAdding) {
                                    val walls = boardState.value.walls().resized(grid.columns(), grid.rows())
                                    draftWalls = paintWalls(
                                        walls, startRaw, blocked = false, brushSize = tools.floorBrushSize,
                                    ) { _, _ -> true }
                                }
                            }
                            BoardTool.WALL -> {
                                val initial = boardState.value.walls().resized(grid.columns(), grid.rows())
                                draftWalls = paintWalls(
                                    initial, startRaw, tools.wallAdding, tools.wallBrushSize,
                                ) { column, row -> !tools.wallAdding || viewModel.occupantAt(column, row) == null }
                            }
                            BoardTool.ERASER -> hitTest(boardState.value, startRaw, grid.feetPerSquare(), cellPx)
                                ?.let { erased += it.id() }
                            BoardTool.TOKEN -> draftTokenPoint = snapCell(startRaw)
                            else -> Unit
                        }
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val raw = cameraState.value.worldAt(change.position, offsetState.value)
                            if (raw != null) {
                                val movement = change.positionChange()
                                if (movement.getDistance() > 0f) moved = true
                                when (active) {
                                    BoardTool.INK -> {
                                        if (
                                            distance(lastRaw, raw) >= 0.025 &&
                                            rawInk.size < BoardLimits.MAX_POINTS_PER_PATH
                                        ) rawInk += raw
                                        draftInk = rawInk.toList()
                                    }
                                    BoardTool.TEMPLATE -> {
                                        draftTemplate = snapCell(startRaw) to snapCell(raw)
                                    }
                                    BoardTool.FOG -> {
                                        val current = draftFog ?: boardState.value.fog().resized(grid.columns(), grid.rows())
                                        draftFog = paintFogLine(
                                            current, lastRaw, raw, tools.fogCovering, tools.fogBrushSize,
                                        )
                                    }
                                    BoardTool.FLOOR -> {
                                        val current = draftFloors
                                            ?: boardState.value.floors().resized(grid.columns(), grid.rows())
                                        draftFloors = paintFloorLine(
                                            current, lastRaw, raw, tools.floorAdding, tools.floorBrushSize,
                                        )
                                        if (tools.floorAdding) {
                                            val walls = draftWalls
                                                ?: boardState.value.walls().resized(grid.columns(), grid.rows())
                                            draftWalls = paintWallLine(
                                                walls, lastRaw, raw, blocked = false,
                                                brushSize = tools.floorBrushSize,
                                            ) { _, _ -> true }
                                        }
                                    }
                                    BoardTool.WALL -> {
                                        val current = draftWalls
                                            ?: boardState.value.walls().resized(grid.columns(), grid.rows())
                                        draftWalls = paintWallLine(
                                            current, lastRaw, raw, tools.wallAdding, tools.wallBrushSize,
                                        ) { column, row ->
                                            !tools.wallAdding || viewModel.occupantAt(column, row) == null
                                        }
                                    }
                                    BoardTool.ERASER -> hitTest(boardState.value, raw, grid.feetPerSquare(), cellPx)
                                        ?.let { erased += it.id() }
                                    BoardTool.EDIT -> if (editStart != null) {
                                        deltaX += raw.x() - lastRaw.x()
                                        deltaY += raw.y() - lastRaw.y()
                                        draftMoved = editStart.translated(deltaX, deltaY)
                                    }
                                    BoardTool.TOKEN -> draftTokenPoint = snapCell(raw)
                                    else -> Unit
                                }
                                lastRaw = raw
                            }
                            change.consume()
                        }

                        when (active) {
                            BoardTool.MEASURE -> {
                                val pair = resolveMeasureEndpoints(startRaw, lastRaw, viewModel)
                                tools.measurePoints = if (moved) {
                                    listOf(pair.first, pair.second)
                                } else {
                                    val clicked = snapCell(startRaw)
                                    (tools.measurePoints + clicked).takeLast(24)
                                }
                            }
                            BoardTool.INK -> {
                                val simplified = simplifyStroke(rawInk, 0.04)
                                if (simplified.size >= 2) {
                                    board.add(
                                        InkStroke(
                                            UUID.randomUUID().toString(), simplified,
                                            preferences.boardColorArgb,
                                            preferences.boardStrokeWidth.toDouble(),
                                        ),
                                    )
                                }
                                draftInk = emptyList()
                            }
                            BoardTool.TEMPLATE -> {
                                val start = snapCell(startRaw)
                                val end = snapCell(lastRaw)
                                if (tools.stampMode) {
                                    board.add(
                                        StaticStamp(
                                            UUID.randomUUID().toString(), end, tools.stampKind,
                                            1.0, 0.0, preferences.boardColorArgb,
                                        ),
                                    )
                                } else {
                                    val squares = max(
                                        1.0,
                                        max(abs(end.x() - start.x()), abs(end.y() - start.y())),
                                    )
                                    board.add(
                                        AreaTemplate(
                                            UUID.randomUUID().toString(), tools.templateShape, start, end,
                                            squares * grid.feetPerSquare(), grid.feetPerSquare().toDouble(),
                                            0.0, preferences.boardColorArgb,
                                        ),
                                    )
                                }
                                draftTemplate = null
                            }
                            BoardTool.FOG -> {
                                draftFog?.let(board::setFog)
                                draftFog = null
                            }
                            BoardTool.FLOOR -> {
                                draftFloors?.let { floors ->
                                    board.setFloors(floors, draftWalls)
                                }
                                draftFloors = null
                                draftWalls = null
                            }
                            BoardTool.WALL -> {
                                draftWalls?.let(board::setWalls)
                                draftWalls = null
                            }
                            BoardTool.ERASER -> if (erased.isNotEmpty()) {
                                board.commit(board.document.withObjects(board.document.objects().filterNot { it.id() in erased }))
                            }
                            BoardTool.EDIT -> {
                                draftMoved?.let(board::replace)
                                draftMoved = null
                            }
                            BoardTool.TOKEN -> {
                                val point = draftTokenPoint ?: snapCell(lastRaw)
                                placePendingSceneToken(board, tools, point)
                                draftTokenPoint = null
                            }
                            else -> Unit
                        }
                    }
                },
        ) {
            if (tools.measurePoints.size >= 2) {
                drawPolyline(tools.measurePoints, Palette.GoldBright, mapOffset, cellPx, 2.5f)
            }
            if (draftInk.size >= 2) {
                drawPolyline(draftInk, Color(preferences.boardColorArgb), mapOffset, cellPx,
                    preferences.boardStrokeWidth * cellPx)
            }
            draftTemplate?.let { (start, end) ->
                val squares = max(1.0, max(abs(end.x() - start.x()), abs(end.y() - start.y())))
                drawTemplate(
                    AreaTemplate("draft", tools.templateShape, start, end,
                        squares * grid.feetPerSquare(), grid.feetPerSquare().toDouble(), 0.0,
                        preferences.boardColorArgb),
                    grid.feetPerSquare(), mapOffset, cellPx, alpha = 0.55f,
                )
            }
            draftFog?.let {
                drawFog(it, camera, mapOffset, cellPx, Palette.Abyss.copy(alpha = 0.52f))
            }
            draftFloors?.let {
                drawFloors(it, camera, mapOffset, cellPx)
            }
            draftWalls?.let {
                drawWalls(it, camera, mapOffset, cellPx)
            }
            val selected = draftMoved ?: tools.selectedId?.let { id -> board.document.objects().firstOrNull { it.id() == id } }
            selected?.let {
                drawBoardBounds(visualBounds(it, grid.feetPerSquare(), cellPx), mapOffset, cellPx, Palette.GoldBright)
            }
            val pending = tools.pendingToken
            val tokenPoint = draftTokenPoint
            if (pending != null && tokenPoint != null) {
                val center = tokenPoint.screen(mapOffset, cellPx)
                val radius = (pending.sizeSquares * cellPx / 2.0).toFloat().coerceAtLeast(4f)
                drawCircle(Color(pending.colorArgb).copy(alpha = 0.38f), radius, center)
                drawCircle(Color(pending.colorArgb), radius, center, style = Stroke(width = 2.5f))
            }
        }

        if (tools.measurePoints.size >= 2) {
            MeasurementTotalLabel(tools.measurePoints, grid.feetPerSquare(), camera, mapOffset)
        }
        pings.forEach { ping ->
            PingPulse(ping, camera, mapOffset) { pings.remove(ping) }
        }
    }

    if (labelPoint != null) {
        AlertDialog(
            onDismissRequest = { labelPoint = null },
            title = { DialogTitle(strings.board.writeLabel) },
            text = {
                TextField(
                    value = labelText,
                    onValueChange = { labelText = it.take(500) },
                    placeholder = { Text(strings.board.labelHint) },
                    singleLine = false,
                )
            },
            confirmButton = {
                GameButton(strings.board.add, enabled = labelText.isNotBlank(), onClick = {
                    val point = labelPoint ?: return@GameButton
                    board.add(
                        Label(
                            UUID.randomUUID().toString(), point, labelText.trim(),
                            preferences.boardColorArgb, 14.0, 0.0,
                        ),
                    )
                    labelPoint = null
                })
            },
            dismissButton = { GameButton(strings.common.cancel, onClick = { labelPoint = null }) },
        )
    }
}

private data class PingMark(val id: String, val point: GridPoint)

@Composable
private fun PingPulse(mark: PingMark, camera: MapViewportGeometry, mapOffset: Offset, onFinished: () -> Unit) {
    val progress = remember(mark.id) { Animatable(0f) }
    LaunchedEffect(mark.id) {
        progress.animateTo(1f, tween(1_250))
        onFinished()
    }
    Canvas(Modifier.fillMaxSize()) {
        val center = camera.screenAt(mark.point, mapOffset)
        repeat(3) { ring ->
            val phase = (progress.value - ring * 0.18f).coerceIn(0f, 1f)
            drawCircle(
                Palette.GoldBright.copy(alpha = (1f - phase) * 0.9f),
                radius = (0.25f + phase * 1.15f) * camera.cellPx,
                center = center,
                style = Stroke(width = 2.5f),
            )
        }
    }
}

private fun DrawScope.drawBoardObject(
    item: BoardObject,
    feetPerSquare: Int,
    mapOffset: Offset,
    cellPx: Float,
) {
    when (item) {
        is InkStroke -> drawPolyline(item.points(), Color(item.colorArgb()), mapOffset, cellPx,
            (item.widthSquares() * cellPx).toFloat().coerceAtLeast(1f))
        is Measurement -> drawPolyline(item.points(), Color(item.colorArgb()), mapOffset, cellPx, 2.5f)
        is AreaTemplate -> drawTemplate(item, feetPerSquare, mapOffset, cellPx)
        is Label, is StaticStamp, is SceneToken -> Unit
    }
}

private fun DrawScope.drawPolyline(
    points: List<GridPoint>,
    color: Color,
    mapOffset: Offset,
    cellPx: Float,
    width: Float,
) {
    if (points.size < 2) return
    for (index in 1 until points.size) {
        drawLine(color, points[index - 1].screen(mapOffset, cellPx), points[index].screen(mapOffset, cellPx),
            strokeWidth = width.coerceAtLeast(1f), cap = StrokeCap.Round)
    }
    points.forEach { drawCircle(color, radius = width.coerceAtLeast(2f), center = it.screen(mapOffset, cellPx)) }
}

private fun DrawScope.drawTemplate(
    template: AreaTemplate,
    feetPerSquare: Int,
    mapOffset: Offset,
    cellPx: Float,
    alpha: Float = 0.28f,
) {
    val color = Color(template.colorArgb())
    val anchor = template.anchor().screen(mapOffset, cellPx)
    val end = template.end().screen(mapOffset, cellPx)
    if (template.rotationDegrees() != 0.0) {
        rotate(template.rotationDegrees().toFloat(), anchor) {
            drawTemplate(
                AreaTemplate(
                    template.id(), template.shape(), template.anchor(), template.end(), template.sizeFeet(),
                    template.widthFeet(), 0.0, template.colorArgb(),
                ),
                feetPerSquare, mapOffset, cellPx, alpha,
            )
        }
        return
    }
    val sizeSquares = (template.sizeFeet() / feetPerSquare.coerceAtLeast(1)).toFloat().coerceAtLeast(0.25f)
    val radius = sizeSquares * cellPx
    when (template.shape()) {
        TemplateShape.SPHERE, TemplateShape.EMANATION, TemplateShape.CYLINDER -> {
            drawCircle(color.copy(alpha = alpha), radius, anchor)
            drawCircle(color, radius, anchor, style = Stroke(width = 2.2f))
        }
        TemplateShape.CUBE -> {
            drawRect(color.copy(alpha = alpha), anchor, Size(radius, radius))
            drawRect(color, anchor, Size(radius, radius), style = Stroke(width = 2.2f))
        }
        TemplateShape.LINE -> {
            val width = (template.widthFeet() / feetPerSquare.coerceAtLeast(1)).toFloat() * cellPx
            drawLine(color.copy(alpha = 0.38f), anchor, end, strokeWidth = width.coerceAtLeast(3f), cap = StrokeCap.Butt)
            drawLine(color, anchor, end, strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
        TemplateShape.CONE -> {
            val angle = atan2(end.y - anchor.y, end.x - anchor.x)
            val half = Math.toRadians(26.565).toFloat()
            val left = Offset(anchor.x + cos(angle - half) * radius, anchor.y + sin(angle - half) * radius)
            val right = Offset(anchor.x + cos(angle + half) * radius, anchor.y + sin(angle + half) * radius)
            val path = Path().apply { moveTo(anchor.x, anchor.y); lineTo(left.x, left.y); lineTo(right.x, right.y); close() }
            drawPath(path, color.copy(alpha = alpha))
            drawPath(path, color, style = Stroke(width = 2.2f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

private fun DrawScope.drawStamp(stamp: StaticStamp, mapOffset: Offset, cellPx: Float) {
    val c = stamp.position().screen(mapOffset, cellPx)
    if (stamp.rotationDegrees() != 0.0) {
        rotate(stamp.rotationDegrees().toFloat(), c) {
            drawStamp(
                StaticStamp(stamp.id(), stamp.position(), stamp.kind(), stamp.sizeSquares(), 0.0, stamp.colorArgb()),
                mapOffset,
                cellPx,
            )
        }
        return
    }
    val radius = (stamp.sizeSquares() * cellPx / 2.0).toFloat().coerceAtLeast(4f)
    val color = Color(stamp.colorArgb())
    when (stamp.kind()) {
        StampKind.FLAME -> {
            val p = Path().apply {
                moveTo(c.x, c.y - radius); cubicTo(c.x + radius, c.y, c.x + radius * .5f, c.y + radius, c.x, c.y + radius)
                cubicTo(c.x - radius, c.y + radius * .4f, c.x - radius * .5f, c.y, c.x, c.y - radius); close()
            }
            drawPath(p, color.copy(alpha = .35f)); drawPath(p, color, style = Stroke(2f))
        }
        StampKind.LIGHT -> {
            drawCircle(color.copy(alpha = .20f), radius * 1.8f, c); drawCircle(color, radius * .45f, c)
            repeat(8) { index ->
                val angle = index * Math.PI.toFloat() / 4f
                drawLine(color, Offset(c.x + cos(angle) * radius * .7f, c.y + sin(angle) * radius * .7f),
                    Offset(c.x + cos(angle) * radius * 1.25f, c.y + sin(angle) * radius * 1.25f), 2f)
            }
        }
        StampKind.DANGER -> {
            val p = Path().apply { moveTo(c.x, c.y - radius); lineTo(c.x + radius, c.y + radius); lineTo(c.x - radius, c.y + radius); close() }
            drawPath(p, color.copy(alpha = .25f)); drawPath(p, color, style = Stroke(2f))
            drawLine(color, Offset(c.x, c.y - radius * .45f), Offset(c.x, c.y + radius * .35f), 3f)
        }
        StampKind.DOOR -> {
            drawRect(color.copy(alpha = .18f), Offset(c.x - radius * .6f, c.y - radius), Size(radius * 1.2f, radius * 2f))
            drawRect(color, Offset(c.x - radius * .6f, c.y - radius), Size(radius * 1.2f, radius * 2f), style = Stroke(2f))
            drawCircle(color, 2.5f, Offset(c.x + radius * .32f, c.y))
        }
        StampKind.TREASURE -> {
            drawRect(color.copy(alpha = .22f), Offset(c.x - radius, c.y - radius * .45f), Size(radius * 2f, radius * 1.35f))
            drawRect(color, Offset(c.x - radius, c.y - radius * .45f), Size(radius * 2f, radius * 1.35f), style = Stroke(2f))
            drawLine(color, Offset(c.x - radius, c.y), Offset(c.x + radius, c.y), 2f)
        }
    }
}

private fun DrawScope.drawFog(
    fog: FogMask,
    camera: MapViewportGeometry,
    mapOffset: Offset,
    cellPx: Float,
    color: Color,
) {
    if (fog.columns() <= 0 || fog.rows() <= 0) return
    val columns = camera.visibleColumns(mapOffset)
    val rows = camera.visibleRows(mapOffset)
    for (row in rows) for (column in columns) {
        if (column >= fog.columns() || row >= fog.rows() || !fog.covered(column, row)) continue
        drawRect(
            color,
            Offset(mapOffset.x + column * cellPx, mapOffset.y + row * cellPx),
            Size(cellPx + 0.5f, cellPx + 0.5f),
        )
    }
}

/** Le tre gradazioni della vista dinamica, disegnate casella per casella. */
private fun DrawScope.drawVisionFog(
    vision: BoardVisionField,
    camera: MapViewportGeometry,
    mapOffset: Offset,
    cellPx: Float,
    exploredColor: Color,
    unseenColor: Color,
) {
    if (vision.columns <= 0 || vision.rows <= 0) return
    val visibleColumns = camera.visibleColumns(mapOffset)
    val visibleRows = camera.visibleRows(mapOffset)
    if (visibleColumns.isEmpty() || visibleRows.isEmpty()) return
    vision.forEachFogRun(
        firstColumn = visibleColumns.first,
        lastColumnExclusive = visibleColumns.last + 1,
        firstRow = visibleRows.first,
        lastRowExclusive = visibleRows.last + 1,
    ) { row, firstColumn, lastColumnExclusive, tier ->
        drawRect(
            color = if (tier == VisionTier.EXPLORED) exploredColor else unseenColor,
            topLeft = Offset(mapOffset.x + firstColumn * cellPx, mapOffset.y + row * cellPx),
            size = Size((lastColumnExclusive - firstColumn) * cellPx + 0.5f, cellPx + 0.5f),
        )
    }
}

private fun DrawScope.drawWalls(
    walls: WallMask,
    camera: MapViewportGeometry,
    mapOffset: Offset,
    cellPx: Float,
) {
    if (walls.columns() <= 0 || walls.rows() <= 0) return
    val fill = Palette.Abyss.copy(alpha = 0.90f)
    val edge = Palette.Bronze.copy(alpha = 0.96f)
    val joint = Palette.TextFaint.copy(alpha = 0.42f)
    for (row in camera.visibleRows(mapOffset)) for (column in camera.visibleColumns(mapOffset)) {
        if (column >= walls.columns() || row >= walls.rows() || !walls.blocked(column, row)) continue
        val topLeft = Offset(mapOffset.x + column * cellPx, mapOffset.y + row * cellPx)
        val size = Size(cellPx + 0.5f, cellPx + 0.5f)
        drawRect(fill, topLeft, size)
        drawRect(edge, topLeft, size, style = Stroke(width = 2f.coerceAtMost(cellPx * 0.12f)))
        if (cellPx >= 12f) {
            drawLine(joint, topLeft, topLeft + Offset(cellPx, cellPx), strokeWidth = 1f)
            drawLine(joint, topLeft + Offset(cellPx, 0f), topLeft + Offset(0f, cellPx), strokeWidth = 1f)
        }
    }
}

private fun DrawScope.drawFloors(
    floors: FloorMask,
    camera: MapViewportGeometry,
    mapOffset: Offset,
    cellPx: Float,
) {
    if (floors.columns() <= 0 || floors.rows() <= 0) return
    val fill = Palette.SurfaceHigh.copy(alpha = 0.46f)
    val edge = Palette.Gold.copy(alpha = 0.34f)
    val grain = Palette.Text.copy(alpha = 0.16f)
    for (row in camera.visibleRows(mapOffset)) for (column in camera.visibleColumns(mapOffset)) {
        if (column >= floors.columns() || row >= floors.rows() || !floors.painted(column, row)) continue
        val topLeft = Offset(mapOffset.x + column * cellPx, mapOffset.y + row * cellPx)
        val size = Size(cellPx + 0.5f, cellPx + 0.5f)
        drawRect(fill, topLeft, size)
        drawRect(edge, topLeft, size, style = Stroke(width = 1.4f.coerceAtMost(cellPx * 0.09f)))
        if (cellPx >= 14f) {
            drawCircle(grain, cellPx * 0.045f, topLeft + Offset(cellPx * 0.30f, cellPx * 0.34f))
            drawCircle(grain, cellPx * 0.035f, topLeft + Offset(cellPx * 0.72f, cellPx * 0.68f))
        }
    }
}

private fun DrawScope.drawBoardBounds(bounds: BoardBounds, mapOffset: Offset, cellPx: Float, color: Color) {
    drawRect(
        color,
        Offset(mapOffset.x + bounds.left().toFloat() * cellPx, mapOffset.y + bounds.top().toFloat() * cellPx),
        Size(((bounds.right() - bounds.left()) * cellPx).toFloat().coerceAtLeast(8f),
            ((bounds.bottom() - bounds.top()) * cellPx).toFloat().coerceAtLeast(8f)),
        style = Stroke(width = 2f),
    )
}

private fun GridPoint.screen(mapOffset: Offset, cellPx: Float): Offset =
    Offset(mapOffset.x + x().toFloat() * cellPx, mapOffset.y + y().toFloat() * cellPx)

private fun snapCell(point: GridPoint): GridPoint =
    GridPoint(floor(point.x()) + 0.5, floor(point.y()) + 0.5)

internal fun visibleSceneTokens(document: BoardDocument, playerPreview: Boolean): List<SceneToken> {
    if (!document.layers().sceneTokensVisible()) return emptyList()
    return document.objects().filterIsInstance<SceneToken>()
        .filter { !playerPreview || it.visibleToPlayers() }
}

/** Posa atomica: un solo oggetto, una sola revisione e un solo passo Undo. */
internal fun placePendingSceneToken(
    board: BoardController,
    tools: BoardToolState,
    point: GridPoint,
    id: String = UUID.randomUUID().toString(),
): String? {
    val draft = tools.pendingToken ?: return null
    val token = SceneToken(
        id, draft.name, draft.category, point, draft.sizeSquares, 0.0,
        draft.colorArgb, draft.imageAssetId, draft.showLabel, draft.visibleToPlayers,
        draft.lootable, draft.lootCategory, draft.lootQuantity, draft.lootDescription, draft.notes,
    )
    if (!board.add(token)) return null
    tools.consumePendingToken()
    tools.select(BoardTool.EDIT)
    tools.selectedId = id
    return id
}

private fun distance(first: GridPoint, second: GridPoint): Double = hypot(second.x() - first.x(), second.y() - first.y())

internal fun measurementFeet(points: List<GridPoint>, feetPerSquare: Int): Int =
    points.zipWithNext().sumOf { (first, second) ->
        max(abs(floor(second.x()) - floor(first.x())), abs(floor(second.y()) - floor(first.y()))).roundToInt() * feetPerSquare
    }

private fun resolveMeasureEndpoints(
    start: GridPoint,
    end: GridPoint,
    viewModel: BattleViewModel,
): Pair<GridPoint, GridPoint> {
    val aCell = GridPosition(floor(start.x()).toInt(), floor(start.y()).toInt())
    val bCell = GridPosition(floor(end.x()).toInt(), floor(end.y()).toInt())
    val aId = viewModel.occupantAt(aCell.column(), aCell.row())
    val bId = viewModel.occupantAt(bCell.column(), bCell.row())
    val a = aId?.let(viewModel::placementOf)
    val b = bId?.let(viewModel::placementOf)
    if (a != null && b != null) return closestCells(a, b)
    if (a != null) return closestCell(a, bCell) to bCell.center()
    if (b != null) return aCell.center() to closestCell(b, aCell)
    return aCell.center() to bCell.center()
}

private fun closestCells(first: TokenPlacement, second: TokenPlacement): Pair<GridPoint, GridPoint> {
    var best: Pair<GridPosition, GridPosition>? = null
    var distance = Int.MAX_VALUE
    first.occupiedSquares().forEach { a -> second.occupiedSquares().forEach { b ->
        val value = a.squaresTo(b)
        if (value < distance) { distance = value; best = a to b }
    } }
    return requireNotNull(best).let { it.first.center() to it.second.center() }
}

private fun closestCell(token: TokenPlacement, target: GridPosition): GridPoint =
    token.occupiedSquares().minBy { it.squaresTo(target) }.center()

private fun GridPosition.center() = GridPoint(column() + 0.5, row() + 0.5)

private fun hitTest(document: BoardDocument, point: GridPoint, feetPerSquare: Int, cellPx: Float): BoardObject? {
    val tolerance = (8f / cellPx.coerceAtLeast(1f)).toDouble().coerceAtMost(1.5)
    return document.objects().asReversed().firstOrNull { item ->
        val visible = when (item) {
            is StaticStamp -> document.layers().stampsVisible()
            is SceneToken -> document.layers().sceneTokensVisible()
            else -> document.layers().annotationsVisible()
        }
        visible && visualBounds(item, feetPerSquare, cellPx).contains(point, tolerance)
    }
}

private fun visualBounds(item: BoardObject, feetPerSquare: Int, cellPx: Float): BoardBounds =
    if (item is Label) labelVisualBounds(item, cellPx) else item.bounds(feetPerSquare)

private fun labelVisualBounds(label: Label, cellPx: Float): BoardBounds {
    val lines = label.text().split('\n')
    val longest = lines.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
    val widthSquares = (longest * label.textSizeSp() * 0.58 / cellPx.coerceAtLeast(1f)).coerceAtLeast(0.25)
    val heightSquares = (lines.size * label.textSizeSp() * 1.35 / cellPx.coerceAtLeast(1f)).coerceAtLeast(0.25)
    return BoardBounds(
        label.position().x(), label.position().y(),
        label.position().x() + widthSquares, label.position().y() + heightSquares,
    )
}

private fun paintFog(
    fog: FogMask,
    point: GridPoint,
    covered: Boolean,
    brushSize: Int,
): FogMask = paintFogLine(fog, point, point, covered, brushSize)

private fun paintFogLine(
    fog: FogMask,
    start: GridPoint,
    end: GridPoint,
    covered: Boolean,
    brushSize: Int,
): FogMask {
    val words = fog.words().toMutableList()
    var changed = false
    rasterBrushLine(start, end, brushSize) { column, row ->
        if (column !in 0 until fog.columns() || row !in 0 until fog.rows()) return@rasterBrushLine
        val cell = row * fog.columns() + column
        val wordIndex = cell ushr 6
        val bit = 1L shl (cell and 63)
        val previous = words[wordIndex]
        val next = if (covered) previous or bit else previous and bit.inv()
        if (next != previous) {
            words[wordIndex] = next
            changed = true
        }
    }
    return if (changed) FogMask(fog.columns(), fog.rows(), words) else fog
}

private fun paintWalls(
    walls: WallMask,
    point: GridPoint,
    blocked: Boolean,
    brushSize: Int,
    allowed: (Int, Int) -> Boolean,
): WallMask = paintWallLine(walls, point, point, blocked, brushSize, allowed)

private fun paintWallLine(
    walls: WallMask,
    start: GridPoint,
    end: GridPoint,
    blocked: Boolean,
    brushSize: Int,
    allowed: (Int, Int) -> Boolean,
): WallMask {
    val words = walls.words().toMutableList()
    var changed = false
    rasterBrushLine(start, end, brushSize) { column, row ->
        if (column !in 0 until walls.columns() || row !in 0 until walls.rows() || !allowed(column, row)) {
            return@rasterBrushLine
        }
        val cell = row * walls.columns() + column
        val wordIndex = cell ushr 6
        val bit = 1L shl (cell and 63)
        val previous = words[wordIndex]
        val next = if (blocked) previous or bit else previous and bit.inv()
        if (next != previous) {
            words[wordIndex] = next
            changed = true
        }
    }
    return if (changed) WallMask(walls.columns(), walls.rows(), words) else walls
}

private fun paintFloors(
    floors: FloorMask,
    point: GridPoint,
    painted: Boolean,
    brushSize: Int,
): FloorMask = paintFloorLine(floors, point, point, painted, brushSize)

private fun paintFloorLine(
    floors: FloorMask,
    start: GridPoint,
    end: GridPoint,
    painted: Boolean,
    brushSize: Int,
): FloorMask {
    val words = floors.words().toMutableList()
    var changed = false
    rasterBrushLine(start, end, brushSize) { column, row ->
        if (column !in 0 until floors.columns() || row !in 0 until floors.rows()) return@rasterBrushLine
        val cell = row * floors.columns() + column
        val wordIndex = cell ushr 6
        val bit = 1L shl (cell and 63)
        val previous = words[wordIndex]
        val next = if (painted) previous or bit else previous and bit.inv()
        if (next != previous) {
            words[wordIndex] = next
            changed = true
        }
    }
    return if (changed) FloorMask(floors.columns(), floors.rows(), words) else floors
}

private inline fun rasterBrushLine(
    start: GridPoint,
    end: GridPoint,
    brushSize: Int,
    visit: (Int, Int) -> Unit,
) {
    val steps = ceil(max(abs(end.x() - start.x()), abs(end.y() - start.y())) * 3.0).toInt().coerceAtLeast(1)
    val radius = (brushSize.coerceIn(1, 5) - 1) / 2
    repeat(steps + 1) { index ->
        val t = index.toDouble() / steps
        val centreColumn = floor(start.x() + (end.x() - start.x()) * t).toInt()
        val centreRow = floor(start.y() + (end.y() - start.y()) * t).toInt()
        for (row in centreRow - radius..centreRow + radius) {
            for (column in centreColumn - radius..centreColumn + radius) visit(column, row)
        }
    }
}

/** Ramer–Douglas–Peucker: riduce punti senza cambiare gli estremi del tratto. */
internal fun simplifyStroke(points: List<GridPoint>, tolerance: Double): List<GridPoint> {
    if (points.size <= 2) return points.toList()
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val ranges = ArrayDeque<Pair<Int, Int>>()
    ranges.addLast(0 to points.lastIndex)

    // La forma iterativa evita una profondità di stack proporzionale ai punti nel
    // caso peggiore, possibile con un tratto valido lungo fino a 10 000 campioni.
    while (ranges.isNotEmpty()) {
        val (startIndex, endIndex) = ranges.removeLast()
        var farthest = -1
        var maximum = 0.0
        for (index in startIndex + 1 until endIndex) {
            val value = perpendicularDistance(points[index], points[startIndex], points[endIndex])
            if (value > maximum) {
                maximum = value
                farthest = index
            }
        }
        if (farthest >= 0 && maximum > tolerance) {
            keep[farthest] = true
            if (farthest - startIndex > 1) ranges.addLast(startIndex to farthest)
            if (endIndex - farthest > 1) ranges.addLast(farthest to endIndex)
        }
    }
    return points.indices.filter { keep[it] }.map(points::get)
}

private fun perpendicularDistance(point: GridPoint, start: GridPoint, end: GridPoint): Double {
    val dx = end.x() - start.x()
    val dy = end.y() - start.y()
    if (dx == 0.0 && dy == 0.0) return distance(point, start)
    val t = ((point.x() - start.x()) * dx + (point.y() - start.y()) * dy) / (dx * dx + dy * dy)
    val projected = GridPoint(start.x() + dx * t.coerceIn(0.0, 1.0), start.y() + dy * t.coerceIn(0.0, 1.0))
    return distance(point, projected)
}
