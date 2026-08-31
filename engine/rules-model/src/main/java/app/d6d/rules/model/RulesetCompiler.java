package app.d6d.rules.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Compila e valida la parte strutturata di una revisione pubblicabile. */
public final class RulesetCompiler {
    private static final Set<String> LOCAL_FORMULA_VALUES = Set.of(
            "score", "current", "maximum", "amount", "stacks", "eventCount", "level", "classLevel",
            "characterLevel", "proficiency", "experience");

    private RulesetCompiler() { }

    public static CompiledRuleset compile(RulesetRevision revision) {
        Objects.requireNonNull(revision, "revision");
        return compileSnapshot(revision.canonicalHash(), revision.entities());
    }

    /** Ricompila lo snapshot autosufficiente conservato in una sessione. */
    public static CompiledRuleset compileSnapshot(String canonicalHash, List<RuleEntity> snapshotEntities) {
        Objects.requireNonNull(canonicalHash, "canonicalHash");
        List<RuleEntity> enabled = Objects.requireNonNull(snapshotEntities, "snapshotEntities")
                .stream().filter(RuleEntity::enabled).toList();
        LinkedHashMap<String, RuleEntity> entities = new LinkedHashMap<>();
        enabled.forEach(entity -> {
            if (entities.putIfAbsent(entity.id(), entity) != null) {
                throw new IllegalArgumentException("Duplicate enabled rule id: " + entity.id());
            }
        });
        Map<String, String> aliases = aliases(enabled);

        LinkedHashMap<String, CompiledRuleset.StatDefinition> stats = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.SkillDefinition> skills = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.ValueDefinition> valueDefinitions = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.RandomizerDefinition> randomizers = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.TableDefinition> tables = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.ResourceDefinition> resources = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.TurnStructureDefinition> turnStructures = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.ActionDefinition> actions = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.ModifierDefinition> modifiers = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.TriggerDefinition> triggers = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.ConditionDefinition> conditionDefinitions = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.HealthModelDefinition> healthModels = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.MovementDefinition> movementModels = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.SheetSectionDefinition> sheetSections = new LinkedHashMap<>();
        LinkedHashMap<String, CompiledRuleset.SceneProcedureDefinition> sceneProcedures = new LinkedHashMap<>();
        LinkedHashMap<String, StatePersistencePolicy> persistencePolicies = new LinkedHashMap<>();
        LinkedHashSet<String> damageTypes = new LinkedHashSet<>();
        LinkedHashSet<String> conditions = new LinkedHashSet<>();
        ArrayList<CompiledRuleset.ProgressionDefinition> progressions = new ArrayList<>();

        validateDeclaredLinks(enabled, entities, aliases);
        for (RuleEntity entity : enabled) {
            Map<String, String> attributes = entity.attributes();
            if (attributes.containsKey("activeByDefault")) bool(attributes, "activeByDefault", entity, false);
            if (entity.kind() == RuleKind.DAMAGE_TYPE) damageTypes.add(entity.id());
            if (entity.kind() == RuleKind.CONDITION) {
                conditions.add(entity.id());
                integer(attributes, "maximumStacks", entity, 1, 1, 1_000);
            }
            if (stateful(entity.kind()) && declaresStatePolicy(attributes)) {
                persistencePolicies.put(entity.id(), statePolicy(entity));
            }
            if (entity.automationLevel() == RuleAutomationLevel.MANUAL) continue;
            switch (entity.kind()) {
                case ROLL, RANDOMIZER -> randomizers.put(entity.id(), randomizer(entity));
                case STAT, SAVE, DEFENSE -> stats.put(entity.id(), stat(entity));
                case SKILL -> skills.put(entity.id(), skill(entity, aliases));
                case VALUE -> valueDefinitions.put(entity.id(), valueDefinition(entity));
                case TABLE -> tables.put(entity.id(), table(entity));
                case RESOURCE, TRACK -> resources.put(entity.id(), resource(entity));
                case ACTION_ECONOMY -> {
                    CompiledRuleset.TurnStructureDefinition structure = turnStructure(entity);
                    if (structure != null) turnStructures.put(entity.id(), structure);
                }
                case ACTION -> {
                    CompiledRuleset.ActionDefinition action = action(entity);
                    if (action != null) actions.put(entity.id(), action);
                }
                case MODIFIER -> {
                    CompiledRuleset.ModifierDefinition modifier = modifier(entity);
                    if (modifier != null) modifiers.put(entity.id(), modifier);
                }
                case TRIGGER -> triggers.put(entity.id(), trigger(entity));
                case CONDITION -> conditionDefinitions.put(entity.id(), condition(entity));
                case HEALTH_MODEL -> {
                    CompiledRuleset.HealthModelDefinition model = healthModel(entity);
                    if (model != null) healthModels.put(entity.id(), model);
                }
                case MOVEMENT -> movementModels.put(entity.id(), movement(entity));
                case SHEET_SECTION -> {
                    CompiledRuleset.SheetSectionDefinition section = sheetSection(entity);
                    if (section != null) sheetSections.put(entity.id(), section);
                }
                case SCENE_PROCEDURE -> {
                    CompiledRuleset.SceneProcedureDefinition procedure = sceneProcedure(entity);
                    if (procedure != null) sceneProcedures.put(entity.id(), procedure);
                }
                case PROGRESSION -> progressions.add(progression(entity));
                default -> { /* Rappresentabile/manuale, nessuna primitiva da compilare qui. */ }
            }
        }

        validateDefinitions(entities, aliases, stats, skills, valueDefinitions, randomizers, tables, resources, turnStructures,
                actions, modifiers, triggers, conditions, healthModels, sheetSections, sceneProcedures, progressions);
        validateFormulaReferences(stats, skills, valueDefinitions, randomizers, tables, resources, turnStructures,
                actions, modifiers, triggers, conditions, sheetSections, aliases);
        validateFormulaCycles(stats, skills, valueDefinitions, modifiers, aliases);
        validateRuntimeStateCycles(resources, turnStructures, aliases);

        CompiledRuleset.ProgressionDefinition progression = progressions.stream()
                .filter(candidate -> !candidate.experienceTableRef().isEmpty())
                .findFirst().orElse(progressions.isEmpty() ? null : progressions.get(0));
        long manualCount = enabled.stream().filter(entity -> entity.automationLevel() == RuleAutomationLevel.MANUAL).count();
        CompiledRuleset.CapabilityProfile profile = new CompiledRuleset.CapabilityProfile(
                !stats.isEmpty(), !skills.isEmpty(), !randomizers.isEmpty(),
                hasFormulas(stats, skills, resources, modifiers),
                !tables.isEmpty(), !resources.isEmpty(), !triggers.isEmpty(), !turnStructures.isEmpty(),
                !damageTypes.isEmpty(), !conditions.isEmpty(), !valueDefinitions.isEmpty(),
                !healthModels.isEmpty(), !movementModels.isEmpty(), !sheetSections.isEmpty(),
                !sceneProcedures.isEmpty(), !persistencePolicies.isEmpty(), manualCount);
        return new CompiledRuleset(canonicalHash, entities, aliases, stats, skills, valueDefinitions, randomizers, tables,
                resources, turnStructures, actions, modifiers, triggers, conditionDefinitions, healthModels,
                movementModels, sheetSections, sceneProcedures, persistencePolicies, damageTypes, conditions,
                progression, profile);
    }

