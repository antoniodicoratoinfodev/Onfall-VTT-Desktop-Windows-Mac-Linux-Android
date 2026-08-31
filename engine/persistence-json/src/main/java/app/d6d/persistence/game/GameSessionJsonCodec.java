package app.d6d.persistence.game;

import app.d6d.domain.game.GameSceneState;
import app.d6d.domain.game.GameSessionEvent;
import app.d6d.domain.game.GameSessionState;
import app.d6d.domain.game.GameSessionStatus;
import app.d6d.engine.GameSession;
import app.d6d.rules.model.LocalizedRuleText;
import app.d6d.rules.model.RuleAutomationLevel;
import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleKind;
import app.d6d.rules.model.RuleRuntimeState;
import app.d6d.rules.model.RuleScope;
import app.d6d.rules.model.RuleSessionSnapshot;
import app.d6d.rules.model.RuleValue;
import app.d6d.rules.model.RulesetBinding;
import app.d6d.rules.model.RulesetOrigin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Codec esplicito e portabile della sessione generale, anche senza combattimento. */
public final class GameSessionJsonCodec {
    public static final int SCHEMA_VERSION = 1;

    public Map<String, Object> encode(GameSession session) {
        Objects.requireNonNull(session, "session");
        GameSessionState state;
        List<GameSessionEvent> audit;
        synchronized (session) {
            state = session.currentState();
            audit = session.auditTrail();
        }
        return object(
                "schemaVersion", SCHEMA_VERSION,
                "currentState", encodeState(state),
                "auditTrail", audit.stream().map(this::encodeEvent).toList());
    }

    public GameSession decode(Map<String, ?> document) {
        Objects.requireNonNull(document, "document");
        int schema = integer(document, "schemaVersion", "$");
        if (schema != SCHEMA_VERSION) throw invalid("$.schemaVersion", "unsupported schema version " + schema);
        GameSessionState state = decodeState(map(document, "currentState", "$"), "$.currentState");
        List<?> rawAudit = list(document, "auditTrail", "$");
        ArrayList<GameSessionEvent> audit = new ArrayList<>(rawAudit.size());
        for (int index = 0; index < rawAudit.size(); index++) {
            String path = "$.auditTrail[" + index + ']';
            audit.add(decodeEvent(asMap(rawAudit.get(index), path), path));
        }
        return GameSession.restore(state, audit);
    }

    private Map<String, Object> encodeState(GameSessionState state) {
        return object(
                "sessionId", state.sessionId(),
                "displayName", state.displayName(),
                "status", state.status().name(),
                "revision", state.revision(),
                "randomSeed", state.randomSeed(),
                "randomState", state.randomState(),
                "rulesetBinding", encodeBinding(state.rulesetBinding()),
                "ruleSession", encodeRuleSession(state.ruleSession()),
                "scenes", state.scenes().values().stream().map(this::encodeScene).toList(),
                "activeSceneId", state.activeSceneId());
    }

    private GameSessionState decodeState(Map<?, ?> value, String path) {
        List<?> rawScenes = list(value, "scenes", path);
        LinkedHashMap<String, GameSceneState> scenes = new LinkedHashMap<>();
        for (int index = 0; index < rawScenes.size(); index++) {
            String itemPath = path + ".scenes[" + index + ']';
            GameSceneState scene = decodeScene(asMap(rawScenes.get(index), itemPath), itemPath);
            if (scenes.put(scene.id(), scene) != null) throw invalid(itemPath, "duplicate scene " + scene.id());
        }
        return new GameSessionState(
                string(value, "sessionId", path),
                string(value, "displayName", path),
                enumeration(value, "status", path, GameSessionStatus::valueOf),
                longInteger(value, "revision", path),
                longInteger(value, "randomSeed", path),
                longInteger(value, "randomState", path),
                decodeBinding(map(value, "rulesetBinding", path), path + ".rulesetBinding"),
                decodeRuleSession(map(value, "ruleSession", path), path + ".ruleSession"),
                scenes,
                string(value, "activeSceneId", path));
    }

