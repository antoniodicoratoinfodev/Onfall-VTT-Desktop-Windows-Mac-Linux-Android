package app.d6d.rules.authoring;

import app.d6d.rules.model.RuleKind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Campi numerici comuni a caratteristiche, tiri salvezza e difese. */
public record StatRuleDraft(
        RuleKind kind,
        FormulaDraft defaultFormula,
        Optional<FormulaDraft> derivedFormula,
        Optional<FormulaDraft> minimumFormula,
        Optional<FormulaDraft> maximumFormula,
        Optional<FormulaDraft> modifierFormula,
        String rounding,
        Map<String, String> preservedAttributes
) {
    public StatRuleDraft {
        kind = Objects.requireNonNull(kind, "kind");
        if (kind != RuleKind.STAT && kind != RuleKind.SAVE && kind != RuleKind.DEFENSE) {
            throw new IllegalArgumentException("Unsupported stat-like rule kind " + kind);
        }
        defaultFormula = Objects.requireNonNull(defaultFormula, "defaultFormula");
        derivedFormula = Objects.requireNonNull(derivedFormula, "derivedFormula");
        minimumFormula = Objects.requireNonNull(minimumFormula, "minimumFormula");
        maximumFormula = Objects.requireNonNull(maximumFormula, "maximumFormula");
        modifierFormula = Objects.requireNonNull(modifierFormula, "modifierFormula");
        rounding = Objects.requireNonNullElse(rounding, "NONE").trim();
        if (rounding.isEmpty()) rounding = "NONE";
        preservedAttributes = Map.copyOf(Objects.requireNonNull(preservedAttributes, "preservedAttributes"));
    }

    /** Ricompone gli attributi senza scartare estensioni non conosciute dall'editor. */
    public Map<String, String> attributesForSave() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(preservedAttributes);
        if (defaultFormula.dirty() || result.containsKey("defaultFormula")) {
            result.put("defaultFormula", defaultFormula.sourceForSave());
        }
        putOptional(result, "derivedFormula", derivedFormula);
        putOptional(result, "minimumFormula", minimumFormula);
        putOptional(result, "maximumFormula", maximumFormula);
        putOptional(result, "modifierFormula", modifierFormula);
        if (result.containsKey("rounding") || !rounding.equals("NONE")) result.put("rounding", rounding);
        else result.remove("rounding");
        return Map.copyOf(result);
    }

    public ProjectionStatus projectionStatus() {
        ProjectionStatus status = defaultFormula.projectionStatus();
        for (Optional<FormulaDraft> formula : java.util.List.of(
                derivedFormula, minimumFormula, maximumFormula, modifierFormula)) {
            if (formula.isPresent()) status = status.combine(formula.orElseThrow().projectionStatus());
        }
        return status;
    }

    private static void putOptional(
            Map<String, String> target,
            String key,
            Optional<FormulaDraft> formula
    ) {
        if (formula.isPresent() && (formula.orElseThrow().dirty() || target.containsKey(key))) {
            target.put(key, formula.orElseThrow().sourceForSave());
        }
    }
}
