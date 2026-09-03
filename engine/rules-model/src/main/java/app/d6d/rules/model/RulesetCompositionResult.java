package app.d6d.rules.model;

import java.util.Objects;

/** Revisione eseguibile appiattita insieme alla provenienza necessaria per rebase e diff. */
public record RulesetCompositionResult(
        RulesetRevision revision,
        RulesetCompositionLock lock) {

    public RulesetCompositionResult {
        revision = Objects.requireNonNull(revision, "revision");
        lock = Objects.requireNonNull(lock, "lock");
        if (!revision.baseCanonicalHash().equals(lock.baseCanonicalHash())) {
            throw new IllegalArgumentException("Revision and composition lock have different bases");
        }
    }
}
