package app.d6d.rules.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Snapshot eseguibile indipendente da una particolare edizione.
 *
 * <p>Le viste SRD possono continuare a usare i propri tipi ricchi; questa e' la
 * rappresentazione comune per regole inventate, pack 3.5-like e giochi non-D20.</p>
 */
public final class CompiledRuleset {
    public static final int MAX_TRIGGER_EVENTS = 128;

    public enum StatRounding { NONE, FLOOR, CEILING, HALF_UP }
    public enum TableLookup { EXACT, FLOOR, CEILING, NEAREST }
    public enum ModifierOperation { ADD, MULTIPLY, SET, MINIMUM, MAXIMUM }
    public enum EffectApplication {
        STATIC,
        CHANGE_VALUE,
        SET_VALUE,
        CHANGE_RESOURCE,
        ADD_CONDITION,
        REMOVE_CONDITION
    }
    public enum EffectRecipient { SELF, TARGET, SESSION }
    public enum CostPool { TURN, RESOURCE }
    public enum RandomizerMode { DICE, DICE_POOL, PERCENTILE, TABLE, MANUAL }
    public enum KeepMode { SUM, HIGHEST, LOWEST, SUCCESSES }
    public enum SheetLayout { LIST, GRID, CARDS, COMPACT }
    public enum BoardTopology { SQUARE, HEX_POINTY, HEX_FLAT, GRIDLESS, THEATRE_OF_MIND }
    public enum DiagonalRule { UNIFORM, FIVE_TEN_FIVE, EUCLIDEAN, MANUAL }
    public enum ZeroState { NONE, DISABLED, UNCONSCIOUS, DYING, DEAD, MANUAL }
    public enum ConditionStacking { REPLACE, STACK, HIGHEST, SEPARATE_BY_SOURCE }

    public interface RandomSource {
        /** Restituisce un intero uniforme nell'intervallo [0, bound). */
        int nextInt(int bound);
    }

    public record RandomizerDefinition(
            String id,
            RandomizerMode mode,
            RuleFormula countFormula,
            RuleFormula sidesFormula,
            KeepMode keep,
            RuleFormula successThresholdFormula,
            String tableRef) {
        public RandomizerDefinition {
            id = requireId(id);
            mode = Objects.requireNonNull(mode, "mode");
            countFormula = Objects.requireNonNull(countFormula, "countFormula");
            sidesFormula = Objects.requireNonNull(sidesFormula, "sidesFormula");
            keep = Objects.requireNonNull(keep, "keep");
            successThresholdFormula = Objects.requireNonNull(successThresholdFormula, "successThresholdFormula");
            tableRef = tableRef == null ? "" : tableRef.trim();
        }
    }

    public record RandomizerResult(String randomizerId, List<Integer> draws, BigDecimal value, RuleValue tableValue) {
        public RandomizerResult {
            randomizerId = requireId(randomizerId);
            draws = List.copyOf(Objects.requireNonNull(draws, "draws"));
            value = normalize(Objects.requireNonNull(value, "value"));
        }
    }

    public record StatDefinition(
            String id,
            RuleFormula defaultFormula,
            RuleFormula derivedFormula,
            RuleFormula minimumFormula,
            RuleFormula maximumFormula,
            RuleFormula modifierFormula,
            StatRounding rounding) {
        public StatDefinition {
            id = requireId(id);
            defaultFormula = Objects.requireNonNull(defaultFormula, "defaultFormula");
            rounding = Objects.requireNonNull(rounding, "rounding");
        }
    }

    public record SkillDefinition(String id, String statRef, RuleFormula formula, RuleFormula trainedBonusFormula) {
        public SkillDefinition {
            id = requireId(id);
            statRef = requireId(statRef);
            formula = Objects.requireNonNull(formula, "formula");
            trainedBonusFormula = Objects.requireNonNull(trainedBonusFormula, "trainedBonusFormula");
        }
    }

    /** Valore tipizzato indirizzabile: numero, booleano, testo o riferimento ad altra regola. */
    public record ValueDefinition(
            String id,
            RuleValue.Type type,
            RuleValue defaultValue,
            Set<String> allowedValues,
            boolean mutable,
            String dimension,
            String canonicalUnit) {
        public ValueDefinition {
            id = requireId(id);
            type = Objects.requireNonNull(type, "type");
            defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
            dimension = dimension == null || dimension.isBlank() ? "SCALAR" : requireId(dimension).toUpperCase(Locale.ROOT);
            canonicalUnit = canonicalUnit == null ? "" : canonicalUnit.trim();
            if (defaultValue.type() != type) throw new IllegalArgumentException(id + " default value type differs");
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            Objects.requireNonNull(allowedValues, "allowedValues").forEach(value ->
                    normalized.add(Objects.requireNonNull(value, "allowed value")));
            allowedValues = Set.copyOf(normalized);
            if (!allowedValues.isEmpty() && !allowedValues.contains(defaultValue.canonicalValue())) {
                throw new IllegalArgumentException(id + " default value is not allowed");
            }
        }

        public boolean accepts(RuleValue value) {
            return value != null && value.type() == type
                    && (allowedValues.isEmpty() || allowedValues.contains(value.canonicalValue()));
        }

        public boolean dimensional() {
            return !"SCALAR".equals(dimension);
        }
    }

    public record TableDefinition(String id, NavigableMap<BigDecimal, RuleValue> rows, TableLookup lookup) {
        public TableDefinition {
            id = requireId(id);
            Objects.requireNonNull(rows, "rows");
            TreeMap<BigDecimal, RuleValue> normalized = new TreeMap<>();
            rows.forEach((key, value) -> normalized.put(normalize(key), Objects.requireNonNull(value, "row value")));
            if (normalized.isEmpty()) throw new IllegalArgumentException(id + " table cannot be empty");
            rows = java.util.Collections.unmodifiableNavigableMap(normalized);
            lookup = Objects.requireNonNull(lookup, "lookup");
        }

        public RuleValue value(BigDecimal rawKey) {
            BigDecimal key = normalize(rawKey);
            Map.Entry<BigDecimal, RuleValue> row = switch (lookup) {
                case EXACT -> rows.containsKey(key) ? Map.entry(key, rows.get(key)) : null;
                case FLOOR -> rows.floorEntry(key);
                case CEILING -> rows.ceilingEntry(key);
                case NEAREST -> nearest(rows.floorEntry(key), rows.ceilingEntry(key), key);
            };
            if (row == null) throw new IllegalArgumentException("Table " + id + " has no row for " + key);
            return row.getValue();
        }

        private static Map.Entry<BigDecimal, RuleValue> nearest(
                Map.Entry<BigDecimal, RuleValue> floor,
                Map.Entry<BigDecimal, RuleValue> ceiling,
                BigDecimal key) {
            if (floor == null) return ceiling;
            if (ceiling == null) return floor;
            BigDecimal below = key.subtract(floor.getKey()).abs();
            BigDecimal above = ceiling.getKey().subtract(key).abs();
            return below.compareTo(above) <= 0 ? floor : ceiling;
        }
    }

    public record ResourceDefinition(
            String id,
            RuleFormula maximumFormula,
            RuleFormula initialFormula,
            String recoveryEvent,
            RuleFormula recoveryFormula) {
        public ResourceDefinition {
            id = requireId(id);
            maximumFormula = Objects.requireNonNull(maximumFormula, "maximumFormula");
            initialFormula = Objects.requireNonNull(initialFormula, "initialFormula");
            recoveryEvent = recoveryEvent == null ? "MANUAL" : requireId(recoveryEvent).toUpperCase(Locale.ROOT);
            recoveryFormula = Objects.requireNonNull(recoveryFormula, "recoveryFormula");
        }
    }

    public record TurnStructureDefinition(String id, Map<String, RuleFormula> budgets) {
        public TurnStructureDefinition {
            id = requireId(id);
            budgets = immutableFormulaMap(budgets);
            if (budgets.isEmpty()) throw new IllegalArgumentException(id + " turn structure needs a budget");
        }
    }

    public record ActionCost(CostPool pool, String targetRef, RuleFormula amountFormula) {
        public ActionCost {
            pool = Objects.requireNonNull(pool, "pool");
            targetRef = requireId(targetRef);
            amountFormula = Objects.requireNonNull(amountFormula, "amountFormula");
        }
    }

    public record ActionDefinition(
            String id,
            String ownerRef,
            RuleFormula conditionFormula,
            List<ActionCost> costs,
            List<String> effectRefs) {
        public ActionDefinition {
            id = requireId(id);
            ownerRef = ownerRef == null ? "" : ownerRef.trim();
            conditionFormula = Objects.requireNonNull(conditionFormula, "conditionFormula");
            costs = List.copyOf(Objects.requireNonNull(costs, "costs"));
            effectRefs = immutableIds(effectRefs);
        }
    }

    public record ModifierDefinition(
            String id,
            String ownerRef,
            String targetRef,
            ModifierOperation operation,
            RuleFormula valueFormula,
            RuleFormula conditionFormula,
            String group,
            int priority,
            int minimumLevel,
            EffectApplication application,
            EffectRecipient recipient,
            RuleValue literalValue) {
        public ModifierDefinition {
            id = requireId(id);
            ownerRef = ownerRef == null ? "" : ownerRef.trim();
            targetRef = requireId(targetRef);
            operation = Objects.requireNonNull(operation, "operation");
            valueFormula = Objects.requireNonNull(valueFormula, "valueFormula");
            conditionFormula = Objects.requireNonNull(conditionFormula, "conditionFormula");
            group = group == null ? "" : group.trim();
            if (minimumLevel < 1) throw new IllegalArgumentException(id + " minimumLevel must be positive");
            application = Objects.requireNonNull(application, "application");
            recipient = Objects.requireNonNull(recipient, "recipient");
            if (application == EffectApplication.STATIC && recipient != EffectRecipient.SELF) {
                throw new IllegalArgumentException(id + " static modifier must target SELF");
            }
            if (application == EffectApplication.SET_VALUE && literalValue == null) {
                throw new IllegalArgumentException(id + " SET_VALUE effect needs a typed literal");
            }
        }
    }

