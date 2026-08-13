package app.d6d.persistence.catalog;

import app.d6d.domain.campaign.ActorKind;
import app.d6d.domain.campaign.ActorTemplate;
import app.d6d.domain.catalog.ActorCatalogEntry;
import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.AbilityEffect;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AutomationStatus;
import app.d6d.domain.combat.CombatResourceState;
import app.d6d.domain.combat.ConditionType;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.DiceExpression;
import app.d6d.domain.combat.HealingDefinition;
import app.d6d.domain.combat.HealingSlotScaling;
import app.d6d.domain.combat.HealingTarget;
import app.d6d.domain.combat.ResolutionMethod;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Explicit JSON-object codec for the actor catalog.
 *
 * <p>The encoded value only contains the primitive values, lists and maps
 * supported by the project's dependency-free JSON codec. Every domain field
 * is mapped deliberately; no reflection or Java object serialization is
 * involved.</p>
 */
public final class ActorCatalogJsonCodec {
    public static final int SCHEMA_VERSION = 1;

    /** Public because callers may prefer to treat codecs as collaborators. */
    public ActorCatalogJsonCodec() {
    }

    /** Encodes a complete catalog as a schema-versioned JSON object tree. */
    public static Map<String, Object> encode(List<ActorCatalogEntry> catalog) {
        if (catalog == null) {
            throw new NullPointerException("catalog");
        }

        List<Object> entries = new ArrayList<>(catalog.size());
        Set<String> stableIds = new LinkedHashSet<>();
        for (int index = 0; index < catalog.size(); index++) {
            ActorCatalogEntry entry = catalog.get(index);
            if (entry == null) {
                throw new IllegalArgumentException("Catalog entry at index " + index + " cannot be null");
            }
            if (!stableIds.add(entry.template().id())) {
                throw new IllegalArgumentException("Duplicate stable actor id: " + entry.template().id());
            }
            entries.add(encodeEntry(entry));
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", SCHEMA_VERSION);
        document.put("entries", entries);
        return document;
    }

    /** Decodes and validates a complete catalog object tree. */
    public static List<ActorCatalogEntry> decode(Map<String, Object> document) {
        if (document == null) {
            throw formatError("$", "expected an object, but was null");
        }

        int schemaVersion = integer(required(document, "schemaVersion", "$"), "$.schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw formatError(
                    "$.schemaVersion",
                    "unsupported schema version " + schemaVersion + "; expected " + SCHEMA_VERSION);
        }

        List<?> encodedEntries = array(required(document, "entries", "$"), "$.entries");
        List<ActorCatalogEntry> entries = new ArrayList<>(encodedEntries.size());
        Set<String> stableIds = new LinkedHashSet<>();
        for (int index = 0; index < encodedEntries.size(); index++) {
            String path = "$.entries[" + index + ']';
            ActorCatalogEntry entry = decodeEntry(object(encodedEntries.get(index), path), path);
            if (!stableIds.add(entry.template().id())) {
                throw formatError(path + ".template.id", "duplicate stable actor id '" + entry.template().id() + "'");
            }
            entries.add(entry);
        }
        return List.copyOf(entries);
    }

    /** Alias that makes the map representation explicit at call sites. */
    public static Map<String, Object> toMap(List<ActorCatalogEntry> catalog) {
        return encode(catalog);
    }

    /** Alias that makes the map representation explicit at call sites. */
    public static List<ActorCatalogEntry> fromMap(Map<String, Object> document) {
        return decode(document);
    }

    private static Map<String, Object> encodeEntry(ActorCatalogEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("template", encodeTemplate(entry.template()));
        result.put("activePartyMember", entry.activePartyMember());
        result.put("challengeRating", entry.challengeRating());
        result.put("xp", entry.xp());
        result.put("combatDefinition", encodeActorDefinition(entry.combatDefinition()));
        return result;
    }

