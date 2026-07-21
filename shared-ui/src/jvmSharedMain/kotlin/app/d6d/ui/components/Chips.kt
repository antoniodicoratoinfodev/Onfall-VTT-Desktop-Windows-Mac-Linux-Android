package app.d6d.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.d6d.domain.combat.ConditionType
import app.d6d.ui.theme.Palette

/** Nome italiano delle condizioni. Le quindici dello SRD piu' il caso personalizzato. */
val ConditionType.italianLabel: String
    get() = when (this) {
        ConditionType.BLINDED -> "Accecato"
        ConditionType.CHARMED -> "Affascinato"
        ConditionType.DEAFENED -> "Assordato"
        ConditionType.EXHAUSTION -> "Sfinimento"
        ConditionType.FRIGHTENED -> "Spaventato"
        ConditionType.GRAPPLED -> "Afferrato"
        ConditionType.INCAPACITATED -> "Incapacitato"
        ConditionType.INVISIBLE -> "Invisibile"
        ConditionType.PARALYZED -> "Paralizzato"
        ConditionType.PETRIFIED -> "Pietrificato"
        ConditionType.POISONED -> "Avvelenato"
        ConditionType.PRONE -> "Prono"
        ConditionType.RESTRAINED -> "Trattenuto"
        ConditionType.STUNNED -> "Stordito"
        ConditionType.UNCONSCIOUS -> "Privo di sensi"
        ConditionType.CUSTOM -> "Personalizzata"
    }

/** Glifo compatto, per leggere le condizioni a colpo d'occhio nelle barre laterali. */
val ConditionType.glyph: String
    get() = when (this) {
        ConditionType.BLINDED -> "◍"
        ConditionType.CHARMED -> "♥"
        ConditionType.DEAFENED -> "◔"
        ConditionType.EXHAUSTION -> "▼"
        ConditionType.FRIGHTENED -> "!"
        ConditionType.GRAPPLED -> "⊗"
        ConditionType.INCAPACITATED -> "∅"
        ConditionType.INVISIBLE -> "◌"
        ConditionType.PARALYZED -> "≡"
        ConditionType.PETRIFIED -> "◆"
        ConditionType.POISONED -> "☠"
        ConditionType.PRONE -> "▂"
        ConditionType.RESTRAINED -> "⛓"
        ConditionType.STUNNED -> "✷"
        ConditionType.UNCONSCIOUS -> "z"
        ConditionType.CUSTOM -> "•"
    }

/** Le condizioni che tolgono il turno meritano risalto visivo maggiore. */
val ConditionType.severe: Boolean
    get() = this in setOf(
        ConditionType.PARALYZED,
        ConditionType.PETRIFIED,
        ConditionType.STUNNED,
        ConditionType.UNCONSCIOUS,
        ConditionType.INCAPACITATED,
    )

@Composable
fun Chip(
    text: String,
    color: Color = Palette.TextMuted,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
) {
    Text(
        text = text,
        color = color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(5.dp))
            .border(1.dp, color.copy(alpha = 0.34f), RoundedCornerShape(5.dp))
            .padding(padding),
    )
}

@Composable
fun ConditionChip(type: ConditionType, rounds: Int = 0, modifier: Modifier = Modifier) {
    val color = if (type.severe) Palette.Critical else Palette.Bloodied
    val suffix = if (rounds > 0) " ${rounds}r" else ""
    Chip(text = "${type.glyph} ${type.italianLabel}$suffix", color = color, modifier = modifier)
}

@Composable
fun Eyebrow(text: String, color: Color = Palette.Gold, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
    )
}
