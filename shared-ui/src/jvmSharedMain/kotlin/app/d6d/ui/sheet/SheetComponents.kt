package app.d6d.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.sheet.formatModifier
import app.d6d.sheet.i18n.distanceValue
import app.d6d.sheet.i18n.distanceUnit
import app.d6d.sheet.i18n.feetFromDistance
import app.d6d.sheet.i18n.parseDistance
import app.d6d.ui.i18n.currentLanguage
import app.d6d.ui.i18n.strings
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
    /** Ammette anche i decimali: sul tocco cambia la tastiera che si apre. */
    decimal: Boolean = false,
    onFocusLost: (() -> Unit)? = null,
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
            keyboardOptions = when {
                decimal -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                numeric -> KeyboardOptions(keyboardType = KeyboardType.Number)
                else -> KeyboardOptions.Default
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Night, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 7.dp)
                .semantics { contentDescription = label }
                .onFocusChanged { state ->
                    if (!state.isFocused) onFocusLost?.invoke()
                },
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
    var draft by remember(value) { mutableStateOf(value.toString()) }
    SheetField(
        label = label,
        value = draft,
        modifier = modifier,
        numeric = true,
        onFocusLost = {
            if (draft.toIntOrNull() == null) draft = value.toString()
        },
    ) { text ->
        val cleaned = text.trim()
        if (cleaned.matches(Regex("-?\\d*"))) {
            draft = cleaned
            cleaned.toIntOrNull()?.let(onChange)
        }
    }
}

/**
 * Distanza nell'unita' della lingua corrente.
 *
 * Il motore misura in piedi interi, ma quella e' una sua contabilita' interna:
 * in italiano qui si scrive e si legge in metri, con la conversione del
 * regolamento; in inglese si usano direttamente i piedi. I decimali servono
 * davvero nel sistema metrico — 1,5 e 4,5 sono misure ordinarie — quindi il
 * campo accetta la virgola oltre al punto.
 */
@Composable
fun SheetMetreField(
    label: String,
    feet: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val words = strings.sheet
    val language = currentLanguage
    var draft by remember(language) { mutableStateOf(distanceValue(feet, language)) }
    // Il testo NON puo' essere ricreato a ogni cambio di `feet`: il valore che
    // arriva qui e' spesso il risultato della battitura in corso, e riscriverlo
    // in forma canonica sotto le dita cancellerebbe la misura a meta'. Chi digita
    // «1,5» passa per «1», che vale 3 piedi: rigenerare il testo lo riporterebbe
    // a «0,9» al secondo carattere. Si riallinea solo quando il valore cambia
    // davvero da fuori — un Undo, il caricamento di un'altra scheda.
    LaunchedEffect(feet, language) {
        if (distanceDraftIsStale(draft, feet, language)) {
            draft = distanceValue(feet, language)
        }
    }
    SheetField(
        label = words.labelWithUnit(label, distanceUnit(language)),
        value = draft,
        modifier = modifier,
        decimal = true,
        // Uscendo dal campo la misura torna in forma canonica: «1,» diventa «0,9»
        // e «1.5» diventa «1,5», cosi' a schermo resta cio' che la scheda conserva.
        onFocusLost = { draft = distanceValue(feet, language) },
    ) { text ->
        val cleaned = text.trim()
        if (cleaned.matches(Regex("""-?\d*(?:[,.]\d*)?"""))) {
            draft = cleaned
            parseDistance(cleaned)?.let { onChange(feetFromDistance(it, language)) }
        }
    }
}

/**
 * Vero quando il testo a schermo non descrive piu' la misura della scheda.
 *
 * Il confronto avviene in piedi, non fra stringhe: «1.5», «1,5» e «1,50» sono la
 * stessa misura scritta in tre modi, e nessuna delle tre va sostituita mentre
 * l'utente sta scrivendo.
 */
internal fun distanceDraftIsStale(
    draft: String,
    feet: Int,
    language: app.d6d.i18n.AppLanguage,
): Boolean = parseDistance(draft)?.let { feetFromDistance(it, language) } != feet

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
    val words = strings.sheet
    val color = when {
        expertise -> Palette.GoldBright
        filled -> Palette.Gold
        else -> Palette.TextFaint
    }
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .semantics {
                role = Role.Checkbox
                selected = filled || expertise
                stateDescription = when {
                    expertise -> words.expertise
                    filled -> words.proficient
                    else -> words.notProficient
                }
            }
            .clickable(role = Role.Checkbox) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(if (filled || expertise) color else Color.Transparent, CircleShape)
                .border(if (expertise) 2.dp else 1.dp, color, CircleShape),
        )
    }
}

/** Casella spuntabile con etichetta, come i rombi delle competenze in armatura. */
@Composable
fun SheetCheck(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    val words = strings.sheet
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, role = Role.Checkbox) { onChange(it) }
            .semantics { stateDescription = if (checked) words.selected else words.notSelected }
            .padding(vertical = 5.dp),
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
    val words = strings.sheet
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { index ->
            val on = index < filled
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .size(44.dp)
                    .semantics {
                        role = Role.Checkbox
                        selected = on
                        stateDescription = if (on) words.marked else words.notMarked
                    }
                    // Ricliccare la casella gia' accesa la spegne: cosi' si corregge
                    // un tocco sbagliato senza azzerare tutta la riga.
                    .clickable(role = Role.Checkbox) {
                        onSet(if (filled == index + 1) index else index + 1)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (on) "◆" else "◇",
                    color = if (on) color else Palette.TextFaint,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