    private static Map<String, Object> encodeTemplate(ActorTemplate template) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", template.id());
        result.put("name", template.name());
        result.put("kind", template.kind().name());
        result.put("level", template.level());
        result.put("attributes", new LinkedHashMap<>(new TreeMap<>(template.attributes())));
        return result;
    }

    private static Map<String, Object> encodeActorDefinition(ActorDefinition definition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", definition.id());
        result.put("definitionVersion", definition.definitionVersion());
        result.put("rulesetVersion", definition.rulesetVersion());
        result.put("name", definition.name());
        result.put("armorClass", definition.armorClass());
        result.put("maxHitPoints", definition.maxHitPoints());
        result.put("currentHitPoints", definition.currentHitPoints());
        result.put("temporaryHitPoints", definition.temporaryHitPoints());
        result.put("speedFeet", definition.speedFeet());
        result.put("initiativeModifier", definition.initiativeModifier());
        result.put("initiativeScore", definition.initiativeScore());
        result.put("constitutionSaveBonus", definition.constitutionSaveBonus());
        Map<String, Object> savingThrowBonuses = new LinkedHashMap<>();
        definition.savingThrowBonuses().forEach((ability, bonus) -> savingThrowBonuses.put(ability.name(), bonus));
        result.put("savingThrowBonuses", savingThrowBonuses);
        result.put("spellSaveDc", definition.spellSaveDc());
        result.put("attacksPerAction", definition.attacksPerAction());
        result.put(
                "strengthDexterityD20Disadvantage",
                definition.strengthDexterityD20Disadvantage());
        result.put("resistances", enumNames(definition.resistances()));
        result.put("vulnerabilities", enumNames(definition.vulnerabilities()));
        result.put("damageImmunities", enumNames(definition.damageImmunities()));
        result.put("conditionImmunities", enumNames(definition.conditionImmunities()));

        List<Object> abilities = new ArrayList<>(definition.abilities().size());
        for (AbilityDefinition ability : definition.abilities()) {
            abilities.add(encodeAbility(ability));
        }
        result.put("abilities", abilities);

        List<Object> resources = new ArrayList<>(definition.resources().size());
        for (CombatResourceState resource : definition.resources()) {
            resources.add(encodeResource(resource));
        }
        result.put("resources", resources);
        return result;
    }

    private static Map<String, Object> encodeAbility(AbilityDefinition ability) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", ability.id());
        result.put("version", ability.version());
        result.put("source", ability.source());
        result.put("rulesetVersion", ability.rulesetVersion());
        result.put("name", ability.name());
        result.put("activationCost", ability.activationCost().name());
        result.put("resolutionMethod", ability.resolutionMethod().name());
        result.put("attackBonus", ability.attackBonus());
        result.put("rangeFeet", ability.rangeFeet());
        result.put("maxTargets", ability.maxTargets());
        result.put("areaRadiusFeet", ability.areaRadiusFeet());
        if (ability.saveAbility() != null) {
            result.put("saveAbility", ability.saveAbility().name());
        }
        result.put("halfOnSave", ability.halfOnSave());
        if (ability.attackAbility() != null) {
            result.put("attackAbility", ability.attackAbility().name());
        }
        result.put("spellOrCantrip", ability.spellOrCantrip());

        List<Object> damage = new ArrayList<>(ability.damage().size());
        for (DamageFormula formula : ability.damage()) {
            damage.add(encodeDamageFormula(formula));
        }
        result.put("damage", damage);
        result.put("automationStatus", ability.automationStatus().name());
        result.put("rulesText", ability.rulesText());
        result.put("passive", ability.passive());
        result.put("effect", ability.effect().name());
        result.put("resourceId", ability.resourceId());
        result.put("resourceCost", ability.resourceCost());
        result.put("healing", ability.healing() == null ? null : encodeHealing(ability.healing()));
        return result;
    }

    private static Map<String, Object> encodeHealing(HealingDefinition healing) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("target", healing.target().name());
        if (healing.dice() == null) {
            result.put("dice", null);
        } else {
            Map<String, Object> dice = new LinkedHashMap<>();
            dice.put("count", healing.dice().count());
            dice.put("sides", healing.dice().sides());
            dice.put("modifier", healing.dice().modifier());
            result.put("dice", dice);
        }
        result.put("fixedAmount", healing.fixedAmount());
        if (healing.slotScaling() == null) {
            result.put("slotScaling", null);
        } else {
            Map<String, Object> scaling = new LinkedHashMap<>();
            scaling.put("baseSlotLevel", healing.slotScaling().baseSlotLevel());
            scaling.put("additionalDicePerSlotLevel", healing.slotScaling().additionalDicePerSlotLevel());
            result.put("slotScaling", scaling);
        }
        return result;
    }

    private static Map<String, Object> encodeResource(CombatResourceState resource) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resource.id());
        result.put("name", resource.name());
        result.put("maximum", resource.maximum());
        result.put("spent", resource.spent());
        return result;
    }

    private static Map<String, Object> encodeDamageFormula(DamageFormula formula) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", formula.type().name());
        if (formula.dice() == null) {
            result.put("dice", null);
        } else {
            Map<String, Object> dice = new LinkedHashMap<>();
            dice.put("count", formula.dice().count());
            dice.put("sides", formula.dice().sides());
            dice.put("modifier", formula.dice().modifier());
            result.put("dice", dice);
        }
        result.put("fixedAmount", formula.fixedAmount());
        return result;
    }

    private static ActorCatalogEntry decodeEntry(Map<String, Object> value, String path) {
        ActorTemplate template = decodeTemplate(
                object(required(value, "template", path), path + ".template"),
                path + ".template");
        boolean activePartyMember = bool(
                required(value, "activePartyMember", path),
                path + ".activePartyMember");
        BigDecimal challengeRating = decimal(
                required(value, "challengeRating", path),
                path + ".challengeRating");
        long xp = longInteger(required(value, "xp", path), path + ".xp");
        ActorDefinition definition = decodeActorDefinition(
                object(required(value, "combatDefinition", path), path + ".combatDefinition"),
                path + ".combatDefinition");

        try {
            return new ActorCatalogEntry(template, definition, activePartyMember, challengeRating, xp);
        } catch (IllegalArgumentException exception) {
            throw formatError(path, messageOf(exception));
        }
    }

    private static ActorTemplate decodeTemplate(Map<String, Object> value, String path) {
        String id = text(required(value, "id", path), path + ".id");
        String name = text(required(value, "name", path), path + ".name");
        ActorKind kind = actorKind(required(value, "kind", path), path + ".kind");
        int level = integer(required(value, "level", path), path + ".level");
        Map<String, String> attributes = stringMap(
                required(value, "attributes", path),
                path + ".attributes");

        try {
            return new ActorTemplate(id, name, kind, level, attributes);
        } catch (IllegalArgumentException exception) {
            throw formatError(path, messageOf(exception));
        }
    }

    private static ActorDefinition decodeActorDefinition(Map<String, Object> value, String path) {
        String id = text(required(value, "id", path), path + ".id");
        String definitionVersion = text(
                required(value, "definitionVersion", path),
                path + ".definitionVersion");
        String rulesetVersion = text(
                required(value, "rulesetVersion", path),
                path + ".rulesetVersion");
        String name = text(required(value, "name", path), path + ".name");
        int armorClass = integer(required(value, "armorClass", path), path + ".armorClass");
        int maxHitPoints = integer(required(value, "maxHitPoints", path), path + ".maxHitPoints");
        int currentHitPoints = integer(
                required(value, "currentHitPoints", path),
                path + ".currentHitPoints");
        int temporaryHitPoints = integer(
                required(value, "temporaryHitPoints", path),
                path + ".temporaryHitPoints");
        int speedFeet = integer(required(value, "speedFeet", path), path + ".speedFeet");
        int initiativeModifier = integer(
                required(value, "initiativeModifier", path),
                path + ".initiativeModifier");
        int initiativeScore = integer(
                required(value, "initiativeScore", path),
                path + ".initiativeScore");
        int constitutionSaveBonus = integer(
                required(value, "constitutionSaveBonus", path),
                path + ".constitutionSaveBonus");
        // Campi opzionali: i file salvati prima degli incantesimi ad area non li hanno.
        Map<SaveAbility, Integer> savingThrowBonuses = savingThrowBonuses(
                value.get("savingThrowBonuses"), path + ".savingThrowBonuses");
        int spellSaveDc = value.get("spellSaveDc") == null
                ? 0
                : integer(value.get("spellSaveDc"), path + ".spellSaveDc");
        int attacksPerAction = value.get("attacksPerAction") == null
                ? 1
                : integer(value.get("attacksPerAction"), path + ".attacksPerAction");
        boolean strengthDexterityD20Disadvantage =
                value.get("strengthDexterityD20Disadvantage") != null
                        && bool(
                                value.get("strengthDexterityD20Disadvantage"),
                                path + ".strengthDexterityD20Disadvantage");
        Set<DamageType> resistances = damageTypes(
                required(value, "resistances", path),
                path + ".resistances");
        Set<DamageType> vulnerabilities = damageTypes(
                required(value, "vulnerabilities", path),
                path + ".vulnerabilities");
        Set<DamageType> damageImmunities = damageTypes(
                required(value, "damageImmunities", path),
                path + ".damageImmunities");
        Set<ConditionType> conditionImmunities = conditionTypes(
                required(value, "conditionImmunities", path),
                path + ".conditionImmunities");

        List<?> encodedAbilities = array(required(value, "abilities", path), path + ".abilities");
        List<AbilityDefinition> abilities = new ArrayList<>(encodedAbilities.size());
        for (int index = 0; index < encodedAbilities.size(); index++) {
            String abilityPath = path + ".abilities[" + index + ']';
            abilities.add(decodeAbility(object(encodedAbilities.get(index), abilityPath), abilityPath));
        }

        List<CombatResourceState> resources = new ArrayList<>();
        if (value.get("resources") != null) {
            List<?> encodedResources = array(value.get("resources"), path + ".resources");
            for (int index = 0; index < encodedResources.size(); index++) {
                String resourcePath = path + ".resources[" + index + ']';
                resources.add(decodeResource(
                        object(encodedResources.get(index), resourcePath), resourcePath));
            }
        }

        try {
            return new ActorDefinition(
                    id,
                    definitionVersion,
                    rulesetVersion,
                    name,
                    armorClass,
                    maxHitPoints,
                    currentHitPoints,
                    temporaryHitPoints,
                    speedFeet,
                    initiativeModifier,
                    initiativeScore,
                    constitutionSaveBonus,
                    resistances,
                    vulnerabilities,
                    damageImmunities,
                    conditionImmunities,
                    abilities,
                    savingThrowBonuses,
                    spellSaveDc,
                    attacksPerAction,
                    strengthDexterityD20Disadvantage,
                    resources);
        } catch (IllegalArgumentException exception) {
            throw formatError(path, messageOf(exception));
        }
    }

    /** Bonus ai tiri salvezza, per nome di caratteristica; vuoto se il campo manca. */
    private static Map<SaveAbility, Integer> savingThrowBonuses(Object value, String path) {
        if (value == null) {
            return Map.of();
        }
        Map<String, Object> raw = object(value, path);
        Map<SaveAbility, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            result.put(SaveAbility.valueOf(entry.getKey()), integer(entry.getValue(), path + '.' + entry.getKey()));
        }
        return result;
    }

    private static AbilityDefinition decodeAbility(Map<String, Object> value, String path) {
        String id = text(required(value, "id", path), path + ".id");
        String version = text(required(value, "version", path), path + ".version");
        String source = text(required(value, "source", path), path + ".source");
        String rulesetVersion = text(
                required(value, "rulesetVersion", path),
                path + ".rulesetVersion");
        String name = text(required(value, "name", path), path + ".name");
        ActivationCost activationCost = activationCost(
                required(value, "activationCost", path),
                path + ".activationCost");
        ResolutionMethod resolutionMethod = resolutionMethod(
                required(value, "resolutionMethod", path),
                path + ".resolutionMethod");
        int attackBonus = integer(required(value, "attackBonus", path), path + ".attackBonus");
        int rangeFeet = integer(required(value, "rangeFeet", path), path + ".rangeFeet");
        int maxTargets = integer(required(value, "maxTargets", path), path + ".maxTargets");
        int areaRadiusFeet = value.get("areaRadiusFeet") == null
                ? 0
                : integer(value.get("areaRadiusFeet"), path + ".areaRadiusFeet");
        SaveAbility saveAbility = value.get("saveAbility") == null
                ? null
                : SaveAbility.valueOf(text(value.get("saveAbility"), path + ".saveAbility"));
        boolean halfOnSave = value.get("halfOnSave") != null && bool(value.get("halfOnSave"), path + ".halfOnSave");
        SaveAbility attackAbility = value.get("attackAbility") == null
                ? null
                : SaveAbility.valueOf(text(value.get("attackAbility"), path + ".attackAbility"));
        boolean spellOrCantrip =
                value.get("spellOrCantrip") != null
                        && bool(value.get("spellOrCantrip"), path + ".spellOrCantrip");

        List<?> encodedDamage = array(required(value, "damage", path), path + ".damage");
        List<DamageFormula> damage = new ArrayList<>(encodedDamage.size());
        for (int index = 0; index < encodedDamage.size(); index++) {
            String damagePath = path + ".damage[" + index + ']';
            damage.add(decodeDamageFormula(object(encodedDamage.get(index), damagePath), damagePath));
        }

        AutomationStatus automationStatus = automationStatus(
                required(value, "automationStatus", path),
                path + ".automationStatus");
        String rulesText = text(required(value, "rulesText", path), path + ".rulesText");
        boolean passive = value.get("passive") != null && bool(value.get("passive"), path + ".passive");
        AbilityEffect effect = value.get("effect") == null
                ? AbilityEffect.NONE
                : abilityEffect(value.get("effect"), path + ".effect");
        String resourceId = value.get("resourceId") == null
                ? ""
                : text(value.get("resourceId"), path + ".resourceId");
        int resourceCost = value.get("resourceCost") == null
                ? 0
                : integer(value.get("resourceCost"), path + ".resourceCost");
        HealingDefinition healing = value.get("healing") == null
                ? null
                : decodeHealing(value.get("healing"), path + ".healing");

        try {
            return new AbilityDefinition(
                    id,
                    version,
                    source,
                    rulesetVersion,
                    name,
                    activationCost,
                    resolutionMethod,
                    attackBonus,
                    rangeFeet,
                    maxTargets,
                    damage,
                    automationStatus,
                    rulesText,
                    areaRadiusFeet,
                    saveAbility,
                    halfOnSave,
                    passive,
                    attackAbility,
                    spellOrCantrip,
                    effect,
                    resourceId,
                    resourceCost,
                    healing);
        } catch (IllegalArgumentException exception) {
            throw formatError(path, messageOf(exception));
        }
    }

    private static CombatResourceState decodeResource(Map<String, Object> value, String path) {
        try {
            return new CombatResourceState(
                    text(required(value, "id", path), path + ".id"),
                    text(required(value, "name", path), path + ".name"),
                    integer(required(value, "maximum", path), path + ".maximum"),
                    integer(required(value, "spent", path), path + ".spent"));
        } catch (IllegalArgumentException exception) {
            if (exception instanceof CatalogFormatException catalogException) {
                throw catalogException;
            }
            throw formatError(path, messageOf(exception));
        }
    }

    private static HealingDefinition decodeHealing(Object encoded, String path) {
        Map<String, Object> value = object(encoded, path);
        HealingTarget target = healingTarget(
                required(value, "target", path), path + ".target");

        Object encodedDice = present(value, "dice", path);
        Object encodedFixedAmount = present(value, "fixedAmount", path);
        DiceExpression dice = null;
        Integer fixedAmount = null;
        if (encodedDice != null) {
            Map<String, Object> diceObject = object(encodedDice, path + ".dice");
            try {
                dice = new DiceExpression(
                        integer(required(diceObject, "count", path + ".dice"), path + ".dice.count"),
                        integer(required(diceObject, "sides", path + ".dice"), path + ".dice.sides"),
                        integer(required(diceObject, "modifier", path + ".dice"), path + ".dice.modifier"));
            } catch (IllegalArgumentException exception) {
                if (exception instanceof CatalogFormatException catalogException) {
                    throw catalogException;
                }
                throw formatError(path + ".dice", messageOf(exception));
            }
        }
        if (encodedFixedAmount != null) {
            fixedAmount = integer(encodedFixedAmount, path + ".fixedAmount");
        }
        HealingSlotScaling slotScaling = null;
        if (value.get("slotScaling") != null) {
            String scalingPath = path + ".slotScaling";
            Map<String, Object> encodedScaling = object(value.get("slotScaling"), scalingPath);
            try {
                slotScaling = new HealingSlotScaling(
                        integer(required(encodedScaling, "baseSlotLevel", scalingPath),
                                scalingPath + ".baseSlotLevel"),
                        integer(required(encodedScaling, "additionalDicePerSlotLevel", scalingPath),
                                scalingPath + ".additionalDicePerSlotLevel"));
            } catch (IllegalArgumentException exception) {
                if (exception instanceof CatalogFormatException catalogException) {
                    throw catalogException;
                }
                throw formatError(scalingPath, messageOf(exception));
            }
        }
        try {
            return new HealingDefinition(target, dice, fixedAmount, slotScaling);
        } catch (IllegalArgumentException exception) {
            throw formatError(path, messageOf(exception));
        }
    }

    private static DamageFormula decodeDamageFormula(Map<String, Object> value, String path) {
        DamageType type = damageType(required(value, "type", path), path + ".type");
        Object encodedDice = present(value, "dice", path);
        Object encodedFixedAmount = present(value, "fixedAmount", path);
        DiceExpression dice = null;
        Integer fixedAmount = null;

        if (encodedDice != null) {
            Map<String, Object> diceObject = object(encodedDice, path + ".dice");
            try {
                dice = new DiceExpression(
                        integer(required(diceObject, "count", path + ".dice"), path + ".dice.count"),
                        integer(required(diceObject, "sides", path + ".dice"), path + ".dice.sides"),
                        integer(required(diceObject, "modifier", path + ".dice"), path + ".dice.modifier"));
            } catch (IllegalArgumentException exception) {
                if (exception instanceof CatalogFormatException catalogException) {
                    throw catalogException;
                }
                throw formatError(path + ".dice", messageOf(exception));
            }
        }
        if (encodedFixedAmount != null) {
            fixedAmount = integer(encodedFixedAmount, path + ".fixedAmount");
        }

        try {
            return new DamageFormula(type, dice, fixedAmount);
        } catch (IllegalArgumentException exception) {
            throw formatError(path, messageOf(exception));
        }
    }

    private static List<String> enumNames(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().toList();
    }

    private static Set<DamageType> damageTypes(Object value, String path) {
        List<?> encodedValues = array(value, path);
        Set<DamageType> values = new LinkedHashSet<>();
        for (int index = 0; index < encodedValues.size(); index++) {
            DamageType decoded = damageType(encodedValues.get(index), path + '[' + index + ']');
            if (!values.add(decoded)) {
                throw formatError(path + '[' + index + ']', "duplicate damage type " + decoded.name());
            }
        }
        return Set.copyOf(values);
    }

    private static Set<ConditionType> conditionTypes(Object value, String path) {
        List<?> encodedValues = array(value, path);
        Set<ConditionType> values = new LinkedHashSet<>();
        for (int index = 0; index < encodedValues.size(); index++) {
            ConditionType decoded = conditionType(encodedValues.get(index), path + '[' + index + ']');
            if (!values.add(decoded)) {
                throw formatError(path + '[' + index + ']', "duplicate condition type " + decoded.name());
            }
        }
        return Set.copyOf(values);
    }

    private static Map<String, String> stringMap(Object value, String path) {
        Map<String, Object> encodedMap = object(value, path);
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : encodedMap.entrySet()) {
            result.put(entry.getKey(), text(entry.getValue(), memberPath(path, entry.getKey())));
        }
        return result;
    }

    private static Object required(Map<String, Object> object, String member, String path) {
        if (!object.containsKey(member)) {
            throw formatError(path, "missing required member '" + member + "'");
        }
        Object value = object.get(member);
        if (value == null) {
            throw formatError(path + '.' + member, "value cannot be null");
        }
        return value;
    }

    /** Requires the member to exist while allowing JSON null for tagged alternatives. */
    private static Object present(Map<String, Object> object, String member, String path) {
        if (!object.containsKey(member)) {
            throw formatError(path, "missing required member '" + member + "'");
        }
        return object.get(member);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) {
            throw typeError(path, "object", value);
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw formatError(path, "object member names must be strings");
            }
        }
        return (Map<String, Object>) map;
    }

    private static List<?> array(Object value, String path) {
        if (!(value instanceof List<?> list)) {
            throw typeError(path, "array", value);
        }
        return list;
    }

    private static String text(Object value, String path) {
        if (!(value instanceof String text)) {
            throw typeError(path, "string", value);
        }
        return text;
    }

    private static boolean bool(Object value, String path) {
        if (!(value instanceof Boolean bool)) {
            throw typeError(path, "boolean", value);
        }
        return bool;
    }

    private static int integer(Object value, String path) {
        BigInteger integer = exactInteger(value, path);
        if (integer.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                || integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw formatError(path, "integer is outside the 32-bit range: " + value);
        }
        return integer.intValue();
    }

    private static long longInteger(Object value, String path) {
        BigInteger integer = exactInteger(value, path);
        if (integer.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0
                || integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw formatError(path, "integer is outside the 64-bit range: " + value);
        }
        return integer.longValue();
    }

    private static BigInteger exactInteger(Object value, String path) {
        try {
            return decimal(value, path).toBigIntegerExact();
        } catch (ArithmeticException exception) {
            throw formatError(path, "expected an integer, but was " + value);
        }
    }

    private static BigDecimal decimal(Object value, String path) {
        if (!(value instanceof Number number)) {
            throw typeError(path, "number", value);
        }
        if (number instanceof Double doubleValue && !Double.isFinite(doubleValue)
                || number instanceof Float floatValue && !Float.isFinite(floatValue)) {
            throw formatError(path, "number must be finite");
        }
        try {
            return number instanceof BigDecimal decimal
                    ? decimal
                    : new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            throw formatError(path, "invalid number " + number);
        }
    }

    private static ActorKind actorKind(Object value, String path) {
        String name = text(value, path);
        try {
            return ActorKind.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw unknownEnum(path, "ActorKind", name, ActorKind.values());
        }
    }

    private static DamageType damageType(Object value, String path) {
        String name = text(value, path);
        try {
            return DamageType.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw unknownEnum(path, "DamageType", name, DamageType.values());
        }
    }

    private static HealingTarget healingTarget(Object value, String path) {
        String name = text(value, path);
        try {
            return HealingTarget.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw unknownEnum(path, "HealingTarget", name, HealingTarget.values());
        }
    }

    private static ConditionType conditionType(Object value, String path) {
        String name = text(value, path);
        try {
            return ConditionType.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw unknownEnum(path, "ConditionType", name, ConditionType.values());
        }
    }

    private static ActivationCost activationCost(Object value, String path) {
        String name = text(value, path);
        try {
            return ActivationCost.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw unknownEnum(path, "ActivationCost", name, ActivationCost.values());
        }
    }

    private static ResolutionMethod resolutionMethod(Object value, String path) {
        String name = text(value, path);
        try {
            return ResolutionMethod.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw unknownEnum(path, "ResolutionMethod", name, ResolutionMethod.values());
        }
    }

    private static AutomationStatus automationStatus(Object value, String path) {
        String name = text(value, path);
        try {
            return AutomationStatus.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw unknownEnum(path, "AutomationStatus", name, AutomationStatus.values());
        }
    }

    private static AbilityEffect abilityEffect(Object value, String path) {
        String name = text(value, path);
        try {
            return AbilityEffect.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw unknownEnum(path, "AbilityEffect", name, AbilityEffect.values());
        }
    }

    private static CatalogFormatException unknownEnum(
            String path, String enumName, String value, Enum<?>[] accepted) {
        StringBuilder values = new StringBuilder();
        for (Enum<?> candidate : accepted) {
            if (values.length() > 0) {
                values.append(", ");
            }
            values.append(candidate.name());
        }
        return formatError(
                path,
                "unknown " + enumName + " value '" + value + "'; expected one of [" + values + ']');
    }

    private static CatalogFormatException typeError(String path, String expected, Object actual) {
        String actualType = actual == null ? "null" : actual.getClass().getSimpleName();
        return formatError(path, "expected " + expected + ", but was " + actualType);
    }

    private static CatalogFormatException formatError(String path, String detail) {
        return new CatalogFormatException("Invalid actor catalog JSON at " + path + ": " + detail);
    }

    private static String memberPath(String parent, String member) {
        if (member.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return parent + '.' + member;
        }
        return parent + "['" + member.replace("'", "\\'") + "']";
    }

    private static String messageOf(IllegalArgumentException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    /** Indicates that a syntactically valid JSON tree does not match schema 1. */
    public static final class CatalogFormatException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private CatalogFormatException(String message) {
            super(message);
        }
    }
}
