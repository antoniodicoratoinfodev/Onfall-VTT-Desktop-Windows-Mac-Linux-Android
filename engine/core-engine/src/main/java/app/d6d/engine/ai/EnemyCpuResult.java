package app.d6d.engine.ai;

import java.util.List;
import java.util.Objects;

/** Esito completo di {@link EnemyCpu#actCurrentGroup}. */
public record EnemyCpuResult(
        EnemyCpuDifficulty difficulty,
        List<EnemyCpuActionReport> actions,
        String focusTargetId,
        boolean turnAdvanced,
        boolean encounterResolved,
        boolean waitingForPlayer,
        boolean partialMixedGroup,
        boolean decisionLimitReached,
        EnemyCpuOutcome outcome,
        long startingRevision,
        long endingRevision) {

    public EnemyCpuResult {
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(outcome, "outcome");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        focusTargetId = focusTargetId == null ? "" : focusTargetId;
    }

    /** Vero quando la CPU ha prodotto almeno un comando di gioco, esclusi gli scarti. */
    public boolean acted() {
        return checkpointCount() > 0;
    }

    /**
     * Numero di comandi riusciti e quindi di checkpoint Undo prodotti.
     * Un'area vale uno, indipendentemente dal numero di bersagli nel suo report.
     */
    public int checkpointCount() {
        return (int) actions.stream().filter(action -> action.type() != EnemyCpuActionType.SKIPPED).count();
    }
}
