package app.d6d.rules.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Stato keyed-by-ID condivisibile da schede, scene e combattimenti. */
public record RuleRuntimeState(
        Map<String, RuleValue> values,
        Map<String, ResourceState> resources,
        Map<String, Integer> conditionStacks,
        Map<String, BigDecimal> turnBudget,
        Set<String> activeRuleIds,
        long revision) {

    public RuleRuntimeState {
        values = immutableMap(values, "values");
        resources = immutableMap(resources, "resources");
        conditionStacks = immutableMap(conditionStacks, "conditionStacks");
        turnBudget = immutableDecimalMap(turnBudget, "turnBudget");
        activeRuleIds = immutableIds(activeRuleIds, "activeRuleIds");
        if (conditionStacks.values().stream().anyMatch(value -> value == null || value < 1)) {
            throw new IllegalArgumentException("Condition stacks must be positive");
        }
        if (turnBudget.values().stream().anyMatch(value -> value.compareTo(BigDecimal.ZERO) < 0)) {
            throw new IllegalArgumentException("Turn budget cannot be negative");
        }
        if (revision < 0) throw new IllegalArgumentException("Rule state revision cannot be negative");
    }

    public static RuleRuntimeState empty() {
        return new RuleRuntimeState(Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), 0);
    }

    public RuleRuntimeState withValue(String id, RuleValue value) {
        LinkedHashMap<String, RuleValue> changed = new LinkedHashMap<>(values);
        changed.put(requireId(id), Objects.requireNonNull(value, "value"));
        return new RuleRuntimeState(changed, resources, conditionStacks, turnBudget, activeRuleIds, revision + 1);
    }

    public RuleRuntimeState withResource(ResourceState resource) {
        Objects.requireNonNull(resource, "resource");
        LinkedHashMap<String, ResourceState> changed = new LinkedHashMap<>(resources);
        changed.put(resource.id(), resource);
        return new RuleRuntimeState(values, changed, conditionStacks, turnBudget, activeRuleIds, revision + 1);
    }

    public RuleRuntimeState withCondition(String id, int stacks) {
        String normalized = requireId(id);
        LinkedHashMap<String, Integer> changed = new LinkedHashMap<>(conditionStacks);
        if (stacks <= 0) changed.remove(normalized); else changed.put(normalized, stacks);
        return new RuleRuntimeState(values, resources, changed, turnBudget, activeRuleIds, revision + 1);
    }

    public RuleRuntimeState withTurnBudget(Map<String, BigDecimal> budget) {
        return new RuleRuntimeState(values, resources, conditionStacks, budget, activeRuleIds, revision + 1);
    }

    public RuleRuntimeState withActiveRules(Set<String> ids) {
        return new RuleRuntimeState(values, resources, conditionStacks, turnBudget, ids, revision + 1);
    }

    public record ResourceState(String id, BigDecimal current, BigDecimal maximum) {
        public ResourceState {
            id = requireId(id);
            current = normalize(Objects.requireNonNull(current, "current"));
            maximum = normalize(Objects.requireNonNull(maximum, "maximum"));
            if (maximum.compareTo(BigDecimal.ZERO) < 0 || current.compareTo(BigDecimal.ZERO) < 0
                    || current.compareTo(maximum) > 0) {
                throw new IllegalArgumentException("Invalid state for resource " + id);
            }
        }

        public ResourceState withCurrent(BigDecimal value) {
            return new ResourceState(id, value.max(BigDecimal.ZERO).min(maximum), maximum);
        }

        public ResourceState withMaximum(BigDecimal value, boolean preserveSpent) {
            BigDecimal nextMaximum = normalize(value.max(BigDecimal.ZERO));
            BigDecimal nextCurrent = preserveSpent
                    ? nextMaximum.subtract(maximum.subtract(current)).max(BigDecimal.ZERO)
                    : current.min(nextMaximum);
            return new ResourceState(id, nextCurrent, nextMaximum);
        }
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source, String field) {
        Objects.requireNonNull(source, field);
        TreeMap<String, T> sorted = new TreeMap<>();
        source.forEach((key, value) -> sorted.put(requireId(key), Objects.requireNonNull(value, field + " value")));
        return Map.copyOf(new LinkedHashMap<>(sorted));
    }

    private static Map<String, BigDecimal> immutableDecimalMap(Map<String, BigDecimal> source, String field) {
        Objects.requireNonNull(source, field);
        LinkedHashMap<String, BigDecimal> result = new LinkedHashMap<>();
        new TreeMap<>(source).forEach((key, value) ->
                result.put(requireId(key), normalize(Objects.requireNonNull(value, field + " value"))));
        return Map.copyOf(result);
    }

    private static Set<String> immutableIds(Set<String> source, String field) {
        Objects.requireNonNull(source, field);
        List<String> sorted = new ArrayList<>();
        source.forEach(id -> sorted.add(requireId(id)));
        sorted.sort(String::compareTo);
        return Set.copyOf(new LinkedHashSet<>(sorted));
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        String normalized = id.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Rule state id cannot be blank");
        return normalized;
    }

    private static BigDecimal normalize(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : normalized;
    }
}