    private static CompiledRuleset.RandomizerDefinition randomizer(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        CompiledRuleset.RandomizerMode fallback = attributes.containsKey("dieSides")
                ? CompiledRuleset.RandomizerMode.DICE : CompiledRuleset.RandomizerMode.MANUAL;
        CompiledRuleset.RandomizerMode mode = enumeration(attributes, "mode",
                CompiledRuleset.RandomizerMode.class, fallback, entity);
        return new CompiledRuleset.RandomizerDefinition(
                entity.id(), mode,
                formula(attributes, "countFormula", attributes.getOrDefault("diceCount", "1"), entity),
                formula(attributes, "sidesFormula", attributes.getOrDefault("dieSides", "20"), entity),
                enumeration(attributes, "keep", CompiledRuleset.KeepMode.class,
                        mode == CompiledRuleset.RandomizerMode.DICE_POOL
                                ? CompiledRuleset.KeepMode.SUCCESSES : CompiledRuleset.KeepMode.SUM,
                        entity),
                formula(attributes, "successThresholdFormula",
                        attributes.getOrDefault("successThreshold", "1"), entity),
                attributes.getOrDefault("tableRef", ""));
    }

    private static CompiledRuleset.StatDefinition stat(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        return new CompiledRuleset.StatDefinition(
                entity.id(),
                formula(attributes, "defaultFormula", attributes.getOrDefault("default", "0"), entity),
                optionalFormula(attributes, "derivedFormula", entity),
                optionalFormula(attributes, "minimumFormula", attributes.get("minimum"), entity),
                optionalFormula(attributes, "maximumFormula", attributes.get("maximum"), entity),
                optionalFormula(attributes, "modifierFormula", entity),
                enumeration(attributes, "rounding", CompiledRuleset.StatRounding.class,
                        CompiledRuleset.StatRounding.NONE, entity));
    }

    private static CompiledRuleset.SkillDefinition skill(RuleEntity entity, Map<String, String> aliases) {
        Map<String, String> attributes = entity.attributes();
        String rawStat = first(attributes, "statRef", "abilityRef", "ability");
        if (rawStat == null || rawStat.isBlank()) throw invalid(entity, "statRef", "is required");
        String statRef = resolve(rawStat, aliases);
        String defaultFormula = "${" + statRef + ":modifier}";
        return new CompiledRuleset.SkillDefinition(
                entity.id(), statRef,
                formula(attributes, "formula", defaultFormula, entity),
                formula(attributes, "trainedBonusFormula", "${proficiency}", entity));
    }

    private static CompiledRuleset.ValueDefinition valueDefinition(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        RuleValue.Type type = enumeration(attributes, "valueType", RuleValue.Type.class,
                RuleValue.Type.TEXT, entity);
        String raw = attributes.get("defaultValue");
        if (raw == null) {
            raw = switch (type) {
                case NUMBER -> "0";
                case BOOLEAN -> "false";
                case TEXT -> "";
                case REFERENCE -> throw invalid(entity, "defaultValue", "is required for a reference value");
            };
        }
        RuleValue defaultValue;
        try {
            defaultValue = new RuleValue(type, raw);
        } catch (RuntimeException failure) {
            throw invalid(entity, "defaultValue", failure.getMessage());
        }
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        try {
            csv(attributes.get("allowedValues")).forEach(value ->
                    allowed.add(new RuleValue(type, value).canonicalValue()));
        } catch (RuntimeException failure) {
            throw invalid(entity, "allowedValues", failure.getMessage());
        }
        return new CompiledRuleset.ValueDefinition(
                entity.id(), type, defaultValue, allowed,
                bool(attributes, "mutable", entity, true),
                attributes.getOrDefault("dimension", "SCALAR"),
                attributes.getOrDefault("canonicalUnit", ""));
    }

    private static CompiledRuleset.TableDefinition table(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        String encoded = required(attributes, "rows", entity);
        RuleValue.Type valueType = enumeration(attributes, "valueType", RuleValue.Type.class,
                RuleValue.Type.NUMBER, entity);
        TreeMap<BigDecimal, RuleValue> rows = new TreeMap<>();
        for (String rawRow : encoded.split(";")) {
            if (rawRow.isBlank()) continue;
            int separator = rawRow.indexOf('=');
            if (separator <= 0 || separator == rawRow.length() - 1) {
                throw invalid(entity, "rows", "contains invalid row '" + rawRow + "'");
            }
            BigDecimal key = decimal(rawRow.substring(0, separator), entity, "rows key");
            String rawValue = rawRow.substring(separator + 1).trim();
            RuleValue value;
            try {
                value = switch (valueType) {
                    case NUMBER -> RuleValue.number(new BigDecimal(rawValue));
                    case BOOLEAN -> new RuleValue(RuleValue.Type.BOOLEAN, rawValue);
                    case TEXT -> RuleValue.text(rawValue);
                    case REFERENCE -> RuleValue.reference(rawValue);
                };
            } catch (RuntimeException failure) {
                throw invalid(entity, "rows", "contains invalid value '" + rawValue + "'");
            }
            if (rows.put(key, value) != null) throw invalid(entity, "rows", "contains duplicate key " + key);
        }
        return new CompiledRuleset.TableDefinition(entity.id(), rows,
                enumeration(attributes, "lookup", CompiledRuleset.TableLookup.class,
                        CompiledRuleset.TableLookup.EXACT, entity));
    }

    private static CompiledRuleset.ResourceDefinition resource(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        return new CompiledRuleset.ResourceDefinition(
                entity.id(),
                formula(attributes, "maximumFormula", attributes.getOrDefault("maximum", "1"), entity),
                formula(attributes, "initialFormula", "${maximum}", entity),
                attributes.getOrDefault("recoveryEvent", "MANUAL"),
                formula(attributes, "recoveryFormula", "${maximum}", entity));
    }

