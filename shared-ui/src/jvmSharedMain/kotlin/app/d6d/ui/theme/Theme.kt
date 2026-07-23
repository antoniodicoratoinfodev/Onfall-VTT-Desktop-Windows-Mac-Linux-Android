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
 * Estetica: dark fantasy da tavolo virtuale. I fondali sono neri caldi, come una
 * sala illuminata a candela; l'accento primario e' un oro antico (selezioni,
 * turni attivi, azioni principali); il testo e' avorio da pergamena. Le fazioni
 * restano leggibili a colpo d'occhio: acciaio freddo per gli alleati, rosso
 * brace per gli avversari. Le tinte forti portano informazione, non decorazione.
 */
object Palette {
    // Fondali: dal nero fondo della sala alle superfici in rilievo. Tutti con la
    // stessa sottotinta bruna, cosi' l'insieme scalda senza mai diventare marrone.
    val Abyss = Color(0xFF0B0908)
    val Night = Color(0xFF14110D)
    val Surface = Color(0xFF1C1813)
    val SurfaceHigh = Color(0xFF282218)
    val Line = Color(0xFF3B3325)

    // Bronzo per gli ornamenti: piu' luminoso della linea ma piu' spento
    // dell'oro, e' il metallo "a riposo" delle cornici e dei fregi.
    val Bronze = Color(0xFF5C4E33)

    // L'accento primario e' oro antico vero e proprio: stati selezionati, turno
    // attivo e azioni principali. `GoldBright` e' il bagliore per i risalti.
    val Gold = Color(0xFFC9A45C)
    val GoldBright = Color(0xFFEDD595)
    val GoldDim = Color(0xFF8C7648)

    // Testo color pergamena: caldo ma quasi neutro, resta riposante da leggere.
    val Text = Color(0xFFEAE3D2)
    val TextMuted = Color(0xFFA89E89)
    // Usato per informazioni secondarie, non soltanto per controlli disabilitati:
    // resta discreto ma mantiene un contrasto leggibile sulle superfici scure.
    val TextFaint = Color(0xFF6F6756)

    /** Fazione alleata: acciaio freddo, distinto dall'oro e dal rosso nemico. */
    val Party = Color(0xFFA9C0D6)

    /** Fazione avversaria: rosso brace, l'unico colore caldo acceso. */
    val Enemy = Color(0xFFD25C4D)

    // Soglie dei punti ferita: pergamena finche' si sta bene, ambra da
    // insanguinato, rosso sangue in condizioni critiche.
    val Healthy = Color(0xFFDDD3B8)
    val Bloodied = Color(0xFFCF8D45)
    val Critical = Color(0xFFC23B2E)

    /** Punti ferita temporanei: assorbono per primi, quindi hanno colore proprio. */
    val Temporary = Color(0xFF8FB9C6)

    val Crit = Color(0xFFF0CE74)
    val Heal = Color(0xFF86BA7C)
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