    private Map<String, Object> encodeScene(GameSceneState scene) {
        return object(
                "id", scene.id(), "name", scene.name(), "kind", scene.kind(),
                "procedureRef", scene.procedureRef(), "phaseIndex", scene.phaseIndex(),
                "participantIds", scene.participantIds(),
                "metadata", new LinkedHashMap<>(scene.metadata()), "revision", scene.revision());
    }

    private GameSceneState decodeScene(Map<?, ?> value, String path) {
        return new GameSceneState(
                string(value, "id", path), string(value, "name", path), string(value, "kind", path),
                string(value, "procedureRef", path), integer(value, "phaseIndex", path),
                stringList(value, "participantIds", path),
                stringMap(map(value, "metadata", path), path + ".metadata"),
                longInteger(value, "revision", path));
    }

    private Map<String, Object> encodeEvent(GameSessionEvent event) {
        return object(
                "sequence", event.sequence(), "type", event.type(),
                "sourceScope", event.sourceScope(), "targetScopes", event.targetScopes(),
                "details", new LinkedHashMap<>(event.details()));
    }

    private GameSessionEvent decodeEvent(Map<?, ?> value, String path) {
        return new GameSessionEvent(
                longInteger(value, "sequence", path), string(value, "type", path),
                string(value, "sourceScope", path), string(value, "targetScopes", path),
                stringMap(map(value, "details", path), path + ".details"));
    }

    private Map<String, Object> encodeBinding(RulesetBinding binding) {
        return object(
                "projectId", binding.projectId(), "revisionId", binding.revisionId(),
                "canonicalHash", binding.canonicalHash(), "runtimeHash", binding.runtimeHash(),
                "runtimeSemanticsVersion", binding.runtimeSemanticsVersion(),
                "displayName", binding.displayName(), "legacy", binding.legacy());
    }

    private RulesetBinding decodeBinding(Map<?, ?> value, String path) {
        return new RulesetBinding(
                string(value, "projectId", path), string(value, "revisionId", path),
                string(value, "canonicalHash", path), string(value, "runtimeHash", path),
                string(value, "runtimeSemanticsVersion", path), string(value, "displayName", path),
                bool(value, "legacy", path));
    }

