package app.d6d.rules.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Testo localizzato che non partecipa mai come chiave alla semantica della regola. */
public record LocalizedRuleText(Map<String, String> values, String primaryLanguage) {

    public LocalizedRuleText {
        Objects.requireNonNull(values, "values");
        TreeMap<String, String> normalized = new TreeMap<>();
        values.forEach((language, text) -> {
            String key = normalizeLanguage(language);
            String value = Objects.requireNonNull(text, "localized text").trim();
            if (!value.isEmpty()) normalized.put(key, value);
        });
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Localized text needs at least one non-blank value");
        }
        primaryLanguage = normalizeLanguage(primaryLanguage);
        if (!normalized.containsKey(primaryLanguage)) {
            throw new IllegalArgumentException("Primary language is not present: " + primaryLanguage);
        }
        values = Map.copyOf(new LinkedHashMap<>(normalized));
    }

    public static LocalizedRuleText bilingual(String italian, String english) {
        return new LocalizedRuleText(Map.of("it", italian, "en", english), "it");
    }

    public static LocalizedRuleText single(String language, String value) {
        return new LocalizedRuleText(Map.of(normalizeLanguage(language), value), language);
    }

    public String text(String requestedLanguage) {
        String normalized = normalizeLanguage(requestedLanguage);
        String exact = values.get(normalized);
        if (exact != null) return exact;
        int separator = normalized.indexOf('-');
        if (separator > 0) {
            String base = values.get(normalized.substring(0, separator));
            if (base != null) return base;
        }
        String primary = values.get(primaryLanguage);
        return primary != null ? primary : values.values().iterator().next();
    }

    public LocalizedRuleText withText(String language, String text) {
        LinkedHashMap<String, String> changed = new LinkedHashMap<>(values);
        String key = normalizeLanguage(language);
        String value = Objects.requireNonNull(text, "text").trim();
        if (value.isEmpty()) {
            if (key.equals(primaryLanguage)) {
                throw new IllegalArgumentException("Primary localized text cannot be blank");
            }
            changed.remove(key);
        } else {
            changed.put(key, value);
        }
        return new LocalizedRuleText(changed, primaryLanguage);
    }

    private static String normalizeLanguage(String language) {
        Objects.requireNonNull(language, "language");
        String normalized = language.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Language cannot be blank");
        return normalized;
    }
}
