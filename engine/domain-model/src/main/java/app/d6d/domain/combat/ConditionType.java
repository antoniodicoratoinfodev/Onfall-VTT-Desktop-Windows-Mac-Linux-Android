package app.d6d.domain.combat;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Open, serializable condition identifier with legacy constants. */
public final class ConditionType implements Comparable<ConditionType> {
    public static final ConditionType BLINDED = standard("BLINDED");
    public static final ConditionType CHARMED = standard("CHARMED");
    public static final ConditionType DEAFENED = standard("DEAFENED");
    public static final ConditionType EXHAUSTION = standard("EXHAUSTION");
    public static final ConditionType FRIGHTENED = standard("FRIGHTENED");
    public static final ConditionType GRAPPLED = standard("GRAPPLED");
    public static final ConditionType INCAPACITATED = standard("INCAPACITATED");
    public static final ConditionType INVISIBLE = standard("INVISIBLE");
    public static final ConditionType PARALYZED = standard("PARALYZED");
    public static final ConditionType PETRIFIED = standard("PETRIFIED");
    public static final ConditionType POISONED = standard("POISONED");
    public static final ConditionType PRONE = standard("PRONE");
    public static final ConditionType RESTRAINED = standard("RESTRAINED");
    public static final ConditionType STUNNED = standard("STUNNED");
    public static final ConditionType UNCONSCIOUS = standard("UNCONSCIOUS");
    /** Kept for legacy sheets; new rulesets should use a descriptive namespaced ID. */
    public static final ConditionType CUSTOM = standard("CUSTOM");

    private static final List<ConditionType> ENTRIES = List.of(
            BLINDED, CHARMED, DEAFENED, EXHAUSTION, FRIGHTENED, GRAPPLED,
            INCAPACITATED, INVISIBLE, PARALYZED, PETRIFIED, POISONED, PRONE,
            RESTRAINED, STUNNED, UNCONSCIOUS, CUSTOM);

    private final String name;

    private ConditionType(String name) {
        this.name = validate(name);
    }

    private static ConditionType standard(String name) {
        return new ConditionType(name);
    }

    public static ConditionType of(String raw) {
        String value = validate(raw);
        return ENTRIES.stream()
                .filter(candidate -> candidate.name.equalsIgnoreCase(value))
                .findFirst()
                .orElseGet(() -> new ConditionType(value));
    }

    public static ConditionType valueOf(String raw) {
        return of(raw);
    }

    public static ConditionType[] values() {
        return ENTRIES.toArray(ConditionType[]::new);
    }

    public static List<ConditionType> getEntries() {
        return ENTRIES;
    }

    public String name() {
        return name;
    }

    public int ordinal() {
        int index = ENTRIES.indexOf(this);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }

    private static String validate(String raw) {
        Objects.requireNonNull(raw, "condition id");
        String value = raw.trim();
        if (value.isEmpty() || value.length() > 160) {
            throw new IllegalArgumentException("Condition id must contain 1..160 characters");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Condition id cannot contain control characters");
        }
        return value;
    }

    @Override public int compareTo(ConditionType other) {
        int folded = name.toLowerCase(Locale.ROOT).compareTo(other.name.toLowerCase(Locale.ROOT));
        return folded != 0 ? folded : name.compareTo(other.name);
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof ConditionType type && name.equals(type.name);
    }

    @Override public int hashCode() {
        return name.hashCode();
    }

    @Override public String toString() {
        return name;
    }
}
