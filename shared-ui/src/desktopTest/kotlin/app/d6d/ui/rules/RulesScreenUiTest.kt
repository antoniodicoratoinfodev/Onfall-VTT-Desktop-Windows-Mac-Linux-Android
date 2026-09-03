package app.d6d.ui.rules

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.d6d.i18n.AppLanguage
import app.d6d.ui.i18n.AppLocale
import app.d6d.ui.theme.AppTheme
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalTestApi::class)
class RulesScreenUiTest {
    @TempDir
    lateinit var directory: Path

    @BeforeEach
    fun italianLocale() {
        AppLocale.use(AppLanguage.ITALIAN)
    }

    @Test
    fun `la navigazione primaria separa il costruttore dalla gestione tecnica`() = runComposeUiTest {
        setContent {
            AppTheme {
                RulesScreen(RulesViewModel(directory), compact = false, activeBattle = null)
            }
        }

        onNodeWithText("Cosa vuoi fare?").assertIsDisplayed()
        onNodeWithText("Costruttore").assertExists().performClick()
        onNodeWithText("Crea una variante").assertIsDisplayed()
        onNodeWithText("Importa").assertDoesNotExist()
        onNodeWithText("Gestione").performClick()
        onNodeWithText("Importa").assertIsDisplayed()
    }

    @Test
    fun `su layout compatto i comandi primari conservano un bersaglio touch adeguato`() = runComposeUiTest {
        setContent {
            AppTheme {
                RulesScreen(RulesViewModel(directory), compact = true, activeBattle = null)
            }
        }

        val bounds = onNodeWithText("Panoramica").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height >= 48f, "Altezza touch effettiva: ${bounds.height}")
        onNode(hasText("Regole") and hasClickAction()).performClick()
        onNodeWithText("Cerca regole, classi, incantesimi…").assertExists()
    }

    @Test
    fun `il wizard chiede l'intenzione e soltanto i valori iniziali pertinenti`() = runComposeUiTest {
        val rules = RulesViewModel(directory).also { it.createBlankRuleset() }
        setContent {
            AppTheme {
                RulesScreen(rules, compact = false, activeBattle = null)
            }
        }

        onNodeWithText("Costruttore").performClick()
        onNode(hasText("Statistiche, valori, salute e progressione") and hasClickAction()).performClick()
        onNodeWithText("Altri tipi per esperti").assertDoesNotExist()
        onNode(hasText("Statistica, difesa o valore derivato") and hasClickAction()).performClick()
        onNodeWithText("1 · COME LA RICONOSCI").assertIsDisplayed()
        onNodeWithText("2 · VALORI INIZIALI").assertIsDisplayed()
        onNodeWithText("Da quale numero parte?").assertExists()
        onNodeWithText("Crea e continua").assertExists()
    }

    @Test
    fun `creare una regola apre sempre un wizard nuovo anche se una regola era selezionata`() = runComposeUiTest {
        val rules = RulesViewModel(directory).also {
            it.createBlankRuleset()
            it.addGuidedRule(app.d6d.rules.model.RuleKind.STAT)
        }
        setContent {
            AppTheme {
                RulesScreen(rules, compact = false, activeBattle = null)
            }
        }

        onNodeWithText("Creare una regola").performClick()
        onNodeWithText("Che risultato vuoi ottenere?").assertIsDisplayed()
        onNodeWithText("Crea un’altra regola…").assertDoesNotExist()
    }
}