    private static CompiledRuleset.ConditionDefinition condition(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        return new CompiledRuleset.ConditionDefinition(
                entity.id(),
                integer(attributes, "maximumStacks", entity, 1, 1, 1_000),
                enumeration(attributes, "stacking", CompiledRuleset.ConditionStacking.class,
                        CompiledRuleset.ConditionStacking.REPLACE, entity),
                bool(attributes, "sourceScoped", entity, false),
                attributes.getOrDefault("removalEvent", ""));
    }

    /** I modelli legacy senza riferimenti restano gestiti dall'adattatore SRD. */
    private static CompiledRuleset.HealthModelDefinition healthModel(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        String primary = attributes.get("primaryResourceRef");
        if (primary == null || primary.isBlank()) return null;
        return new CompiledRuleset.HealthModelDefinition(
                entity.id(),
                primary,
                csv(attributes.get("bufferResourceRefs")),
                attributes.getOrDefault("zeroConditionRef", ""),
                attributes.getOrDefault("deathConditionRef", ""),
                bool(attributes, "allowsNegative", entity, false),
                enumeration(attributes, "zeroState", CompiledRuleset.ZeroState.class,
                        CompiledRuleset.ZeroState.MANUAL, entity));
    }

    private static CompiledRuleset.MovementDefinition movement(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        String rawUnits = attributes.getOrDefault(
                "unitsPerCell",
                attributes.getOrDefault("defaultCellFeet", "1"));
        String unit = attributes.getOrDefault(
                "canonicalUnit",
                attributes.containsKey("defaultCellFeet") ? "ft" : "unit");
        return new CompiledRuleset.MovementDefinition(
                entity.id(),
                enumeration(attributes, "topology", CompiledRuleset.BoardTopology.class,
                        CompiledRuleset.BoardTopology.SQUARE, entity),
                enumeration(attributes, "diagonalRule", CompiledRuleset.DiagonalRule.class,
                        CompiledRuleset.DiagonalRule.UNIFORM, entity),
                decimal(rawUnits, entity, "unitsPerCell"),
                unit,
                bool(attributes, "elevation", entity, false),
                bool(attributes, "occupancyRequired", entity, true));
    }

    private static CompiledRuleset.SheetSectionDefinition sheetSection(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        List<String> fieldRefs = csv(attributes.get("fieldRefs"));
        if (fieldRefs.isEmpty()) return null;
        return new CompiledRuleset.SheetSectionDefinition(
                entity.id(),
                integer(attributes, "order", entity, 0, -1_000_000, 1_000_000),
                integer(attributes, "columns", entity, 1, 1, 12),
                enumeration(attributes, "layout", CompiledRuleset.SheetLayout.class,
                        CompiledRuleset.SheetLayout.LIST, entity),
                fieldRefs,
                formula(attributes, "visibilityFormula", "1", entity));
    }

    private static CompiledRuleset.SceneProcedureDefinition sceneProcedure(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        List<String> phases = csv(attributes.get("phases"));
        if (phases.isEmpty()) return null;
        return new CompiledRuleset.SceneProcedureDefinition(
                entity.id(),
                phases,
                csv(attributes.get("actionRefs")),
                csv(attributes.get("trackerRefs")),
                bool(attributes, "initiativeRequired", entity, false),
                bool(attributes, "boardRequired", entity, false));
    }

    private static boolean stateful(RuleKind kind) {
        return switch (kind) {
            case STAT, SKILL, SAVE, DEFENSE, VALUE, RESOURCE, TRACK, CONDITION, ACTION_ECONOMY -> true;
            default -> false;
        };
    }

    private static boolean declaresStatePolicy(Map<String, String> attributes) {
        return attributes.containsKey("lifetime") || attributes.containsKey("owner")
                || attributes.containsKey("syncPolicy") || attributes.containsKey("resetEvent");
    }

    private static StatePersistencePolicy statePolicy(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        return new StatePersistencePolicy(
                enumeration(attributes, "lifetime", StatePersistencePolicy.Lifetime.class,
                        StatePersistencePolicy.Lifetime.PERMANENT, entity),
                enumeration(attributes, "owner", StatePersistencePolicy.Owner.class,
                        StatePersistencePolicy.Owner.SCOPE, entity),
                enumeration(attributes, "syncPolicy", StatePersistencePolicy.SyncPolicy.class,
                        StatePersistencePolicy.SyncPolicy.LOCAL_ONLY, entity),
                attributes.getOrDefault("resetEvent", ""));
    }

    private static CompiledRuleset.TurnStructureDefinition turnStructure(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        LinkedHashMap<String, RuleFormula> budgets = new LinkedHashMap<>();
        String encoded = attributes.get("budgets");
        if (encoded != null && !encoded.isBlank()) {
            parseAssignments(encoded, entity, "budgets").forEach((id, source) ->
                    budgets.put(id, compile(source, entity, "budgets")));
        }
        legacyBudget(attributes, "actions", "action", entity, budgets);
        legacyBudget(attributes, "bonusActions", "bonus_action", entity, budgets);
        legacyBudget(attributes, "reactions", "reaction", entity, budgets);
        legacyBudget(attributes, "moveActions", "move", entity, budgets);
        legacyBudget(attributes, "swiftActions", "swift", entity, budgets);
        legacyBudget(attributes, "immediateActions", "immediate", entity, budgets);
        legacyBudget(attributes, "fullRoundActions", "full_round", entity, budgets);
        return budgets.isEmpty() ? null : new CompiledRuleset.TurnStructureDefinition(entity.id(), budgets);
    }

    private static void legacyBudget(
            Map<String, String> attributes,
            String attribute,
            String id,
            RuleEntity entity,
            Map<String, RuleFormula> target) {
        String raw = attributes.get(attribute);
        if (raw != null && !raw.isBlank()) target.put(id, compile(raw, entity, attribute));
    }

