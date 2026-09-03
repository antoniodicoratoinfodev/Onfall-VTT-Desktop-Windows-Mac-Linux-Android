package app.d6d.rules.authoring;

import app.d6d.rules.model.LocalizedRuleText;
import app.d6d.rules.model.RuleAutomationLevel;
import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleFormula;
import app.d6d.rules.model.RuleKind;
import app.d6d.rules.model.RulesetOrigin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormulaDraftTest {

    @Test
    void preservesExistingSourceUntilTheVisualTreeActuallyChanges() {
        FormulaDraft draft = FormulaDraft.parse("${score}+floor((${level}-1)/2)");

        assertFalse(draft.dirty());
        assertEquals("${score}+floor((${level}-1)/2)", draft.sourceForSave());

        FormulaDraft changed = draft.edit(new RuleFormula.BinaryExpression(
                "+",
                draft.expression(),
                new RuleFormula.NumberExpression(BigDecimal.ONE)));

        assertTrue(changed.dirty());
        assertEquals("((${score} + floor(((${level} - 1) / 2))) + 1)", changed.sourceForSave());
    }

    @Test
    void marksAdvancedButValidNodesAsProtectedVisualBlocks() {
        FormulaDraft draft = FormulaDraft.parse("if(${trained}, lookup(\"rank\", ${level}), 0)");

        assertEquals(ProjectionStatus.PARTIAL, draft.projectionStatus());
        assertEquals("if(${trained}, lookup(\"rank\", ${level}), 0)", draft.sourceForSave());
    }

    @Test
    void comparisonsAndBooleanCombinationsAreEditableVisualNodes() {
        FormulaDraft draft = FormulaDraft.parse("${level} >= 3 && !${disabled}");

        assertEquals(ProjectionStatus.EXACT, draft.projectionStatus());
    }

    @Test
    void statProjectionPreservesUnknownAttributesAndOnlyRewritesDirtyFormula() {
        RuleEntity entity = new RuleEntity(
                "home:stat:focus",
                RuleKind.STAT,
                RulesetOrigin.HOMEBREW,
                LocalizedRuleText.single("it", "Concentrazione"),
                LocalizedRuleText.single("it", "Una statistica di prova"),
                "",
                true,
                RuleAutomationLevel.FULL,
                Map.of(
                        "defaultFormula", "10+${level}",
                        "maximumFormula", "30",
                        "rounding", "FLOOR",
                        "plugin.custom", "keep-me"),
                List.of(),
                "",
                "",
                0);

        AuthoringProjection<StatRuleDraft> projection = StatRuleAuthoring.project(entity);
        StatRuleDraft draft = projection.value().orElseThrow();

        assertEquals(ProjectionStatus.PARTIAL, projection.status());
        assertEquals("10+${level}", draft.attributesForSave().get("defaultFormula"));
        assertEquals("keep-me", draft.attributesForSave().get("plugin.custom"));
        assertEquals("FLOOR", draft.attributesForSave().get("rounding"));
        assertEquals(entity.attributes(), draft.attributesForSave());
    }

    @Test
    void skillProjectionUsesRuntimeFallbacksAndPreservesExtensions() {
        RuleEntity entity = new RuleEntity(
                "home:skill:lore",
                RuleKind.SKILL,
                RulesetOrigin.HOMEBREW,
                LocalizedRuleText.single("it", "Conoscenze"),
                LocalizedRuleText.single("it", "Una competenza di prova"),
                "",
                true,
                RuleAutomationLevel.FULL,
                Map.of(
                        "statRef", "home:stat:mind",
                        "formula", "",
                        "trainedBonusFormula", "",
                        "plugin.custom", "keep-me"),
                List.of(),
                "",
                "",
                0);

        SkillRuleDraft draft = SkillRuleAuthoring.project(entity).value().orElseThrow();

        assertEquals("${home:stat:mind:modifier}", draft.formula().sourceForSave());
        assertEquals("${proficiency}", draft.trainedBonusFormula().sourceForSave());
        assertEquals("keep-me", draft.attributesForSave().get("plugin.custom"));
        assertEquals(entity.attributes(), draft.attributesForSave());
    }

    @Test
    void legacyStatAliasesAreNotRewrittenByOpeningAndSaving() {
        RuleEntity entity = new RuleEntity(
                "home:defense:guard",
                RuleKind.DEFENSE,
                RulesetOrigin.HOMEBREW,
                LocalizedRuleText.single("it", "Guardia"),
                LocalizedRuleText.single("it", "Difesa legacy"),
                "",
                true,
                RuleAutomationLevel.FULL,
                Map.of("default", "10", "minimum", "1", "plugin.custom", "keep-me"),
                List.of(),
                "",
                "",
                0);

        StatRuleDraft draft = StatRuleAuthoring.project(entity).value().orElseThrow();

        assertEquals(entity.attributes(), draft.attributesForSave());
    }

    @Test
    void typedValueProjectionIsLosslessAndProtectsUnknownExtensions() {
        RuleEntity entity = new RuleEntity(
                "home:value:stance",
                RuleKind.VALUE,
                RulesetOrigin.HOMEBREW,
                LocalizedRuleText.single("it", "Atteggiamento"),
                LocalizedRuleText.single("it", "Valore enumerato"),
                "",
                true,
                RuleAutomationLevel.FULL,
                Map.of(
                        "valueType", "TEXT",
                        "defaultValue", "CALM",
                        "allowedValues", "CALM, RISKY",
                        "mutable", "true",
                        "plugin.custom", "keep-me"),
                List.of(),
                "",
                "",
                0);

        AuthoringProjection<ValueRuleDraft> projection = ValueRuleAuthoring.project(entity);
        ValueRuleDraft draft = projection.value().orElseThrow();

        assertEquals(ProjectionStatus.PARTIAL, projection.status());
        assertEquals(List.of("CALM", "RISKY"), draft.allowedValues().stream()
                .map(value -> value.canonicalValue()).toList());
        assertEquals(entity.attributes(), draft.attributesForSave());
    }
}
