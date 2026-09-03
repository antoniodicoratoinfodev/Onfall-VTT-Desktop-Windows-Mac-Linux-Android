package app.d6d.rules.authoring;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Esempio nominato conservato con il progetto visuale, non con lo snapshot runtime. */
public record RuleAuthoringExample(
        String id,
        Map<String, String> inputs,
        String expectedResult
) {
    public RuleAuthoringExample {
        id = requireText(id, "id");
        TreeMap<String, String> sorted = new TreeMap<>();
        Objects.requireNonNull(inputs, "inputs").forEach((key, value) ->
                sorted.put(requireText(key, "input key"), Objects.requireNonNull(value, "input value").trim()));
        inputs = Map.copyOf(sorted);
        expectedResult = Objects.requireNonNullElse(expectedResult, "").trim();
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
