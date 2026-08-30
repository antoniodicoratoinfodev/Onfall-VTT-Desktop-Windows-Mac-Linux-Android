package app.d6d.engine;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.AbilityEffect;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AreaSpellResult;
import app.d6d.domain.combat.AreaTargetResult;
import app.d6d.domain.combat.AttackOutcome;
import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.AttackResult;
import app.d6d.domain.combat.AutomationStatus;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.domain.combat.CombatEvent;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.CombatStatus;
import app.d6d.domain.combat.CombatantSetup;
import app.d6d.domain.combat.CombatantSnapshot;
import app.d6d.domain.combat.CombatantState;
import app.d6d.domain.combat.CombatResourceState;
import app.d6d.domain.combat.ConcentrationCheckResult;
import app.d6d.domain.combat.ConcentrationState;
import app.d6d.domain.combat.ConditionDuration;
import app.d6d.domain.combat.ConditionExpiry;
import app.d6d.domain.combat.ConditionInstance;
import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.D20RollInput;
import app.d6d.domain.combat.D20RollResult;
import app.d6d.domain.combat.DamageComponent;
import app.d6d.domain.combat.DamageComponentResult;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageResult;
import app.d6d.domain.combat.DeathSaveState;
import app.d6d.domain.combat.DiceRollResult;
import app.d6d.domain.combat.EventType;
import app.d6d.domain.combat.HealingDefinition;
import app.d6d.domain.combat.HealingTarget;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.RollSource;
import app.d6d.domain.combat.SpellSlotResourceId;
import app.d6d.domain.combat.TurnBudget;
import app.d6d.domain.combat.TurnResource;
import app.d6d.domain.space.TokenPlacement;
import app.d6d.domain.space.MapGrid;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.GridLineTraversal;
import app.d6d.domain.space.BattleMap;
import app.d6d.domain.space.MapBackground;
import app.d6d.rules.model.RulesetBinding;
import app.d6d.rules.model.CompiledRuleset;
import app.d6d.rules.model.RuleExecutionResult;
import app.d6d.rules.model.RuleRuntimeEvent;
import app.d6d.rules.model.RuleRuntimeState;
import app.d6d.rules.model.RuleScope;
import app.d6d.rules.model.RuleSessionSnapshot;
import app.d6d.rules.model.RuleValue;
import app.d6d.rules.model.ScopedRuleExecutionResult;
import app.d6d.rules.model.RulesetCanonicalizer;
import app.d6d.rules.model.RulesetRevision;
import app.d6d.rules.model.RulesetRuntimeConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * UI-independent command facade for one encounter. All successful commands are atomic,
 * audited and undoable; rejected commands change neither game state nor RNG.
 */
public final class CombatSession {

    /**
     * Raggio dell'allarme, in piedi.
     *
     * <p>Sessanta piedi sono la distanza a cui un grido si sente in un corridoio
     * senza che serva vedersi: l'allarme infatti non chiede la linea di vista,
     * perche' il suono gira l'angolo. Non e' una regola del manuale — il
     * regolamento lascia la percezione al tavolo — ma una convenzione dichiarata,
     * cosi' il tavolo sa esattamente cosa aspettarsi.</p>
     */
    public static final int DEFAULT_ALARM_RADIUS_FEET = CombatState.DEFAULT_ALARM_RADIUS_FEET;

    private MutableState state;
    private final DeterministicDice dice;
    private final List<CombatEvent> audit;
    private final Deque<Checkpoint> undoStack = new ArrayDeque<>();
    /** Regole spaziali della Board: persistono nel documento Board, non nell'Undo del combattimento. */
    private Set<GridPosition> blockedCells = Set.of();
    private long nextEventSequence;
    private long revisionCounter;
    private List<app.d6d.rules.model.RuleEntity> compiledEntitiesCache;
    private String compiledRulesHashCache;
    private CompiledRuleset compiledRulesCache;

    private CombatSession(
            String encounterId,
            long seed,
            String rulesetVersion,
            String contentVersion,
            RulesetBinding rulesetBinding,
            RulesetRuntimeConfig rulesetRuntime,
            RuleSessionSnapshot ruleSession) {
        this.state = MutableState.empty(
                encounterId, rulesetVersion, contentVersion, rulesetBinding, rulesetRuntime, ruleSession);
        this.dice = new DeterministicDice(seed);
        this.audit = new ArrayList<>();
        this.nextEventSequence = 0;
        this.revisionCounter = 0;
        append(EventType.ENCOUNTER_CREATED, "", "", details(
                "seed", seed,
                "rulesetVersion", rulesetVersion,
                "rulesetRevision", rulesetBinding.revisionId(),
                "rulesetHash", rulesetBinding.canonicalHash(),
                "rulesetRuntimeHash", rulesetBinding.runtimeHash(),
                "runtimeSemantics", rulesetBinding.runtimeSemanticsVersion(),
                "contentVersion", contentVersion));
    }

    private CombatSession(CombatState savedState, List<CombatEvent> savedAudit) {
        validateRuleSnapshot(savedState.rulesetBinding(), savedState.rulesetRuntime(), savedState.ruleSession());
        this.state = MutableState.from(savedState);
        this.dice = DeterministicDice.fromState(savedState.randomSeed(), savedState.randomState());
        this.audit = new ArrayList<>(List.copyOf(savedAudit));
        this.nextEventSequence = audit.stream().mapToLong(CombatEvent::sequence).max().orElse(-1L) + 1;
        this.revisionCounter = Math.max(savedState.revision(),
                audit.stream().mapToLong(CombatEvent::revision).max().orElse(0L));
    }

    public static CombatSession create(String encounterId, long seed) {
        return create(encounterId, seed, "srd-5.2.1", "local-1");
    }

    public static CombatSession create(
            String encounterId, long seed, String rulesetVersion, String contentVersion) {
        requireText(encounterId, "encounterId");
        requireText(rulesetVersion, "rulesetVersion");
        requireText(contentVersion, "contentVersion");
        return new CombatSession(encounterId, seed, rulesetVersion, contentVersion,
                RulesetBinding.legacySrd(rulesetVersion), RulesetRuntimeConfig.standardSrd521(),
                RuleSessionSnapshot.empty());
    }

    /** Crea una sessione vincolata a una revisione pubblicata e al suo snapshot eseguibile. */
    public static CombatSession create(
            String encounterId,
            long seed,
            RulesetBinding rulesetBinding,
            RulesetRuntimeConfig rulesetRuntime,
            String contentVersion) {
        requireText(encounterId, "encounterId");
        Objects.requireNonNull(rulesetBinding, "rulesetBinding");
        Objects.requireNonNull(rulesetRuntime, "rulesetRuntime");
        requireText(contentVersion, "contentVersion");
        validateSupportedRuntime(rulesetBinding, rulesetRuntime);
        return new CombatSession(encounterId, seed, rulesetBinding.revisionId(), contentVersion,
                rulesetBinding, rulesetRuntime, RuleSessionSnapshot.empty());
    }

    /** Crea una sessione autosufficiente con l'intera revisione e lo stato generico iniziale. */
    public static CombatSession create(
            String encounterId,
            long seed,
            RulesetRevision revision,
            String contentVersion) {
        Objects.requireNonNull(revision, "revision");
        requireText(encounterId, "encounterId");
        requireText(contentVersion, "contentVersion");
        RuleSessionSnapshot snapshot = RuleSessionSnapshot.fromRevision(revision);
        validateRuleSnapshot(revision.binding(), revision.runtime(), snapshot);
        return new CombatSession(encounterId, seed, revision.revisionId(), contentVersion,
                revision.binding(), revision.runtime(), snapshot);
    }

    /** Uses actor ids as encounter instance ids; duplicate definitions should use fromCombatants instead. */
    public static CombatSession fromActors(String encounterId, long seed, List<ActorDefinition> actors) {
        Objects.requireNonNull(actors, "actors");
        CombatSession result = create(encounterId, seed);
        for (ActorDefinition actor : actors) {
            result.addCombatant(actor.id(), actor);
        }
        return result;
    }

    public static CombatSession fromCombatants(String encounterId, long seed, List<CombatantSetup> combatants) {
        Objects.requireNonNull(combatants, "combatants");
        CombatSession result = create(encounterId, seed);
        for (CombatantSetup setup : combatants) {
            result.addCombatant(setup.instanceId(), setup.actor());
        }
        return result;
    }

    /** Crea l'incontro usando la revisione selezionata nel wizard. */
    public static CombatSession fromCombatants(
            String encounterId,
            long seed,
            List<CombatantSetup> combatants,
            RulesetBinding rulesetBinding,
            RulesetRuntimeConfig rulesetRuntime,
            String contentVersion) {
        Objects.requireNonNull(combatants, "combatants");
        CombatSession result = create(encounterId, seed, rulesetBinding, rulesetRuntime, contentVersion);
        for (CombatantSetup setup : combatants) {
            result.addCombatant(setup.instanceId(), setup.actor());
        }
        return result;
    }

    public static CombatSession fromCombatants(
            String encounterId,
            long seed,
            List<CombatantSetup> combatants,
            RulesetRevision revision,
            String contentVersion) {
        Objects.requireNonNull(combatants, "combatants");
        CombatSession result = create(encounterId, seed, revision, contentVersion);
        for (CombatantSetup setup : combatants) {
            result.addCombatant(setup.instanceId(), setup.actor());
        }
        return result;
    }

    /** Restores a persistence snapshot. Undo history starts empty; the supplied audit remains append-only. */
    public static CombatSession restore(CombatState savedState, List<CombatEvent> savedAudit) {
        Objects.requireNonNull(savedState, "savedState");
        Objects.requireNonNull(savedAudit, "savedAudit");
        return new CombatSession(savedState, savedAudit);
    }

    public synchronized CombatState currentState() {
        return state.toDomain(dice.seed(), dice.state());
    }

    public synchronized List<CombatEvent> auditTrail() {
        return List.copyOf(audit);
    }

    /**
     * Sostituisce in modo atomico il regolamento di una sessione esistente.
     * Una sessione attiva viene messa in pausa nello stesso comando; una revisione conclusa resta storica.
     */
    public synchronized void changeRuleset(
            RulesetBinding rulesetBinding,
            RulesetRuntimeConfig rulesetRuntime) {
        changeRuleset(rulesetBinding, rulesetRuntime, RuleSessionSnapshot.empty());
    }

    public synchronized void changeRuleset(RulesetRevision revision) {
        Objects.requireNonNull(revision, "revision");
        changeRuleset(revision.binding(), revision.runtime(), RuleSessionSnapshot.fromRevision(revision));
    }

    private void changeRuleset(
            RulesetBinding rulesetBinding,
            RulesetRuntimeConfig rulesetRuntime,
            RuleSessionSnapshot ruleSession) {
        Objects.requireNonNull(rulesetBinding, "rulesetBinding");
        Objects.requireNonNull(rulesetRuntime, "rulesetRuntime");
        Objects.requireNonNull(ruleSession, "ruleSession");
        validateSupportedRuntime(rulesetBinding, rulesetRuntime);
        validateRuleSnapshot(rulesetBinding, rulesetRuntime, ruleSession);
        if (state.status == CombatStatus.RESOLVED) {
            throw rule("A resolved encounter keeps its historical ruleset");
        }
        for (Map.Entry<String, MutableCombatant> entry : state.combatants.entrySet()) {
            if (entry.getValue().exhaustionLevel > rulesetRuntime.maximumExhaustion()) {
                throw rule("Combatant " + entry.getKey() + " has more Exhaustion than the new ruleset allows");
            }
        }
        // Riapplicare la stessa revisione non deve azzerare risorse e condizioni correnti.
        if (state.rulesetBinding.equals(rulesetBinding) && state.rulesetRuntime.equals(rulesetRuntime)
                && state.ruleSession.entities().equals(ruleSession.entities())) return;

        RuleSessionSnapshot migratedRuleSession = migrateRuleSession(ruleSession, rulesetBinding);

        RulesetBinding before = state.rulesetBinding;
        boolean pausedByChange = state.status == CombatStatus.ACTIVE;
        beginCommand();
        if (pausedByChange) {
            state.status = CombatStatus.PAUSED;
            append(EventType.ENCOUNTER_PAUSED, "", "", details("cause", "ruleset change"));
        }
        state.rulesetBinding = rulesetBinding;
        state.rulesetRuntime = rulesetRuntime;
        state.ruleSession = migratedRuleSession;
        state.rulesetVersion = rulesetBinding.revisionId();
        state.turnBudgets.replaceAll((combatantId, budget) ->
                budget.withMovementAllowance(effectiveSpeed(state.combatants.get(combatantId))));
        append(EventType.RULESET_CHANGED, "", "", details(
                "beforeProjectId", before.projectId(),
                "beforeRevisionId", before.revisionId(),
                "beforeHash", before.canonicalHash(),
                "beforeRuntimeHash", before.runtimeHash(),
                "afterProjectId", rulesetBinding.projectId(),
                "afterRevisionId", rulesetBinding.revisionId(),
                "afterHash", rulesetBinding.canonicalHash(),
                "afterRuntimeHash", rulesetBinding.runtimeHash(),
                "runtimeSemantics", rulesetBinding.runtimeSemanticsVersion(),
                "displayName", rulesetBinding.displayName(),
                "migratedValues", migratedRuleSession.state().values().size(),
                "migratedResources", migratedRuleSession.state().resources().size(),
                "migratedConditions", migratedRuleSession.state().conditionStacks().size(),
                "migratedScopes", migratedRuleSession.scopedStates().size(),
                "paused", pausedByChange));
    }

    /**
     * Porta nella nuova revisione soltanto lo stato che ha ancora un significato.
     * I massimi vengono sempre ricalcolati dalle nuove formule; per risorse e budget
     * si conserva quanto e' gia' stato speso, così una modifica live non ricarica il turno.
     */
    private RuleSessionSnapshot migrateRuleSession(
            RuleSessionSnapshot requested,
            RulesetBinding nextBinding) {
        if (!requested.executable() || !state.ruleSession.executable()) return requested;

        CompiledRuleset nextRules = requested.compile(nextBinding.canonicalHash());
        CompiledRuleset previousRules = null;
        try {
            previousRules = state.ruleSession.compile(state.rulesetBinding.canonicalHash());
        } catch (RuntimeException ignored) {
            // Una vecchia revisione corrotta non deve impedire di migrare verso una valida.
        }
        RuleRuntimeState migrated = migrateRuleRuntimeState(
                nextRules, previousRules, state.ruleSession.state());
        LinkedHashMap<RuleScope, RuleRuntimeState> migratedScopes = new LinkedHashMap<>();
        for (Map.Entry<RuleScope, RuleRuntimeState> entry : state.ruleSession.scopedStates().entrySet()) {
            migratedScopes.put(entry.getKey(), migrateRuleRuntimeState(
                    nextRules, previousRules, entry.getValue()));
        }
        return new RuleSessionSnapshot(requested.entities(), migrated, migratedScopes);
    }

    private RuleRuntimeState migrateRuleRuntimeState(
            CompiledRuleset nextRules,
            CompiledRuleset previousRules,
            RuleRuntimeState previous) {
        Set<String> survivingIds = nextRules.entities().keySet();

        LinkedHashMap<String, RuleValue> values = new LinkedHashMap<>();
        previous.values().forEach((id, value) -> {
            if (id.startsWith("context:") || id.startsWith("level:")) {
                if (value.type() == RuleValue.Type.NUMBER || value.type() == RuleValue.Type.BOOLEAN) {
                    values.put(id, value);
                }
                return;
            }
            String resolved = nextRules.resolveId(id);
            CompiledRuleset.ValueDefinition definition = nextRules.valueDefinitions().get(resolved);
            if (definition != null && definition.accepts(value)) {
                if (value.type() != RuleValue.Type.REFERENCE
                        || survivingIds.contains(nextRules.resolveId(value.canonicalValue()))) {
                    values.put(resolved, value);
                }
            } else if (value.type() == RuleValue.Type.NUMBER
                    && (nextRules.stats().containsKey(resolved) || nextRules.skills().containsKey(resolved))) {
                values.put(resolved, value);
            }
        });
        LinkedHashSet<String> activeRules = new LinkedHashSet<>();
        previous.activeRuleIds().forEach(id -> {
            if (id.startsWith("trained:")) {
                String skill = nextRules.resolveId(id.substring("trained:".length()));
                if (nextRules.skills().containsKey(skill)) activeRules.add("trained:" + skill);
                return;
            }
            String resolved = nextRules.resolveId(id);
            if (survivingIds.contains(resolved)) activeRules.add(resolved);
        });

        RuleRuntimeState fresh = nextRules.initialState(values, activeRules);
        LinkedHashMap<String, RuleRuntimeState.ResourceState> resources =
                new LinkedHashMap<>(fresh.resources());
        LinkedHashMap<String, RuleRuntimeState.ResourceState> previousResources = new LinkedHashMap<>();
        previous.resources().forEach((id, resource) -> {
            String resolved = nextRules.resolveId(id);
            if (nextRules.resources().containsKey(resolved)) previousResources.putIfAbsent(resolved, resource);
        });
        resources.replaceAll((id, next) -> {
            RuleRuntimeState.ResourceState old = previousResources.get(id);
            if (old == null) return next;
            BigDecimal spent = old.maximum().subtract(old.current()).max(BigDecimal.ZERO);
            return new RuleRuntimeState.ResourceState(
                    id,
                    next.maximum().subtract(spent).max(BigDecimal.ZERO),
                    next.maximum());
        });

        LinkedHashMap<String, Integer> conditions = new LinkedHashMap<>();
        previous.conditionStacks().forEach((id, stacks) -> {
            String resolved = nextRules.resolveId(id);
            if (nextRules.conditions().contains(resolved)) {
                int maximum = Integer.parseInt(nextRules.entities().get(resolved)
                        .attributes().getOrDefault("maximumStacks", "1"));
                conditions.put(resolved, Math.min(stacks, maximum));
            }
        });

        LinkedHashMap<String, BigDecimal> turnBudget = new LinkedHashMap<>(fresh.turnBudget());
        if (previousRules != null) {
            try {
                Map<String, BigDecimal> previousMaximums = previousRules.beginTurn(previous).turnBudget();
                turnBudget.replaceAll((id, nextMaximum) -> {
                    BigDecimal oldMaximum = previousMaximums.get(id);
                    BigDecimal oldCurrent = previous.turnBudget().get(id);
                    if (oldMaximum == null || oldCurrent == null) return nextMaximum;
                    BigDecimal spent = oldMaximum.subtract(oldCurrent).max(BigDecimal.ZERO);
                    return nextMaximum.subtract(spent).max(BigDecimal.ZERO);
                });
            } catch (RuntimeException ignored) {
                // Il resto dello stato resta migrabile anche se il vecchio budget era invalido.
            }
        }

        return new RuleRuntimeState(
                fresh.values(),
                resources,
                conditions,
                turnBudget,
                fresh.activeRuleIds(),
                Math.max(previous.revision(), fresh.revision()) + 1);
    }

    public synchronized RuleSessionSnapshot genericRuleSession() {
        return state.ruleSession;
    }

    public synchronized BigDecimal genericRuleValue(String ruleId) {
        return genericRuleValue(RuleScope.session(), ruleId);
    }

    public synchronized BigDecimal genericRuleValue(RuleScope scope, String ruleId) {
        return genericRules().value(ruleId, genericRuleState(scope));
    }

