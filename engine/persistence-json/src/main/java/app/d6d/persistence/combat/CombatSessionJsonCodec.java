package app.d6d.persistence.combat;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.AutomationStatus;
import app.d6d.domain.combat.CombatEvent;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.CombatStatus;
import app.d6d.domain.combat.CombatantSnapshot;
import app.d6d.domain.combat.CombatantState;
import app.d6d.domain.combat.ConcentrationState;
import app.d6d.domain.combat.ConditionDuration;
import app.d6d.domain.combat.ConditionExpiry;
import app.d6d.domain.combat.ConditionInstance;
import app.d6d.domain.combat.DeathSaveState;
import app.d6d.domain.combat.ConditionType;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.DiceExpression;
import app.d6d.domain.combat.EventType;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.TurnBudget;
import app.d6d.domain.space.BattleMap;
import app.d6d.domain.space.MapBackground;
import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.MapGrid;
import app.d6d.domain.space.TokenPlacement;
import app.d6d.engine.CombatSession;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Explicit JSON-object codec for complete combat sessions.
 *
 * <p>The resulting value only contains JSON primitives, lists and string-keyed
 * maps. No reflective record serialization is used, so the portable schema is
 * independent from Java implementation details.</p>
 */
public final class CombatSessionJsonCodec {
    public static final int SCHEMA_VERSION = 1;

    /** Converts a consistent state-and-audit snapshot into a JSON object. */
    public Map<String, Object> encode(CombatSession session) {
        Objects.requireNonNull(session, "session");
        CombatState state;
        List<CombatEvent> audit;
        synchronized (session) {
            state = session.currentState();
            audit = session.auditTrail();
        }
        return object(
                "schemaVersion", SCHEMA_VERSION,
                "currentState", encodeState(state),
                "auditTrail", audit.stream().map(this::encodeEvent).toList());
    }

    /** Alias that describes the concrete representation returned by {@link #encode}. */
    public Map<String, Object> toMap(CombatSession session) {
        return encode(session);
    }

    /** Validates a JSON object and restores the complete session. */
    public CombatSession decode(Map<String, ?> document) {
        Objects.requireNonNull(document, "document");
        int schemaVersion = integer(document, "schemaVersion", "$");
        if (schemaVersion != SCHEMA_VERSION) {
            throw invalid("$.schemaVersion", "unsupported schema version " + schemaVersion);
        }

        CombatState state = decodeState(objectValue(document, "currentState", "$"), "$.currentState");
        List<?> auditValues = list(document, "auditTrail", "$" );
        List<CombatEvent> audit = new ArrayList<>(auditValues.size());
        for (int index = 0; index < auditValues.size(); index++) {
            String path = "$.auditTrail[" + index + ']';
            audit.add(decodeEvent(asObject(auditValues.get(index), path), path));
        }
        return CombatSession.restore(state, audit);
    }

    /** Alias that describes the concrete representation consumed by {@link #decode}. */
    public CombatSession fromMap(Map<String, ?> document) {
        return decode(document);
    }

    private Map<String, Object> encodeState(CombatState state) {
        Map<String, Object> combatants = new LinkedHashMap<>();
        state.combatants().forEach((id, combatant) -> combatants.put(id, encodeCombatant(combatant)));

        Map<String, Object> initiativeScores = new LinkedHashMap<>();
        state.initiativeScores().forEach(initiativeScores::put);

        Map<String, Object> turnBudgets = new LinkedHashMap<>();
        state.turnBudgets().forEach((id, budget) -> turnBudgets.put(id, encodeTurnBudget(budget)));

        return object(
                "encounterId", state.encounterId(),
                "rulesetVersion", state.rulesetVersion(),
                "contentVersion", state.contentVersion(),
                "status", state.status().name(),
                "revision", state.revision(),
                "randomSeed", state.randomSeed(),
                "randomState", state.randomState(),
                "rosterOrder", state.rosterOrder(),
                "combatants", combatants,
                "initiativeScores", initiativeScores,
                "initiativeOrder", state.initiativeOrder(),
                "round", state.round(),
                "turnIndex", state.turnIndex(),
                "turnBudgets", turnBudgets,
                "partyCombatantIds", state.partyCombatantIds().stream().sorted().toList(),
                "simultaneousTies", state.simultaneousTies(),
                "battleMap", encodeBattleMap(state.battleMap()));
    }

