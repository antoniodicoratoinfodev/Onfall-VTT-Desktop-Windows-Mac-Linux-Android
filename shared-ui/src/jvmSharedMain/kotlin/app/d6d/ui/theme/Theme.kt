package app.d6d.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.d6d.ui.generated.resources.Res
import app.d6d.ui.generated.resources.alegreya_bold
import app.d6d.ui.generated.resources.alegreya_medium
import app.d6d.ui.generated.resources.cinzel_bold
import app.d6d.ui.generated.resources.cinzel_extrabold
import org.jetbrains.compose.resources.Font

/**
 * Palette originale dell'applicazione.
 *
 * Nessun colore, logo o elemento grafico proviene dai manuali commerciali: il
 * paragrafo 17 del documento vieta di includere grafica protetta, quindi tutta
 * l'identita' visiva e' disegnata qui.
 *
 * Estetica: dark fantasy essenziale, costruita su nero, grafite e acciaio. Le
 * superfici sono neutre e l'accento primario e' un blu ardesia desaturato
 * (selezioni, turni attivi, azioni principali). Le fazioni restano leggibili a
 * colpo d'occhio: azzurro freddo per gli alleati, rosso per gli avversari. Le
 * tinte forti portano informazione, non decorazione.
 */
object Palette {
    // Fondali: nero vero e grafiti neutre, senza sottotinta gialla o marrone.
    // I gradini restano abbastanza distinti da separare shell, pannelli e card.
    val Abyss = Color(0xFF030405)
    val Night = Color(0xFF080A0D)
    val Surface = Color(0xFF0F1216)
    val SurfaceHigh = Color(0xFF181C22)
    val Line = Color(0xFF2B313A)
    // Estremo superiore della luminosita' della griglia: bianco puro. Ai valori
    // intermedi il reticolo conserva l'acciaio scuro di `Line`; soltanto vicino
    // al 100% diventa davvero bianco e luminoso.
    val LineBright = Color.White

    // Mantiene il vecchio nome per non propagare un cambio puramente cosmetico
    // nell'intera UI: ora e' acciaio scuro per cornici e fregi, non bronzo.
    val Bronze = Color(0xFF485462)

    // Anche questi nomi restano stabili per compatibilita' interna: la famiglia
    // cromatica e' ora ardesia fredda, usata soltanto per interazione e focus.
    val Gold = Color(0xFF8FA7C4)
    val GoldBright = Color(0xFFCBD7E6)
    val GoldDim = Color(0xFF586B82)

    /**
     * Turno corrente nelle barre Squadra/Nemici: oro caldo ripreso dai riflessi
     * del puntatore. Resta separato dall'accento ardesia e dal bianco usato per
     * la scheda semplicemente in esame.
     */
    val Turn = Color(0xFFC9A45C)
    val TurnBright = Color(0xFFEDD595)

    // Testo quasi bianco e secondari freddi, per una lettura pulita sul nero.
    val Text = Color(0xFFECEFF4)
    val TextMuted = Color(0xFFA5ADB8)
    // Usato per informazioni secondarie, non soltanto per controlli disabilitati:
    // resta discreto ma mantiene un contrasto leggibile sulle superfici scure.
    val TextFaint = Color(0xFF7D8793)

    /** Fazione alleata: azzurro freddo, distinto dall'accento e dal rosso nemico. */
    val Party = Color(0xFF82B5D8)

    /** Fazione avversaria: rosso netto, riservato a nemici ed errori. */
    val Enemy = Color(0xFFDB6A6A)

    /** Anteprima della gittata in hover: ambra desaturato, caldo ma discreto. */
    val RangePreview = Color(0xFFB6926B)

    // I colori caldi sopravvivono solo come informazione semantica: arancio per
    // l'avvertimento e rosso per una condizione critica.
    val Healthy = Color(0xFFC7D1DC)
    val Bloodied = Color(0xFFD18B57)
    val Critical = Color(0xFFD95353)

    /** Punti ferita temporanei: assorbono per primi, quindi hanno colore proprio. */
    val Temporary = Color(0xFF72B3C8)

    val Crit = Color(0xFFB99ADD)
    val Heal = Color(0xFF75B98A)
}

/** Colore della barra dei PF in base alla soglia. `Bloodied` e' meta' dei PF massimi. */
fun healthColor(current: Int, max: Int): Color {
    if (max <= 0) return Palette.Critical
    val ratio = current.toFloat() / max
    return when {
        ratio <= 0.25f -> Palette.Critical
        ratio <= 0.5f -> Palette.Bloodied
        else -> Palette.Healthy
    }
}

private val DarkScheme = darkColorScheme(
    primary = Palette.Gold,
    onPrimary = Palette.Abyss,
    secondary = Palette.Party,
    onSecondary = Palette.Abyss,
    error = Palette.Critical,
    background = Palette.Night,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextMuted,
    outline = Palette.Line,
)

// Titoli e intestazioni in graziato (serif), corpo e comandi in lineare: e' il
// contrasto tipografico da manuale di gioco che definisce l'estetica.
//
// Le famiglie sono incorporate come risorse (vedi NOTICE-FONTS.md), cosi' il
// carattere e' identico su desktop e Android: Cinzel — capitali da iscrizione,
// le minuscole diventano maiuscoletto — per le etichette e il display; Alegreya
// per i titoli correnti e i nomi. Il corpo resta nel lineare di sistema.
private fun appTypography(display: FontFamily, title: FontFamily) = Typography(
    displaySmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.8.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = title,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        letterSpacing = 0.3.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = title,
        fontWeight = FontWeight.Bold,
        fontSize = 16.5.sp,
        letterSpacing = 0.2.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = title,
        fontWeight = FontWeight.Bold,
        fontSize = 14.5.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.5.sp,
        color = Palette.TextMuted,
    ),
    labelMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 2.2.sp,
    ),
)

// Spigoli piu' netti dei predefiniti Material: il pannello deve sembrare
// intagliato, non gonfiato. Gli arrotondamenti restano piccoli e costanti.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // `Font` di Compose Resources tiene una cache interna: costruire le famiglie
    // qui non ricarica i file a ogni ricomposizione.
    val display = FontFamily(
        Font(Res.font.cinzel_bold, weight = FontWeight.Bold),
        Font(Res.font.cinzel_extrabold, weight = FontWeight.Black),
    )
    val title = FontFamily(
        Font(Res.font.alegreya_medium, weight = FontWeight.Medium),
        Font(Res.font.alegreya_bold, weight = FontWeight.Bold),
    )
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = appTypography(display, title),
        shapes = AppShapes,
        content = content,
    )
}
