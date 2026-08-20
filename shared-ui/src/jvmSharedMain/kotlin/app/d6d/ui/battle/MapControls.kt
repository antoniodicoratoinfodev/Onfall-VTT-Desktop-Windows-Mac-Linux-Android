package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.d6d.domain.space.MapGrid
import app.d6d.ui.board.BoardController
import app.d6d.ui.board.BoardTool
import app.d6d.ui.board.BoardToolState
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.i18n.strings
import app.d6d.ui.components.Chip
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.maps.GridLimits
import app.d6d.ui.maps.MapPickerDialog
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.Palette

/**
 * Comandi della mappa.
 *
 * Tutto e' modificabile a schermo: dimensioni della griglia, scala in piedi per
 * casella, ingrandimento e sfondo. La scala e le dimensioni sono la stessa cosa
 * vista da due lati — una scaramuccia in una stanza e una battaglia campale non
 * si rappresentano bene con lo stesso passo.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapControls(
    viewModel: BattleViewModel,
    portraits: PortraitRepository,
    cellSize: Dp,
    onCellSizeChange: (Dp) -> Unit,
    showGrid: Boolean,
    onShowGridChange: (Boolean) -> Unit,
    gridBrightness: Float,
    onGridBrightnessChange: (Float) -> Unit,
    board: BoardController,
    boardTools: BoardToolState,
    compact: Boolean,
    onBoardToolSelected: (BoardTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = viewModel.battleMap.grid()
    val words = strings.battle
    val language = currentLanguage
    var expanded by remember { mutableStateOf(false) }
    var showMapPicker by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.82f)),
    ) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(words.mapCaps, color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
                Chip(
                    words.gridSummary(
                        grid.columns(),
                        grid.rows(),
                        distanceLabel(grid.feetPerSquare(), language),
                    ),
                    Palette.TextMuted,
                )
            }
            GameButton("−", accent = Palette.TextMuted, dense = true, onClick = {
                onCellSizeChange((cellSize - 6.dp).coerceIn(MIN_CELL, MAX_CELL))
            })
            Chip(words.zoomLevel(cellSize.value.toInt()), Palette.TextMuted)
            GameButton("+", accent = Palette.TextMuted, dense = true, onClick = {
                onCellSizeChange((cellSize + 6.dp).coerceIn(MIN_CELL, MAX_CELL))
            })
            GameButton(
                label = if (showGrid) words.gridVisible else words.gridHidden,
                accent = if (showGrid) Palette.Gold else Palette.TextFaint,
                selected = showGrid,
                dense = true,
                onClick = { onShowGridChange(!showGrid) },
            )
            GameButton(
                label = if (expanded) words.hideOptions else words.mapOptions,
                accent = if (expanded) Palette.Gold else Palette.TextMuted,
                selected = expanded,
                dense = true,
                onClick = { expanded = !expanded },
            )
            GameButton(
                label = strings.board.tools,
                accent = if (boardTools.active == BoardTool.TABLE) Palette.TextMuted else Palette.Gold,
                selected = boardTools.toolboxOpen,
                dense = true,
                onClick = { boardTools.toolboxOpen = !boardTools.toolboxOpen },
            )
        }

        BoardToolOptions(boardTools, board, grid, compact)

        if (viewModel.mapEditMode) {
            Text(
                text = words.dragImageHint,
                color = Palette.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        if (expanded) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // Rimpicciolire la griglia toglie dalla mappa i segnaposti rimasti
                // fuori bordo: i comandi si fermano quindi al minimo che la
                // procedura Nuova partita consente, e al massimo che il motore
                // accetta, invece di produrre un rifiuto o una griglia inservibile.
                GameButton(
                    label = words.fewerColumns,
                    accent = Palette.TextMuted,
                    dense = true,
                    enabled = grid.columns() > GridLimits.MIN_SIDE,
                    onClick = {
                        viewModel.configureMap(grid.columns() - 1, grid.rows(), grid.feetPerSquare())
                    },
                )
                GameButton(
                    label = words.moreColumns,
                    accent = Palette.TextMuted,
                    dense = true,
                    enabled = grid.columns() < MapGrid.MAX_SIDE,
                    onClick = {
                        viewModel.configureMap(grid.columns() + 1, grid.rows(), grid.feetPerSquare())
                    },
                )
                GameButton(
                    label = words.fewerRows,
                    accent = Palette.TextMuted,
                    dense = true,
                    enabled = grid.rows() > GridLimits.MIN_SIDE,
                    onClick = {
                        viewModel.configureMap(grid.columns(), grid.rows() - 1, grid.feetPerSquare())
                    },
                )
                GameButton(
                    label = words.moreRows,
                    accent = Palette.TextMuted,
                    dense = true,
                    enabled = grid.rows() < MapGrid.MAX_SIDE,
                    onClick = {
                        viewModel.configureMap(grid.columns(), grid.rows() + 1, grid.feetPerSquare())
                    },
                )

                listOf(5, 10, 20, 50).forEach { feet ->
                    GameButton(
                        label = words.perSquare(distanceLabel(feet, language)),
                        accent = if (grid.feetPerSquare() == feet) Palette.GoldBright else Palette.TextFaint,
                        selected = grid.feetPerSquare() == feet,
                        dense = true,
                        onClick = { viewModel.configureMap(grid.columns(), grid.rows(), feet) },
                    )
                }

                Slider(
                    value = cellSize.value.coerceIn(MIN_CELL.value, MAX_CELL.value),
                    onValueChange = { onCellSizeChange(it.dp.coerceIn(MIN_CELL, MAX_CELL)) },
                    valueRange = MIN_CELL.value..MAX_CELL.value,
                    colors = SliderDefaults.colors(
                        thumbColor = Palette.Gold,
                        activeTrackColor = Palette.Gold,
                        inactiveTrackColor = Palette.Line,
                    ),
                    modifier = Modifier.width(150.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(words.gridBrightness, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = gridBrightness.coerceIn(MIN_GRID_BRIGHTNESS, MAX_GRID_BRIGHTNESS),
                        onValueChange = { onGridBrightnessChange(it.coerceIn(MIN_GRID_BRIGHTNESS, MAX_GRID_BRIGHTNESS)) },
                        valueRange = MIN_GRID_BRIGHTNESS..MAX_GRID_BRIGHTNESS,
                        colors = SliderDefaults.colors(
                            thumbColor = Palette.Gold,
                            activeTrackColor = Palette.Gold,
                            inactiveTrackColor = Palette.Line,
                        ),
                        modifier = Modifier.width(120.dp),
                    )
                    Chip("${(gridBrightness * 100).toInt()}%", Palette.TextMuted)
                }
                GameButton(
                    label = words.chooseBackground,
                    accent = Palette.Party,
                    dense = true,
                    subtitle = words.fromMapArchive,
                    onClick = { showMapPicker = true },
                )
                if (viewModel.battleMap.backgroundImage().isNotBlank()) {
                    GameButton(
                        label = words.mapEditing,
                        accent = if (viewModel.mapEditMode) Palette.Heal else Palette.Gold,
                        selected = viewModel.mapEditMode,
                        dense = true,
                        onClick = {
                            val activate = !viewModel.mapEditMode
                            // La modifica dello sfondo resta una sotto-modalita'
                            // sicura della modifica generale, ma questo comando vi
                            // entra direttamente senza obbligare a cercare prima
                            // il pulsante nella barra superiore.
                            if (activate) {
                                onBoardToolSelected(BoardTool.TABLE)
                                viewModel.editMode = true
                            }
                            viewModel.mapEditMode = activate
                        },
                    )
                    GameButton(words.removeBackground, accent = Palette.TextFaint, dense = true, onClick = {
                        viewModel.setMapBackground("")
                    })
                    if (viewModel.mapEditMode) {
                        GameButton(words.fitAndCentre, accent = Palette.TextMuted, dense = true, onClick = {
                            // Una trasformazione non impostata fa ricalcolare alla
                            // vista il miglior "contain" usando le proporzioni vere
                            // dell'immagine. E' anche la via di recupero se lo sfondo
                            // era stato trascinato interamente fuori inquadratura.
                            viewModel.setMapBackgroundTransform(0.0, 0.0, 0.0, 0.0)
                        })
                    }
                }
                GameButton(words.placeAll, accent = Palette.Heal, dense = true, onClick = {
                    viewModel.autoPlaceMissing { id -> viewModel.squaresPerSideFor(id) }
                })
            }
        }

        // Esito della scelta immagine: conferma di caricamento oppure il motivo del
        // rifiuto (formato, dimensione, file non valido). Un tocco lo congeda.
        portraits.message?.let { note ->
            Text(
                text = note,
                color = Palette.Gold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { portraits.dismissMessage() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    if (compact && boardTools.toolboxOpen) {
        ModalBottomSheet(onDismissRequest = { boardTools.toolboxOpen = false }) {
            BoardToolboxPanel(
                state = boardTools,
                board = board,
                compact = true,
                onSelect = onBoardToolSelected,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    if (showMapPicker) {
        MapPickerDialog(
            portraits = portraits,
            currentImage = viewModel.battleMap.backgroundImage(),
            onChoose = { viewModel.setMapBackground(it) },
            onRemove = { viewModel.setMapBackground("") },
            onDismiss = { showMapPicker = false },
        )
    }
}

/**
 * Dimensione minima e massima di una casella, in dp.
 *
 * Un minimo di 1 dp permette anche alla griglia massima (400 × 400) di entrare
 * interamente in un viewport desktop comune. Le caselle diventano volutamente
 * molto piccole: a questa scala lo scopo e' orientarsi sulla mappa completa,
 * prima di ingrandire di nuovo la zona di gioco.
 */
internal const val MIN_CELL_DP = 1f
internal const val MAX_CELL_DP = 140f
internal val MIN_CELL = MIN_CELL_DP.dp
internal val MAX_CELL = MAX_CELL_DP.dp

/** Estremi della luminosita' della griglia: mai del tutto invisibile, mai piena. */
internal const val MIN_GRID_BRIGHTNESS = 0.05f
internal const val MAX_GRID_BRIGHTNESS = 1f
