package app.d6d.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Ruoli tipografici del prodotto, espressi per significato invece che per misura.
 *
 * Material continua a ricevere la scala completa, necessaria ai suoi componenti;
 * questi nomi sono il contratto dell'interfaccia Onfall. In particolare, un nome
 * di abilita' non deve scegliere ogni volta fra bodyMedium, Bold o titleMedium:
 * usa [abilityName] e conserva famiglia e peso in archivio, scheda e battaglia.
 */
@Immutable
class OnfallTypography internal constructor(
    val screenTitle: TextStyle,
    val panelTitle: TextStyle,
    val itemTitle: TextStyle,
    val abilityNameLarge: TextStyle,
    val abilityName: TextStyle,
    val abilityNameCompact: TextStyle,
    val sectionLabel: TextStyle,
    val body: TextStyle,
    val bodyEmphasis: TextStyle,
    val supporting: TextStyle,
    val supportingEmphasis: TextStyle,
    val control: TextStyle,
    val compactControl: TextStyle,
    val fieldValue: TextStyle,
    val numberLarge: TextStyle,
    val numberMedium: TextStyle,
    val numberCompact: TextStyle,
    val numberSmall: TextStyle,
    val tokenInitials: TextStyle,
)

internal fun onfallTypography(
    material: Typography,
    displayFamily: FontFamily,
    bodyFamily: FontFamily,
) = OnfallTypography(
    screenTitle = material.titleLarge,
    panelTitle = material.titleMedium,
    itemTitle = material.titleSmall,
    abilityNameLarge = material.titleLarge,
    abilityName = material.titleSmall,
    abilityNameCompact = material.titleSmall.copy(fontSize = 13.sp, lineHeight = 16.sp),
    sectionLabel = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
    ),
    body = material.bodyMedium,
    bodyEmphasis = material.bodyMedium.copy(fontWeight = FontWeight.Bold),
    supporting = material.bodySmall,
    supportingEmphasis = material.bodySmall.copy(fontWeight = FontWeight.Bold),
    control = material.labelLarge,
    compactControl = material.labelMedium,
    fieldValue = material.bodyMedium.copy(fontWeight = FontWeight.Medium),
    numberLarge = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontFeatureSettings = "tnum",
    ),
    numberMedium = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Black,
        fontSize = 17.sp,
        lineHeight = 21.sp,
        fontFeatureSettings = "tnum",
    ),
    numberCompact = material.bodyMedium.copy(
        fontWeight = FontWeight.Black,
        fontFeatureSettings = "tnum",
    ),
    numberSmall = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Black,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontFeatureSettings = "tnum",
    ),
    tokenInitials = TextStyle(
        fontFamily = bodyFamily,
        fontWeight = FontWeight.Black,
        fontSize = 14.sp,
        lineHeight = 14.sp,
    ),
)

private val LocalOnfallTypography = staticCompositionLocalOf<OnfallTypography> {
    error("OnfallTypography is available only inside AppTheme")
}

/** Accesso ai ruoli semantici condivisi dall'intera applicazione. */
object OnfallTheme {
    val typography: OnfallTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalOnfallTypography.current
}

@Composable
internal fun ProvideOnfallTypography(
    typography: OnfallTypography,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalOnfallTypography provides typography, content = content)
}
