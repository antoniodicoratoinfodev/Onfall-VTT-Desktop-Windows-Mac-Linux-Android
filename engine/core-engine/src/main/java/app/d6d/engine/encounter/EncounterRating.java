package app.d6d.engine.encounter;

import app.d6d.domain.rules.EncounterDifficulty;

import java.util.Optional;

/** Resulting official budget band; ABOVE_HIGH is kept distinct from High. */
public enum EncounterRating {
    LOW,
    MODERATE,
    HIGH,
    ABOVE_HIGH;

    public Optional<EncounterDifficulty> officialDifficulty() {
        return switch (this) {
            case LOW -> Optional.of(EncounterDifficulty.LOW);
            case MODERATE -> Optional.of(EncounterDifficulty.MODERATE);
            case HIGH -> Optional.of(EncounterDifficulty.HIGH);
            case ABOVE_HIGH -> Optional.empty();
        };
    }
}