    private static CompiledRuleset.ActionDefinition action(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        String encodedCosts = attributes.getOrDefault("costs", "");
        List<String> effectRefs = csv(attributes.get("effectRefs"));
        boolean explicitlyExecutable = !encodedCosts.isBlank() || !effectRefs.isEmpty()
                || attributes.containsKey("conditionFormula") || attributes.containsKey("ownerRef");
        if (!explicitlyExecutable) return null;
        ArrayList<CompiledRuleset.ActionCost> costs = new ArrayList<>();
        for (Map.Entry<String, String> entry : parseAssignments(encodedCosts, entity, "costs").entrySet()) {
            String encodedTarget = entry.getKey();
            CompiledRuleset.CostPool pool;
            String target;
            if (encodedTarget.startsWith("turn:")) {
                pool = CompiledRuleset.CostPool.TURN;
                target = encodedTarget.substring("turn:".length());
            } else if (encodedTarget.startsWith("resource:")) {
                pool = CompiledRuleset.CostPool.RESOURCE;
                target = encodedTarget.substring("resource:".length());
            } else {
                throw invalid(entity, "costs", "target must start with turn: or resource:");
            }
            costs.add(new CompiledRuleset.ActionCost(pool, target, compile(entry.getValue(), entity, "costs")));
        }
        return new CompiledRuleset.ActionDefinition(entity.id(), attributes.getOrDefault("ownerRef", ""),
                formula(attributes, "conditionFormula", "1", entity), costs, effectRefs);
    }

    private static CompiledRuleset.ModifierDefinition modifier(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        boolean generic = attributes.containsKey("targetRef") || attributes.containsKey("valueFormula")
                || attributes.containsKey("application") || attributes.containsKey("operation");
        if (!generic) return null; // Il modificatore V1 viene validato/proiettato dall'adattatore personaggio.
        String owner = required(attributes, "ownerRef", entity);
        String target = required(attributes, "targetRef", entity);
        String valueSource = attributes.getOrDefault("valueFormula", attributes.getOrDefault("amount", "0"));
        CompiledRuleset.EffectApplication application = enumeration(
                attributes, "application", CompiledRuleset.EffectApplication.class,
                CompiledRuleset.EffectApplication.STATIC, entity);
        RuleValue literalValue = null;
        if (application == CompiledRuleset.EffectApplication.SET_VALUE) {
            RuleValue.Type type = enumeration(attributes, "valueType", RuleValue.Type.class,
                    RuleValue.Type.TEXT, entity);
            try {
                literalValue = new RuleValue(type, attributes.getOrDefault("valueLiteral", ""));
            } catch (RuntimeException failure) {
                throw invalid(entity, "valueLiteral", failure.getMessage());
            }
        }
        return new CompiledRuleset.ModifierDefinition(
                entity.id(), owner, target,
                enumeration(attributes, "operation", CompiledRuleset.ModifierOperation.class,
                        CompiledRuleset.ModifierOperation.ADD, entity),
                compile(valueSource, entity, "valueFormula"),
                formula(attributes, "conditionFormula", "1", entity),
                attributes.getOrDefault("group", ""),
                integer(attributes, "priority", entity, 0, -1_000_000, 1_000_000),
                integer(attributes, "minimumLevel", entity, 1, 1, 1_000_000),
                application,
                enumeration(attributes, "recipient", CompiledRuleset.EffectRecipient.class,
                        CompiledRuleset.EffectRecipient.SELF, entity),
                literalValue);
    }

    private static CompiledRuleset.TriggerDefinition trigger(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        return new CompiledRuleset.TriggerDefinition(
                entity.id(), required(attributes, "event", entity),
                formula(attributes, "conditionFormula", "1", entity),
                csv(required(attributes, "effectRefs", entity)),
                integer(attributes, "priority", entity, 0, -1_000_000, 1_000_000),
                integer(attributes, "maximumExecutions", entity, 1, 1, CompiledRuleset.MAX_TRIGGER_EVENTS));
    }

    private static CompiledRuleset.ProgressionDefinition progression(RuleEntity entity) {
        Map<String, String> attributes = entity.attributes();
        int minimum = integer(attributes, "minimumLevel", entity, 1, 0, 1_000_000);
        int maximum = integer(attributes, "maximumCharacterLevel", entity,
                integer(attributes, "maximumLevel", entity, 20, minimum, 1_000_000), minimum, 1_000_000);
        return new CompiledRuleset.ProgressionDefinition(entity.id(),
                attributes.getOrDefault("experienceTableRef", ""), minimum, maximum);
    }

