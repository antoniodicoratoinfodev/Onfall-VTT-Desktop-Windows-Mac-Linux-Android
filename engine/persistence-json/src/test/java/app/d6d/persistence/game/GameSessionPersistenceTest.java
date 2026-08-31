package app.d6d.persistence.game;

import app.d6d.engine.GameSession;
import app.d6d.persistence.json.Json;
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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionPersistenceTest {
    @TempDir
    Path directory;

    @Test
    void codecRoundTripsScenesScopedStateAuditAndFutureRandomness() {
        GameSession original = populated(123456L);
        GameSessionJsonCodec codec = new GameSessionJsonCodec();

        GameSession restored = codec.decode(Json.parseObject(Json.encode(codec.encode(original))));

        assertEquals(original.currentState(), restored.currentState());
        assertEquals(original.auditTrail(), restored.auditTrail());
        CompiledRuleset.RandomizerResult originalNext = original.roll("game:randomizer", RuleScope.actor("hero"));
        CompiledRuleset.RandomizerResult restoredNext = restored.roll("game:randomizer", RuleScope.actor("hero"));
        assertEquals(originalNext, restoredNext);
        assertEquals(original.currentState().randomState(), restored.currentState().randomState());
    }

    @Test
    void storeImportsAndExportsAStandaloneNonCombatSession() throws Exception {
        GameSession original = populated(77L);
        GameSessionStore source = new GameSessionStore(directory.resolve("source"), "session");
        GameSessionStore target = new GameSessionStore(directory.resolve("target"), "session");
        Path portable = directory.resolve("portable/game.onfall-session");

        assertFalse(source.exists());
        source.save(original);
        source.exportTo(portable);
        GameSession imported = target.importFrom(portable);

        assertTrue(source.exists());
        assertTrue(target.exists());
        assertEquals(original.currentState(), imported.currentState());
        assertEquals(original.auditTrail(), target.load().auditTrail());
    }

    private static GameSession populated(long seed) {
        RulesetRevision revision = RulesetRevision.create(
                "game", "game:revision:1", "1", "Game", "", RulesetOrigin.HOMEBREW, "",
                RulesetRuntimeConfig.genericManual(), List.of(
                        entity("game:value:clock", RuleKind.VALUE, Map.of(
                                "valueType", "NUMBER", "defaultValue", "0")),
                        entity("game:randomizer", RuleKind.RANDOMIZER, Map.of(
                                "mode", "DICE", "countFormula", "1", "sidesFormula", "12")),
                        entity("game:procedure", RuleKind.SCENE_PROCEDURE, Map.of(
                                "phases", "OPEN,RESOLVE", "trackerRefs", "game:value:clock"))),
                "2026-08-31T00:00:00Z");
        GameSession session = GameSession.fromRevision("game:session", "A session", revision, seed);
        session.addScene("scene:road", "The road", "EXPLORATION", "game:procedure");
        session.addParticipant("scene:road", "hero");
        session.activateScene("scene:road");
        session.start();
        session.setRuleValue(RuleScope.actor("hero"), "game:value:clock", RuleValue.number(3));
        session.roll("game:randomizer", RuleScope.actor("hero"));
        return session;
    }

    private static RuleEntity entity(String id, RuleKind kind, Map<String, String> attributes) {
        return new RuleEntity(id, kind, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.bilingual(id, id), LocalizedRuleText.bilingual("Test", "Test"), "",
                true, RuleAutomationLevel.FULL, attributes, List.of("test"), "Test", "", 0);
    }
}