    private CombatState decodeState(Map<?, ?> value, String path) {
        Map<?, ?> combatantValues = objectValue(value, "combatants", path);
        Map<String, CombatantState> combatants = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : combatantValues.entrySet()) {
            String id = mapKey(entry.getKey(), path + ".combatants");
            String combatantPath = member(path + ".combatants", id);
            combatants.put(id, decodeCombatant(asObject(entry.getValue(), combatantPath), combatantPath));
        }

        Map<?, ?> initiativeValues = objectValue(value, "initiativeScores", path);
        Map<String, Integer> initiativeScores = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : initiativeValues.entrySet()) {
            String id = mapKey(entry.getKey(), path + ".initiativeScores");
            initiativeScores.put(id, asInteger(entry.getValue(), member(path + ".initiativeScores", id)));
        }

        Map<?, ?> budgetValues = objectValue(value, "turnBudgets", path);
        Map<String, TurnBudget> turnBudgets = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : budgetValues.entrySet()) {
            String id = mapKey(entry.getKey(), path + ".turnBudgets");
            String budgetPath = member(path + ".turnBudgets", id);
            turnBudgets.put(id, decodeTurnBudget(asObject(entry.getValue(), budgetPath), budgetPath));
        }

        return new CombatState(
                string(value, "encounterId", path),
                string(value, "rulesetVersion", path),
                string(value, "contentVersion", path),
                enumeration(value, "status", path, CombatStatus::valueOf),
                longInteger(value, "revision", path),
                longInteger(value, "randomSeed", path),
                longInteger(value, "randomState", path),
                stringList(value, "rosterOrder", path),
                combatants,
                initiativeScores,
                stringList(value, "initiativeOrder", path),
                integer(value, "round", path),
                integer(value, "turnIndex", path),
                turnBudgets,
                optionalStringSet(value, "partyCombatantIds", path),
                // Aggiunta dopo: un salvataggio precedente non ha la chiave e va
                // letto come turni separati, che era il solo comportamento possibile.
                value.containsKey("simultaneousTies") && bool(value, "simultaneousTies", path),
                // Aggiunta dopo: un salvataggio senza mappa resta un incontro astratto.
                value.containsKey("battleMap")
                        ? decodeBattleMap(objectValue(value, "battleMap", path), path + ".battleMap")
                        : BattleMap.none());
    }

    /** Backward-compatible with early schema-v1 saves created before sides were embedded. */
    private Set<String> optionalStringSet(Map<?, ?> value, String member, String path) {
        if (!value.containsKey(member)) return Set.of();
        List<String> values = stringList(value, member, path);
        Set<String> result = new LinkedHashSet<>(values);
        if (result.size() != values.size()) {
            throw invalid(path + "." + member, "contains duplicate values");
        }
        return Set.copyOf(result);
    }

    private Map<String, Object> encodeBattleMap(BattleMap map) {
        Map<String, Object> placements = new LinkedHashMap<>();
        map.placements().forEach((id, placement) -> placements.put(id, object(
                "position", placement.origin().toString(),
                "squaresPerSide", placement.squaresPerSide())));
        MapBackground background = map.background();
        return object(
                "columns", map.grid().columns(),
                "rows", map.grid().rows(),
                "feetPerSquare", map.grid().feetPerSquare(),
                "backgroundImage", map.backgroundImage(),
                "background", object(
                        "offsetX", background.offsetX(),
                        "offsetY", background.offsetY(),
                        "width", background.width(),
                        "height", background.height()),
                "placements", placements);
    }

    private BattleMap decodeBattleMap(Map<?, ?> value, String path) {
        MapGrid grid = new MapGrid(
                integer(value, "columns", path),
                integer(value, "rows", path),
                integer(value, "feetPerSquare", path));

        Map<?, ?> placementValues = objectValue(value, "placements", path);
        Map<String, TokenPlacement> placements = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : placementValues.entrySet()) {
            String id = mapKey(entry.getKey(), path + ".placements");
            String placementPath = member(path + ".placements", id);
            Map<?, ?> placement = asObject(entry.getValue(), placementPath);
            placements.put(id, new TokenPlacement(
                    id,
                    GridPosition.parse(string(placement, "position", placementPath)),
                    integer(placement, "squaresPerSide", placementPath)));
        }

        // La collocazione dello sfondo e' facoltativa: i salvataggi precedenti non
        // la conoscono e ripartono da UNSET, cosi' l'interfaccia la ricalcola.
        MapBackground background = MapBackground.UNSET;
        if (value.containsKey("background")) {
            Map<?, ?> bg = objectValue(value, "background", path);
            String bgPath = member(path, "background");
            background = new MapBackground(
                    number(bg, "offsetX", bgPath),
                    number(bg, "offsetY", bgPath),
                    number(bg, "width", bgPath),
                    number(bg, "height", bgPath));
        }
        return new BattleMap(grid, placements, string(value, "backgroundImage", path), background);
    }

    private Map<String, Object> encodeCombatant(CombatantState combatant) {
        return object(
                "snapshot", encodeSnapshot(combatant.snapshot()),
                "currentHitPoints", combatant.currentHitPoints(),
                "temporaryHitPoints", combatant.temporaryHitPoints(),
                "conditions", combatant.conditions().stream().map(this::encodeCondition).toList(),
                "concentration", combatant.concentration() == null
                        ? null
                        : encodeConcentration(combatant.concentration()),
                "deathSaves", encodeDeathSaves(combatant.deathSaves()),
                "exhaustionLevel", combatant.exhaustionLevel());
    }

    private Map<String, Object> encodeDeathSaves(DeathSaveState deathSaves) {
        return object(
                "successes", deathSaves.successes(),
                "failures", deathSaves.failures(),
                "stable", deathSaves.stable());
    }

    private CombatantState decodeCombatant(Map<?, ?> value, String path) {
        List<?> conditionValues = list(value, "conditions", path);
        List<ConditionInstance> conditions = new ArrayList<>(conditionValues.size());
        for (int index = 0; index < conditionValues.size(); index++) {
            String conditionPath = path + ".conditions[" + index + ']';
            conditions.add(decodeCondition(asObject(conditionValues.get(index), conditionPath), conditionPath));
        }

        Object concentrationValue = required(value, "concentration", path);
        ConcentrationState concentration = concentrationValue == null
                ? null
                : decodeConcentration(asObject(concentrationValue, path + ".concentration"), path + ".concentration");
        // Morte ed Exhaustion sono stati aggiunti dopo: un salvataggio scritto prima
        // non ha queste chiavi e va letto assumendo lo stato iniziale, non rifiutato.
        DeathSaveState deathSaves = value.containsKey("deathSaves")
                ? decodeDeathSaves(objectValue(value, "deathSaves", path), path + ".deathSaves")
                : DeathSaveState.none();
        int exhaustionLevel = value.containsKey("exhaustionLevel")
                ? integer(value, "exhaustionLevel", path)
                : 0;

        return new CombatantState(
                decodeSnapshot(objectValue(value, "snapshot", path), path + ".snapshot"),
                integer(value, "currentHitPoints", path),
                integer(value, "temporaryHitPoints", path),
                conditions,
                concentration,
                deathSaves,
                exhaustionLevel);
    }

    private DeathSaveState decodeDeathSaves(Map<?, ?> value, String path) {
        return new DeathSaveState(
                integer(value, "successes", path),
                integer(value, "failures", path),
                bool(value, "stable", path));
    }

    private Map<String, Object> encodeSnapshot(CombatantSnapshot snapshot) {
        return object(
                "instanceId", snapshot.instanceId(),
                "definitionId", snapshot.definitionId(),
                "definitionVersion", snapshot.definitionVersion(),
                "rulesetVersion", snapshot.rulesetVersion(),
                "name", snapshot.name(),
                "armorClass", snapshot.armorClass(),
                "maxHitPoints", snapshot.maxHitPoints(),
                "initialHitPoints", snapshot.initialHitPoints(),
                "initialTemporaryHitPoints", snapshot.initialTemporaryHitPoints(),
                "speedFeet", snapshot.speedFeet(),
                "initiativeModifier", snapshot.initiativeModifier(),
                "initiativeScore", snapshot.initiativeScore(),
                "constitutionSaveBonus", snapshot.constitutionSaveBonus(),
                "resistances", enumNames(snapshot.resistances()),
                "vulnerabilities", enumNames(snapshot.vulnerabilities()),
                "damageImmunities", enumNames(snapshot.damageImmunities()),
                "conditionImmunities", enumNames(snapshot.conditionImmunities()),
                "abilities", snapshot.abilities().stream().map(this::encodeAbility).toList());
    }

    private CombatantSnapshot decodeSnapshot(Map<?, ?> value, String path) {
        List<?> abilityValues = list(value, "abilities", path);
        List<AbilityDefinition> abilities = new ArrayList<>(abilityValues.size());
        for (int index = 0; index < abilityValues.size(); index++) {
            String abilityPath = path + ".abilities[" + index + ']';
            abilities.add(decodeAbility(asObject(abilityValues.get(index), abilityPath), abilityPath));
        }
        return new CombatantSnapshot(
                string(value, "instanceId", path),
                string(value, "definitionId", path),
                string(value, "definitionVersion", path),
                string(value, "rulesetVersion", path),
                string(value, "name", path),
                integer(value, "armorClass", path),
                integer(value, "maxHitPoints", path),
                integer(value, "initialHitPoints", path),
                integer(value, "initialTemporaryHitPoints", path),
                integer(value, "speedFeet", path),
                integer(value, "initiativeModifier", path),
                integer(value, "initiativeScore", path),
                integer(value, "constitutionSaveBonus", path),
                enumSet(value, "resistances", path, DamageType::valueOf),
                enumSet(value, "vulnerabilities", path, DamageType::valueOf),
                enumSet(value, "damageImmunities", path, DamageType::valueOf),
                enumSet(value, "conditionImmunities", path, ConditionType::valueOf),
                abilities);
    }

    private Map<String, Object> encodeAbility(AbilityDefinition ability) {
        return object(
                "id", ability.id(),
                "version", ability.version(),
                "source", ability.source(),
                "rulesetVersion", ability.rulesetVersion(),
                "name", ability.name(),
                "activationCost", ability.activationCost().name(),
                "resolutionMethod", ability.resolutionMethod().name(),
                "attackBonus", ability.attackBonus(),
                "rangeFeet", ability.rangeFeet(),
                "maxTargets", ability.maxTargets(),
                "damage", ability.damage().stream().map(this::encodeDamageFormula).toList(),
                "automationStatus", ability.automationStatus().name(),
                "rulesText", ability.rulesText());
    }

    private AbilityDefinition decodeAbility(Map<?, ?> value, String path) {
        List<?> damageValues = list(value, "damage", path);
        List<DamageFormula> damage = new ArrayList<>(damageValues.size());
        for (int index = 0; index < damageValues.size(); index++) {
            String damagePath = path + ".damage[" + index + ']';
            damage.add(decodeDamageFormula(asObject(damageValues.get(index), damagePath), damagePath));
        }
        return new AbilityDefinition(
                string(value, "id", path),
                string(value, "version", path),
                string(value, "source", path),
                string(value, "rulesetVersion", path),
                string(value, "name", path),
                enumeration(value, "activationCost", path, ActivationCost::valueOf),
                enumeration(value, "resolutionMethod", path, ResolutionMethod::valueOf),
                integer(value, "attackBonus", path),
                integer(value, "rangeFeet", path),
                integer(value, "maxTargets", path),
                damage,
                enumeration(value, "automationStatus", path, AutomationStatus::valueOf),
                string(value, "rulesText", path));
    }

    private Map<String, Object> encodeDamageFormula(DamageFormula formula) {
        return object(
                "type", formula.type().name(),
                "dice", formula.dice() == null ? null : encodeDice(formula.dice()),
                "fixedAmount", formula.fixedAmount());
    }

    private DamageFormula decodeDamageFormula(Map<?, ?> value, String path) {
        Object diceValue = required(value, "dice", path);
        DiceExpression dice = diceValue == null
                ? null
                : decodeDice(asObject(diceValue, path + ".dice"), path + ".dice");
        return new DamageFormula(
                enumeration(value, "type", path, DamageType::valueOf),
                dice,
                nullableInteger(value, "fixedAmount", path));
    }

    private Map<String, Object> encodeDice(DiceExpression dice) {
        return object("count", dice.count(), "sides", dice.sides(), "modifier", dice.modifier());
    }

    private DiceExpression decodeDice(Map<?, ?> value, String path) {
        return new DiceExpression(
                integer(value, "count", path),
                integer(value, "sides", path),
                integer(value, "modifier", path));
    }

    private Map<String, Object> encodeCondition(ConditionInstance condition) {
        return object(
                "id", condition.id(),
                "type", condition.type().name(),
                "sourceCombatantId", condition.sourceCombatantId(),
                "sourceAbilityId", condition.sourceAbilityId(),
                "appliedRound", condition.appliedRound(),
                "duration", encodeDuration(condition.duration()),
                "concentrationOwnerId", condition.concentrationOwnerId(),
                "note", condition.note());
    }

    private ConditionInstance decodeCondition(Map<?, ?> value, String path) {
        return new ConditionInstance(
                string(value, "id", path),
                enumeration(value, "type", path, ConditionType::valueOf),
                string(value, "sourceCombatantId", path),
                string(value, "sourceAbilityId", path),
                integer(value, "appliedRound", path),
                decodeDuration(objectValue(value, "duration", path), path + ".duration"),
                string(value, "concentrationOwnerId", path),
                string(value, "note", path));
    }

    private Map<String, Object> encodeDuration(ConditionDuration duration) {
        return object(
                "expiry", duration.expiry().name(),
                "remainingOccurrences", duration.remainingOccurrences());
    }

    private ConditionDuration decodeDuration(Map<?, ?> value, String path) {
        return new ConditionDuration(
                enumeration(value, "expiry", path, ConditionExpiry::valueOf),
                integer(value, "remainingOccurrences", path));
    }

    private Map<String, Object> encodeConcentration(ConcentrationState concentration) {
        return object(
                "abilityId", concentration.abilityId(),
                "startedRound", concentration.startedRound());
    }

    private ConcentrationState decodeConcentration(Map<?, ?> value, String path) {
        return new ConcentrationState(
                string(value, "abilityId", path),
                integer(value, "startedRound", path));
    }

    private Map<String, Object> encodeTurnBudget(TurnBudget budget) {
        return object(
                "movementAllowanceFeet", budget.movementAllowanceFeet(),
                "movementSpentFeet", budget.movementSpentFeet(),
                "actionAvailable", budget.actionAvailable(),
                "bonusActionAvailable", budget.bonusActionAvailable(),
                "reactionAvailable", budget.reactionAvailable(),
                "objectInteractionAvailable", budget.objectInteractionAvailable(),
                "attacksRemaining", budget.attacksRemaining(),
                "spellSlotSpentThisTurn", budget.spellSlotSpentThisTurn());
    }

    private TurnBudget decodeTurnBudget(Map<?, ?> value, String path) {
        return new TurnBudget(
                integer(value, "movementAllowanceFeet", path),
                integer(value, "movementSpentFeet", path),
                bool(value, "actionAvailable", path),
                bool(value, "bonusActionAvailable", path),
                bool(value, "reactionAvailable", path),
                bool(value, "objectInteractionAvailable", path),
                integer(value, "attacksRemaining", path),
                bool(value, "spellSlotSpentThisTurn", path));
    }

    private Map<String, Object> encodeEvent(CombatEvent event) {
        return object(
                "sequence", event.sequence(),
                "revision", event.revision(),
                "type", event.type().name(),
                "round", event.round(),
                "actorId", event.actorId(),
                "targetId", event.targetId(),
                "details", new LinkedHashMap<>(event.details()));
    }

    private CombatEvent decodeEvent(Map<?, ?> value, String path) {
        Map<?, ?> detailValues = objectValue(value, "details", path);
        Map<String, String> details = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : detailValues.entrySet()) {
            String key = mapKey(entry.getKey(), path + ".details");
            if (!(entry.getValue() instanceof String detail)) {
                throw invalid(member(path + ".details", key), "expected a string");
            }
            details.put(key, detail);
        }
        return new CombatEvent(
                longInteger(value, "sequence", path),
                longInteger(value, "revision", path),
                enumeration(value, "type", path, EventType::valueOf),
                integer(value, "round", path),
                string(value, "actorId", path),
                string(value, "targetId", path),
                details);
    }

    private static List<String> enumNames(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().toList();
    }

    private static Object required(Map<?, ?> object, String key, String path) {
        if (!object.containsKey(key)) {
            throw invalid(member(path, key), "missing required value");
        }
        return object.get(key);
    }

    private static Map<?, ?> objectValue(Map<?, ?> object, String key, String path) {
        return asObject(required(object, key, path), member(path, key));
    }

    private static Map<?, ?> asObject(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(path, "expected an object");
        }
        for (Object key : map.keySet()) {
            mapKey(key, path);
        }
        return map;
    }

    private static List<?> list(Map<?, ?> object, String key, String path) {
        Object value = required(object, key, path);
        if (!(value instanceof List<?> values)) {
            throw invalid(member(path, key), "expected an array");
        }
        return values;
    }

    private static List<String> stringList(Map<?, ?> object, String key, String path) {
        List<?> values = list(object, key, path);
        List<String> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof String text)) {
                throw invalid(member(path, key) + '[' + index + ']', "expected a string");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static String string(Map<?, ?> object, String key, String path) {
        Object value = required(object, key, path);
        if (!(value instanceof String text)) {
            throw invalid(member(path, key), "expected a string");
        }
        return text;
    }

    private static boolean bool(Map<?, ?> object, String key, String path) {
        Object value = required(object, key, path);
        if (!(value instanceof Boolean bool)) {
            throw invalid(member(path, key), "expected a boolean");
        }
        return bool;
    }

    private static Integer nullableInteger(Map<?, ?> object, String key, String path) {
        Object value = required(object, key, path);
        return value == null ? null : asInteger(value, member(path, key));
    }

    private static int integer(Map<?, ?> object, String key, String path) {
        return asInteger(required(object, key, path), member(path, key));
    }

    private static double number(Map<?, ?> object, String key, String path) {
        Object value = required(object, key, path);
        if (!(value instanceof Number decimal)) {
            throw invalid(member(path, key), "expected a number");
        }
        double result = decimal.doubleValue();
        if (!Double.isFinite(result)) {
            throw invalid(member(path, key), "expected a finite number");
        }
        return result;
    }

    private static int asInteger(Object value, String path) {
        long number = asLong(value, path);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw invalid(path, "integer is outside the 32-bit range");
        }
        return (int) number;
    }

    private static long longInteger(Map<?, ?> object, String key, String path) {
        return asLong(required(object, key, path), member(path, key));
    }

    private static long asLong(Object value, String path) {
        if (!(value instanceof Number number)) {
            throw invalid(path, "expected an integer");
        }
        try {
            if (number instanceof BigInteger integer) {
                return integer.longValueExact();
            }
            if (number instanceof BigDecimal decimal) {
                return decimal.longValueExact();
            }
            if (number instanceof Byte || number instanceof Short
                    || number instanceof Integer || number instanceof Long) {
                return number.longValue();
            }
            return new BigDecimal(number.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid(path, "expected an integer in the 64-bit range");
        }
    }

    private static <E> E enumeration(
            Map<?, ?> object, String key, String path, Function<String, E> parser) {
        String enumPath = member(path, key);
        String value = string(object, key, path);
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException exception) {
            throw invalid(enumPath, "unknown enum value '" + value + "'");
        }
    }

    private static <E> Set<E> enumSet(
            Map<?, ?> object, String key, String path, Function<String, E> parser) {
        List<?> values = list(object, key, path);
        LinkedHashSet<E> result = new LinkedHashSet<>();
        for (int index = 0; index < values.size(); index++) {
            String itemPath = member(path, key) + '[' + index + ']';
            Object item = values.get(index);
            if (!(item instanceof String text)) {
                throw invalid(itemPath, "expected a string");
            }
            E parsed;
            try {
                parsed = parser.apply(text);
            } catch (IllegalArgumentException exception) {
                throw invalid(itemPath, "unknown enum value '" + text + "'");
            }
            if (!result.add(parsed)) {
                throw invalid(itemPath, "duplicate enum value '" + text + "'");
            }
        }
        return Set.copyOf(result);
    }

    private static String mapKey(Object key, String path) {
        if (!(key instanceof String text)) {
            throw invalid(path, "object keys must be strings");
        }
        return text;
    }

    private static String member(String parent, String key) {
        if (key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return parent + '.' + key;
        }
        return parent + "['" + key.replace("'", "\\'") + "']";
    }

    private static IllegalArgumentException invalid(String path, String detail) {
        return new IllegalArgumentException("Invalid combat session JSON at " + path + ": " + detail);
    }

    private static Map<String, Object> object(Object... entries) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }
}