    private static void validateDefinitions(
            Map<String, RuleEntity> entities,
            Map<String, String> aliases,
            Map<String, CompiledRuleset.StatDefinition> stats,
            Map<String, CompiledRuleset.SkillDefinition> skills,
            Map<String, CompiledRuleset.ValueDefinition> valueDefinitions,
            Map<String, CompiledRuleset.RandomizerDefinition> randomizers,
            Map<String, CompiledRuleset.TableDefinition> tables,
            Map<String, CompiledRuleset.ResourceDefinition> resources,
            Map<String, CompiledRuleset.TurnStructureDefinition> turnStructures,
            Map<String, CompiledRuleset.ActionDefinition> actions,
            Map<String, CompiledRuleset.ModifierDefinition> modifiers,
            Map<String, CompiledRuleset.TriggerDefinition> triggers,
            Set<String> conditions,
            Map<String, CompiledRuleset.HealthModelDefinition> healthModels,
            Map<String, CompiledRuleset.SheetSectionDefinition> sheetSections,
            Map<String, CompiledRuleset.SceneProcedureDefinition> sceneProcedures,
            List<CompiledRuleset.ProgressionDefinition> progressions) {
        skills.values().forEach(skill -> requireKind(skill.id(), "statRef", resolve(skill.statRef(), aliases),
                stats.keySet(), "an executable stat"));
        valueDefinitions.values().forEach(value -> {
            if (value.type() != RuleValue.Type.REFERENCE) return;
            requireEntity(value.id(), "defaultValue", value.defaultValue().canonicalValue(), entities, aliases);
            value.allowedValues().forEach(reference ->
                    requireEntity(value.id(), "allowedValues", reference, entities, aliases));
        });
        randomizers.values().stream().filter(value -> !value.tableRef().isEmpty()).forEach(value ->
                requireKind(value.id(), "tableRef", resolve(value.tableRef(), aliases), tables.keySet(), "a table"));
        LinkedHashSet<String> turnResourceIds = new LinkedHashSet<>();
        turnStructures.values().forEach(structure -> structure.budgets().keySet().forEach(id -> {
            if (!turnResourceIds.add(id)) throw new IllegalArgumentException("Turn resource " + id + " is declared twice");
        }));
        actions.values().forEach(action -> {
            if (!action.ownerRef().isEmpty()) requireEntity(action.id(), "ownerRef", action.ownerRef(), entities, aliases);
            action.costs().forEach(cost -> {
                String target = cost.pool() == CompiledRuleset.CostPool.TURN
                        ? cost.targetRef() : resolve(cost.targetRef(), aliases);
                if (cost.pool() == CompiledRuleset.CostPool.TURN && !turnResourceIds.contains(target)) {
                    throw new IllegalArgumentException(action.id() + ".costs references missing turn resource " + target);
                }
                if (cost.pool() == CompiledRuleset.CostPool.RESOURCE && !resources.containsKey(target)) {
                    throw new IllegalArgumentException(action.id() + ".costs references missing resource " + target);
                }
            });
            action.effectRefs().forEach(ref -> requireEffect(action.id(), ref, modifiers, aliases, false));
        });
        modifiers.values().forEach(modifier -> {
            if (!modifier.ownerRef().isEmpty()) {
                requireEntity(modifier.id(), "ownerRef", modifier.ownerRef(), entities, aliases);
            }
            String target = resolve(modifier.targetRef(), aliases);
            switch (modifier.application()) {
                case STATIC -> requireKind(modifier.id(), "targetRef", target,
                        union(union(stats.keySet(), skills.keySet()),
                                valueIds(valueDefinitions, RuleValue.Type.NUMBER, false)),
                        "a numeric stat, skill, or value");
                case CHANGE_VALUE -> {
                    requireKind(modifier.id(), "targetRef", target,
                            union(union(stats.keySet(), skills.keySet()),
                                    valueIds(valueDefinitions, RuleValue.Type.NUMBER, true)),
                            "a mutable numeric stat, skill, or value");
                    CompiledRuleset.ValueDefinition definition = valueDefinitions.get(target);
                    if (definition != null && !definition.mutable()) {
                        throw new IllegalArgumentException(modifier.id() + ".targetRef points to immutable value " + target);
                    }
                }
                case SET_VALUE -> {
                    requireKind(modifier.id(), "targetRef", target, valueDefinitions.keySet(), "a typed value");
                    CompiledRuleset.ValueDefinition definition = valueDefinitions.get(target);
                    if (!definition.mutable()) {
                        throw new IllegalArgumentException(modifier.id() + ".targetRef points to immutable value " + target);
                    }
                    if (!definition.accepts(modifier.literalValue())) {
                        throw new IllegalArgumentException(modifier.id() + ".valueLiteral is invalid for " + target);
                    }
                    if (modifier.literalValue().type() == RuleValue.Type.REFERENCE) {
                        requireEntity(modifier.id(), "valueLiteral",
                                modifier.literalValue().canonicalValue(), entities, aliases);
                    }
                }
                case CHANGE_RESOURCE -> requireKind(modifier.id(), "targetRef", target,
                        resources.keySet(), "a resource");
                case ADD_CONDITION, REMOVE_CONDITION -> requireKind(modifier.id(), "targetRef", target,
                        conditions, "a condition");
            }
        });
        triggers.values().forEach(trigger -> trigger.effectRefs()
                .forEach(ref -> requireEffect(trigger.id(), ref, modifiers, aliases, true)));
        healthModels.values().forEach(model -> {
            requireKind(model.id(), "primaryResourceRef", resolve(model.primaryResourceRef(), aliases),
                    resources.keySet(), "a resource or track");
            model.bufferResourceRefs().forEach(ref -> requireKind(model.id(), "bufferResourceRefs",
                    resolve(ref, aliases), resources.keySet(), "a resource or track"));
            if (!model.zeroConditionRef().isEmpty()) requireKind(model.id(), "zeroConditionRef",
                    resolve(model.zeroConditionRef(), aliases), conditions, "a condition");
            if (!model.deathConditionRef().isEmpty()) requireKind(model.id(), "deathConditionRef",
                    resolve(model.deathConditionRef(), aliases), conditions, "a condition");
        });
        sheetSections.values().forEach(section -> section.fieldRefs().forEach(ref ->
                requireEntity(section.id(), "fieldRefs", ref, entities, aliases)));
        sceneProcedures.values().forEach(procedure -> {
            procedure.actionRefs().forEach(ref -> requireKind(procedure.id(), "actionRefs",
                    resolve(ref, aliases), actions.keySet(), "an executable action"));
            procedure.trackerRefs().forEach(ref -> {
                String resolved = resolve(ref, aliases);
                RuleEntity target = entities.get(resolved);
                if (target == null || target.kind() != RuleKind.RESOURCE && target.kind() != RuleKind.TRACK
                        && target.kind() != RuleKind.VALUE && target.kind() != RuleKind.STAT
                        && target.kind() != RuleKind.SKILL && target.kind() != RuleKind.SAVE
                        && target.kind() != RuleKind.DEFENSE && target.kind() != RuleKind.CONDITION) {
                    throw new IllegalArgumentException(procedure.id()
                            + ".trackerRefs must reference stateful rules: " + ref);
                }
            });
        });
        List<CompiledRuleset.ProgressionDefinition> experienceProgressions = progressions.stream()
                .filter(candidate -> !candidate.experienceTableRef().isEmpty()).toList();
        if (experienceProgressions.size() > 1) {
            throw new IllegalArgumentException("A ruleset can define only one executable experience progression");
        }
        experienceProgressions.forEach(progression -> {
            String table = resolve(progression.experienceTableRef(), aliases);
            requireKind(progression.id(), "experienceTableRef", table, tables.keySet(), "a table");
            CompiledRuleset.TableDefinition definition = tables.get(table);
            if (definition.rows().values().stream().anyMatch(value -> value.type() != RuleValue.Type.NUMBER)) {
                throw new IllegalArgumentException(progression.id() + ".experienceTableRef must point to a numeric table");
            }
            if (definition.lookup() != CompiledRuleset.TableLookup.FLOOR) {
                throw new IllegalArgumentException(progression.id()
                        + ".experienceTableRef must use FLOOR lookup for cumulative experience thresholds");
            }
            if (definition.rows().firstKey().compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException(progression.id()
                        + ".experienceTableRef must define a threshold at zero experience");
            }
            Integer previousLevel = null;
            for (RuleValue value : definition.rows().values()) {
                final int level;
                try {
                    level = value.asNumber().intValueExact();
                } catch (ArithmeticException failure) {
                    throw new IllegalArgumentException(progression.id()
                            + ".experienceTableRef must contain exact integer levels");
                }
                if (level < progression.minimumLevel() || level > progression.maximumLevel()) {
                    throw new IllegalArgumentException(progression.id()
                            + ".experienceTableRef contains level outside the declared range: " + level);
                }
                if (previousLevel != null && level < previousLevel) {
                    throw new IllegalArgumentException(progression.id()
                            + ".experienceTableRef levels must not decrease as experience increases");
                }
                previousLevel = level;
            }
        });
    }

