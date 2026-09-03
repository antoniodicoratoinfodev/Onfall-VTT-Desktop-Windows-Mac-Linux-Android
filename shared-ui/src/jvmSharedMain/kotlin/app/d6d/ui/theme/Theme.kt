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

    /**
     * Anello dei PF avversario: un solo rosso a ogni soglia di vita.
     *
     * Sul token la fazione va riconosciuta prima della salute, e le tre tinte di
     * [healthColor] la nascondevano: un nemico intatto portava lo stesso anello
     * chiaro di un alleato intatto. Quanto e' ferito resta leggibile dalla
     * lunghezza dell'arco, che e' l'informazione che l'anello porta davvero.
     *
     * Piu' profondo e saturo di [Enemy], che cinge lo stesso token a un pelo di
     * distanza: due rossi identici e concentrici si leggerebbero come un unico
     * bordo spesso invece che come cerchio e anello.
     */
    val EnemyHealth = Color(0xFFC2453F)

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

// Tre voci, tre compiti. Cinzel porta l'immaginario del gioco ma resta confinato
// alle intestazioni brevi; Alegreya da' ai nomi un tono editoriale; Alegreya Sans
// regge tutto cio' che si deve leggere o azionare. Le tre famiglie sono incorporate
// (vedi NOTICE-FONTS.md), quindi Android e desktop mostrano davvero la stessa UI.
//
// Tutti i quindici ruoli Material sono dichiarati: lasciarne anche uno ai default
// riporterebbe in scena Roboto/San Francisco dentro dialoghi o controlli costruiti
// dalla libreria, spezzando la coerenza senza che il chiamante se ne accorga.
internal fun appTypography(
    display: FontFamily,
    title: FontFamily,
    body: FontFamily,
) = Typography(
    displayLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.8.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.7.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.6.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.5.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.45.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.4.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = title,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.15.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = title,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = title,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp,
        color = Palette.TextMuted,
    ),
    // Tutti i label Material sono funzionali: sans, compatti e marcati. Le
    // iscrizioni decorative non riusano labelSmall ma il token sectionLabel,
    // sempre attraverso componenti strutturali come Eyebrow e SheetBox.
    labelLarge = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.35.sp,
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

/**
 * Le tre famiglie del tema. Si costruiscono al primo uso e poi restano: leggere i
 * caratteri e' lavoro d'avvio, non di ricomposizione.
 */
private val displayFamily by lazy {
    themeFontFamily(
        "cinzel_bold.ttf" to FontWeight.Bold,
        "cinzel_extrabold.ttf" to FontWeight.Black,
    )
}

private val titleFamily by lazy {
    themeFontFamily(
        "alegreya_medium.ttf" to FontWeight.Medium,
        "alegreya_bold.ttf" to FontWeight.Bold,
    )
}

private val bodyFamily by lazy {
    themeFontFamily(
        "alegreya_sans_regular.ttf" to FontWeight.Normal,
        "alegreya_sans_medium.ttf" to FontWeight.Medium,
        "alegreya_sans_bold.ttf" to FontWeight.Bold,
        // Il nero e' riservato a dadi, contatori e iniziali sui token: averne il
        // volto reale evita il grassetto sintetico proprio nei glifi piu' piccoli.
        "alegreya_sans_black.ttf" to FontWeight.Black,
    )
}

private val AppTypography by lazy { appTypography(displayFamily, titleFamily, bodyFamily) }
private val AppSemanticTypography by lazy {
    onfallTypography(AppTypography, displayFamily, bodyFamily)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    ProvideOnfallTypography(AppSemanticTypography) {
        MaterialTheme(
            colorScheme = DarkScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
