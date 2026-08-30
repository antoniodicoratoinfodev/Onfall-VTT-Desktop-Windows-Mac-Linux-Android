package app.d6d.engine;

import app.d6d.domain.combat.AttackOutcome;
import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.CombatStatus;
import app.d6d.domain.combat.EventType;
import app.d6d.rules.model.LocalizedRuleText;
import app.d6d.rules.model.RuleAutomationLevel;
import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleKind;
import app.d6d.rules.model.RuleScope;
import app.d6d.rules.model.RuleValue;
import app.d6d.rules.model.RulesetBinding;
import app.d6d.rules.model.RulesetOrigin;
import app.d6d.rules.model.RulesetRevision;
import app.d6d.rules.model.RulesetRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModularRulesetRuntimeTest {
    private static final RulesetBinding HOMEBREW = new RulesetBinding(
            "test:homebrew", "revision:1", "canonical:1", "runtime:1", "1", "Test Homebrew", false);
    private static final RulesetRuntimeConfig CONFIG = new RulesetRuntimeConfig(
            "1", 19, false, 4, 3, 10, 1, 3, 8);

    private CombatSession active(RulesetBinding binding, RulesetRuntimeConfig config) {
        CombatSession session = CombatSession.create("modular", 42L, binding, config, "test");
        session.addCombatant("hero", CombatFixtures.hero());
        session.addCombatant("goblin", CombatFixtures.goblin());
        session.setInitiative("hero", 20);
        session.setInitiative("goblin", 10);
        session.markReady();
        session.start();
        return session;
    }

    @Test
    void criticalThresholdAndNaturalOneComeFromTheSelectedRevision() {
        CombatSession critical = active(HOMEBREW, CONFIG);
        assertEquals(AttackOutcome.CRITICAL_HIT, critical.attack(
                AttackRequest.manual("hero", "goblin", "sword", 19, List.of(4))).outcome());

        CombatSession naturalOneCanHit = active(HOMEBREW, CONFIG);
        assertEquals(AttackOutcome.HIT, naturalOneCanHit.attack(
                AttackRequest.manual("hero", "goblin", "sword", 1, List.of(4))).outcome());
    }

    @Test
    void exhaustionUsesTheHomebrewMaximumAndPenaltiesEverywhere() {
        CombatSession session = active(HOMEBREW, CONFIG);
        session.setExhaustion("hero", 2);

        assertEquals(-6, session.currentState().combatant("hero").exhaustionD20Penalty());
        assertEquals(10, session.currentState().combatant("hero").effectiveSpeedFeet());
        assertEquals(10, session.currentState().turnBudgets().get("hero").movementAllowanceFeet());
        session.setExhaustion("hero", 4);
        assertTrue(session.currentState().combatant("hero").dead());
    }

    @Test
    void changingAnActiveSessionPausesItAndIsUndoableAndAudited() {
        CombatSession session = CombatFixtures.active(9L);

        session.changeRuleset(HOMEBREW, CONFIG);

        assertEquals(CombatStatus.PAUSED, session.currentState().status());
        assertEquals(HOMEBREW, session.currentState().rulesetBinding());
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.RULESET_CHANGED));
        assertTrue(session.undo());
        assertEquals(CombatStatus.ACTIVE, session.currentState().status());
        assertFalse(session.currentState().rulesetBinding().equals(HOMEBREW));
    }

    @Test
    void embeddedUniversalRulesExecuteAndUndoInsideARealSession() {
        RulesetRevision revision = executableRevision("revision:universal:1", 10, 2);
        CombatSession session = active(revision);

        assertEquals(revision.entities(), session.currentState().ruleSession().entities());
        assertEquals(new BigDecimal("2"),
                session.currentState().ruleSession().state().turnBudget().get("spotlight"));
        assertFalse(session.genericRuleActive("test:condition:exposed"));
        assertEquals(new BigDecimal("3"), session.genericRuleValue("test:stat:focus"));
        session.setGenericRuleActive("test:condition:exposed", true);
        assertTrue(session.genericRuleActive("test:condition:exposed"));
        assertEquals(new BigDecimal("4"), session.genericRuleValue("test:stat:focus"));
        assertTrue(session.auditTrail().stream()
                .anyMatch(event -> event.type() == EventType.RULE_ACTIVATION_CHANGED));
        assertTrue(session.undo());
        assertFalse(session.genericRuleActive("test:condition:exposed"));

        session.setGenericNumericRuleValue("test:stat:focus", new BigDecimal("5"));
        assertEquals(new BigDecimal("5"), session.genericRuleValue("test:stat:focus"));
        assertTrue(session.undo());
        assertEquals(new BigDecimal("3"), session.genericRuleValue("test:stat:focus"));

        session.setGenericResource("test:resource:momentum", new BigDecimal("2"), new BigDecimal("12"));
        assertEquals(new BigDecimal("12"), session.currentState().ruleSession().state()
                .resources().get("test:resource:momentum").maximum());
        assertTrue(session.undo());

        session.setGenericConditionStacks("test:condition:exposed", 2);
        assertEquals(2, session.currentState().ruleSession().state()
                .conditionStacks().get("test:condition:exposed"));
        assertTrue(session.undo());

        session.setGenericTurnResource("spotlight", new BigDecimal("5"));
        assertEquals(new BigDecimal("5"), session.currentState().ruleSession().state()
                .turnBudget().get("spotlight"));
        assertTrue(session.undo());
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.RULE_RESOURCE_SET));
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.RULE_CONDITION_SET));
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.RULE_TURN_RESOURCE_SET));

        session.executeRuleAction("test:action:push");
        assertEquals(new BigDecimal("4"), session.currentState().ruleSession().state()
                .resources().get("test:resource:momentum").current());
        assertEquals(new BigDecimal("1"),
                session.currentState().ruleSession().state().turnBudget().get("spotlight"));
        assertEquals(1, session.currentState().ruleSession().state()
                .conditionStacks().get("test:condition:exposed"));
        assertEquals(RuleValue.text("STORM"), session.genericTypedRuleValue("test:value:weather"));
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.RULE_ACTION_EXECUTED));

        assertTrue(session.undo());
        assertEquals(new BigDecimal("6"), session.currentState().ruleSession().state()
                .resources().get("test:resource:momentum").current());
        assertFalse(session.currentState().ruleSession().state().conditionStacks()
                .containsKey("test:condition:exposed"));
        assertEquals(RuleValue.text("CLEAR"), session.genericTypedRuleValue("test:value:weather"));

        session.executeRuleAction("test:action:push");
        session.fireRuleEvent("BATTLE_CRY");
        assertEquals(new BigDecimal("6"), session.currentState().ruleSession().state()
                .resources().get("test:resource:momentum").current());
        var roll = session.rollRuleRandomizer("test:randomizer:pool");
        assertEquals(3, roll.draws().size());
        assertTrue(roll.value().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.RULE_EVENT_FIRED));
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type() == EventType.RULE_RANDOMIZER_ROLLED));
    }

    @Test
    void changingRevisionMigratesSpentStateAndReapplyingSameRevisionIsANoOp() {
        RulesetRevision first = executableRevision("revision:universal:1", 10, 2);
        RulesetRevision second = executableRevision("revision:universal:2", 14, 3);
        CombatSession session = active(first);
        session.executeRuleAction("test:action:push"); // spende 2 momentum e 1 spotlight
        long beforeNoOp = session.currentState().revision();

        session.changeRuleset(first);

        assertEquals(beforeNoOp, session.currentState().revision());
        assertEquals(CombatStatus.ACTIVE, session.currentState().status());
        session.changeRuleset(second);

        assertEquals(CombatStatus.PAUSED, session.currentState().status());
        // Il deficit resta sei (quattro iniziali + due spesi): il nuovo massimo non cura la risorsa.
        assertEquals(new BigDecimal("8"), session.currentState().ruleSession().state()
                .resources().get("test:resource:momentum").current());
        assertEquals(new BigDecimal("2"),
                session.currentState().ruleSession().state().turnBudget().get("spotlight"));
        assertEquals(1, session.currentState().ruleSession().state()
                .conditionStacks().get("test:condition:exposed"));
        assertEquals(RuleValue.text("STORM"), session.genericTypedRuleValue("test:value:weather"));
        assertTrue(session.undo());
        assertEquals(CombatStatus.ACTIVE, session.currentState().status());
        assertEquals(first.canonicalHash(), session.currentState().rulesetBinding().canonicalHash());
    }

    @Test
    void identicalRulesHaveIndependentActorObjectSceneAndCampaignInstances() {
        RulesetRevision first = executableRevision("revision:scoped:1", 10, 2);
        RulesetRevision second = executableRevision("revision:scoped:2", 14, 3);
        CombatSession session = active(first);
        RuleScope hero = RuleScope.actor("hero");
        RuleScope goblin = RuleScope.actor("goblin");
        RuleScope relic = RuleScope.objectScope("relic:moon");
        RuleScope scene = RuleScope.scene("scene:crypt");
        RuleScope campaign = RuleScope.campaign("campaign:one");

        session.setGenericResource(hero, "test:resource:momentum", new BigDecimal("5"), new BigDecimal("10"));
        session.setGenericResource(goblin, "test:resource:momentum", new BigDecimal("2"), new BigDecimal("10"));
        session.setGenericRuleValue(scene, "test:value:weather", RuleValue.text("STORM"));
        session.setGenericNumericRuleValue(relic, "test:stat:focus", new BigDecimal("8"));
        session.setGenericConditionStacks(campaign, "test:condition:exposed", 2);
        session.setGenericRuleActive(hero, "test:condition:exposed", true);

        assertEquals(new BigDecimal("4"), session.genericRuleValue(hero, "test:stat:focus"));
        assertEquals(new BigDecimal("3"), session.genericRuleValue(goblin, "test:stat:focus"));
        assertEquals(new BigDecimal("8"), session.genericRuleValue(relic, "test:stat:focus"));
        assertEquals(RuleValue.text("STORM"), session.genericTypedRuleValue(scene, "test:value:weather"));
        assertEquals(2, session.genericRuleState(campaign).conditionStacks().get("test:condition:exposed"));
        assertFalse(session.genericRuleActive(goblin, "test:condition:exposed"));
        assertFalse(session.genericRuleActive("test:condition:exposed"));

        session.executeRuleAction(hero, "test:action:push");
        assertEquals(new BigDecimal("3"), session.genericRuleState(hero).resources()
                .get("test:resource:momentum").current());
        assertEquals(new BigDecimal("2"), session.genericRuleState(goblin).resources()
                .get("test:resource:momentum").current());
        assertEquals(1, session.genericRuleState(hero).conditionStacks().get("test:condition:exposed"));
        assertFalse(session.genericRuleState(goblin).conditionStacks().containsKey("test:condition:exposed"));
        assertTrue(session.auditTrail().stream().anyMatch(event ->
                event.type() == EventType.RULE_ACTION_EXECUTED
                        && "ACTOR".equals(event.details().get("scopeKind"))
                        && "hero".equals(event.details().get("scopeId"))));

        assertTrue(session.undo());
        assertEquals(new BigDecimal("5"), session.genericRuleState(hero).resources()
                .get("test:resource:momentum").current());
        assertFalse(session.genericRuleState(hero).conditionStacks().containsKey("test:condition:exposed"));

        session.changeRuleset(second);
        assertEquals(new BigDecimal("9"), session.genericRuleState(hero).resources()
                .get("test:resource:momentum").current());
        assertEquals(RuleValue.text("STORM"), session.genericTypedRuleValue(scene, "test:value:weather"));
        assertEquals(new BigDecimal("8"), session.genericRuleValue(relic, "test:stat:focus"));
        assertThrows(IllegalStateException.class,
                () -> session.genericRuleValue(RuleScope.actor("missing"), "test:stat:focus"));
    }

    @Test
    void anActorScopedTurnBudgetResetsOnlyWhenThatActorStartsAgain() {
        CombatSession session = active(executableRevision("revision:scoped-turn:1", 10, 2));
        RuleScope hero = RuleScope.actor("hero");

        session.setGenericTurnResource(hero, "spotlight", BigDecimal.ZERO);
        session.endTurn();

        assertEquals(BigDecimal.ZERO, session.genericRuleState(hero).turnBudget().get("spotlight"));
        session.endTurn();
        assertEquals(new BigDecimal("2"), session.genericRuleState(hero).turnBudget().get("spotlight"));
    }

    @Test
    void oneAtomicActionCanSpendOnSourceAffectTargetAndChangeSessionState() {
        CombatSession session = active(executableRevision("revision:scoped-target:1", 10, 2));
        RuleScope hero = RuleScope.actor("hero");
        RuleScope goblin = RuleScope.actor("goblin");

        session.executeRuleAction(hero, goblin, "test:action:push");

        assertEquals(new BigDecimal("4"), session.genericRuleState(hero).resources()
                .get("test:resource:momentum").current());
        assertEquals(new BigDecimal("1"), session.genericRuleState(hero).turnBudget().get("spotlight"));
        assertFalse(session.genericRuleState(hero).conditionStacks().containsKey("test:condition:exposed"));
        assertEquals(1, session.genericRuleState(goblin).conditionStacks().get("test:condition:exposed"));
        assertEquals(RuleValue.text("STORM"), session.genericTypedRuleValue("test:value:weather"));
        assertEquals(RuleValue.text("CLEAR"), session.genericTypedRuleValue(hero, "test:value:weather"));
        assertTrue(session.auditTrail().stream().anyMatch(event ->
                event.type() == EventType.RULE_ACTION_EXECUTED
                        && "hero".equals(event.details().get("scopeId"))
                        && "goblin".equals(event.details().get("targetScopeId"))));

        assertTrue(session.undo());
        assertEquals(new BigDecimal("6"), session.genericRuleState(hero).resources()
                .get("test:resource:momentum").current());
        assertFalse(session.genericRuleState(goblin).conditionStacks().containsKey("test:condition:exposed"));
        assertEquals(RuleValue.text("CLEAR"), session.genericTypedRuleValue("test:value:weather"));

        session.fireRuleEvent(hero, goblin, "MARK_TARGET");
        assertFalse(session.genericRuleState(hero).conditionStacks().containsKey("test:condition:exposed"));
        assertEquals(1, session.genericRuleState(goblin).conditionStacks().get("test:condition:exposed"));
        assertTrue(session.undo());
        assertFalse(session.genericRuleState(goblin).conditionStacks().containsKey("test:condition:exposed"));
    }

    @Test
    void automaticActorTurnEventsRouteSessionEffectsToTheSessionScope() {
        List<RuleEntity> entities = List.of(
                entity("test:value:actor-marker", RuleKind.VALUE, Map.of(
                        "valueType", "NUMBER", "defaultValue", "0", "mutable", "true")),
                entity("test:value:weather", RuleKind.VALUE, Map.of(
                        "valueType", "TEXT", "defaultValue", "CLEAR",
                        "allowedValues", "CLEAR,STORM", "mutable", "true")),
                entity("test:effect:storm-session", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:value:weather", "targetRef", "test:value:weather",
                        "application", "SET_VALUE", "valueType", "TEXT", "valueLiteral", "STORM",
                        "recipient", "SESSION")),
                entity("test:trigger:marked-turn", RuleKind.TRIGGER, Map.of(
                        "event", "TURN_START", "conditionFormula", "${test:value:actor-marker} == 1",
                        "effectRefs", "test:effect:storm-session")));
        RulesetRevision revision = RulesetRevision.create(
                "test:turn-routing", "revision:turn-routing:1", "1", "Turn routing", "",
                RulesetOrigin.HOMEBREW, "", CONFIG, entities, "2026-08-30T00:00:00Z");
        CombatSession session = active(revision);
        RuleScope hero = RuleScope.actor("hero");

        session.setGenericRuleValue(hero, "test:value:actor-marker", RuleValue.number(1));
        assertEquals(RuleValue.text("CLEAR"), session.genericTypedRuleValue("test:value:weather"));

        session.endTurn(); // inizia il goblin
        assertEquals(RuleValue.text("CLEAR"), session.genericTypedRuleValue("test:value:weather"));
        session.endTurn(); // ricomincia l'eroe: il trigger appartiene al suo scope

        assertEquals(RuleValue.text("STORM"), session.genericTypedRuleValue("test:value:weather"));
        assertEquals(RuleValue.text("CLEAR"), session.genericTypedRuleValue(hero, "test:value:weather"));
        assertTrue(session.auditTrail().stream().anyMatch(event ->
                event.type() == EventType.RULE_EVENT_FIRED
                        && "ACTOR".equals(event.details().get("scopeKind"))
                        && "hero".equals(event.details().get("scopeId"))));
    }

    @Test
    void revisionMigrationDropsIncompatibleValuesClampsConditionsAndFollowsResourceAliases() {
        CombatSession session = active(migrationRevision("revision:migration:1", false));
        session.setGenericRuleValue("test:value:mood", RuleValue.text("RISKY"));
        session.setGenericConditionStacks("test:condition:marked", 3);
        session.setGenericResource("test:resource:old", new BigDecimal("2"), new BigDecimal("5"));

        session.changeRuleset(migrationRevision("revision:migration:2", true));

        assertEquals(RuleValue.number(7), session.genericTypedRuleValue("test:value:mood"));
        assertEquals(1, session.genericRuleState(RuleScope.session())
                .conditionStacks().get("test:condition:marked"));
        assertEquals(new BigDecimal("7"), session.genericRuleState(RuleScope.session())
                .resources().get("test:resource:new").current());
        assertEquals(new BigDecimal("10"), session.genericRuleState(RuleScope.session())
                .resources().get("test:resource:new").maximum());
    }

    private CombatSession active(RulesetRevision revision) {
        CombatSession session = CombatSession.create("universal", 42L, revision, "test");
        session.addCombatant("hero", CombatFixtures.hero());
        session.addCombatant("goblin", CombatFixtures.goblin());
        session.setInitiative("hero", 20);
        session.setInitiative("goblin", 10);
        session.markReady();
        session.start();
        return session;
    }

    private static RulesetRevision executableRevision(String revisionId, int resourceMaximum, int spotlight) {
        List<RuleEntity> entities = List.of(
                entity("test:stat:focus", RuleKind.STAT, Map.of("defaultFormula", "3")),
                entity("test:turn", RuleKind.ACTION_ECONOMY,
                        Map.of("budgets", "spotlight=" + spotlight)),
                entity("test:resource:momentum", RuleKind.RESOURCE, Map.of(
                        "maximumFormula", Integer.toString(resourceMaximum),
                        "initialFormula", "6", "recoveryEvent", "LONG_REST")),
                entity("test:value:weather", RuleKind.VALUE, Map.of(
                        "valueType", "TEXT", "defaultValue", "CLEAR",
                        "allowedValues", "CLEAR,STORM", "mutable", "true")),
                entity("test:condition:exposed", RuleKind.CONDITION, Map.of("maximumStacks", "3")),
                entity("test:damage:void", RuleKind.DAMAGE_TYPE,
                        Map.of("damageTypeId", "test:void")),
                entity("test:effect:expose", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:condition:exposed",
                        "targetRef", "test:condition:exposed", "application", "ADD_CONDITION",
                        "operation", "ADD", "valueFormula", "1", "recipient", "TARGET")),
                entity("test:modifier:focus", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:condition:exposed",
                        "targetRef", "test:stat:focus", "application", "STATIC",
                        "operation", "ADD", "valueFormula", "1")),
                entity("test:effect:momentum", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:resource:momentum",
                        "targetRef", "test:resource:momentum", "application", "CHANGE_RESOURCE",
                        "operation", "ADD", "valueFormula", "2")),
                entity("test:effect:storm", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "test:value:weather",
                        "targetRef", "test:value:weather", "application", "SET_VALUE",
                        "valueType", "TEXT", "valueLiteral", "STORM", "recipient", "SESSION")),
                entity("test:action:push", RuleKind.ACTION, Map.of(
                        "costs", "turn:spotlight=1;resource:test:resource:momentum=2",
                        "effectRefs", "test:effect:expose,test:effect:storm")),
                entity("test:trigger:cry", RuleKind.TRIGGER, Map.of(
                        "event", "BATTLE_CRY", "effectRefs", "test:effect:momentum")),
                entity("test:trigger:mark", RuleKind.TRIGGER, Map.of(
                        "event", "MARK_TARGET", "effectRefs", "test:effect:expose")),
                entity("test:randomizer:pool", RuleKind.RANDOMIZER, Map.of(
                        "mode", "DICE_POOL", "countFormula", "${test:stat:focus}",
                        "sidesFormula", "6", "keep", "SUCCESSES", "successThresholdFormula", "5")));
        return RulesetRevision.create("test:universal", revisionId, "1.0.0", "Universal",
                "Executable universal rules", RulesetOrigin.HOMEBREW, "", CONFIG, entities,
                "2026-08-30T00:00:00Z");
    }

    private static RulesetRevision migrationRevision(String revisionId, boolean next) {
        List<RuleEntity> entities = next
                ? List.of(
                    entity("test:value:mood", RuleKind.VALUE, Map.of(
                            "valueType", "NUMBER", "defaultValue", "7", "mutable", "true")),
                    entity("test:condition:marked", RuleKind.CONDITION, Map.of("maximumStacks", "1")),
                    entity("test:resource:new", RuleKind.RESOURCE, Map.of(
                            "resourceId", "test:resource:old",
                            "maximumFormula", "10", "initialFormula", "10")))
                : List.of(
                    entity("test:value:mood", RuleKind.VALUE, Map.of(
                            "valueType", "TEXT", "defaultValue", "CALM",
                            "allowedValues", "CALM,RISKY", "mutable", "true")),
                    entity("test:condition:marked", RuleKind.CONDITION, Map.of("maximumStacks", "3")),
                    entity("test:resource:old", RuleKind.RESOURCE, Map.of(
                            "maximumFormula", "5", "initialFormula", "5")));
        return RulesetRevision.create("test:migration", revisionId, next ? "2" : "1", "Migration", "",
                RulesetOrigin.HOMEBREW, "", CONFIG, entities, "2026-08-30T00:00:00Z");
    }

    private static RuleEntity entity(String id, RuleKind kind, Map<String, String> attributes) {
        return new RuleEntity(id, kind, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.bilingual(id, id), LocalizedRuleText.bilingual("Test", "Test"), "",
                true, RuleAutomationLevel.FULL, attributes, List.of("test"), "Test", "", 0);
    }
}
