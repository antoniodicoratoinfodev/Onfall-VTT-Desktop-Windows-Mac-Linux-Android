package app.d6d.ui

import app.d6d.i18n.AppLanguage
import app.d6d.ui.i18n.EnglishStrings
import app.d6d.ui.i18n.ItalianStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * La versione, presa dove l'utente la legge.
 *
 * Il numero nasce in `gradle.properties`, diventa una costante generata e da li'
 * arriva a finestra e Impostazioni. Ogni anello della catena si vede altrove — nel
 * build, nel file generato, nello schermo — e in nessun punto si vede intero: e'
 * quello che prova questo file, confrontando cio' che l'app mostra con il numero
 * che il build dichiara di aver messo dentro.
 */
class AppIdentityTest {

    /** Iniettata dal task di test di `shared-ui`, dalla stessa fonte del codice generato. */
    private val declared: String = System.getProperty("onfall.version").orEmpty()

    @Test
    fun `l'app mostra la versione dichiarata dal build`() {
        assertTrue(declared.isNotBlank(), "il test non ha ricevuto «onfall.version» dal build")
        assertEquals(declared, AppIdentity.version)
    }

    @Test
    fun `la versione ha tre numeri separati da punti`() {
        assertTrue(
            Regex("""\d+\.\d+\.\d+""").matches(AppIdentity.version),
            "versione fuori formato: «${AppIdentity.version}»",
        )
    }

    @Test
    fun `il titolo della finestra porta nome, versione e dicitura di compatibilita'`() {
        AppLanguage.entries.forEach { language ->
            val title = AppIdentity.windowTitle(language)
            assertTrue(title.startsWith("${AppIdentity.displayName} ${AppIdentity.version}"), title)
            assertTrue(title.endsWith(AppIdentity.compatibilityLine(language)), title)
        }
    }

    @Test
    fun `Impostazioni ha l'etichetta della versione nelle due lingue`() {
        assertEquals("Versione", ItalianStrings.settings.version)
        assertEquals("Version", EnglishStrings.settings.version)
    }
}
