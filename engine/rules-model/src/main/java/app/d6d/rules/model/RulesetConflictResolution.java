package app.d6d.rules.model;

import java.util.Objects;

/** Scelta esplicita del modulo vincente per un singolo campo in conflitto. */
public record RulesetConflictResolution(RuleFieldRef field, String winnerModuleHash) {
    public RulesetConflictResolution {
        field = Objects.requireNonNull(field, "field");
        winnerModuleHash = Objects.requireNonNull(winnerModuleHash, "winnerModuleHash").trim();
        if (winnerModuleHash.isEmpty()) {
            throw new IllegalArgumentException("winnerModuleHash cannot be blank");
        }
    }
}
