package app.d6d.ui.rules

import app.d6d.i18n.AppLanguage
import app.d6d.rules.model.RuleFormula
import app.d6d.rules.model.RuleKind
import app.d6d.ui.i18n.AppLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RulesUxTest {
    @TempDir
    lateinit var directory: Path

    @BeforeEach
    fun useItalian() {
        AppLocale.use(AppLanguage.ITALIAN)
    }

    @Test
    fun `le intenzioni riducono i tipi tecnici a cinque famiglie stabili`() {
        assertEquals(RuleIntentFamily.VALUES, intentFamilyFor(RuleKind.STAT))
        assertEquals(RuleIntentFamily.CHECKS, intentFamilyFor(RuleKind.ROLL))
        assertEquals(RuleIntentFamily.EFFECTS, intentFamilyFor(RuleKind.MODIFIER))
        assertEquals(RuleIntentFamily.ACTIONS, intentFamilyFor(RuleKind.TRIGGER))
        assertEquals(RuleIntentFamily.CONTENT, intentFamilyFor(RuleKind.TEXT_RULE))
        assertEquals(5, RuleIntentFamily.entries.size)
    }

    @Test
    fun `una ricetta composta appare come una regola e conserva i componenti tecnici`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()
        rules.addGuidedRule(RuleKind.ROLL)
        val roll = requireNotNull(rules.selectedEntity)
        val members = rules.authoringGroupMembers(roll.id())

        assertEquals(2, members.size)
        assertEquals(listOf(roll.id()), rules.visibleEntities.map { it.id() })
        assertTrue(rules.hasGeneratedParts)

        rules.showGeneratedParts = true
        assertEquals(members.map { it.id() }.toSet(), rules.visibleEntities.map { it.id() }.toSet())
    }

    @Test
    fun `il wizard consegna nome descrizione e valori iniziali al motore`() {
        val rules = RulesViewModel(directory)
        rules.createBlankRuleset()
        rules.addGuidedRule(
            RuleKind.ROLL,
            "Scassinare",
            "Tira un d20 contro la difficoltà della serratura.",
            mapOf(
                "mode" to "DICE",
                "countFormula" to "1",
                "sidesFormula" to "20",
                "totalFormula" to "\${roll} + 2",
                "targetFormula" to "12",
            ),
        )

        val roll = requireNotNull(rules.selectedEntity)
        assertEquals("Scassinare", roll.name().text("it"))
        assertEquals("12", roll.attributes()["targetFormula"])
        val randomizer = rules.authoringGroupMembers(roll.id()).single { it.kind() == RuleKind.RANDOMIZER }
        assertEquals("20", randomizer.attributes()["sidesFormula"])
        assertTrue(rules.validateSelectedDraft())
    }

    @Test
    fun `la navigazione salva prima di lasciare la regola e si ferma se il salvataggio fallisce`() {
        val gate = RuleEditorNavigationGate()
        var saves = 0
        var navigations = 0
        gate.bind("first", dirty = true, valid = true, validationMessage = null,
            save = { saves++; true }, reset = {})

        assertTrue(gate.navigate { navigations++ })
        assertEquals(1, saves)
        assertEquals(1, navigations)
        assertFalse(gate.dirty)

        gate.bind("second", dirty = true, valid = true, validationMessage = null,
            save = { false }, reset = {})
        assertFalse(gate.navigate { navigations++ })
        assertEquals(1, navigations)
        assertTrue(gate.dirty)

        gate.bind("third", dirty = true, valid = false, validationMessage = "Nome richiesto",
            save = { saves++; true }, reset = {})
        assertFalse(gate.save())
        assertTrue(gate.showValidationErrors)
        assertEquals("Nome richiesto", gate.validationMessage)
        assertEquals(1, saves)
    }

    @Test
    fun `la formula guidata parla italiano senza mostrare gli id del contesto`() {
        val expression = RuleFormula.compile("\${classLevel} + \${proficiency} + 2").expression()
        val description = naturalFormulaDescription(expression, emptyList(), italian = true)

        assertEquals("Risultato: Livello nella classe più Bonus di competenza più 2", description)
        assertFalse("classLevel" in description)
        assertFalse("proficiency" in description)
    }

    @Test
    fun `il riepilogo descrive l'intenzione e non gli attributi interni`() {
        val summary = plainRuleSummary(
            RuleKind.MODIFIER,
            "Armatura pesante",
            mapOf("operation" to "ADD", "targetRef" to "speed", "valueFormula" to "-3"),
            italian = true,
        )

        assertEquals(
            "Armatura pesante sottrae un valore quando sono soddisfatte le sue condizioni.",
            summary,
        )
        assertFalse("targetRef" in summary)
    }
}
