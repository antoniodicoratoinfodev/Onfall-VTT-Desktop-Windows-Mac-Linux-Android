package app.d6d.ui.battle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import app.d6d.sheet.i18n.distanceLabel
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.i18n.strings
import app.d6d.ui.components.Chip
import app.d6d.ui.components.ConditionChip
import app.d6d.ui.components.Faction
import app.d6d.ui.components.HealthBar
import app.d6d.ui.components.color
import app.d6d.ui.images.PortraitRepository
import app.d6d.ui.board.BoardController
import app.d6d.ui.board.BoardTool
import app.d6d.ui.board.BoardToolState
import app.d6d.ui.layout.LocalUiLayout
import app.d6d.ui.settings.LocalAppPreferences
import app.d6d.ui.roster.RosterViewModel
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.ornateFrame

/**
 * Il centro della schermata: mappa tattica con le targhe sovrapposte agli angoli.
 *
 * La mappa prende tutto lo spazio perche' e' li' che si gioca; bersaglio e attore
 * di turno restano leggibili in due riquadri compatti, cosi' non si perde nulla
 * di quello che serve a decidere.
 */
@Composable
fun BattleStage(
    viewModel: BattleViewModel,
    portraits: PortraitRepository,
    modifier: Modifier = Modifier,
    dropTarget: TokenPlacementDrag? = null,
    // Sul desktop le targhe flottanti sono ospitate a tutta schermata dal chiamante,
    // cosi' si possono spostare anche sopra le barre laterali; qui restano nel palco
    // solo nella shell compatta, dove il palco occupa da solo la superficie.
    floatingPlates: Boolean = true,
    board: BoardController,
    roster: RosterViewModel,
    activeSessionId: String,
    compact: Boolean = false,
) {
    val layout = LocalUiLayout.current
    val preferences = LocalAppPreferences.current
    val boardTools = remember(activeSessionId, portraits) {
        BoardToolState(portraits::clearPortrait).apply {
            templateShape = preferences.boardTemplateShape
            stampKind = preferences.boardStampKind
        }
    }
    DisposableEffect(boardTools) {
        onDispose { boardTools.discardPendingToken() }
    }

    val selectBoardTool: (BoardTool) -> Unit = { tool ->
        if (tool != BoardTool.TABLE) {
            viewModel.cancelSingleTargeting()
            viewModel.cancelAreaTargeting()
            viewModel.mapEditMode = false
        }
        // Accendere il livello che si sta per disegnare e' una conseguenza della
        // scelta, non una modifica d'autore: passa da `revealLayers`, che non
        // aggiunge un passo di Undo e non fa risultare la partita da salvare.
        val layers = board.document.layers()
        if (tool == BoardTool.FOG && !layers.fogVisible()) {
            board.revealLayers(layers.withFogVisible(true))
        } else if (tool == BoardTool.FLOOR && !layers.floorsVisible()) {
            board.revealLayers(layers.withFloorsVisible(true))
        } else if (tool == BoardTool.WALL && !layers.wallsVisible()) {
            board.revealLayers(layers.withWallsVisible(true))
        }
        boardTools.select(tool)
        if (compact || !layout.toolboxPinned) boardTools.toolboxOpen = false
    }

    // La modifica dello sfondo e' una sotto-modalità della modifica: chi esce dalla
    // modifica se la porta dietro, cosi' non resta acceso un velo invisibile.
    LaunchedEffect(viewModel.editMode) {
        if (!viewModel.editMode) viewModel.mapEditMode = false
    }
    LaunchedEffect(viewModel.mapEditMode) {
        if (viewModel.mapEditMode) boardTools.table()
    }

    // Confine visivo netto: il fuoco atmosferico anima la shell della Battaglia,
    // ma non deve mai filtrare nella mappa, nella griglia o nei suoi controlli.
    Column(modifier.fillMaxSize().background(Palette.Abyss)) {
        if (viewModel.mapConfigured) {
            MapControls(
                viewModel = viewModel,
                portraits = portraits,
                cellSize = layout.mapCellSize,
                onCellSizeChange = { layout.mapCellSize = it },
                showGrid = layout.mapShowGrid,
                onShowGridChange = { layout.mapShowGrid = it },
                gridBrightness = layout.mapGridBrightness,
                onGridBrightnessChange = { layout.mapGridBrightness = it },
                board = board,
                boardTools = boardTools,
                compact = compact,
                onBoardToolSelected = selectBoardTool,
            )
        }

        BoxWithConstraints(Modifier.weight(1f)) {
            BattleMapView(
                viewModel = viewModel,
                portraits = portraits,
                cellSize = layout.mapCellSize,
                showGrid = layout.mapShowGrid,
                gridBrightness = layout.mapGridBrightness,
                editingBackground = viewModel.editMode && viewModel.mapEditMode,
                modifier = Modifier.fillMaxSize(),
                dropTarget = dropTarget,
                onCellSizeChange = { layout.mapCellSize = it },
                board = board,
                boardTools = boardTools,
            )

            if (layout.toolboxPinned && !compact) {
                val density = LocalDensity.current
                var railHeightPx by remember { mutableStateOf(0) }
                val railHeight = with(density) { railHeightPx.toDp() }
                val travel = (maxHeight - railHeight).coerceAtLeast(0.dp)
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(y = travel * layout.toolboxVerticalFraction)
                        .onGloballyPositioned { railHeightPx = it.size.height }
                        .padding(start = 8.dp, top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "⋮⋮",
                        color = Palette.TextMuted,
                        modifier = Modifier
                            .pointerInput(travel, density.density) {
                                val travelPx = with(density) { travel.toPx() }
                                detectVerticalDragGestures { change, amount ->
                                    change.consume()
                                    if (travelPx > 0f) {
                                        layout.toolboxVerticalFraction =
                                            (layout.toolboxVerticalFraction + amount / travelPx).coerceIn(0f, 1f)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                    )
                    BoardToolRail(boardTools, board, selectBoardTool)
                }
            }

            if (boardTools.toolboxOpen && !compact) {
                BoardToolboxPanel(
                    state = boardTools,
                    board = board,
                    compact = false,
                    onSelect = selectBoardTool,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = if (layout.toolboxPinned) 58.dp else 8.dp, top = 8.dp),
                )
            }

            if (floatingPlates) {
                FloatingCombatantPlates(viewModel)
            }

            if (viewModel.mapConfigured) {
                MapLegend(viewModel, Modifier.align(Alignment.BottomEnd).padding(10.dp))
            }
        }
    }

    SceneTokenDialogHost(boardTools, board, portraits, viewModel, roster, compact)
}

/**
 * Le due targhe — turno attivo e bersaglio selezionato — come pannelli flottanti.
 *
 * Non sono finestre del sistema: sono riquadri dell'app, quindi niente cornice
 * dell'OS. Si afferrano in qualunque punto e si trascinano dove serve, restando
 * dentro il contenitore che le ospita. La posizione e' ricordata qui, sopra i
 * `let`, cosi' resta la stessa anche quando cambia il combattente attivo o il
 * bersaglio: la targa non torna nell'angolo a ogni turno.
 */
@Composable
internal fun BoxScope.FloatingCombatantPlates(viewModel: BattleViewModel) {
    val layout = LocalUiLayout.current
    val words = strings.battle
    val inspectedId = viewModel.inspectedCombatantId?.takeIf { it != viewModel.activeCombatantId }
    val topId = inspectedId ?: viewModel.selectedTargetId

    topId?.let { combatantId ->
        FloatingPanel(Alignment.TopEnd, layout.targetPlate, { layout.targetPlate = it }) {
            // Cambiare scheda in consultazione non e' una perdita o una cura di PF:
            // ricreando lo stato visivo per identita', la nuova barra compare gia'
            // al valore corretto invece di animarsi dai PF del soggetto precedente.
            key(combatantId) {
                StagePlate(
                    viewModel,
                    combatantId,
                    if (inspectedId != null) {
                        if (viewModel.selectedTargetId == combatantId) {
                            words.plateInspectedTarget
                        } else {
                            words.plateInspectedReadOnly
                        }
                    } else {
                        words.plateSelectedTarget
                    },
                    scale = layout.targetPlateScale,
                    onScaleChange = { layout.targetPlateScale = it },
                )
            }
        }
    }

    viewModel.activeCombatantId?.let { activeId ->
        FloatingPanel(Alignment.BottomStart, layout.activePlate, { layout.activePlate = it }) {
            // Un nuovo attore non e' una variazione dei PF del precedente: separando
            // lo stato visivo per identita', la barra nasce subito sul valore giusto
            // e non mostra la scia/animazione di un danno al cambio turno.
            key(activeId) {
                StagePlate(
                    viewModel,
                    activeId,
                    if (viewModel.isSimultaneousTurn) words.plateSharedTurn else words.plateActiveTurn,
                    scale = layout.activePlateScale,
                    onScaleChange = { layout.activePlateScale = it },
                )
            }
        }
    }
}

/**
 * Rende trascinabile il contenuto dentro il Box che lo ospita.
 *
 * Finche' non viene spostato parte dall'angolo indicato da `initialAlignment`,
 * con un piccolo margine; poi segue il trascinamento e non esce mai dai bordi,
 * cosi' non puo' sparire fuori schermo. La posizione ricordata e' una frazione
 * dello spazio libero (0..1), non un pixel: cosi' resta valida anche riaprendo
 * l'app su una finestra di dimensioni diverse.
 */
@Composable
internal fun FloatingPanel(
    initialAlignment: Alignment,
    fraction: Offset?,
    onFractionChange: (Offset) -> Unit,
    content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val inset = with(LocalDensity.current) { 10.dp.roundToPx() }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var parentSize by remember { mutableStateOf(IntSize.Zero) }

    // Spazio in cui la targa puo' scorrere: quanto resta del contenitore una volta
    // sottratto l'ingombro della targa stessa.
    fun freeSpace(): IntSize = IntSize(
        (parentSize.width - panelSize.width).coerceAtLeast(0),
        (parentSize.height - panelSize.height).coerceAtLeast(0),
    )

    // Posizione di partenza: l'angolo richiesto, rientrato del margine.
    fun start(): IntOffset {
        if (parentSize == IntSize.Zero) return IntOffset.Zero
        val space = IntSize(
            (parentSize.width - inset * 2).coerceAtLeast(0),
            (parentSize.height - inset * 2).coerceAtLeast(0),
        )
        val aligned = initialAlignment.align(panelSize, space, layoutDirection)
        return IntOffset(aligned.x + inset, aligned.y + inset)
    }

    // Dalla frazione ricordata al pixel attuale, dentro lo spazio disponibile.
    fun toPixels(f: Offset): IntOffset {
        val free = freeSpace()
        return IntOffset(
            (f.x * free.width).roundToInt().coerceIn(0, free.width),
            (f.y * free.height).roundToInt().coerceIn(0, free.height),
        )
    }

    // Il gestore del trascinamento non va riavviato a ogni spostamento, ma deve
    // leggere sempre la frazione piu' recente: `rememberUpdatedState` la tiene fresca.
    val currentFraction by rememberUpdatedState(fraction)
    // Durante il gesto la posizione resta locale: aggiornare UiLayoutState a ogni
    // evento ricomponeva la shell e riavviava inutilmente il debounce di persistenza.
    // La frazione globale viene pubblicata una volta sola, al rilascio.
    var livePosition by remember { mutableStateOf<Offset?>(null) }

    Box(
        Modifier
            .onGloballyPositioned {
                panelSize = it.size
                parentSize = it.parentLayoutCoordinates?.size ?: parentSize
            }
            .offset {
                livePosition?.let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) }
                    ?: fraction?.let(::toPixels)
                    ?: start()
            }
            .pointerInput(Unit) {
                var dragPosition = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        val base = currentFraction?.let(::toPixels) ?: start()
                        dragPosition = Offset(base.x.toFloat(), base.y.toFloat())
                        livePosition = dragPosition
                    },
                    onDragCancel = { livePosition = null },
                    onDragEnd = {
                        val free = freeSpace()
                        onFractionChange(dragPosition.toPanelFraction(free))
                        livePosition = null
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val free = freeSpace()
                        // Conserva i sottopixel fra un evento e il successivo. Su Windows,
                        // con scaling DPI o mouse ad alta frequenza, i delta sono spesso
                        // frazionari: arrotondarli uno per uno faceva restare la targa molto
                        // indietro rispetto al puntatore.
                        dragPosition = accumulatePanelDrag(dragPosition, amount, free)
                        livePosition = dragPosition
                    },
                )
            },
    ) {
        content()
    }
}

