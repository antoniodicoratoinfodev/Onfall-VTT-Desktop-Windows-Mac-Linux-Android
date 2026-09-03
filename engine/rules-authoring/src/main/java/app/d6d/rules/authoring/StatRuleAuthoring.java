package app.d6d.rules.authoring;

import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Proiettore lossless per il primo percorso guidato statistiche/calcoli. */
public final class StatRuleAuthoring {
    private static final Set<String> KNOWN_ATTRIBUTES = Set.of(
            "statId", "abbreviation", "default", "defaultFormula", "derivedFormula",
            "minimum", "minimumFormula", "maximum", "maximumFormula", "modifierFormula",
            "rounding", "advancementMaximum", "activeByDefault", "links", "lifetime",
            "owner", "syncPolicy", "resetEvent");

    private StatRuleAuthoring() { }

    public static AuthoringProjection<StatRuleDraft> project(RuleEntity entity) {
        if (!isSupported(entity.kind())) {
            return new AuthoringProjection<>(
                    Optional.empty(),
                    ProjectionStatus.EXPERT_ONLY,
                    List.of(new AuthoringDiagnostic(
                            "authoring.stat.unsupported-kind",
                            DiagnosticSeverity.ERROR,
                            "kind",
                            entity.kind().name())));
        }
        Map<String, String> attributes = entity.attributes();
        ArrayList<AuthoringDiagnostic> diagnostics = new ArrayList<>();
        try {
            FormulaDraft base = FormulaDraft.parse(
                    attributes.getOrDefault("defaultFormula", attributes.getOrDefault("default", "0")));
            StatRuleDraft draft = new StatRuleDraft(
                    entity.kind(),
                    base,
                    optional(attributes, "derivedFormula"),
                    optionalAlias(attributes, "minimumFormula", "minimum"),
                    optionalAlias(attributes, "maximumFormula", "maximum"),
                    optional(attributes, "modifierFormula"),
                    attributes.getOrDefault("rounding", "NONE"),
                    attributes);
            ProjectionStatus status = draft.projectionStatus();
            if (!attributes.keySet().stream().allMatch(KNOWN_ATTRIBUTES::contains)) {
                status = status.combine(ProjectionStatus.PARTIAL);
                diagnostics.add(new AuthoringDiagnostic(
                        "authoring.attributes.protected",
                        DiagnosticSeverity.INFO,
                        "attributes",
                        "Unknown attributes are preserved as protected fields"));
            }
            if (draft.projectionStatus() == ProjectionStatus.PARTIAL) {
                diagnostics.add(new AuthoringDiagnostic(
                        "authoring.formula.protected-blocks",
                        DiagnosticSeverity.INFO,
                        "formula",
                        "Some nodes remain visible but require expert editing"));
            }
            return new AuthoringProjection<>(Optional.of(draft), status, diagnostics);
        } catch (RuntimeException failure) {
            diagnostics.add(new AuthoringDiagnostic(
                    "authoring.formula.invalid",
                    DiagnosticSeverity.ERROR,
                    "formula",
                    failure.getMessage()));
            return new AuthoringProjection<>(Optional.empty(), ProjectionStatus.EXPERT_ONLY, diagnostics);
        }
    }

    private static Optional<FormulaDraft> optional(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(FormulaDraft.parse(value));
    }

    private static Optional<FormulaDraft> optionalAlias(
            Map<String, String> attributes,
            String primary,
            String legacy
    ) {
        String value = attributes.get(primary);
        if (value == null) value = attributes.get(legacy);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(FormulaDraft.parse(value));
    }

    private static boolean isSupported(RuleKind kind) {
        return kind == RuleKind.STAT || kind == RuleKind.SAVE || kind == RuleKind.DEFENSE;
    }
}
