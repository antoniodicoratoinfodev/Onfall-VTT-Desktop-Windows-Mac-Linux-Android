package app.d6d.domain.game;

import app.d6d.rules.model.RuleScope;
import app.d6d.rules.model.RuleSessionSnapshot;
import app.d6d.rules.model.RulesetBinding;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Snapshot completo di una sessione che puo' contenere zero o piu' scene. */
public record GameSessionState(
        String sessionId,
        String displayName,
        GameSessionStatus status,
        long revision,
        long randomSeed,
        long randomState,
        RulesetBinding rulesetBinding,
        RuleSessionSnapshot ruleSession,
        Map<String, GameSceneState> scenes,
        String activeSceneId) {

    public GameSessionState {
        sessionId = requireText(sessionId, "sessionId");
        displayName = requireText(displayName, "displayName");
        status = Objects.requireNonNull(status, "status");
        if (revision < 0) throw new IllegalArgumentException("revision cannot be negative");
        rulesetBinding = Objects.requireNonNull(rulesetBinding, "rulesetBinding");
        ruleSession = Objects.requireNonNull(ruleSession, "ruleSession");
        if (!ruleSession.configured()) {
            throw new IllegalArgumentException("A game session needs an explicit ruleset snapshot");
        }
        TreeMap<String, GameSceneState> sorted = new TreeMap<>();
        Objects.requireNonNull(scenes, "scenes").forEach((key, value) -> {
            String id = requireText(key, "scene key");
            GameSceneState scene = Objects.requireNonNull(value, "scene");
            if (!id.equals(scene.id())) throw new IllegalArgumentException("Scene key and id differ");
            sorted.put(id, scene);
        });
        scenes = Map.copyOf(new LinkedHashMap<>(sorted));
        activeSceneId = activeSceneId == null ? "" : activeSceneId.trim();
        if (!activeSceneId.isEmpty() && !scenes.containsKey(activeSceneId)) {
            throw new IllegalArgumentException("Active scene does not exist");
        }
        for (RuleScope scope : ruleSession.scopedStates().keySet()) {
            if (scope.kind() == RuleScope.Kind.SCENE && !scenes.containsKey(scope.id())) {
                throw new IllegalArgumentException("Rule state references an unknown scene " + scope.id());
            }
        }
    }

    public java.util.Optional<GameSceneState> activeScene() {
        return activeSceneId.isEmpty() ? java.util.Optional.empty()
                : java.util.Optional.of(scenes.get(activeSceneId));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
