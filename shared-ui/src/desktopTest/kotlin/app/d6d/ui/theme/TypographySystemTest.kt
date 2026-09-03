package app.d6d.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypographySystemTest {

    private val display = FontFamily.Serif
    private val title = FontFamily.Monospace
    private val body = FontFamily.SansSerif
    private val material = appTypography(display, title, body)

    @Test
    fun `ogni ruolo Material usa una famiglia incorporata intenzionale`() {
        listOf(
            material.displayLarge,
            material.displayMedium,
            material.displaySmall,
            material.headlineLarge,
            material.headlineMedium,
            material.headlineSmall,
        ).forEach { assertEquals(display, it.fontFamily) }

        listOf(material.titleLarge, material.titleMedium, material.titleSmall)
            .forEach { assertEquals(title, it.fontFamily) }

        listOf(
            material.bodyLarge,
            material.bodyMedium,
            material.bodySmall,
            material.labelLarge,
            material.labelMedium,
            material.labelSmall,
        ).forEach { assertEquals(body, it.fontFamily) }
    }

    @Test
    fun `i ruoli semantici fissano pesi e gerarchia`() {
        val onfall = onfallTypography(material, display, body)

        assertEquals(FontWeight.Bold, onfall.screenTitle.fontWeight)
        assertEquals(title, onfall.abilityNameLarge.fontFamily)
        assertEquals(title, onfall.abilityName.fontFamily)
        assertEquals(FontWeight.Bold, onfall.abilityName.fontWeight)
        assertEquals(title, onfall.abilityNameCompact.fontFamily)
        assertEquals(13.sp, onfall.abilityNameCompact.fontSize)
        assertEquals(display, onfall.sectionLabel.fontFamily)
        assertEquals(body, onfall.bodyEmphasis.fontFamily)
        assertEquals(FontWeight.Bold, onfall.bodyEmphasis.fontWeight)
        assertEquals(FontWeight.Bold, onfall.supportingEmphasis.fontWeight)
        assertEquals(body, onfall.control.fontFamily)
        assertEquals(FontWeight.Bold, onfall.control.fontWeight)
        assertEquals(FontWeight.Medium, onfall.fieldValue.fontWeight)
        assertEquals(FontWeight.Black, onfall.numberLarge.fontWeight)
        assertEquals(22.sp, onfall.numberLarge.fontSize)
        assertEquals(FontWeight.Black, onfall.numberCompact.fontWeight)
        assertEquals("tnum", onfall.numberLarge.fontFeatureSettings)
        assertEquals("tnum", onfall.numberMedium.fontFeatureSettings)
        assertEquals("tnum", onfall.numberCompact.fontFeatureSettings)
        assertEquals("tnum", onfall.numberSmall.fontFeatureSettings)
        assertEquals(12.5.sp, onfall.numberSmall.fontSize)
        assertEquals(body, onfall.numberSmall.fontFamily)
        assertEquals(FontWeight.Black, onfall.numberSmall.fontWeight)
        assertEquals(body, onfall.tokenInitials.fontFamily)
        assertEquals(FontWeight.Black, onfall.tokenInitials.fontWeight)
    }
}
