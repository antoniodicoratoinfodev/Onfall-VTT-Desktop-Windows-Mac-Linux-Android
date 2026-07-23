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

/**
 * Palette originale dell'applicazione.
 *
 * Nessun colore, logo o elemento grafico proviene dai manuali commerciali: il
 * paragrafo 17 del documento vieta di includere grafica protetta, quindi tutta
 * l'identita' visiva e' disegnata qui.
 *
 * Estetica: neri neutri da tavolo virtuale, avorio come accento principale
 * (stati selezionati e azioni primarie), argento per gli alleati e salmone per
 * gli avversari. Le tinte forti restano solo dove portano informazione.
 */
object Palette {
    val Abyss = Color(0xFF0A0A0C)
    val Night = Color(0xFF131315)
    val Surface = Color(0xFF1B1B1E)
    val SurfaceHigh = Color(0xFF252529)
    val Line = Color(0xFF2F2F34)

    // "Gold" resta il nome storico dell'accento primario: oggi e' un avorio
    // quasi bianco, usato per selezioni, turni attivi e azioni principali.
    val Gold = Color(0xFFF0EDE6)
    val GoldBright = Color(0xFFFFFFFF)
    val GoldDim = Color(0xFF8E8B84)

    val Text = Color(0xFFF1F1F0)
    val TextMuted = Color(0xFFA2A2A8)
    // Usato per informazioni secondarie, non soltanto per controlli disabilitati:
    // resta discreto ma mantiene un contrasto leggibile sulle superfici scure.
    val TextFaint = Color(0xFF75757B)

    /** Fazione alleata: argento freddo, leggibile ma distinto dall'avorio. */
    val Party = Color(0xFFD9DFE7)

    /** Fazione avversaria: salmone acceso, l'unico colore caldo dominante. */
    val Enemy = Color(0xFFE06E66)

    val Healthy = Color(0xFFECEAE3)
    val Bloodied = Color(0xFFD8A55F)
    val Critical = Color(0xFFE2564E)

    /** Punti ferita temporanei: assorbono per primi, quindi hanno colore proprio. */
    val Temporary = Color(0xFFA3D5DC)

    val Crit = Color(0xFFF2D68C)
    val Heal = Color(0xFF8FD6A8)
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
private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 0.5.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.3.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.3.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
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
    labelSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        letterSpacing = 1.6.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(5.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
