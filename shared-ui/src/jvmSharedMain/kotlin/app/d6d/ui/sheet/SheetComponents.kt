package app.d6d.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.sheet.formatModifier
import app.d6d.ui.theme.Palette

/**
 * Riquadro incorniciato con titolo centrato in alto.
 *
 * Ricalca i box della scheda ufficiale, dove ogni sezione ha una cornice propria
 * e un'intestazione centrata in maiuscoletto.
 */
@Composable
fun SheetBox(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .background(Palette.Surface, RoundedCornerShape(9.dp))
            .border(1.dp, Palette.Line, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = Palette.Gold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        content()
    }
}

/**
 * Campo con etichetta sotto la riga di scrittura, come sulla scheda cartacea.
 */
@Composable
fun SheetField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Palette.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(Palette.Gold),
            keyboardOptions = if (numeric) {
                KeyboardOptions(keyboardType = KeyboardType.Number)
            } else {
                KeyboardOptions.Default
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Night, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp),
        )
        Text(
            text = label.uppercase(),
            color = Palette.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Variante numerica: accetta solo interi ed eventuale segno. */
@Composable
fun SheetNumberField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    SheetField(
        label = label,
        value = value.toString(),
        modifier = modifier,
        numeric = true,
    ) { text ->
        val cleaned = text.trim()
        if (cleaned.isEmpty() || cleaned == "-") {
            onChange(0)
        } else {
            cleaned.toIntOrNull()?.let(onChange)
        }
    }
}

/**
 * Valore derivato e non modificabile, mostrato in risalto.
 *
 * Corrisponde alle caselle che sulla scheda si calcolano invece di scriversi:
 * modificatori, iniziativa, Percezione passiva, CD degli incantesimi.
 */
@Composable
fun DerivedValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Gold,
) {
    Column(
        modifier
            .background(Palette.Night, RoundedCornerShape(6.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = value,
            color = accent,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = label.uppercase(),
            color = Palette.TextMuted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Pallino di competenza a tre stati.
 *
 * Vuoto, pieno per competente e cerchiato per Maestria: la scheda 2024 stampa un
 * solo cerchio, ma la Maestria raddoppia il bonus e va comunque rappresentata.
 */
@Composable
fun ProficiencyDot(
    filled: Boolean,
    expertise: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = when {
        expertise -> Palette.GoldBright
        filled -> Palette.Gold
        else -> Palette.TextFaint
    }
    Box(
        modifier
            .size(12.dp)
            .background(if (filled || expertise) color else Color.Transparent, CircleShape)
            .border(if (expertise) 2.dp else 1.dp, color, CircleShape)
            .clickable { onClick() },
    )
}

/** Casella spuntabile con etichetta, come i rombi delle competenze in armatura. */
@Composable
fun SheetCheck(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier.clickable { onChange(!checked) },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (checked) "◆" else "◇",
            color = if (checked) Palette.Gold else Palette.TextFaint,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = label,
            color = if (checked) Palette.Text else Palette.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Serie di caselle consumabili, come i tre successi e i tre fallimenti dei tiri
 * contro morte o gli slot incantesimo spesi.
 */
@Composable
fun PipRow(
    total: Int,
    filled: Int,
    modifier: Modifier = Modifier,
    color: Color = Palette.Gold,
    onSet: (Int) -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { index ->
            val on = index < filled
            Text(
                text = if (on) "◆" else "◇",
                color = if (on) color else Palette.TextFaint,
                style = MaterialTheme.typography.bodyMedium,
                // Ricliccare la casella gia' accesa la spegne: cosi' si corregge
                // un tocco sbagliato senza azzerare tutta la riga.
                modifier = Modifier.clickable { onSet(if (filled == index + 1) index else index + 1) },
            )
        }
    }
}

/** Riga testuale multilinea per i blocchi liberi della scheda. */
@Composable
fun SheetTextArea(
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 4,
    onChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = Palette.Text, fontSize = 12.5.sp, lineHeight = 17.sp),
        cursorBrush = SolidColor(Palette.Gold),
        minLines = minLines,
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.Night, RoundedCornerShape(5.dp))
            .padding(7.dp),
    )
}

/** Etichetta e valore su una riga, per le voci compatte dello stat block. */
@Composable
fun StatLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = Palette.Gold,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            color = Palette.Text,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Formatta un bonus col segno, come compare ovunque sulla scheda. */
fun signed(value: Int): String = formatModifier(value)
