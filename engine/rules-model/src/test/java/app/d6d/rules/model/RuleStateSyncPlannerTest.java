package app.d6d.rules.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleStateSyncPlannerTest {

    @Test
    void appliesOnlyCompatibleAutomaticStateAndKeepsProposalsExplicit() {
        RulesetRevision revision = revision(List.of(
                entity("sync:value:auto", RuleKind.VALUE, Map.of(
                        "valueType", "NUMBER", "defaultValue", "0", "owner", "ACTOR",
                        "syncPolicy", "AUTO_IF_COMPATIBLE")),
                entity("sync:value:proposal", RuleKind.VALUE, Map.of(
                        "valueType", "TEXT", "defaultValue", "OLD", "allowedValues", "OLD,NEW",
                        "owner", "ACTOR", "syncPolicy", "PROPOSE")),
                entity("sync:resource:local", RuleKind.RESOURCE, Map.of(
                        "maximumFormula", "5", "initialFormula", "5", "owner", "ACTOR",
                        "syncPolicy", "LOCAL_ONLY"))));
        CompiledRuleset rules = revision.compile();
        RuleRuntimeState source = rules.initialState(Map.of(), Set.of());
        source = rules.setRuleValue("sync:value:auto", RuleValue.number(4), source);
        source = rules.setRuleValue("sync:value:proposal", RuleValue.text("NEW"), source);
        source = rules.setResource("sync:resource:local", BigDecimal.ONE, new BigDecimal("5"), source);
        RuleRuntimeState target = rules.initialState(Map.of(), Set.of());

        RuleStateSyncPlanner.Plan plan = RuleStateSyncPlanner.plan(
                rules, revision.binding(), revision.binding(), RuleScope.actor("encounter:hero"),
                RuleScope.actor("sheet:hero"), source, target);

        assertTrue(plan.bindingCompatible());
        assertFalse(plan.hasConflicts());
        assertTrue(plan.requiresConfirmation());
        assertEquals(RuleValue.number(4), rules.ruleValue("sync:value:auto", plan.automaticResult()));
        assertEquals(RuleValue.text("OLD"), rules.ruleValue("sync:value:proposal", plan.automaticResult()));
        assertEquals(new BigDecimal("5"), plan.automaticResult().resources().get("sync:resource:local").current());
        assertEquals(List.of(
                        RuleStateSyncPlanner.Decision.SKIPPED,
                        RuleStateSyncPlanner.Decision.AUTO_APPLIED,
                        RuleStateSyncPlanner.Decision.PROPOSED),
                plan.changes().stream().map(RuleStateSyncPlanner.Change::decision).toList());
    }

    @Test
    void incompatibleRevisionsNeverApplyAutomaticState() {
        RulesetRevision revision = revision(List.of(entity("sync:value:auto", RuleKind.VALUE, Map.of(
                "valueType", "NUMBER", "defaultValue", "0", "owner", "ACTOR",
                "syncPolicy", "AUTO_IF_COMPATIBLE"))));
        CompiledRuleset rules = revision.compile();
        RuleRuntimeState source = rules.setRuleValue(
                "sync:value:auto", RuleValue.number(7), rules.initialState(Map.of(), Set.of()));
        RuleRuntimeState target = rules.initialState(Map.of(), Set.of());
        RulesetBinding incompatible = new RulesetBinding(
                "other", "other:1", "other-hash", "other-runtime", revision.binding().runtimeSemanticsVersion(),
                "Other", false);

        RuleStateSyncPlanner.Plan plan = RuleStateSyncPlanner.plan(
                rules, revision.binding(), incompatible, RuleScope.actor("source"), RuleScope.actor("target"),
                source, target);

        assertFalse(plan.bindingCompatible());
        assertTrue(plan.hasConflicts());
        assertEquals(target, plan.automaticResult());
        assertEquals(RuleStateSyncPlanner.Decision.CONFLICT, plan.changes().get(0).decision());
    }

    private static RulesetRevision revision(List<RuleEntity> entities) {
        return RulesetRevision.create("sync", "sync:revision:1", "1", "Sync", "",
                RulesetOrigin.HOMEBREW, "", RulesetRuntimeConfig.genericManual(), entities,
                "2026-08-31T00:00:00Z");
    }

    private static RuleEntity entity(String id, RuleKind kind, Map<String, String> attributes) {
        return new RuleEntity(id, kind, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.bilingual(id, id), LocalizedRuleText.bilingual("Test", "Test"), "",
                true, RuleAutomationLevel.FULL, attributes, List.of("test"), "Test", "", 0);
    }
}
