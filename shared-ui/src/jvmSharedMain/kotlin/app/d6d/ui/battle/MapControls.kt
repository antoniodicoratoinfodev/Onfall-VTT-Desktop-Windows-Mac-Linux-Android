package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import app.d6d.sheet.feetWithMetres
import app.d6d.ui.components.Chip
import app.d6d.ui.images.PortraitRepository
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapControls(
    viewModel: BattleViewModel,
    portraits: PortraitRepository,
    cellSize: Dp,
    onCellSizeChange: (Dp) -> Unit,
    showGrid: Boolean,
    onShowGridChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = viewModel.battleMap.grid()
    var expanded by remember { mutableStateOf(false) }

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
                Text("MAPPA", color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
                Chip("${grid.columns()} × ${grid.rows()} · ${feetWithMetres(grid.feetPerSquare())}/casella", Palette.TextMuted)
            }
            GameButton("−", accent = Palette.TextMuted, dense = true, onClick = {
                onCellSizeChange((cellSize - 6.dp).coerceIn(MIN_CELL, MAX_CELL))
            })
            Chip("Zoom ${cellSize.value.toInt()}", Palette.TextMuted)
            GameButton("+", accent = Palette.TextMuted, dense = true, onClick = {
                onCellSizeChange((cellSize + 6.dp).coerceIn(MIN_CELL, MAX_CELL))
            })
            GameButton(
                label = if (showGrid) "Griglia visibile" else "Griglia nascosta",
                accent = if (showGrid) Palette.Gold else Palette.TextFaint,
                selected = showGrid,
                dense = true,
                onClick = { onShowGridChange(!showGrid) },
            )
            GameButton(
                label = if (expanded) "Nascondi opzioni" else "Opzioni mappa",
                accent = if (expanded) Palette.Gold else Palette.TextMuted,
                selected = expanded,
                dense = true,
                onClick = { expanded = !expanded },
            )
        }

        if (expanded) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                GameButton("− colonne", accent = Palette.TextMuted, dense = true, onClick = {
                    viewModel.configureMap(grid.columns() - 5, grid.rows(), grid.feetPerSquare())
                })
                GameButton("+ colonne", accent = Palette.TextMuted, dense = true, onClick = {
                    viewModel.configureMap(grid.columns() + 5, grid.rows(), grid.feetPerSquare())
                })
                GameButton("− righe", accent = Palette.TextMuted, dense = true, onClick = {
                    viewModel.configureMap(grid.columns(), grid.rows() - 5, grid.feetPerSquare())
                })
                GameButton("+ righe", accent = Palette.TextMuted, dense = true, onClick = {
                    viewModel.configureMap(grid.columns(), grid.rows() + 5, grid.feetPerSquare())
                })

                listOf(5, 10, 20, 50).forEach { feet ->
                    GameButton(
                        label = "${feetWithMetres(feet)}/casella",
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
                GameButton("Scegli sfondo", accent = Palette.Party, dense = true, onClick = {
                    portraits.pickBackgroundAsync { stored ->
                        stored?.let { viewModel.setMapBackground(it) }
                    }
                })
                if (viewModel.battleMap.backgroundImage().isNotBlank()) {
                    GameButton("Togli sfondo", accent = Palette.TextFaint, dense = true, onClick = {
                        viewModel.setMapBackground("")
                    })
                }
                GameButton("Disponi tutti", accent = Palette.Heal, dense = true, onClick = {
                    viewModel.autoPlaceMissing { id -> viewModel.squaresPerSideFor(id) }
                })
            }
        }
    }
}

/** Dimensione minima e massima di una casella, in dp. */
private val MIN_CELL = 14.dp
private val MAX_CELL = 140.dp
