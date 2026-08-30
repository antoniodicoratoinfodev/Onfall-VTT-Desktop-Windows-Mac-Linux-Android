package app.d6d.rules.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Exact executable rules and mutable generic state embedded in a game session. */
public record RuleSessionSnapshot(
        List<RuleEntity> entities,
        RuleRuntimeState state,
        Map<RuleScope, RuleRuntimeState> scopedStates) {
    public RuleSessionSnapshot {
        ArrayList<RuleEntity> sorted = new ArrayList<>(Objects.requireNonNull(entities, "entities"));
        sorted.sort(java.util.Comparator.comparing(RuleEntity::id));
        HashSet<String> ids = new HashSet<>();
        sorted.forEach(entity -> {
            Objects.requireNonNull(entity, "entities contains null");
            if (!ids.add(entity.id())) throw new IllegalArgumentException("Duplicate session rule " + entity.id());
        });
        entities = List.copyOf(sorted);
        state = Objects.requireNonNull(state, "state");
        TreeMap<RuleScope, RuleRuntimeState> orderedScopes = new TreeMap<>();
        Objects.requireNonNull(scopedStates, "scopedStates").forEach((scope, scopedState) -> {
            Objects.requireNonNull(scope, "scope");
            if (scope.isSession()) {
                throw new IllegalArgumentException("Session state must use the dedicated state component");
            }
            orderedScopes.put(scope, Objects.requireNonNull(scopedState, "scoped state"));
        });
        scopedStates = Map.copyOf(new LinkedHashMap<>(orderedScopes));
    }

    /** Compatibilità sorgente: uno snapshot precedente contieneva soltanto lo stato di sessione. */
    public RuleSessionSnapshot(List<RuleEntity> entities, RuleRuntimeState state) {
        this(entities, state, Map.of());
    }

    public static RuleSessionSnapshot empty() {
        return new RuleSessionSnapshot(List.of(), RuleRuntimeState.empty());
    }

    public static RuleSessionSnapshot fromRevision(RulesetRevision revision) {
        Objects.requireNonNull(revision, "revision");
        CompiledRuleset compiled = revision.compile();
        return new RuleSessionSnapshot(revision.entities(), compiled.initialState(java.util.Map.of(), java.util.Set.of()));
    }

    public boolean executable() {
        return !entities.isEmpty();
    }

    public CompiledRuleset compile(String canonicalHash) {
        if (!executable()) throw new IllegalStateException("This legacy session has no embedded generic rules");
        return RulesetCompiler.compileSnapshot(canonicalHash, entities);
    }

    public RuleSessionSnapshot withState(RuleRuntimeState changed) {
        return new RuleSessionSnapshot(entities, changed, scopedStates);
    }

    public Optional<RuleRuntimeState> findState(RuleScope scope) {
        Objects.requireNonNull(scope, "scope");
        return scope.isSession() ? Optional.of(state) : Optional.ofNullable(scopedStates.get(scope));
    }

    /** Sostituisce atomically lo stato di uno scope senza toccare le altre istanze. */
    public RuleSessionSnapshot withState(RuleScope scope, RuleRuntimeState changed) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(changed, "changed");
        if (scope.isSession()) return withState(changed);
        LinkedHashMap<RuleScope, RuleRuntimeState> updated = new LinkedHashMap<>(scopedStates);
        updated.put(scope, changed);
        return new RuleSessionSnapshot(entities, state, updated);
    }

    public RuleSessionSnapshot withoutScope(RuleScope scope) {
        Objects.requireNonNull(scope, "scope");
        if (scope.isSession() || !scopedStates.containsKey(scope)) return this;
        LinkedHashMap<RuleScope, RuleRuntimeState> updated = new LinkedHashMap<>(scopedStates);
        updated.remove(scope);
        return new RuleSessionSnapshot(entities, state, updated);
    }
}