/** Accumula un delta senza perdere i sottopixel e mantiene la targa nel contenitore. */
internal fun accumulatePanelDrag(position: Offset, amount: Offset, freeSpace: IntSize): Offset = Offset(
    x = (position.x + amount.x).coerceIn(0f, freeSpace.width.toFloat()),
    y = (position.y + amount.y).coerceIn(0f, freeSpace.height.toFloat()),
)

/** Converte la posizione corrente nella frazione persistita, gestendo gli assi senza spazio. */
internal fun Offset.toPanelFraction(freeSpace: IntSize): Offset = Offset(
    x = if (freeSpace.width == 0) 0f else x / freeSpace.width,
    y = if (freeSpace.height == 0) 0f else y / freeSpace.height,
)

/**
 * Legenda: scala della griglia e distanza fra attore e bersaglio.
 *
 * La distanza compare solo quando entrambi sono posizionati, perche' senza
 * coordinate complete il documento vieta di dichiararla.
 */
@Composable
private fun MapLegend(viewModel: BattleViewModel, modifier: Modifier = Modifier) {
    val grid = viewModel.battleMap.grid()
    val active = viewModel.activeCombatantId
    val target = viewModel.selectedTargetId
    val distance = if (active != null && target != null) viewModel.distanceFeet(active, target) else null
    val words = strings.battle
    val language = currentLanguage
    val square = distanceLabel(grid.feetPerSquare(), language)
    val description = buildString {
        append(words.mapScaleDescription(square))
        append(
            distance?.let { words.distanceToTargetSentence(distanceLabel(it, language)) }
                ?: words.distanceToTargetUnknownSentence,
        )
    }

    Column(
        modifier
            .background(Palette.Abyss.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Bronze.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = words.oneSquareEquals(square),
            color = Palette.Gold,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = distance?.let { words.distanceToTarget(distanceLabel(it, language)) }
                ?: words.distanceUndetermined,
            color = if (distance != null) Palette.Text else Palette.TextFaint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Targa compatta di un combattente, sovrapposta a un angolo della mappa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StagePlate(
    viewModel: BattleViewModel,
    combatantId: String,
    role: String,
    scale: Float = 1f,
    onScaleChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val combatant = viewModel.combatant(combatantId) ?: return
    val snapshot = combatant.snapshot()
    val faction = if (viewModel.isParty(combatantId)) Faction.PARTY else Faction.ENEMY
    val isTarget = viewModel.selectedTargetId == combatantId
    val isActive = viewModel.isActive(combatantId)
    val isInspected = viewModel.inspectedCombatantId == combatantId && !isActive
    val accent = when {
        isTarget -> faction.color
        isActive -> Palette.Gold
        isInspected -> Palette.TextMuted
        else -> Palette.Gold
    }
    val words = strings.battle
    val state = buildString {
        append(words.hitPointsSentence(combatant.currentHitPoints(), snapshot.maxHitPoints()))
        if (isTarget) append(words.selectedTargetSentence)
        if (isActive) append(words.activeTurnSentence)
        if (isInspected) append(words.inspectedReadOnlySentence)
        if (combatant.dead()) append(words.deadSentence)
        else if (combatant.defeated()) append(words.defeatedSentence)
    }

    val plateShape = RoundedCornerShape(10.dp)
    val outerDensity = LocalDensity.current
    // Scala l'intera densita' interna: cosi' ogni dp e sp del contenuto (nome,
    // barra, chip, cornice) cresce o cala in proporzione e si ricompone nitido,
    // invece di essere ingrandito come un'immagine sfocata.
    val scaledDensity = Density(outerDensity.density * scale, outerDensity.fontScale)

    Box(modifier) {
        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Column(
                Modifier
                    .width(232.dp)
                    // Lastra scura sempre leggibile sopra la mappa; quando e' il bersaglio
                    // vi si sovrappone una velatura del colore di fazione.
                    .background(
                        Brush.verticalGradient(
                            listOf(Palette.Surface.copy(alpha = 0.96f), Palette.Night.copy(alpha = 0.96f)),
                        ),
                        plateShape,
                    )
                    .then(
                        if (isTarget) Modifier.background(faction.color.copy(alpha = 0.10f), plateShape)
                        else Modifier,
                    )
                    .border(
                        if (isTarget) 2.dp else 1.dp,
                        accent.copy(alpha = if (isTarget) 0.9f else 0.58f),
                        plateShape,
                    )
                    // Angoli a squadra dorati: la targa diventa una placca incorniciata.
                    .ornateFrame(accent = accent, alpha = if (isTarget) 0.85f else 0.5f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = words.roleAndName(role, snapshot.name())
                        stateDescription = state
                    }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = role.uppercase(),
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = snapshot.name(),
                    color = Palette.Text,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )

                HealthBar(
                    current = combatant.currentHitPoints(),
                    max = snapshot.maxHitPoints(),
                    temporary = combatant.temporaryHitPoints(),
                    height = 5.dp,
                )

                // FlowRow: quando la targa e' stretta i chip vanno a capo invece di
                // sfondare il bordo.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Chip(words.hitPointsShort(combatant.currentHitPoints(), snapshot.maxHitPoints()), Palette.Text)
                    Chip(words.armorClassShort(snapshot.armorClass()), Palette.Gold)
                    if (combatant.bloodied() && !combatant.defeated() && !combatant.dead()) {
                        Chip(words.bloodiedBadge, Palette.Bloodied)
                    }
                }

                // La taglia non si sceglie qui: e' un'informazione dell'attore, si imposta
                // nel Compendio e da li' determina la dimensione del segnaposto sulla mappa.

                if (combatant.concentration() != null) {
                    Text(
                        text = strings.abilities.concentration,
                        color = Palette.Temporary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (combatant.conditions().isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        combatant.conditions().take(4).forEach {
                            ConditionChip(type = it.type(), rounds = it.duration().remainingOccurrences())
                        }
                    }
                }
            }
        }

        // Maniglia d'angolo: trascinala per ridimensionare la targa. Consuma il
        // proprio trascinamento, quindi non sposta la targa (che si afferra altrove).
        ResizeGrip(
            accent = accent,
            scale = scale,
            referencePx = 120f * outerDensity.density,
            onScaleChange = onScaleChange,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/**
 * Maniglia di ridimensionamento all'angolo in basso a destra di una targa: due
 * trattini diagonali, come i grip di sistema. Il trascinamento arriva in pixel
 * grezzi, quindi e' indipendente dalla scala gia' applicata al contenuto.
 */
@Composable
private fun ResizeGrip(
    accent: Color,
    scale: Float,
    referencePx: Float,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `pointerInput(Unit)` non si riavvia a ogni cambio di scala: legge i valori
    // aggiornati con `rememberUpdatedState`, come il trascinamento della targa. La
    // scala parte da quella d'inizio gesto e vi somma il trascinato, cosi' cresce
    // con continuita' invece di ripartire dal valore iniziale a ogni fotogramma.
    val currentScale by rememberUpdatedState(scale)
    val currentRef by rememberUpdatedState(referencePx)
    val notify by rememberUpdatedState(onScaleChange)
    Box(
        modifier
            .size(22.dp)
            .pointerInput(Unit) {
                var startScale = 1f
                var travelled = 0f
                detectDragGestures(
                    onDragStart = {
                        startScale = currentScale
                        travelled = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        travelled += (amount.x + amount.y) / 2f
                        notify((startScale + travelled / currentRef).coerceIn(0.7f, 1.8f))
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val color = accent.copy(alpha = 0.8f)
            val stroke = 1.6.dp.toPx()
            val w = size.width
            val h = size.height
            // Tre trattini diagonali, come i grip di ridimensionamento di sistema.
            drawLine(color, Offset(w, h * 0.18f), Offset(w * 0.18f, h), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w, h * 0.48f), Offset(w * 0.48f, h), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w, h * 0.78f), Offset(w * 0.78f, h), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}
