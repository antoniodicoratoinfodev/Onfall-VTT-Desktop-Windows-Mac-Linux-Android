package app.d6d.engine;

import app.d6d.domain.game.GameSceneState;
import app.d6d.domain.game.GameSessionEvent;
import app.d6d.domain.game.GameSessionState;
import app.d6d.domain.game.GameSessionStatus;
import app.d6d.rules.model.CompiledRuleset;
import app.d6d.rules.model.RuleRuntimeEvent;
import app.d6d.rules.model.RuleRuntimeState;
import app.d6d.rules.model.RuleScope;
import app.d6d.rules.model.RuleSessionSnapshot;
import app.d6d.rules.model.RuleValue;
import app.d6d.rules.model.RulesetBinding;
import app.d6d.rules.model.RulesetRevision;
import app.d6d.rules.model.ScopedRuleExecutionResult;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Aggregate generale per scene e regole, indipendente da {@link CombatSession}.
 *
 * <p>Un incontro SRD puo' continuare a usare il motore tattico specializzato;
 * esplorazione, interazione sociale, downtime e regolamenti senza combattimento
 * usano invece questo contenitore senza inventare PF o iniziativa. Tutti i
 * comandi restano atomici, deterministici, auditabili e annullabili.</p>
 */
public final class GameSession {
    private String sessionId;
    private String displayName;
    private GameSessionStatus status;
    private long revision;
    private final long randomSeed;
    private final DeterministicDice random;
    private RulesetBinding binding;
    private RuleSessionSnapshot ruleSession;
    private final LinkedHashMap<String, GameSceneState> scenes;
    private String activeSceneId;
    private final ArrayList<GameSessionEvent> audit;
    private final Deque<Checkpoint> undo;
    private CompiledRuleset compiled;

    public static GameSession fromRevision(
            String sessionId,
            String displayName,
            RulesetRevision ruleset,
            long randomSeed) {
        Objects.requireNonNull(ruleset, "ruleset");
        return new GameSession(new GameSessionState(
                sessionId,
                displayName,
                GameSessionStatus.DRAFT,
                0,
                randomSeed,
                randomSeed,
                ruleset.binding(),
                RuleSessionSnapshot.fromRevision(ruleset),
                Map.of(),
                ""), List.of());
    }

    public static GameSession restore(GameSessionState state, List<GameSessionEvent> auditTrail) {
        return new GameSession(state, auditTrail);
    }

    private GameSession(GameSessionState state, List<GameSessionEvent> auditTrail) {
        Objects.requireNonNull(state, "state");
        sessionId = state.sessionId();
        displayName = state.displayName();
        status = state.status();
        revision = state.revision();
        randomSeed = state.randomSeed();
        random = DeterministicDice.fromState(randomSeed, state.randomState());
        binding = state.rulesetBinding();
        ruleSession = state.ruleSession();
        scenes = new LinkedHashMap<>(state.scenes());
        activeSceneId = state.activeSceneId();
        audit = new ArrayList<>(Objects.requireNonNull(auditTrail, "auditTrail"));
        for (int index = 0; index < audit.size(); index++) {
            if (audit.get(index).sequence() != index) {
                throw new IllegalArgumentException("Game session audit sequence is not contiguous");
            }
        }
        undo = new ArrayDeque<>();
        compiled = ruleSession.compile(binding.canonicalHash());
    }

    public synchronized GameSessionState currentState() {
        return new GameSessionState(sessionId, displayName, status, revision,
                randomSeed, random.state(), binding, ruleSession, scenes, activeSceneId);
    }

    public synchronized List<GameSessionEvent> auditTrail() {
        return List.copyOf(audit);
    }

    public synchronized CompiledRuleset rules() {
        return compiled;
    }

    public synchronized void rename(String name) {
        String normalized = requireText(name, "name");
        if (normalized.equals(displayName)) return;
        beginCommand();
        displayName = normalized;
        commit("SESSION_RENAMED", RuleScope.session(), List.of(), Map.of("name", normalized));
    }

