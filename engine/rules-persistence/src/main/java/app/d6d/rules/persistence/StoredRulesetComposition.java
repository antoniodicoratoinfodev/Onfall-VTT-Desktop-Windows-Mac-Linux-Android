package app.d6d.rules.persistence;

import app.d6d.rules.model.RulesetCompositionLock;

import java.util.Objects;

/** Provenienza modulare opzionale associata a una revisione appiattita. */
public record StoredRulesetComposition(
        String revisionCanonicalHash,
        RulesetCompositionLock lock) {

    public StoredRulesetComposition {
        revisionCanonicalHash = Objects.requireNonNull(
                revisionCanonicalHash, "revisionCanonicalHash").trim();
        if (revisionCanonicalHash.isEmpty()) {
            throw new IllegalArgumentException("revisionCanonicalHash cannot be blank");
        }
        lock = Objects.requireNonNull(lock, "lock");
    }
}
