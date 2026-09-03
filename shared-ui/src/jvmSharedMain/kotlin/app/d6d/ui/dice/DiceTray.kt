package app.d6d.ui.dice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.D20Mode
import app.d6d.ui.battle.GameButton
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.i18n.strings
import app.d6d.ui.settings.LocalAppPreferences
import app.d6d.ui.state.BattleViewModel
import app.d6d.ui.theme.OnfallTheme
import app.d6d.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val FULL_ROLL_MILLIS = 1_250
private const val REDUCED_ROLL_MILLIS = 280
private const val MAX_ANIMATED_DICE = 20

/** Overlay condiviso dalle shell desktop e Android. */
@Composable
fun DiceTrayHost(
    viewModel: BattleViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val preferences = LocalAppPreferences.current
    LaunchedEffect(preferences.diceRollVisibility) {
        viewModel.applyDiceRollVisibility(preferences.diceRollVisibility)
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = viewModel.diceTrayOpen,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            DiceTray(
                viewModel = viewModel,
                skin = preferences.diceSkin,
                reducedEffects = preferences.reducedDiceEffects,
                compact = compact,
                modifier = if (compact) {
                    Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(6.dp)
                } else {
                    Modifier.widthIn(min = 480.dp, max = 720.dp).heightIn(max = 560.dp).padding(12.dp)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiceTray(
    viewModel: BattleViewModel,
    skin: DiceSkinId,
    reducedEffects: Boolean,
    compact: Boolean,
    modifier: Modifier,
) {
    val words = strings.dice
    val pending = viewModel.pendingLinkedRoll
    val result = viewModel.diceTrayResult
    var sides by remember { mutableIntStateOf(20) }
    var count by remember { mutableIntStateOf(1) }
    var modifierValue by remember { mutableIntStateOf(0) }
    var freeMode by remember { mutableStateOf(D20Mode.NORMAL) }
    var automaticRolling by remember { mutableStateOf(false) }
    val linkedRolling = pending?.started == true
    val rolling = linkedRolling || automaticRolling
    val rollMillis = if (reducedEffects) REDUCED_ROLL_MILLIS else FULL_ROLL_MILLIS

    LaunchedEffect(pending?.id, pending?.started) {
        val active = pending?.takeIf { it.started } ?: return@LaunchedEffect
        delay(rollMillis.toLong())
        viewModel.commitPendingLinkedRoll(active.id)
    }
    LaunchedEffect(result?.id) {
        if (result == null) return@LaunchedEffect
        // I risultati CPU e i tiri liberi sono gia' stati generati quando arrivano
        // qui: li copriamo per la durata del lancio, cosi' anche l'unlinked rotola
        // davvero prima di rivelare il totale. Il linked del giocatore usa invece
        // il ciclo pending sopra, che effettua il commit solo a fine animazione.
        if (viewModel.pendingLinkedRoll != null) return@LaunchedEffect
        automaticRolling = true
        delay(rollMillis.toLong())
        automaticRolling = false
    }

    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Palette.SurfaceHigh.copy(alpha = 0.99f), Palette.Abyss.copy(alpha = 0.99f)),
                ),
            )
            .border(1.dp, skinPalette(skin).edge.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Eyebrow(if (pending != null) words.linked else words.title)
                Text(words.title, color = Palette.Text, style = MaterialTheme.typography.titleLarge)
            }
            GameButton(
                label = words.close,
                enabled = pending == null,
                dense = true,
                accent = Palette.TextMuted,
                onClick = viewModel::closeDiceTray,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DiceLinkMode.LINKED, DiceLinkMode.UNLINKED).forEach { mode ->
                val enabled = mode == DiceLinkMode.UNLINKED || pending != null
                GameButton(
                    label = if (mode == DiceLinkMode.LINKED) words.linked else words.unlinked,
                    selected = viewModel.diceLinkMode == mode,
                    enabled = enabled,
                    dense = true,
                    accent = if (mode == DiceLinkMode.LINKED) Palette.Gold else Palette.TextMuted,
                    onClick = { viewModel.chooseDiceLinkMode(mode) },
                )
            }
        }
        Text(
            text = when {
                viewModel.diceLinkMode == DiceLinkMode.LINKED -> words.linkedHint
                pending == null -> "${words.unlinkedHint} ${words.linkedUnavailable}"
                else -> words.unlinkedHint
            },
            color = Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )

        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (viewModel.diceLinkMode == DiceLinkMode.LINKED && pending != null) {
                LinkedRollControls(viewModel, pending, rolling)
            } else {
                Eyebrow(words.sides)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    DicePoolSpec.SUPPORTED_DICE.forEach { value ->
                        GameButton(
                            label = "d$value",
                            selected = sides == value,
                            dense = true,
                            accent = skinPalette(skin).edge,
                            onClick = { sides = value },
                        )
                    }
                }
                StepperRow(words.quantity, count, 1, DicePoolSpec.MAX_FREE_DICE) { count = it }
                StepperRow(words.modifier, modifierValue, -999, 999) { modifierValue = it }
                Eyebrow(words.normal)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    D20Mode.entries.forEach { mode ->
                        GameButton(
                            label = when (mode) {
                                D20Mode.NORMAL -> words.normal
                                D20Mode.ADVANTAGE -> words.advantage
                                D20Mode.DISADVANTAGE -> words.disadvantage
                            },
                            selected = freeMode == mode,
                            dense = true,
                            onClick = { freeMode = mode },
                        )
                    }
                }
                GameButton(
                    label = words.rollFormula(DicePoolSpec(count, sides, modifierValue, freeMode).notation),
                    primary = true,
                    enabled = !rolling,
                    onClick = {
                        viewModel.rollUnlinkedDice(DicePoolSpec(count, sides, modifierValue, freeMode))
                    },
                )
            }

            result?.let {
                DiceResult(
                    result = it,
                    viewModel = viewModel,
                    skin = skin,
                    rolling = rolling,
                    reducedEffects = reducedEffects,
                    compact = compact,
                )
            }

            if (viewModel.unlinkedDiceHistory.isNotEmpty() && !rolling) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Eyebrow(words.history)
                    GameButton(words.clearHistory, dense = true, accent = Palette.TextMuted,
                        onClick = viewModel::clearUnlinkedDiceHistory)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    viewModel.unlinkedDiceHistory.forEach { historic ->
                        val kept = historic.rolls.firstOrNull { it.kept } ?: historic.rolls.first()
                        Chip("${kept.notation} = ${kept.total}", skinPalette(skin).number)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedRollControls(
    viewModel: BattleViewModel,
    pending: PendingLinkedRoll,
    rolling: Boolean,
) {
    val words = strings.dice
    Eyebrow(words.linked)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pending.rolls.forEach { roll ->
            Chip("${words.purpose(roll.purpose)} · ${roll.notation}", Palette.GoldBright)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        GameButton(
            label = if (rolling) words.rolling else words.roll,
            primary = true,
            enabled = !rolling,
            onClick = viewModel::startPendingLinkedRoll,
        )
        GameButton(
            label = words.cancelLinkedRoll,
            dense = true,
            enabled = !rolling,
            accent = Palette.Bloodied,
            onClick = viewModel::cancelPendingLinkedRoll,
        )
    }
}

@Composable
private fun StepperRow(label: String, value: Int, minimum: Int, maximum: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.TextMuted, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            GameButton("−", dense = true, enabled = value > minimum, onClick = { onChange(value - 1) })
            Text(
                value.toString(),
                color = Palette.Text,
                style = OnfallTheme.typography.numberMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 42.dp),
            )
            GameButton("+", dense = true, enabled = value < maximum, onClick = { onChange(value + 1) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiceResult(
    result: DiceTrayResult,
    viewModel: BattleViewModel,
    skin: DiceSkinId,
    rolling: Boolean,
    reducedEffects: Boolean,
    compact: Boolean,
) {
    val words = strings.dice
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(if (rolling) words.rolling else words.result, skinPalette(skin).number)
        result.rolls.forEach { roll ->
            val actor = roll.actorId.takeIf(String::isNotBlank)?.let(viewModel::name).orEmpty()
            val target = roll.targetId.takeIf(String::isNotBlank)?.let(viewModel::name).orEmpty()
            Column(
                Modifier.fillMaxWidth()
                    .background(Palette.Surface.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        skinPalette(skin).edge.copy(alpha = if (rolling || roll.kept) 0.55f else 0.22f),
                        RoundedCornerShape(8.dp),
                    )
                    .alpha(if (rolling || roll.kept) 1f else 0.5f)
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    words.rollFor(words.purpose(roll.purpose), actor, target),
                    color = Palette.Text,
                    style = OnfallTheme.typography.bodyEmphasis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    roll.values.take(MAX_ANIMATED_DICE).forEachIndexed { index, value ->
                        val selected = rolling || roll.selectedValue == null ||
                            roll.mode == D20Mode.NORMAL || value == roll.selectedValue
                        if (roll.sides == 100) {
                            PercentileDice(value, skin, rolling, reducedEffects, selected && (rolling || roll.kept))
                        } else {
                            DicePiece(
                                sides = roll.sides,
                                label = if (rolling) "?" else value.toString(),
                                skin = skin,
                                rolling = rolling,
                                reducedEffects = reducedEffects,
                                kept = selected && (rolling || roll.kept),
                                phaseOffset = index,
                                compact = compact,
                            )
                        }
                    }
                    val remainder = roll.values.size - MAX_ANIMATED_DICE
                    if (remainder > 0) Chip(words.diceMore(remainder), skinPalette(skin).number)
                }
                Text(
                    if (rolling) "${words.total}: —" else "${words.total}: ${roll.total}",
                    color = if (rolling || roll.kept) skinPalette(skin).number else Palette.TextFaint,
                    style = OnfallTheme.typography.numberMedium,
                )
            }
        }
    }
}

@Composable
private fun PercentileDice(
    value: Int,
    skin: DiceSkinId,
    rolling: Boolean,
    reducedEffects: Boolean,
    kept: Boolean,
) {
    val normalized = if (value == 100) 0 else value
    val tens = (normalized / 10) * 10
    val ones = normalized % 10
    Row(
        Modifier.semantics { contentDescription = "d100: $value" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DicePiece(10, if (rolling) "?" else tens.toString().padStart(2, '0'), skin, rolling,
            reducedEffects, kept, 0, compact = true)
        DicePiece(10, if (rolling) "?" else ones.toString(), skin, rolling,
            reducedEffects, kept, 1, compact = true)
    }
}

private data class DiceSkinPalette(
    val faceTop: Color,
    val faceBottom: Color,
    val edge: Color,
    val number: Color,
    val glow: Color,
    val particle: Color,
)

private fun skinPalette(skin: DiceSkinId): DiceSkinPalette = when (skin) {
    DiceSkinId.RUNIC_OBSIDIAN -> DiceSkinPalette(
        Color(0xFF252136), Color(0xFF08070D), Color(0xFF9D7AE8), Color(0xFFE0D4FF),
        Color(0xFF7B51D8), Color(0xFFB49AF2),
    )
    DiceSkinId.DRAGONFORGE -> DiceSkinPalette(
        Color(0xFF433127), Color(0xFF110B08), Color(0xFFD4874A), Color(0xFFFFD09A),
        Color(0xFFD95335), Color(0xFFFFA54D),
    )
    DiceSkinId.MOON_IVORY -> DiceSkinPalette(
        Color(0xFFE3DDD0), Color(0xFF777D89), Color(0xFFBFD9F6), Color(0xFF07111E),
        Color(0xFF88BFEA), Color(0xFFD9EDFF),
    )
}

@Composable
private fun DicePiece(
    sides: Int,
    label: String,
    skin: DiceSkinId,
    rolling: Boolean,
    reducedEffects: Boolean,
    kept: Boolean,
    phaseOffset: Int,
    compact: Boolean,
) {
    val palette = skinPalette(skin)
    val duration = if (reducedEffects) REDUCED_ROLL_MILLIS else FULL_ROLL_MILLIS
    val rotation by animateFloatAsState(
        targetValue = if (rolling && !reducedEffects) 720f + phaseOffset * 37f else 0f,
        animationSpec = tween(duration),
        label = "diceRotation",
    )
    val scale by animateFloatAsState(
        targetValue = if (rolling) 0.9f else 1f,
        animationSpec = keyframes {
            durationMillis = duration
            0.82f at 0
            1.12f at (duration * 0.35f).toInt()
            0.94f at (duration * 0.72f).toInt()
            1f at duration
        },
        label = "diceBounce",
    )
    val dieSize = if (compact) 45.dp else 54.dp
    Box(
        Modifier
            .size(dieSize)
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (kept) 1f else 0.36f)
            .semantics { contentDescription = "d$sides: $label" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val min = size.minDimension
            if (rolling && !reducedEffects) {
                repeat(7) { index ->
                    val angle = (index * 2.0 * PI / 7.0 + phaseOffset).toFloat()
                    val radius = min * (0.30f + (index % 3) * 0.07f)
                    drawCircle(
                        palette.particle.copy(alpha = 0.34f),
                        radius = min * 0.025f,
                        center = center + androidx.compose.ui.geometry.Offset(cos(angle) * radius, sin(angle) * radius),
                    )
                }
            }
            drawCircle(palette.glow.copy(alpha = if (kept) 0.18f else 0.05f), min * 0.48f)
            drawDieShape(sides, palette)
        }
        Text(
            label,
            color = palette.number,
            style = if (label.length > 1) {
                OnfallTheme.typography.tokenInitials
            } else {
                OnfallTheme.typography.numberMedium
            },
            textAlign = TextAlign.Center,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDieShape(
    sides: Int,
    palette: DiceSkinPalette,
) {
    val radius = size.minDimension * 0.40f
    val vertices = when (sides) {
        4 -> 3
        6 -> 4
        8 -> 4
        10 -> 6
        12 -> 5
        20 -> 6
        else -> 6
    }
    val start = when (sides) {
        4 -> -90f
        6 -> -45f
        8 -> -90f
        10 -> -90f
        12 -> -90f
        else -> -90f
    }
    val points = List(vertices) { index ->
        val angle = Math.toRadians((start + index * 360f / vertices).toDouble())
        androidx.compose.ui.geometry.Offset(
            center.x + cos(angle).toFloat() * radius,
            center.y + sin(angle).toFloat() * radius,
        )
    }
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, Brush.linearGradient(listOf(palette.faceTop, palette.faceBottom)))
    drawPath(path, palette.edge, style = Stroke(size.minDimension * 0.045f))
    when (sides) {
        4 -> points.forEach { drawLine(palette.edge.copy(alpha = 0.5f), center, it, 1.2f) }
        6 -> {
            drawLine(palette.edge.copy(alpha = 0.45f), points[0], points[2], 1.1f)
            drawLine(palette.edge.copy(alpha = 0.30f), points[1], points[3], 1.1f)
        }
        8 -> points.forEach { drawLine(palette.edge.copy(alpha = 0.42f), center, it, 1.1f) }
        10, 20 -> points.forEachIndexed { index, point ->
            if (index % 2 == 0) drawLine(palette.edge.copy(alpha = 0.38f), center, point, 1.1f, StrokeCap.Round)
        }
        12 -> {
            val innerRadius = radius * 0.48f
            val inner = Path().apply {
                points.forEachIndexed { index, point ->
                    val vector = point - center
                    val innerPoint = center + vector * (innerRadius / radius)
                    if (index == 0) moveTo(innerPoint.x, innerPoint.y) else lineTo(innerPoint.x, innerPoint.y)
                }
                close()
            }
            drawPath(inner, palette.edge.copy(alpha = 0.48f), style = Stroke(1.1f))
        }
    }
}