    public synchronized void start() {
        requireStatus(GameSessionStatus.DRAFT);
        beginCommand();
        try {
            status = GameSessionStatus.ACTIVE;
            fireLifecycleInternal("SESSION_STARTED", List.of(RuleScope.session()));
            commit("SESSION_STARTED", RuleScope.session(), List.of(), Map.of());
        } catch (RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    public synchronized void pause() {
        requireStatus(GameSessionStatus.ACTIVE);
        beginCommand();
        status = GameSessionStatus.PAUSED;
        commit("SESSION_PAUSED", RuleScope.session(), List.of(), Map.of());
    }

    public synchronized void resume() {
        requireStatus(GameSessionStatus.PAUSED);
        beginCommand();
        status = GameSessionStatus.ACTIVE;
        commit("SESSION_RESUMED", RuleScope.session(), List.of(), Map.of());
    }

    public synchronized void complete() {
        if (status == GameSessionStatus.COMPLETED) return;
        beginCommand();
        try {
            if (!activeSceneId.isEmpty()) {
                GameSceneState active = scenes.get(activeSceneId);
                fireLifecycleInternal("SCENE_ENDED", lifecycleScopes(active));
            }
            fireLifecycleInternal("SESSION_ENDED", allScopes());
            status = GameSessionStatus.COMPLETED;
            activeSceneId = "";
            commit("SESSION_COMPLETED", RuleScope.session(), List.of(), Map.of());
        } catch (RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    public synchronized void addScene(
            String sceneId,
            String name,
            String kind,
            String procedureRef) {
        ensureMutable();
        String id = requireText(sceneId, "sceneId");
        if (scenes.containsKey(id)) throw new IllegalArgumentException("Duplicate scene " + id);
        String procedure = procedureRef == null ? "" : procedureRef.trim();
        if (!procedure.isEmpty() && !compiled.sceneProcedures().containsKey(compiled.resolveId(procedure))) {
            throw new IllegalArgumentException("Unknown executable scene procedure " + procedure);
        }
        beginCommand();
        try {
            scenes.put(id, new GameSceneState(id, name, kind, procedure, 0, List.of(), Map.of(), 0));
            materializeScopeInternal(RuleScope.scene(id));
            commit("SCENE_ADDED", RuleScope.session(), List.of(RuleScope.scene(id)), Map.of(
                    "name", name, "kind", kind, "procedureRef", procedure));
        } catch (RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    public synchronized void addParticipant(String sceneId, String actorId) {
        ensureMutable();
        GameSceneState scene = scene(sceneId);
        String actor = requireText(actorId, "actorId");
        if (scene.participantIds().contains(actor)) return;
        beginCommand();
        scenes.put(scene.id(), scene.withParticipant(actor));
        materializeScopeInternal(RuleScope.actor(actor));
        commit("SCENE_PARTICIPANT_ADDED", RuleScope.scene(scene.id()), List.of(RuleScope.actor(actor)), Map.of());
    }

    public synchronized void removeParticipant(String sceneId, String actorId) {
        ensureMutable();
        GameSceneState scene = scene(sceneId);
        if (!scene.participantIds().contains(actorId)) return;
        beginCommand();
        scenes.put(scene.id(), scene.withoutParticipant(actorId));
        commit("SCENE_PARTICIPANT_REMOVED", RuleScope.scene(scene.id()),
                List.of(RuleScope.actor(actorId)), Map.of());
    }

    public synchronized void activateScene(String sceneId) {
        ensureMutable();
        GameSceneState next = scene(sceneId);
        if (next.id().equals(activeSceneId)) return;
        beginCommand();
        try {
            String previous = activeSceneId;
            if (!previous.isEmpty()) {
                fireLifecycleInternal("SCENE_ENDED", lifecycleScopes(scenes.get(previous)));
            }
            activeSceneId = next.id();
            fireLifecycleInternal("SCENE_STARTED", lifecycleScopes(next));
            commit("SCENE_ACTIVATED", RuleScope.scene(next.id()), List.of(), Map.of("previous", previous));
        } catch (RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    public synchronized void advanceScenePhase() {
        ensureMutable();
        GameSceneState scene = activeScene();
        CompiledRuleset.SceneProcedureDefinition procedure = procedure(scene);
        if (scene.phaseIndex() + 1 >= procedure.phases().size()) {
            throw new IllegalStateException("The active scene is already in its final phase");
        }
        beginCommand();
        try {
            fireLifecycleInternal("PHASE_ENDED", lifecycleScopes(scene));
            GameSceneState changed = scene.withPhase(scene.phaseIndex() + 1);
            scenes.put(scene.id(), changed);
            fireLifecycleInternal("PHASE_STARTED", lifecycleScopes(changed));
            commit("SCENE_PHASE_ADVANCED", RuleScope.scene(scene.id()), List.of(), Map.of(
                    "phase", procedure.phases().get(changed.phaseIndex()),
                    "phaseIndex", Integer.toString(changed.phaseIndex())));
        } catch (RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    public synchronized RuleRuntimeState ruleState(RuleScope scope) {
        RuleScope checked = Objects.requireNonNull(scope, "scope");
        return ruleSession.findState(checked)
                .orElseGet(() -> compiled.initialState(Map.of(), Set.of()));
    }

    public synchronized void materializeScope(RuleScope scope) {
        ensureMutable();
        if (ruleSession.findState(scope).isPresent()) return;
        beginCommand();
        materializeScopeInternal(scope);
        commit("RULE_SCOPE_CREATED", scope, List.of(), Map.of());
    }

    public synchronized void setRuleValue(RuleScope scope, String ruleId, RuleValue value) {
        ensureMutable();
        RuleScope checked = Objects.requireNonNull(scope, "scope");
        RuleRuntimeState before = ruleState(checked);
        RuleRuntimeState changed = compiled.setRuleValue(ruleId, value, before);
        if (changed.equals(before)) return;
        beginCommand();
        try {
            ruleSession = ruleSession.withState(checked, changed);
            commit("RULE_VALUE_SET", checked, List.of(), Map.of(
                    "ruleId", ruleId, "type", value.type().name(), "value", value.canonicalValue()));
        } catch (RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    public synchronized ScopedRuleExecutionResult executeRuleAction(
            String actionId,
            RuleScope source,
            List<RuleScope> targets) {
        requireStatus(GameSessionStatus.ACTIVE);
        Objects.requireNonNull(source, "source");
        List<RuleScope> checkedTargets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        if (checkedTargets.isEmpty()) throw new IllegalArgumentException("An action needs at least one target");
        beginCommand();
        try {
            materializeScopeInternal(source);
            checkedTargets.forEach(this::materializeScopeInternal);
            ScopedRuleExecutionResult result = compiled.executeScopedActionToTargets(
                    actionId, source, checkedTargets, stateFrame());
            apply(result.states());
            recordRuntimeEvents(result.events());
            commit("RULE_ACTION_EXECUTED", source, checkedTargets, Map.of(
                    "actionId", actionId, "eventCount", Integer.toString(result.events().size())));
            return result;
        } catch (RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    public synchronized void fireRuleEvent(String event, RuleScope source, RuleScope target) {
        ensureMutable();
        beginCommand();
        try {
            materializeScopeInternal(source);
            materializeScopeInternal(target);
            ScopedRuleExecutionResult result = compiled.fireScopedEvent(event, source, target, stateFrame());
            apply(result.states());
            recordRuntimeEvents(result.events());
            commit("RULE_EVENT_FIRED", source, List.of(target), Map.of(
                    "event", event, "eventCount", Integer.toString(result.events().size())));
        } catch (RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    public synchronized CompiledRuleset.RandomizerResult roll(String randomizerId, RuleScope scope) {
        ensureMutable();
        beginCommand();
        try {
            materializeScopeInternal(scope);
            CompiledRuleset.RandomizerResult result = compiled.roll(
                    randomizerId, ruleState(scope), bound -> random.roll(bound) - 1);
            commit("RULE_RANDOMIZER_ROLLED", scope, List.of(), Map.of(
                    "randomizerId", result.randomizerId(),
                    "draws", result.draws().stream().map(String::valueOf).collect(Collectors.joining(",")),
                    "value", result.value().toPlainString()));
            return result;
        } catch (RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    /** Cambia revisione conservando soltanto stato ancora compatibile per ID e tipo. */
    public synchronized void changeRuleset(RulesetRevision revision) {
        ensureMutable();
        Objects.requireNonNull(revision, "revision");
        if (binding.canonicalHash().equals(revision.canonicalHash())) return;
        beginCommand();
        try {
            CompiledRuleset next = revision.compile();
            RuleSessionSnapshot requested = RuleSessionSnapshot.fromRevision(revision);
            RuleSessionSnapshot migrated = requested.withState(
                    migrateState(ruleSession.state(), next));
            for (Map.Entry<RuleScope, RuleRuntimeState> entry : ruleSession.scopedStates().entrySet()) {
                migrated = migrated.withState(entry.getKey(), migrateState(entry.getValue(), next));
            }
            binding = revision.binding();
            ruleSession = migrated;
            compiled = next;
            commit("RULESET_CHANGED", RuleScope.session(), List.of(), Map.of(
                    "projectId", binding.projectId(), "revisionId", binding.revisionId(),
                    "canonicalHash", binding.canonicalHash()));
        } catch (RuntimeException failure) {
            rollback();
            throw failure;
        }
    }

    public synchronized boolean canUndo() {
        return !undo.isEmpty();
    }

    public synchronized void undo() {
        if (undo.isEmpty()) throw new IllegalStateException("Nothing to undo");
        Checkpoint checkpoint = undo.pop();
        restoreInternal(checkpoint.state());
        while (audit.size() > checkpoint.auditSize()) audit.remove(audit.size() - 1);
        revision++;
        append("UNDO", RuleScope.session(), List.of(), Map.of());
    }

    private RuleRuntimeState migrateState(RuleRuntimeState previous, CompiledRuleset next) {
        LinkedHashMap<String, RuleValue> supplied = new LinkedHashMap<>();
        previous.values().forEach((id, value) -> {
            String resolved = safeResolve(next, id);
            CompiledRuleset.ValueDefinition definition = next.valueDefinitions().get(resolved);
            if (definition != null && definition.accepts(value)) supplied.put(resolved, value);
            else if (value.type() == RuleValue.Type.NUMBER
                    && (next.stats().containsKey(resolved) || next.skills().containsKey(resolved)
                        || id.startsWith("context:") || id.startsWith("level:"))) {
                supplied.put(resolved, value);
            }
        });
        LinkedHashSet<String> active = previous.activeRuleIds().stream()
                .filter(id -> id.startsWith("trained:")
                        ? next.skills().containsKey(safeResolve(next, id.substring("trained:".length())))
                        : next.entities().containsKey(safeResolve(next, id)))
                .map(id -> id.startsWith("trained:")
                        ? "trained:" + safeResolve(next, id.substring("trained:".length()))
                        : safeResolve(next, id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        RuleRuntimeState fresh = next.initialState(supplied, active);
        LinkedHashMap<String, RuleRuntimeState.ResourceState> pools = new LinkedHashMap<>(fresh.resources());
        previous.resources().forEach((id, old) -> {
            String resolved = safeResolve(next, id);
            RuleRuntimeState.ResourceState created = pools.get(resolved);
            if (created == null) return;
            BigDecimal spent = old.maximum().subtract(old.current()).max(BigDecimal.ZERO);
            pools.put(resolved, created.withCurrent(created.maximum().subtract(spent).max(BigDecimal.ZERO)));
        });
        LinkedHashMap<String, Integer> conditions = new LinkedHashMap<>();
        previous.conditionStacks().forEach((id, stacks) -> {
            String resolved = safeResolve(next, id);
            CompiledRuleset.ConditionDefinition definition = next.conditionDefinitions().get(resolved);
            if (definition != null) conditions.put(resolved, Math.min(stacks, definition.maximumStacks()));
        });
        LinkedHashMap<String, BigDecimal> budget = new LinkedHashMap<>(fresh.turnBudget());
        previous.turnBudget().forEach((id, current) -> {
            BigDecimal nextMaximum = budget.get(id);
            if (nextMaximum == null) return;
            budget.put(id, current.max(BigDecimal.ZERO).min(nextMaximum));
        });
        return new RuleRuntimeState(fresh.values(), pools, conditions, budget,
                fresh.activeRuleIds(), previous.revision() + 1);
    }

    private static String safeResolve(CompiledRuleset rules, String id) {
        try {
            return rules.resolveId(id);
        } catch (RuntimeException ignored) {
            return id;
        }
    }

    private void fireLifecycleInternal(String event, Collection<RuleScope> rawScopes) {
        TreeSet<RuleScope> scopes = new TreeSet<>(rawScopes);
        for (RuleScope scope : scopes) {
            materializeScopeInternal(scope);
            ScopedRuleExecutionResult result = compiled.fireScopedEvent(event, scope, scope, stateFrame());
            apply(result.states());
            recordRuntimeEvents(result.events());
        }
    }

    private List<RuleScope> lifecycleScopes(GameSceneState scene) {
        LinkedHashSet<RuleScope> result = new LinkedHashSet<>();
        result.add(RuleScope.session());
        result.add(RuleScope.scene(scene.id()));
        scene.participantIds().forEach(id -> result.add(RuleScope.actor(id)));
        return List.copyOf(result);
    }

    private List<RuleScope> allScopes() {
        ArrayList<RuleScope> result = new ArrayList<>();
        result.add(RuleScope.session());
        result.addAll(ruleSession.scopedStates().keySet());
        return result;
    }

    private void materializeScopeInternal(RuleScope scope) {
        Objects.requireNonNull(scope, "scope");
        if (scope.kind() == RuleScope.Kind.SCENE && !scenes.containsKey(scope.id())) {
            throw new IllegalArgumentException("Unknown scene scope " + scope.id());
        }
        if (ruleSession.findState(scope).isEmpty()) {
            ruleSession = ruleSession.withState(scope, compiled.initialState(Map.of(), Set.of()));
        }
    }

    private LinkedHashMap<RuleScope, RuleRuntimeState> stateFrame() {
        LinkedHashMap<RuleScope, RuleRuntimeState> frame = new LinkedHashMap<>();
        frame.put(RuleScope.session(), ruleSession.state());
        frame.putAll(ruleSession.scopedStates());
        return frame;
    }

    private void apply(Map<RuleScope, RuleRuntimeState> states) {
        for (Map.Entry<RuleScope, RuleRuntimeState> entry : states.entrySet()) {
            ruleSession = ruleSession.withState(entry.getKey(), entry.getValue());
        }
    }

    private void recordRuntimeEvents(List<RuleRuntimeEvent> events) {
        for (RuleRuntimeEvent event : events) {
            String scopeKind = event.details().getOrDefault("scopeKind", "");
            String scopeId = event.details().getOrDefault("scopeId", "");
            String target = scopeKind.isEmpty() ? event.targetId() : scopeKind.toLowerCase() + ':' + scopeId;
            append("RULE_" + event.type(), RuleScope.session(), List.of(), Map.of(
                    "sourceRuleId", event.sourceRuleId(),
                    "target", target,
                    "runtimeSequence", Long.toString(event.sequence())));
        }
    }

    private CompiledRuleset.SceneProcedureDefinition procedure(GameSceneState scene) {
        if (scene.procedureRef().isEmpty()) {
            throw new IllegalStateException("The active scene has no executable procedure");
        }
        CompiledRuleset.SceneProcedureDefinition result =
                compiled.sceneProcedures().get(compiled.resolveId(scene.procedureRef()));
        if (result == null) throw new IllegalStateException("The active scene procedure is unavailable");
        return result;
    }

    private GameSceneState activeScene() {
        if (activeSceneId.isEmpty()) throw new IllegalStateException("No active scene");
        return scenes.get(activeSceneId);
    }

    private GameSceneState scene(String id) {
        GameSceneState result = scenes.get(requireText(id, "sceneId"));
        if (result == null) throw new IllegalArgumentException("Unknown scene " + id);
        return result;
    }

    private void beginCommand() {
        undo.push(new Checkpoint(currentState(), audit.size()));
    }

    private void rollback() {
        Checkpoint checkpoint = undo.pop();
        restoreInternal(checkpoint.state());
        while (audit.size() > checkpoint.auditSize()) audit.remove(audit.size() - 1);
    }

    private void restoreInternal(GameSessionState state) {
        sessionId = state.sessionId();
        displayName = state.displayName();
        status = state.status();
        revision = state.revision();
        random.restore(state.randomState());
        binding = state.rulesetBinding();
        ruleSession = state.ruleSession();
        scenes.clear();
        scenes.putAll(state.scenes());
        activeSceneId = state.activeSceneId();
        compiled = ruleSession.compile(binding.canonicalHash());
    }

    private void commit(
            String type,
            RuleScope source,
            List<RuleScope> targets,
            Map<String, String> details) {
        revision++;
        append(type, source, targets, details);
    }

    private void append(
            String type,
            RuleScope source,
            List<RuleScope> targets,
            Map<String, String> details) {
        String encodedTargets = targets.stream().map(RuleScope::canonicalKey).collect(Collectors.joining(","));
        audit.add(new GameSessionEvent(audit.size(), type,
                source == null ? "" : source.canonicalKey(), encodedTargets, details));
    }

    private void ensureMutable() {
        if (status == GameSessionStatus.COMPLETED) throw new IllegalStateException("A completed session is immutable");
    }

    private void requireStatus(GameSessionStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected session status " + expected + " but was " + status);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }

    private record Checkpoint(GameSessionState state, int auditSize) { }
}
