package app.d6d.rules.authoring;

import app.d6d.rules.model.RuleValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Draft tipizzato di un valore indirizzabile, inclusi enum e riferimenti. */
public record ValueRuleDraft(
        RuleValue.Type valueType,
        RuleValue defaultValue,
        List<RuleValue> allowedValues,
        boolean mutable,
        String dimension,
        String canonicalUnit,
        Map<String, String> preservedAttributes,
        ProjectionStatus projectionStatus
) {
    public ValueRuleDraft {
        valueType = Objects.requireNonNull(valueType, "valueType");
        defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        if (defaultValue.type() != valueType) throw new IllegalArgumentException("Default value type differs");
        allowedValues = List.copyOf(Objects.requireNonNull(allowedValues, "allowedValues"));
        for (RuleValue value : allowedValues) {
            if (value.type() != valueType) throw new IllegalArgumentException("Allowed value type differs");
        }
        dimension = Objects.requireNonNullElse(dimension, "SCALAR").trim();
        if (dimension.isEmpty()) dimension = "SCALAR";
        canonicalUnit = Objects.requireNonNullElse(canonicalUnit, "").trim();
        preservedAttributes = Map.copyOf(Objects.requireNonNull(preservedAttributes, "preservedAttributes"));
        projectionStatus = Objects.requireNonNull(projectionStatus, "projectionStatus");
    }

    /** Scrive soltanto i campi semanticamente cambiati, lasciando intatta la forma originale. */
    public Map<String, String> attributesForSave() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(preservedAttributes);
        RuleValue.Type originalType = parseType(result.get("valueType"));
        if (originalType != valueType) result.put("valueType", valueType.name());

        RuleValue originalDefault = parseDefault(result, originalType);
        if (!defaultValue.equals(originalDefault)) result.put("defaultValue", defaultValue.canonicalValue());

        List<RuleValue> originalAllowed = parseAllowed(result.get("allowedValues"), originalType);
        if (!allowedValues.equals(originalAllowed)) {
            result.put("allowedValues", allowedValues.stream()
                    .map(RuleValue::canonicalValue).distinct().reduce((left, right) -> left + "," + right)
                    .orElse(""));
        }

        boolean originalMutable = Boolean.parseBoolean(result.getOrDefault("mutable", "true"));
        if (mutable != originalMutable) result.put("mutable", Boolean.toString(mutable));
        if (!dimension.equals(result.getOrDefault("dimension", "SCALAR"))) result.put("dimension", dimension);
        if (!canonicalUnit.equals(result.getOrDefault("canonicalUnit", ""))) {
            result.put("canonicalUnit", canonicalUnit);
        }
        return Map.copyOf(result);
    }

    static RuleValue.Type parseType(String raw) {
        return RuleValue.Type.valueOf(raw == null || raw.isBlank() ? "TEXT" : raw);
    }

    static RuleValue parseDefault(Map<String, String> attributes, RuleValue.Type type) {
        String raw = attributes.get("defaultValue");
        if (raw == null) {
            raw = switch (type) {
                case NUMBER -> "0";
                case BOOLEAN -> "false";
                case TEXT -> "";
                case REFERENCE -> throw new IllegalArgumentException(
                        "defaultValue is required for a reference value");
            };
        }
        return new RuleValue(type, raw);
    }

    static List<RuleValue> parseAllowed(String raw, RuleValue.Type type) {
        if (raw == null || raw.isBlank()) return List.of();
        ArrayList<RuleValue> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (part.isBlank()) continue;
            RuleValue value = new RuleValue(type, part.trim());
            if (!result.contains(value)) result.add(value);
        }
        return List.copyOf(result);
    }
}
