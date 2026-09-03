package app.d6d.rules.model;

import java.util.Objects;

/** Campo strutturato di una regola che può essere modificato da più moduli. */
public record RuleFieldRef(String entityId, Field field, String attributeKey) {
    public enum Field {
        NAME,
        DESCRIPTION,
        KIND,
        ENABLED,
        AUTOMATION_LEVEL,
        TAGS,
        ATTRIBUTE
    }

    public RuleFieldRef {
        entityId = requireText(entityId, "entityId");
        field = Objects.requireNonNull(field, "field");
        attributeKey = attributeKey == null ? "" : attributeKey.trim();
        if (field == Field.ATTRIBUTE && attributeKey.isEmpty()) {
            throw new IllegalArgumentException("An attribute field needs an attribute key");
        }
        if (field != Field.ATTRIBUTE && !attributeKey.isEmpty()) {
            throw new IllegalArgumentException("Only an attribute field can have an attribute key");
        }
    }

    public static RuleFieldRef name(String entityId) {
        return new RuleFieldRef(entityId, Field.NAME, "");
    }

    public static RuleFieldRef description(String entityId) {
        return new RuleFieldRef(entityId, Field.DESCRIPTION, "");
    }

    public static RuleFieldRef kind(String entityId) {
        return new RuleFieldRef(entityId, Field.KIND, "");
    }

    public static RuleFieldRef enabled(String entityId) {
        return new RuleFieldRef(entityId, Field.ENABLED, "");
    }

    public static RuleFieldRef automationLevel(String entityId) {
        return new RuleFieldRef(entityId, Field.AUTOMATION_LEVEL, "");
    }

    public static RuleFieldRef tags(String entityId) {
        return new RuleFieldRef(entityId, Field.TAGS, "");
    }

    public static RuleFieldRef attribute(String entityId, String attributeKey) {
        return new RuleFieldRef(entityId, Field.ATTRIBUTE, attributeKey);
    }

    /** Percorso stabile per diagnostica e futuri codec. */
    public String path() {
        return field == Field.ATTRIBUTE
                ? escape(entityId) + "/attributes/" + escape(attributeKey)
                : escape(entityId) + '/' + field.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