    public synchronized RuleValue genericTypedRuleValue(String ruleId) {
        return genericTypedRuleValue(RuleScope.session(), ruleId);
    }

    public synchronized RuleValue genericTypedRuleValue(RuleScope scope, String ruleId) {
        return genericRules().ruleValue(ruleId, genericRuleState(scope));
    }

    public synchronized boolean genericRuleActive(String ruleId) {
        return genericRuleActive(RuleScope.session(), ruleId);
    }

    public synchronized boolean genericRuleActive(RuleScope scope, String ruleId) {
        return genericRules().isRuleActive(ruleId, genericRuleState(scope));
    }

    /** Stato effettivo dello scope; uno scope mai usato espone i default senza materializzarsi. */
    public synchronized RuleRuntimeState genericRuleState(RuleScope scope) {
        RuleScope checked = validateRuleScope(scope);
        return state.ruleSession.findState(checked)
                .orElseGet(() -> genericRules().initialState(Map.of(), Set.of()));
    }

    /** Attiva un owner di modificatori/azioni come comando atomico, auditabile e annullabile. */
    public synchronized void setGenericRuleActive(String ruleId, boolean active) {
        setGenericRuleActive(RuleScope.session(), ruleId, active);
    }

    public synchronized void setGenericRuleActive(RuleScope scope, String ruleId, boolean active) {
        if (state.status == CombatStatus.RESOLVED) throw rule("A resolved encounter is immutable");
        RuleScope checked = validateRuleScope(scope);
        CompiledRuleset rules = genericRules();
        RuleRuntimeState scopedState = genericRuleState(checked);
        boolean before = rules.isRuleActive(ruleId, scopedState);
        if (before == active) return;
        beginCommand();
        try {
            state.ruleSession = state.ruleSession.withState(checked,
                    rules.setRuleActive(ruleId, active, scopedState));
            append(EventType.RULE_ACTIVATION_CHANGED, "", "", details(
                    "ruleId", ruleId,
                    "before", before,
                    "after", active,
                    "scopeKind", checked.kind(),
                    "scopeId", checked.id()));
        } catch (RuntimeException failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /** Correzione tipizzata e atomica di un valore aperto dichiarato dal regolamento. */
    public synchronized void setGenericRuleValue(String ruleId, RuleValue value) {
        setGenericRuleValue(RuleScope.session(), ruleId, value);
    }

    public synchronized void setGenericRuleValue(RuleScope scope, String ruleId, RuleValue value) {
        if (state.status == CombatStatus.RESOLVED) throw rule("A resolved encounter is immutable");
        Objects.requireNonNull(value, "value");
        RuleScope checked = validateRuleScope(scope);
        CompiledRuleset rules = genericRules();
        RuleRuntimeState scopedState = genericRuleState(checked);
        RuleValue before = rules.ruleValue(ruleId, scopedState);
        RuleRuntimeState changed = rules.setRuleValue(ruleId, value, scopedState);
        if (changed.equals(scopedState)) return;
        beginCommand();
        try {
            state.ruleSession = state.ruleSession.withState(checked, changed);
            append(EventType.RULE_VALUE_SET, "", "", details(
                    "ruleId", ruleId,
                    "beforeType", before.type(),
                    "before", before.canonicalValue(),
                    "afterType", value.type(),
                    "after", value.canonicalValue(),
                    "scopeKind", checked.kind(),
                    "scopeId", checked.id()));
        } catch (RuntimeException failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    public synchronized void setGenericNumericRuleValue(String ruleId, BigDecimal value) {
        setGenericNumericRuleValue(RuleScope.session(), ruleId, value);
    }

    public synchronized void setGenericNumericRuleValue(RuleScope scope, String ruleId, BigDecimal value) {
        if (state.status == CombatStatus.RESOLVED) throw rule("A resolved encounter is immutable");
        RuleScope checked = validateRuleScope(scope);
        CompiledRuleset rules = genericRules();
        RuleRuntimeState scopedState = genericRuleState(checked);
        BigDecimal before = rules.value(ruleId, scopedState);
        RuleRuntimeState changed = rules.setNumericValue(ruleId, value, scopedState);
        if (changed.equals(scopedState)) return;
        BigDecimal after = rules.value(ruleId, changed);
        beginCommand();
        try {
            state.ruleSession = state.ruleSession.withState(checked, changed);
            append(EventType.RULE_VALUE_SET, "", "", details(
                    "ruleId", ruleId, "beforeType", RuleValue.Type.NUMBER,
                    "before", before, "afterType", RuleValue.Type.NUMBER, "after", after,
                    "scopeKind", checked.kind(), "scopeId", checked.id()));
        } catch (RuntimeException failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    public synchronized void setGenericResource(String resourceId, BigDecimal current, BigDecimal maximum) {
        setGenericResource(RuleScope.session(), resourceId, current, maximum);
    }

    public synchronized void setGenericResource(
            RuleScope scope,
            String resourceId,
            BigDecimal current,
            BigDecimal maximum) {
        if (state.status == CombatStatus.RESOLVED) throw rule("A resolved encounter is immutable");
        RuleScope checked = validateRuleScope(scope);
        RuleRuntimeState scopedState = genericRuleState(checked);
        RuleRuntimeState.ResourceState before = scopedState.resources()
                .get(genericRules().resolveId(resourceId));
        if (before == null) throw rule("Unknown generic resource " + resourceId);
        RuleRuntimeState changed = genericRules().setResource(
                resourceId, current, maximum, scopedState);
        if (changed.equals(scopedState)) return;
        beginCommand();
        try {
            state.ruleSession = state.ruleSession.withState(checked, changed);
            append(EventType.RULE_RESOURCE_SET, "", "", details(
                    "resourceId", resourceId,
                    "beforeCurrent", before.current(), "beforeMaximum", before.maximum(),
                    "afterCurrent", current, "afterMaximum", maximum,
                    "scopeKind", checked.kind(), "scopeId", checked.id()));
        } catch (RuntimeException failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    public synchronized void setGenericConditionStacks(String conditionId, int stacks) {
        setGenericConditionStacks(RuleScope.session(), conditionId, stacks);
    }

    public synchronized void setGenericConditionStacks(RuleScope scope, String conditionId, int stacks) {
        if (state.status == CombatStatus.RESOLVED) throw rule("A resolved encounter is immutable");
        RuleScope checked = validateRuleScope(scope);
        String resolved = genericRules().resolveId(conditionId);
        RuleRuntimeState scopedState = genericRuleState(checked);
        int before = scopedState.conditionStacks().getOrDefault(resolved, 0);
        RuleRuntimeState changed = genericRules().setConditionStacks(
                conditionId, stacks, scopedState);
        if (changed.equals(scopedState)) return;
        beginCommand();
        try {
            state.ruleSession = state.ruleSession.withState(checked, changed);
            append(EventType.RULE_CONDITION_SET, "", "", details(
                    "conditionId", conditionId, "before", before, "after", stacks,
                    "scopeKind", checked.kind(), "scopeId", checked.id()));
        } catch (RuntimeException failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    public synchronized void setGenericTurnResource(String resourceId, BigDecimal current) {
        setGenericTurnResource(RuleScope.session(), resourceId, current);
    }

    public synchronized void setGenericTurnResource(RuleScope scope, String resourceId, BigDecimal current) {
        if (state.status == CombatStatus.RESOLVED) throw rule("A resolved encounter is immutable");
        RuleScope checked = validateRuleScope(scope);
        RuleRuntimeState scopedState = genericRuleState(checked);
        BigDecimal before = scopedState.turnBudget().get(resourceId);
        if (before == null) throw rule("Unknown generic turn resource " + resourceId);
        RuleRuntimeState changed = genericRules().setTurnResource(
                resourceId, current, scopedState);
        if (changed.equals(scopedState)) return;
        beginCommand();
        try {
            state.ruleSession = state.ruleSession.withState(checked, changed);
            append(EventType.RULE_TURN_RESOURCE_SET, "", "", details(
                    "resourceId", resourceId, "before", before, "after", current,
                    "scopeKind", checked.kind(), "scopeId", checked.id()));
        } catch (RuntimeException failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /** Esegue costi ed effetti atomici dell'action economy generica ed entra nell'Undo/audit. */
    public synchronized RuleExecutionResult executeRuleAction(String actionId) {
        return executeRuleAction(RuleScope.session(), actionId);
    }

    public synchronized RuleExecutionResult executeRuleAction(RuleScope scope, String actionId) {
        ScopedRuleExecutionResult result = executeRuleAction(scope, scope, actionId);
        return new RuleExecutionResult(result.state(scope), result.events());
    }

    public synchronized ScopedRuleExecutionResult executeRuleAction(
            RuleScope sourceScope,
            RuleScope targetScope,
            String actionId) {
        requireStatus(CombatStatus.ACTIVE);
        RuleScope source = validateRuleScope(sourceScope);
        RuleScope target = validateRuleScope(targetScope);
        LinkedHashMap<RuleScope, RuleRuntimeState> frame = genericRuleFrame(source, target);
        beginCommand();
        try {
            ScopedRuleExecutionResult result = genericRules().executeScopedAction(
                    actionId, source, target, frame);
            result.states().forEach((scope, changed) ->
                    state.ruleSession = state.ruleSession.withState(scope, changed));
            append(EventType.RULE_ACTION_EXECUTED,
                    source.kind() == RuleScope.Kind.ACTOR ? source.id() : "",
                    target.kind() == RuleScope.Kind.ACTOR ? target.id() : "",
                    genericEventDetails(actionId, result.events(), source, target));
            return result;
        } catch (RuntimeException failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /** Invia un trigger arbitrario (riposo, cambio scena, evento homebrew) al runtime della sessione. */
    public synchronized RuleExecutionResult fireRuleEvent(String eventType) {
        return fireRuleEvent(RuleScope.session(), eventType);
    }

    public synchronized RuleExecutionResult fireRuleEvent(RuleScope scope, String eventType) {
        ScopedRuleExecutionResult result = fireRuleEvent(scope, scope, eventType);
        return new RuleExecutionResult(result.state(scope), result.events());
    }

    public synchronized ScopedRuleExecutionResult fireRuleEvent(
            RuleScope sourceScope,
            RuleScope targetScope,
            String eventType) {
        if (state.status == CombatStatus.RESOLVED) throw rule("A resolved encounter is immutable");
        RuleScope source = validateRuleScope(sourceScope);
        RuleScope target = validateRuleScope(targetScope);
        LinkedHashMap<RuleScope, RuleRuntimeState> frame = genericRuleFrame(source, target);
        beginCommand();
        try {
            ScopedRuleExecutionResult result = genericRules().fireScopedEvent(
                    eventType, source, target, frame);
            result.states().forEach((scope, changed) ->
                    state.ruleSession = state.ruleSession.withState(scope, changed));
            append(EventType.RULE_EVENT_FIRED,
                    source.kind() == RuleScope.Kind.ACTOR ? source.id() : "",
                    target.kind() == RuleScope.Kind.ACTOR ? target.id() : "",
                    genericEventDetails(eventType, result.events(), source, target));
            return result;
        } catch (RuntimeException failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    public synchronized CompiledRuleset.RandomizerResult rollRuleRandomizer(String randomizerId) {
        return rollRuleRandomizer(RuleScope.session(), randomizerId);
    }

    public synchronized CompiledRuleset.RandomizerResult rollRuleRandomizer(
            RuleScope scope,
            String randomizerId) {
        if (state.status == CombatStatus.RESOLVED) throw rule("A resolved encounter is immutable");
        RuleScope checked = validateRuleScope(scope);
        beginCommand();
        try {
            CompiledRuleset.RandomizerResult result = genericRules().roll(
                    randomizerId, genericRuleState(checked), bound -> dice.roll(bound) - 1);
            append(EventType.RULE_RANDOMIZER_ROLLED, "", "", details(
                    "randomizer", result.randomizerId(),
                    "draws", result.draws(),
                    "value", result.value().toPlainString(),
                    "tableValue", result.tableValue() == null ? "" : result.tableValue().canonicalValue(),
                    "scopeKind", checked.kind(),
                    "scopeId", checked.id()));
            return result;
        } catch (RuntimeException failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    private CompiledRuleset genericRules() {
        if (!state.ruleSession.executable()) {
            throw rule("This session has no embedded executable generic rules");
        }
        if (!state.ruleSession.entities().equals(compiledEntitiesCache)
                || !state.rulesetBinding.canonicalHash().equals(compiledRulesHashCache)) {
            compiledRulesCache = state.ruleSession.compile(state.rulesetBinding.canonicalHash());
            compiledEntitiesCache = state.ruleSession.entities();
            compiledRulesHashCache = state.rulesetBinding.canonicalHash();
        }
        return compiledRulesCache;
    }

    private RuleScope validateRuleScope(RuleScope scope) {
        RuleScope checked = Objects.requireNonNull(scope, "scope");
        if (checked.kind() == RuleScope.Kind.ACTOR && !state.combatants.containsKey(checked.id())) {
            throw rule("Unknown combatant rule scope " + checked.id());
        }
        return checked;
    }

    private LinkedHashMap<RuleScope, RuleRuntimeState> genericRuleFrame(
            RuleScope source,
            RuleScope target) {
        LinkedHashMap<RuleScope, RuleRuntimeState> frame = new LinkedHashMap<>();
        RuleScope sessionScope = RuleScope.session();
        frame.put(sessionScope, genericRuleState(sessionScope));
        frame.put(source, genericRuleState(source));
        frame.put(target, genericRuleState(target));
        return frame;
    }

    private void fireGenericEventInternal(String eventType) {
        fireGenericEventInternal(eventType, RuleScope.session());
    }

    private void fireGenericEventInternal(String eventType, RuleScope scope) {
        if (!state.ruleSession.executable()) return;
        RuleScope checked = validateRuleScope(scope);
        ScopedRuleExecutionResult result = genericRules().fireScopedEvent(
                eventType, checked, checked, genericRuleFrame(checked, checked));
        result.states().forEach((changedScope, changed) ->
                state.ruleSession = state.ruleSession.withState(changedScope, changed));
        if (!result.events().isEmpty()) {
            append(EventType.RULE_EVENT_FIRED, "", "",
                    genericEventDetails(eventType, result.events(), checked, checked));
        }
    }

    private static Map<String, String> genericEventDetails(String source, List<RuleRuntimeEvent> events) {
        return details(
                "source", source,
                "eventCount", events.size(),
                "events", events.stream().map(RuleRuntimeEvent::type).collect(Collectors.joining(",")));
    }

    private static Map<String, String> genericEventDetails(
            String source,
            List<RuleRuntimeEvent> events,
            RuleScope scope) {
        LinkedHashMap<String, String> scoped = new LinkedHashMap<>(genericEventDetails(source, events));
        scoped.put("scopeKind", scope.kind().name());
        scoped.put("scopeId", scope.id());
        return Map.copyOf(scoped);
    }

    private static Map<String, String> genericEventDetails(
            String sourceRuleId,
            List<RuleRuntimeEvent> events,
            RuleScope source,
            RuleScope target) {
        LinkedHashMap<String, String> scoped = new LinkedHashMap<>(genericEventDetails(sourceRuleId, events));
        scoped.put("scopeKind", source.kind().name());
        scoped.put("scopeId", source.id());
        scoped.put("targetScopeKind", target.kind().name());
        scoped.put("targetScopeId", target.id());
        return Map.copyOf(scoped);
    }

    public synchronized boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Revisione dello stato che il prossimo {@link #undo()} ripristinerebbe.
     *
     * <p>Serve a chi tratta piu' comandi come una sola operazione di tavolo: la
     * revisione corrente non basta a sapere se il proprio gruppo e' ancora in
     * cima, perche' ogni Undo ne assegna comunque una nuova. Il confine, invece,
     * torna a scendere sotto quella del gruppo esattamente quando ogni comando
     * inserito sopra e' stato rimosso.</p>
     *
     * @return la revisione ripristinabile, o {@link Long#MIN_VALUE} se non c'e' nulla da annullare
     */
    public synchronized long nextUndoRevision() {
        Checkpoint next = undoStack.peek();
        return next == null ? Long.MIN_VALUE : next.state.revision;
    }

    public synchronized void addCombatant(String instanceId, ActorDefinition actor) {
        requireStatus(CombatStatus.DRAFT);
        requireText(instanceId, "instanceId");
        Objects.requireNonNull(actor, "actor");
        if (state.combatants.containsKey(instanceId)) {
            throw rule("Duplicate combatant instance id: " + instanceId);
        }
        CombatantSnapshot snapshot = CombatantSnapshot.from(instanceId, actor);
        beginCommand();
        state.rosterOrder.add(instanceId);
        state.combatants.put(instanceId, MutableCombatant.from(snapshot));
        state.turnBudgets.put(instanceId, TurnBudget.fresh(snapshot.speedFeet(), snapshot.attacksPerAction()));
        append(EventType.COMBATANT_ADDED, instanceId, "", details(
                "definitionId", actor.id(), "definitionVersion", actor.definitionVersion(), "name", actor.name()));
    }

    /**
     * Adds a combatant while an encounter is already being prepared or played.
     *
     * <p>The new entry receives its static initiative immediately. In live play the
     * initiative list is rebuilt around the existing current turn, so reinforcements
     * enter the queue without stealing the action in progress.</p>
     */
    public synchronized void addCombatantToEncounter(String instanceId, ActorDefinition actor, boolean party) {
        if (state.status == CombatStatus.RESOLVED) {
            throw rule("A resolved encounter cannot receive new combatants");
        }
        requireText(instanceId, "instanceId");
        Objects.requireNonNull(actor, "actor");
        if (state.combatants.containsKey(instanceId)) {
            throw rule("Duplicate combatant instance id: " + instanceId);
        }
        String anchor = (state.status == CombatStatus.ACTIVE || state.status == CombatStatus.PAUSED)
                ? currentTurnAnchor()
                : "";
        CombatantSnapshot snapshot = CombatantSnapshot.from(instanceId, actor);
        beginCommand();
        state.rosterOrder.add(instanceId);
        state.combatants.put(instanceId, MutableCombatant.from(snapshot));
        state.turnBudgets.put(instanceId, TurnBudget.fresh(snapshot.speedFeet(), snapshot.attacksPerAction()));
        state.initiativeScores.put(instanceId, actor.initiativeScore());
        if (party) {
            state.partyCombatantIds.add(instanceId);
        }
        if (state.status == CombatStatus.ACTIVE || state.status == CombatStatus.PAUSED) {
            rebuildInitiativeOrder();
            List<List<String>> groups = turnGroups();
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).contains(anchor)) {
                    state.turnIndex = i;
                    break;
                }
            }
        } else if (state.status == CombatStatus.READY &&
                state.initiativeScores.keySet().containsAll(state.combatants.keySet())) {
            rebuildInitiativeOrder();
        }
        append(EventType.COMBATANT_ADDED, instanceId, "", details(
                "definitionId", actor.id(),
                "definitionVersion", actor.definitionVersion(),
                "name", actor.name(),
                "faction", party ? "party" : "enemy",
                "initiative", actor.initiativeScore(),
                "live", state.status == CombatStatus.ACTIVE || state.status == CombatStatus.PAUSED));
    }

    public synchronized void markReady() {
        requireStatus(CombatStatus.DRAFT);
        if (state.combatants.isEmpty()) throw rule("An encounter needs at least one combatant");
        beginCommand();
        state.status = CombatStatus.READY;
        append(EventType.ENCOUNTER_READY, "", "", Map.of());
    }

    /** Records encounter sides in the portable snapshot before initiative begins. */
    public synchronized void setPartyCombatants(Collection<String> combatantIds) {
        requireStatus(CombatStatus.DRAFT);
        Objects.requireNonNull(combatantIds, "combatantIds");
        List<String> ids = List.copyOf(combatantIds);
        if (new HashSet<>(ids).size() != ids.size()) {
            throw rule("Party combatants must be unique");
        }
        for (String id : ids) combatant(id);
        beginCommand();
        state.partyCombatantIds.clear();
        state.partyCombatantIds.addAll(ids);
        append(EventType.PARTY_SET, "", "", details("combatantIds", String.join(",", ids)));
    }

    /** Sets an already-computed initiative total, useful in Tracker mode. */
    public synchronized void setInitiative(String combatantId, int total) {
        requireSetupPhase();
        combatant(combatantId);
        beginCommand();
        state.initiativeScores.put(combatantId, total);
        state.initiativeOrder.clear();
        append(EventType.INITIATIVE_SET, combatantId, "", details("total", total));
    }

    public synchronized D20RollResult rollInitiative(String combatantId, D20Mode mode) {
        requireSetupPhase();
        MutableCombatant combatant = combatant(combatantId);
        Objects.requireNonNull(mode, "mode");
        beginCommand();
        try {
            D20Mode effectiveMode = imposeDisadvantage(
                    mode,
                    combatant.snapshot.strengthDexterityD20Disadvantage());
            D20RollResult roll = rollD20(
                    D20RollInput.digital(effectiveMode),
                    combatant.snapshot.initiativeModifier());
            state.initiativeScores.put(combatantId, roll.total());
            state.initiativeOrder.clear();
            append(EventType.INITIATIVE_ROLLED, combatantId, "", rollDetails(roll));
            return roll;
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /**
     * The same dice pool is shared, while each combatant applies its own modifier
     * and selects the die using its effective advantage/disadvantage state.
     */
    public synchronized Map<String, D20RollResult> rollSharedInitiative(
            Collection<String> combatantIds, D20Mode mode) {
        requireSetupPhase();
        Objects.requireNonNull(combatantIds, "combatantIds");
        Objects.requireNonNull(mode, "mode");
        List<String> ids = List.copyOf(combatantIds);
        if (ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) {
            throw rule("Shared initiative needs a non-empty set of unique combatants");
        }
        for (String id : ids) combatant(id);
        beginCommand();
        try {
            boolean anyImposedDisadvantage = ids.stream()
                    .map(this::combatant)
                    .anyMatch(it -> it.snapshot.strengthDexterityD20Disadvantage());
            D20Mode diceMode =
                    mode == D20Mode.NORMAL && !anyImposedDisadvantage ? D20Mode.NORMAL : D20Mode.DISADVANTAGE;
            List<Integer> rolledDice = dice.rollD20(diceMode);
            Map<String, D20RollResult> results = new LinkedHashMap<>();
            for (String id : ids) {
                CombatantSnapshot snapshot = combatant(id).snapshot;
                D20Mode effectiveMode =
                        imposeDisadvantage(mode, snapshot.strengthDexterityD20Disadvantage());
                int natural = selectedD20(rolledDice, effectiveMode);
                int modifier = snapshot.initiativeModifier();
                int total = checkedTotal(natural, modifier, "Initiative total");
                D20RollResult result = new D20RollResult(RollSource.DIGITAL, effectiveMode, rolledDice,
                        natural, modifier, total);
                results.put(id, result);
                state.initiativeScores.put(id, result.total());
                append(EventType.INITIATIVE_ROLLED, id, "", merge(
                        rollDetails(result), details("shared", true)));
            }
            state.initiativeOrder.clear();
            return Map.copyOf(results);
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    public synchronized int useStaticInitiative(String combatantId, D20Mode mode) {
        requireSetupPhase();
        MutableCombatant combatant = combatant(combatantId);
        Objects.requireNonNull(mode, "mode");
        D20Mode effectiveMode = imposeDisadvantage(
                mode,
                combatant.snapshot.strengthDexterityD20Disadvantage());
        int adjustment =
                effectiveMode == D20Mode.ADVANTAGE ? 5 : effectiveMode == D20Mode.DISADVANTAGE ? -5 : 0;
        int total = checkedTotal(combatant.snapshot.initiativeScore(), adjustment, "Static initiative total");
        beginCommand();
        state.initiativeScores.put(combatantId, total);
        state.initiativeOrder.clear();
        append(EventType.INITIATIVE_SET, combatantId, "", details(
                "total", total, "mode", effectiveMode, "static", true));
        return total;
    }

    /** Explicit ordering is how the DM/player resolves ties. */
    public synchronized void setInitiativeOrder(List<String> orderedCombatantIds) {
        requireSetupPhase();
        Objects.requireNonNull(orderedCombatantIds, "orderedCombatantIds");
        List<String> order = List.copyOf(orderedCombatantIds);
        Set<String> expected = state.combatants.keySet();
        if (order.size() != expected.size() || new HashSet<>(order).size() != order.size()
                || !new HashSet<>(order).equals(expected) || !state.initiativeScores.keySet().containsAll(expected)) {
            throw rule("Initiative order must contain every initialized combatant exactly once");
        }
        beginCommand();
        state.initiativeOrder.clear();
        state.initiativeOrder.addAll(order);
        append(EventType.INITIATIVE_ORDER_SET, "", "", details("order", String.join(",", order)));
    }

    public synchronized void start() {
        requireStatus(CombatStatus.READY);
        if (!state.initiativeScores.keySet().containsAll(state.combatants.keySet())) {
            throw rule("Every combatant needs initiative before starting");
        }
        beginCommand();
        try {
            if (state.initiativeOrder.size() != state.combatants.size()) {
                rebuildInitiativeOrder();
            }
            state.status = CombatStatus.ACTIVE;
            state.round = 1;
            state.turnIndex = 0;
            append(EventType.ENCOUNTER_STARTED, "", "", details(
                    "initiativeOrder", String.join(",", state.initiativeOrder)));
            append(EventType.ROUND_STARTED, "", "", details("round", state.round));
            if (seekFirstPlayableTurn()) {
                startTurnInternal();
            }
        } catch (RuntimeException failure) {
            rollbackFailedCommand();
            throw failure;
        }
        // Setup is the immutable baseline of live play: Undo must never return the combat UI to READY/DRAFT.
        undoStack.clear();
    }

    public synchronized void pause() {
        requireStatus(CombatStatus.ACTIVE);
        beginCommand();
        state.status = CombatStatus.PAUSED;
        append(EventType.ENCOUNTER_PAUSED, "", "", Map.of());
    }

    public synchronized void resume() {
        requireStatus(CombatStatus.PAUSED);
        beginCommand();
        state.status = CombatStatus.ACTIVE;
        append(EventType.ENCOUNTER_RESUMED, "", "", Map.of());
    }

    public synchronized void resolve(String outcome) {
        if (state.status != CombatStatus.ACTIVE && state.status != CombatStatus.PAUSED) {
            throw rule("Only an active or paused encounter can be resolved");
        }
        beginCommand();
        state.status = CombatStatus.RESOLVED;
        append(EventType.ENCOUNTER_RESOLVED, "", "", details("outcome", outcome == null ? "" : outcome));
    }

    public synchronized AttackResult attack(AttackRequest request) {
        requireStatus(CombatStatus.ACTIVE);
        Objects.requireNonNull(request, "request");
        MutableCombatant attacker = combatant(request.attackerId());
        MutableCombatant target = combatant(request.targetId());
        if (isDead(attacker)) {
            throw rule("A dead combatant cannot attack");
        }
        if (attacker.currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot attack");
        }
        if (isDead(target)) {
            throw rule("A dead combatant cannot be targeted by an attack");
        }
        if (attacker.conditions.stream().anyMatch(condition -> incapacitates(condition.type()))) {
            throw rule("An incapacitated combatant cannot attack");
        }
        AbilityDefinition ability = ability(attacker, request.abilityId());
        if (ability.resolutionMethod() != ResolutionMethod.ATTACK_ROLL) {
            throw rule("Ability does not use an attack roll: " + ability.id());
        }
        if (attacker.snapshot.strengthDexterityD20Disadvantage() && ability.spellOrCantrip()) {
            throw rule("The combatant cannot cast spells while wearing armor without training");
        }
        if (ability.automationStatus() == AutomationStatus.MANUAL_REQUIRED
                && request.attackRoll().source() == RollSource.DIGITAL) {
            throw rule("Ability requires manual resolution: " + ability.id());
        }
        if (!request.manualDamageValues().isEmpty()
                && request.manualDamageValues().size() != ability.damage().size()) {
            throw rule("Manual damage must contain one value per damage component");
        }
        validateRange(request.attackerId(), request.targetId(), ability);
        validateAttackActivationCost(
                request.attackerId(), ability.activationCost(), ability.spellOrCantrip());
        validateAbilityResource(request.attackerId(), attacker, ability);

        beginCommand();
        try {
            // Anche un colpo mancato si sente passare: chi e' preso di mira smette
            // di essere una creatura che non si e' accorta di niente.
            noticed(request.targetId());
            consumeAttackActivationCost(
                    request.attackerId(), ability.activationCost(), ability.spellOrCantrip());
            consumeAbilityResource(request.attackerId(), attacker, ability);
            D20RollInput attackInput = imposeDisadvantage(
                    request.attackRoll(),
                    attacker.snapshot.strengthDexterityD20Disadvantage()
                            && usesStrengthOrDexterityForAttack(ability));
            D20RollResult attackRoll = rollD20For(attacker, attackInput, ability.attackBonus());
            AttackOutcome outcome = attackOutcome(attackRoll, target.snapshot.armorClass());
            Map<String, String> attackDetails = merge(
                    rollDetails(attackRoll),
                    details(
                            "abilityId", ability.id(),
                            "abilityName", ability.name(),
                            "armorClass", target.snapshot.armorClass()));
            append(EventType.ATTACK_ROLLED, request.attackerId(), request.targetId(), attackDetails);

            if (outcome == AttackOutcome.MISS) {
                append(EventType.ATTACK_MISSED, request.attackerId(), request.targetId(), attackDetails);
                return new AttackResult(request.attackerId(), request.targetId(), ability.id(), attackRoll,
                        outcome, List.of(), Optional.empty());
            }

            boolean critical = outcome == AttackOutcome.CRITICAL_HIT;
            append(critical ? EventType.CRITICAL_HIT : EventType.ATTACK_HIT,
                    request.attackerId(), request.targetId(), attackDetails);
            List<DamageComponent> rolledDamage = resolveAttackDamage(ability, request.manualDamageValues(), critical,
                    request.attackerId(), request.targetId());
            DamageResult damage = applyDamageInternal(request.attackerId(), request.targetId(), rolledDamage,
                    critical, D20RollInput.digital());
            return new AttackResult(request.attackerId(), request.targetId(), ability.id(), attackRoll,
                    outcome, rolledDamage, Optional.of(damage));
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /**
     * Lancia un incantesimo ad area risolvendo i tiri salvezza automaticamente.
     *
     * <p>Il danno si tira una sola volta; poi ogni creatura entro la sfera di raggio
     * indicato, centrata sulla casella scelta, tira il proprio tiro salvezza contro
     * la CD incantesimi del lanciatore. Chi fallisce subisce il danno pieno, chi
     * supera ne subisce meta' quando l'incantesimo lo prevede. Come una vera area,
     * colpisce chiunque sia dentro il raggio, alleati compresi.</p>
     */
    public synchronized AreaSpellResult castArea(String casterId, GridPosition center, String abilityId) {
        return castAreaInternal(casterId, center, abilityId, null);
    }

    /**
     * Lancia un incantesimo ad area con i tiri salvezza decisi al tavolo.
     *
     * <p>{@code savedByTarget} dice, per ciascun bersaglio, se ha superato il tiro
     * salvezza; chi non compare vale come fallito (danno pieno). Non si tira alcun
     * d20: e' la risoluzione manuale, quella usata con la modalita' modifica attiva.</p>
     */
    public synchronized AreaSpellResult castAreaManual(
            String casterId, GridPosition center, String abilityId, Map<String, Boolean> savedByTarget) {
        Objects.requireNonNull(savedByTarget, "savedByTarget");
        return castAreaInternal(casterId, center, abilityId, Map.copyOf(savedByTarget));
    }

    /**
     * Bersagli che l'area coprirebbe, senza tirare nulla ne' toccare lo stato.
     *
     * <p>Serve all'interfaccia per elencare chi verrebbe colpito prima di confermare
     * — in particolare per costruire i tiri salvezza da decidere a mano.</p>
     */
    public synchronized List<String> areaTargets(String casterId, GridPosition center, String abilityId) {
        Objects.requireNonNull(center, "center");
        if (!state.battleMap.configured()) return List.of();
        AbilityDefinition ability = ability(combatant(casterId), abilityId);
        if (!ability.isArea()) return List.of();
        return combatantsInArea(center, ability.areaRadiusFeet());
    }

    private AreaSpellResult castAreaInternal(
            String casterId, GridPosition center, String abilityId, Map<String, Boolean> savedByTarget) {
        requireStatus(CombatStatus.ACTIVE);
        Objects.requireNonNull(center, "center");
        requireConfiguredMap();
        MutableCombatant caster = combatant(casterId);
        if (isDead(caster)) {
            throw rule("A dead combatant cannot cast");
        }
        if (caster.currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot cast");
        }
        if (caster.conditions.stream().anyMatch(condition -> incapacitates(condition.type()))) {
            throw rule("An incapacitated combatant cannot cast");
        }
        AbilityDefinition ability = ability(caster, abilityId);
        if (!ability.isArea()) {
            throw rule("Ability is not an area effect: " + ability.id());
        }
        if (caster.snapshot.strengthDexterityD20Disadvantage() && ability.spellOrCantrip()) {
            throw rule("The combatant cannot cast spells while wearing armor without training");
        }
        if (!state.battleMap.grid().contains(center)) {
            throw rule("The area centre is outside the map");
        }
        if (blockedCells.contains(center)) {
            throw rule("The area centre is blocked by a wall");
        }
        validateAreaRange(casterId, center, ability);
        validateActivationCost(casterId, ability.activationCost(), ability.spellOrCantrip());
        validateAbilityResource(casterId, caster, ability);

        beginCommand();
        try {
            consumeActivationCost(casterId, ability.activationCost(), ability.spellOrCantrip());
            consumeAbilityResource(casterId, caster, ability);
            // Un solo tiro di danno per tutta l'area; i critici non toccano i tiri
            // salvezza, quindi i dadi non si raddoppiano mai qui.
            List<DamageComponent> rolled = resolveAttackDamage(ability, List.of(), false, casterId, "");
            int saveDc = caster.snapshot.spellSaveDc();
            List<String> targets = combatantsInArea(center, ability.areaRadiusFeet());
            append(EventType.AREA_SPELL_CAST, casterId, "", details(
                    "abilityId", ability.id(),
                    "abilityName", ability.name(),
                    "center", center,
                    "radiusFeet", ability.areaRadiusFeet(),
                    "saveDc", saveDc,
                    "targets", targets.size()));

            List<AreaTargetResult> perTarget = new ArrayList<>();
            for (String targetId : targets) {
                perTarget.add(resolveAreaTarget(casterId, targetId, ability, saveDc, rolled, savedByTarget));
            }
            AreaSpellResult result = new AreaSpellResult(
                    casterId, ability.id(), center, ability.areaRadiusFeet(), saveDc, rolled, perTarget);
            // Un'area puo' comprendere il lanciatore. Se questi si porta a 0 PF e
            // nel suo gruppo non resta nessun altro attore vivo, il turno non deve
            // restare sospeso senza un combattente corrente: si chiude e si passa
            // al prossimo gruppo giocabile nello stesso comando (e nello stesso
            // passo di Undo) del lancio.
            if (caster.currentHitPoints == 0 && currentCombatantIds().isEmpty()) {
                endTurnInternal();
            }
            return result;
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    private AreaTargetResult resolveAreaTarget(
            String casterId, String targetId, AbilityDefinition ability, int saveDc,
            List<DamageComponent> rolled, Map<String, Boolean> savedByTarget) {
        D20RollResult saveRoll = null;
        boolean saved;
        if (savedByTarget != null) {
            // Risoluzione manuale: il tavolo ha gia' deciso, nessun d20.
            saved = Boolean.TRUE.equals(savedByTarget.get(targetId));
        } else if (ability.hasSavingThrow()) {
            MutableCombatant target = combatant(targetId);
            int bonus = target.snapshot.saveBonus(ability.saveAbility());
            boolean strengthOrDexteritySave =
                    ability.saveAbility() == SaveAbility.STRENGTH
                            || ability.saveAbility() == SaveAbility.DEXTERITY;
            D20RollInput saveInput = imposeDisadvantage(
                    D20RollInput.digital(),
                    strengthOrDexteritySave && target.snapshot.strengthDexterityD20Disadvantage());
            saveRoll = rollD20For(target, saveInput, bonus);
            saved = saveRoll.total() >= saveDc;
        } else {
            saved = false;
        }
        if (ability.hasSavingThrow()) {
            Map<String, String> saveDetails = details(
                    "abilityId", ability.id(),
                    "abilityName", ability.name(),
                    "save", ability.saveAbility(),
                    "dc", saveDc,
                    "saved", saved);
            append(EventType.SAVING_THROW_ROLLED, casterId, targetId, saveRoll == null
                    ? merge(saveDetails, details("source", "MANUAL"))
                    : merge(rollDetails(saveRoll), saveDetails));
        }
        List<DamageComponent> applied = damageAfterSave(rolled, ability, saved);
        DamageResult damage = applied.isEmpty()
                ? null
                : applyDamageInternal(casterId, targetId, applied, false, D20RollInput.digital());
        return new AreaTargetResult(targetId, saved, Optional.ofNullable(saveRoll), Optional.ofNullable(damage));
    }

    /** Danno dopo il tiro salvezza: pieno se fallito, meta' (o nullo) se superato. */
    private static List<DamageComponent> damageAfterSave(
            List<DamageComponent> rolled, AbilityDefinition ability, boolean saved) {
        if (!saved || !ability.hasSavingThrow()) return rolled;
        if (!ability.halfOnSave()) return List.of();
        List<DamageComponent> halved = new ArrayList<>(rolled.size());
        for (DamageComponent component : rolled) {
            int half = component.amount() / 2;
            if (half > 0) halved.add(new DamageComponent(component.type(), half));
        }
        return halved;
    }

    private void validateAreaRange(String casterId, GridPosition center, AbilityDefinition ability) {
        TokenPlacement caster = state.battleMap.placementOf(casterId).orElse(null);
        if (caster == null) return; // lanciatore non sulla mappa: nessuna gittata da imporre
        int nearest = Integer.MAX_VALUE;
        for (GridPosition square : caster.occupiedSquares()) {
            nearest = Math.min(nearest, square.squaresTo(center));
        }
        int distance = state.battleMap.grid().feetFor(nearest);
        if (distance > ability.rangeFeet()) {
            throw rule("The area centre is " + distance + " feet away, beyond the range of "
                    + ability.rangeFeet() + " feet");
        }
        boolean clear = caster.occupiedSquares().stream().anyMatch(square -> clearLine(square, center));
        if (!clear) throw rule("A wall blocks line of effect to the area centre");
    }

    /** Combattenti la cui sagoma tocca la sfera di raggio [radiusFeet] centrata su [center]. */
    private List<String> combatantsInArea(GridPosition center, int radiusFeet) {
        BattleMap map = state.battleMap;
        int feetPerSquare = map.grid().feetPerSquare();
        double radiusSquares = (double) radiusFeet / feetPerSquare;
        double centerX = center.column() + 0.5;
        double centerY = center.row() + 0.5;
        List<String> caught = new ArrayList<>();
        for (TokenPlacement placement : map.orderedPlacements()) {
            MutableCombatant target = state.combatants.get(placement.combatantId());
            if (target == null || isDead(target)) continue;
            boolean within = placement.occupiedSquares().stream().anyMatch(square -> {
                double dx = (square.column() + 0.5) - centerX;
                double dy = (square.row() + 0.5) - centerY;
                return Math.sqrt(dx * dx + dy * dy) <= radiusSquares + 1e-9;
            });
            if (within && hasLineOfEffect(center, placement)) caught.add(placement.combatantId());
        }
        return caught;
    }

    public synchronized DamageResult applyDamage(
            String sourceCombatantId, String targetCombatantId, List<DamageComponent> components, boolean critical) {
        return applyDamage(sourceCombatantId, targetCombatantId, components, critical, D20RollInput.digital());
    }

    /** concentrationRoll is ignored when the target is not concentrating or adjusted damage is zero. */
    public synchronized DamageResult applyDamage(
            String sourceCombatantId,
            String targetCombatantId,
            List<DamageComponent> components,
            boolean critical,
            D20RollInput concentrationRoll) {
        requireStatus(CombatStatus.ACTIVE);
        combatant(targetCombatantId);
        Objects.requireNonNull(components, "components");
        List<DamageComponent> copied = List.copyOf(components);
        if (copied.isEmpty()) throw rule("Damage needs at least one component");
        Objects.requireNonNull(concentrationRoll, "concentrationRoll");
        beginCommand();
        try {
            return applyDamageInternal(sourceCombatantId, targetCombatantId, copied, critical, concentrationRoll);
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    public synchronized int heal(String targetCombatantId, int amount) {
        requireStatus(CombatStatus.ACTIVE);
        combatant(targetCombatantId);
        if (amount <= 0) throw rule("Healing must be positive");
        beginCommand();
        return healInternal("", targetCombatantId, amount, Map.of());
    }

    /**
     * Uses an automated healing ability as one atomic, undoable command.
     *
     * <p>Target legality, range, turn cost and limited resources are validated
     * before the command begins. Any failure after that point restores combat,
     * audit and deterministic RNG state.</p>
     */
    public synchronized int useHealingAbility(String healerId, String targetId, String abilityId) {
        return useHealingAbilityInternal(healerId, targetId, abilityId, null);
    }

    /**
     * Uses an upcastable healing spell with the exact standard or Pact slot
     * selected by the caller. The slot and its scaled formula are validated by
     * the engine before any command state is mutated.
     */
    public synchronized int useHealingAbility(
            String healerId,
            String targetId,
            String abilityId,
            String selectedResourceId) {
        if (selectedResourceId == null || selectedResourceId.isBlank()) {
            throw rule("A selected healing resource id cannot be blank");
        }
        return useHealingAbilityInternal(healerId, targetId, abilityId, selectedResourceId);
    }

    private int useHealingAbilityInternal(
            String healerId,
            String targetId,
            String abilityId,
            String selectedResourceId) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant healer = combatant(healerId);
        MutableCombatant target = combatant(targetId);
        AbilityDefinition ability = ability(healer, abilityId);
        HealingDefinition healing = ability.healing();
        if (healing == null) {
            throw rule("Ability does not heal: " + ability.id());
        }
        if (healer.currentHitPoints == 0
                || healer.deathSaves.dead()
                || healer.exhaustionLevel >= state.rulesetRuntime.maximumExhaustion()
                || healer.conditions.stream().anyMatch(condition -> incapacitates(condition.type()))) {
            throw rule("An incapacitated combatant cannot use a healing ability");
        }
        boolean self = healerId.equals(targetId);
        boolean sameFaction = state.partyCombatantIds.contains(healerId)
                == state.partyCombatantIds.contains(targetId);
        if (!sameFaction) {
            throw rule("A healing ability can target only the healer's faction");
        }
        if (target.deathSaves.dead()
                || target.exhaustionLevel >= state.rulesetRuntime.maximumExhaustion()) {
            throw rule("A dead combatant cannot be healed");
        }
        if (healing.target() == HealingTarget.SELF && !self) {
            throw rule("This healing ability can target only its user");
        }
        if (healing.target() == HealingTarget.ALLY && self) {
            throw rule("This healing ability requires a different ally");
        }
        if (healer.snapshot.strengthDexterityD20Disadvantage() && ability.spellOrCantrip()) {
            throw rule("The combatant cannot cast spells while wearing armor without training");
        }

        String resourceId = selectedResourceId == null ? ability.resourceId() : selectedResourceId;
        int slotLevel = 0;
        HealingDefinition resolvedHealing = healing;
        if (healing.scalesWithSlot()) {
            if (!ability.spellOrCantrip() || ability.resourceCost() != 1) {
                throw rule("An upcast healing spell must consume exactly one spell slot");
            }
            SpellSlotResourceId baseSlot = SpellSlotResourceId.parse(ability.resourceId())
                    .orElseThrow(() -> rule("The healing ability has no valid base spell slot"));
            if (baseSlot.level() != healing.slotScaling().baseSlotLevel()) {
                throw rule("The healing ability base slot does not match its scaling");
            }
            SpellSlotResourceId selectedSlot = SpellSlotResourceId.parse(resourceId)
                    .orElseThrow(() -> rule("The selected resource is not a spell slot"));
            if (selectedSlot.level() < baseSlot.level()) {
                throw rule("The selected spell slot is below the ability's base level");
            }
            slotLevel = selectedSlot.level();
            resolvedHealing = healing.resolveAtSlotLevel(slotLevel);
        } else if (selectedResourceId != null && !selectedResourceId.equals(ability.resourceId())) {
            throw rule("This healing ability cannot use a different resource");
        }

        validateRange(healerId, targetId, ability);
        validateActivationCost(healerId, ability.activationCost(), ability.spellOrCantrip());
        validateAbilityResource(healerId, healer, ability, resourceId);

        beginCommand();
        try {
            consumeActivationCost(healerId, ability.activationCost(), ability.spellOrCantrip());
            consumeAbilityResource(healerId, healer, ability, resourceId);

            int requested;
            Map<String, String> healingDetails;
            if (resolvedHealing.usesDice()) {
                DiceRollResult roll = dice.roll(resolvedHealing.dice(), false);
                requested = Math.max(0, roll.total());
                healingDetails = details(
                        "abilityId", ability.id(),
                        "abilityName", ability.name(),
                        "formula", resolvedHealing.dice().notation(),
                        "dice", roll.dice(),
                        "modifier", roll.modifier(),
                        "resourceId", resourceId,
                        "slotLevel", slotLevel,
                        "source", "digital");
            } else {
                requested = resolvedHealing.fixedAmount();
                healingDetails = details(
                        "abilityId", ability.id(),
                        "abilityName", ability.name(),
                        "formula", resolvedHealing.fixedAmount(),
                        "resourceId", resourceId,
                        "source", "fixed");
            }
            int restored = healInternal(healerId, targetId, requested, healingDetails);
            append(EventType.ABILITY_ACTIVATED, healerId, targetId, details(
                    "abilityId", ability.id(),
                    "abilityName", ability.name(),
                    "resourceId", resourceId,
                    "slotLevel", slotLevel,
                    "requested", requested,
                    "restored", restored));
            return restored;
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    private int healInternal(
            String sourceCombatantId,
            String targetCombatantId,
            int amount,
            Map<String, String> extraDetails) {
        MutableCombatant target = combatant(targetCombatantId);
        int before = target.currentHitPoints;
        int room = target.snapshot.maxHitPoints() - before;
        target.currentHitPoints = before + Math.min(room, amount);
        int restored = target.currentHitPoints - before;
        append(EventType.HEALED, sourceCombatantId, targetCombatantId, merge(
                extraDetails,
                details("requested", amount, "restored", restored, "hitPointsAfter", target.currentHitPoints)));
        // Recuperare punti ferita azzera successi e fallimenti contro morte e
        // annulla lo stato Stable: la creatura torna cosciente.
        if (restored > 0 && before == 0) {
            target.deathSaves = DeathSaveState.none();
        }
        return restored;
    }

    /**
     * Correzione manuale dei punti ferita attuali, pensata per la modalità
     * Modifica del tavolo.
     *
     * <p>Portare una creatura a 0 PF con questa correzione la dichiara morta
     * esplicitamente: non la lascia nel normale stato di tiri salvezza contro
     * morte. Qualunque valore positivo azzera invece lo stato dei tiri contro
     * morte; una morte dovuta a Exhaustion resta invariata.</p>
     */
    public synchronized void setCurrentHitPoints(String combatantId, int hitPoints) {
        MutableCombatant target = combatant(combatantId);
        if (hitPoints < 0 || hitPoints > target.snapshot.maxHitPoints()) {
            throw rule("Current hit points must be between 0 and " + target.snapshot.maxHitPoints());
        }

        beginCommand();
        int before = target.currentHitPoints;
        int temporaryBefore = target.temporaryHitPoints;
        boolean wasDead = target.deathSaves.dead()
                || target.exhaustionLevel >= state.rulesetRuntime.maximumExhaustion();
        target.currentHitPoints = hitPoints;
        if (hitPoints == 0) {
            // L'azione e' una dichiarazione del tavolo, non danno: 0 significa
            // morto come richiesto dalla modalità Modifica.
            target.temporaryHitPoints = 0;
            target.deathSaves = new DeathSaveState(0, DeathSaveState.REQUIRED, false);
            if (target.concentration != null) {
                endConcentrationInternal(combatantId, "manual current hit points edit");
            }
        } else {
            target.deathSaves = DeathSaveState.none();
        }
        append(EventType.CURRENT_HIT_POINTS_SET, "", combatantId, details(
                "before", before,
                "after", hitPoints,
                "temporaryBefore", temporaryBefore,
                "temporaryAfter", target.temporaryHitPoints,
                "zeroMeansDead", hitPoints == 0));
        if (hitPoints == 0 && !wasDead) {
            append(EventType.DIED, "", combatantId, details("cause", "manual current hit points edit"));
        }
    }

    /**
     * Corregge massimo e quantita' disponibile di una risorsa limitata.
     *
     * <p>La risorsa deve gia' appartenere alla fotografia dell'incontro: questo
     * comando corregge il contatore, non inventa nuove capacita'. La modifica e'
     * registrata e annullabile come le altre correzioni decise al tavolo.</p>
     */
    public synchronized void setCombatResource(
            String combatantId,
            String resourceId,
            int maximum,
            int remaining) {
        MutableCombatant target = combatant(combatantId);
        requireText(resourceId, "resourceId");
        CombatResourceState previous = target.resources.get(resourceId);
        if (previous == null) throw rule("Unknown combat resource: " + resourceId);
        if (maximum < 0 || remaining < 0 || remaining > maximum) {
            throw rule("Available resource uses must be between 0 and the maximum");
        }
        if (previous.maximum() == maximum && previous.remaining() == remaining) return;

        CombatResourceState updated = new CombatResourceState(
                previous.id(), previous.name(), maximum, maximum - remaining);
        beginCommand();
        target.resources.put(resourceId, updated);
        append(EventType.COMBAT_RESOURCE_SET, combatantId, "", details(
                "resourceId", resourceId,
                "resourceName", previous.name(),
                "previousRemaining", previous.remaining(),
                "remaining", remaining,
                "previousMaximum", previous.maximum(),
                "maximum", maximum));
    }

    /** Corregge una risorsa 0/1 del turno senza toccare le altre disponibilita'. */
    public synchronized void setTurnResourceAvailable(
            String combatantId,
            TurnResource resource,
            boolean available) {
        combatant(combatantId);
        Objects.requireNonNull(resource, "resource");
        TurnBudget previous = budget(combatantId);
        if (previous.available(resource) == available) return;

        beginCommand();
        state.turnBudgets.put(combatantId, previous.withAvailability(resource, available));
        append(EventType.TURN_RESOURCE_SET, combatantId, "", details(
                "resource", resource,
                "before", previous.available(resource) ? 1 : 0,
                "after", available ? 1 : 0));
    }

    /**
     * Tiro salvezza contro morte all'inizio del turno di una creatura a 0 punti ferita.
     *
     * <p>10 o piu' e' un successo, meno di 10 un fallimento. Il 20 naturale fa
     * recuperare 1 punto ferita e riporta la creatura cosciente; l'1 naturale vale
     * due fallimenti. Tre successi rendono Stable, tre fallimenti causano la morte.</p>
     */
    public synchronized DeathSaveState rollDeathSave(String combatantId, D20RollInput input) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(combatantId);
        Objects.requireNonNull(input, "input");
        if (target.currentHitPoints > 0) {
            throw rule("Only a creature at zero hit points rolls death saves");
        }
        if (target.deathSaves.dead()) {
            throw rule("A dead creature does not roll death saves");
        }
        if (target.deathSaves.stable()) {
            throw rule("A stable creature does not roll death saves");
        }

        beginCommand();
        try {
            // Il tiro contro morte non ha modificatori propri, ma resta un D20 Test:
            // la penalita' di Exhaustion si applica anche qui.
            D20RollResult roll = rollD20For(target, input, 0);
            int natural = roll.naturalRoll();

            if (natural == 20) {
                target.deathSaves = DeathSaveState.none();
                target.currentHitPoints = 1;
                append(EventType.DEATH_SAVE_ROLLED, combatantId, "", merge(rollDetails(roll), details(
                        "outcome", "natural20", "hitPointsAfter", target.currentHitPoints)));
                return target.deathSaves;
            }

            if (natural == 1) {
                target.deathSaves = target.deathSaves.withFailures(2);
            } else if (roll.total() >= 10) {
                target.deathSaves = target.deathSaves.withSuccess();
            } else {
                target.deathSaves = target.deathSaves.withFailures(1);
            }

            append(EventType.DEATH_SAVE_ROLLED, combatantId, "", merge(rollDetails(roll), details(
                    "successes", target.deathSaves.successes(),
                    "failures", target.deathSaves.failures())));

            if (target.deathSaves.stable()) {
                append(EventType.STABILIZED, combatantId, "", details("cause", "three successes"));
            } else if (target.deathSaves.dead()) {
                append(EventType.DIED, combatantId, "", details("cause", "three failures"));
            }
            return target.deathSaves;
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /**
     * Effettua una prova di caratteristica generica senza consumare automaticamente
     * risorse del turno.
     *
     * <p>Il modificatore e' quello dichiarato dal tavolo per la prova specifica.
     * Il motore aggiunge la penalita' di Sfinimento e, soltanto per Forza o
     * Destrezza, impone lo Svantaggio dell'armatura indossata senza competenza.
     * Vantaggio e Svantaggio imposti si annullano come per gli altri D20 Test.</p>
     */
    public synchronized D20RollResult rollAbilityCheck(
            String combatantId,
            SaveAbility ability,
            int modifier,
            D20RollInput input) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant roller = combatant(combatantId);
        Objects.requireNonNull(ability, "ability");
        Objects.requireNonNull(input, "input");

        boolean armorDisadvantage =
                roller.snapshot.strengthDexterityD20Disadvantage()
                        && (ability == SaveAbility.STRENGTH || ability == SaveAbility.DEXTERITY);
        D20RollInput effectiveInput = imposeDisadvantage(input, armorDisadvantage);

        beginCommand();
        try {
            D20RollResult roll = rollD20For(roller, effectiveInput, modifier);
            append(EventType.ABILITY_CHECK_ROLLED, combatantId, "", merge(
                    rollDetails(roll),
                    details("ability", ability, "requestedModifier", modifier)));
            return roll;
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /**
     * Rende Stable una creatura morente, per esempio con una prova di Medicina CD 10.
     *
     * <p>Resta priva di sensi ma smette di tirare contro morte. Il motore non
     * decide come la stabilizzazione sia stata ottenuta: registra la causa fornita.</p>
     */
    public synchronized void stabilize(String combatantId, String cause) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(combatantId);
        if (target.currentHitPoints > 0) {
            throw rule("Only a creature at zero hit points can be stabilized");
        }
        if (target.deathSaves.dead()) {
            throw rule("A dead creature cannot be stabilized");
        }
        beginCommand();
        target.deathSaves = DeathSaveState.stabilized();
        append(EventType.STABILIZED, combatantId, "", details(
                "cause", cause == null || cause.isBlank() ? "manual" : cause));
    }

    /**
     * Mette fuori combattimento senza uccidere.
     *
     * <p>Un attacco in mischia puo' lasciare il bersaglio a 1 punto ferita e privo
     * di sensi invece di portarlo a 0: la creatura non entra nei tiri contro morte.</p>
     */
    public synchronized void knockOut(String combatantId) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(combatantId);
        beginCommand();
        target.currentHitPoints = Math.max(1, Math.min(1, target.snapshot.maxHitPoints()));
        target.deathSaves = DeathSaveState.none();
        append(EventType.KNOCKED_OUT, "", combatantId, details("hitPointsAfter", target.currentHitPoints));
    }

    /**
     * Imposta il livello di Exhaustion.
     *
     * <p>Exhaustion e' l'eccezione cumulativa fra le condizioni: da 1 a 6, con −2 a
     * tutti i D20 Test e −5 piedi di Speed per livello. Al sesto livello la creatura
     * muore.</p>
     */
    public synchronized void setExhaustion(String combatantId, int level) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(combatantId);
        if (level < 0 || level > state.rulesetRuntime.maximumExhaustion()) {
            throw rule("Exhaustion must be between 0 and " + state.rulesetRuntime.maximumExhaustion());
        }
        beginCommand();
        int before = target.exhaustionLevel;
        target.exhaustionLevel = level;
        state.turnBudgets.computeIfPresent(
                combatantId,
                (ignored, budget) -> budget.withMovementAllowance(effectiveSpeed(target)));
        append(EventType.EXHAUSTION_CHANGED, combatantId, "", details(
                "before", before,
                "after", level,
                "d20Penalty", -state.rulesetRuntime.exhaustionD20PenaltyPerLevel() * level,
                "speedPenaltyFeet", -state.rulesetRuntime.exhaustionSpeedPenaltyFeetPerLevel() * level));
        if (level >= state.rulesetRuntime.maximumExhaustion()) {
            append(EventType.DIED, combatantId, "", details("cause", "exhaustion"));
        }
    }

    /** Aggiunge livelli di Exhaustion, senza superare il massimo. */
    public synchronized void addExhaustion(String combatantId, int levels) {
        MutableCombatant target = combatant(combatantId);
        setExhaustion(combatantId,
                Math.min(state.rulesetRuntime.maximumExhaustion(), target.exhaustionLevel + levels));
    }

    /**
     * Modifica la fotografia di un combattente durante lo scontro.
     *
     * <p>Il documento vuole che un combattimento usi una fotografia degli attori e
     * che modificare la scheda originale non alteri retroattivamente uno scontro
     * gia' giocato. Questo comando va nella direzione opposta e consentita: e' una
     * correzione dichiarata al tavolo, quindi resta un comando esplicito, registrato
     * nell'audit e annullabile come ogni altro, non una mutazione silenziosa.</p>
     *
     * <p>I punti ferita correnti vengono riportati entro il nuovo massimo.</p>
     *
     * <p>L'evento registra ogni statistica sia prima sia dopo, e la revisione della
     * definizione da cui la fotografia proviene. Il registro resta cosi' sufficiente
     * da solo a ricostruire la correzione: una rilettura della cronologia sa quale
     * fotografia era in uso in ogni punto della partita, invece di vedere soltanto
     * quella finale.</p>
     */
    public synchronized void editCombatant(String combatantId, CombatantSnapshot updated) {
        MutableCombatant existing = combatant(combatantId);
        Objects.requireNonNull(updated, "updated");
        CombatantSnapshot previous = existing.snapshot;
        if (!previous.instanceId().equals(updated.instanceId())) {
            throw rule("A combatant edit cannot change its instance id");
        }
        if (!previous.definitionId().equals(updated.definitionId())) {
            throw rule("A combatant edit cannot change its definition id");
        }
        if (updated.maxHitPoints() < 1) {
            throw rule("Maximum hit points must be positive");
        }

        beginCommand();
        existing.snapshot = updated;
        existing.currentHitPoints = Math.min(existing.currentHitPoints, updated.maxHitPoints());
        append(EventType.COMBATANT_EDITED, combatantId, "", details(
                "previousVersion", previous.definitionVersion(),
                "version", updated.definitionVersion(),
                "previousName", previous.name(),
                "name", updated.name(),
                "previousArmorClass", previous.armorClass(),
                "armorClass", updated.armorClass(),
                "previousMaxHitPoints", previous.maxHitPoints(),
                "maxHitPoints", updated.maxHitPoints(),
                "previousSpeedFeet", previous.speedFeet(),
                "speedFeet", updated.speedFeet(),
                "previousInitiativeModifier", previous.initiativeModifier(),
                "initiativeModifier", updated.initiativeModifier(),
                "previousInitiativeScore", previous.initiativeScore(),
                "initiativeScore", updated.initiativeScore(),
                "previousConstitutionSaveBonus", previous.constitutionSaveBonus(),
                "constitutionSaveBonus", updated.constitutionSaveBonus(),
                "hitPointsAfter", existing.currentHitPoints));
    }

    /**
     * Riscrive le sole etichette di un combattente e delle sue capacita'.
     *
     * <p><b>Non e' un comando.</b> Non apre una revisione, non scrive nell'audit
     * e non entra nell'annullamento, e la scelta e' deliberata: cambiare lingua
     * non e' una mossa del tavolo. Registrarla come tale riempirebbe il registro
     * di {@code COMBATANT_EDITED} che nessuno ha compiuto e, peggio, metterebbe
     * fra le cose annullabili un atto che non appartiene alla partita.</p>
     *
     * <p>Restano fuori portata statistiche, risorse, posizione, iniziativa e
     * {@code definitionVersion}: passano solo i nomi. Gli eventi gia' scritti
     * puntano agli identificativi e non alle etichette, quindi il registro si
     * rilegge nella lingua nuova senza che una riga di storia cambi significato
     * e senza che una revisione debba essere inventata per giustificarlo.</p>
     *
     * @return vero se qualcosa e' davvero cambiato, cosi' chi chiama sa se vale
     *         la pena riscrivere il salvataggio.
     */
    public synchronized boolean relabelCombatant(
            String combatantId,
            String name,
            Map<String, String> abilityNames,
            Map<String, String> abilityRulesTexts) {
        MutableCombatant existing = combatant(combatantId);
        CombatantSnapshot relabelled =
                existing.snapshot.relabelled(name, abilityNames, abilityRulesTexts);
        if (relabelled == existing.snapshot) return false;
        existing.snapshot = relabelled;
        return true;
    }

    /**
     * Attiva una forma alternativa consumando azione e risorsa nello stesso
     * comando annullabile. La definizione di catalogo del personaggio non cambia:
     * la nuova fotografia vale soltanto per questo incontro.
     */
    public synchronized void transformCombatant(
            String combatantId,
            String abilityId,
            CombatantSnapshot transformed,
            int temporaryHitPoints) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant existing = combatant(combatantId);
        AbilityDefinition ability = ability(existing, abilityId);
        Objects.requireNonNull(transformed, "transformed");
        if (!existing.snapshot.instanceId().equals(transformed.instanceId())
                || !existing.snapshot.definitionId().equals(transformed.definitionId())) {
            throw rule("A transformation must preserve combatant and definition ids");
        }
        if (ability.passive()) throw rule("A passive trait cannot transform a combatant");
        if (temporaryHitPoints < 0) throw rule("Temporary hit points cannot be negative");
        validateActivationCost(combatantId, ability.activationCost(), ability.spellOrCantrip());
        validateAbilityResource(combatantId, existing, ability);

        beginCommand();
        try {
            consumeActivationCost(combatantId, ability.activationCost(), ability.spellOrCantrip());
            consumeAbilityResource(combatantId, existing, ability);
            String previousName = existing.snapshot.name();
            existing.snapshot = transformed;
            existing.currentHitPoints = Math.min(existing.currentHitPoints, transformed.maxHitPoints());
            existing.temporaryHitPoints = Math.max(existing.temporaryHitPoints, temporaryHitPoints);
            append(EventType.COMBATANT_TRANSFORMED, combatantId, "", details(
                    "abilityId", abilityId,
                    "previousName", previousName,
                    "name", transformed.name(),
                    "temporaryHitPoints", existing.temporaryHitPoints));
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    // --- mappa tattica -------------------------------------------------------------

    /**
     * Configura o riconfigura la griglia.
     *
     * <p>Restringere la mappa scarta i segnaposti rimasti fuori dal nuovo bordo:
     * andranno riposizionati esplicitamente, cosi' nessun combattente resta a
     * coordinate impossibili.</p>
     */
    public synchronized void configureMap(MapGrid grid) {
        Objects.requireNonNull(grid, "grid");
        beginCommand();
        int placedBefore = state.battleMap.placements().size();
        state.battleMap = state.battleMap.withGrid(grid);
        append(EventType.MAP_CONFIGURED, "", "", details(
                "columns", grid.columns(),
                "rows", grid.rows(),
                "feetPerSquare", grid.feetPerSquare(),
                "droppedPlacements", placedBefore - state.battleMap.placements().size()));
    }

    /**
     * Sincronizza i muri posseduti dal documento Board.
     *
     * <p>Non e' un comando di combattimento e non crea eventi o passi Undo: il
     * relativo storico appartiene alla Board. Tutte le successive verifiche del
     * motore, compresi i turni CPU, usano immediatamente questa fotografia.</p>
     */
    public synchronized void setBlockedCells(Collection<GridPosition> cells) {
        Objects.requireNonNull(cells, "cells");
        blockedCells = Set.copyOf(cells);
    }

    public synchronized Set<GridPosition> blockedCells() {
        return blockedCells;
    }

    // --- attivazione ---------------------------------------------------------------

    /**
     * Chi e' nell'incontro ma non si e' ancora accorto del gruppo.
     *
     * <p>Non e' una condizione del regolamento: e' lo stato di una creatura che
     * sta ancora facendo la guardia. La CPU non le fa giocare il turno; tutto il
     * resto del motore le tratta come qualunque altro combattente, perche' una
     * creatura distratta resta colpibile esattamente come le altre.</p>
     */
    public synchronized Set<String> dormantCombatantIds() {
        return Set.copyOf(state.dormantCombatantIds);
    }

    public synchronized boolean dormant(String combatantId) {
        return state.dormantCombatantIds.contains(combatantId);
    }

    /**
     * Dichiara chi e' inattivo, sostituendo l'insieme precedente.
     *
     * <p>Come i muri della Board, non e' un comando: non apre una revisione e non
     * scrive nell'audit. Chi la chiama sta dicendo al motore com'e' il mondo
     * adesso — a inizio combattimento, quando entra un nuovo nemico, o perche' il
     * master ha deciso a mano — non sta giocando una mossa. Gli identificatori
     * sconosciuti vengono ignorati: e' una fotografia, non una regola violata.</p>
     */
    public synchronized void setDormantCombatants(Collection<String> combatantIds) {
        Objects.requireNonNull(combatantIds, "combatantIds");
        LinkedHashSet<String> next = new LinkedHashSet<>();
        for (String id : combatantIds) {
            if (id != null && state.combatants.containsKey(id)) next.add(id);
        }
        state.dormantCombatantIds.clear();
        state.dormantCombatantIds.addAll(next);
    }

    /** Rende inattiva una sola creatura; falso se non esiste o lo era gia'. */
    public synchronized boolean markDormant(String combatantId) {
        if (combatantId == null || !state.combatants.containsKey(combatantId)) return false;
        return state.dormantCombatantIds.add(combatantId);
    }

    /**
     * Sveglia le creature indicate, e con loro chi e' abbastanza vicino da sentirle.
     *
     * @return chi si e' davvero svegliato, nell'ordine in cui e' accaduto
     */
    public synchronized List<String> awaken(Collection<String> combatantIds) {
        Objects.requireNonNull(combatantIds, "combatantIds");
        List<String> woken = new ArrayList<>();
        for (String id : combatantIds) awakenWithAlarm(id, woken);
        return List.copyOf(woken);
    }

    /** Raggio dell'allarme in piedi; zero lo spegne e ognuno si sveglia per conto suo. */
    public synchronized void setAlarmRadiusFeet(int feet) {
        if (feet < 0) throw new IllegalArgumentException("The alarm radius cannot be negative");
        state.alarmRadiusFeet = feet;
    }

    public synchronized int alarmRadiusFeet() {
        return state.alarmRadiusFeet;
    }

    /**
     * Sveglia una creatura e da' l'allarme ai suoi.
     *
     * <p>L'allarme non chiede la linea di vista: un grido gira l'angolo. Non e'
     * pero' transitivo — chi lo sente si sveglia ma non lo rilancia — altrimenti
     * il primo colpo sveglierebbe il sotterraneo intero passando di creatura in
     * creatura.</p>
     */
    private void awakenWithAlarm(String combatantId, Collection<String> woken) {
        if (combatantId == null || !state.dormantCombatantIds.remove(combatantId)) return;
        woken.add(combatantId);
        if (state.alarmRadiusFeet <= 0 || state.dormantCombatantIds.isEmpty()) return;
        boolean party = state.partyCombatantIds.contains(combatantId);
        for (String other : List.copyOf(state.dormantCombatantIds)) {
            if (state.partyCombatantIds.contains(other) != party) continue;
            Optional<Integer> distance = state.battleMap.distanceFeet(combatantId, other);
            if (distance.isPresent() && distance.get() <= state.alarmRadiusFeet) {
                state.dormantCombatantIds.remove(other);
                woken.add(other);
            }
        }
    }

    /**
     * Chi viene colpito, mancato di un soffio o preso di mira da una capacita' si
     * accorge del gruppo: e' la parte del risveglio che non dipende dalla vista, e
     * quindi appartiene al motore e non a chi disegna la mappa.
     */
    private void noticed(String combatantId) {
        if (state.dormantCombatantIds.isEmpty()) return;
        awakenWithAlarm(combatantId, new ArrayList<>());
    }

    /** Immagine di sfondo, indicata per nome nell'archivio locale delle immagini. */
    public synchronized void setMapBackground(String imageName) {
        beginCommand();
        state.battleMap = state.battleMap.withBackground(imageName == null ? "" : imageName);
        append(EventType.MAP_BACKGROUND_SET, "", "", details("image", state.battleMap.backgroundImage()));
    }

    /**
     * Collocazione dello sfondo sulla griglia: dove sta e quanto e' grande.
     *
     * <p>Le misure sono in caselle. Un solo evento per gesto completo — non uno per
     * ogni scatto del trascinamento — cosi' spostare o stirare l'immagine resta un
     * passo solo da annullare, come muovere un segnaposto.</p>
     */
    public synchronized void setMapBackgroundTransform(
            double offsetX, double offsetY, double width, double height) {
        beginCommand();
        MapBackground transform = new MapBackground(offsetX, offsetY, width, height);
        state.battleMap = state.battleMap.withBackgroundTransform(transform);
        append(EventType.MAP_BACKGROUND_SET, "", "", details(
                "offsetX", transform.offsetX(),
                "offsetY", transform.offsetY(),
                "width", transform.width(),
                "height", transform.height()));
    }

    /**
     * Colloca un combattente sulla griglia.
     *
     * <p>{@code squaresPerSide} e' l'ingombro: una creatura Grande ne occupa due,
     * una Enorme tre. Posizionare non consuma movimento: e' preparazione, non
     * spostamento.</p>
     */
    public synchronized void placeCombatant(String combatantId, GridPosition position, int squaresPerSide) {
        combatant(combatantId);
        Objects.requireNonNull(position, "position");
        requireConfiguredMap();
        TokenPlacement placement = new TokenPlacement(combatantId, position, squaresPerSide);
        if (!state.battleMap.fitsInsideGrid(placement)) {
            throw rule("The token does not fit inside the grid");
        }
        if (!state.battleMap.isFree(placement)) {
            throw rule("That space is already occupied");
        }
        if (touchesWall(placement)) {
            throw rule("That space is blocked by a wall");
        }
        beginCommand();
        state.battleMap = state.battleMap.withPlacement(placement);
        append(EventType.COMBATANT_PLACED, combatantId, "", details(
                "position", position.toString(), "squaresPerSide", squaresPerSide));
    }

    /**
     * Sposta un combattente consumando il suo movimento.
     *
     * <p>La distanza percorsa si conta dall'origine alla destinazione con la metrica
     * della griglia — una diagonale vale una casella — e viene sottratta dal budget
     * del turno, che e' la stessa risorsa spesa da {@code spendMovement}. Restituisce
     * i piedi effettivamente spesi.</p>
     */
    public synchronized int moveCombatant(String combatantId, GridPosition destination) {
        requireStatus(CombatStatus.ACTIVE);
        requireCurrentCombatant(combatantId);
        Objects.requireNonNull(destination, "destination");
        requireConfiguredMap();

        TokenPlacement current = state.battleMap.placementOf(combatantId)
                .orElseThrow(() -> rule("The combatant is not on the map"));
        TokenPlacement moved = current.movedTo(destination);
        if (!state.battleMap.fitsInsideGrid(moved)) {
            throw rule("The destination is outside the grid");
        }
        if (!state.battleMap.isFree(moved)) {
            throw rule("The destination is already occupied");
        }
        if (touchesWall(moved)) {
            throw rule("The destination is blocked by a wall");
        }

        int squares = shortestWalkableDistance(current, destination);
        if (squares < 0) {
            throw rule("A wall blocks every path to the destination");
        }
        int feet = state.battleMap.grid().feetFor(squares);
        TurnBudget budget = budget(combatantId);
        if (feet > budget.movementRemainingFeet()) {
            throw rule("Movement exceeds the remaining budget");
        }

        beginCommand();
        state.battleMap = state.battleMap.withPlacement(moved);
        state.turnBudgets.put(combatantId, budget.spendMovement(feet));
        append(EventType.COMBATANT_MOVED, combatantId, "", details(
                "from", current.origin().toString(),
                "to", destination.toString(),
                "squares", squares,
                "feet", feet,
                "remainingFeet", budget(combatantId).movementRemainingFeet()));
        return feet;
    }

    public synchronized void removeFromMap(String combatantId) {
        if (!state.battleMap.isPlaced(combatantId)) {
            throw rule("The combatant is not on the map");
        }
        beginCommand();
        state.battleMap = state.battleMap.without(combatantId);
        append(EventType.COMBATANT_REMOVED_FROM_MAP, combatantId, "", Map.of());
    }

    /**
     * Verifica la gittata quando la mappa lo consente.
     *
     * <p>Senza mappa, o quando anche uno solo dei due combattenti non e' posizionato,
     * non si puo' dichiarare una distanza: il documento vuole che in quel caso resti
     * il tavolo a stabilire se il bersaglio e' raggiungibile, e il motore non
     * inventa un vincolo che non sa verificare.</p>
     */
    private void validateRange(String attackerId, String targetId, AbilityDefinition ability) {
        if (!state.battleMap.configured()) return;
        state.battleMap.distanceFeet(attackerId, targetId).ifPresent(distance -> {
            if (distance > ability.rangeFeet()) {
                throw rule("Target is " + distance + " feet away, beyond the ability range of "
                        + ability.rangeFeet() + " feet");
            }
        });
        TokenPlacement attacker = state.battleMap.placementOf(attackerId).orElse(null);
        TokenPlacement target = state.battleMap.placementOf(targetId).orElse(null);
        if (attacker != null && target != null && !hasLineOfEffect(attacker, target)) {
            throw rule("A wall blocks line of effect to the target");
        }
    }

    private boolean touchesWall(TokenPlacement placement) {
        return placement.occupiedSquares().stream().anyMatch(blockedCells::contains);
    }

    /** Percorso minimo a otto direzioni, senza attraversare muri ne' tagliarne gli angoli. */
    private int shortestWalkableDistance(TokenPlacement current, GridPosition destination) {
        if (blockedCells.isEmpty()) return current.origin().squaresTo(destination);
        MapGrid grid = state.battleMap.grid();
        int columns = grid.columns();
        int rows = grid.rows();
        boolean[] visited = new boolean[Math.multiplyExact(columns, rows)];
        int[] distance = new int[visited.length];
        ArrayDeque<GridPosition> queue = new ArrayDeque<>();
        GridPosition start = current.origin();
        visited[start.row() * columns + start.column()] = true;
        queue.add(start);
        int[] deltas = {-1, 0, 1};
        while (!queue.isEmpty()) {
            GridPosition point = queue.removeFirst();
            int currentDistance = distance[point.row() * columns + point.column()];
            if (point.equals(destination)) return currentDistance;
            for (int rowDelta : deltas) for (int columnDelta : deltas) {
                if (columnDelta == 0 && rowDelta == 0) continue;
                int column = point.column() + columnDelta;
                int row = point.row() + rowDelta;
                if (column < 0 || row < 0 || column >= columns || row >= rows) continue;
                GridPosition next = new GridPosition(column, row);
                int index = row * columns + column;
                if (visited[index] || !walkableStep(current, point, next)) continue;
                visited[index] = true;
                distance[index] = currentDistance + 1;
                queue.addLast(next);
            }
        }
        return -1;
    }

    private boolean walkableOrigin(TokenPlacement source, GridPosition origin) {
        TokenPlacement candidate = source.movedTo(origin);
        return state.battleMap.fitsInsideGrid(candidate) && !touchesWall(candidate);
    }

    /**
     * Un singolo passo fra otto direzioni.
     *
     * <p>La destinazione deve essere percorribile, e in diagonale devono esserlo
     * anche le due caselle ortogonali: non si taglia l'angolo di un muro. E' la
     * regola sottile del movimento, quindi vive in un solo posto — la usano sia il
     * comando che spende il budget sia la query che disegna il raggio.</p>
     */
    private boolean walkableStep(TokenPlacement mover, GridPosition from, GridPosition to) {
        if (!walkableOrigin(mover, to)) return false;
        int columnDelta = to.column() - from.column();
        int rowDelta = to.row() - from.row();
        if (columnDelta == 0 || rowDelta == 0) return true;
        GridPosition horizontal = new GridPosition(to.column(), from.row());
        GridPosition vertical = new GridPosition(from.column(), to.row());
        return walkableOrigin(mover, horizontal) && walkableOrigin(mover, vertical);
    }

    /**
     * Caselle d'origine che il combattente puo' davvero raggiungere col movimento residuo.
     *
     * <p>Non e' un comando: non tocca lo stato, non genera eventi e non produce un
     * passo di Undo. Esiste perche' chi disegna il raggio sulla mappa non deve
     * reimplementare le regole del movimento — muri, angoli e ingombro — e finire
     * per illuminare caselle che {@link #moveCombatant} poi rifiuta.</p>
     *
     * <p>L'occupazione altrui non entra nel conto, esattamente come nel percorso di
     * {@link #moveCombatant}: attraversare qualcuno e' lecito, e la casella
     * d'arrivo resta verificata dal comando vero.</p>
     */
    public synchronized Set<GridPosition> reachableOrigins(String combatantId) {
        Objects.requireNonNull(combatantId, "combatantId");
        TokenPlacement current = state.battleMap.placementOf(combatantId).orElse(null);
        if (current == null) return Set.of();
        MapGrid grid = state.battleMap.grid();
        if (!grid.configured() || grid.feetPerSquare() <= 0) return Set.of();
        TurnBudget budget = state.turnBudgets.get(combatantId);
        if (budget == null) return Set.of();
        int maxSquares = budget.movementRemainingFeet() / grid.feetPerSquare();
        if (maxSquares <= 0) return Set.of();

        int columns = grid.columns();
        int rows = grid.rows();
        boolean[] visited = new boolean[Math.multiplyExact(columns, rows)];
        int[] distance = new int[visited.length];
        Set<GridPosition> reached = new LinkedHashSet<>();
        ArrayDeque<GridPosition> queue = new ArrayDeque<>();
        GridPosition start = current.origin();
        visited[start.row() * columns + start.column()] = true;
        queue.add(start);
        int[] deltas = {-1, 0, 1};
        while (!queue.isEmpty()) {
            GridPosition point = queue.removeFirst();
            int currentDistance = distance[point.row() * columns + point.column()];
            // Oltre il budget non si prosegue: la visita resta grande quanto il
            // raggio, non quanto la mappa.
            if (currentDistance >= maxSquares) continue;
            for (int rowDelta : deltas) for (int columnDelta : deltas) {
                if (columnDelta == 0 && rowDelta == 0) continue;
                int column = point.column() + columnDelta;
                int row = point.row() + rowDelta;
                if (column < 0 || row < 0 || column >= columns || row >= rows) continue;
                int index = row * columns + column;
                if (visited[index]) continue;
                GridPosition next = new GridPosition(column, row);
                if (!walkableStep(current, point, next)) continue;
                visited[index] = true;
                distance[index] = currentDistance + 1;
                reached.add(next);
                queue.addLast(next);
            }
        }
        return Set.copyOf(reached);
    }

    private boolean hasLineOfEffect(TokenPlacement source, TokenPlacement target) {
        if (blockedCells.isEmpty()) return true;
        for (GridPosition from : source.occupiedSquares()) {
            for (GridPosition to : target.occupiedSquares()) {
                if (clearLine(from, to)) return true;
            }
        }
        return false;
    }

    private boolean hasLineOfEffect(GridPosition source, TokenPlacement target) {
        if (blockedCells.isEmpty()) return true;
        for (GridPosition to : target.occupiedSquares()) {
            if (clearLine(source, to)) return true;
        }
        return false;
    }

    /** Bresenham conservativo: anche un angolo chiuso da un muro interrompe la linea. */
    private boolean clearLine(GridPosition source, GridPosition target) {
        return GridLineTraversal.clear(
                source.column(), source.row(), target.column(), target.row(),
                (column, row) -> blockedCells.contains(new GridPosition(column, row)));
    }

    private void requireConfiguredMap() {
        if (!state.battleMap.configured()) {
            throw rule("No map has been configured for this encounter");
        }
    }

    /** Temporary hit points never stack; the greater old/new value is retained. */
    public synchronized int grantTemporaryHitPoints(String targetCombatantId, int amount) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(targetCombatantId);
        if (amount < 0) throw rule("Temporary hit points cannot be negative");
        beginCommand();
        int before = target.temporaryHitPoints;
        target.temporaryHitPoints = Math.max(before, amount);
        append(EventType.TEMPORARY_HIT_POINTS_GRANTED, "", targetCombatantId, details(
                "offered", amount, "before", before, "retained", target.temporaryHitPoints));
        return target.temporaryHitPoints;
    }

    /** Returns false when the target is immune; the immunity decision is still audited. */
    public synchronized boolean applyCondition(String targetCombatantId, ConditionInstance condition) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(targetCombatantId);
        Objects.requireNonNull(condition, "condition");
        combatant(condition.sourceCombatantId());
        if (conditionIdExists(condition.id())) throw rule("Duplicate condition instance id: " + condition.id());
        if (condition.duration().expiry() == ConditionExpiry.CONCENTRATION) {
            MutableCombatant owner = combatant(condition.concentrationOwnerId());
            if (owner.concentration == null) {
                throw rule("The condition's concentration owner is not concentrating");
            }
        }
        beginCommand();
        noticed(targetCombatantId);
        if (target.snapshot.conditionImmunities().contains(condition.type())) {
            append(EventType.CONDITION_IMMUNE, condition.sourceCombatantId(), targetCombatantId,
                    details("conditionId", condition.id(), "condition", condition.type()));
            return false;
        }
        target.conditions.add(condition);
        append(EventType.CONDITION_APPLIED, condition.sourceCombatantId(), targetCombatantId, details(
                "conditionId", condition.id(),
                "condition", condition.type(),
                "sourceAbilityId", condition.sourceAbilityId(),
                "expiry", condition.duration().expiry(),
                "remaining", condition.duration().remainingOccurrences()));
        if (target.concentration != null && incapacitates(condition.type())) {
            endConcentrationInternal(targetCombatantId, "incapacitated by " + condition.type());
        }
        return true;
    }

    public synchronized ConditionInstance addCondition(
            String conditionId,
            String targetCombatantId,
            app.d6d.domain.combat.ConditionType type,
            String sourceCombatantId,
            String sourceAbilityId,
            ConditionDuration duration,
            String concentrationOwnerId,
            String note) {
        ConditionInstance condition = new ConditionInstance(conditionId, type, sourceCombatantId, sourceAbilityId,
                state.round, duration, concentrationOwnerId, note);
        applyCondition(targetCombatantId, condition);
        return condition;
    }

    public synchronized boolean removeCondition(String targetCombatantId, String conditionInstanceId) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant target = combatant(targetCombatantId);
        ConditionInstance condition = target.conditions.stream()
                .filter(candidate -> candidate.id().equals(conditionInstanceId)).findFirst().orElse(null);
        if (condition == null) return false;
        beginCommand();
        target.conditions.remove(condition);
        append(EventType.CONDITION_REMOVED, "", targetCombatantId,
                details("conditionId", condition.id(), "condition", condition.type()));
        return true;
    }

    /** Replaces any existing concentration and expires all effects tied to the previous one. */
    public synchronized void beginConcentration(String ownerCombatantId, String abilityId) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant owner = combatant(ownerCombatantId);
        ability(owner, abilityId);
        if (owner.currentHitPoints == 0 || owner.conditions.stream().anyMatch(condition -> incapacitates(condition.type()))) {
            throw rule("An incapacitated combatant cannot begin concentration");
        }
        beginCommand();
        if (owner.concentration != null) {
            endConcentrationInternal(ownerCombatantId, "replaced");
        }
        owner.concentration = new ConcentrationState(abilityId, state.round);
        append(EventType.CONCENTRATION_STARTED, ownerCombatantId, "",
                details("abilityId", abilityId));
    }

    public synchronized boolean endConcentration(String ownerCombatantId, String reason) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant owner = combatant(ownerCombatantId);
        if (owner.concentration == null) return false;
        beginCommand();
        endConcentrationInternal(ownerCombatantId, reason == null ? "manual" : reason);
        return true;
    }

    public synchronized void spendMovement(String combatantId, int feet) {
        requireStatus(CombatStatus.ACTIVE);
        requireCurrentCombatant(combatantId);
        TurnBudget budget = budget(combatantId);
        if (feet < 0 || feet > budget.movementRemainingFeet()) {
            throw rule("Movement exceeds the remaining budget");
        }
        beginCommand();
        state.turnBudgets.put(combatantId, budget.spendMovement(feet));
        append(EventType.MOVEMENT_SPENT, combatantId, "", details(
                "feet", feet, "remaining", state.turnBudgets.get(combatantId).movementRemainingFeet()));
    }

    public synchronized void spendAction(String combatantId, ActivationCost cost) {
        requireStatus(CombatStatus.ACTIVE);
        Objects.requireNonNull(cost, "cost");
        validateActivationCost(combatantId, cost, false);
        if (cost == ActivationCost.NONE) throw rule("NONE does not spend a turn resource");
        beginCommand();
        consumeActivationCost(combatantId, cost, false);
    }

    /** Applica una capacità automatica priva di bersaglio, consumandone costo e risorsa in un solo comando. */
    public synchronized void activateAbility(String combatantId, String abilityId) {
        requireStatus(CombatStatus.ACTIVE);
        MutableCombatant combatant = combatant(combatantId);
        AbilityDefinition ability = ability(combatant, abilityId);
        if (ability.passive()) {
            throw rule("A passive trait cannot be activated: " + ability.id());
        }
        if (ability.effect() == AbilityEffect.NONE) {
            throw rule("Ability has no automatic effect: " + ability.id());
        }
        if (ability.automationStatus() != AutomationStatus.AUTOMATED
                || ability.resolutionMethod() != ResolutionMethod.AUTOMATIC) {
            throw rule("Ability requires a different resolution: " + ability.id());
        }
        validateActivationCost(combatantId, ability.activationCost(), ability.spellOrCantrip());
        validateAbilityResource(combatantId, combatant, ability);
        if (ability.effect() == AbilityEffect.GRANT_NON_MAGIC_ACTION) {
            requireCurrentCombatant(combatantId);
            if (budget(combatantId).actionSurgeUsedThisTurn()) {
                throw rule("Action Surge can be used only once in the same turn");
            }
        }

        beginCommand();
        try {
            consumeActivationCost(combatantId, ability.activationCost(), ability.spellOrCantrip());
            consumeAbilityResource(combatantId, combatant, ability);
            switch (ability.effect()) {
                case GRANT_NON_MAGIC_ACTION -> {
                    state.turnBudgets.put(combatantId, budget(combatantId).grantNonMagicAction());
                    append(EventType.ACTION_GRANTED, combatantId, "", details(
                            "abilityId", ability.id(),
                            "abilityName", ability.name(),
                            "restriction", "NON_MAGIC"));
                }
                case NONE -> throw new IllegalStateException("Validated effect unexpectedly disappeared");
            }
            append(EventType.ABILITY_ACTIVATED, combatantId, "", details(
                    "abilityId", ability.id(), "abilityName", ability.name()));
        } catch (RuntimeException | Error failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    public synchronized void markSpellSlotSpent(String combatantId) {
        requireStatus(CombatStatus.ACTIVE);
        requireCurrentCombatant(combatantId);
        TurnBudget budget = budget(combatantId);
        if (budget.spellSlotSpentThisTurn()) throw rule("A spell slot was already spent this turn");
        beginCommand();
        state.turnBudgets.put(combatantId, budget.markSpellSlotSpent());
        append(EventType.SPELL_SLOT_SPENT, combatantId, "", Map.of());
    }

    public synchronized void endTurn() {
        requireStatus(CombatStatus.ACTIVE);
        beginCommand();
        try {
            endTurnInternal();
        } catch (RuntimeException failure) {
            rollbackFailedCommand();
            throw failure;
        }
    }

    /** Chiude il gruppo corrente; il chiamante ha gia' aperto il comando annullabile. */
    private void endTurnInternal() {
        List<String> ending = currentCombatantIds();
        List<String> endingGroup = currentTurnGroup();
        fireGenericEventInternal("TURN_END");
        for (String combatantId : ending) {
            RuleScope scope = RuleScope.actor(combatantId);
            if (state.ruleSession.findState(scope).isPresent()) {
                fireGenericEventInternal("TURN_END", scope);
            }
        }
        // Il turno finisce per tutto il gruppo: in parita' i combattenti hanno
        // giocato insieme e chiudono insieme. Anche il membro a 0 PF attraversa
        // comunque la soglia temporale di fine turno, pur senza ricevere azioni.
        for (String endingCombatant : endingGroup) {
            if (ending.contains(endingCombatant)) {
                append(EventType.TURN_ENDED, endingCombatant, "", details("round", state.round));
            }
            processConditionBoundary(endingCombatant, false);
        }
        // I gruppi composti soltanto da combattenti a 0 PF restano visibili
        // nell'iniziativa, ma non ricevono un turno. La scansione e' limitata a un
        // giro completo: anche uno stato eccezionale con tutti a 0 PF non puo'
        // innescare una ricorsione o un ciclo infinito.
        if (advanceToNextPlayableTurn()) {
            startTurnInternal();
        }
    }

    /**
     * Riordina i turni a scontro gia' avviato.
     *
     * <p>E' una correzione dichiarata del tavolo: chi sta agendo ora resta il
     * combattente corrente, cambia solo la sua posizione nella coda. Passa dal
     * registro ed e' annullabile come ogni altro comando.</p>
     */
    public synchronized void reorderTurns(List<String> orderedCombatantIds) {
        if (state.status != CombatStatus.ACTIVE && state.status != CombatStatus.PAUSED) {
            throw rule("Turns can only be reordered during active play");
        }
        Objects.requireNonNull(orderedCombatantIds, "orderedCombatantIds");
        List<String> order = List.copyOf(orderedCombatantIds);
        Set<String> expected = state.combatants.keySet();
        if (order.size() != expected.size() || new HashSet<>(order).size() != order.size()
                || !new HashSet<>(order).equals(expected)) {
            throw rule("Turn order must contain every combatant exactly once");
        }
        String anchor = currentTurnAnchor();
        beginCommand();
        state.initiativeOrder.clear();
        state.initiativeOrder.addAll(order);
        List<List<String>> groups = turnGroups();
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).contains(anchor)) {
                state.turnIndex = i;
                break;
            }
        }
        state.turnIndex = Math.max(0, Math.min(state.turnIndex, groups.size() - 1));
        append(EventType.INITIATIVE_ORDER_SET, "", "", details("order", String.join(",", order)));
    }

    /**
     * Sposta a mano il turno corrente su un combattente scelto.
     *
     * <p>Il gruppo scelto riceve un budget fresco cosi' puo' agire; gli effetti
     * d'inizio turno delle condizioni non vengono riattivati, perche' e' una
     * correzione del tavolo e non un turno giocato per intero.</p>
     */
    public synchronized void setCurrentTurn(String combatantId) {
        if (state.status != CombatStatus.ACTIVE && state.status != CombatStatus.PAUSED) {
            throw rule("The current turn can only be changed during active play");
        }
        MutableCombatant selected = combatant(combatantId);
        if (isDead(selected)) {
            throw rule("A dead combatant cannot take a turn");
        }
        if (selected.currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot take a turn");
        }
        List<List<String>> groups = turnGroups();
        int target = -1;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).contains(combatantId)) {
                target = i;
                break;
            }
        }
        if (target < 0) throw rule("Combatant is not in the current initiative order");
        if (target == state.turnIndex) {
            return;
        }
        beginCommand();
        state.turnIndex = target;
        resetSpellSlotBudgetsForNewTurn();
        for (String id : livingCombatants(groups.get(target))) {
            MutableCombatant occupant = combatant(id);
            int speed = effectiveSpeed(occupant);
            state.turnBudgets.put(id, TurnBudget.fresh(speed, occupant.snapshot.attacksPerAction()));
            append(EventType.TURN_STARTED, id, "", details("round", state.round));
        }
    }

    /**
     * Cambia a mano l'iniziativa di un combattente a scontro avviato e riordina la
     * coda di conseguenza.
     *
     * <p>La coda viene riordinata per punteggio decrescente (le parita' seguono
     * l'ordine di inserimento, come all'avvio). Chi sta agendo ora resta il
     * combattente corrente, anche se la sua iniziativa e' cambiata.</p>
     */
    public synchronized void overrideInitiative(String combatantId, int total) {
        if (state.status != CombatStatus.ACTIVE && state.status != CombatStatus.PAUSED) {
            throw rule("Initiative can only be overridden during active play");
        }
        combatant(combatantId);
        String anchor = currentTurnAnchor();
        beginCommand();
        state.initiativeScores.put(combatantId, total);
        rebuildInitiativeOrder();
        List<List<String>> groups = turnGroups();
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).contains(anchor)) {
                state.turnIndex = i;
                break;
            }
        }
        state.turnIndex = Math.max(0, Math.min(state.turnIndex, groups.size() - 1));
        append(EventType.INITIATIVE_SET, combatantId, "", details("total", total));
    }

    /**
     * Dichiara se i pareggi d'iniziativa vengono giocati insieme.
     *
     * <p>Il regolamento fa risolvere le parita' al DM: renderle simultanee resta
     * una scelta del tavolo, quindi e' una bandiera esplicita e registrata.</p>
     */
    public synchronized void setSimultaneousTies(boolean simultaneous) {
        requireSetupPhase();
        if (state.simultaneousTies == simultaneous) return;
        beginCommand();
        state.simultaneousTies = simultaneous;
        append(EventType.INITIATIVE_ORDER_SET, "", "", details(
                "simultaneousTies", simultaneous,
                "groups", turnGroups().size()));
    }

    /**
     * Restores the last pre-command game snapshot and exact RNG state. Previous audit events are retained and an
     * UNDO_PERFORMED event is appended, so the audit itself is never rewritten.
     */
    /**
     * Undoes every command applied after the given revision.
     *
     * <p>For callers that treat several commands as a single table operation - the
     * enemy CPU turn - keeping the revision they started from is safer than counting
     * checkpoints: a command that produces more (or fewer) checkpoints than expected
     * cannot move the boundary. The revision is the one read from the state before
     * the first command of the group.</p>
     *
     * @return true when at least one command was undone
     */
    public synchronized boolean undoTo(long revision) {
        boolean undone = false;
        while (!undoStack.isEmpty() && undoStack.peek().state.revision >= revision) {
            if (!undo()) break;
            undone = true;
        }
        return undone;
    }

    public synchronized boolean undo() {
        if (undoStack.isEmpty()) return false;
        long revertedRevision = state.revision;
        Checkpoint checkpoint = undoStack.pop();
        state = checkpoint.state.copy();
        dice.restore(checkpoint.randomState);
        state.revision = ++revisionCounter;
        append(EventType.UNDO_PERFORMED, "", "", details(
                "revertedRevision", revertedRevision,
                "restoredRandomState", checkpoint.randomState));
        return true;
    }

    private List<DamageComponent> resolveAttackDamage(
            AbilityDefinition ability,
            List<Integer> manualValues,
            boolean critical,
            String attackerId,
            String targetId) {
        List<DamageComponent> result = new ArrayList<>();
        for (int index = 0; index < ability.damage().size(); index++) {
            DamageFormula formula = ability.damage().get(index);
            int amount;
            Map<String, String> eventDetails;
            if (!manualValues.isEmpty()) {
                amount = manualValues.get(index);
                eventDetails = details(
                        "abilityId", ability.id(),
                        "abilityName", ability.name(),
                        "type", formula.type(),
                        "formula", formula.usesDice() ? formula.dice().notation() : formula.fixedAmount(),
                        "amount", amount,
                        "total", amount,
                        "source", "manual");
            } else if (formula.usesDice()) {
                DiceRollResult roll = dice.roll(formula.dice(), critical);
                amount = Math.max(0, roll.total());
                eventDetails = details(
                        "abilityId", ability.id(),
                        "abilityName", ability.name(),
                        "type", formula.type(),
                        "formula", formula.dice().notation(),
                        "dice", roll.dice(),
                        "modifier", roll.modifier(),
                        "amount", amount,
                        "total", amount,
                        "critical", critical);
            } else {
                amount = formula.fixedAmount();
                eventDetails = details(
                        "abilityId", ability.id(),
                        "abilityName", ability.name(),
                        "type", formula.type(),
                        "formula", formula.fixedAmount(),
                        "amount", amount,
                        "total", amount,
                        "source", "fixed",
                        "critical", critical);
            }
            result.add(new DamageComponent(formula.type(), amount));
            append(EventType.DAMAGE_ROLLED, attackerId, targetId, eventDetails);
        }
        return List.copyOf(result);
    }

    private DamageResult applyDamageInternal(
            String sourceCombatantId,
            String targetCombatantId,
            List<DamageComponent> components,
            boolean critical,
            D20RollInput concentrationRoll) {
        MutableCombatant target = combatant(targetCombatantId);
        noticed(targetCombatantId);
        List<DamageComponentResult> resolved = new ArrayList<>();
        long totalRawLong = 0;
        long totalAdjustedLong = 0;
        for (DamageComponent component : components) {
            totalRawLong = Math.addExact(totalRawLong, component.amount());
            boolean immune = target.snapshot.damageImmunities().contains(component.type());
            boolean resistant = !immune && target.snapshot.resistances().contains(component.type());
            boolean vulnerable = !immune && target.snapshot.vulnerabilities().contains(component.type());
            long adjustedLong = immune ? 0L : component.amount();
            if (resistant) adjustedLong /= 2;
            if (vulnerable) adjustedLong = Math.multiplyExact(adjustedLong, 2L);
            if (adjustedLong > Integer.MAX_VALUE) {
                throw rule("A damage component exceeds the supported range");
            }
            int adjusted = (int) adjustedLong;
            totalAdjustedLong = Math.addExact(totalAdjustedLong, adjustedLong);
            if (totalRawLong > Integer.MAX_VALUE || totalAdjustedLong > Integer.MAX_VALUE) {
                throw rule("Total damage exceeds the supported range");
            }
            DamageComponentResult componentResult = new DamageComponentResult(component.type(), component.amount(),
                    adjusted, immune, resistant, vulnerable);
            resolved.add(componentResult);
            append(EventType.DAMAGE_APPLIED, sourceCombatantId, targetCombatantId, details(
                    "type", component.type(),
                    "raw", component.amount(),
                    "adjusted", adjusted,
                    "immune", immune,
                    "resistant", resistant,
                    "vulnerable", vulnerable));
        }

        int totalRaw = (int) totalRawLong;
        int totalAdjusted = (int) totalAdjustedLong;

        int temporaryAbsorbed = Math.min(target.temporaryHitPoints, totalAdjusted);
        target.temporaryHitPoints -= temporaryAbsorbed;
        int remaining = totalAdjusted - temporaryAbsorbed;
        int hitPointsBefore = target.currentHitPoints;
        int hitPointsLost = Math.min(hitPointsBefore, remaining);
        target.currentHitPoints -= hitPointsLost;
        append(EventType.DAMAGE_APPLIED, sourceCombatantId, targetCombatantId, details(
                "totalRaw", totalRaw,
                "totalAdjusted", totalAdjusted,
                "temporaryAbsorbed", temporaryAbsorbed,
                "hitPointsLost", hitPointsLost,
                "hitPointsAfter", target.currentHitPoints,
                "critical", critical));
        if (target.currentHitPoints == 0 && hitPointsLost > 0) {
            append(EventType.ZERO_HIT_POINTS, sourceCombatantId, targetCombatantId, details("critical", critical));
        }

        applyDamageAtZeroHitPoints(sourceCombatantId, targetCombatantId, target, remaining, hitPointsBefore,
                hitPointsLost, critical);

        Optional<ConcentrationCheckResult> concentrationCheck = Optional.empty();
        if (totalAdjusted > 0 && target.concentration != null && target.currentHitPoints == 0) {
            endConcentrationInternal(targetCombatantId, "zero hit points");
        } else if (totalAdjusted > 0 && target.concentration != null) {
            int difficultyClass = Math.min(30, Math.max(10, totalAdjusted / 2));
            D20RollResult roll = rollD20For(target, concentrationRoll, target.snapshot.constitutionSaveBonus());
            boolean maintained = roll.total() >= difficultyClass;
            ConcentrationCheckResult check = new ConcentrationCheckResult(difficultyClass, roll, maintained);
            concentrationCheck = Optional.of(check);
            append(EventType.CONCENTRATION_CHECKED, targetCombatantId, "", merge(
                    rollDetails(roll), details("difficultyClass", difficultyClass, "maintained", maintained)));
            if (!maintained) endConcentrationInternal(targetCombatantId, "failed save");
        }

        return new DamageResult(sourceCombatantId, targetCombatantId, resolved, totalRaw, totalAdjusted,
                temporaryAbsorbed, hitPointsLost, target.currentHitPoints, critical, concentrationCheck);
    }

    /**
     * Conseguenze del danno su una creatura che si trova, o finisce, a 0 punti ferita.
     *
     * <p>Subire danni elimina lo stato Stable. Se il danno residuo dopo essere
     * arrivati a zero raggiunge i punti ferita massimi la morte e' immediata;
     * altrimenti un colpo incassato gia' a 0 PF causa un fallimento contro morte,
     * due se il colpo e' critico.</p>
     */
    private void applyDamageAtZeroHitPoints(
            String sourceCombatantId,
            String targetCombatantId,
            MutableCombatant target,
            int remaining,
            int hitPointsBefore,
            int hitPointsLost,
            boolean critical) {
        if (remaining <= 0 || target.currentHitPoints != 0) {
            return;
        }

        if (target.deathSaves.stable()) {
            target.deathSaves = DeathSaveState.none();
        }

        int excess = remaining - hitPointsLost;
        if (excess >= target.snapshot.maxHitPoints()) {
            target.deathSaves = target.deathSaves.withFailures(DeathSaveState.REQUIRED);
            append(EventType.DIED, sourceCombatantId, targetCombatantId, details(
                    "cause", "massive damage", "excess", excess));
            return;
        }

        if (hitPointsBefore == 0) {
            int failures = critical ? 2 : 1;
            target.deathSaves = target.deathSaves.withFailures(failures);
            append(EventType.DEATH_SAVE_ROLLED, sourceCombatantId, targetCombatantId, details(
                    "source", "damage",
                    "failures", failures,
                    "totalFailures", target.deathSaves.failures()));
            if (target.deathSaves.dead()) {
                append(EventType.DIED, sourceCombatantId, targetCombatantId, details("cause", "death saves"));
            }
        }
    }

    /** Avvia il turno per ogni membro del gruppo corrente. */
    private void startTurnInternal() {
        // Il limite agli slot e' del turno globale, non del periodo fra due
        // turni dello stesso lanciatore. Azzerarlo per tutti consente quindi una
        // reazione a slot nel turno successivo senza ricaricare la reazione.
        resetSpellSlotBudgetsForNewTurn();
        if (state.ruleSession.executable()) {
            state.ruleSession = state.ruleSession.withState(genericRules().beginTurn(state.ruleSession.state()));
            fireGenericEventInternal("TURN_START");
        }
        List<String> active = currentCombatantIds();
        if (state.ruleSession.executable()) {
            for (String combatantId : active) {
                RuleScope scope = RuleScope.actor(combatantId);
                state.ruleSession.findState(scope).ifPresent(existing -> {
                    state.ruleSession = state.ruleSession.withState(scope, genericRules().beginTurn(existing));
                    fireGenericEventInternal("TURN_START", scope);
                });
            }
        }
        for (String combatantId : currentTurnGroup()) {
            // Un membro a 0 PF non riceve budget ne' evento di turno, ma la soglia
            // d'inizio continua a far avanzare correttamente condizioni ed effetti.
            if (!active.contains(combatantId)) {
                processConditionBoundary(combatantId, true);
                continue;
            }
            MutableCombatant combatant = combatant(combatantId);
            // La velocita' e' ridotta da Exhaustion: il budget parte da quella effettiva.
            int speed = effectiveSpeed(combatant);
            state.turnBudgets.put(combatantId, TurnBudget.fresh(speed, combatant.snapshot.attacksPerAction()));
            processConditionBoundary(combatantId, true);
            append(EventType.TURN_STARTED, combatantId, "", details("round", state.round));
        }
    }

    private void resetSpellSlotBudgetsForNewTurn() {
        state.turnBudgets.replaceAll(
                (combatantId, turnBudget) -> turnBudget.resetSpellSlotSpentForNewTurn());
    }

    private void processConditionBoundary(String activeCombatantId, boolean start) {
        for (Map.Entry<String, MutableCombatant> targetEntry : state.combatants.entrySet()) {
            String targetId = targetEntry.getKey();
            MutableCombatant target = targetEntry.getValue();
            List<ConditionInstance> original = new ArrayList<>(target.conditions);
            for (ConditionInstance condition : original) {
                if (!matchesBoundary(condition, targetId, activeCombatantId, start)) continue;
                if (condition.duration().remainingOccurrences() == 1) {
                    target.conditions.remove(condition);
                    append(EventType.CONDITION_EXPIRED, condition.sourceCombatantId(), targetId, details(
                            "conditionId", condition.id(), "condition", condition.type()));
                } else {
                    int index = target.conditions.indexOf(condition);
                    target.conditions.set(index, condition.withDuration(condition.duration().decrement()));
                }
            }
        }
    }

    private static boolean matchesBoundary(
            ConditionInstance condition, String targetId, String activeCombatantId, boolean start) {
        return switch (condition.duration().expiry()) {
            case START_OF_TARGET_TURN -> start && targetId.equals(activeCombatantId);
            case END_OF_TARGET_TURN -> !start && targetId.equals(activeCombatantId);
            case START_OF_SOURCE_TURN -> start && condition.sourceCombatantId().equals(activeCombatantId);
            case END_OF_SOURCE_TURN -> !start && condition.sourceCombatantId().equals(activeCombatantId);
            case MANUAL, CONCENTRATION -> false;
        };
    }

    private void endConcentrationInternal(String ownerCombatantId, String reason) {
        MutableCombatant owner = combatant(ownerCombatantId);
        ConcentrationState ended = owner.concentration;
        if (ended == null) return;
        owner.concentration = null;
        append(EventType.CONCENTRATION_ENDED, ownerCombatantId, "", details(
                "abilityId", ended.abilityId(), "reason", reason));
        for (Map.Entry<String, MutableCombatant> targetEntry : state.combatants.entrySet()) {
            List<ConditionInstance> dependent = targetEntry.getValue().conditions.stream()
                    .filter(condition -> condition.duration().expiry() == ConditionExpiry.CONCENTRATION
                            && ownerCombatantId.equals(condition.concentrationOwnerId()))
                    .collect(Collectors.toList());
            for (ConditionInstance condition : dependent) {
                targetEntry.getValue().conditions.remove(condition);
                append(EventType.CONDITION_EXPIRED, ownerCombatantId, targetEntry.getKey(), details(
                        "conditionId", condition.id(), "condition", condition.type(), "reason", "concentration"));
            }
        }
    }

    private void validateActivationCost(String combatantId, ActivationCost cost) {
        validateActivationCost(combatantId, cost, false);
    }

    private void validateActivationCost(String combatantId, ActivationCost cost, boolean magicAction) {
        Objects.requireNonNull(cost, "cost");
        MutableCombatant combatant = combatant(combatantId);
        if (isDead(combatant)) {
            throw rule("A dead combatant cannot act");
        }
        if (combatant.currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot act");
        }
        TurnBudget budget = budget(combatantId);
        switch (cost) {
            case ACTION -> {
                requireCurrentCombatant(combatantId);
                if (!budget.canUseAction(magicAction)) throw rule("Action already spent");
            }
            case BONUS_ACTION -> {
                requireCurrentCombatant(combatantId);
                if (!budget.bonusActionAvailable()) throw rule("Bonus action already spent");
            }
            case REACTION -> {
                if (!budget.reactionAvailable()) throw rule("Reaction already spent");
            }
            case LEGENDARY_ACTION -> throw rule("Legendary action pools are not part of this vertical slice");
            case NONE -> { }
        }
    }

    /**
     * An attack paid with an Action starts the Attack action on its first strike.
     * Further strikes from that same action spend only its remaining attack count.
     */
    private void validateAttackActivationCost(
            String combatantId, ActivationCost cost, boolean magicAction) {
        if (cost != ActivationCost.ACTION || magicAction) {
            validateActivationCost(combatantId, cost, magicAction);
            return;
        }
        MutableCombatant combatant = combatant(combatantId);
        if (isDead(combatant)) {
            throw rule("A dead combatant cannot act");
        }
        if (combatant.currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot act");
        }
        requireCurrentCombatant(combatantId);
        TurnBudget budget = budget(combatantId);
        if (budget.attackActionInProgress()) {
            if (budget.attacksRemaining() == 0) {
                throw rule("No attacks remain in the Attack action");
            }
        } else if (!budget.canUseAction(false)) {
            throw rule("Action already spent");
        }
    }

    private void consumeAttackActivationCost(
            String combatantId, ActivationCost cost, boolean magicAction) {
        if (cost != ActivationCost.ACTION || magicAction) {
            consumeActivationCost(combatantId, cost, magicAction);
            return;
        }
        TurnBudget current = budget(combatantId);
        boolean startsAttackAction = !current.attackActionInProgress();
        TurnBudget updated = startsAttackAction
                ? current.startAttackAction(combatant(combatantId).snapshot.attacksPerAction())
                : current.useAttack();
        state.turnBudgets.put(combatantId, updated);
        if (startsAttackAction) {
            append(EventType.ACTION_SPENT, combatantId, "", details("cost", cost));
        }
    }

    private void consumeActivationCost(String combatantId, ActivationCost cost) {
        consumeActivationCost(combatantId, cost, false);
    }

    private void consumeActivationCost(String combatantId, ActivationCost cost, boolean magicAction) {
        TurnBudget budget = budget(combatantId);
        TurnBudget updated = switch (cost) {
            case ACTION -> budget.useAction(magicAction);
            case BONUS_ACTION -> budget.useBonusAction();
            case REACTION -> budget.useReaction();
            case NONE -> budget;
            case LEGENDARY_ACTION -> throw rule("Legendary action pools are not part of this vertical slice");
        };
        state.turnBudgets.put(combatantId, updated);
        if (cost != ActivationCost.NONE) {
            append(EventType.ACTION_SPENT, combatantId, "", details("cost", cost));
        }
    }

    private void validateAbilityResource(
            String combatantId,
            MutableCombatant combatant,
            AbilityDefinition ability) {
        validateAbilityResource(combatantId, combatant, ability, ability.resourceId());
    }

    private void validateAbilityResource(
            String combatantId,
            MutableCombatant combatant,
            AbilityDefinition ability,
            String resourceId) {
        if (ability.resourceCost() == 0) return;
        CombatResourceState resource = combatant.resources.get(resourceId);
        if (resource == null) {
            throw rule("Ability resource is missing: " + resourceId);
        }
        if (resource.remaining() < ability.resourceCost()) {
            throw rule("Not enough uses of " + resource.name());
        }
        if (SpellSlotResourceId.parse(resourceId).isPresent()
                && budget(combatantId).spellSlotSpentThisTurn()) {
            throw rule("A spell slot was already spent this turn");
        }
    }

    private void consumeAbilityResource(
            String combatantId, MutableCombatant combatant, AbilityDefinition ability) {
        consumeAbilityResource(combatantId, combatant, ability, ability.resourceId());
    }

    private void consumeAbilityResource(
            String combatantId,
            MutableCombatant combatant,
            AbilityDefinition ability,
            String resourceId) {
        if (ability.resourceCost() == 0) return;
        CombatResourceState resource = combatant.resources.get(resourceId);
        CombatResourceState updated = resource.spend(ability.resourceCost());
        combatant.resources.put(updated.id(), updated);
        append(EventType.RESOURCE_SPENT, combatantId, "", details(
                "abilityId", ability.id(),
                "abilityName", ability.name(),
                "resourceId", updated.id(),
                "resourceName", updated.name(),
                "cost", ability.resourceCost(),
                "remaining", updated.remaining(),
                "maximum", updated.maximum()));
        SpellSlotResourceId.parse(resourceId).ifPresent(slot -> {
            state.turnBudgets.put(combatantId, budget(combatantId).markSpellSlotSpent());
            append(EventType.SPELL_SLOT_SPENT, combatantId, "", details(
                    "abilityId", ability.id(),
                    "abilityName", ability.name(),
                    "resourceId", updated.id(),
                    "slotLevel", slot.level()));
        });
    }

    private D20RollResult rollD20(D20RollInput input, int modifier) {
        if (input.source() == RollSource.MANUAL) {
            int natural = input.manualNaturalRoll();
            int total = checkedTotal(natural, modifier, "D20 total");
            return new D20RollResult(RollSource.MANUAL, input.mode(), List.of(natural),
                    natural, modifier, total);
        }
        List<Integer> rolled = dice.rollD20(input.mode());
        int natural = selectedD20(rolled, input.mode());
        int total = checkedTotal(natural, modifier, "D20 total");
        return new D20RollResult(RollSource.DIGITAL, input.mode(), rolled, natural, modifier, total);
    }

    /**
     * Tiro d20 effettuato da un combattente identificato.
     *
     * <p>Applica la penalita' di Exhaustion, che vale per <em>tutti</em> i D20 Test
     * — prove, attacchi e tiri salvezza — nella misura di −2 per livello.</p>
     */
    private D20RollResult rollD20For(MutableCombatant roller, D20RollInput input, int modifier) {
        int penalty = -state.rulesetRuntime.exhaustionD20PenaltyPerLevel() * roller.exhaustionLevel;
        return rollD20(input, checkedTotal(modifier, penalty, "D20 modifier"));
    }

    private static int checkedTotal(int first, int second, String label) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            throw rule(label + " exceeds the supported range");
        }
    }

    private static int selectedD20(List<Integer> dice, D20Mode mode) {
        return mode == D20Mode.ADVANTAGE
                ? dice.stream().mapToInt(Integer::intValue).max().orElseThrow()
                : mode == D20Mode.DISADVANTAGE
                        ? dice.stream().mapToInt(Integer::intValue).min().orElseThrow()
                        : dice.get(0);
    }

    /** Applica uno svantaggio imposto dalle regole, annullando un eventuale vantaggio. */
    private static D20Mode imposeDisadvantage(D20Mode requested, boolean imposed) {
        if (!imposed) return requested;
        return requested == D20Mode.ADVANTAGE ? D20Mode.NORMAL : D20Mode.DISADVANTAGE;
    }

    private static D20RollInput imposeDisadvantage(D20RollInput input, boolean imposed) {
        D20Mode effectiveMode = imposeDisadvantage(input.mode(), imposed);
        if (effectiveMode == input.mode()) return input;
        return new D20RollInput(input.source(), effectiveMode, input.manualNaturalRoll());
    }

    private static boolean usesStrengthOrDexterityForAttack(AbilityDefinition ability) {
        return ability.attackAbility() == SaveAbility.STRENGTH
                || ability.attackAbility() == SaveAbility.DEXTERITY;
    }

    private AttackOutcome attackOutcome(D20RollResult roll, int armorClass) {
        if (state.rulesetRuntime.naturalOneAlwaysMisses() && roll.naturalRoll() == 1) {
            return AttackOutcome.MISS;
        }
        if (roll.naturalRoll() >= state.rulesetRuntime.criticalHitMinimumNatural()) {
            return AttackOutcome.CRITICAL_HIT;
        }
        return roll.total() >= armorClass ? AttackOutcome.HIT : AttackOutcome.MISS;
    }

    private static boolean incapacitates(app.d6d.domain.combat.ConditionType type) {
        return type.equals(app.d6d.domain.combat.ConditionType.INCAPACITATED)
                || type.equals(app.d6d.domain.combat.ConditionType.PARALYZED)
                || type.equals(app.d6d.domain.combat.ConditionType.PETRIFIED)
                || type.equals(app.d6d.domain.combat.ConditionType.STUNNED)
                || type.equals(app.d6d.domain.combat.ConditionType.UNCONSCIOUS);
    }

    private void beginCommand() {
        undoStack.push(new Checkpoint(
                state.copy(), dice.state(), audit.size(), nextEventSequence, revisionCounter));
        state.revision = ++revisionCounter;
    }

    /** Rolls back a command that failed after validation, including RNG, audit and revision counters. */
    private void rollbackFailedCommand() {
        Checkpoint checkpoint = undoStack.pop();
        state = checkpoint.state.copy();
        dice.restore(checkpoint.randomState);
        while (audit.size() > checkpoint.auditSize) {
            audit.remove(audit.size() - 1);
        }
        nextEventSequence = checkpoint.nextEventSequence;
        revisionCounter = checkpoint.revisionCounter;
    }

    private void append(EventType type, String actorId, String targetId, Map<String, String> details) {
        audit.add(new CombatEvent(nextEventSequence++, state.revision, type, state.round,
                actorId, targetId, details));
    }

    private MutableCombatant combatant(String id) {
        MutableCombatant result = state.combatants.get(id);
        if (result == null) throw rule("Unknown combatant: " + id);
        return result;
    }

    private AbilityDefinition ability(MutableCombatant combatant, String abilityId) {
        AbilityDefinition ability = combatant.snapshot.abilities().stream()
                .filter(candidate -> candidate.id().equals(abilityId)).findFirst()
                .orElseThrow(() -> rule("Unknown ability: " + abilityId));
        // Un tratto passivo vale sempre: non si attiva e non consuma il turno.
        if (ability.passive()) {
            throw rule("A passive trait cannot be activated: " + abilityId);
        }
        return ability;
    }

    private TurnBudget budget(String combatantId) {
        combatant(combatantId);
        return state.turnBudgets.get(combatantId);
    }

    private void rebuildInitiativeOrder() {
        List<String> resorted = state.rosterOrder.stream()
                .sorted(Comparator
                        .<String>comparingInt(id -> state.initiativeScores.get(id)).reversed()
                        .thenComparingInt(state.rosterOrder::indexOf))
                .collect(Collectors.toList());
        state.initiativeOrder.clear();
        state.initiativeOrder.addAll(resorted);
    }

    /**
     * L'ordine dei turni raggruppato.
     *
     * <p>Senza turni simultanei ogni combattente forma un gruppo da solo; con i
     * turni simultanei attivi i pareggi d'iniziativa consecutivi formano un gruppo
     * unico che agisce insieme.</p>
     */
    private List<List<String>> turnGroups() {
        if (!state.simultaneousTies) {
            return state.initiativeOrder.stream().map(List::<String>of).collect(Collectors.toList());
        }
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        Integer currentScore = null;
        for (String id : state.initiativeOrder) {
            Integer score = state.initiativeScores.get(id);
            if (!current.isEmpty() && Objects.equals(score, currentScore)) {
                current.add(id);
            } else {
                if (!current.isEmpty()) groups.add(List.copyOf(current));
                current = new ArrayList<>();
                current.add(id);
                currentScore = score;
            }
        }
        if (!current.isEmpty()) groups.add(List.copyOf(current));
        return groups;
    }

    /** Tutti i combattenti che stanno giocando il turno corrente. */
    private List<String> currentCombatantIds() {
        return livingCombatants(currentTurnGroup());
    }

    /** Gruppo strutturale corrente, inclusi i membri a 0 PF mostrati nella striscia. */
    private List<String> currentTurnGroup() {
        List<List<String>> groups = turnGroups();
        if (state.turnIndex < 0 || state.turnIndex >= groups.size()) throw rule("There is no current turn");
        return groups.get(state.turnIndex);
    }

    private String currentCombatantId() {
        return currentCombatantIds().get(0);
    }

    /** In un turno simultaneo ognuno dei combattenti in parita' puo' agire. */
    private void requireCurrentCombatant(String combatantId) {
        MutableCombatant combatant = combatant(combatantId);
        if (isDead(combatant)) {
            throw rule("A dead combatant cannot act");
        }
        if (combatant.currentHitPoints == 0) {
            throw rule("A combatant at zero hit points cannot act");
        }
        if (!currentCombatantIds().contains(combatantId)) {
            throw rule("It is not " + combatantId + "'s turn");
        }
    }

    /** Membri ancora in piedi di un gruppo strutturale d'iniziativa. */
    private List<String> livingCombatants(List<String> group) {
        return group.stream()
                .filter(id -> {
                    MutableCombatant combatant = combatant(id);
                    return combatant.currentHitPoints > 0 && !isDead(combatant);
                })
                .collect(Collectors.toList());
    }

    /** La morte e' indipendente dai PF: Exhaustion 6 puo' lasciare un valore positivo. */
    private boolean isDead(MutableCombatant combatant) {
        return combatant.deathSaves.dead()
                || combatant.exhaustionLevel >= state.rulesetRuntime.maximumExhaustion();
    }

    private int effectiveSpeed(MutableCombatant combatant) {
        return Math.max(0, combatant.snapshot.speedFeet()
                - state.rulesetRuntime.exhaustionSpeedPenaltyFeetPerLevel() * combatant.exhaustionLevel);
    }

    /** Primo attore vivo del gruppo, oppure il suo primo membro per le sole correzioni d'ordine. */
    private String currentTurnAnchor() {
        List<List<String>> groups = turnGroups();
        if (state.turnIndex < 0 || state.turnIndex >= groups.size()) {
            throw rule("There is no current turn");
        }
        List<String> group = groups.get(state.turnIndex);
        List<String> living = livingCombatants(group);
        return living.isEmpty() ? group.get(0) : living.get(0);
    }

    /** All'avvio salta gli eventuali gruppi iniziali interamente a 0 PF senza chiudere il round. */
    private boolean seekFirstPlayableTurn() {
        List<List<String>> groups = turnGroups();
        for (int index = 0; index < groups.size(); index++) {
            if (!livingCombatants(groups.get(index)).isEmpty()) {
                state.turnIndex = index;
                return true;
            }
            processSkippedTurn(groups.get(index));
        }
        return false;
    }

    /**
     * Avanza fino al prossimo gruppo con almeno un membro vivo.
     *
     * Il passaggio oltre l'ultimo gruppo chiude e riapre il round esattamente una
     * volta. Se nessuno e' in piedi, dopo un giro completo restituisce {@code false}.
     */
    private boolean advanceToNextPlayableTurn() {
        List<List<String>> groups = turnGroups();
        for (int checked = 0; checked < groups.size(); checked++) {
            state.turnIndex++;
            if (state.turnIndex >= groups.size()) {
                append(EventType.ROUND_ENDED, "", "", details("round", state.round));
                state.round++;
                state.turnIndex = 0;
                append(EventType.ROUND_STARTED, "", "", details("round", state.round));
            }
            if (!livingCombatants(groups.get(state.turnIndex)).isEmpty()) return true;
            processSkippedTurn(groups.get(state.turnIndex));
        }
        return false;
    }

    /** Fa trascorrere le soglie temporali di un gruppo saltato, senza concedere azioni. */
    private void processSkippedTurn(List<String> group) {
        for (String combatantId : group) {
            processConditionBoundary(combatantId, true);
            processConditionBoundary(combatantId, false);
        }
    }

    private boolean conditionIdExists(String conditionId) {
        return state.combatants.values().stream()
                .flatMap(combatant -> combatant.conditions.stream())
                .anyMatch(condition -> condition.id().equals(conditionId));
    }

    private void requireSetupPhase() {
        if (state.status != CombatStatus.DRAFT && state.status != CombatStatus.READY) {
            throw rule("Initiative can only be changed in DRAFT or READY");
        }
    }

    private void requireStatus(CombatStatus required) {
        if (state.status != required) {
            throw rule("Command requires " + required + " but encounter is " + state.status);
        }
    }

    private static CombatRuleException rule(String message) {
        return new CombatRuleException(message);
    }

    private static void validateSupportedRuntime(
            RulesetBinding binding,
            RulesetRuntimeConfig runtime) {
        if (!binding.runtimeSemanticsVersion().equals(runtime.semanticsVersion())) {
            throw new IllegalArgumentException("Ruleset binding and runtime semantics differ");
        }
        if (!RulesetRuntimeConfig.CURRENT_SEMANTICS.equals(runtime.semanticsVersion())) {
            throw new IllegalArgumentException(
                    "Unsupported ruleset runtime semantics: " + runtime.semanticsVersion());
        }
    }

    private static void validateRuleSnapshot(
            RulesetBinding binding,
            RulesetRuntimeConfig runtime,
            RuleSessionSnapshot snapshot) {
        if (!snapshot.executable()) return; // Compatibilità con sessioni schema 1/2.
        String runtimeHash = RulesetCanonicalizer.runtimeHash(runtime, snapshot.entities());
        if (!runtimeHash.equals(binding.runtimeHash())) {
            throw new IllegalArgumentException("Embedded rules do not match the bound runtime hash");
        }
        snapshot.compile(binding.canonicalHash());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value;
    }

    private static Map<String, String> rollDetails(D20RollResult roll) {
        return details(
                "source", roll.source(),
                "mode", roll.mode(),
                "dice", roll.dice(),
                "natural", roll.naturalRoll(),
                "modifier", roll.modifier(),
                "total", roll.total());
    }

    private static Map<String, String> merge(Map<String, String> first, Map<String, String> second) {
        Map<String, String> result = new LinkedHashMap<>(first);
        result.putAll(second);
        return result;
    }

    private static Map<String, String> details(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Details need key/value pairs");
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), String.valueOf(pairs[index + 1]));
        }
        return result;
    }

    private record Checkpoint(
            MutableState state,
            long randomState,
            int auditSize,
            long nextEventSequence,
            long revisionCounter) { }

    private static final class MutableCombatant {
        private CombatantSnapshot snapshot;
        private int currentHitPoints;
        private int temporaryHitPoints;
        private final List<ConditionInstance> conditions;
        private ConcentrationState concentration;
        private DeathSaveState deathSaves = DeathSaveState.none();
        private int exhaustionLevel;
        private final LinkedHashMap<String, CombatResourceState> resources;

        private MutableCombatant(
                CombatantSnapshot snapshot,
                int currentHitPoints,
                int temporaryHitPoints,
                List<ConditionInstance> conditions,
                ConcentrationState concentration) {
            this.snapshot = snapshot;
            this.currentHitPoints = currentHitPoints;
            this.temporaryHitPoints = temporaryHitPoints;
            this.conditions = new ArrayList<>(conditions);
            this.concentration = concentration;
            this.resources = new LinkedHashMap<>();
            snapshot.resources().forEach(resource -> this.resources.put(resource.id(), resource));
        }

        private static MutableCombatant from(CombatantSnapshot snapshot) {
            return new MutableCombatant(snapshot, snapshot.initialHitPoints(), snapshot.initialTemporaryHitPoints(),
                    List.of(), null);
        }

        private static MutableCombatant from(CombatantState state) {
            MutableCombatant restored = new MutableCombatant(state.snapshot(), state.currentHitPoints(),
                    state.temporaryHitPoints(), state.conditions(), state.concentration());
            restored.deathSaves = state.deathSaves();
            restored.exhaustionLevel = state.exhaustionLevel();
            restored.resources.clear();
            state.resources().forEach(resource -> restored.resources.put(resource.id(), resource));
            return restored;
        }

        private MutableCombatant copy() {
            MutableCombatant duplicate =
                    new MutableCombatant(snapshot, currentHitPoints, temporaryHitPoints, conditions, concentration);
            duplicate.deathSaves = deathSaves;
            duplicate.exhaustionLevel = exhaustionLevel;
            duplicate.resources.clear();
            duplicate.resources.putAll(resources);
            return duplicate;
        }

        private CombatantState toDomain(RulesetRuntimeConfig runtime) {
            return new CombatantState(snapshot, currentHitPoints, temporaryHitPoints, conditions, concentration,
                    deathSaves, exhaustionLevel, List.copyOf(resources.values()),
                    runtime.maximumExhaustion(), runtime.exhaustionD20PenaltyPerLevel(),
                    runtime.exhaustionSpeedPenaltyFeetPerLevel());
        }
    }

    private static final class MutableState {
        private final String encounterId;
        private String rulesetVersion;
        private final String contentVersion;
        private RulesetBinding rulesetBinding;
        private RulesetRuntimeConfig rulesetRuntime;
        private RuleSessionSnapshot ruleSession;
        private CombatStatus status;
        private long revision;
        private final List<String> rosterOrder;
        private final LinkedHashMap<String, MutableCombatant> combatants;
        private final LinkedHashMap<String, Integer> initiativeScores;
        private final List<String> initiativeOrder;
        private int round;
        private int turnIndex;
        private final LinkedHashMap<String, TurnBudget> turnBudgets;
        private final LinkedHashSet<String> partyCombatantIds;
        private boolean simultaneousTies;
        private BattleMap battleMap = BattleMap.none();
        private int alarmRadiusFeet = DEFAULT_ALARM_RADIUS_FEET;
        private final LinkedHashSet<String> dormantCombatantIds = new LinkedHashSet<>();

        private MutableState(
                String encounterId,
                String rulesetVersion,
                String contentVersion,
                CombatStatus status,
                long revision,
                List<String> rosterOrder,
                Map<String, MutableCombatant> combatants,
                Map<String, Integer> initiativeScores,
                List<String> initiativeOrder,
                int round,
                int turnIndex,
                Map<String, TurnBudget> turnBudgets,
                Collection<String> partyCombatantIds,
                RulesetBinding rulesetBinding,
                RulesetRuntimeConfig rulesetRuntime,
                RuleSessionSnapshot ruleSession) {
            this.encounterId = encounterId;
            this.rulesetVersion = rulesetVersion;
            this.contentVersion = contentVersion;
            this.rulesetBinding = Objects.requireNonNull(rulesetBinding, "rulesetBinding");
            this.rulesetRuntime = Objects.requireNonNull(rulesetRuntime, "rulesetRuntime");
            this.ruleSession = Objects.requireNonNull(ruleSession, "ruleSession");
            this.status = status;
            this.revision = revision;
            this.rosterOrder = new ArrayList<>(rosterOrder);
            this.combatants = new LinkedHashMap<>(combatants);
            this.initiativeScores = new LinkedHashMap<>(initiativeScores);
            this.initiativeOrder = new ArrayList<>(initiativeOrder);
            this.round = round;
            this.turnIndex = turnIndex;
            this.turnBudgets = new LinkedHashMap<>(turnBudgets);
            this.partyCombatantIds = new LinkedHashSet<>(partyCombatantIds);
        }

        private static MutableState empty(
                String encounterId,
                String rulesetVersion,
                String contentVersion,
                RulesetBinding rulesetBinding,
                RulesetRuntimeConfig rulesetRuntime,
                RuleSessionSnapshot ruleSession) {
            return new MutableState(encounterId, rulesetVersion, contentVersion, CombatStatus.DRAFT, 0,
                    List.of(), Map.of(), Map.of(), List.of(), 0, -1, Map.of(), Set.of(),
                    rulesetBinding, rulesetRuntime, ruleSession);
        }

        private static MutableState from(CombatState state) {
            Map<String, MutableCombatant> combatants = new LinkedHashMap<>();
            state.combatants().forEach((id, combatant) -> combatants.put(id, MutableCombatant.from(combatant)));
            MutableState restored = new MutableState(state.encounterId(), state.rulesetVersion(),
                    state.contentVersion(), state.status(), state.revision(), state.rosterOrder(), combatants,
                    state.initiativeScores(), state.initiativeOrder(), state.round(), state.turnIndex(),
                    state.turnBudgets(), state.partyCombatantIds(), state.rulesetBinding(), state.rulesetRuntime(),
                    state.ruleSession());
            restored.simultaneousTies = state.simultaneousTies();
            restored.battleMap = state.battleMap();
            restored.alarmRadiusFeet = state.alarmRadiusFeet();
            restored.dormantCombatantIds.addAll(state.dormantCombatantIds());
            return restored;
        }

        private MutableState copy() {
            Map<String, MutableCombatant> copiedCombatants = new LinkedHashMap<>();
            combatants.forEach((id, combatant) -> copiedCombatants.put(id, combatant.copy()));
            MutableState duplicate = new MutableState(encounterId, rulesetVersion, contentVersion, status, revision,
                    rosterOrder, copiedCombatants, initiativeScores, initiativeOrder, round, turnIndex, turnBudgets,
                    partyCombatantIds, rulesetBinding, rulesetRuntime, ruleSession);
            duplicate.simultaneousTies = simultaneousTies;
            duplicate.battleMap = battleMap;
            duplicate.alarmRadiusFeet = alarmRadiusFeet;
            duplicate.dormantCombatantIds.addAll(dormantCombatantIds);
            return duplicate;
        }

        private CombatState toDomain(long seed, long randomState) {
            Map<String, CombatantState> domainCombatants = new LinkedHashMap<>();
            combatants.forEach((id, combatant) -> domainCombatants.put(id, combatant.toDomain(rulesetRuntime)));
            return new CombatState(encounterId, rulesetVersion, contentVersion, status, revision, seed, randomState,
                    rosterOrder, domainCombatants, initiativeScores, initiativeOrder, round, turnIndex, turnBudgets,
                    partyCombatantIds, simultaneousTies, battleMap, alarmRadiusFeet, dormantCombatantIds,
                    rulesetBinding, rulesetRuntime, ruleSession);
        }
    }
}