    public record TriggerDefinition(
            String id,
            String event,
            RuleFormula conditionFormula,
            List<String> effectRefs,
            int priority,
            int maximumExecutions) {
        public TriggerDefinition {
            id = requireId(id);
            event = requireId(event).toUpperCase(Locale.ROOT);
            conditionFormula = Objects.requireNonNull(conditionFormula, "conditionFormula");
            effectRefs = immutableIds(effectRefs);
            if (maximumExecutions < 1 || maximumExecutions > MAX_TRIGGER_EVENTS) {
                throw new IllegalArgumentException(id + " maximumExecutions is invalid");
            }
        }
    }

    public record ProgressionDefinition(String id, String experienceTableRef, int minimumLevel, int maximumLevel) {
        public ProgressionDefinition {
            id = requireId(id);
            experienceTableRef = experienceTableRef == null ? "" : experienceTableRef.trim();
            if (minimumLevel < 0 || maximumLevel < minimumLevel) {
                throw new IllegalArgumentException(id + " progression level range is invalid");
            }
        }
    }

    /** Condizione generica; gli effetti meccanici restano normali MODIFIER collegati. */
    public record ConditionDefinition(
            String id,
            int maximumStacks,
            ConditionStacking stacking,
            boolean sourceScoped,
            String removalEvent) {
        public ConditionDefinition {
            id = requireId(id);
            if (maximumStacks < 1 || maximumStacks > 1_000) {
                throw new IllegalArgumentException(id + " maximumStacks is invalid");
            }
            stacking = Objects.requireNonNull(stacking, "stacking");
            removalEvent = removalEvent == null ? "" : removalEvent.trim().toUpperCase(Locale.ROOT);
        }
    }

    /** Modello salute composto da risorse aperte, non da campi PF obbligatori. */
    public record HealthModelDefinition(
            String id,
            String primaryResourceRef,
            List<String> bufferResourceRefs,
            String zeroConditionRef,
            String deathConditionRef,
            boolean allowsNegative,
            ZeroState zeroState) {
        public HealthModelDefinition {
            id = requireId(id);
            primaryResourceRef = requireId(primaryResourceRef);
            bufferResourceRefs = immutableIds(bufferResourceRefs);
            zeroConditionRef = zeroConditionRef == null ? "" : zeroConditionRef.trim();
            deathConditionRef = deathConditionRef == null ? "" : deathConditionRef.trim();
            zeroState = Objects.requireNonNull(zeroState, "zeroState");
        }
    }

    /** Geometria e unita' canonica richieste dal regolamento. */
    public record MovementDefinition(
            String id,
            BoardTopology topology,
            DiagonalRule diagonalRule,
            BigDecimal unitsPerCell,
            String canonicalUnit,
            boolean elevation,
            boolean occupancyRequired) {
        public MovementDefinition {
            id = requireId(id);
            topology = Objects.requireNonNull(topology, "topology");
            diagonalRule = Objects.requireNonNull(diagonalRule, "diagonalRule");
            unitsPerCell = normalize(Objects.requireNonNull(unitsPerCell, "unitsPerCell"));
            if (unitsPerCell.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(id + " unitsPerCell must be positive");
            }
            canonicalUnit = canonicalUnit == null || canonicalUnit.isBlank()
                    ? "unit" : canonicalUnit.trim();
        }
    }

    /** Sezione della scheda composta da riferimenti a campi del medesimo snapshot. */
    public record SheetSectionDefinition(
            String id,
            int order,
            int columns,
            SheetLayout layout,
            List<String> fieldRefs,
            RuleFormula visibilityFormula) {
        public SheetSectionDefinition {
            id = requireId(id);
            if (columns < 1 || columns > 12) throw new IllegalArgumentException(id + " columns is invalid");
            layout = Objects.requireNonNull(layout, "layout");
            fieldRefs = immutableIds(fieldRefs);
            if (fieldRefs.isEmpty()) throw new IllegalArgumentException(id + " sheet section needs fields");
            visibilityFormula = Objects.requireNonNull(visibilityFormula, "visibilityFormula");
        }
    }

    /** Workflow di scena generico; azioni e tracker sono entita' gia' validate. */
    public record SceneProcedureDefinition(
            String id,
            List<String> phases,
            List<String> actionRefs,
            List<String> trackerRefs,
            boolean initiativeRequired,
            boolean boardRequired) {
        public SceneProcedureDefinition {
            id = requireId(id);
            phases = immutableIds(phases);
            actionRefs = immutableIds(actionRefs);
            trackerRefs = immutableIds(trackerRefs);
            if (phases.isEmpty()) throw new IllegalArgumentException(id + " scene procedure needs phases");
        }
    }

    public record CapabilityProfile(
            boolean dynamicStats,
            boolean dynamicSkills,
            boolean randomizers,
            boolean formulas,
            boolean tables,
            boolean resources,
            boolean triggers,
            boolean actionEconomy,
            boolean dynamicDamageTypes,
            boolean dynamicConditions,
            boolean typedValues,
            boolean healthModels,
            boolean movementModels,
            boolean sheetSections,
            boolean sceneProcedures,
            boolean statePolicies,
            long manualRuleCount) { }

    private final String canonicalHash;
    private final Map<String, RuleEntity> entities;
    private final Map<String, String> aliases;
    private final Map<String, StatDefinition> stats;
    private final Map<String, SkillDefinition> skills;
    private final Map<String, ValueDefinition> valueDefinitions;
    private final Map<String, RandomizerDefinition> randomizers;
    private final Map<String, TableDefinition> tables;
    private final Map<String, ResourceDefinition> resources;
    private final Map<String, TurnStructureDefinition> turnStructures;
    private final Map<String, ActionDefinition> actions;
    private final Map<String, ModifierDefinition> modifiers;
    private final Map<String, TriggerDefinition> triggers;
    private final Map<String, ConditionDefinition> conditionDefinitions;
    private final Map<String, HealthModelDefinition> healthModels;
    private final Map<String, MovementDefinition> movementModels;
    private final Map<String, SheetSectionDefinition> sheetSections;
    private final Map<String, SceneProcedureDefinition> sceneProcedures;
    private final Map<String, StatePersistencePolicy> persistencePolicies;
    private final Set<String> damageTypes;
    private final Set<String> conditions;
    private final ProgressionDefinition progression;
    private final CapabilityProfile capabilities;

    CompiledRuleset(
            String canonicalHash,
            Map<String, RuleEntity> entities,
            Map<String, String> aliases,
            Map<String, StatDefinition> stats,
            Map<String, SkillDefinition> skills,
            Map<String, ValueDefinition> valueDefinitions,
            Map<String, RandomizerDefinition> randomizers,
            Map<String, TableDefinition> tables,
            Map<String, ResourceDefinition> resources,
            Map<String, TurnStructureDefinition> turnStructures,
            Map<String, ActionDefinition> actions,
            Map<String, ModifierDefinition> modifiers,
            Map<String, TriggerDefinition> triggers,
            Map<String, ConditionDefinition> conditionDefinitions,
            Map<String, HealthModelDefinition> healthModels,
            Map<String, MovementDefinition> movementModels,
            Map<String, SheetSectionDefinition> sheetSections,
            Map<String, SceneProcedureDefinition> sceneProcedures,
            Map<String, StatePersistencePolicy> persistencePolicies,
            Set<String> damageTypes,
            Set<String> conditions,
            ProgressionDefinition progression,
            CapabilityProfile capabilities) {
        this.canonicalHash = requireId(canonicalHash);
        this.entities = Map.copyOf(entities);
        this.aliases = Map.copyOf(aliases);
        this.stats = Map.copyOf(stats);
        this.skills = Map.copyOf(skills);
        this.valueDefinitions = Map.copyOf(valueDefinitions);
        this.randomizers = Map.copyOf(randomizers);
        this.tables = Map.copyOf(tables);
        this.resources = Map.copyOf(resources);
        this.turnStructures = Map.copyOf(turnStructures);
        this.actions = Map.copyOf(actions);
        this.modifiers = Map.copyOf(modifiers);
        this.triggers = Map.copyOf(triggers);
        this.conditionDefinitions = Map.copyOf(conditionDefinitions);
        this.healthModels = Map.copyOf(healthModels);
        this.movementModels = Map.copyOf(movementModels);
        this.sheetSections = Map.copyOf(sheetSections);
        this.sceneProcedures = Map.copyOf(sceneProcedures);
        this.persistencePolicies = Map.copyOf(persistencePolicies);
        this.damageTypes = Set.copyOf(damageTypes);
        this.conditions = Set.copyOf(conditions);
        this.progression = progression;
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    }

    public String canonicalHash() { return canonicalHash; }
    public Map<String, RuleEntity> entities() { return entities; }
    public Map<String, StatDefinition> stats() { return stats; }
    public Map<String, SkillDefinition> skills() { return skills; }
    public Map<String, ValueDefinition> valueDefinitions() { return valueDefinitions; }
    public Map<String, RandomizerDefinition> randomizers() { return randomizers; }
    public Map<String, TableDefinition> tables() { return tables; }
    public Map<String, ResourceDefinition> resources() { return resources; }
    public Map<String, TurnStructureDefinition> turnStructures() { return turnStructures; }
    public Map<String, ActionDefinition> actions() { return actions; }
    public Map<String, ModifierDefinition> modifiers() { return modifiers; }
    public Map<String, TriggerDefinition> triggers() { return triggers; }
    public Map<String, ConditionDefinition> conditionDefinitions() { return conditionDefinitions; }
    public Map<String, HealthModelDefinition> healthModels() { return healthModels; }
    public Map<String, MovementDefinition> movementModels() { return movementModels; }
    public Map<String, SheetSectionDefinition> sheetSections() { return sheetSections; }
    public Map<String, SceneProcedureDefinition> sceneProcedures() { return sceneProcedures; }
    public Map<String, StatePersistencePolicy> persistencePolicies() { return persistencePolicies; }
    public Set<String> damageTypes() { return damageTypes; }
    public Set<String> conditions() { return conditions; }
    public ProgressionDefinition progression() { return progression; }
    public CapabilityProfile capabilities() { return capabilities; }

