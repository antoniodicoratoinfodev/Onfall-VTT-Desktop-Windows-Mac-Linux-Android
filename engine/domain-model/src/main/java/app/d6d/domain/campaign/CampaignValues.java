package app.d6d.domain.campaign;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class CampaignValues {

    private CampaignValues() {
    }

    static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    static String optionalText(String value, String field) {
        return Objects.requireNonNull(value, field).trim();
    }

    static Map<String, String> copyStringMap(Map<String, String> source, String field) {
        Objects.requireNonNull(source, field);
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(
                requireText(key, field + " key"),
                Objects.requireNonNull(value, field + " value")));
        return Map.copyOf(copy);
    }
}
