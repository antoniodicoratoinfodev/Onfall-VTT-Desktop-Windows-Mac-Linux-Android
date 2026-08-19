package app.d6d.engine.encounter;

import app.d6d.domain.rules.EncounterDifficulty;
import app.d6d.domain.rules.XpBudget;

import java.util.List;
import java.util.Objects;

/** Official numeric assessment, kept separate from any future simulation result. */
public record EncounterEvaluation(
        XpBudget partyBudget,
        long encounterCostXp,
        EncounterDifficulty targetDifficulty,
        long targetBudgetXp,
        long xpDeltaFromTarget,
        EncounterRating officialRating,
        boolean overTargetBudget,
        boolean overBudgetAllowed,
        int presentCharacterCount,
        long enemyCount,
        int distinctStatBlockCount,
        List<EncounterWarning> warnings) {

    public EncounterEvaluation {
        partyBudget = Objects.requireNonNull(partyBudget, "partyBudget");
        if (encounterCostXp < 0 || targetBudgetXp < 0) {
            throw new IllegalArgumentException("XP values must not be negative");
        }
        targetDifficulty = Objects.requireNonNull(targetDifficulty, "targetDifficulty");
        officialRating = Objects.requireNonNull(officialRating, "officialRating");
        if (presentCharacterCount < 0 || enemyCount < 0 || distinctStatBlockCount < 0) {
            throw new IllegalArgumentException("encounter counts must not be negative");
        }
        Objects.requireNonNull(warnings, "warnings");
        warnings.forEach(warning -> Objects.requireNonNull(warning, "warnings contains null"));
        warnings = List.copyOf(warnings);
    }

    public long costXp() {
        return encounterCostXp;
    }

    public long selectedBudgetXp() {
        return targetBudgetXp;
    }

    public long deviationXp() {
        return xpDeltaFromTarget;
    }

    public boolean hasWarning(EncounterWarningCode code) {
        Objects.requireNonNull(code, "code");
        return warnings.stream().anyMatch(warning -> warning.code() == code);
    }
}
