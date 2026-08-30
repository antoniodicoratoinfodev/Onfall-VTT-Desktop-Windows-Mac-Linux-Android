package app.d6d.rules.model;

import java.util.Comparator;
import java.util.Objects;

/**
 * Identità stabile dell'istanza alla quale appartiene uno stato di regole.
 *
 * <p>Il tipo descrive la durata/il proprietario semantico, mentre {@code id}
 * identifica l'istanza concreta. Lo scope SESSION conserva il comportamento
 * dei salvataggi precedenti; gli altri ID restano aperti e non dipendono da una
 * particolare edizione o dal modello tattico.</p>
 */
public record RuleScope(Kind kind, String id) implements Comparable<RuleScope> {
    public static final String SESSION_ID = "session";
    private static final int MAX_ID_LENGTH = 512;
    private static final Comparator<RuleScope> ORDER = Comparator
            .comparing((RuleScope scope) -> scope.kind().ordinal())
            .thenComparing(RuleScope::id);

    public enum Kind { SESSION, ACTOR, OBJECT, SCENE, CAMPAIGN }

    public RuleScope {
        kind = Objects.requireNonNull(kind, "kind");
        id = requireId(id);
        if (kind == Kind.SESSION && !id.equals(SESSION_ID)) {
            throw new IllegalArgumentException("The session scope id must be '" + SESSION_ID + "'");
        }
    }

    public static RuleScope session() {
        return new RuleScope(Kind.SESSION, SESSION_ID);
    }

    public static RuleScope actor(String id) {
        return new RuleScope(Kind.ACTOR, id);
    }

    public static RuleScope objectScope(String id) {
        return new RuleScope(Kind.OBJECT, id);
    }

    public static RuleScope scene(String id) {
        return new RuleScope(Kind.SCENE, id);
    }

    public static RuleScope campaign(String id) {
        return new RuleScope(Kind.CAMPAIGN, id);
    }

    public boolean isSession() {
        return kind == Kind.SESSION;
    }

    public String canonicalKey() {
        return kind.name().toLowerCase(java.util.Locale.ROOT) + ':' + id;
    }

    @Override public int compareTo(RuleScope other) {
        return ORDER.compare(this, Objects.requireNonNull(other, "other"));
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "id");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Rule scope id cannot be blank");
        if (normalized.length() > MAX_ID_LENGTH) throw new IllegalArgumentException("Rule scope id is too long");
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Rule scope id cannot contain control characters");
        }
        return normalized;
    }
}
