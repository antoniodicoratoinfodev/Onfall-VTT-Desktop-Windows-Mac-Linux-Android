package app.d6d.rules.model;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * Valore persistibile del runtime generico.
 *
 * <p>I numeri sono decimali esatti: nessuna regola pubblicata dipende da
 * {@code float}/{@code double} o dalla lingua del dispositivo.</p>
 */
public record RuleValue(Type type, String canonicalValue) {

    public enum Type { NUMBER, BOOLEAN, TEXT, REFERENCE }

    public RuleValue {
        type = Objects.requireNonNull(type, "type");
        canonicalValue = Objects.requireNonNull(canonicalValue, "canonicalValue");
        if (type == Type.NUMBER) canonicalValue = canonicalNumber(new BigDecimal(canonicalValue));
        if (type == Type.BOOLEAN) {
            String normalized = canonicalValue.trim().toLowerCase(Locale.ROOT);
            if (!normalized.equals("true") && !normalized.equals("false")) {
                throw new IllegalArgumentException("Boolean rule value must be true or false");
            }
            canonicalValue = normalized;
        }
        if (type == Type.REFERENCE) {
            canonicalValue = canonicalValue.trim();
            if (canonicalValue.isEmpty()) {
                throw new IllegalArgumentException("Rule reference cannot be blank");
            }
        }
    }

    public static RuleValue number(long value) {
        return number(BigDecimal.valueOf(value));
    }

    public static RuleValue number(BigDecimal value) {
        return new RuleValue(Type.NUMBER, canonicalNumber(Objects.requireNonNull(value, "value")));
    }

    public static RuleValue bool(boolean value) {
        return new RuleValue(Type.BOOLEAN, Boolean.toString(value));
    }

    public static RuleValue text(String value) {
        return new RuleValue(Type.TEXT, Objects.requireNonNull(value, "value"));
    }

    public static RuleValue reference(String entityId) {
        return new RuleValue(Type.REFERENCE, entityId.trim());
    }

    public BigDecimal asNumber() {
        return switch (type) {
            case NUMBER -> new BigDecimal(canonicalValue);
            case BOOLEAN -> "true".equals(canonicalValue) ? BigDecimal.ONE : BigDecimal.ZERO;
            case TEXT, REFERENCE -> throw new IllegalStateException("Rule value is not numeric");
        };
    }

    public boolean asBoolean() {
        return switch (type) {
            case BOOLEAN -> Boolean.parseBoolean(canonicalValue);
            case NUMBER -> asNumber().compareTo(BigDecimal.ZERO) != 0;
            case TEXT, REFERENCE -> !canonicalValue.isEmpty();
        };
    }

    private static String canonicalNumber(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.compareTo(BigDecimal.ZERO) == 0) return "0";
        return normalized.toPlainString();
    }
}
