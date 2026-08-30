package app.d6d.rules.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Risultato atomico di un comando che può interessare più istanze di regola. */
public record ScopedRuleExecutionResult(
        Map<RuleScope, RuleRuntimeState> states,
        List<RuleRuntimeEvent> events) {

    public ScopedRuleExecutionResult {
        TreeMap<RuleScope, RuleRuntimeState> ordered = new TreeMap<>();
        Objects.requireNonNull(states, "states").forEach((scope, state) ->
                ordered.put(Objects.requireNonNull(scope, "scope"),
                        Objects.requireNonNull(state, "state")));
        if (ordered.isEmpty()) throw new IllegalArgumentException("Scoped result needs at least one state");
        states = Map.copyOf(new LinkedHashMap<>(ordered));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }

    public RuleRuntimeState state(RuleScope scope) {
        RuleRuntimeState state = states.get(Objects.requireNonNull(scope, "scope"));
        if (state == null) throw new IllegalArgumentException("Missing result state " + scope.canonicalKey());
        return state;
    }
}
