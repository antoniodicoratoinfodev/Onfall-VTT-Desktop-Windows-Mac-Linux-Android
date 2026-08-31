package app.d6d.engine;

import app.d6d.domain.game.GameSessionStatus;
import app.d6d.rules.model.CompiledRuleset;
import app.d6d.rules.model.LocalizedRuleText;
import app.d6d.rules.model.RuleAutomationLevel;
import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleKind;
import app.d6d.rules.model.RuleScope;
import app.d6d.rules.model.RuleValue;
import app.d6d.rules.model.RulesetOrigin;
import app.d6d.rules.model.RulesetRevision;
import app.d6d.rules.model.RulesetRuntimeConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionTest {

    @Test
    void runsAndRestoresAClasslessNonCombatSessionWithoutD20Fields() {
        RulesetRevision rules = revision(List.of(
                entity("story:value:stress", RuleKind.VALUE, Map.of(
                        "valueType", "NUMBER", "defaultValue", "0", "lifetime", "SCENE")),
                entity("story:turn", RuleKind.ACTION_ECONOMY, Map.of("budgets", "spotlight=1")),
                entity("story:effect:pressure", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "story:value:stress", "targetRef", "story:value:stress",
                        "application", "CHANGE_VALUE", "recipient", "TARGET",
                        "operation", "ADD", "valueFormula", "1")),
                entity("story:action:pressure", RuleKind.ACTION, Map.of(
                        "costs", "turn:spotlight=1", "effectRefs", "story:effect:pressure")),
                entity("story:randomizer", RuleKind.RANDOMIZER, Map.of(
                        "mode", "DICE_POOL", "countFormula", "3", "sidesFormula", "6",
                        "keep", "SUCCESSES", "successThresholdFormula", "5")),
                entity("story:procedure", RuleKind.SCENE_PROCEDURE, Map.of(
                        "phases", "SETUP,EXCHANGE,AFTERMATH", "actionRefs", "story:action:pressure",
                        "trackerRefs", "story:value:stress"))));
        GameSession session = GameSession.fromRevision("session:story", "Story", rules, 42L);
        session.addScene("scene:one", "Negotiation", "SOCIAL", "story:procedure");
        session.addParticipant("scene:one", "actor:a");
        session.addParticipant("scene:one", "actor:b");
        session.addParticipant("scene:one", "actor:c");
        session.addScene("scene:two", "Journey", "EXPLORATION", "story:procedure");
        session.activateScene("scene:one");
        session.start();

        session.executeRuleAction("story:action:pressure", RuleScope.actor("actor:a"),
                List.of(RuleScope.actor("actor:b"), RuleScope.actor("actor:c")));

        assertEquals(BigDecimal.ONE,
                session.rules().value("story:value:stress", session.ruleState(RuleScope.actor("actor:b"))));
        assertEquals(BigDecimal.ONE,
                session.rules().value("story:value:stress", session.ruleState(RuleScope.actor("actor:c"))));
        assertEquals(BigDecimal.ZERO,
                session.ruleState(RuleScope.actor("actor:a")).turnBudget().get("spotlight"));
        session.advanceScenePhase();
        assertEquals(1, session.currentState().activeScene().orElseThrow().phaseIndex());

        session.activateScene("scene:two");
        assertEquals(BigDecimal.ZERO,
                session.rules().value("story:value:stress", session.ruleState(RuleScope.actor("actor:b"))));
        session.undo();
        assertEquals("scene:one", session.currentState().activeSceneId());
        assertEquals(BigDecimal.ONE,
                session.rules().value("story:value:stress", session.ruleState(RuleScope.actor("actor:b"))));

        CompiledRuleset.RandomizerResult first = session.roll("story:randomizer", RuleScope.actor("actor:a"));
        session.undo();
        CompiledRuleset.RandomizerResult replay = session.roll("story:randomizer", RuleScope.actor("actor:a"));
        assertEquals(first, replay);
        assertEquals(GameSessionStatus.ACTIVE, session.currentState().status());
        assertFalse(session.currentState().ruleSession().entities().stream()
                .anyMatch(entity -> entity.id().toLowerCase().contains("d20")));
        assertTrue(session.auditTrail().stream().anyMatch(event -> event.type().equals("RULE_ACTION_EXECUTED")));
    }

    @Test
    void rulesetMigrationKeepsCompatibleIdsAndDropsRemovedState() {
        RulesetRevision first = revision(List.of(
                entity("game:value:kept", RuleKind.VALUE, Map.of(
                        "valueType", "NUMBER", "defaultValue", "0")),
                entity("game:value:removed", RuleKind.VALUE, Map.of(
                        "valueType", "TEXT", "defaultValue", "A"))));
        RulesetRevision second = RulesetRevision.create(
                "story", "story:revision:2", "2", "Story 2", "", RulesetOrigin.HOMEBREW,
                first.canonicalHash(), RulesetRuntimeConfig.genericManual(),
                List.of(entity("game:value:kept", RuleKind.VALUE, Map.of(
                        "valueType", "NUMBER", "defaultValue", "1"))), "2026-08-31T01:00:00Z");
        GameSession session = GameSession.fromRevision("game", "Game", first, 1L);
        session.setRuleValue(RuleScope.session(), "game:value:kept", RuleValue.number(8));
        session.setRuleValue(RuleScope.session(), "game:value:removed", RuleValue.text("changed"));

        session.changeRuleset(second);

        assertEquals(new BigDecimal("8"), session.rules().value(
                "game:value:kept", session.ruleState(RuleScope.session())));
        assertFalse(session.ruleState(RuleScope.session()).values().containsKey("game:value:removed"));
        assertEquals(second.canonicalHash(), session.currentState().rulesetBinding().canonicalHash());
    }

    private static RulesetRevision revision(List<RuleEntity> entities) {
        return RulesetRevision.create("story", "story:revision:1", "1", "Story", "",
                RulesetOrigin.HOMEBREW, "", RulesetRuntimeConfig.genericManual(), entities,
                "2026-08-31T00:00:00Z");
    }

    private static RuleEntity entity(String id, RuleKind kind, Map<String, String> attributes) {
        return new RuleEntity(id, kind, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.bilingual(id, id), LocalizedRuleText.bilingual("Test", "Test"), "",
                true, RuleAutomationLevel.FULL, attributes, List.of("test"), "Test", "", 0);
    }
}
