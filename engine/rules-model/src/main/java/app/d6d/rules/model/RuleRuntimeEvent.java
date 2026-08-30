package app.d6d.rules.model;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Evento spiegabile prodotto dal runtime generico. */
public record RuleRuntimeEvent(
        long sequence,
        String type,
        String sourceRuleId,
        String targetId,
        Map<String, String> details) {

    public RuleRuntimeEvent {
        if (sequence < 0) throw new IllegalArgumentException("Event sequence cannot be negative");
        type = requireText(type, "type");
        sourceRuleId = sourceRuleId == null ? "" : sourceRuleId.trim();
        targetId = targetId == null ? "" : targetId.trim();
        TreeMap<String, String> sorted = new TreeMap<>();
        Objects.requireNonNull(details, "details").forEach((key, value) ->
                sorted.put(requireText(key, "detail key"), Objects.requireNonNull(value, "detail value")));
        details = Map.copyOf(sorted);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
