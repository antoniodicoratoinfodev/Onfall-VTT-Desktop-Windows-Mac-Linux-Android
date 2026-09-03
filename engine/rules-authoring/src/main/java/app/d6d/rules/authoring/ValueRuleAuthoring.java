package app.d6d.rules.authoring;

import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleKind;
import app.d6d.rules.model.RuleValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Proiezione tipizzata del valore semplice già usato dall'editor guidato. */
public final class ValueRuleAuthoring {
    private static final Set<String> KNOWN_ATTRIBUTES = Set.of(
            "valueType", "defaultValue", "allowedValues", "mutable", "dimension", "canonicalUnit",
            "activeByDefault", "links", "lifetime", "owner", "syncPolicy", "resetEvent");

    private ValueRuleAuthoring() { }

    public static AuthoringProjection<ValueRuleDraft> project(RuleEntity entity) {
        if (entity.kind() != RuleKind.VALUE) {
            return new AuthoringProjection<>(Optional.empty(), ProjectionStatus.EXPERT_ONLY,
                    List.of(new AuthoringDiagnostic(
                            "authoring.value.unsupported-kind",
                            DiagnosticSeverity.ERROR,
                            "kind",
                            entity.kind().name())));
        }
        Map<String, String> attributes = entity.attributes();
        try {
            RuleValue.Type type = ValueRuleDraft.parseType(attributes.get("valueType"));
            ProjectionStatus status = attributes.keySet().stream().allMatch(KNOWN_ATTRIBUTES::contains)
                    ? ProjectionStatus.EXACT : ProjectionStatus.PARTIAL;
            ValueRuleDraft draft = new ValueRuleDraft(
                    type,
                    ValueRuleDraft.parseDefault(attributes, type),
                    ValueRuleDraft.parseAllowed(attributes.get("allowedValues"), type),
                    Boolean.parseBoolean(attributes.getOrDefault("mutable", "true")),
                    attributes.getOrDefault("dimension", "SCALAR"),
                    attributes.getOrDefault("canonicalUnit", ""),
                    attributes,
                    status);
            List<AuthoringDiagnostic> diagnostics = status == ProjectionStatus.PARTIAL
                    ? List.of(new AuthoringDiagnostic(
                            "authoring.attributes.protected",
                            DiagnosticSeverity.INFO,
                            "attributes",
                            "Unknown attributes are preserved as protected fields"))
                    : List.of();
            return new AuthoringProjection<>(Optional.of(draft), status, diagnostics);
        } catch (RuntimeException failure) {
            return new AuthoringProjection<>(Optional.empty(), ProjectionStatus.EXPERT_ONLY,
                    List.of(new AuthoringDiagnostic(
                            "authoring.value.invalid",
                            DiagnosticSeverity.ERROR,
                            "defaultValue",
                            failure.getMessage())));
        }
    }
}