    public String resolveId(String id) {
        String normalized = requireId(id);
        return aliases.getOrDefault(normalized, aliases.getOrDefault(normalized.toLowerCase(Locale.ROOT), normalized));
    }

    public RuleRuntimeState initialState(Map<String, RuleValue> suppliedValues, Set<String> activeRuleIds) {
        LinkedHashMap<String, RuleValue> values = new LinkedHashMap<>();
        valueDefinitions.values().stream().sorted(Comparator.comparing(ValueDefinition::id)).forEach(definition ->
                values.put(definition.id(), definition.defaultValue()));
        Objects.requireNonNull(suppliedValues, "suppliedValues").forEach((id, value) -> {
            String resolved = resolveId(id);
            RuleValue supplied = Objects.requireNonNull(value, "supplied value");
            ValueDefinition definition = valueDefinitions.get(resolved);
            boolean numericOverride = stats.containsKey(resolved) || skills.containsKey(resolved);
            boolean genericContext = resolved.startsWith("context:");
            boolean levelContext = resolved.startsWith("level:");
            if (definition == null && !numericOverride && !genericContext && !levelContext) {
                throw new IllegalArgumentException("Unknown supplied rule value " + id);
            }
            if ((numericOverride || levelContext) && supplied.type() != RuleValue.Type.NUMBER) {
                throw new IllegalArgumentException("Supplied numeric rule value " + id + " must be numeric");
            }
            if (levelContext) validateLevelValue(id, supplied);
            if (genericContext && supplied.type() != RuleValue.Type.NUMBER
                    && supplied.type() != RuleValue.Type.BOOLEAN) {
                throw new IllegalArgumentException("Supplied context value " + id + " must be numeric or boolean");
            }
            if (definition != null && !definition.accepts(supplied)) {
                throw new IllegalArgumentException("Invalid supplied value for " + resolved);
            }
            if (definition != null && supplied.type() == RuleValue.Type.REFERENCE
                    && !entities.containsKey(resolveId(supplied.canonicalValue()))) {
                throw new IllegalArgumentException("Supplied value for " + resolved + " references a missing rule");
            }
            values.put(resolved, supplied);
        });
        LinkedHashSet<String> active = new LinkedHashSet<>();
        entities.values().stream()
                .filter(entity -> Boolean.parseBoolean(entity.attributes().getOrDefault("activeByDefault", "false")))
                .forEach(entity -> active.add(entity.id()));
        Objects.requireNonNull(activeRuleIds, "activeRuleIds").forEach(id -> {
            String normalized = requireId(id);
            if (normalized.startsWith("trained:")) {
                String skill = resolveId(normalized.substring("trained:".length()));
                if (!skills.containsKey(skill)) {
                    throw new IllegalArgumentException("Unknown trained skill " + id);
                }
                active.add("trained:" + skill);
                return;
            }
            String resolved = resolveId(normalized);
            if (!entities.containsKey(resolved)) {
                throw new IllegalArgumentException("Unknown active rule " + id);
            }
            active.add(resolved);
        });
        LinkedHashMap<String, RuleRuntimeState.ResourceState> pools = new LinkedHashMap<>();
        LinkedHashSet<String> initializing = new LinkedHashSet<>();
        for (ResourceDefinition definition : resources.values().stream()
                .sorted(Comparator.comparing(ResourceDefinition::id)).toList()) {
            initializeResource(definition.id, values, active, pools, initializing);
        }
        RuleRuntimeState draft = new RuleRuntimeState(values, pools, Map.of(), Map.of(), active, 0);
        return beginTurn(draft);
    }

    /**
     * Inizializza prima le risorse referenziate dalle formule di massimo o valore iniziale.
     * L'ordine lessicografico resta deterministico, ma non determina più la semantica.
     */
    private void initializeResource(
            String id,
            Map<String, RuleValue> values,
            Set<String> active,
            LinkedHashMap<String, RuleRuntimeState.ResourceState> pools,
            LinkedHashSet<String> initializing) {
        if (pools.containsKey(id)) return;
        if (!initializing.add(id)) {
            throw new IllegalStateException("Cyclic resource initialization at " + id);
        }
        ResourceDefinition definition = resources.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown resource " + id);
        for (String dependency : resourceDependencies(definition.maximumFormula, definition.initialFormula)) {
            initializeResource(dependency, values, active, pools, initializing);
        }
        RuleRuntimeState draft = new RuleRuntimeState(values, pools, Map.of(), Map.of(), active, 0);
        RuntimeContext context = new RuntimeContext(draft, Map.of(), new LinkedHashSet<>());
        BigDecimal maximum = definition.maximumFormula.evaluate(context).max(BigDecimal.ZERO);
        BigDecimal initial = definition.initialFormula.evaluate(
                new RuntimeContext(draft, Map.of("maximum", maximum), new LinkedHashSet<>()))
                .max(BigDecimal.ZERO).min(maximum);
        pools.put(id, new RuleRuntimeState.ResourceState(id, initial, maximum));
        initializing.remove(id);
    }

    private Set<String> resourceDependencies(RuleFormula... formulas) {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        for (RuleFormula formula : formulas) {
            if (formula == null) continue;
            for (String reference : formula.valueReferences()) {
                if (!reference.startsWith("resource:")
                        || !(reference.endsWith(":current") || reference.endsWith(":maximum"))) continue;
                String rawId = reference.substring("resource:".length(), reference.lastIndexOf(':'));
                String resolved = resolveId(rawId);
                if (resources.containsKey(resolved)) dependencies.add(resolved);
            }
        }
        return dependencies;
    }

    public BigDecimal value(String id, RuleRuntimeState state) {
        return new RuntimeContext(Objects.requireNonNull(state, "state"), Map.of(), new LinkedHashSet<>())
                .value(resolveId(id));
    }

    /** Restituisce anche valori non numerici senza costringerli dentro una formula. */
    public RuleValue ruleValue(String id, RuleRuntimeState state) {
        String resolved = resolveId(id);
        RuleValue result = Objects.requireNonNull(state, "state").values().get(resolved);
        if (result != null) return result;
        ValueDefinition definition = valueDefinitions.get(resolved);
        if (definition != null) return definition.defaultValue();
        throw new IllegalArgumentException("Unknown rule value " + id);
    }

    /** Correzione esplicita del tavolo, validata contro tipo, dominio e mutabilità pubblicati. */
    public RuleRuntimeState setRuleValue(String id, RuleValue value, RuleRuntimeState state) {
        String resolved = resolveId(id);
        ValueDefinition definition = valueDefinitions.get(resolved);
        if (definition == null) throw new IllegalArgumentException("Unknown typed rule value " + id);
        if (!definition.mutable()) throw new IllegalStateException("Rule value " + id + " is read only");
        if (!definition.accepts(value)) throw new IllegalArgumentException("Invalid value for " + id);
        if (value.type() == RuleValue.Type.REFERENCE
                && !entities.containsKey(resolveId(value.canonicalValue()))) {
            throw new IllegalArgumentException("Rule value " + id + " references a missing rule");
        }
        if (ruleValue(resolved, state).equals(value)) return state;
        return Objects.requireNonNull(state, "state").withValue(resolved, value);
    }

    /** Override numerico di sessione per una caratteristica/difesa o un VALUE numerico mutabile. */
    public RuleRuntimeState setNumericValue(String id, BigDecimal value, RuleRuntimeState state) {
        String resolved = resolveId(id);
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(state, "state");
        ValueDefinition definition = valueDefinitions.get(resolved);
        if (definition != null) return setRuleValue(resolved, RuleValue.number(value), state);
        if (!stats.containsKey(resolved) && !skills.containsKey(resolved)) {
            throw new IllegalArgumentException("Rule " + id + " is not a settable numeric stat or skill");
        }
        RuleValue changed = RuleValue.number(value);
        RuleValue existing = state.values().get(resolved);
        if (changed.equals(existing)) return state;
        if (existing == null && changed.asNumber().compareTo(value(resolved, state)) == 0) return state;
        return state.withValue(resolved, changed);
    }

    public RuleRuntimeState setResource(
            String id,
            BigDecimal current,
            BigDecimal maximum,
            RuleRuntimeState state) {
        String resolved = resolveId(id);
        if (!resources.containsKey(resolved)) throw new IllegalArgumentException("Unknown resource " + id);
        RuleRuntimeState.ResourceState changed = new RuleRuntimeState.ResourceState(resolved, current, maximum);
        RuleRuntimeState.ResourceState before = Objects.requireNonNull(state, "state").resources().get(resolved);
        if (changed.equals(before)) return state;
        return state.withResource(changed);
    }

    public RuleRuntimeState setConditionStacks(String id, int stacks, RuleRuntimeState state) {
        String resolved = resolveId(id);
        if (!conditions.contains(resolved)) throw new IllegalArgumentException("Unknown condition " + id);
        if (stacks < 0 || stacks > conditionMaximumStacks(resolved)) {
            throw new IllegalArgumentException("Invalid stacks for condition " + id);
        }
        Objects.requireNonNull(state, "state");
        if (state.conditionStacks().getOrDefault(resolved, 0) == stacks) return state;
        return state.withCondition(resolved, stacks);
    }

    public RuleRuntimeState setTurnResource(String id, BigDecimal current, RuleRuntimeState state) {
        String normalized = requireId(id);
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(state, "state");
        if (!state.turnBudget().containsKey(normalized)) {
            throw new IllegalArgumentException("Unknown turn resource " + id);
        }
        if (current.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Turn resource cannot be negative");
        }
        if (state.turnBudget().get(normalized).compareTo(current) == 0) return state;
        LinkedHashMap<String, BigDecimal> changed = new LinkedHashMap<>(state.turnBudget());
        changed.put(normalized, current);
        return state.withTurnBudget(changed);
    }

