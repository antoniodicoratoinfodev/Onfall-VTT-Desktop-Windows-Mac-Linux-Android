package app.d6d.domain.combat;

import java.util.Objects;
import java.util.Optional;

/** Stable resource identifier for a standard or Pact Magic spell-slot pool. */
public record SpellSlotResourceId(Kind kind, int level) {
    public static final String STANDARD_PREFIX = "app.d6d:spell-slot:";
    public static final String PACT_PREFIX = "app.d6d:pact-slot:";

    public enum Kind {
        STANDARD,
        PACT
    }

    public SpellSlotResourceId {
        Objects.requireNonNull(kind, "kind");
        if (level < 1 || level > 9) {
            throw new IllegalArgumentException("Spell-slot level must be between 1 and 9");
        }
    }

    public static SpellSlotResourceId standard(int level) {
        return new SpellSlotResourceId(Kind.STANDARD, level);
    }

    public static SpellSlotResourceId pact(int level) {
        return new SpellSlotResourceId(Kind.PACT, level);
    }

    /** Returns an empty result for unrelated, malformed, or unsupported resource ids. */
    public static Optional<SpellSlotResourceId> parse(String resourceId) {
        if (resourceId == null) return Optional.empty();
        Kind kind;
        String encodedLevel;
        if (resourceId.startsWith(STANDARD_PREFIX)) {
            kind = Kind.STANDARD;
            encodedLevel = resourceId.substring(STANDARD_PREFIX.length());
        } else if (resourceId.startsWith(PACT_PREFIX)) {
            kind = Kind.PACT;
            encodedLevel = resourceId.substring(PACT_PREFIX.length());
        } else {
            return Optional.empty();
        }
        if (encodedLevel.length() != 1
                || encodedLevel.charAt(0) < '1'
                || encodedLevel.charAt(0) > '9') {
            return Optional.empty();
        }
        try {
            return Optional.of(new SpellSlotResourceId(kind, Integer.parseInt(encodedLevel)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public String id() {
        return (kind == Kind.STANDARD ? STANDARD_PREFIX : PACT_PREFIX) + level;
    }
}
