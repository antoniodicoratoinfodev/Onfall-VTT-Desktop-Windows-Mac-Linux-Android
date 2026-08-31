package app.d6d.rules.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pianifica una sincronizzazione senza scrivere direttamente in alcun archivio.
 *
 * <p>Il risultato contiene lo stato ottenibile con le sole modifiche automatiche
 * sicure e mantiene separate le proposte che richiedono conferma. Il chiamante
 * puo' quindi includere applicazione e salvataggio nel proprio checkpoint
 * transazionale, invece di lasciare scritture parziali alla UI.</p>
 */
public final class RuleStateSyncPlanner {
    private RuleStateSyncPlanner() { }

    public enum Decision { AUTO_APPLIED, PROPOSED, SKIPPED, CONFLICT }

    public record Change(
            String ruleId,
            RuleKind kind,
            String before,
            String after,
            Decision decision,
            String reason) {
        public Change {
            ruleId = Objects.requireNonNull(ruleId, "ruleId");
            kind = Objects.requireNonNull(kind, "kind");
            before = before == null ? "" : before;
            after = after == null ? "" : after;
            decision = Objects.requireNonNull(decision, "decision");
            reason = reason == null ? "" : reason;
        }
    }

    public record Plan(
            boolean bindingCompatible,
            RuleRuntimeState automaticResult,
            List<Change> changes) {
        public Plan {
            automaticResult = Objects.requireNonNull(automaticResult, "automaticResult");
            changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        }

        public boolean hasConflicts() {
            return changes.stream().anyMatch(change -> change.decision() == Decision.CONFLICT);
        }

        public boolean requiresConfirmation() {
            return changes.stream().anyMatch(change -> change.decision() == Decision.PROPOSED);
        }
    }

    public static Plan plan(
            CompiledRuleset rules,
            RulesetBinding sourceBinding,
            RulesetBinding targetBinding,
            RuleScope sourceScope,
            RuleScope targetScope,
            RuleRuntimeState source,
            RuleRuntimeState target) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(sourceBinding, "sourceBinding");
        Objects.requireNonNull(targetBinding, "targetBinding");
        Objects.requireNonNull(sourceScope, "sourceScope");
        Objects.requireNonNull(targetScope, "targetScope");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");

        boolean compatible = sourceBinding.canonicalHash().equals(targetBinding.canonicalHash())
                && sourceBinding.runtimeHash().equals(targetBinding.runtimeHash())
                && sourceBinding.runtimeSemanticsVersion().equals(targetBinding.runtimeSemanticsVersion());
        LinkedHashMap<String, RuleValue> values = new LinkedHashMap<>(target.values());
        LinkedHashMap<String, RuleRuntimeState.ResourceState> resources = new LinkedHashMap<>(target.resources());
        LinkedHashMap<String, Integer> conditions = new LinkedHashMap<>(target.conditionStacks());
        ArrayList<Change> changes = new ArrayList<>();
        boolean changed = false;

        List<Map.Entry<String, StatePersistencePolicy>> policies = rules.persistencePolicies().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (Map.Entry<String, StatePersistencePolicy> entry : policies) {
            String id = entry.getKey();
            StatePersistencePolicy policy = entry.getValue();
            RuleEntity entity = rules.entities().get(id);
            if (entity == null) continue;
            String before = present(entity.kind(), id, target);
            String after = present(entity.kind(), id, source);
            if (before.equals(after)) continue;

            Decision decision;
            String reason;
            if (!ownerMatches(policy.owner(), sourceScope, targetScope)) {
                decision = Decision.CONFLICT;
                reason = "scope does not match the declared owner";
            } else if (policy.syncPolicy() == StatePersistencePolicy.SyncPolicy.NEVER
                    || policy.syncPolicy() == StatePersistencePolicy.SyncPolicy.LOCAL_ONLY) {
                decision = Decision.SKIPPED;
                reason = "state is local to its current owner";
            } else if (!compatible) {
                decision = Decision.CONFLICT;
                reason = "ruleset binding or runtime semantics differ";
            } else if (policy.syncPolicy() == StatePersistencePolicy.SyncPolicy.PROPOSE) {
                decision = Decision.PROPOSED;
                reason = "the ruleset requires explicit confirmation";
            } else {
                decision = Decision.AUTO_APPLIED;
                reason = "compatible binding and automatic policy";
                changed |= copy(entity.kind(), id, source, values, resources, conditions);
            }
            changes.add(new Change(id, entity.kind(), before, after, decision, reason));
        }
        RuleRuntimeState result = changed
                ? new RuleRuntimeState(values, resources, conditions, target.turnBudget(),
                        target.activeRuleIds(), target.revision() + 1)
                : target;
        changes.sort(Comparator.comparing(Change::ruleId));
        return new Plan(compatible, result, changes);
    }

    private static boolean ownerMatches(
            StatePersistencePolicy.Owner owner,
            RuleScope source,
            RuleScope target) {
        return switch (owner) {
            case SCOPE -> source.kind() == target.kind();
            case ACTOR -> source.kind() == RuleScope.Kind.ACTOR && target.kind() == RuleScope.Kind.ACTOR;
            case SESSION -> source.kind() == RuleScope.Kind.SESSION && target.kind() == RuleScope.Kind.SESSION;
            case CAMPAIGN -> source.kind() == RuleScope.Kind.CAMPAIGN && target.kind() == RuleScope.Kind.CAMPAIGN;
            case PARTY, GM -> source.kind() == target.kind();
        };
    }

    private static String present(RuleKind kind, String id, RuleRuntimeState state) {
        return switch (kind) {
            case STAT, SKILL, SAVE, DEFENSE, VALUE -> {
                RuleValue value = state.values().get(id);
                yield value == null ? "<derived>" : value.type() + ":" + value.canonicalValue();
            }
            case RESOURCE, TRACK -> {
                RuleRuntimeState.ResourceState resource = state.resources().get(id);
                yield resource == null ? "<missing>"
                        : number(resource.current()) + "/" + number(resource.maximum());
            }
            case CONDITION -> Integer.toString(state.conditionStacks().getOrDefault(id, 0));
            case ACTION_ECONOMY -> state.turnBudget().toString();
            default -> "<unsupported>";
        };
    }

    private static boolean copy(
            RuleKind kind,
            String id,
            RuleRuntimeState source,
            Map<String, RuleValue> values,
            Map<String, RuleRuntimeState.ResourceState> resources,
            Map<String, Integer> conditions) {
        return switch (kind) {
            case STAT, SKILL, SAVE, DEFENSE, VALUE -> {
                RuleValue value = source.values().get(id);
                RuleValue before = values.get(id);
                if (value == null) values.remove(id); else values.put(id, value);
                yield !Objects.equals(before, value);
            }
            case RESOURCE, TRACK -> {
                RuleRuntimeState.ResourceState value = source.resources().get(id);
                RuleRuntimeState.ResourceState before = resources.get(id);
                if (value == null) resources.remove(id); else resources.put(id, value);
                yield !Objects.equals(before, value);
            }
            case CONDITION -> {
                Integer value = source.conditionStacks().get(id);
                Integer before = conditions.get(id);
                if (value == null) conditions.remove(id); else conditions.put(id, value);
                yield !Objects.equals(before, value);
            }
            // I budget del turno sono sempre transitori: una policy puo' descriverli,
            // ma non vengono copiati fra documenti persistenti.
            case ACTION_ECONOMY -> false;
            default -> false;
        };
    }

    private static String number(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.compareTo(BigDecimal.ZERO) == 0 ? "0" : normalized.toPlainString();
    }
}