    public boolean isRuleActive(String id, RuleRuntimeState state) {
        String resolved = resolveId(id);
        if (!entities.containsKey(resolved)) throw new IllegalArgumentException("Unknown rule " + id);
        return Objects.requireNonNull(state, "state").activeRuleIds().contains(resolved);
    }

    /** Attiva o sospende un owner senza alterare la revisione pubblicata. */
    public RuleRuntimeState setRuleActive(String id, boolean active, RuleRuntimeState state) {
        String resolved = resolveId(id);
        if (!entities.containsKey(resolved)) throw new IllegalArgumentException("Unknown rule " + id);
        Objects.requireNonNull(state, "state");
        if (state.activeRuleIds().contains(resolved) == active) return state;
        LinkedHashSet<String> changed = new LinkedHashSet<>(state.activeRuleIds());
        if (active) changed.add(resolved); else changed.remove(resolved);
        return state.withActiveRules(changed);
    }

    /** Consuma casualita' fornita dalla sessione; il ruleset non crea mai un RNG nascosto. */
    public RandomizerResult roll(String randomizerId, RuleRuntimeState state, RandomSource source) {
        RandomizerDefinition definition = randomizers.get(resolveId(randomizerId));
        if (definition == null) throw new IllegalArgumentException("Unknown randomizer " + randomizerId);
        Objects.requireNonNull(source, "source");
        RuntimeContext context = new RuntimeContext(state, Map.of(), new LinkedHashSet<>());
        if (definition.mode == RandomizerMode.MANUAL) {
            throw new IllegalStateException("Randomizer " + definition.id + " requires manual input");
        }
        int count = definition.mode == RandomizerMode.PERCENTILE ? 1
                : definition.countFormula.evaluate(context).intValueExact();
        int sides = definition.mode == RandomizerMode.PERCENTILE ? 100
                : definition.sidesFormula.evaluate(context).intValueExact();
        if (count < 1 || count > 1_000 || sides < 2 || sides > 1_000_000) {
            throw new IllegalStateException("Randomizer " + definition.id + " exceeds safe dice limits");
        }
        ArrayList<Integer> draws = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int draw = source.nextInt(sides);
            if (draw < 0 || draw >= sides) throw new IllegalArgumentException("Random source returned an invalid value");
            draws.add(draw + 1);
        }
        BigDecimal threshold = definition.successThresholdFormula.evaluate(context);
        BigDecimal value = switch (definition.keep) {
            case SUM -> BigDecimal.valueOf(draws.stream().mapToLong(Integer::longValue).sum());
            case HIGHEST -> BigDecimal.valueOf(draws.stream().mapToInt(Integer::intValue).max().orElseThrow());
            case LOWEST -> BigDecimal.valueOf(draws.stream().mapToInt(Integer::intValue).min().orElseThrow());
            case SUCCESSES -> BigDecimal.valueOf(draws.stream()
                    .filter(draw -> BigDecimal.valueOf(draw).compareTo(threshold) >= 0).count());
        };
        RuleValue tableValue = definition.tableRef.isEmpty() ? null : table(definition.tableRef).value(value);
        return new RandomizerResult(definition.id, draws, value, tableValue);
    }

    public RuleRuntimeState beginTurn(RuleRuntimeState state) {
        Objects.requireNonNull(state, "state");
        LinkedHashMap<String, RuleFormula> formulas = new LinkedHashMap<>();
        turnStructures.values().stream().sorted(Comparator.comparing(TurnStructureDefinition::id)).forEach(structure ->
                structure.budgets.forEach((id, formula) -> {
                    if (formulas.putIfAbsent(id, formula) != null) {
                        throw new IllegalStateException("Duplicate turn resource " + id);
                    }
                }));
        LinkedHashMap<String, BigDecimal> budget = new LinkedHashMap<>();
        LinkedHashSet<String> initializing = new LinkedHashSet<>();
        formulas.keySet().stream().sorted().forEach(id ->
                initializeTurnResource(id, state, formulas, budget, initializing));
        return state.withTurnBudget(budget);
    }

    private void initializeTurnResource(
            String id,
            RuleRuntimeState state,
            Map<String, RuleFormula> formulas,
            LinkedHashMap<String, BigDecimal> budget,
            LinkedHashSet<String> initializing) {
        if (budget.containsKey(id)) return;
        if (!initializing.add(id)) {
            throw new IllegalStateException("Cyclic turn resource initialization at " + id);
        }
        RuleFormula formula = formulas.get(id);
        if (formula == null) throw new IllegalArgumentException("Unknown turn resource " + id);
        formula.valueReferences().stream()
                .filter(reference -> reference.startsWith("turn:"))
                .map(reference -> reference.substring("turn:".length()))
                .filter(formulas::containsKey)
                .sorted()
                .forEach(dependency -> initializeTurnResource(dependency, state, formulas, budget, initializing));
        RuleRuntimeState frame = new RuleRuntimeState(
                state.values(), state.resources(), state.conditionStacks(), budget,
                state.activeRuleIds(), state.revision());
        BigDecimal amount = formula.evaluate(new RuntimeContext(frame, Map.of(), new LinkedHashSet<>()));
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Turn resource " + id + " cannot start negative");
        }
        budget.put(id, amount);
        initializing.remove(id);
    }

    /** Verifica il totale per pool canonico, includendo alias diversi della stessa risorsa. */
    private void validateActionCosts(
            ActionDefinition action,
            RuleRuntimeState original,
            List<BigDecimal> amounts) {
        LinkedHashMap<CostKey, BigDecimal> totals = new LinkedHashMap<>();
        for (int index = 0; index < action.costs.size(); index++) {
            ActionCost cost = action.costs.get(index);
            BigDecimal amount = amounts.get(index);
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("Action cost cannot be negative");
            }
            String target = cost.pool == CostPool.RESOURCE ? resolveId(cost.targetRef) : cost.targetRef;
            CostKey key = new CostKey(cost.pool, target);
            totals.merge(key, amount, BigDecimal::add);
        }
        totals.forEach((key, required) -> {
            BigDecimal available = key.pool == CostPool.TURN
                    ? original.turnBudget().getOrDefault(key.target, BigDecimal.ZERO)
                    : original.resources().containsKey(key.target)
                        ? original.resources().get(key.target).current()
                        : BigDecimal.ZERO;
            if (available.compareTo(required) < 0) {
                throw new IllegalStateException("Not enough " + key.target + " for action " + action.id);
            }
        });
    }

    public RuleExecutionResult executeAction(String actionId, RuleRuntimeState original) {
        ActionDefinition action = actions.get(resolveId(actionId));
        if (action == null) throw new IllegalArgumentException("Unknown executable action " + actionId);
        if (!action.ownerRef.isEmpty() && !original.activeRuleIds().contains(resolveId(action.ownerRef))) {
            throw new IllegalStateException("Action " + action.id + " is not active for this state");
        }
        RuntimeContext initialContext = new RuntimeContext(original, Map.of(), new LinkedHashSet<>());
        if (!truth(action.conditionFormula.evaluate(initialContext))) {
            throw new IllegalStateException("Action " + action.id + " prerequisites are not satisfied");
        }

        // Prima si verificano tutti i costi; nessun costo parziale puo' sfuggire.
        List<BigDecimal> amounts = action.costs.stream()
                .map(cost -> cost.amountFormula.evaluate(initialContext)).toList();
        validateActionCosts(action, original, amounts);

        RuleRuntimeState state = original;
        ArrayList<RuleRuntimeEvent> events = new ArrayList<>();
        long sequence = 0;
        for (int index = 0; index < action.costs.size(); index++) {
            ActionCost cost = action.costs.get(index);
            String target = cost.pool == CostPool.TURN ? cost.targetRef : resolveId(cost.targetRef);
            BigDecimal amount = amounts.get(index);
            if (cost.pool == CostPool.TURN) {
                LinkedHashMap<String, BigDecimal> budget = new LinkedHashMap<>(state.turnBudget());
                BigDecimal before = budget.getOrDefault(target, BigDecimal.ZERO);
                budget.put(target, before.subtract(amount));
                state = state.withTurnBudget(budget);
                events.add(event(sequence++, "TURN_RESOURCE_SPENT", action.id, target, before, budget.get(target)));
            } else {
                RuleRuntimeState.ResourceState before = state.resources().get(target);
                RuleRuntimeState.ResourceState after = before.withCurrent(before.current().subtract(amount));
                state = state.withResource(after);
                events.add(event(sequence++, "RESOURCE_CHANGED", action.id, target, before.current(), after.current()));
            }
        }
        for (String effectRef : action.effectRefs) {
            Applied applied = applyEffect(state, modifiers.get(resolveId(effectRef)), action.id, sequence);
            state = applied.state;
            sequence += applied.events.size();
            events.addAll(applied.events);
        }
        events.add(new RuleRuntimeEvent(sequence++, "ACTION_EXECUTED", action.id, "", Map.of()));
        RuleExecutionResult triggered = fireEvents(state, events, List.of(), sequence);
        ArrayList<RuleRuntimeEvent> combined = new ArrayList<>(events);
        combined.addAll(triggered.events());
        return new RuleExecutionResult(triggered.state(), combined);
    }

    /**
     * Esegue un'azione su un frame atomico source/target/session.
     *
     * <p>I costi e i prerequisiti appartengono sempre a {@code sourceScope};
     * ciascun effetto sceglie il destinatario tramite {@link EffectRecipient}.
     * Scope coincidenti condividono la stessa voce nella mappa e non possono
     * quindi divergere durante il comando.</p>
     */
    public ScopedRuleExecutionResult executeScopedAction(
            String actionId,
            RuleScope sourceScope,
            RuleScope targetScope,
            Map<RuleScope, RuleRuntimeState> originalStates) {
        RuleScope source = Objects.requireNonNull(sourceScope, "sourceScope");
        RuleScope target = Objects.requireNonNull(targetScope, "targetScope");
        LinkedHashMap<RuleScope, RuleRuntimeState> states = scopedFrame(originalStates, source, target);
        ActionDefinition action = actions.get(resolveId(actionId));
        if (action == null) throw new IllegalArgumentException("Unknown executable action " + actionId);
        RuleRuntimeState originalSource = states.get(source);
        if (!action.ownerRef.isEmpty() && !originalSource.activeRuleIds().contains(resolveId(action.ownerRef))) {
            throw new IllegalStateException("Action " + action.id + " is not active for this state");
        }
        RuntimeContext initialContext = new RuntimeContext(originalSource, Map.of(), new LinkedHashSet<>());
        if (!truth(action.conditionFormula.evaluate(initialContext))) {
            throw new IllegalStateException("Action " + action.id + " prerequisites are not satisfied");
        }

        List<BigDecimal> amounts = action.costs.stream()
                .map(cost -> cost.amountFormula.evaluate(initialContext)).toList();
        validateActionCosts(action, originalSource, amounts);

        RuleRuntimeState sourceState = originalSource;
        ArrayList<RuleRuntimeEvent> events = new ArrayList<>();
        long sequence = 0;
        for (int index = 0; index < action.costs.size(); index++) {
            ActionCost cost = action.costs.get(index);
            String costTarget = cost.pool == CostPool.TURN ? cost.targetRef : resolveId(cost.targetRef);
            BigDecimal amount = amounts.get(index);
            if (cost.pool == CostPool.TURN) {
                LinkedHashMap<String, BigDecimal> budget = new LinkedHashMap<>(sourceState.turnBudget());
                BigDecimal before = budget.getOrDefault(costTarget, BigDecimal.ZERO);
                budget.put(costTarget, before.subtract(amount));
                sourceState = sourceState.withTurnBudget(budget);
                events.add(scopedEvent(
                        event(sequence++, "TURN_RESOURCE_SPENT", action.id, costTarget, before, budget.get(costTarget)),
                        source));
            } else {
                RuleRuntimeState.ResourceState before = sourceState.resources().get(costTarget);
                RuleRuntimeState.ResourceState after = before.withCurrent(before.current().subtract(amount));
                sourceState = sourceState.withResource(after);
                events.add(scopedEvent(
                        event(sequence++, "RESOURCE_CHANGED", action.id, costTarget, before.current(), after.current()),
                        source));
            }
        }
        states.put(source, sourceState);
        for (String effectRef : action.effectRefs) {
            ScopedApplied applied = applyScopedEffect(
                    states, modifiers.get(resolveId(effectRef)), action.id, sequence, source, target);
            states = applied.states;
            sequence += applied.events.size();
            events.addAll(applied.events);
        }
        events.add(new RuleRuntimeEvent(sequence++, "ACTION_EXECUTED", action.id, "", Map.of(
                "sourceScope", source.canonicalKey(), "targetScope", target.canonicalKey())));
        ScopedRuleExecutionResult triggered = fireScopedEvents(
                states, events.stream().map(RuleRuntimeEvent::type).toList(), sequence, source, target);
        ArrayList<RuleRuntimeEvent> combined = new ArrayList<>(events);
        combined.addAll(triggered.events());
        return new ScopedRuleExecutionResult(triggered.states(), combined);
    }

    /**
     * Variante atomica multi-bersaglio: i costi si pagano una sola volta, gli
     * effetti TARGET raggiungono ogni scope scelto e SELF/SESSION si applicano
     * una sola volta. L'ordine dei bersagli fornito dal comando determina
     * soltanto l'ordine degli eventi, non il risultato finale.
     */
    public ScopedRuleExecutionResult executeScopedActionToTargets(
            String actionId,
            RuleScope sourceScope,
            List<RuleScope> targetScopes,
            Map<RuleScope, RuleRuntimeState> originalStates) {
        RuleScope source = Objects.requireNonNull(sourceScope, "sourceScope");
        List<RuleScope> targets = List.copyOf(new LinkedHashSet<>(
                Objects.requireNonNull(targetScopes, "targetScopes")));
        if (targets.isEmpty()) throw new IllegalArgumentException("A rule action needs at least one target scope");
        if (targets.stream().anyMatch(Objects::isNull)) throw new NullPointerException("targetScopes contains null");
        if (targets.size() == 1) return executeScopedAction(actionId, source, targets.get(0), originalStates);

        LinkedHashMap<RuleScope, RuleRuntimeState> states = scopedFrameForTargets(originalStates, source, targets);
        ActionDefinition action = actions.get(resolveId(actionId));
        if (action == null) throw new IllegalArgumentException("Unknown executable action " + actionId);
        RuleRuntimeState sourceState = states.get(source);
        if (!action.ownerRef.isEmpty() && !sourceState.activeRuleIds().contains(resolveId(action.ownerRef))) {
            throw new IllegalStateException("Action " + action.id + " is not active for this state");
        }
        RuntimeContext initialContext = new RuntimeContext(sourceState, Map.of(), new LinkedHashSet<>());
        if (!truth(action.conditionFormula.evaluate(initialContext))) {
            throw new IllegalStateException("Action " + action.id + " prerequisites are not satisfied");
        }
        List<BigDecimal> amounts = action.costs.stream()
                .map(cost -> cost.amountFormula.evaluate(initialContext)).toList();
        validateActionCosts(action, sourceState, amounts);

        ArrayList<RuleRuntimeEvent> events = new ArrayList<>();
        long sequence = 0;
        for (int index = 0; index < action.costs.size(); index++) {
            ActionCost cost = action.costs.get(index);
            String costTarget = cost.pool == CostPool.TURN ? cost.targetRef : resolveId(cost.targetRef);
            BigDecimal amount = amounts.get(index);
            if (cost.pool == CostPool.TURN) {
                LinkedHashMap<String, BigDecimal> budget = new LinkedHashMap<>(sourceState.turnBudget());
                BigDecimal before = budget.getOrDefault(costTarget, BigDecimal.ZERO);
                budget.put(costTarget, before.subtract(amount));
                sourceState = sourceState.withTurnBudget(budget);
                events.add(scopedEvent(
                        event(sequence++, "TURN_RESOURCE_SPENT", action.id, costTarget, before, budget.get(costTarget)),
                        source));
            } else {
                RuleRuntimeState.ResourceState before = sourceState.resources().get(costTarget);
                RuleRuntimeState.ResourceState after = before.withCurrent(before.current().subtract(amount));
                sourceState = sourceState.withResource(after);
                events.add(scopedEvent(
                        event(sequence++, "RESOURCE_CHANGED", action.id, costTarget, before.current(), after.current()),
                        source));
            }
        }
        states.put(source, sourceState);
        for (String effectRef : action.effectRefs) {
            ModifierDefinition effect = modifiers.get(resolveId(effectRef));
            if (effect == null) throw new IllegalArgumentException("Action references a missing effect " + effectRef);
            List<RuleScope> recipients = effect.recipient == EffectRecipient.TARGET ? targets : List.of(targets.get(0));
            for (RuleScope target : recipients) {
                ScopedApplied applied = applyScopedEffect(states, effect, action.id, sequence, source, target);
                states = applied.states;
                sequence += applied.events.size();
                events.addAll(applied.events);
            }
        }
        String targetKeys = targets.stream().map(RuleScope::canonicalKey)
                .collect(java.util.stream.Collectors.joining(","));
        events.add(new RuleRuntimeEvent(sequence++, "ACTION_EXECUTED", action.id, "", Map.of(
                "sourceScope", source.canonicalKey(), "targetScopes", targetKeys)));
        ScopedRuleExecutionResult triggered = fireScopedEventsToTargets(
                states, events.stream().map(RuleRuntimeEvent::type).toList(), sequence, source, targets);
        ArrayList<RuleRuntimeEvent> combined = new ArrayList<>(events);
        combined.addAll(triggered.events());
        return new ScopedRuleExecutionResult(triggered.states(), combined);
    }

    public RuleExecutionResult fireEvent(String eventType, RuleRuntimeState state) {
        String event = requireId(eventType).toUpperCase(Locale.ROOT);
        ArrayList<RuleRuntimeEvent> events = new ArrayList<>();
        RuleRuntimeState recovered = recoverResources(event, state, events);
        RuleExecutionResult triggered = fireEvents(recovered, events, List.of(event), events.size());
        ArrayList<RuleRuntimeEvent> combined = new ArrayList<>(events);
        combined.addAll(triggered.events());
        RuleRuntimeState expired = expireState(event, triggered.state(), combined);
        return new RuleExecutionResult(expired, combined);
    }

    public ScopedRuleExecutionResult fireScopedEvent(
            String eventType,
            RuleScope sourceScope,
            RuleScope targetScope,
            Map<RuleScope, RuleRuntimeState> originalStates) {
        String event = requireId(eventType).toUpperCase(Locale.ROOT);
        RuleScope source = Objects.requireNonNull(sourceScope, "sourceScope");
        RuleScope target = Objects.requireNonNull(targetScope, "targetScope");
        LinkedHashMap<RuleScope, RuleRuntimeState> states = scopedFrame(originalStates, source, target);
        ArrayList<RuleRuntimeEvent> events = new ArrayList<>();
        RuleRuntimeState recovered = recoverResources(event, states.get(source), events);
        states.put(source, recovered);
        ArrayList<RuleRuntimeEvent> scopedRecovery = new ArrayList<>(events.size());
        events.forEach(produced -> scopedRecovery.add(scopedEvent(produced, source)));
        ArrayList<String> emittedTypes = events.stream()
                .map(RuleRuntimeEvent::type)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        emittedTypes.add(event);
        ScopedRuleExecutionResult triggered = fireScopedEvents(
                states, emittedTypes, events.size(), source, target);
        scopedRecovery.addAll(triggered.events());
        LinkedHashMap<RuleScope, RuleRuntimeState> expiredStates = new LinkedHashMap<>(triggered.states());
        RuleRuntimeState expired = expireState(event, expiredStates.get(source), scopedRecovery);
        expiredStates.put(source, expired);
        return new ScopedRuleExecutionResult(expiredStates, scopedRecovery);
    }

    /** Policy effettiva; le entita' senza dichiarazione restano permanenti e locali. */
    public StatePersistencePolicy persistencePolicy(String ruleId) {
        String resolved = resolveId(ruleId);
        if (!entities.containsKey(resolved)) throw new IllegalArgumentException("Unknown rule " + ruleId);
        return persistencePolicies.getOrDefault(resolved, StatePersistencePolicy.persistentLocal());
    }

    public boolean isSheetSectionVisible(String sectionId, RuleRuntimeState state) {
        SheetSectionDefinition section = sheetSections.get(resolveId(sectionId));
        if (section == null) throw new IllegalArgumentException("Unknown executable sheet section " + sectionId);
        return truth(section.visibilityFormula().evaluate(
                new RuntimeContext(Objects.requireNonNull(state, "state"), Map.of(), new LinkedHashSet<>())));
    }

    /**
     * Applica le scadenze dopo recuperi e trigger del medesimo evento.
     *
     * <p>Il reset e' costruito in memoria e pubblicato come un solo nuovo stato:
     * nessun osservatore puo' vedere meta' delle risorse gia' ripristinate e
     * meta' ancora appartenenti alla scena precedente.</p>
     */
    private RuleRuntimeState expireState(
            String event,
            RuleRuntimeState original,
            List<RuleRuntimeEvent> events) {
        List<String> expiring = persistencePolicies.entrySet().stream()
                .filter(entry -> entry.getValue().expiresOn(event))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (expiring.isEmpty()) return original;

        LinkedHashMap<String, RuleValue> values = new LinkedHashMap<>(original.values());
        LinkedHashMap<String, RuleRuntimeState.ResourceState> pools = new LinkedHashMap<>(original.resources());
        LinkedHashMap<String, Integer> stacks = new LinkedHashMap<>(original.conditionStacks());
        LinkedHashMap<String, BigDecimal> budget = new LinkedHashMap<>(original.turnBudget());
        RuleRuntimeState fresh = null;
        boolean resetTurn = false;
        boolean changed = false;

        for (String id : expiring) {
            RuleEntity entity = entities.get(id);
            if (entity == null) continue;
            switch (entity.kind()) {
                case VALUE -> {
                    ValueDefinition definition = valueDefinitions.get(id);
                    if (definition == null) continue;
                    RuleValue before = values.get(id);
                    RuleValue after = definition.defaultValue();
                    if (!Objects.equals(before, after)) {
                        values.put(id, after);
                        changed = true;
                        events.add(new RuleRuntimeEvent(events.size(), "STATE_EXPIRED", id, id, Map.of(
                                "event", event, "before", before == null ? "" : before.canonicalValue(),
                                "after", after.canonicalValue())));
                    }
                }
                case STAT, SKILL, SAVE, DEFENSE -> {
                    RuleValue before = values.remove(id);
                    if (before != null) {
                        changed = true;
                        events.add(new RuleRuntimeEvent(events.size(), "STATE_EXPIRED", id, id,
                                Map.of("event", event, "before", before.canonicalValue(), "after", "derived")));
                    }
                }
                case RESOURCE, TRACK -> {
                    if (fresh == null) fresh = initialState(values, original.activeRuleIds());
                    RuleRuntimeState.ResourceState before = pools.get(id);
                    RuleRuntimeState.ResourceState after = fresh.resources().get(id);
                    if (after != null && !Objects.equals(before, after)) {
                        pools.put(id, after);
                        changed = true;
                        events.add(new RuleRuntimeEvent(events.size(), "STATE_EXPIRED", id, id, Map.of(
                                "event", event,
                                "before", before == null ? "" : before.current().toPlainString(),
                                "after", after.current().toPlainString())));
                    }
                }
                case CONDITION -> {
                    Integer before = stacks.remove(id);
                    if (before != null) {
                        changed = true;
                        events.add(new RuleRuntimeEvent(events.size(), "STATE_EXPIRED", id, id,
                                Map.of("event", event, "before", before.toString(), "after", "0")));
                    }
                }
                case ACTION_ECONOMY -> resetTurn = true;
                default -> { }
            }
        }
        if (resetTurn) {
            RuleRuntimeState frame = new RuleRuntimeState(
                    values, pools, stacks, budget, original.activeRuleIds(), original.revision());
            Map<String, BigDecimal> reset = beginTurn(frame).turnBudget();
            if (!reset.equals(budget)) {
                budget.clear();
                budget.putAll(reset);
                changed = true;
                events.add(new RuleRuntimeEvent(events.size(), "STATE_EXPIRED", "action-economy", "", Map.of(
                        "event", event, "after", "reset")));
            }
        }
        if (!changed) return original;
        return new RuleRuntimeState(
                values, pools, stacks, budget, original.activeRuleIds(), original.revision() + 1);
    }

    public int levelForExperience(BigDecimal experience) {
        if (progression == null || progression.experienceTableRef.isEmpty()) {
            throw new IllegalStateException("This ruleset does not define an experience progression");
        }
        RuleValue value = table(progression.experienceTableRef).value(experience.max(BigDecimal.ZERO));
        int level = value.asNumber().intValueExact();
        return Math.max(progression.minimumLevel, Math.min(progression.maximumLevel, level));
    }

    public BigDecimal experienceForLevel(int level) {
        if (progression == null || progression.experienceTableRef.isEmpty()) {
            throw new IllegalStateException("This ruleset does not define an experience progression");
        }
        int normalized = Math.max(progression.minimumLevel, Math.min(progression.maximumLevel, level));
        TableDefinition table = table(progression.experienceTableRef);
        return table.rows.entrySet().stream()
                .filter(entry -> entry.getValue().type() == RuleValue.Type.NUMBER
                        && entry.getValue().asNumber().intValue() == normalized)
                .map(Map.Entry::getKey)
                .min(BigDecimal::compareTo)
                .orElseThrow(() -> new IllegalStateException("No experience threshold for level " + normalized));
    }

    private RuleRuntimeState recoverResources(String event, RuleRuntimeState original, List<RuleRuntimeEvent> events) {
        RuleRuntimeState state = original;
        for (ResourceDefinition definition : resources.values()) {
            if (!definition.recoveryEvent.equals(event)) continue;
            RuleRuntimeState.ResourceState before = state.resources().get(definition.id);
            if (before == null) continue;
            RuntimeContext context = new RuntimeContext(state, Map.of(
                    "current", before.current(), "maximum", before.maximum()), new LinkedHashSet<>());
            BigDecimal recovered = definition.recoveryFormula.evaluate(context).max(BigDecimal.ZERO).min(before.maximum());
            RuleRuntimeState.ResourceState after = before.withCurrent(recovered);
            if (!after.equals(before)) {
                state = state.withResource(after);
                events.add(event(events.size(), "RESOURCE_CHANGED", definition.id, definition.id,
                        before.current(), after.current()));
            }
        }
        return state;
    }

    private RuleExecutionResult fireEvents(
            RuleRuntimeState original,
            List<RuleRuntimeEvent> prior,
            List<String> initialEvents,
            long initialSequence) {
        RuleRuntimeState state = original;
        ArrayList<RuleRuntimeEvent> emitted = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        prior.forEach(produced -> queue.addLast(produced.type().toUpperCase(Locale.ROOT)));
        initialEvents.forEach(value -> queue.add(value.toUpperCase(Locale.ROOT)));
        LinkedHashMap<String, Integer> executions = new LinkedHashMap<>();
        long sequence = initialSequence;
        int processed = 0;
        while (!queue.isEmpty()) {
            if (++processed > MAX_TRIGGER_EVENTS) throw new IllegalStateException("Trigger event budget exceeded");
            String event = queue.removeFirst();
            List<TriggerDefinition> matching = triggers.values().stream()
                    .filter(trigger -> trigger.event.equals(event))
                    .sorted(Comparator.comparingInt(TriggerDefinition::priority).reversed()
                            .thenComparing(TriggerDefinition::id))
                    .toList();
            for (TriggerDefinition trigger : matching) {
                int used = executions.getOrDefault(trigger.id, 0);
                if (used >= trigger.maximumExecutions) continue;
                RuntimeContext context = new RuntimeContext(state, Map.of("eventCount", BigDecimal.valueOf(processed)),
                        new LinkedHashSet<>());
                if (!truth(trigger.conditionFormula.evaluate(context))) continue;
                executions.put(trigger.id, used + 1);
                for (String effectRef : trigger.effectRefs) {
                    Applied applied = applyEffect(state, modifiers.get(resolveId(effectRef)), trigger.id, sequence);
                    state = applied.state;
                    sequence += applied.events.size();
                    emitted.addAll(applied.events);
                    applied.events.forEach(produced -> queue.addLast(produced.type()));
                }
                emitted.add(new RuleRuntimeEvent(sequence++, "TRIGGER_FIRED", trigger.id, "",
                        Map.of("event", event, "execution", Integer.toString(used + 1))));
            }
        }
        return new RuleExecutionResult(state, emitted);
    }

    private ScopedRuleExecutionResult fireScopedEvents(
            Map<RuleScope, RuleRuntimeState> originalStates,
            List<String> initialEvents,
            long initialSequence,
            RuleScope source,
            RuleScope target) {
        LinkedHashMap<RuleScope, RuleRuntimeState> states = scopedFrame(originalStates, source, target);
        ArrayList<RuleRuntimeEvent> emitted = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        initialEvents.forEach(value -> queue.add(value.toUpperCase(Locale.ROOT)));
        LinkedHashMap<String, Integer> executions = new LinkedHashMap<>();
        long sequence = initialSequence;
        int processed = 0;
        while (!queue.isEmpty()) {
            if (++processed > MAX_TRIGGER_EVENTS) throw new IllegalStateException("Trigger event budget exceeded");
            String event = queue.removeFirst();
            List<TriggerDefinition> matching = triggers.values().stream()
                    .filter(trigger -> trigger.event.equals(event))
                    .sorted(Comparator.comparingInt(TriggerDefinition::priority).reversed()
                            .thenComparing(TriggerDefinition::id))
                    .toList();
            for (TriggerDefinition trigger : matching) {
                int used = executions.getOrDefault(trigger.id, 0);
                if (used >= trigger.maximumExecutions) continue;
                RuntimeContext context = new RuntimeContext(states.get(source),
                        Map.of("eventCount", BigDecimal.valueOf(processed)), new LinkedHashSet<>());
                if (!truth(trigger.conditionFormula.evaluate(context))) continue;
                executions.put(trigger.id, used + 1);
                for (String effectRef : trigger.effectRefs) {
                    ScopedApplied applied = applyScopedEffect(
                            states, modifiers.get(resolveId(effectRef)), trigger.id, sequence, source, target);
                    states = applied.states;
                    sequence += applied.events.size();
                    emitted.addAll(applied.events);
                    applied.events.forEach(produced -> queue.addLast(produced.type()));
                }
                emitted.add(new RuleRuntimeEvent(sequence++, "TRIGGER_FIRED", trigger.id, "", Map.of(
                        "event", event,
                        "execution", Integer.toString(used + 1),
                        "sourceScope", source.canonicalKey(),
                        "targetScope", target.canonicalKey())));
            }
        }
        return new ScopedRuleExecutionResult(states, emitted);
    }

    private ScopedRuleExecutionResult fireScopedEventsToTargets(
            Map<RuleScope, RuleRuntimeState> originalStates,
            List<String> initialEvents,
            long initialSequence,
            RuleScope source,
            List<RuleScope> targets) {
        LinkedHashMap<RuleScope, RuleRuntimeState> states = scopedFrameForTargets(originalStates, source, targets);
        ArrayList<RuleRuntimeEvent> emitted = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        initialEvents.forEach(value -> queue.add(value.toUpperCase(Locale.ROOT)));
        LinkedHashMap<String, Integer> executions = new LinkedHashMap<>();
        long sequence = initialSequence;
        int processed = 0;
        while (!queue.isEmpty()) {
            if (++processed > MAX_TRIGGER_EVENTS) throw new IllegalStateException("Trigger event budget exceeded");
            String event = queue.removeFirst();
            List<TriggerDefinition> matching = triggers.values().stream()
                    .filter(trigger -> trigger.event.equals(event))
                    .sorted(Comparator.comparingInt(TriggerDefinition::priority).reversed()
                            .thenComparing(TriggerDefinition::id))
                    .toList();
            for (TriggerDefinition trigger : matching) {
                int used = executions.getOrDefault(trigger.id, 0);
                if (used >= trigger.maximumExecutions) continue;
                RuntimeContext context = new RuntimeContext(states.get(source),
                        Map.of("eventCount", BigDecimal.valueOf(processed)), new LinkedHashSet<>());
                if (!truth(trigger.conditionFormula.evaluate(context))) continue;
                executions.put(trigger.id, used + 1);
                for (String effectRef : trigger.effectRefs) {
                    ModifierDefinition effect = modifiers.get(resolveId(effectRef));
                    if (effect == null) throw new IllegalArgumentException("Trigger references a missing effect " + effectRef);
                    List<RuleScope> recipients = effect.recipient == EffectRecipient.TARGET
                            ? targets : List.of(targets.get(0));
                    for (RuleScope target : recipients) {
                        ScopedApplied applied = applyScopedEffect(
                                states, effect, trigger.id, sequence, source, target);
                        states = applied.states;
                        sequence += applied.events.size();
                        emitted.addAll(applied.events);
                        applied.events.forEach(produced -> queue.addLast(produced.type()));
                    }
                }
                emitted.add(new RuleRuntimeEvent(sequence++, "TRIGGER_FIRED", trigger.id, "", Map.of(
                        "event", event,
                        "execution", Integer.toString(used + 1),
                        "sourceScope", source.canonicalKey(),
                        "targetScopes", targets.stream().map(RuleScope::canonicalKey)
                                .collect(java.util.stream.Collectors.joining(",")))));
            }
        }
        return new ScopedRuleExecutionResult(states, emitted);
    }

    private ScopedApplied applyScopedEffect(
            Map<RuleScope, RuleRuntimeState> originalStates,
            ModifierDefinition effect,
            String sourceRuleId,
            long sequence,
            RuleScope source,
            RuleScope target) {
        if (effect == null) throw new IllegalArgumentException("Action or trigger references a missing effect");
        RuleScope recipient = switch (effect.recipient) {
            case SELF -> source;
            case TARGET -> target;
            case SESSION -> RuleScope.session();
        };
        LinkedHashMap<RuleScope, RuleRuntimeState> states = scopedFrame(originalStates, source, target);
        Applied applied = applyEffect(states.get(recipient), effect, sourceRuleId, sequence);
        states.put(recipient, applied.state);
        List<RuleRuntimeEvent> events = applied.events.stream()
                .map(event -> scopedEvent(event, recipient))
                .toList();
        return new ScopedApplied(states, events);
    }

    private static LinkedHashMap<RuleScope, RuleRuntimeState> scopedFrame(
            Map<RuleScope, RuleRuntimeState> original,
            RuleScope source,
            RuleScope target) {
        Objects.requireNonNull(original, "states");
        LinkedHashMap<RuleScope, RuleRuntimeState> states = new LinkedHashMap<>();
        original.forEach((scope, state) -> states.put(
                Objects.requireNonNull(scope, "scope"), Objects.requireNonNull(state, "state")));
        LinkedHashSet<RuleScope> requiredScopes = new LinkedHashSet<>();
        requiredScopes.add(source);
        requiredScopes.add(target);
        requiredScopes.add(RuleScope.session());
        for (RuleScope required : requiredScopes) {
            if (!states.containsKey(required)) {
                throw new IllegalArgumentException("Missing scoped action state " + required.canonicalKey());
            }
        }
        return states;
    }

    private static LinkedHashMap<RuleScope, RuleRuntimeState> scopedFrameForTargets(
            Map<RuleScope, RuleRuntimeState> original,
            RuleScope source,
            List<RuleScope> targets) {
        Objects.requireNonNull(original, "states");
        LinkedHashMap<RuleScope, RuleRuntimeState> states = new LinkedHashMap<>();
        original.forEach((scope, state) -> states.put(
                Objects.requireNonNull(scope, "scope"), Objects.requireNonNull(state, "state")));
        LinkedHashSet<RuleScope> required = new LinkedHashSet<>();
        required.add(RuleScope.session());
        required.add(source);
        required.addAll(targets);
        for (RuleScope scope : required) {
            if (!states.containsKey(scope)) {
                throw new IllegalArgumentException("Missing scoped action state " + scope.canonicalKey());
            }
        }
        return states;
    }

    private static RuleRuntimeEvent scopedEvent(RuleRuntimeEvent event, RuleScope scope) {
        LinkedHashMap<String, String> details = new LinkedHashMap<>(event.details());
        details.put("scopeKind", scope.kind().name());
        details.put("scopeId", scope.id());
        return new RuleRuntimeEvent(
                event.sequence(), event.type(), event.sourceRuleId(), event.targetId(), details);
    }

    private Applied applyEffect(RuleRuntimeState state, ModifierDefinition effect, String source, long sequence) {
        if (effect == null) throw new IllegalArgumentException("Action or trigger references a missing effect");
        if (effect.application == EffectApplication.STATIC) {
            throw new IllegalArgumentException("Static modifier " + effect.id + " cannot be applied as an event effect");
        }
        RuntimeContext baseContext = new RuntimeContext(state, Map.of(), new LinkedHashSet<>());
        if (!truth(effect.conditionFormula.evaluate(baseContext))) return new Applied(state, List.of());
        String target = resolveId(effect.targetRef);
        if (effect.application == EffectApplication.SET_VALUE) {
            ValueDefinition definition = valueDefinitions.get(target);
            if (definition == null || !definition.mutable || !definition.accepts(effect.literalValue)) {
                throw new IllegalArgumentException("Invalid typed value effect for " + target);
            }
            RuleValue beforeValue = ruleValue(target, state);
            if (beforeValue.equals(effect.literalValue)) return new Applied(state, List.of());
            RuleRuntimeState changed = state.withValue(target, effect.literalValue);
            RuleRuntimeEvent event = new RuleRuntimeEvent(sequence, "VALUE_SET", source, target, Map.of(
                    "beforeType", beforeValue.type().name(),
                    "before", beforeValue.canonicalValue(),
                    "afterType", effect.literalValue.type().name(),
                    "after", effect.literalValue.canonicalValue()));
            return new Applied(changed, List.of(event));
        }
        BigDecimal before;
        if (effect.application == EffectApplication.CHANGE_VALUE) {
            before = value(target, state);
        } else if (effect.application == EffectApplication.CHANGE_RESOURCE) {
            RuleRuntimeState.ResourceState resource = state.resources().get(target);
            if (resource == null) throw new IllegalArgumentException("Missing target resource " + target);
            before = resource.current();
        } else {
            before = BigDecimal.valueOf(state.conditionStacks().getOrDefault(target, 0));
        }
        RuntimeContext context = new RuntimeContext(state, Map.of("current", before), new LinkedHashSet<>());
        BigDecimal operand = effect.valueFormula.evaluate(context);
        BigDecimal after = apply(effect.operation, before, operand);
        RuleRuntimeState changed;
        String eventType;
        switch (effect.application) {
            case CHANGE_VALUE -> {
                changed = state.withValue(target, RuleValue.number(after));
                after = value(target, changed);
                eventType = "VALUE_CHANGED";
            }
            case CHANGE_RESOURCE -> {
                RuleRuntimeState.ResourceState resource = state.resources().get(target);
                RuleRuntimeState.ResourceState next = resource.withCurrent(after);
                changed = state.withResource(next);
                after = next.current();
                eventType = "RESOURCE_CHANGED";
            }
            case ADD_CONDITION -> {
                int maximum = conditionMaximumStacks(target);
                int stacks = after.max(BigDecimal.ONE)
                        .min(BigDecimal.valueOf(maximum))
                        .setScale(0, RoundingMode.FLOOR)
                        .intValueExact();
                changed = state.withCondition(target, stacks);
                after = BigDecimal.valueOf(stacks);
                eventType = "CONDITION_ADDED";
            }
            case REMOVE_CONDITION -> {
                changed = state.withCondition(target, 0);
                after = BigDecimal.ZERO;
                eventType = "CONDITION_REMOVED";
            }
            default -> throw new IllegalStateException("Unsupported event effect " + effect.application);
        }
        if (normalize(before).compareTo(normalize(after)) == 0) return new Applied(state, List.of());
        return new Applied(changed, List.of(event(sequence, eventType, source, target, before, after)));
    }

    private int conditionMaximumStacks(String id) {
        ConditionDefinition definition = conditionDefinitions.get(id);
        if (definition != null) return definition.maximumStacks();
        RuleEntity entity = entities.get(id);
        if (entity == null) return 1;
        String raw = entity.attributes().getOrDefault("maximumStacks", "1");
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(id + ".maximumStacks must be an integer");
        }
    }

    private BigDecimal calculate(String resolvedId, RuleRuntimeState state, Set<String> path) {
        RuleValue supplied = state.values().get(resolvedId);
        StatDefinition stat = stats.get(resolvedId);
        SkillDefinition skill = skills.get(resolvedId);
        if (stat == null && skill == null) {
            if (supplied != null) {
                BigDecimal base = supplied.asNumber();
                if (valueDefinitions.containsKey(resolvedId)) {
                    if (!path.add(resolvedId)) {
                        throw new IllegalStateException("Cyclic runtime dependency at " + resolvedId);
                    }
                    base = applyStaticModifiers(resolvedId, base, state, path);
                    path.remove(resolvedId);
                }
                return normalize(base);
            }
            throw new IllegalArgumentException("Unknown numeric rule value " + resolvedId);
        }
        if (!path.add(resolvedId)) throw new IllegalStateException("Cyclic runtime dependency at " + resolvedId);
        RuntimeContext context = new RuntimeContext(state, Map.of(), path);
        BigDecimal base;
        if (stat != null) {
            base = supplied != null ? supplied.asNumber()
                    : (stat.derivedFormula != null ? stat.derivedFormula : stat.defaultFormula).evaluate(context);
            base = applyStaticModifiers(resolvedId, base, state, path);
            if (stat.minimumFormula != null) base = base.max(stat.minimumFormula.evaluate(context));
            if (stat.maximumFormula != null) base = base.min(stat.maximumFormula.evaluate(context));
            base = round(base, stat.rounding);
        } else {
            if (supplied != null) {
                base = supplied.asNumber();
            } else {
                base = skill.formula.evaluate(context);
                String trainedId = "trained:" + resolvedId;
                if (state.activeRuleIds().contains(trainedId)) {
                    base = base.add(skill.trainedBonusFormula.evaluate(context));
                }
            }
            base = applyStaticModifiers(resolvedId, base, state, path);
        }
        path.remove(resolvedId);
        return normalize(base);
    }

    private BigDecimal applyStaticModifiers(
            String target,
            BigDecimal initial,
            RuleRuntimeState state,
            Set<String> path) {
        List<ModifierDefinition> candidates = modifiers.values().stream()
                .filter(modifier -> modifier.application == EffectApplication.STATIC)
                .filter(modifier -> resolveId(modifier.targetRef).equals(target))
                .filter(modifier -> modifier.ownerRef.isEmpty()
                        || state.activeRuleIds().contains(resolveId(modifier.ownerRef)))
                .filter(modifier -> modifier.minimumLevel <= ownerLevel(modifier.ownerRef, state))
                .filter(modifier -> truth(modifier.conditionFormula.evaluate(
                        new RuntimeContext(state, Map.of("current", initial), path))))
                .sorted(Comparator.comparingInt(ModifierDefinition::priority)
                        .thenComparing(ModifierDefinition::id))
                .toList();
        LinkedHashMap<String, ModifierDefinition> grouped = new LinkedHashMap<>();
        ArrayList<ModifierDefinition> effective = new ArrayList<>();
        for (ModifierDefinition candidate : candidates) {
            if (candidate.group.isEmpty()) effective.add(candidate);
            else grouped.put(candidate.group, candidate); // ordinati: priorita' maggiore sostituisce.
        }
        effective.addAll(grouped.values());
        effective.sort(Comparator.comparingInt(ModifierDefinition::priority).thenComparing(ModifierDefinition::id));
        BigDecimal result = initial;
        for (ModifierDefinition modifier : effective) {
            BigDecimal operand = modifier.valueFormula.evaluate(
                    new RuntimeContext(state, Map.of("current", result), path));
            result = apply(modifier.operation, result, operand);
        }
        return result;
    }

    private int ownerLevel(String ownerRef, RuleRuntimeState state) {
        if (ownerRef.isEmpty()) return Integer.MAX_VALUE;
        RuleValue value = state.values().get("level:" + resolveId(ownerRef));
        return value == null ? 1 : value.asNumber().intValueExact();
    }

    private static void validateLevelValue(String id, RuleValue value) {
        try {
            int level = value.asNumber().intValueExact();
            if (level < 0 || level > 1_000_000) throw new ArithmeticException();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(
                    "Supplied level rule value " + id + " must be a whole number from 0 to 1000000");
        }
    }

    private BigDecimal apply(ModifierOperation operation, BigDecimal current, BigDecimal operand) {
        return switch (operation) {
            case ADD -> current.add(operand);
            case MULTIPLY -> current.multiply(operand);
            case SET -> operand;
            case MINIMUM -> current.max(operand);
            case MAXIMUM -> current.min(operand);
        };
    }

    private TableDefinition table(String id) {
        TableDefinition table = tables.get(resolveId(id));
        if (table == null) throw new IllegalArgumentException("Unknown rule table " + id);
        return table;
    }

    private final class RuntimeContext implements RuleFormula.Context {
        private final RuleRuntimeState state;
        private final Map<String, BigDecimal> local;
        private final Set<String> path;

        private RuntimeContext(RuleRuntimeState state, Map<String, BigDecimal> local, Set<String> path) {
            this.state = state;
            this.local = local;
            this.path = path;
        }

        @Override public BigDecimal value(String rawId) {
            BigDecimal localValue = local.get(rawId);
            if (localValue != null) return localValue;
            if (rawId.endsWith(":modifier")) {
                String scoreRef = rawId.substring(0, rawId.length() - ":modifier".length());
                String scoreId = resolveId(scoreRef);
                StatDefinition definition = stats.get(scoreId);
                if (definition == null) return null;
                BigDecimal score = calculate(scoreId, state, path);
                return definition.modifierFormula == null
                        ? score
                        : definition.modifierFormula.evaluate(
                                new RuntimeContext(state, Map.of("score", score), path));
            }
            String id = resolveId(rawId);
            localValue = local.get(id);
            if (localValue != null) return localValue;
            if (id.startsWith("resource:") && id.endsWith(":current")) {
                String resourceId = id.substring("resource:".length(), id.length() - ":current".length());
                RuleRuntimeState.ResourceState resource = state.resources().get(resolveId(resourceId));
                return resource == null ? null : resource.current();
            }
            if (id.startsWith("resource:") && id.endsWith(":maximum")) {
                String resourceId = id.substring("resource:".length(), id.length() - ":maximum".length());
                RuleRuntimeState.ResourceState resource = state.resources().get(resolveId(resourceId));
                return resource == null ? null : resource.maximum();
            }
            if (id.startsWith("condition:") && id.endsWith(":stacks")) {
                String conditionId = id.substring("condition:".length(), id.length() - ":stacks".length());
                return BigDecimal.valueOf(state.conditionStacks().getOrDefault(resolveId(conditionId), 0));
            }
            if (id.startsWith("turn:")) return state.turnBudget().get(id.substring("turn:".length()));
            RuleValue direct = state.values().get(id);
            if (direct != null && !stats.containsKey(id) && !skills.containsKey(id)) return direct.asNumber();
            return calculate(id, state, path);
        }

        @Override public BigDecimal lookup(String tableId, BigDecimal key) {
            return table(tableId).value(key).asNumber();
        }
    }

    private record Applied(RuleRuntimeState state, List<RuleRuntimeEvent> events) { }
    private record CostKey(CostPool pool, String target) { }
    private record ScopedApplied(
            LinkedHashMap<RuleScope, RuleRuntimeState> states,
            List<RuleRuntimeEvent> events) { }

    private static RuleRuntimeEvent event(
            long sequence,
            String type,
            String source,
            String target,
            BigDecimal before,
            BigDecimal after) {
        return new RuleRuntimeEvent(sequence, type, source, target,
                Map.of("before", normalize(before).toPlainString(), "after", normalize(after).toPlainString()));
    }

    private static BigDecimal round(BigDecimal value, StatRounding rounding) {
        return switch (rounding) {
            case NONE -> value;
            case FLOOR -> value.setScale(0, RoundingMode.FLOOR);
            case CEILING -> value.setScale(0, RoundingMode.CEILING);
            case HALF_UP -> value.setScale(0, RoundingMode.HALF_UP);
        };
    }

    private static boolean truth(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) != 0;
    }

    private static BigDecimal normalize(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : new BigDecimal(normalized.toPlainString());
    }

    private static Map<String, RuleFormula> immutableFormulaMap(Map<String, RuleFormula> source) {
        Objects.requireNonNull(source, "source");
        TreeMap<String, RuleFormula> sorted = new TreeMap<>();
        source.forEach((key, value) -> sorted.put(requireId(key), Objects.requireNonNull(value, "formula")));
        return Map.copyOf(new LinkedHashMap<>(sorted));
    }

    private static List<String> immutableIds(List<String> source) {
        Objects.requireNonNull(source, "source");
        ArrayList<String> ids = new ArrayList<>();
        source.forEach(id -> ids.add(requireId(id)));
        if (new LinkedHashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("Duplicate rule reference");
        return List.copyOf(ids);
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "id");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Rule id cannot be blank");
        return normalized;
    }
}
