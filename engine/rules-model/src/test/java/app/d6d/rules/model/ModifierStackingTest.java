package app.d6d.rules.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierStackingTest {
    private static final String TARGET = "test:defense:armor";

    @Test
    void groupedModifiersKeepTheLegacyHighestPriorityDefault() {
        CompiledRuleset rules = compile(
                stat(TARGET, "10"),
                owner("owner:low"), owner("owner:high"),
                modifier("modifier:low", "owner:low", "1", Map.of(
                        "group", "armor", "priority", "1")),
                modifier("modifier:high", "owner:high", "3", Map.of(
                        "group", "armor", "priority", "2")));
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of("owner:low", "owner:high"));

        CompiledRuleset.RuleValueTrace trace = rules.valueTrace(TARGET, state);

        assertEquals(new BigDecimal("13"), rules.value(TARGET, state));
        assertEquals(new BigDecimal("13"), trace.resultValue());
        assertEquals(CompiledRuleset.ModifierStacking.HIGHEST_PRIORITY,
                rules.modifiers().get("modifier:low").stacking());
        assertEquals(CompiledRuleset.ModifierDecision.LOWER_PRIORITY,
                step(trace, "modifier:low").decision());
        assertEquals(CompiledRuleset.ModifierDecision.APPLIED,
                step(trace, "modifier:high").decision());
    }

    @Test
    void threePointFiveStyleTypedBonusesKeepBestBonusWorstPenaltyAndStackDodge() {
        CompiledRuleset rules = compile(
                stat(TARGET, "10"),
                owner("owner:armor-4"), owner("owner:armor-2"),
                owner("owner:armor-minus-1"), owner("owner:armor-minus-3"),
                owner("owner:dodge-1"), owner("owner:dodge-2"),
                modifier("modifier:armor-4", "owner:armor-4", "4", typed("armor")),
                modifier("modifier:armor-2", "owner:armor-2", "2", typed("armor")),
                modifier("modifier:armor-minus-1", "owner:armor-minus-1", "-1", typed("armor")),
                modifier("modifier:armor-minus-3", "owner:armor-minus-3", "-3", typed("armor")),
                modifier("modifier:dodge-1", "owner:dodge-1", "1", stack("dodge")),
                modifier("modifier:dodge-2", "owner:dodge-2", "2", stack("dodge")));
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of(
                "owner:armor-4", "owner:armor-2", "owner:armor-minus-1", "owner:armor-minus-3",
                "owner:dodge-1", "owner:dodge-2"));

        CompiledRuleset.RuleValueTrace trace = rules.valueTrace(TARGET, state);

        assertEquals(new BigDecimal("14"), trace.resultValue());
        assertEquals(CompiledRuleset.ModifierDecision.APPLIED, step(trace, "modifier:armor-4").decision());
        assertEquals(CompiledRuleset.ModifierDecision.LOWER_VALUE, step(trace, "modifier:armor-2").decision());
        assertEquals(CompiledRuleset.ModifierDecision.HIGHER_VALUE,
                step(trace, "modifier:armor-minus-1").decision());
        assertEquals(CompiledRuleset.ModifierDecision.APPLIED,
                step(trace, "modifier:armor-minus-3").decision());
        assertEquals(CompiledRuleset.ModifierDecision.APPLIED, step(trace, "modifier:dodge-1").decision());
        assertEquals(CompiledRuleset.ModifierDecision.APPLIED, step(trace, "modifier:dodge-2").decision());
    }

    @Test
    void highestLowestAndUniqueSourcePoliciesAreDeterministic() {
        CompiledRuleset rules = compile(
                stat(TARGET, "10"),
                owner("owner:morale-2"), owner("owner:morale-5"),
                owner("owner:penalty-1"), owner("owner:penalty-4"),
                owner("owner:spell-a-low"), owner("owner:spell-a-high"), owner("owner:spell-b"),
                modifier("modifier:morale-2", "owner:morale-2", "2", policy("morale", "HIGHEST_VALUE")),
                modifier("modifier:morale-5", "owner:morale-5", "5", policy("morale", "HIGHEST_VALUE")),
                modifier("modifier:penalty-1", "owner:penalty-1", "-1", policy("penalty", "LOWEST_VALUE")),
                modifier("modifier:penalty-4", "owner:penalty-4", "-4", policy("penalty", "LOWEST_VALUE")),
                modifier("modifier:spell-a-low", "owner:spell-a-low", "1", Map.of(
                        "group", "spell", "stacking", "UNIQUE_SOURCE", "sourceRef", "source:spell-a",
                        "priority", "1")),
                modifier("modifier:spell-a-high", "owner:spell-a-high", "2", Map.of(
                        "group", "spell", "stacking", "UNIQUE_SOURCE", "sourceRef", "source:spell-a",
                        "priority", "2")),
                modifier("modifier:spell-b", "owner:spell-b", "3", Map.of(
                        "group", "spell", "stacking", "UNIQUE_SOURCE", "sourceRef", "source:spell-b")));
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of(
                "owner:morale-2", "owner:morale-5", "owner:penalty-1", "owner:penalty-4",
                "owner:spell-a-low", "owner:spell-a-high", "owner:spell-b"));

        CompiledRuleset.RuleValueTrace trace = rules.valueTrace(TARGET, state);

        assertEquals(new BigDecimal("16"), trace.resultValue()); // 10 + 5 - 4 + 2 + 3
        assertEquals(CompiledRuleset.ModifierDecision.LOWER_VALUE,
                step(trace, "modifier:morale-2").decision());
        assertEquals(CompiledRuleset.ModifierDecision.HIGHER_VALUE,
                step(trace, "modifier:penalty-1").decision());
        assertEquals(CompiledRuleset.ModifierDecision.DUPLICATE_SOURCE,
                step(trace, "modifier:spell-a-low").decision());
    }

    @Test
    void explicitPhasesOrderReplaceAddMultiplyLimitAndFinalIndependentlyOfPriority() {
        CompiledRuleset rules = compile(
                stat(TARGET, "10"),
                owner("owner:set"), owner("owner:add"), owner("owner:multiply"),
                owner("owner:limit"), owner("owner:final"),
                modifier("modifier:set", "owner:set", "5", Map.of(
                        "operation", "SET", "phase", "REPLACE", "priority", "100")),
                modifier("modifier:add", "owner:add", "3", Map.of(
                        "operation", "ADD", "phase", "ADDITIVE", "priority", "80")),
                modifier("modifier:multiply", "owner:multiply", "2", Map.of(
                        "operation", "MULTIPLY", "phase", "MULTIPLICATIVE", "priority", "60")),
                modifier("modifier:limit", "owner:limit", "14", Map.of(
                        "operation", "MAXIMUM", "phase", "LIMIT", "priority", "40")),
                modifier("modifier:final", "owner:final", "1", Map.of(
                        "operation", "ADD", "phase", "FINAL", "priority", "20")));
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of(
                "owner:set", "owner:add", "owner:multiply", "owner:limit", "owner:final"));

        CompiledRuleset.RuleValueTrace trace = rules.valueTrace(TARGET, state);

        assertEquals(new BigDecimal("15"), trace.resultValue());
        assertEquals(List.of(
                        CompiledRuleset.ModifierPhase.REPLACE,
                        CompiledRuleset.ModifierPhase.ADDITIVE,
                        CompiledRuleset.ModifierPhase.MULTIPLICATIVE,
                        CompiledRuleset.ModifierPhase.LIMIT,
                        CompiledRuleset.ModifierPhase.FINAL),
                trace.modifiers().stream().map(CompiledRuleset.ModifierTraceStep::phase).toList());
    }

    @Test
    void exclusiveGroupsFailWhenMoreThanOneCandidateIsActive() {
        CompiledRuleset rules = compile(
                stat(TARGET, "10"), owner("owner:first"), owner("owner:second"),
                modifier("modifier:first", "owner:first", "1", policy("stance", "EXCLUSIVE")),
                modifier("modifier:second", "owner:second", "2", policy("stance", "EXCLUSIVE")));
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of("owner:first", "owner:second"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> rules.value(TARGET, state));

        assertTrue(failure.getMessage().contains("Exclusive modifier group stance"));
    }

    @Test
    void compilerRejectsAmbiguousPoliciesAndInvalidPhaseContracts() {
        IllegalArgumentException mixedPolicies = assertThrows(IllegalArgumentException.class, () -> compile(
                stat(TARGET, "10"), owner("owner:first"), owner("owner:second"),
                modifier("modifier:first", "owner:first", "1", policy("armor", "HIGHEST_VALUE")),
                modifier("modifier:second", "owner:second", "2", policy("armor", "LOWEST_VALUE"))));
        assertTrue(mixedPolicies.getMessage().contains("different stacking policies"));

        IllegalArgumentException mixedPhases = assertThrows(IllegalArgumentException.class, () -> compile(
                stat(TARGET, "10"), owner("owner:first"), owner("owner:second"),
                modifier("modifier:first", "owner:first", "1", Map.of("phase", "ADDITIVE")),
                modifier("modifier:second", "owner:second", "2", Map.of())));
        assertTrue(mixedPhases.getMessage().contains("cannot mix LEGACY"));

        IllegalArgumentException invalidOperation = assertThrows(IllegalArgumentException.class, () -> compile(
                stat(TARGET, "10"), owner("owner:first"),
                modifier("modifier:first", "owner:first", "1", Map.of(
                        "operation", "ADD", "phase", "REPLACE"))));
        assertTrue(invalidOperation.getMessage().contains("invalid in phase REPLACE"));
    }

    @Test
    void numericValueDefinitionsActuallyReceiveStaticModifiersAndShareTheTracePipeline() {
        String valueId = "test:value:number";
        CompiledRuleset rules = compile(
                entity(valueId, RuleKind.VALUE, Map.of(
                        "valueType", "NUMBER", "defaultValue", "10", "mutable", "true")),
                owner("owner:value"),
                modifier("modifier:value", "owner:value", "2", Map.of("targetRef", valueId)));
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of("owner:value"));

        CompiledRuleset.RuleValueTrace trace = rules.valueTrace(valueId, state);

        assertEquals(new BigDecimal("12"), rules.value(valueId, state));
        assertEquals(rules.value(valueId, state), trace.resultValue());
        assertEquals(new BigDecimal("10"), trace.baseValue());
        assertEquals(CompiledRuleset.ModifierDecision.APPLIED, trace.modifiers().get(0).decision());
    }

    private static Map<String, String> typed(String group) {
        return policy(group, "HIGHEST_BONUS_AND_LOWEST_PENALTY");
    }

    private static Map<String, String> stack(String group) {
        return policy(group, "STACK");
    }

    private static Map<String, String> policy(String group, String policy) {
        return Map.of("group", group, "stacking", policy);
    }

    private static CompiledRuleset.ModifierTraceStep step(
            CompiledRuleset.RuleValueTrace trace,
            String id) {
        return trace.modifiers().stream().filter(candidate -> candidate.modifierId().equals(id))
                .findFirst().orElseThrow();
    }

    private static CompiledRuleset compile(RuleEntity... entities) {
        return RulesetRevision.create(
                "test", "revision:test", "1", "Test", "", RulesetOrigin.HOMEBREW,
                "", RulesetRuntimeConfig.standardSrd521(), List.of(entities), "now").compile();
    }

    private static RuleEntity stat(String id, String base) {
        return entity(id, RuleKind.DEFENSE, Map.of("defaultFormula", base));
    }

    private static RuleEntity owner(String id) {
        return entity(id, RuleKind.FEATURE, Map.of());
    }

    private static RuleEntity modifier(
            String id,
            String owner,
            String value,
            Map<String, String> changes) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("ownerRef", owner);
        attributes.put("targetRef", TARGET);
        attributes.put("application", "STATIC");
        attributes.put("operation", "ADD");
        attributes.put("valueFormula", value);
        attributes.putAll(changes);
        return entity(id, RuleKind.MODIFIER, attributes);
    }

    private static RuleEntity entity(String id, RuleKind kind, Map<String, String> attributes) {
        return new RuleEntity(
                id, kind, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.single("en", id), LocalizedRuleText.single("en", "Test"),
                "", true, RuleAutomationLevel.FULL, attributes, List.of("test"), "test", "", 0);
    }
}
