package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.d6d.board.BoardLayers
import app.d6d.board.BoardObject
import app.d6d.board.AreaTemplate
import app.d6d.board.FogMask
import app.d6d.board.FloorMask
import app.d6d.board.Label
import app.d6d.board.StaticStamp
import app.d6d.board.Measurement
import app.d6d.board.SceneToken
import app.d6d.board.StampKind
import app.d6d.board.TemplateShape
import app.d6d.board.VisionMode
import app.d6d.board.VisionSettings
import app.d6d.board.WallMask
import app.d6d.domain.space.MapGrid
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.ui.board.BoardController
import app.d6d.ui.board.BoardTool
import app.d6d.ui.board.BoardToolState
import app.d6d.ui.components.AppGlyph
import app.d6d.ui.components.GlyphIcon
import app.d6d.ui.i18n.BoardStrings
import app.d6d.ui.i18n.AppLocale
import app.d6d.ui.i18n.strings
import app.d6d.ui.layout.LocalUiLayout
import app.d6d.ui.settings.LocalAppPreferences
import app.d6d.ui.theme.Palette
import java.util.UUID

internal data class ToolEntry(val tool: BoardTool, val glyph: AppGlyph, val label: (BoardStrings) -> String)

internal val BOARD_TOOLS = listOf(
    ToolEntry(BoardTool.TABLE, AppGlyph.TABLE) { it.table },
    ToolEntry(BoardTool.EDIT, AppGlyph.EDIT_BOARD) { it.edit },
    ToolEntry(BoardTool.HAND, AppGlyph.HAND) { it.hand },
    ToolEntry(BoardTool.MEASURE, AppGlyph.MEASURE) { it.measure },
    ToolEntry(BoardTool.INK, AppGlyph.INK) { it.ink },
    ToolEntry(BoardTool.TEMPLATE, AppGlyph.TEMPLATE) { it.template },
    ToolEntry(BoardTool.LABEL, AppGlyph.LABEL) { it.label },
    ToolEntry(BoardTool.PING, AppGlyph.PING) { it.ping },
    ToolEntry(BoardTool.FOG, AppGlyph.FOG) { it.fog },
    ToolEntry(BoardTool.FLOOR, AppGlyph.FLOOR) { it.floor },
    ToolEntry(BoardTool.WALL, AppGlyph.WALL) { it.wall },
    ToolEntry(BoardTool.TOKEN, AppGlyph.TOKEN) { it.token },
    ToolEntry(BoardTool.ERASER, AppGlyph.ERASER) { it.eraser },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoardToolboxPanel(
    state: BoardToolState,
    board: BoardController,
    compact: Boolean,
    onSelect: (BoardTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = strings.board
    val layout = LocalUiLayout.current
    val locked = board.document.layers().locked()
    Column(
        modifier
            .then(if (compact) Modifier.fillMaxWidth() else Modifier.width(190.dp))
            .background(Palette.Surface.copy(alpha = 0.97f), RoundedCornerShape(9.dp))
            .border(1.dp, Palette.Bronze.copy(alpha = 0.75f), RoundedCornerShape(9.dp))
            .padding(7.dp)
            // Il pannello e' ancorato in alto e cresce verso il basso: fra Strati e
            // barre degli strumenti alte due righe puo' superare l'altezza della
            // mappa, e l'ultimo comando finirebbe fuori schermo senza modo di
            // raggiungerlo. Scorrendo, resta sempre tutto a portata.
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.layersOpen) {
            Text(words.layers, color = Palette.Gold, style = MaterialTheme.typography.titleSmall)
            BoardOptionButton(words.tools, compact, onClick = { state.layersOpen = false })
            BoardLayersPanel(state, board, compact)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                BOARD_TOOLS.forEach { entry ->
                    val persistent = entry.tool in setOf(
                        BoardTool.EDIT, BoardTool.INK, BoardTool.TEMPLATE,
                        BoardTool.LABEL, BoardTool.FOG, BoardTool.FLOOR, BoardTool.WALL,
                        BoardTool.TOKEN, BoardTool.ERASER,
                    )
                    val previewAllowed = !state.playerPreview || entry.tool in setOf(
                        BoardTool.TABLE, BoardTool.HAND, BoardTool.MEASURE, BoardTool.PING,
                    )
                    val enabled = (!locked || !persistent) && previewAllowed
                    BoardToolButton(
                        entry = entry,
                        selected = state.active == entry.tool,
                        enabled = enabled,
                        showLabel = true,
                        touchTarget = compact,
                        onClick = { onSelect(entry.tool) },
                    )
                }
                BoardCommandButton(
                    AppGlyph.LAYERS, words.layers, selected = false,
                    showLabel = true, touchTarget = compact,
                ) {
                    state.layersOpen = true
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                BoardOptionButton(words.undo, compact, enabled = board.canUndo, onClick = board::undo)
                BoardOptionButton(words.redo, compact, enabled = board.canRedo, onClick = board::redo)
            }
            if (!compact) {
                BoardOptionButton(
                    label = if (layout.toolboxPinned) words.unpin else words.pin,
                    compact = false,
                    selected = layout.toolboxPinned,
                    onClick = { layout.toolboxPinned = !layout.toolboxPinned },
                )
            }
            if (locked) {
                Text(words.boardLockedHint, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun BoardToolRail(
    state: BoardToolState,
    board: BoardController,
    onSelect: (BoardTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = strings.board
    val locked = board.document.layers().locked()
    Column(
        modifier
            .background(Palette.Surface.copy(alpha = 0.94f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Bronze.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BOARD_TOOLS.forEach { entry ->
            val persistent = entry.tool in setOf(
                BoardTool.EDIT, BoardTool.INK, BoardTool.TEMPLATE,
                BoardTool.LABEL, BoardTool.FOG, BoardTool.FLOOR, BoardTool.WALL,
                BoardTool.TOKEN, BoardTool.ERASER,
            )
            BoardToolButton(
                entry,
                selected = state.active == entry.tool,
                enabled = (!locked || !persistent) &&
                    (!state.playerPreview || entry.tool in setOf(
                        BoardTool.TABLE, BoardTool.HAND, BoardTool.MEASURE, BoardTool.PING,
                    )),
                showLabel = false,
                onClick = { onSelect(entry.tool) },
            )
        }
        BoardCommandButton(AppGlyph.LAYERS, words.layers, state.layersOpen) {
            state.layersOpen = !state.layersOpen
            state.toolboxOpen = true
        }
    }
}

@Composable
private fun BoardToolButton(
    entry: ToolEntry,
    selected: Boolean,
    enabled: Boolean,
    showLabel: Boolean,
    touchTarget: Boolean = false,
    onClick: () -> Unit,
) {
    val label = entry.label(strings.board)
    val tint = when {
        !enabled -> Palette.TextFaint
        selected -> Palette.Abyss
        else -> Palette.Gold
    }
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .sizeIn(
                minWidth = when {
                    showLabel -> 82.dp
                    touchTarget -> 48.dp
                    else -> 40.dp
                },
                minHeight = if (touchTarget) 48.dp else 40.dp,
            )
            .background(if (selected) Palette.Gold else Palette.SurfaceHigh, shape)
            .border(1.dp, if (selected) Palette.GoldBright else Palette.Line, shape)
            .semantics {
                role = Role.Button
                contentDescription = label
                this.selected = selected
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(entry.glyph, tint, size = 18.dp)
        if (showLabel) Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BoardCommandButton(
    glyph: AppGlyph,
    label: String,
    selected: Boolean,
    showLabel: Boolean = false,
    touchTarget: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .sizeIn(
                minWidth = when {
                    showLabel -> 82.dp
                    touchTarget -> 48.dp
                    else -> 40.dp
                },
                minHeight = if (touchTarget) 48.dp else 40.dp,
            )
            .background(if (selected) Palette.Gold else Palette.SurfaceHigh, shape)
            .border(1.dp, if (selected) Palette.GoldBright else Palette.Line, shape)
            .semantics { role = Role.Button; contentDescription = label; this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, if (selected) Palette.Abyss else Palette.Gold, size = 18.dp)
        if (showLabel) {
            Text(
                label,
                color = if (selected) Palette.Abyss else Palette.Gold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun BoardOptionButton(
    label: String,
    compact: Boolean,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    GameButton(
        label = label,
        modifier = Modifier.sizeIn(minHeight = if (compact) 48.dp else 40.dp),
        enabled = enabled,
        selected = selected,
        dense = true,
        onClick = onClick,
    )
}

@Composable
private fun BoardLayersPanel(state: BoardToolState, board: BoardController, compact: Boolean) {
    val words = strings.board
    val layers = board.document.layers()
    val layout = LocalUiLayout.current
    fun clearSelectionIfHidden(hidden: (BoardObject) -> Boolean) {
        val selected = state.selectedId?.let { id -> board.document.objects().firstOrNull { it.id() == id } }
        if (selected != null && hidden(selected)) state.selectedId = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LayerToggle(words.background, layers.backgroundVisible(), compact) {
            board.setLayers(layers.withBackgroundVisible(it))
        }
        LayerToggle(words.floor, layers.floorsVisible(), compact) {
            board.setLayers(layers.withFloorsVisible(it))
        }
        LayerToggle(words.grid, layout.mapShowGrid, compact) { layout.mapShowGrid = it }
        LayerToggle("${words.combatants} · ${words.protectedLayer}", true, compact, enabled = false) { }
        LayerToggle(words.sceneTokens, layers.sceneTokensVisible(), compact) {
            board.setLayers(layers.withSceneTokensVisible(it))
            if (!it) clearSelectionIfHidden { item -> item is SceneToken }
        }
        LayerToggle(words.annotations, layers.annotationsVisible(), compact) {
            board.setLayers(layers.withAnnotationsVisible(it))
            if (!it) clearSelectionIfHidden { item -> item !is StaticStamp && item !is SceneToken }
        }
        LayerToggle(words.stamps, layers.stampsVisible(), compact) {
            board.setLayers(layers.withStampsVisible(it))
            if (!it) clearSelectionIfHidden { item -> item is StaticStamp }
        }
        LayerToggle(words.wall, layers.wallsVisible(), compact) {
            board.setLayers(layers.withWallsVisible(it))
        }
        LayerToggle(words.fog, layers.fogVisible(), compact) {
            board.setLayers(layers.withFogVisible(it))
        }
        LayerToggle(if (layers.locked()) words.unlock else words.lock, layers.locked(), compact) {
            board.setLayers(layers.withLocked(it))
            if (it && state.active !in setOf(BoardTool.TABLE, BoardTool.HAND, BoardTool.MEASURE, BoardTool.PING)) {
                state.table()
            }
        }
        LayerToggle(words.playerPreview, state.playerPreview, compact) {
            state.playerPreview = it
            if (
                (it && state.active !in setOf(
                    BoardTool.TABLE, BoardTool.HAND, BoardTool.MEASURE, BoardTool.PING,
                ))
            ) state.table()
        }
    }
}

internal fun BoardLayers.withBackgroundVisible(value: Boolean) = BoardLayers(
    value, floorsVisible(), annotationsVisible(), stampsVisible(), sceneTokensVisible(), wallsVisible(), fogVisible(), locked(),
)

internal fun BoardLayers.withFloorsVisible(value: Boolean) = BoardLayers(
    backgroundVisible(), value, annotationsVisible(), stampsVisible(), sceneTokensVisible(), wallsVisible(), fogVisible(), locked(),
)

internal fun BoardLayers.withAnnotationsVisible(value: Boolean) = BoardLayers(
    backgroundVisible(), floorsVisible(), value, stampsVisible(), sceneTokensVisible(), wallsVisible(), fogVisible(), locked(),
)

internal fun BoardLayers.withStampsVisible(value: Boolean) = BoardLayers(
    backgroundVisible(), floorsVisible(), annotationsVisible(), value, sceneTokensVisible(), wallsVisible(), fogVisible(), locked(),
)

internal fun BoardLayers.withSceneTokensVisible(value: Boolean) = BoardLayers(
    backgroundVisible(), floorsVisible(), annotationsVisible(), stampsVisible(), value, wallsVisible(), fogVisible(), locked(),
)

internal fun BoardLayers.withWallsVisible(value: Boolean) = BoardLayers(
    backgroundVisible(), floorsVisible(), annotationsVisible(), stampsVisible(), sceneTokensVisible(), value, fogVisible(), locked(),
)

internal fun BoardLayers.withFogVisible(value: Boolean) = BoardLayers(
    backgroundVisible(), floorsVisible(), annotationsVisible(), stampsVisible(), sceneTokensVisible(), wallsVisible(), value, locked(),
)

internal fun BoardLayers.withLocked(value: Boolean) = BoardLayers(
    backgroundVisible(), floorsVisible(), annotationsVisible(), stampsVisible(), sceneTokensVisible(), wallsVisible(), fogVisible(), value,
)

@Composable
private fun LayerToggle(
    label: String,
    selected: Boolean,
    compact: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    BoardOptionButton(label, compact, selected = selected, enabled = enabled, onClick = { onChange(!selected) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoardToolOptions(
    state: BoardToolState,
    board: BoardController,
    grid: MapGrid,
    compact: Boolean,
    modifier: Modifier = Modifier,
    inspectedCombatantId: String? = null,
    inspectedCombatantName: String = "",
) {
    if (state.active == BoardTool.TABLE || state.active == BoardTool.HAND || state.active == BoardTool.PING ||
        state.active == BoardTool.ERASER || state.active == BoardTool.LABEL
    ) return
    val words = strings.board
    val preferences = LocalAppPreferences.current
    FlowRow(
        modifier.fillMaxWidth().background(Palette.Surface.copy(alpha = 0.82f)).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.active) {
            BoardTool.EDIT -> {
                val selected = state.selectedId?.let { id -> board.document.objects().firstOrNull { it.id() == id } }
                BoardOptionButton(words.deleteObject, compact, enabled = selected != null, onClick = {
                    selected?.let { board.remove(it.id()) }
                    state.selectedId = null
                })
                BoardOptionButton(words.smaller, compact, enabled = selected.isResizable(),
                    onClick = { selected?.resized(0.85)?.let(board::replace) })
                BoardOptionButton(words.bigger, compact, enabled = selected.isResizable(),
                    onClick = { selected?.resized(1.15)?.let(board::replace) })
                BoardOptionButton(words.rotateLeft, compact, enabled = selected.isResizable(),
                    onClick = { selected?.rotated(-15.0)?.let(board::replace) })
                BoardOptionButton(words.rotateRight, compact, enabled = selected.isResizable(),
                    onClick = { selected?.rotated(15.0)?.let(board::replace) })
                BoardOptionButton(words.editText, compact, enabled = selected is Label, onClick = {
                    state.labelEditorId = (selected as? Label)?.id()
                })
                BoardOptionButton(words.editToken, compact, enabled = selected is SceneToken, onClick = {
                    (selected as? SceneToken)?.let { state.requestTokenEdit(it.id()) }
                })
            }
            BoardTool.MEASURE -> {
                BoardOptionButton(
                    words.pinMeasurement,
                    compact,
                    enabled = state.measurePoints.size >= 2 && !board.document.layers().locked(),
                    onClick = {
                        board.add(Measurement(UUID.randomUUID().toString(), state.measurePoints, preferences.boardColorArgb))
                        state.measurePoints = emptyList()
                    },
                )
                BoardOptionButton(words.clearMeasurement, compact, enabled = state.measurePoints.isNotEmpty(), onClick = {
                    state.measurePoints = emptyList()
                })
            }
            BoardTool.INK -> {
                Text(words.strokeWidth, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                listOf(0.06f, 0.12f, 0.24f, 0.48f).forEach { width ->
                    BoardOptionButton(
                        "${(width * 100).toInt()}",
                        compact,
                        selected = preferences.boardStrokeWidth == width,
                        onClick = { preferences.boardStrokeWidth = width },
                    )
                }
                ColourChoices(preferences.boardColorArgb, compact) { preferences.boardColorArgb = it }
            }
            BoardTool.TEMPLATE -> {
                BoardOptionButton(words.templateMode, compact, selected = !state.stampMode, onClick = { state.stampMode = false })
                BoardOptionButton(words.stampMode, compact, selected = state.stampMode, onClick = { state.stampMode = true })
                if (state.stampMode) {
                    StampKind.entries.forEach { kind ->
                        BoardOptionButton(kind.label(words), compact, selected = state.stampKind == kind, onClick = {
                            state.stampKind = kind
                            preferences.boardStampKind = kind
                        })
                    }
                } else {
                    TemplateShape.entries.forEach { shape ->
                        BoardOptionButton(shape.label(words), compact, selected = state.templateShape == shape, onClick = {
                            state.templateShape = shape
                            preferences.boardTemplateShape = shape
                        })
                    }
                    Text(words.illustrativeTemplate, color = Palette.TextFaint, style = MaterialTheme.typography.labelSmall)
                }
                ColourChoices(preferences.boardColorArgb, compact) { preferences.boardColorArgb = it }
            }
            BoardTool.FOG -> {
                val vision = board.document.vision()
                BoardOptionButton(
                    words.visionPainted, compact, selected = !vision.dynamic(),
                    onClick = { board.setVision(vision.withMode(VisionMode.MANUAL)) },
                )
                BoardOptionButton(
                    words.visionDynamic, compact, selected = vision.dynamic(),
                    onClick = { board.setVision(vision.withMode(VisionMode.DYNAMIC)) },
                )
                if (vision.dynamic()) {
                    VisionOptions(
                        vision = vision,
                        board = board,
                        grid = grid,
                        compact = compact,
                        inspectedCombatantId = inspectedCombatantId,
                        inspectedCombatantName = inspectedCombatantName,
                    )
                } else {
                    BoardOptionButton(words.revealFog, compact, selected = !state.fogCovering, onClick = { state.fogCovering = false })
                    BoardOptionButton(words.coverFog, compact, selected = state.fogCovering, onClick = { state.fogCovering = true })
                    Text(words.brushSize, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    listOf(1, 3, 5).forEach { size ->
                        BoardOptionButton(
                            "${size}×$size", compact, selected = state.fogBrushSize == size,
                            onClick = { state.fogBrushSize = size },
                        )
                    }
                    BoardOptionButton(words.coverAllFog, compact, onClick = {
                        board.setFog(FogMask.fullyCovered(grid.columns(), grid.rows()))
                    })
                    BoardOptionButton(words.revealAllFog, compact, onClick = {
                        board.setFog(FogMask.empty(grid.columns(), grid.rows()))
                    })
                    Text(words.fogPaintHint, color = Palette.TextFaint, style = MaterialTheme.typography.labelSmall)
                }
            }
            BoardTool.FLOOR -> {
                BoardOptionButton(
                    words.paintFloors, compact, selected = state.floorAdding,
                    onClick = { state.floorAdding = true },
                )
                BoardOptionButton(
                    words.eraseFloors, compact, selected = !state.floorAdding,
                    onClick = { state.floorAdding = false },
                )
                Text(words.brushSize, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                listOf(1, 3, 5).forEach { size ->
                    BoardOptionButton(
                        "${size}×$size", compact, selected = state.floorBrushSize == size,
                        onClick = { state.floorBrushSize = size },
                    )
                }
                BoardOptionButton(words.fillFloors, compact, onClick = {
                    board.setFloors(FloorMask.filled(grid.columns(), grid.rows()))
                })
                BoardOptionButton(words.clearFloors, compact, onClick = {
                    board.setFloors(FloorMask.empty(grid.columns(), grid.rows()))
                })
                Text(words.floorHint, color = Palette.TextFaint, style = MaterialTheme.typography.labelSmall)
            }
            BoardTool.WALL -> {
                BoardOptionButton(words.addWalls, compact, selected = state.wallAdding, onClick = { state.wallAdding = true })
                BoardOptionButton(words.eraseWalls, compact, selected = !state.wallAdding, onClick = { state.wallAdding = false })
                Text(words.brushSize, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                listOf(1, 3, 5).forEach { size ->
                    BoardOptionButton(
                        "${size}×$size", compact, selected = state.wallBrushSize == size,
                        onClick = { state.wallBrushSize = size },
                    )
                }
                BoardOptionButton(words.clearWalls, compact, onClick = {
                    board.setWalls(WallMask.empty(grid.columns(), grid.rows()))
                })
                Text(words.wallHint, color = Palette.TextFaint, style = MaterialTheme.typography.labelSmall)
            }
            BoardTool.TOKEN -> {
                if (state.pendingToken == null) {
                    BoardOptionButton(words.createToken, compact, onClick = state::requestTokenCreation)
                } else {
                    Text(words.tokenPlacementHint, color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
                    BoardOptionButton(words.cancelPlacement, compact, onClick = state::table)
                }
            }
            else -> Unit
        }
    }

    val edited = state.labelEditorId?.let { id -> board.document.objects().filterIsInstance<Label>().firstOrNull { it.id() == id } }
    if (edited != null) {
        var text by remember(edited.id()) { mutableStateOf(edited.text()) }
        AlertDialog(
            onDismissRequest = { state.labelEditorId = null },
            title = { Text(words.editText) },
            text = { TextField(text, { text = it.take(500) }, singleLine = false) },
            confirmButton = {
                GameButton(strings.common.apply, enabled = text.isNotBlank(), onClick = {
                    board.replace(Label(edited.id(), edited.position(), text.trim(), edited.colorArgb(), edited.textSizeSp(), edited.rotationDegrees()))
                    state.labelEditorId = null
                })
            },
            dismissButton = { GameButton(strings.common.cancel, onClick = { state.labelEditorId = null }) },
        )
    }
}

@Composable
private fun ColourChoices(selected: Int, compact: Boolean, onSelect: (Int) -> Unit) {
    val description = strings.board.colour
    listOf(0xFFFFC857, 0xFFE35D6A, 0xFF66D9EF, 0xFF8BE28B, 0xFFFFFFFF).forEach { raw ->
        val argb = raw.toInt()
        val color = Color(argb)
        Box(
            Modifier
                .sizeIn(
                    minWidth = if (compact) 48.dp else 40.dp,
                    minHeight = if (compact) 48.dp else 40.dp,
                )
                .background(color, RoundedCornerShape(20.dp))
                .border(if (selected == argb) 3.dp else 1.dp, Palette.Text, RoundedCornerShape(20.dp))
                .clickable { onSelect(argb) }
                .semantics { role = Role.Button; contentDescription = description },
        )
    }
}

private fun BoardObject?.isResizable(): Boolean =
    this is AreaTemplate || this is StaticStamp || this is Label || this is SceneToken

private fun TemplateShape.label(words: BoardStrings): String = when (this) {
    TemplateShape.CONE -> words.cone
    TemplateShape.CUBE -> words.cube
    TemplateShape.CYLINDER -> words.cylinder
    TemplateShape.EMANATION -> words.emanation
    TemplateShape.LINE -> words.line
    TemplateShape.SPHERE -> words.sphere
}

private fun StampKind.label(words: BoardStrings): String = when (this) {
    StampKind.FLAME -> words.flame
    StampKind.LIGHT -> words.light
    StampKind.DANGER -> words.danger
    StampKind.DOOR -> words.door
    StampKind.TREASURE -> words.treasure
}

private fun BoardObject.resized(factor: Double): BoardObject? = when (this) {
    is AreaTemplate -> AreaTemplate(id(), shape(), anchor(), end(), sizeFeet() * factor,
        widthFeet() * factor, rotationDegrees(), colorArgb())
    is StaticStamp -> StaticStamp(id(), position(), kind(), sizeSquares() * factor, rotationDegrees(), colorArgb())
    is Label -> Label(id(), position(), text(), colorArgb(), textSizeSp() * factor, rotationDegrees())
    is SceneToken -> SceneToken(
        id(), name(), category(), position(), (sizeSquares() * factor).coerceIn(0.25, 20.0),
        rotationDegrees(), colorArgb(), imageAssetId(), showLabel(), visibleToPlayers(),
        lootable(), lootCategory(), lootQuantity(), lootDescription(), notes(),
    )
    else -> null
}

private fun BoardObject.rotated(delta: Double): BoardObject? = when (this) {
    is AreaTemplate -> AreaTemplate(id(), shape(), anchor(), end(), sizeFeet(), widthFeet(), rotationDegrees() + delta, colorArgb())
    is StaticStamp -> StaticStamp(id(), position(), kind(), sizeSquares(), rotationDegrees() + delta, colorArgb())
    is Label -> Label(id(), position(), text(), colorArgb(), textSizeSp(), rotationDegrees() + delta)
    is SceneToken -> SceneToken(
        id(), name(), category(), position(), sizeSquares(), rotationDegrees() + delta,
        colorArgb(), imageAssetId(), showLabel(), visibleToPlayers(),
        lootable(), lootCategory(), lootQuantity(), lootDescription(), notes(),
    )
    else -> null
}

/**
 * Comandi della vista dinamica: il raggio di mappa, l'eccezione di chi è
 * selezionato, e il pulsante che dimentica l'esplorato.
 *
 * Il passo è una casella, non un piede: la vista si misura sulla griglia, e un
 * raggio che non è multiplo del passo verrebbe comunque arrotondato per difetto
 * dal calcolo. Meglio non mostrare valori che poi non si vedono applicati.
 */
@Composable
private fun VisionOptions(
    vision: VisionSettings,
    board: BoardController,
    grid: MapGrid,
    compact: Boolean,
    inspectedCombatantId: String?,
    inspectedCombatantName: String,
) {
    val words = strings.board
    val language = AppLocale.language
    val step = grid.feetPerSquare().coerceAtLeast(1)

    fun label(feet: Int): String = if (feet <= 0) words.visionBlind else distanceLabel(feet, language)

    Text(words.visionRadius, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
    BoardOptionButton("−", compact, enabled = vision.radiusFeet() > 0, onClick = {
        board.setVision(vision.withRadiusFeet((vision.radiusFeet() - step).coerceAtLeast(0)))
    })
    Text(label(vision.radiusFeet()), color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
    BoardOptionButton("+", compact, onClick = {
        board.setVision(vision.withRadiusFeet(vision.radiusFeet() + step))
    })

    if (inspectedCombatantId == null) {
        Text(words.visionPickCombatantHint, color = Palette.TextFaint, style = MaterialTheme.typography.labelSmall)
    } else {
        val personal = vision.radiusFeetFor(inspectedCombatantId)
        val overridden = vision.hasOverride(inspectedCombatantId)
        Text(
            words.visionOf(inspectedCombatantName),
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        BoardOptionButton("−", compact, enabled = personal > 0, onClick = {
            board.setVision(vision.withOverride(inspectedCombatantId, (personal - step).coerceAtLeast(0)))
        })
        Text(
            label(personal),
            color = if (overridden) Palette.Gold else Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        BoardOptionButton("+", compact, onClick = {
            board.setVision(vision.withOverride(inspectedCombatantId, personal + step))
        })
        BoardOptionButton(
            words.visionUseMapRadius, compact,
            enabled = overridden,
            selected = !overridden,
            onClick = { board.setVision(vision.withOverride(inspectedCombatantId, null)) },
        )
    }

    BoardOptionButton(words.forgetExplored, compact, onClick = {
        board.resetExplored(grid.columns(), grid.rows())
    })
    Text(words.visionDynamicHint, color = Palette.TextFaint, style = MaterialTheme.typography.labelSmall)
}