    private static void requireEffect(
            String owner,
            String rawRef,
            Map<String, CompiledRuleset.ModifierDefinition> modifiers,
            Map<String, String> aliases,
            boolean eventEffectRequired) {
        String ref = resolve(rawRef, aliases);
        CompiledRuleset.ModifierDefinition effect = modifiers.get(ref);
        if (effect == null) throw new IllegalArgumentException(owner + ".effectRefs references missing effect " + rawRef);
        if (eventEffectRequired && effect.application() == CompiledRuleset.EffectApplication.STATIC) {
            throw new IllegalArgumentException(owner + ".effectRefs cannot execute static modifier " + rawRef);
        }
        if (!eventEffectRequired && effect.application() == CompiledRuleset.EffectApplication.STATIC) {
            throw new IllegalArgumentException(owner + ".effectRefs cannot execute static modifier " + rawRef);
        }
    }

    private static void validateFormulaReferences(
            Map<String, CompiledRuleset.StatDefinition> stats,
            Map<String, CompiledRuleset.SkillDefinition> skills,
            Map<String, CompiledRuleset.ValueDefinition> valueDefinitions,
            Map<String, CompiledRuleset.RandomizerDefinition> randomizers,
            Map<String, CompiledRuleset.TableDefinition> tables,
            Map<String, CompiledRuleset.ResourceDefinition> resources,
            Map<String, CompiledRuleset.TurnStructureDefinition> turnStructures,
            Map<String, CompiledRuleset.ActionDefinition> actions,
            Map<String, CompiledRuleset.ModifierDefinition> modifiers,
            Map<String, CompiledRuleset.TriggerDefinition> triggers,
            Set<String> conditions,
            Map<String, CompiledRuleset.SheetSectionDefinition> sheetSections,
            Map<String, String> aliases) {
        ArrayList<OwnedFormula> formulas = new ArrayList<>();
        stats.values().forEach(value -> {
            add(formulas, value.id(), "defaultFormula", value.defaultFormula());
            add(formulas, value.id(), "derivedFormula", value.derivedFormula());
            add(formulas, value.id(), "minimumFormula", value.minimumFormula());
            add(formulas, value.id(), "maximumFormula", value.maximumFormula());
            add(formulas, value.id(), "modifierFormula", value.modifierFormula());
        });
        skills.values().forEach(value -> {
            add(formulas, value.id(), "formula", value.formula());
            add(formulas, value.id(), "trainedBonusFormula", value.trainedBonusFormula());
        });
        randomizers.values().forEach(value -> {
            add(formulas, value.id(), "countFormula", value.countFormula());
            add(formulas, value.id(), "sidesFormula", value.sidesFormula());
            add(formulas, value.id(), "successThresholdFormula", value.successThresholdFormula());
        });
        resources.values().forEach(value -> {
            add(formulas, value.id(), "maximumFormula", value.maximumFormula());
            add(formulas, value.id(), "initialFormula", value.initialFormula());
            add(formulas, value.id(), "recoveryFormula", value.recoveryFormula());
        });
        turnStructures.values().forEach(value -> value.budgets().forEach((id, formula) ->
                add(formulas, value.id(), "budgets." + id, formula)));
        actions.values().forEach(value -> {
            add(formulas, value.id(), "conditionFormula", value.conditionFormula());
            value.costs().forEach(cost -> add(formulas, value.id(), "costs", cost.amountFormula()));
        });
        modifiers.values().forEach(value -> {
            add(formulas, value.id(), "valueFormula", value.valueFormula());
            add(formulas, value.id(), "conditionFormula", value.conditionFormula());
        });
        triggers.values().forEach(value -> add(formulas, value.id(), "conditionFormula", value.conditionFormula()));
        sheetSections.values().forEach(value ->
                add(formulas, value.id(), "visibilityFormula", value.visibilityFormula()));

        Set<String> numericIds = union(union(stats.keySet(), skills.keySet()), numericValueIds(valueDefinitions));
        LinkedHashSet<String> turnResourceIds = new LinkedHashSet<>();
        turnStructures.values().forEach(structure -> turnResourceIds.addAll(structure.budgets().keySet()));
        for (OwnedFormula owned : formulas) {
            for (String rawRef : owned.formula.valueReferences()) {
                if (LOCAL_FORMULA_VALUES.contains(rawRef) || rawRef.startsWith("context:")
                        || rawRef.startsWith("level:")) continue;
                if (rawRef.startsWith("resource:") && (rawRef.endsWith(":current") || rawRef.endsWith(":maximum"))) {
                    String id = rawRef.substring("resource:".length(), rawRef.lastIndexOf(':'));
                    requireKind(owned.owner, owned.field, resolve(id, aliases), resources.keySet(), "a resource");
                    continue;
                }
                if (rawRef.startsWith("condition:") && rawRef.endsWith(":stacks")) {
                    String id = rawRef.substring("condition:".length(), rawRef.length() - ":stacks".length());
                    requireKind(owned.owner, owned.field, resolve(id, aliases), conditions, "a condition");
                    continue;
                }
                if (rawRef.startsWith("turn:")) {
                    String id = rawRef.substring("turn:".length());
                    requireKind(owned.owner, owned.field, id, turnResourceIds, "a turn resource");
                    continue;
                }
                String candidate = rawRef.endsWith(":modifier")
                        ? rawRef.substring(0, rawRef.length() - ":modifier".length()) : rawRef;
                String resolved = resolve(candidate, aliases);
                requireKind(owned.owner, owned.field, resolved, numericIds, "a numeric stat or skill");
                if (rawRef.endsWith(":modifier") && !stats.containsKey(resolved)) {
                    throw new IllegalArgumentException(owned.owner + '.' + owned.field
                            + " modifier reference must point to a stat: " + rawRef);
                }
            }
            for (String tableRef : owned.formula.tableReferences()) {
                requireKind(owned.owner, owned.field, resolve(tableRef, aliases), tables.keySet(), "a table");
            }
        }
    }

