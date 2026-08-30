package app.d6d.rules.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesetCompilerTest {

    @Test
    void executesSyntheticThreePointFiveLikeRulesWithoutEditionSpecificCode() {
        List<RuleEntity> entities = List.of(
                entity("test:stat:strength", RuleKind.STAT, Map.of(
                        "statId", "STRENGTH", "defaultFormula", "10",
                        "modifierFormula", "floor((${score} - 10) / 2)")),
                entity("test:stat:armor-class", RuleKind.DEFENSE, Map.of(
                        "defaultFormula", "10", "derivedFormula", "10 + ${test:stat:strength:modifier}")),
                entity("test:skill:climb", RuleKind.SKILL, Map.of(
                        "statRef", "STRENGTH",
                        "formula", "${test:stat:strength:modifier} + ${context:ranks:climb}")),
                entity("test:table:experience", RuleKind.TABLE, Map.of(
                        "rows", "0=1;1000=2;3000=3;6000=4", "lookup", "FLOOR")),
                entity("test:progression", RuleKind.PROGRESSION, Map.of(
                        "experienceTableRef", "test:table:experience", "maximumCharacterLevel", "4")),
                entity("test:turn", RuleKind.ACTION_ECONOMY, Map.of(
                        "budgets", "standard=1;move=1;swift=1;immediate=1")),
                entity("test:resource:stamina", RuleKind.RESOURCE, Map.of(
                        "maximumFormula", "10", "initialFormula", "5", "recoveryEvent", "REST_COMPLETED")),
                entity("test:damage:void", RuleKind.DAMAGE_TYPE, Map.of()),
                entity("test:condition:bleeding", RuleKind.CONDITION, Map.of("maximumStacks", "5")),
                entity("test:value:alert", RuleKind.VALUE, Map.of(
                        "valueType", "BOOLEAN", "defaultValue", "false", "mutable", "true")),
                entity("test:effect:recover", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:resource:stamina",
                        "targetRef", "test:resource:stamina", "application", "CHANGE_RESOURCE",
                        "operation", "ADD", "valueFormula", "2")),
                entity("test:effect:bleed", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:condition:bleeding",
                        "targetRef", "test:condition:bleeding", "application", "ADD_CONDITION",
                        "operation", "ADD", "valueFormula", "1")),
                entity("test:effect:alert", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:value:alert",
                        "targetRef", "test:value:alert", "application", "SET_VALUE",
                        "valueType", "BOOLEAN", "valueLiteral", "true")),
                entity("test:action:second-wind", RuleKind.ACTION, Map.of(
                        "costs", "turn:swift=1",
                        "effectRefs", "test:effect:recover,test:effect:bleed")),
                entity("test:trigger:rest-focus", RuleKind.TRIGGER, Map.of(
                        "event", "REST_COMPLETED", "effectRefs", "test:effect:recover",
                        "maximumExecutions", "1")),
                entity("test:trigger:bleeding-alert", RuleKind.TRIGGER, Map.of(
                        "event", "CONDITION_ADDED", "effectRefs", "test:effect:alert")));
        CompiledRuleset rules = revision("test:3.5", entities).compile();
        RuleRuntimeState state = rules.initialState(Map.of(
                "test:stat:strength", RuleValue.number(18),
                "context:ranks:climb", RuleValue.number(5)), Set.of());

        assertEquals(new BigDecimal("4"), rules.value("test:stat:strength:modifier", state));
        assertEquals(new BigDecimal("14"), rules.value("test:stat:armor-class", state));
        assertEquals(new BigDecimal("9"), rules.value("test:skill:climb", state));
        assertEquals(3, rules.levelForExperience(new BigDecimal("4500")));
        assertEquals(0, new BigDecimal("1000").compareTo(rules.experienceForLevel(2)));
        assertTrue(rules.damageTypes().contains("test:damage:void"));
        assertTrue(rules.conditions().contains("test:condition:bleeding"));

        RuleExecutionResult action = rules.executeAction("test:action:second-wind", state);
        assertEquals(new BigDecimal("7"), action.state().resources().get("test:resource:stamina").current());
        assertEquals(BigDecimal.ZERO, action.state().turnBudget().get("swift"));
        assertEquals(RuleValue.bool(true), rules.ruleValue("test:value:alert", action.state()));
        assertTrue(action.events().stream().anyMatch(event -> event.type().equals("ACTION_EXECUTED")));
        assertTrue(action.events().stream().anyMatch(event -> event.type().equals("TRIGGER_FIRED")));

        RuleExecutionResult rest = rules.fireEvent("REST_COMPLETED", action.state());
        assertEquals(0, new BigDecimal("10").compareTo(
                rest.state().resources().get("test:resource:stamina").current()));
        assertTrue(rest.events().stream().anyMatch(event -> event.type().equals("TRIGGER_FIRED")));
        assertTrue(rules.capabilities().actionEconomy());
        assertTrue(rules.capabilities().triggers());
    }

    @Test
    void supportsA_nonD20DicePoolAndCustomTurnCurrency() {
        List<RuleEntity> entities = List.of(
                entity("story:stat:focus", RuleKind.STAT, Map.of("defaultFormula", "3")),
                entity("story:value:stance", RuleKind.VALUE, Map.of(
                        "valueType", "TEXT", "defaultValue", "CALM",
                        "allowedValues", "CALM,RISKY", "mutable", "true",
                        "activeByDefault", "true")),
                entity("story:effect:risk", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "story:value:stance",
                        "targetRef", "story:value:stance", "application", "SET_VALUE",
                        "valueType", "TEXT", "valueLiteral", "RISKY")),
                entity("story:action:take-risk", RuleKind.ACTION,
                        Map.of("effectRefs", "story:effect:risk")),
                entity("story:randomizer:risk", RuleKind.RANDOMIZER, Map.of(
                        "mode", "DICE_POOL", "countFormula", "${story:stat:focus}",
                        "sidesFormula", "6", "keep", "SUCCESSES", "successThresholdFormula", "5")),
                entity("story:turn", RuleKind.ACTION_ECONOMY, Map.of("budgets", "spotlight=2")));
        CompiledRuleset rules = revision("test:story", entities).compile();
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of());
        int[] draws = {4, 5, 0}; // Risultati visibili 5, 6, 1: due successi.
        AtomicInteger cursor = new AtomicInteger();

        CompiledRuleset.RandomizerResult result = rules.roll(
                "story:randomizer:risk", state, bound -> draws[cursor.getAndIncrement()]);

        assertEquals(List.of(5, 6, 1), result.draws());
        assertEquals(new BigDecimal("2"), result.value());
        assertEquals(new BigDecimal("2"), state.turnBudget().get("spotlight"));
        assertEquals(RuleValue.text("CALM"), rules.ruleValue("story:value:stance", state));
        assertTrue(rules.isRuleActive("story:value:stance", state));
        RuleExecutionResult changed = rules.executeAction("story:action:take-risk", state);
        assertEquals(RuleValue.text("RISKY"),
                rules.ruleValue("story:value:stance", changed.state()));
        assertTrue(changed.events().stream().anyMatch(event -> event.type().equals("VALUE_SET")));
        assertTrue(rules.capabilities().randomizers());
        assertTrue(rules.capabilities().typedValues());
        assertFalse(rules.capabilities().dynamicDamageTypes());
    }

    @Test
    void publicationCompilerRejectsBrokenLinksAndFormulaCycles() {
        List<RuleEntity> broken = List.of(
                entity("test:stat:a", RuleKind.STAT, Map.of("derivedFormula", "${test:stat:b} + 1")),
                entity("test:stat:b", RuleKind.STAT, Map.of("derivedFormula", "${test:stat:a} + 1")));
        IllegalArgumentException cycle = assertThrows(IllegalArgumentException.class,
                () -> revision("test:cycle", broken).compile());
        assertTrue(cycle.getMessage().contains("Cyclic"));

        List<RuleEntity> modifierCycle = List.of(
                entity("test:value:pool", RuleKind.VALUE, Map.of(
                        "valueType", "NUMBER", "defaultValue", "1")),
                entity("test:stat:derived", RuleKind.STAT, Map.of(
                        "derivedFormula", "${test:value:pool}")),
                entity("test:modifier:pool", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:stat:derived", "targetRef", "test:value:pool",
                        "application", "STATIC", "valueFormula", "${test:stat:derived}")));
        IllegalArgumentException modifierFailure = assertThrows(IllegalArgumentException.class,
                () -> revision("test:modifier-cycle", modifierCycle).compile());
        assertTrue(modifierFailure.getMessage().contains("Cyclic"));

        RuleEntity link = entity("test:text", RuleKind.TEXT_RULE, Map.of("links", "missing:rule"));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> revision("test:broken", List.of(link)).compile());
        assertTrue(missing.getMessage().contains("missing enabled rule"));

        List<RuleEntity> invalidRecipient = List.of(
                entity("test:owner", RuleKind.FEATURE, Map.of()),
                entity("test:stat", RuleKind.STAT, Map.of("defaultFormula", "1")),
                entity("test:modifier", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:owner", "targetRef", "test:stat",
                        "application", "STATIC", "recipient", "TARGET", "valueFormula", "1")));
        IllegalArgumentException recipientFailure = assertThrows(IllegalArgumentException.class,
                () -> revision("test:recipient", invalidRecipient).compile());
        assertTrue(recipientFailure.getMessage().contains("static modifier must target SELF"));
    }

    @Test
    void publicationCompilerRejectsTypedValuesOutsideTheirDeclaredDomain() {
        List<RuleEntity> entities = List.of(
                entity("test:value:stance", RuleKind.VALUE, Map.of(
                        "valueType", "TEXT", "defaultValue", "CALM",
                        "allowedValues", "CALM,RISKY", "mutable", "true")),
                entity("test:effect:invalid-stance", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:value:stance",
                        "targetRef", "test:value:stance", "application", "SET_VALUE",
                        "valueType", "TEXT", "valueLiteral", "IMPOSSIBLE")));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> revision("test:invalid-value", entities).compile());

        assertTrue(failure.getMessage().contains("valueLiteral"));
        assertTrue(failure.getMessage().contains("invalid"));
    }

    @Test
    void actionCostsAreAggregatedAcrossAliasesOfTheSameResource() {
        List<RuleEntity> entities = List.of(
                entity("test:resource:mana", RuleKind.RESOURCE, Map.of(
                        "resourceId", "MANA", "maximumFormula", "5", "initialFormula", "5")),
                entity("test:action:overspend", RuleKind.ACTION, Map.of(
                        "costs", "resource:MANA=3;resource:test:resource:mana=3")));
        CompiledRuleset rules = revision("test:aggregate-costs", entities).compile();
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> rules.executeAction("test:action:overspend", state));

        assertTrue(failure.getMessage().contains("Not enough"));
        assertEquals(new BigDecimal("5"), state.resources().get("test:resource:mana").current());
        assertThrows(IllegalStateException.class, () -> rules.executeScopedAction(
                "test:action:overspend", RuleScope.session(), RuleScope.session(),
                Map.of(RuleScope.session(), state)));
    }

    @Test
    void numericSkillOverridesAreRealAndExecutableEffectsCanChangeThem() {
        List<RuleEntity> entities = List.of(
                entity("test:stat:focus", RuleKind.STAT, Map.of(
                        "defaultFormula", "3", "modifierFormula", "${score}")),
                entity("test:skill:ritual", RuleKind.SKILL, Map.of(
                        "statRef", "test:stat:focus", "formula", "${test:stat:focus}")),
                entity("test:effect:improve-ritual", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:skill:ritual", "targetRef", "test:skill:ritual",
                        "application", "CHANGE_VALUE", "operation", "ADD", "valueFormula", "2")),
                entity("test:action:practice", RuleKind.ACTION,
                        Map.of("effectRefs", "test:effect:improve-ritual")));
        CompiledRuleset rules = revision("test:skill-override", entities).compile();
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of());

        RuleRuntimeState overridden = rules.setNumericValue(
                "test:skill:ritual", new BigDecimal("10"), state);
        RuleExecutionResult practiced = rules.executeAction("test:action:practice", overridden);

        assertEquals(new BigDecimal("10"), rules.value("test:skill:ritual", overridden));
        assertEquals(new BigDecimal("12"), rules.value("test:skill:ritual", practiced.state()));
        assertTrue(practiced.events().stream().anyMatch(event -> event.type().equals("VALUE_CHANGED")));
    }

    @Test
    void effectsThatDoNotChangeStateDoNotEmitFalseChangeEvents() {
        List<RuleEntity> entities = List.of(
                entity("test:value:stance", RuleKind.VALUE, Map.of(
                        "valueType", "TEXT", "defaultValue", "CALM", "mutable", "true")),
                entity("test:resource:guard", RuleKind.RESOURCE, Map.of(
                        "maximumFormula", "5", "initialFormula", "5")),
                entity("test:condition:marked", RuleKind.CONDITION, Map.of("maximumStacks", "1")),
                entity("test:condition:absent", RuleKind.CONDITION, Map.of()),
                entity("test:effect:same-stance", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:value:stance", "targetRef", "test:value:stance",
                        "application", "SET_VALUE", "valueType", "TEXT", "valueLiteral", "CALM")),
                entity("test:effect:full-guard", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:resource:guard", "targetRef", "test:resource:guard",
                        "application", "CHANGE_RESOURCE", "operation", "ADD", "valueFormula", "2")),
                entity("test:effect:max-mark", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:condition:marked", "targetRef", "test:condition:marked",
                        "application", "ADD_CONDITION", "operation", "ADD", "valueFormula", "1")),
                entity("test:effect:remove-absent", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:condition:absent", "targetRef", "test:condition:absent",
                        "application", "REMOVE_CONDITION", "operation", "SET", "valueFormula", "0")),
                entity("test:action:no-op", RuleKind.ACTION, Map.of(
                        "effectRefs", "test:effect:same-stance,test:effect:full-guard,"
                                + "test:effect:max-mark,test:effect:remove-absent")),
                entity("test:trigger:false-positive", RuleKind.TRIGGER, Map.of(
                        "event", "VALUE_SET", "effectRefs", "test:effect:remove-absent")));
        CompiledRuleset rules = revision("test:no-op-effects", entities).compile();
        RuleRuntimeState state = rules.setConditionStacks(
                "test:condition:marked", 1, rules.initialState(Map.of(), Set.of()));

        RuleExecutionResult result = rules.executeAction("test:action:no-op", state);

        assertEquals(state, result.state());
        assertEquals(List.of("ACTION_EXECUTED"), result.events().stream().map(RuleRuntimeEvent::type).toList());
    }

    @Test
    void resourceAndTurnInitializationFollowDependenciesInsteadOfLexicalOrder() {
        List<RuleEntity> entities = List.of(
                entity("test:resource:a", RuleKind.RESOURCE, Map.of(
                        "maximumFormula", "${resource:test:resource:z:maximum} + 1",
                        "initialFormula", "${maximum}")),
                entity("test:resource:z", RuleKind.RESOURCE, Map.of(
                        "maximumFormula", "4", "initialFormula", "2")),
                entity("test:turn", RuleKind.ACTION_ECONOMY, Map.of(
                        "budgets", "a=${turn:z} + 1;z=2")));
        CompiledRuleset rules = revision("test:dependent-pools", entities).compile();

        RuleRuntimeState state = rules.initialState(Map.of(), Set.of());

        assertEquals(new BigDecimal("5"), state.resources().get("test:resource:a").maximum());
        assertEquals(new BigDecimal("5"), state.resources().get("test:resource:a").current());
        assertEquals(new BigDecimal("3"), state.turnBudget().get("a"));
        assertEquals(new BigDecimal("2"), state.turnBudget().get("z"));
    }

    @Test
    void publicationRejectsMissingConditionAndTurnReferencesAndPoolCycles() {
        IllegalArgumentException missingCondition = assertThrows(IllegalArgumentException.class,
                () -> revision("test:missing-condition", List.of(
                        entity("test:stat", RuleKind.STAT,
                                Map.of("defaultFormula", "${condition:missing:stacks}"))))
                        .compile());
        assertTrue(missingCondition.getMessage().contains("condition"));

        IllegalArgumentException missingTurn = assertThrows(IllegalArgumentException.class,
                () -> revision("test:missing-turn", List.of(
                        entity("test:stat", RuleKind.STAT,
                                Map.of("defaultFormula", "${turn:missing}"))))
                        .compile());
        assertTrue(missingTurn.getMessage().contains("turn resource"));

        IllegalArgumentException resourceCycle = assertThrows(IllegalArgumentException.class,
                () -> revision("test:resource-cycle", List.of(
                        entity("test:resource:a", RuleKind.RESOURCE,
                                Map.of("maximumFormula", "${resource:test:resource:b:maximum}")),
                        entity("test:resource:b", RuleKind.RESOURCE,
                                Map.of("maximumFormula", "${resource:test:resource:a:maximum}"))))
                        .compile());
        assertTrue(resourceCycle.getMessage().contains("Cyclic"));

        IllegalArgumentException turnCycle = assertThrows(IllegalArgumentException.class,
                () -> revision("test:turn-cycle", List.of(
                        entity("test:turn", RuleKind.ACTION_ECONOMY,
                                Map.of("budgets", "a=${turn:b};b=${turn:a}"))))
                        .compile());
        assertTrue(turnCycle.getMessage().contains("Cyclic"));
    }

    @Test
    void suppliedReferenceValuesMustPointToAnEnabledRule() {
        List<RuleEntity> entities = List.of(
                entity("test:target", RuleKind.FEATURE, Map.of()),
                entity("test:value:target", RuleKind.VALUE, Map.of(
                        "valueType", "REFERENCE", "defaultValue", "test:target", "mutable", "true")));
        CompiledRuleset rules = revision("test:reference-state", entities).compile();

        assertThrows(IllegalArgumentException.class, () -> rules.initialState(
                Map.of("test:value:target", RuleValue.reference("test:missing")), Set.of()));
    }

    private static RulesetRevision revision(String projectId, List<RuleEntity> entities) {
        return RulesetRevision.create(projectId, projectId + ":revision:1", "1.0.0", projectId,
                "Synthetic test ruleset", RulesetOrigin.HOMEBREW, "",
                RulesetRuntimeConfig.standardSrd521(), entities, "2026-08-30T00:00:00Z");
    }

    private static RuleEntity entity(String id, RuleKind kind, Map<String, String> attributes) {
        return new RuleEntity(id, kind, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.bilingual(id, id), LocalizedRuleText.bilingual("Test", "Test"), "",
                true, RuleAutomationLevel.FULL, attributes, List.of("test"), "Test", "", 0);
    }
}
