package app.d6d.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.d6d.domain.combat.TurnResource
import app.d6d.ui.components.dismissDialogOnTap
import app.d6d.ui.components.keepDialogOpenOnTap
import app.d6d.ui.i18n.strings
import app.d6d.ui.theme.OrnateDivider
import app.d6d.ui.theme.Palette
import app.d6d.ui.theme.ornateFrame
import app.d6d.ui.theme.panelBrush

/** La selezione conserva soltanto la fotografia da mostrare nel dialogo. */
internal sealed interface ResourceEditorTarget {
    val name: String
    val remaining: Int
    val maximum: Int
    val accent: Color

    data class Pool(
        val id: String,
        override val name: String,
        override val remaining: Int,
        override val maximum: Int,
        override val accent: Color,
    ) : ResourceEditorTarget

    data class Turn(
        val resource: TurnResource,
        override val name: String,
        val available: Boolean,
        override val accent: Color,
    ) : ResourceEditorTarget {
        override val remaining: Int = if (available) 1 else 0
        override val maximum: Int = 1
    }
}

/**
 * Editor unico per riserve numeriche e risorse 0/1 del turno.
 *
 * Tenere la stessa grammatica visiva evita che slot, Ispirazione bardica e
 * Azione sembrino correzioni di natura diversa: cambia soltanto se il massimo
 * e' modificabile oppure fissato a uno dalle regole del turno.
 */
@Composable
internal fun ResourceQuantityEditor(
    target: ResourceEditorTarget,
    combatantName: String,
    onConfirm: (remaining: Int, maximum: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val words = strings.battle
    val canEditMaximum = target is ResourceEditorTarget.Pool
    var maximum by remember(target) { mutableIntStateOf(target.maximum.coerceAtLeast(1)) }
    var remaining by remember(target) {
        mutableIntStateOf(target.remaining.coerceIn(0, target.maximum.coerceAtLeast(1)))
    }
    val changed = maximum != target.maximum || remaining != target.remaining

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .dismissDialogOnTap(onDismiss)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .panelBrush(shape)
                    .border(1.dp, target.accent.copy(alpha = 0.72f), shape)
                    .ornateFrame(accent = target.accent, alpha = 0.46f)
                    .keepDialogOpenOnTap()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .background(target.accent.copy(alpha = 0.12f), CircleShape)
                            .border(1.5.dp, target.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$remaining/$maximum",
                            color = target.accent,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = words.resourceEditorEyebrow,
                            color = target.accent,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = words.editResourceFor(target.name, combatantName),
                            color = Palette.Text,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }

                OrnateDivider(color = target.accent.copy(alpha = 0.65f))

                Text(
                    text = if (canEditMaximum) {
                        words.resourceQuantityHelp
                    } else {
                        words.turnResourceQuantityHelp
                    },
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )

                QuantityCard(
                    label = words.availableQuantity,
                    value = remaining,
                    range = 0..maximum,
                    accent = target.accent,
                    onChange = { remaining = it },
                )

                if (canEditMaximum) {
                    QuantityCard(
                        label = words.maximumQuantity,
                        value = maximum,
                        range = 1..MAX_EDITABLE_RESOURCE_QUANTITY,
                        accent = Palette.Gold,
                        onChange = {
                            maximum = it
                            remaining = remaining.coerceAtMost(it)
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GameButton(
                        label = words.exhaustResource,
                        onClick = { remaining = 0 },
                        modifier = Modifier.weight(1f),
                        accent = Palette.TextMuted,
                        selected = remaining == 0,
                    )
                    GameButton(
                        label = words.restoreResource,
                        onClick = { remaining = maximum },
                        modifier = Modifier.weight(1f),
                        accent = target.accent,
                        selected = remaining == maximum,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameButton(
                        label = strings.common.cancel,
                        accent = Palette.TextMuted,
                        onClick = onDismiss,
                    )
                    Box(Modifier.width(8.dp))
                    GameButton(
                        label = strings.common.apply,
                        enabled = changed,
                        primary = true,
                        accent = target.accent,
                        onClick = { onConfirm(remaining, maximum) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuantityCard(
    label: String,
    value: Int,
    range: IntRange,
    accent: Color,
    onChange: (Int) -> Unit,
) {
    val words = strings.battle
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.07f), shape)
            .border(1.dp, accent.copy(alpha = 0.4f), shape)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Palette.Text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        GameButton(
            label = "−",
            enabled = value > range.first,
            dense = true,
            accent = accent,
            modifier = Modifier
                .sizeIn(minWidth = 38.dp, minHeight = 38.dp)
                .semantics { contentDescription = words.decreaseQuantity(label) },
            onClick = { onChange((value - 1).coerceIn(range)) },
        )
        QuantityField(
            value = value,
            range = range,
            accent = accent,
            label = label,
            onChange = onChange,
        )
        GameButton(
            label = "+",
            enabled = value < range.last,
            dense = true,
            accent = accent,
            modifier = Modifier
                .sizeIn(minWidth = 38.dp, minHeight = 38.dp)
                .semantics { contentDescription = words.increaseQuantity(label) },
            onClick = { onChange((value + 1).coerceIn(range)) },
        )
    }
}

@Composable
private fun QuantityField(
    value: Int,
    range: IntRange,
    accent: Color,
    label: String,
    onChange: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    BasicTextField(
        value = value.toString(),
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(3)
            val parsed = digits.toIntOrNull() ?: range.first
            onChange(parsed.coerceIn(range))
        },
        singleLine = true,
        textStyle = TextStyle(
            color = Palette.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .padding(horizontal = 7.dp)
            .width(62.dp)
            .background(Palette.Night, shape)
            .border(1.dp, accent.copy(alpha = 0.8f), shape)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .semantics { contentDescription = label },
    )
}

private const val MAX_EDITABLE_RESOURCE_QUANTITY = 999
