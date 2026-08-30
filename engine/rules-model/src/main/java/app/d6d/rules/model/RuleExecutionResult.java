package app.d6d.rules.model;

import java.util.List;
import java.util.Objects;

/** Risultato atomico: nuovo stato e traccia delle fonti che lo hanno prodotto. */
public record RuleExecutionResult(RuleRuntimeState state, List<RuleRuntimeEvent> events) {
    public RuleExecutionResult {
        state = Objects.requireNonNull(state, "state");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }
}
