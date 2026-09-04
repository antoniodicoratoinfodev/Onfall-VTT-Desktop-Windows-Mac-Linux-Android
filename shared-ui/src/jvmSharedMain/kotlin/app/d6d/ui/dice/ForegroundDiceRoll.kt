package app.d6d.ui.dice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.D20Mode
import app.d6d.ui.components.Chip
import app.d6d.ui.components.Eyebrow
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.OnfallTheme
import app.d6d.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val RESULT_HOLD_MILLIS = 2_800L
private const val REDUCED_RESULT_HOLD_MILLIS = 2_200L
private const val EXIT_MILLIS = 320
private const val MAX_CINEMATIC_DICE = 3

internal enum class ForegroundRollPhase {
    READY,
    ROLLING,
    RESULT,
}

/** Tiene il pannello pronto finche' il giocatore tira e il risultato finche' viene chiuso. */
@Composable
internal fun ForegroundDiceRollHost(
    pending: PendingLinkedRoll?,
    result: DiceTrayResult?,
    presentation: DiceRollPresentation,
    skin: DiceSkinId,
    reducedEffects: Boolean,
    compact: Boolean,
    nameOf: (String) -> String,
    onRoll: () -> Unit,
) {
    var lastHandledId by remember { mutableStateOf(result?.id) }
    var foregroundResult by remember { mutableStateOf<DiceTrayResult?>(null) }
    var visible by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(ForegroundRollPhase.READY) }
    val rollMillis = diceRollDurationMillis(reducedEffects, DiceRollPresentation.FOREGROUND)

    LaunchedEffect(presentation, pending?.id, pending?.started) {
        if (presentation != DiceRollPresentation.FOREGROUND) return@LaunchedEffect
        val waiting = pending?.takeIf { !it.started }
        if (waiting != null) {
            foregroundResult = DiceTrayResult(waiting.id, DiceLinkMode.LINKED, waiting.rolls)
            phase = ForegroundRollPhase.READY
            visible = true
        } else if (pending == null && phase == ForegroundRollPhase.READY) {
            visible = false
            delay(EXIT_MILLIS.toLong())
            if (phase == ForegroundRollPhase.READY) foregroundResult = null
        }
    }

    LaunchedEffect(presentation, result?.id, reducedEffects) {
        if (presentation != DiceRollPresentation.FOREGROUND) {
            lastHandledId = result?.id
            visible = false
            foregroundResult = null
            return@LaunchedEffect
        }
        val incoming = result ?: return@LaunchedEffect
        if (incoming.id == lastHandledId) return@LaunchedEffect
        lastHandledId = incoming.id
        foregroundResult = incoming
        phase = ForegroundRollPhase.ROLLING
        visible = true
        delay(rollMillis.toLong())
        phase = ForegroundRollPhase.RESULT
        delay(if (reducedEffects) REDUCED_RESULT_HOLD_MILLIS else RESULT_HOLD_MILLIS)
        visible = false
        delay(EXIT_MILLIS.toLong())
        if (foregroundResult?.id == incoming.id) foregroundResult = null
    }

    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visible = visible && foregroundResult != null,
        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.97f),
        exit = fadeOut(tween(EXIT_MILLIS)) +
            scaleOut(targetScale = 1.015f, animationSpec = tween(EXIT_MILLIS)),
    ) {
        foregroundResult?.let { shown ->
            ForegroundDiceRoll(
                result = shown,
                skin = skin,
                phase = phase,
                reducedEffects = reducedEffects,
                compact = compact,
                nameOf = nameOf,
                onRoll = onRoll,
                onDismiss = { visible = false },
            )
        }
    }
}

internal data class CinematicDieSpec(
    val sides: Int,
    val faceLabels: List<String>,
    val targetFaceIndex: Int,
    val kept: Boolean = true,
    val competing: Boolean = false,
)

internal data class CinematicDiceSelection(
    val primary: PresentedDiceRoll,
    val dice: List<CinematicDieSpec>,
    val remaining: Int,
)

/**
 * Traduce i due modi con cui il motore rappresenta vantaggio e svantaggio:
 *
 * - un Linked d20 contiene due valori nello stesso [PresentedDiceRoll];
 * - un Unlinked contiene due pool alternativi, uno tenuto e uno scartato.
 *
 * In entrambi i casi il primo piano deve animare tutte e due le alternative prima
 * di evidenziare quella scelta dal motore.
 */
internal fun cinematicDiceSelection(result: DiceTrayResult): CinematicDiceSelection {
    val primary = result.rolls.firstOrNull { it.kept } ?: result.rolls.first()
    val alternativePools = result.linkMode == DiceLinkMode.UNLINKED &&
        primary.mode != D20Mode.NORMAL &&
        result.rolls.size == 2 &&
        result.rolls.all { roll ->
            roll.mode == primary.mode &&
                roll.sides == primary.sides &&
                roll.purpose == primary.purpose &&
                roll.values.size == primary.values.size
        }
    val sourceRolls = if (alternativePools) result.rolls else listOf(primary)
    val allDice = sourceRolls.flatMap(::cinematicDice)
    return CinematicDiceSelection(
        primary = primary,
        dice = allDice.take(MAX_CINEMATIC_DICE),
        remaining = (allDice.size - MAX_CINEMATIC_DICE).coerceAtLeast(0),
    )
}

