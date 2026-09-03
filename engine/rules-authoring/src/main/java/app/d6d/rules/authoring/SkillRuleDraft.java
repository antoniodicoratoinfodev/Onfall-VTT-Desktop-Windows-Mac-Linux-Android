package app.d6d.rules.authoring;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Draft tipizzato del calcolo di un'abilità. */
public record SkillRuleDraft(
        String statRef,
        FormulaDraft formula,
        FormulaDraft trainedBonusFormula,
        Map<String, String> preservedAttributes
) {
    public SkillRuleDraft {
        statRef = Objects.requireNonNull(statRef, "statRef").trim();
        if (statRef.isEmpty()) throw new IllegalArgumentException("statRef cannot be blank");
        formula = Objects.requireNonNull(formula, "formula");
        trainedBonusFormula = Objects.requireNonNull(trainedBonusFormula, "trainedBonusFormula");
        preservedAttributes = Map.copyOf(Objects.requireNonNull(preservedAttributes, "preservedAttributes"));
    }

    public ProjectionStatus projectionStatus() {
        return formula.projectionStatus().combine(trainedBonusFormula.projectionStatus());
    }

    public Map<String, String> attributesForSave() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(preservedAttributes);
        String originalStatRef = first(result, "statRef", "abilityRef", "ability");
        if (result.containsKey("statRef") || !statRef.equals(originalStatRef)) result.put("statRef", statRef);
        if (formula.dirty()) result.put("formula", formula.sourceForSave());
        if (trainedBonusFormula.dirty()) {
            result.put("trainedBonusFormula", trainedBonusFormula.sourceForSave());
        }
        return Map.copyOf(result);
    }

    private static String first(Map<String, String> attributes, String... keys) {
        for (String key : keys) {
            String value = attributes.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
