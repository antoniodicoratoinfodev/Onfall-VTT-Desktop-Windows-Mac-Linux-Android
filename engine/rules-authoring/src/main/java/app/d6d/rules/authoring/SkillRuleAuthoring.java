package app.d6d.rules.authoring;

import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleKind;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Proiezione lossless dell'abilità verso riferimenti e calcoli a blocchi. */
public final class SkillRuleAuthoring {
    private static final Set<String> KNOWN_ATTRIBUTES = Set.of(
            "skillId", "statRef", "abilityRef", "ability", "formula", "trainedBonusFormula",
            "activeByDefault", "links", "lifetime", "owner", "syncPolicy", "resetEvent");

    private SkillRuleAuthoring() { }

    public static AuthoringProjection<SkillRuleDraft> project(RuleEntity entity) {
        if (entity.kind() != RuleKind.SKILL) {
            return new AuthoringProjection<>(Optional.empty(), ProjectionStatus.EXPERT_ONLY,
                    List.of(new AuthoringDiagnostic(
                            "authoring.skill.unsupported-kind",
                            DiagnosticSeverity.ERROR,
                            "kind",
                            entity.kind().name())));
        }
        Map<String, String> attributes = entity.attributes();
        String statRef = first(attributes, "statRef", "abilityRef", "ability");
        if (statRef == null || statRef.isBlank()) {
            return new AuthoringProjection<>(Optional.empty(), ProjectionStatus.EXPERT_ONLY,
                    List.of(new AuthoringDiagnostic(
                            "authoring.skill.missing-stat",
                            DiagnosticSeverity.ERROR,
                            "statRef",
                            "A skill needs a linked stat")));
        }
        try {
            SkillRuleDraft draft = new SkillRuleDraft(
                    statRef,
                    FormulaDraft.parse(formulaSource(
                            attributes, "formula", "${" + statRef + ":modifier}")),
                    FormulaDraft.parse(formulaSource(
                            attributes, "trainedBonusFormula", "${proficiency}")),
                    attributes);
            ProjectionStatus status = draft.projectionStatus();
            java.util.ArrayList<AuthoringDiagnostic> diagnostics = new java.util.ArrayList<>();
            if (status == ProjectionStatus.PARTIAL) {
                diagnostics.add(new AuthoringDiagnostic(
                        "authoring.formula.protected-blocks",
                        DiagnosticSeverity.INFO,
                        "formula",
                        "Some nodes remain visible but require expert editing"));
            }
            if (!attributes.keySet().stream().allMatch(KNOWN_ATTRIBUTES::contains)) {
                status = status.combine(ProjectionStatus.PARTIAL);
                diagnostics.add(new AuthoringDiagnostic(
                        "authoring.attributes.protected",
                        DiagnosticSeverity.INFO,
                        "attributes",
                        "Unknown attributes are preserved as protected fields"));
            }
            return new AuthoringProjection<>(Optional.of(draft), status, diagnostics);
        } catch (RuntimeException failure) {
            return new AuthoringProjection<>(Optional.empty(), ProjectionStatus.EXPERT_ONLY,
                    List.of(new AuthoringDiagnostic(
                            "authoring.formula.invalid",
                            DiagnosticSeverity.ERROR,
                            "formula",
                            failure.getMessage())));
        }
    }

    private static String first(Map<String, String> attributes, String... keys) {
        for (String key : keys) {
            String value = attributes.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String formulaSource(Map<String, String> attributes, String key, String fallback) {
        String value = attributes.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
