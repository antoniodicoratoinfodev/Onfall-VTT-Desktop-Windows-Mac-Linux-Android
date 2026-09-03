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
    void statFormulaFieldsTakePrecedenceOverLegacyLimitAliases() {
        CompiledRuleset rules = revision("test:stat-limits", List.of(
                entity("test:stat:focus", RuleKind.STAT, Map.of(
                        "defaultFormula", "10",
                        "minimum", "1",
                        "minimumFormula", "2 + 1",
                        "maximum", "20",
                        "maximumFormula", "30")))).compile();

        CompiledRuleset.StatDefinition stat = rules.stats().get("test:stat:focus");

        assertEquals(new BigDecimal("3"), stat.minimumFormula().evaluate(
                RuleFormula.context(Map.of(), Map.of())));
        assertEquals(new BigDecimal("30"), stat.maximumFormula().evaluate(
                RuleFormula.context(Map.of(), Map.of())));

        CompiledRuleset withoutLimit = revision("test:stat-no-limit", List.of(
                entity("test:stat:focus", RuleKind.STAT, Map.of(
                        "defaultFormula", "10", "minimum", "1", "minimumFormula", ""))))
                .compile();
        assertEquals(null, withoutLimit.stats().get("test:stat:focus").minimumFormula());
    }

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
    void conditionEffectsClampHugeFormulaResultsWithoutIntegerOverflow() {
        List<RuleEntity> entities = List.of(
                entity("test:condition:marked", RuleKind.CONDITION, Map.of("maximumStacks", "3")),
                entity("test:effect:mark", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:condition:marked", "targetRef", "test:condition:marked",
                        "application", "ADD_CONDITION", "operation", "SET",
                        "valueFormula", "999999999999999999999999")),
                entity("test:action:mark", RuleKind.ACTION, Map.of("effectRefs", "test:effect:mark")));
        CompiledRuleset rules = revision("test:large-condition", entities).compile();

        RuleExecutionResult result = rules.executeAction(
                "test:action:mark", rules.initialState(Map.of(), Set.of()));

        assertEquals(3, result.state().conditionStacks().get("test:condition:marked"));
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

    @Test
    void initialStateRejectsUnknownValuesAndBrokenTrainedSkillMarkers() {
        CompiledRuleset rules = revision("test:initial-integrity", List.of(
                entity("test:stat:focus", RuleKind.STAT, Map.of("defaultFormula", "1")),
                entity("test:skill:notice", RuleKind.SKILL, Map.of("statRef", "test:stat:focus"))))
                .compile();

        assertThrows(IllegalArgumentException.class, () -> rules.initialState(
                Map.of("test:typo", RuleValue.number(2)), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> rules.initialState(
                Map.of("test:stat:focus", RuleValue.bool(true)), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> rules.initialState(
                Map.of("level:test:class", RuleValue.number(new BigDecimal("1.5"))), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> rules.initialState(
                Map.of("level:test:class", RuleValue.number(new BigDecimal("2147483648"))), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> rules.initialState(
                Map.of(), Set.of("trained:test:skill:missing")));

        RuleRuntimeState valid = rules.initialState(
                Map.of("context:rank", RuleValue.number(2), "level:test:class", RuleValue.number(12)),
                Set.of("trained:test:skill:notice"));
        assertTrue(valid.activeRuleIds().contains("trained:test:skill:notice"));
    }

    @Test
    void experienceProgressionsMustHaveAnExplicitDefaultAndRemainMonotonic() {
        IllegalArgumentException decreasing = assertThrows(IllegalArgumentException.class,
                () -> revision("test:decreasing-xp", List.of(
                        entity("test:xp", RuleKind.TABLE, Map.of(
                                "rows", "0=1;100=3;200=2", "lookup", "FLOOR", "valueType", "NUMBER")),
                        entity("test:progression", RuleKind.PROGRESSION, Map.of(
                                "experienceTableRef", "test:xp", "maximumLevel", "3"))))
                        .compile());
        assertTrue(decreasing.getMessage().contains("must not decrease"));

        IllegalArgumentException ambiguous = assertThrows(IllegalArgumentException.class,
                () -> revision("test:ambiguous-xp", List.of(
                        entity("test:xp", RuleKind.TABLE, Map.of(
                                "rows", "0=1;100=2", "lookup", "FLOOR", "valueType", "NUMBER")),
                        entity("test:progression:a", RuleKind.PROGRESSION, Map.of(
                                "experienceTableRef", "test:xp", "maximumLevel", "2")),
                        entity("test:progression:b", RuleKind.PROGRESSION, Map.of(
                                "experienceTableRef", "test:xp", "maximumLevel", "2"))))
                        .compile());
        assertTrue(ambiguous.getMessage().contains("defaultExperience"));
    }

    @Test
    void compilesSheetHealthMovementSceneAndStatePoliciesAsExecutableContracts() {
        List<RuleEntity> entities = List.of(
                entity("open:value:stress", RuleKind.VALUE, Map.of(
                        "valueType", "NUMBER", "defaultValue", "0", "dimension", "POINTS",
                        "canonicalUnit", "stress", "lifetime", "SCENE", "owner", "ACTOR",
                        "syncPolicy", "PROPOSE")),
                entity("open:resource:health", RuleKind.RESOURCE, Map.of(
                        "maximumFormula", "10", "initialFormula", "10")),
                entity("open:resource:guard", RuleKind.TRACK, Map.of(
                        "maximumFormula", "3", "initialFormula", "3")),
                entity("open:condition:down", RuleKind.CONDITION, Map.of(
                        "maximumStacks", "1", "stacking", "REPLACE")),
                entity("open:health", RuleKind.HEALTH_MODEL, Map.of(
                        "primaryResourceRef", "open:resource:health",
                        "bufferResourceRefs", "open:resource:guard",
                        "zeroConditionRef", "open:condition:down", "zeroState", "DISABLED")),
                entity("open:movement", RuleKind.MOVEMENT, Map.of(
                        "topology", "HEX_POINTY", "diagonalRule", "UNIFORM",
                        "unitsPerCell", "2", "canonicalUnit", "m", "elevation", "true")),
                entity("open:action:recover", RuleKind.ACTION, Map.of("conditionFormula", "1")),
                entity("open:sheet:vitals", RuleKind.SHEET_SECTION, Map.of(
                        "fieldRefs", "open:resource:health,open:value:stress",
                        "layout", "GRID", "columns", "2", "order", "10")),
                entity("open:scene:challenge", RuleKind.SCENE_PROCEDURE, Map.of(
                        "phases", "SETUP,PLAY,AFTERMATH",
                        "actionRefs", "open:action:recover",
                        "trackerRefs", "open:value:stress,open:resource:health")));

        CompiledRuleset rules = revision("open:system", entities).compile();

        assertTrue(rules.capabilities().healthModels());
        assertTrue(rules.capabilities().movementModels());
        assertTrue(rules.capabilities().sheetSections());
        assertTrue(rules.capabilities().sceneProcedures());
        assertTrue(rules.capabilities().statePolicies());
        assertEquals(CompiledRuleset.BoardTopology.HEX_POINTY,
                rules.movementModels().get("open:movement").topology());
        assertEquals(List.of("open:resource:health", "open:value:stress"),
                rules.sheetSections().get("open:sheet:vitals").fieldRefs());
        assertEquals("POINTS", rules.valueDefinitions().get("open:value:stress").dimension());
        assertEquals(StatePersistencePolicy.SyncPolicy.PROPOSE,
                rules.persistencePolicy("open:value:stress").syncPolicy());
    }

    @Test
    void lifecycleEventsExpireOnlyStateDeclaredForThatBoundary() {
        List<RuleEntity> entities = List.of(
                entity("life:value:scene", RuleKind.VALUE, Map.of(
                        "valueType", "NUMBER", "defaultValue", "1", "lifetime", "SCENE")),
                entity("life:resource:scene", RuleKind.RESOURCE, Map.of(
                        "maximumFormula", "5", "initialFormula", "5", "lifetime", "SCENE")),
                entity("life:condition:turn", RuleKind.CONDITION, Map.of(
                        "maximumStacks", "3", "lifetime", "TURN")),
                entity("life:value:permanent", RuleKind.VALUE, Map.of(
                        "valueType", "TEXT", "defaultValue", "A", "allowedValues", "A,B")));
        CompiledRuleset rules = revision("life:rules", entities).compile();
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of());
        state = rules.setRuleValue("life:value:scene", RuleValue.number(9), state);
        state = rules.setResource("life:resource:scene", new BigDecimal("2"), new BigDecimal("5"), state);
        state = rules.setConditionStacks("life:condition:turn", 2, state);
        state = rules.setRuleValue("life:value:permanent", RuleValue.text("B"), state);

        RuleExecutionResult sceneEnded = rules.fireEvent("SCENE_ENDED", state);

        assertEquals(RuleValue.number(1), rules.ruleValue("life:value:scene", sceneEnded.state()));
        assertEquals(new BigDecimal("5"), sceneEnded.state().resources().get("life:resource:scene").current());
        assertEquals(2, sceneEnded.state().conditionStacks().get("life:condition:turn"));
        assertEquals(RuleValue.text("B"), rules.ruleValue("life:value:permanent", sceneEnded.state()));
        assertEquals(2, sceneEnded.events().stream().filter(event -> event.type().equals("STATE_EXPIRED")).count());

        RuleExecutionResult turnEnded = rules.fireEvent("TURN_ENDED", sceneEnded.state());
        assertFalse(turnEnded.state().conditionStacks().containsKey("life:condition:turn"));
    }

    @Test
    void multiTargetActionsPayOnceAndApplyTargetEffectsToEverySelectedScope() {
        List<RuleEntity> entities = List.of(
                entity("many:value:mark", RuleKind.VALUE, Map.of(
                        "valueType", "NUMBER", "defaultValue", "0")),
                entity("many:turn", RuleKind.ACTION_ECONOMY, Map.of("budgets", "action=1")),
                entity("many:effect:mark", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "many:value:mark", "targetRef", "many:value:mark",
                        "application", "CHANGE_VALUE", "recipient", "TARGET",
                        "operation", "ADD", "valueFormula", "1")),
                entity("many:action:mark", RuleKind.ACTION, Map.of(
                        "costs", "turn:action=1", "effectRefs", "many:effect:mark")));
        CompiledRuleset rules = revision("many:rules", entities).compile();
        RuleRuntimeState initial = rules.initialState(Map.of(), Set.of());
        RuleScope source = RuleScope.actor("source");
        RuleScope first = RuleScope.actor("first");
        RuleScope second = RuleScope.actor("second");
        Map<RuleScope, RuleRuntimeState> frame = Map.of(
                RuleScope.session(), initial,
                source, initial,
                first, initial,
                second, initial);

        ScopedRuleExecutionResult result = rules.executeScopedActionToTargets(
                "many:action:mark", source, List.of(first, second), frame);

        assertEquals(BigDecimal.ZERO, result.state(source).turnBudget().get("action"));
        assertEquals(new BigDecimal("1"), rules.value("many:value:mark", result.state(first)));
        assertEquals(new BigDecimal("1"), rules.value("many:value:mark", result.state(second)));
        assertEquals(BigDecimal.ZERO, rules.value("many:value:mark", result.state(RuleScope.session())));
        assertEquals(2, result.events().stream().filter(event -> event.type().equals("VALUE_CHANGED")).count());
    }

    @Test
    void editorCapabilitiesMatchTheCompilerContracts() {
        assertTrue(RulesetCompiler.isDirectNumericFormulaReferenceTarget(
                entity("cap:stat", RuleKind.STAT, Map.of())));
        assertTrue(RulesetCompiler.isDirectNumericFormulaReferenceTarget(
                entity("cap:boolean", RuleKind.VALUE, Map.of("valueType", "boolean"))));
        assertFalse(RulesetCompiler.isDirectNumericFormulaReferenceTarget(
                entity("cap:text", RuleKind.VALUE, Map.of("valueType", "TEXT"))));
        assertFalse(RulesetCompiler.isDirectNumericFormulaReferenceTarget(
                entity("cap:resource", RuleKind.RESOURCE, Map.of())));
        RuleEntity manualStat = new RuleEntity(
                "cap:manual", RuleKind.STAT, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.bilingual("Manual", "Manual"),
                LocalizedRuleText.bilingual("Test", "Test"), "", true,
                RuleAutomationLevel.MANUAL, Map.of(), List.of(), "Test", "", 0);
        assertFalse(RulesetCompiler.isDirectNumericFormulaReferenceTarget(manualStat));

        assertTrue(RulesetCompiler.supportsStatePolicy(RuleKind.RESOURCE));
        assertTrue(RulesetCompiler.supportsStatePolicy(RuleKind.CONDITION));
        assertFalse(RulesetCompiler.supportsStatePolicy(RuleKind.MODIFIER));
        assertFalse(RulesetCompiler.supportsStatePolicy(RuleKind.RANDOMIZER));
        assertFalse(RulesetCompiler.supportsStatePolicy(RuleKind.ROLL));
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
