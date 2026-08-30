package app.d6d.domain.combat;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Open, stable identifier for a damage category.
 *
 * <p>The named constants and {@link #values()} preserve the old enum API and
 * serialized values. Rulesets may additionally use a namespaced identifier
 * such as {@code user:eldritch}; no application rebuild is required.</p>
 */
public final class DamageType implements Comparable<DamageType> {
    public static final DamageType ACID = standard("ACID");
    public static final DamageType BLUDGEONING = standard("BLUDGEONING");
    public static final DamageType COLD = standard("COLD");
    public static final DamageType FIRE = standard("FIRE");
    public static final DamageType FORCE = standard("FORCE");
    public static final DamageType LIGHTNING = standard("LIGHTNING");
    public static final DamageType NECROTIC = standard("NECROTIC");
    public static final DamageType PIERCING = standard("PIERCING");
    public static final DamageType POISON = standard("POISON");
    public static final DamageType PSYCHIC = standard("PSYCHIC");
    public static final DamageType RADIANT = standard("RADIANT");
    public static final DamageType SLASHING = standard("SLASHING");
    public static final DamageType THUNDER = standard("THUNDER");
    public static final DamageType UNTYPED = standard("UNTYPED");

    private static final List<DamageType> ENTRIES = List.of(
            ACID, BLUDGEONING, COLD, FIRE, FORCE, LIGHTNING, NECROTIC,
            PIERCING, POISON, PSYCHIC, RADIANT, SLASHING, THUNDER, UNTYPED);

    private final String name;

    private DamageType(String name) {
        this.name = validate(name);
    }

    private static DamageType standard(String name) {
        return new DamageType(name);
    }

    public static DamageType of(String raw) {
        String value = validate(raw);
        return ENTRIES.stream()
                .filter(candidate -> candidate.name.equalsIgnoreCase(value))
                .findFirst()
                .orElseGet(() -> new DamageType(value));
    }

    /** Compatibility with the former enum and existing codecs. */
    public static DamageType valueOf(String raw) {
        return of(raw);
    }

    /** Standard bundled entries only; custom entries come from the active ruleset. */
    public static DamageType[] values() {
        return ENTRIES.toArray(DamageType[]::new);
    }

    /** Kotlin-friendly equivalent of the former enum {@code entries}. */
    public static List<DamageType> getEntries() {
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
        Objects.requireNonNull(raw, "damage type id");
        String value = raw.trim();
        if (value.isEmpty() || value.length() > 160) {
            throw new IllegalArgumentException("Damage type id must contain 1..160 characters");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Damage type id cannot contain control characters");
        }
        return value;
    }

    @Override public int compareTo(DamageType other) {
        int folded = name.toLowerCase(Locale.ROOT).compareTo(other.name.toLowerCase(Locale.ROOT));
        return folded != 0 ? folded : name.compareTo(other.name);
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof DamageType type && name.equals(type.name);
    }

    @Override public int hashCode() {
        return name.hashCode();
    }

    @Override public String toString() {
        return name;
    }
}