    private static void validateFormulaCycles(
            Map<String, CompiledRuleset.StatDefinition> stats,
            Map<String, CompiledRuleset.SkillDefinition> skills,
            Map<String, CompiledRuleset.ValueDefinition> valueDefinitions,
            Map<String, CompiledRuleset.ModifierDefinition> modifiers,
            Map<String, String> aliases) {
        LinkedHashMap<String, Set<String>> graph = new LinkedHashMap<>();
        Set<String> numericIds = union(union(stats.keySet(), skills.keySet()), numericValueIds(valueDefinitions));
        stats.values().forEach(stat -> {
            LinkedHashSet<String> refs = new LinkedHashSet<>();
            collectNumericReferences(refs, stat.derivedFormula(), aliases, numericIds);
            collectNumericReferences(refs, stat.defaultFormula(), aliases, numericIds);
            collectNumericReferences(refs, stat.minimumFormula(), aliases, numericIds);
            collectNumericReferences(refs, stat.maximumFormula(), aliases, numericIds);
            collectNumericReferences(refs, stat.modifierFormula(), aliases, numericIds);
            graph.put(stat.id(), refs);
        });
        skills.values().forEach(skill -> {
            LinkedHashSet<String> refs = new LinkedHashSet<>();
            collectNumericReferences(refs, skill.formula(), aliases, numericIds);
            collectNumericReferences(refs, skill.trainedBonusFormula(), aliases, numericIds);
            graph.put(skill.id(), refs);
        });
        numericValueIds(valueDefinitions).forEach(id -> graph.put(id, new LinkedHashSet<>()));
        modifiers.values().stream()
                .filter(modifier -> modifier.application() == CompiledRuleset.EffectApplication.STATIC)
                .forEach(modifier -> {
                    String target = resolve(modifier.targetRef(), aliases);
                    Set<String> refs = graph.get(target);
                    if (refs == null) return;
                    collectNumericReferences(refs, modifier.valueFormula(), aliases, numericIds);
                    collectNumericReferences(refs, modifier.conditionFormula(), aliases, numericIds);
                });
        HashSet<String> complete = new HashSet<>();
        LinkedHashSet<String> visiting = new LinkedHashSet<>();
        for (String id : graph.keySet()) visit(id, graph, complete, visiting);
    }

    /** Le formule di inizializzazione non possono dipendere circolarmente da altri pool. */
    private static void validateRuntimeStateCycles(
            Map<String, CompiledRuleset.ResourceDefinition> resources,
            Map<String, CompiledRuleset.TurnStructureDefinition> turnStructures,
            Map<String, String> aliases) {
        LinkedHashMap<String, Set<String>> resourceGraph = new LinkedHashMap<>();
        resources.values().forEach(resource -> {
            LinkedHashSet<String> references = new LinkedHashSet<>();
            collectResourceReferences(references, resource.maximumFormula(), aliases, resources.keySet());
            collectResourceReferences(references, resource.initialFormula(), aliases, resources.keySet());
            resourceGraph.put(resource.id(), references);
        });
        validateGraph(resourceGraph);

        LinkedHashMap<String, RuleFormula> turnFormulas = new LinkedHashMap<>();
        turnStructures.values().forEach(structure -> turnFormulas.putAll(structure.budgets()));
        LinkedHashMap<String, Set<String>> turnGraph = new LinkedHashMap<>();
        turnFormulas.forEach((id, formula) -> {
            LinkedHashSet<String> references = new LinkedHashSet<>();
            formula.valueReferences().stream()
                    .filter(reference -> reference.startsWith("turn:"))
                    .map(reference -> reference.substring("turn:".length()))
                    .filter(turnFormulas::containsKey)
                    .forEach(references::add);
            turnGraph.put(id, references);
        });
        validateGraph(turnGraph);
    }

    private static void collectResourceReferences(
            Set<String> target,
            RuleFormula formula,
            Map<String, String> aliases,
            Set<String> resourceIds) {
        for (String reference : formula.valueReferences()) {
            if (!reference.startsWith("resource:")
                    || !(reference.endsWith(":current") || reference.endsWith(":maximum"))) continue;
            String rawId = reference.substring("resource:".length(), reference.lastIndexOf(':'));
            String resolved = resolve(rawId, aliases);
            if (resourceIds.contains(resolved)) target.add(resolved);
        }
    }

    private static void validateGraph(Map<String, Set<String>> graph) {
        HashSet<String> complete = new HashSet<>();
        LinkedHashSet<String> visiting = new LinkedHashSet<>();
        for (String id : graph.keySet()) visit(id, graph, complete, visiting);
    }

    private static void visit(
            String id,
            Map<String, Set<String>> graph,
            Set<String> complete,
            LinkedHashSet<String> visiting) {
        if (complete.contains(id)) return;
        if (!visiting.add(id)) {
            ArrayList<String> cycle = new ArrayList<>(visiting);
            cycle.add(id);
            throw new IllegalArgumentException("Cyclic rule formula dependency: " + String.join(" -> ", cycle));
        }
        graph.getOrDefault(id, Set.of()).forEach(next -> visit(next, graph, complete, visiting));
        visiting.remove(id);
        complete.add(id);
    }

    private static void collectNumericReferences(
            Set<String> target,
            RuleFormula formula,
            Map<String, String> aliases,
            Set<String> numericIds) {
        if (formula == null) return;
        for (String raw : formula.valueReferences()) {
            String candidate = raw.endsWith(":modifier")
                    ? raw.substring(0, raw.length() - ":modifier".length()) : raw;
            String resolved = resolve(candidate, aliases);
            if (numericIds.contains(resolved)) target.add(resolved);
        }
    }

    private static void validateDeclaredLinks(
            List<RuleEntity> enabled,
            Map<String, RuleEntity> entities,
            Map<String, String> aliases) {
        enabled.forEach(entity -> {
            csv(entity.attributes().get("links")).forEach(ref ->
                    requireEntity(entity.id(), "links", ref, entities, aliases));
            entity.attributes().forEach((key, value) -> {
                if (key.endsWith("EntityRef") && !value.isBlank()) {
                    requireEntity(entity.id(), key, value, entities, aliases);
                } else if (key.endsWith("EntityRefs")) {
                    csv(value).forEach(ref -> requireEntity(entity.id(), key, ref, entities, aliases));
                }
            });
        });
    }

    private static Map<String, String> aliases(List<RuleEntity> entities) {
        LinkedHashMap<String, String> resolved = new LinkedHashMap<>();
        HashSet<String> ambiguous = new HashSet<>();
        entities.forEach(entity -> {
            alias(resolved, ambiguous, entity.id(), entity.id());
            alias(resolved, ambiguous, entity.id().toLowerCase(Locale.ROOT), entity.id());
            String last = entity.id().substring(entity.id().lastIndexOf(':') + 1);
            alias(resolved, ambiguous, last, entity.id());
            alias(resolved, ambiguous, last.toLowerCase(Locale.ROOT), entity.id());
            alias(resolved, ambiguous, last.toUpperCase(Locale.ROOT), entity.id());
            for (String key : List.of("statId", "abilityId", "skillId", "resourceId", "damageTypeId",
                    "conditionId", "actionId", "tableId", "classId")) {
                String value = entity.attributes().get(key);
                if (value != null && !value.isBlank()) {
                    alias(resolved, ambiguous, value.trim(), entity.id());
                    alias(resolved, ambiguous, value.trim().toLowerCase(Locale.ROOT), entity.id());
                    alias(resolved, ambiguous, value.trim().toUpperCase(Locale.ROOT), entity.id());
                }
            }
        });
        ambiguous.forEach(resolved::remove);
        return Map.copyOf(resolved);
    }

