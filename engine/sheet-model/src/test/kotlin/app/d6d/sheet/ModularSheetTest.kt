package app.d6d.sheet

import app.d6d.rules.model.LocalizedRuleText
import app.d6d.rules.model.RuleAutomationLevel
import app.d6d.rules.model.RuleEntity
import app.d6d.rules.model.RuleKind
import app.d6d.rules.model.RulesetOrigin
import app.d6d.rules.model.RulesetRevision
import app.d6d.rules.model.RulesetRuntimeConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModularSheetTest {

    @Test
    fun `la scheda materializza campi linkati e conserva valori e massimo esatti`() {
        val revision = revision()

        val initial = RuleDrivenSheetProjector.project(revision, "it")

        assertEquals(revision.canonicalHash(), initial.rulesetCanonicalHash)
        assertEquals("Stato della spedizione", initial.sections.single().title)
        assertEquals(listOf("test:morale", "test:scorte", "test:ferito", "test:testo"),
            initial.sections.single().fields.map { it.id })
        assertEquals("MORALE", initial.sections.single().fields.first().dimension)
        assertEquals("pt", initial.sections.single().fields.first().canonicalUnit)
        assertEquals("5", initial.values.getValue("test:morale").current)
        assertEquals("7", initial.values.getValue("test:scorte").current)
        assertEquals("10", initial.values.getValue("test:scorte").maximum)
        assertEquals("Testo italiano.", initial.values.getValue("test:testo").current)

        val changedResource = RuleDrivenSheetProjector.update(
            revision, initial, "test:scorte", current = "3", maximum = "12",
        )
        val changedMorale = RuleDrivenSheetProjector.update(
            revision, changedResource, "test:morale", current = "8",
        )

        assertEquals("8", changedMorale.values.getValue("test:morale").current)
        assertEquals("3", changedMorale.values.getValue("test:scorte").current)
        assertEquals("12", changedMorale.values.getValue("test:scorte").maximum)
        assertFalse(changedMorale.sections.single().fields.last().mutable)
    }

    @Test
    fun `cambio lingua ritraduce etichette senza perdere i valori`() {
        val revision = revision()
        val changed = RuleDrivenSheetProjector.update(
            revision,
            RuleDrivenSheetProjector.project(revision, "it"),
            "test:morale",
            "9",
        )

        val translated = RuleDrivenSheetProjector.project(revision, "en", changed)

        assertEquals("Expedition state", translated.sections.single().title)
        assertEquals("9", translated.values.getValue("test:morale").current)
        assertEquals("English text.", translated.values.getValue("test:testo").current)
        assertTrue(translated.configured)
    }

    private fun revision(): RulesetRevision = RulesetRevision.create(
        "test:sheet",
        "test:sheet:revision:1",
        "1.0.0",
        "Scheda di test",
        "Regole sintetiche",
        RulesetOrigin.HOMEBREW,
        "",
        RulesetRuntimeConfig.genericManual(),
        listOf(
            entity(
                "test:morale",
                RuleKind.VALUE,
                "Morale",
                "Morale",
                mapOf(
                    "valueType" to "NUMBER",
                    "defaultValue" to "5",
                    "dimension" to "MORALE",
                    "canonicalUnit" to "pt",
                ),
            ),
            entity(
                "test:scorte",
                RuleKind.RESOURCE,
                "Scorte",
                "Supplies",
                mapOf("maximumFormula" to "10", "initialFormula" to "7"),
            ),
            entity(
                "test:ferito",
                RuleKind.CONDITION,
                "Ferito",
                "Wounded",
                mapOf("maximumStacks" to "3"),
            ),
            entity(
                "test:testo",
                RuleKind.TEXT_RULE,
                "Procedura",
                "Procedure",
                emptyMap(),
                "Testo italiano.",
                "English text.",
            ),
            entity(
                "test:section",
                RuleKind.SHEET_SECTION,
                "Stato della spedizione",
                "Expedition state",
                mapOf(
                    "fieldRefs" to "test:morale,test:scorte,test:ferito,test:testo",
                    "columns" to "2",
                    "layout" to "GRID",
                ),
            ),
        ),
        "2026-08-31T00:00:00Z",
    )

    private fun entity(
        id: String,
        kind: RuleKind,
        italianName: String,
        englishName: String,
        attributes: Map<String, String>,
        italianDescription: String = "Descrizione.",
        englishDescription: String = "Description.",
    ): RuleEntity = RuleEntity(
        id,
        kind,
        RulesetOrigin.HOMEBREW,
        LocalizedRuleText.bilingual(italianName, englishName),
        LocalizedRuleText.bilingual(italianDescription, englishDescription),
        "",
        true,
        RuleAutomationLevel.FULL,
        attributes,
        listOf("test"),
        "Test",
        "",
        0,
    )
}
