package app.d6d.domain.game;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Evento deterministico e persistibile della sessione generale. */
public record GameSessionEvent(
        long sequence,
        String type,
        String sourceScope,
        String targetScopes,
        Map<String, String> details) {

    public GameSessionEvent {
        if (sequence < 0) throw new IllegalArgumentException("sequence cannot be negative");
        type = requireText(type, "type");
        sourceScope = sourceScope == null ? "" : sourceScope.trim();
        targetScopes = targetScopes == null ? "" : targetScopes.trim();
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