    private static void alias(Map<String, String> aliases, Set<String> ambiguous, String alias, String id) {
        if (alias.isBlank() || ambiguous.contains(alias)) return;
        String previous = aliases.putIfAbsent(alias, id);
        if (previous != null && !previous.equals(id)) {
            aliases.remove(alias);
            ambiguous.add(alias);
        }
    }

    private static String resolve(String raw, Map<String, String> aliases) {
        String normalized = raw.trim();
        return aliases.getOrDefault(normalized, aliases.getOrDefault(normalized.toLowerCase(Locale.ROOT), normalized));
    }

    private static void requireEntity(
            String owner,
            String field,
            String raw,
            Map<String, RuleEntity> entities,
            Map<String, String> aliases) {
        String resolved = resolve(raw, aliases);
        if (!entities.containsKey(resolved)) {
            throw new IllegalArgumentException(owner + '.' + field + " references missing enabled rule " + raw);
        }
    }

    private static void requireKind(String owner, String field, String ref, Set<String> expected, String detail) {
        if (!expected.contains(ref)) {
            throw new IllegalArgumentException(owner + '.' + field + " must reference " + detail + ": " + ref);
        }
    }

    private static RuleFormula formula(
            Map<String, String> attributes,
            String key,
            String fallback,
            RuleEntity entity) {
        String source = attributes.get(key);
        return compile(source == null || source.isBlank() ? fallback : source, entity, key);
    }

    private static RuleFormula optionalFormula(Map<String, String> attributes, String key, RuleEntity entity) {
        return optionalFormula(attributes, key, attributes.get(key), entity);
    }

    private static RuleFormula optionalFormula(
            Map<String, String> attributes,
            String key,
            String source,
            RuleEntity entity) {
        return source == null || source.isBlank() ? null : compile(source, entity, key);
    }

    private static RuleFormula compile(String source, RuleEntity entity, String key) {
        try {
            return RuleFormula.compile(source);
        } catch (RuntimeException failure) {
            throw invalid(entity, key, failure.getMessage());
        }
    }

    private static Map<String, String> parseAssignments(String encoded, RuleEntity entity, String field) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) return result;
        for (String row : encoded.split(";")) {
            if (row.isBlank()) continue;
            int separator = row.indexOf('=');
            if (separator <= 0 || separator == row.length() - 1) {
                throw invalid(entity, field, "contains invalid assignment '" + row + "'");
            }
            String id = row.substring(0, separator).trim();
            String formula = row.substring(separator + 1).trim();
            if (result.put(id, formula) != null) throw invalid(entity, field, "contains duplicate " + id);
        }
        return result;
    }

    private static <T extends Enum<T>> T enumeration(
            Map<String, String> attributes,
            String key,
            Class<T> type,
            T fallback,
            RuleEntity entity) {
        String raw = attributes.get(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw invalid(entity, key, "has unsupported value '" + raw + "'");
        }
    }

    private static int integer(
            Map<String, String> attributes,
            String key,
            RuleEntity entity,
            int fallback,
            int minimum,
            int maximum) {
        String raw = attributes.get(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException failure) {
            throw invalid(entity, key, "must be an integer between " + minimum + " and " + maximum);
        }
    }

    private static boolean bool(
            Map<String, String> attributes,
            String key,
            RuleEntity entity,
            boolean fallback) {
        String raw = attributes.get(key);
        if (raw == null || raw.isBlank()) return fallback;
        if (raw.equalsIgnoreCase("true")) return true;
        if (raw.equalsIgnoreCase("false")) return false;
        throw invalid(entity, key, "must be true or false");
    }

    private static BigDecimal decimal(String raw, RuleEntity entity, String field) {
        try {
            BigDecimal normalized = new BigDecimal(raw.trim()).stripTrailingZeros();
            return normalized.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : new BigDecimal(normalized.toPlainString());
        } catch (NumberFormatException failure) {
            throw invalid(entity, field, "must be an exact decimal");
        }
    }

    private static String required(Map<String, String> attributes, String key, RuleEntity entity) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) throw invalid(entity, key, "is required");
        return value.trim();
    }

    private static String first(Map<String, String> attributes, String... keys) {
        for (String key : keys) {
            String value = attributes.get(key);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static List<String> csv(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String raw : encoded.split(",")) if (!raw.isBlank()) values.add(raw.trim());
        return List.copyOf(values);
    }

    private static IllegalArgumentException invalid(RuleEntity entity, String field, String detail) {
        return new IllegalArgumentException(entity.id() + '.' + field + ' ' + detail);
    }

    private static void add(List<OwnedFormula> target, String owner, String field, RuleFormula formula) {
        if (formula != null) target.add(new OwnedFormula(owner, field, formula));
    }

    private static boolean hasFormulas(
            Map<String, CompiledRuleset.StatDefinition> stats,
            Map<String, CompiledRuleset.SkillDefinition> skills,
            Map<String, CompiledRuleset.ResourceDefinition> resources,
            Map<String, CompiledRuleset.ModifierDefinition> modifiers) {
        return !stats.isEmpty() || !skills.isEmpty() || !resources.isEmpty() || !modifiers.isEmpty();
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        LinkedHashSet<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private static Set<String> numericValueIds(
            Map<String, CompiledRuleset.ValueDefinition> definitions) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        definitions.values().stream()
                .filter(value -> value.type() == RuleValue.Type.NUMBER || value.type() == RuleValue.Type.BOOLEAN)
                .map(CompiledRuleset.ValueDefinition::id)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private static Set<String> valueIds(
            Map<String, CompiledRuleset.ValueDefinition> definitions,
            RuleValue.Type type,
            boolean mutableOnly) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        definitions.values().stream()
                .filter(value -> value.type() == type && (!mutableOnly || value.mutable()))
                .map(CompiledRuleset.ValueDefinition::id)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private record OwnedFormula(String owner, String field, RuleFormula formula) { }
}
