package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

    FlowRow(
        modifier
            .fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.75f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("GRIGLIA", color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
            Chip("${grid.columns()} × ${grid.rows()}", Palette.TextMuted)
        }

        GameButton("−col", accent = Palette.TextMuted, onClick = {
            viewModel.configureMap(grid.columns() - 5, grid.rows(), grid.feetPerSquare())
        })
        GameButton("+col", accent = Palette.TextMuted, onClick = {
            viewModel.configureMap(grid.columns() + 5, grid.rows(), grid.feetPerSquare())
        })
        GameButton("−rig", accent = Palette.TextMuted, onClick = {
            viewModel.configureMap(grid.columns(), grid.rows() - 5, grid.feetPerSquare())
        })
        GameButton("+rig", accent = Palette.TextMuted, onClick = {
            viewModel.configureMap(grid.columns(), grid.rows() + 5, grid.feetPerSquare())
        })

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SCALA", color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
            Chip("${grid.feetPerSquare()} ft / casella", Palette.Gold)
        }
        // Le scale utili vanno dal combattimento in stanza alla battaglia campale.
        listOf(5, 10, 20, 50).forEach { feet ->
            GameButton(
                label = "$feet ft",
                accent = if (grid.feetPerSquare() == feet) Palette.GoldBright else Palette.TextFaint,
                onClick = { viewModel.configureMap(grid.columns(), grid.rows(), feet) },
            )
        }

        // Zoom: pulsanti per lo scatto fine e un cursore per spostarsi in fretta
        // fra una casella minuscola e una molto grande.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ZOOM", color = Palette.Gold, style = MaterialTheme.typography.labelSmall)
            Chip("${cellSize.value.toInt()} px", Palette.TextMuted)
        }
        GameButton("−", accent = Palette.TextMuted, onClick = {
            onCellSizeChange((cellSize - 6.dp).coerceIn(MIN_CELL, MAX_CELL))
        })
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
        GameButton("+", accent = Palette.TextMuted, onClick = {
            onCellSizeChange((cellSize + 6.dp).coerceIn(MIN_CELL, MAX_CELL))
        })

        GameButton(
            label = if (showGrid) "▦ Griglia ON" else "▦ Griglia OFF",
            accent = if (showGrid) Palette.Gold else Palette.TextFaint,
            onClick = { onShowGridChange(!showGrid) },
        )

        GameButton("🖼 Sfondo", accent = Palette.Party, onClick = {
            portraits.pickBackground()?.let { viewModel.setMapBackground(it) }
        })
        if (viewModel.battleMap.backgroundImage().isNotBlank()) {
            GameButton("Togli sfondo", accent = Palette.TextFaint, onClick = {
                viewModel.setMapBackground("")
            })
        }

        GameButton("⤢ Disponi tutti", accent = Palette.Heal, onClick = {
            viewModel.autoPlaceMissing { id -> viewModel.squaresPerSideFor(id) }
        })
    }
}

/** Dimensione minima e massima di una casella, in dp. */
private val MIN_CELL = 14.dp
private val MAX_CELL = 140.dp
