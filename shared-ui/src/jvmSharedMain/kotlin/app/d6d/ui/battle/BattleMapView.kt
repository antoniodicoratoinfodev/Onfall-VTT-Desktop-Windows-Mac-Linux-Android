package app.d6d.ui.battle

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.domain.space.TokenPlacement
import app.d6d.ui.components.Faction
import app.d6d.ui.components.FloatingNumberView
import app.d6d.ui.components.color
import app.d6d.ui.components.initials
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.images.rememberBitmap
import app.d6d.ui.images.rememberPortrait
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.healthColor

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
) {
    if (!viewModel.mapConfigured) {
        MapNotConfigured(viewModel, modifier)
        return
    }

    val map = viewModel.battleMap
    val grid = map.grid()
    val density = LocalDensity.current
    val background = portraits.rememberBitmap(map.backgroundImage())

    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Abyss)
            .horizontalScroll(rememberScrollState())
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            Modifier
                .width(cellSize * grid.columns())
                .height(cellSize * grid.rows())
                .pointerInput(grid, cellSize, viewModel.activeCombatantIds) {
                    detectTapGestures { offset ->
                        val cellPx = with(density) { cellSize.toPx() }
                        val column = (offset.x / cellPx).toInt()
                        val row = (offset.y / cellPx).toInt()
                        onCellTapped(viewModel, column, row)
                    }
                },
        ) {
            // Lo sfondo si adatta alla griglia: e' la griglia a definire la scala,
            // non l'immagine, altrimenti le distanze non corrisponderebbero.
            if (background != null) {
                Image(
                    bitmap = background,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (showGrid) {
                Canvas(Modifier.fillMaxSize()) {
                    val cellPx = size.width / grid.columns()
                    val line = Palette.Line.copy(alpha = if (background != null) 0.55f else 0.35f)
                    for (column in 0..grid.columns()) {
                        val x = column * cellPx
                        drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    }
                    val rowPx = size.height / grid.rows()
                    for (row in 0..grid.rows()) {
                        val y = row * rowPx
                        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                }
            }

            // Raggio di movimento residuo del combattente attivo.
            viewModel.activeCombatantIds.forEach { activeId ->
                viewModel.placementOf(activeId)?.let { placement ->
                    MovementReach(viewModel, placement, cellSize)
                }
            }

            map.orderedPlacements().forEach { placement ->
                MapToken(viewModel, portraits, placement, cellSize)
            }
        }
    }
}

/**
 * Un tocco su una casella libera muove il combattente attivo; su un segnaposto
 * seleziona quel combattente, esattamente come cliccarlo nella barra laterale.
 */
private fun onCellTapped(viewModel: BattleViewModel, column: Int, row: Int) {
    // Un tocco proprio sul bordo destro o inferiore cade fuori griglia per un pixel:
    // va ignorato, non trasformato in un errore di regola mostrato al tavolo.
    val grid = viewModel.battleMap.grid()
    if (column < 0 || row < 0 || column >= grid.columns() || row >= grid.rows()) return

    val occupant = viewModel.occupantAt(column, row)
    if (occupant != null) {
        viewModel.selectedTargetId = occupant
        return
    }
    val active = viewModel.activeCombatantId ?: return
    viewModel.move(active, column, row)
}

/**
 * Alone che mostra fin dove arriva il movimento rimasto.
 *
 * E' un quadrato perche' la griglia conta la diagonale come una casella: il raggio
 * di Chebyshev e' un anello quadrato, non un cerchio.
 *
 * Viene ritagliato ai bordi della griglia. Senza il ritaglio un combattente vicino
 * al bordo produrrebbe uno scostamento negativo e l'alone verrebbe disegnato fuori
 * dalla mappa, perche' Compose non ritaglia i figli di predefinito.
 */
@Composable
private fun MovementReach(viewModel: BattleViewModel, placement: TokenPlacement, cellSize: Dp) {
    val budget = viewModel.budget(placement.combatantId()) ?: return
    val grid = viewModel.battleMap.grid()
    val squares = budget.movementRemainingFeet() / grid.feetPerSquare()
    if (squares <= 0) return

    val origin = placement.origin()
    val startColumn = (origin.column() - squares).coerceAtLeast(0)
    val startRow = (origin.row() - squares).coerceAtLeast(0)
    val endColumn = (origin.column() + placement.squaresPerSide() + squares).coerceAtMost(grid.columns())
    val endRow = (origin.row() + placement.squaresPerSide() + squares).coerceAtMost(grid.rows())
    if (endColumn <= startColumn || endRow <= startRow) return

    Box(
        Modifier
            .offset(x = cellSize * startColumn, y = cellSize * startRow)
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
) {
    val id = placement.combatantId()
    val combatant = viewModel.combatant(id) ?: return
    val snapshot = combatant.snapshot()
    val faction = if (viewModel.isParty(id)) Faction.PARTY else Faction.ENEMY
    val active = viewModel.isActive(id)
    val targeted = viewModel.effectiveTargetId() == id
    val defeated = combatant.defeated()

    val side = cellSize * placement.squaresPerSide()
    // Lo spostamento viene animato: si vede il segnaposto scorrere, non saltare.
    val x by animateDpAsState(cellSize * placement.origin().column(), tween(260, easing = FastOutSlowInEasing), label = "tokenX")
    val y by animateDpAsState(cellSize * placement.origin().row(), tween(260, easing = FastOutSlowInEasing), label = "tokenY")

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
    val portrait = portraits.rememberPortrait(snapshot.definitionId())

    Box(
        Modifier
            .offset(x = x, y = y)
            .size(side)
            .semantics {
                role = Role.Button
                contentDescription = snapshot.name()
                stateDescription = buildString {
                    append("${combatant.currentHitPoints()} su ${snapshot.maxHitPoints()} punti ferita")
                    if (active) append(", turno attivo")
                    if (targeted) append(", bersaglio selezionato")
                    if (defeated) append(", fuori combattimento")
                }
            }
            .clickable(role = Role.Button) { viewModel.selectedTargetId = id }
            .alpha(if (defeated) 0.5f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        // L'immagine viene ritagliata a cerchio dentro l'anello.
        Box(
            Modifier
                .size(side * 0.76f)
                .clip(CircleShape)
                .background(Palette.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (portrait != null) {
                Image(
                    bitmap = portrait,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = if (defeated) "✕" else initials(snapshot.name()),
                    color = if (defeated) Palette.TextFaint else Palette.Text,
                    fontWeight = FontWeight.Black,
                    fontSize = (side.value * 0.26f).sp,
                )
            }
        }

        Canvas(Modifier.size(side)) {
            val stroke = size.minDimension * 0.085f
            val inset = stroke * 1.1f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            if (active && !defeated) {
                drawCircle(accent.copy(alpha = 0.13f + 0.18f * pulse), radius = size.minDimension / 2f)
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
            drawCircle(
                color = when {
                    active -> Palette.GoldBright
                    targeted -> accent
                    else -> accent.copy(alpha = 0.5f)
                },
                radius = size.minDimension / 2f - inset,
                style = Stroke(width = if (active || targeted) 2.2f else 1.2f),
            )
        }

        viewModel.floating[id].orEmpty().forEach { number ->
            FloatingNumberView(number, onExpired = { viewModel.expire(id, it) })
        }
    }
}

/** Invito alla configurazione quando la mappa non esiste ancora. */
@Composable
private fun MapNotConfigured(viewModel: BattleViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Palette.Abyss).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "▦",
            color = Palette.TextFaint,
            fontSize = 46.sp,
        )
        Text(
            text = "Nessuna mappa configurata",
            color = Palette.Text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Senza griglia l'incontro resta una simulazione astratta: " +
                "portate e distanze le dichiara il tavolo, non il motore.",
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
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