private fun cinematicDice(roll: PresentedDiceRoll): List<CinematicDieSpec> {
    val competing = roll.mode != D20Mode.NORMAL
    return roll.values.take(MAX_CINEMATIC_DICE).mapIndexed { index, value ->
        CinematicDieSpec(
            sides = roll.sides,
            faceLabels = (1..roll.sides).map(Int::toString),
            targetFaceIndex = resultFaceIndex(roll.sides, value),
            kept = roll.keepsDieAt(index),
            competing = competing,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForegroundDiceRoll(
    result: DiceTrayResult,
    skin: DiceSkinId,
    phase: ForegroundRollPhase,
    reducedEffects: Boolean,
    compact: Boolean,
    nameOf: (String) -> String,
    onRoll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val words = strings.dice
    val selection = cinematicDiceSelection(result)
    val primary = selection.primary
    val dice = selection.dice
    val actor = primary.actorId.takeIf(String::isNotBlank)?.let(nameOf).orEmpty()
    val target = primary.targetId.takeIf(String::isNotBlank)?.let(nameOf).orEmpty()

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Palette.Abyss.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        val panelWidth = (maxWidth - 24.dp).coerceAtMost(if (compact) 520.dp else 680.dp)
        val panelHeight = (maxHeight - 24.dp).coerceAtMost(if (compact) 500.dp else 580.dp)
        val shortest = minOf(panelWidth, panelHeight)
        val lightSize = shortest * 0.74f
        val dieSize = (shortest * if (dice.size == 1) 0.36f else 0.29f).coerceAtMost(220.dp)
        val spread = dieSize * 0.58f
        val interaction = remember { MutableInteractionSource() }
        val panelShape = RoundedCornerShape(18.dp)
        val interactionModifier = when (phase) {
            ForegroundRollPhase.READY -> Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onRoll,
            )
            ForegroundRollPhase.RESULT -> Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onDismiss,
            )
            ForegroundRollPhase.ROLLING -> Modifier
        }

        Box(
            Modifier
                .size(panelWidth, panelHeight)
                .shadow(24.dp, panelShape)
                .clip(panelShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Palette.SurfaceHigh.copy(alpha = 0.98f), Palette.Abyss.copy(alpha = 0.97f)),
                    ),
                )
                .border(1.dp, skinPalette(skin).edge.copy(alpha = 0.55f), panelShape)
                .then(interactionModifier)
                .semantics { contentDescription = words.foregroundPresentation },
        ) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 18.dp, vertical = if (compact) 15.dp else 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Eyebrow(
                    if (phase == ForegroundRollPhase.READY) words.readyToRoll
                    else if (result.linkMode == DiceLinkMode.LINKED) words.linked else words.unlinked,
                )
                Text(
                    words.rollFor(words.purpose(primary.purpose), actor, target),
                    color = Palette.Text,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Box(Modifier.size(lightSize).align(Alignment.Center), contentAlignment = Alignment.Center) {
                ForegroundLight(
                    skin = skin,
                    reducedEffects = reducedEffects,
                    modifier = Modifier.fillMaxSize(),
                )
                dice.forEachIndexed { index, die ->
                    val horizontal = (index - (dice.lastIndex / 2f)) * spread.value
                    val vertical = if (index % 2 == 0) -dieSize.value * 0.04f else dieSize.value * 0.08f
                    CinematicDie(
                        spec = die,
                        skin = skin,
                        rollPhase = phase,
                        reducedEffects = reducedEffects,
                        animationId = result.id,
                        phase = index,
                        dieSize = dieSize,
                        modifier = Modifier.offset(horizontal.dp, vertical.dp),
                    )
                }
                if (selection.remaining > 0) {
                    Chip(
                        words.diceMore(selection.remaining),
                        skinPalette(skin).number,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 620.dp)
                    .padding(horizontal = 18.dp, vertical = if (compact) 15.dp else 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    when (phase) {
                        ForegroundRollPhase.READY -> words.clickDieToRoll
                        ForegroundRollPhase.ROLLING -> words.rolling
                        ForegroundRollPhase.RESULT -> "${words.total}: ${primary.total}"
                    },
                    color = skinPalette(skin).number,
                    style = if (phase == ForegroundRollPhase.RESULT) {
                        OnfallTheme.typography.numberLarge
                    } else {
                        OnfallTheme.typography.bodyEmphasis
                    },
                    textAlign = TextAlign.Center,
                )
                if (phase == ForegroundRollPhase.RESULT && result.rolls.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        result.rolls.forEach { roll ->
                            Chip("${words.purpose(roll.purpose)} · ${roll.total}", skinPalette(skin).edge)
                        }
                    }
                }
                if (phase == ForegroundRollPhase.RESULT) {
                    Text(words.clickToDismiss, color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Luce da studio molto discreta: niente anello, rune o immagine ornamentale. */
@Composable
private fun ForegroundLight(
    skin: DiceSkinId,
    reducedEffects: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = skinPalette(skin)
    val movement = rememberInfiniteTransition(label = "foregroundDiceLight")
    val pulse by movement.animateFloat(
        initialValue = if (reducedEffects) 1f else 0.98f,
        targetValue = if (reducedEffects) 1f else 1.02f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "foregroundDiceLightPulse",
    )
    Canvas(modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(palette.glow.copy(alpha = 0.12f), palette.glow.copy(alpha = 0.035f), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.44f * pulse,
            ),
            radius = size.minDimension * 0.44f * pulse,
        )
        drawOval(
            brush = Brush.radialGradient(
                listOf(palette.edge.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(center.x, size.height * 0.67f),
                radius = size.minDimension * 0.34f,
            ),
            topLeft = Offset(size.width * 0.20f, size.height * 0.58f),
            size = Size(size.width * 0.60f, size.height * 0.17f),
        )
        repeat(5) { index ->
            val angle = index * 1.7 + 0.4
            val radius = size.minDimension * (0.18f + index * 0.035f)
            drawCircle(
                color = palette.number.copy(alpha = 0.10f),
                radius = size.minDimension * 0.0045f,
                center = center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius * 0.55f),
            )
        }
    }
}

@Composable
internal fun CinematicDie(
    spec: CinematicDieSpec,
    skin: DiceSkinId,
    rollPhase: ForegroundRollPhase,
    reducedEffects: Boolean,
    animationId: Long,
    phase: Int,
    dieSize: Dp,
    modifier: Modifier = Modifier,
) {
    val palette = skinPalette(skin)
    val progress = remember(animationId, phase) { Animatable(0f) }
    val duration = diceRollDurationMillis(reducedEffects, DiceRollPresentation.FOREGROUND)
    LaunchedEffect(animationId, phase, rollPhase, duration) {
        when (rollPhase) {
            ForegroundRollPhase.READY -> progress.snapTo(0f)
            ForegroundRollPhase.ROLLING -> {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(duration, easing = LinearOutSlowInEasing))
            }
            ForegroundRollPhase.RESULT -> progress.snapTo(1f)
        }
    }
    val value = progress.value
    val jump = if (reducedEffects) 0f else 4f * value * (1f - value)
    val bounce = if (reducedEffects) 0f else sin(value * PI * 6f).toFloat() * (1f - value)
    val skid = if (reducedEffects) 0f else sin(value * PI * 2f).toFloat() * (1f - value)
    val density = LocalDensity.current
    val dieSizePx = with(density) { dieSize.toPx() }
    val resolvedChoice = rollPhase == ForegroundRollPhase.RESULT && spec.competing
    val resolvedScale by animateFloatAsState(
        targetValue = when {
            !resolvedChoice -> 1f
            spec.kept -> 1.08f
            else -> 0.82f
        },
        animationSpec = tween(260, easing = LinearOutSlowInEasing),
        label = "foregroundDieSelectionScale",
    )
    val resolvedAlpha by animateFloatAsState(
        targetValue = if (resolvedChoice && !spec.kept) 0.30f else 1f,
        animationSpec = tween(260),
        label = "foregroundDieSelectionAlpha",
    )
    val resolvedOffset by animateFloatAsState(
        targetValue = when {
            !resolvedChoice -> 0f
            spec.kept -> -0.08f
            else -> 0.08f
        },
        animationSpec = tween(260, easing = LinearOutSlowInEasing),
        label = "foregroundDieSelectionOffset",
    )

    Box(modifier.size(dieSize), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(dieSize)
                .graphicsLayer {
                    translationY = dieSizePx * 0.34f
                    scaleX = 0.44f + 0.24f * (1f - jump)
                    scaleY = 0.13f + 0.05f * (1f - jump)
                    alpha = (0.58f - jump * 0.28f) * resolvedAlpha
                },
        ) {
            drawOval(Color.Black.copy(alpha = 0.88f), topLeft = Offset.Zero, size = size)
        }

        Box(
            Modifier
                .size(dieSize)
                .alpha(resolvedAlpha)
                .graphicsLayer {
                    translationX = skid * dieSizePx * 0.12f * if (phase % 2 == 0) 1f else -1f
                    translationY = (-jump * 0.30f + resolvedOffset) * dieSizePx
                    scaleX = (1f + bounce * 0.07f) * resolvedScale
                    scaleY = (1f - bounce * 0.06f) * resolvedScale
                },
            contentAlignment = Alignment.Center,
        ) {
            PolyhedralDie(
                sides = spec.sides,
                faceLabels = spec.faceLabels,
                targetFaceIndex = spec.targetFaceIndex,
                progress = value,
                phaseOffset = phase,
                reducedEffects = reducedEffects,
                palette = palette,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