    private Map<String, Object> encodeRuleSession(RuleSessionSnapshot snapshot) {
        return object(
                "configured", snapshot.configured(),
                "entities", snapshot.entities().stream().map(this::encodeEntity).toList(),
                "state", encodeRuntimeState(snapshot.state()),
                "scopedStates", snapshot.scopedStates().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> object(
                                "kind", entry.getKey().kind().name(), "id", entry.getKey().id(),
                                "state", encodeRuntimeState(entry.getValue())))
                        .toList());
    }

    private RuleSessionSnapshot decodeRuleSession(Map<?, ?> value, String path) {
        List<?> rawEntities = list(value, "entities", path);
        ArrayList<RuleEntity> entities = new ArrayList<>(rawEntities.size());
        for (int index = 0; index < rawEntities.size(); index++) {
            String itemPath = path + ".entities[" + index + ']';
            entities.add(decodeEntity(asMap(rawEntities.get(index), itemPath), itemPath));
        }
        LinkedHashMap<RuleScope, RuleRuntimeState> scoped = new LinkedHashMap<>();
        List<?> rawScopes = list(value, "scopedStates", path);
        for (int index = 0; index < rawScopes.size(); index++) {
            String itemPath = path + ".scopedStates[" + index + ']';
            Map<?, ?> item = asMap(rawScopes.get(index), itemPath);
            RuleScope scope = new RuleScope(
                    enumeration(item, "kind", itemPath, RuleScope.Kind::valueOf),
                    string(item, "id", itemPath));
            if (scope.isSession()) throw invalid(itemPath, "session scope must use the root state");
            if (scoped.put(scope, decodeRuntimeState(map(item, "state", itemPath), itemPath + ".state")) != null) {
                throw invalid(itemPath, "duplicate rule scope " + scope.canonicalKey());
            }
        }
        return new RuleSessionSnapshot(
                entities,
                decodeRuntimeState(map(value, "state", path), path + ".state"),
                scoped,
                bool(value, "configured", path));
    }

    private Map<String, Object> encodeRuntimeState(RuleRuntimeState state) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        state.values().forEach((id, value) -> values.put(id,
                object("type", value.type().name(), "value", value.canonicalValue())));
        LinkedHashMap<String, Object> resources = new LinkedHashMap<>();
        state.resources().forEach((id, resource) -> resources.put(id,
                object("current", resource.current().toPlainString(),
                        "maximum", resource.maximum().toPlainString())));
        LinkedHashMap<String, Object> conditions = new LinkedHashMap<>();
        state.conditionStacks().forEach(conditions::put);
        LinkedHashMap<String, Object> budget = new LinkedHashMap<>();
        state.turnBudget().forEach((id, amount) -> budget.put(id, amount.toPlainString()));
        return object(
                "values", values, "resources", resources, "conditionStacks", conditions,
                "turnBudget", budget, "activeRuleIds", state.activeRuleIds().stream().sorted().toList(),
                "revision", state.revision());
    }

    private RuleRuntimeState decodeRuntimeState(Map<?, ?> value, String path) {
        LinkedHashMap<String, RuleValue> values = new LinkedHashMap<>();
        map(value, "values", path).forEach((rawId, rawValue) -> {
            String id = key(rawId, path + ".values");
            Map<?, ?> encoded = asMap(rawValue, path + ".values." + id);
            values.put(id, new RuleValue(
                    enumeration(encoded, "type", path + ".values." + id, RuleValue.Type::valueOf),
                    string(encoded, "value", path + ".values." + id)));
        });
        LinkedHashMap<String, RuleRuntimeState.ResourceState> resources = new LinkedHashMap<>();
        map(value, "resources", path).forEach((rawId, rawValue) -> {
            String id = key(rawId, path + ".resources");
            Map<?, ?> encoded = asMap(rawValue, path + ".resources." + id);
            resources.put(id, new RuleRuntimeState.ResourceState(
                    id,
                    decimal(encoded, "current", path + ".resources." + id),
                    decimal(encoded, "maximum", path + ".resources." + id)));
        });
        LinkedHashMap<String, Integer> conditions = new LinkedHashMap<>();
        map(value, "conditionStacks", path).forEach((rawId, rawValue) ->
                conditions.put(key(rawId, path + ".conditionStacks"), integer(rawValue, path + ".conditionStacks")));
        LinkedHashMap<String, BigDecimal> budget = new LinkedHashMap<>();
        map(value, "turnBudget", path).forEach((rawId, rawValue) -> {
            String id = key(rawId, path + ".turnBudget");
            if (!(rawValue instanceof String text)) throw invalid(path + ".turnBudget." + id, "expected decimal text");
            try {
                budget.put(id, new BigDecimal(text));
            } catch (NumberFormatException failure) {
                throw invalid(path + ".turnBudget." + id, "invalid decimal");
            }
        });
        return new RuleRuntimeState(
                values, resources, conditions, budget,
                new LinkedHashSet<>(stringList(value, "activeRuleIds", path)),
                longInteger(value, "revision", path));
    }

    private Map<String, Object> encodeEntity(RuleEntity entity) {
        return object(
                "id", entity.id(), "kind", entity.kind().name(), "origin", entity.origin().name(),
                "name", encodeText(entity.name()), "description", encodeText(entity.description()),
                "derivedFrom", entity.derivedFrom(), "enabled", entity.enabled(),
                "automationLevel", entity.automationLevel().name(),
                "attributes", new LinkedHashMap<>(entity.attributes()), "tags", entity.tags(),
                "source", entity.source(), "license", entity.license(), "sourcePage", entity.sourcePage());
    }

    private RuleEntity decodeEntity(Map<?, ?> value, String path) {
        return new RuleEntity(
                string(value, "id", path), enumeration(value, "kind", path, RuleKind::valueOf),
                enumeration(value, "origin", path, RulesetOrigin::valueOf),
                decodeText(map(value, "name", path), path + ".name"),
                decodeText(map(value, "description", path), path + ".description"),
                string(value, "derivedFrom", path), bool(value, "enabled", path),
                enumeration(value, "automationLevel", path, RuleAutomationLevel::valueOf),
                stringMap(map(value, "attributes", path), path + ".attributes"),
                stringList(value, "tags", path), string(value, "source", path),
                string(value, "license", path), integer(value, "sourcePage", path));
    }

    private Map<String, Object> encodeText(LocalizedRuleText text) {
        return object("primaryLanguage", text.primaryLanguage(), "values", new LinkedHashMap<>(text.values()));
    }

    private LocalizedRuleText decodeText(Map<?, ?> value, String path) {
        return new LocalizedRuleText(
                stringMap(map(value, "values", path), path + ".values"),
                string(value, "primaryLanguage", path));
    }

    private static Map<String, Object> object(Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    private static Map<?, ?> map(Map<?, ?> value, String field, String path) {
        if (!value.containsKey(field)) throw invalid(path + '.' + field, "missing field");
        return asMap(value.get(field), path + '.' + field);
    }

    private static Map<?, ?> asMap(Object value, String path) {
        if (!(value instanceof Map<?, ?> result)) throw invalid(path, "expected object");
        return result;
    }

    private static List<?> list(Map<?, ?> value, String field, String path) {
        Object raw = value.get(field);
        if (!(raw instanceof List<?> result)) throw invalid(path + '.' + field, "expected array");
        return result;
    }

    private static String string(Map<?, ?> value, String field, String path) {
        Object raw = value.get(field);
        if (!(raw instanceof String result)) throw invalid(path + '.' + field, "expected string");
        return result;
    }

    private static boolean bool(Map<?, ?> value, String field, String path) {
        Object raw = value.get(field);
        if (!(raw instanceof Boolean result)) throw invalid(path + '.' + field, "expected boolean");
        return result;
    }

    private static int integer(Map<?, ?> value, String field, String path) {
        return integer(value.get(field), path + '.' + field);
    }

    private static int integer(Object raw, String path) {
        if (!(raw instanceof Number number)) throw invalid(path, "expected integer");
        long value = number.longValue();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                || number.doubleValue() != (double) value) throw invalid(path, "expected exact integer");
        return (int) value;
    }

    private static long longInteger(Map<?, ?> value, String field, String path) {
        Object raw = value.get(field);
        if (!(raw instanceof Number number)) throw invalid(path + '.' + field, "expected integer");
        long result = number.longValue();
        if (number.doubleValue() != (double) result) throw invalid(path + '.' + field, "expected exact integer");
        return result;
    }

    private static BigDecimal decimal(Map<?, ?> value, String field, String path) {
        String text = string(value, field, path);
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException failure) {
            throw invalid(path + '.' + field, "invalid decimal");
        }
    }

    private static List<String> stringList(Map<?, ?> value, String field, String path) {
        List<?> raw = list(value, field, path);
        ArrayList<String> result = new ArrayList<>(raw.size());
        for (int index = 0; index < raw.size(); index++) {
            if (!(raw.get(index) instanceof String text)) {
                throw invalid(path + '.' + field + '[' + index + ']', "expected string");
            }
            result.add(text);
        }
        return result;
    }

    private static Map<String, String> stringMap(Map<?, ?> raw, String path) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            String id = key(key, path);
            if (!(value instanceof String text)) throw invalid(path + '.' + id, "expected string");
            result.put(id, text);
        });
        return result;
    }

    private static String key(Object raw, String path) {
        if (!(raw instanceof String value) || value.isBlank()) throw invalid(path, "invalid object key");
        return value;
    }

    private static <T> T enumeration(
            Map<?, ?> value,
            String field,
            String path,
            java.util.function.Function<String, T> parser) {
        String raw = string(value, field, path);
        try {
            return parser.apply(raw);
        } catch (RuntimeException failure) {
            throw invalid(path + '.' + field, "unknown enum value " + raw);
        }
    }

    private static IllegalArgumentException invalid(String path, String message) {
        return new IllegalArgumentException(path + ": " + message);
    }
}
