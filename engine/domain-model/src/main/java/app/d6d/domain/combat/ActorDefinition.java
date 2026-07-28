package app.d6d.domain.combat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable library definition. CombatSession copies it into an encounter snapshot. */
public record ActorDefinition(
        String id,
        String definitionVersion,
        String rulesetVersion,
        String name,
        int armorClass,
        int maxHitPoints,
        int currentHitPoints,
        int temporaryHitPoints,
        int speedFeet,
        int initiativeModifier,
        int initiativeScore,
        int constitutionSaveBonus,
        Set<DamageType> resistances,
        Set<DamageType> vulnerabilities,
        Set<DamageType> damageImmunities,
        Set<ConditionType> conditionImmunities,
        List<AbilityDefinition> abilities,
        Map<SaveAbility, Integer> savingThrowBonuses,
        int spellSaveDc,
        int attacksPerAction) {

    public ActorDefinition {
        id = requireText(id, "id");
        definitionVersion = requireText(definitionVersion, "definitionVersion");
        rulesetVersion = requireText(rulesetVersion, "rulesetVersion");
        name = requireText(name, "name");
        if (armorClass < 0 || maxHitPoints <= 0 || currentHitPoints < 0 || currentHitPoints > maxHitPoints
                || temporaryHitPoints < 0 || speedFeet < 0) {
            throw new IllegalArgumentException("Invalid actor statistics");
        }
        if (spellSaveDc < 0) {
            throw new IllegalArgumentException("spellSaveDc cannot be negative");
        }
        if (attacksPerAction < 1) {
            throw new IllegalArgumentException("attacksPerAction must be at least 1");
        }
        resistances = Set.copyOf(Objects.requireNonNull(resistances, "resistances"));
        vulnerabilities = Set.copyOf(Objects.requireNonNull(vulnerabilities, "vulnerabilities"));
        damageImmunities = Set.copyOf(Objects.requireNonNull(damageImmunities, "damageImmunities"));
        conditionImmunities = Set.copyOf(Objects.requireNonNull(conditionImmunities, "conditionImmunities"));
        abilities = List.copyOf(Objects.requireNonNull(abilities, "abilities"));
        savingThrowBonuses = Map.copyOf(Objects.requireNonNull(savingThrowBonuses, "savingThrowBonuses"));
        long uniqueAbilityIds = abilities.stream().map(AbilityDefinition::id).distinct().count();
        if (uniqueAbilityIds != abilities.size()) {
            throw new IllegalArgumentException("Ability ids must be unique inside an actor definition");
        }
    }

    /** Backward-compatible constructor: no per-ability save bonuses and not a spellcaster. */
    public ActorDefinition(
            String id, String definitionVersion, String rulesetVersion, String name, int armorClass,
            int maxHitPoints, int currentHitPoints, int temporaryHitPoints, int speedFeet, int initiativeModifier,
            int initiativeScore, int constitutionSaveBonus, Set<DamageType> resistances,
            Set<DamageType> vulnerabilities, Set<DamageType> damageImmunities, Set<ConditionType> conditionImmunities,
            List<AbilityDefinition> abilities) {
        this(id, definitionVersion, rulesetVersion, name, armorClass, maxHitPoints, currentHitPoints,
                temporaryHitPoints, speedFeet, initiativeModifier, initiativeScore, constitutionSaveBonus,
                resistances, vulnerabilities, damageImmunities, conditionImmunities, abilities, Map.of(), 0, 1);
    }

    /** Backward-compatible constructor: one attack for each Attack action. */
    public ActorDefinition(
            String id, String definitionVersion, String rulesetVersion, String name, int armorClass,
            int maxHitPoints, int currentHitPoints, int temporaryHitPoints, int speedFeet, int initiativeModifier,
            int initiativeScore, int constitutionSaveBonus, Set<DamageType> resistances,
            Set<DamageType> vulnerabilities, Set<DamageType> damageImmunities, Set<ConditionType> conditionImmunities,
            List<AbilityDefinition> abilities, Map<SaveAbility, Integer> savingThrowBonuses, int spellSaveDc) {
        this(id, definitionVersion, rulesetVersion, name, armorClass, maxHitPoints, currentHitPoints,
                temporaryHitPoints, speedFeet, initiativeModifier, initiativeScore, constitutionSaveBonus,
                resistances, vulnerabilities, damageImmunities, conditionImmunities, abilities,
                savingThrowBonuses, spellSaveDc, 1);
    }

    /** Saving-throw bonus for an ability, or 0 when the actor has no recorded save. */
    public int saveBonus(SaveAbility ability) {
        return savingThrowBonuses.getOrDefault(ability, 0);
    }

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    public AbilityDefinition ability(String abilityId) {
        return abilities.stream().filter(ability -> ability.id().equals(abilityId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ability: " + abilityId));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }

    public static final class Builder {
        private final String id;
        private final String name;
        private String definitionVersion = "1";
        private String rulesetVersion = "srd-5.2.1";
        private int armorClass = 10;
        private int maxHitPoints = 1;
        private Integer currentHitPoints;
        private int temporaryHitPoints;
        private int speedFeet = 30;
        private int initiativeModifier;
        private Integer initiativeScore;
        private int constitutionSaveBonus;
        private Set<DamageType> resistances = Set.of();
        private Set<DamageType> vulnerabilities = Set.of();
        private Set<DamageType> damageImmunities = Set.of();
        private Set<ConditionType> conditionImmunities = Set.of();
        private List<AbilityDefinition> abilities = List.of();
        private Map<SaveAbility, Integer> savingThrowBonuses = Map.of();
        private int spellSaveDc;
        private int attacksPerAction = 1;

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder definitionVersion(String value) { this.definitionVersion = value; return this; }
        public Builder rulesetVersion(String value) { this.rulesetVersion = value; return this; }
        public Builder armorClass(int value) { this.armorClass = value; return this; }
        public Builder maxHitPoints(int value) { this.maxHitPoints = value; return this; }
        public Builder currentHitPoints(int value) { this.currentHitPoints = value; return this; }
        public Builder temporaryHitPoints(int value) { this.temporaryHitPoints = value; return this; }
        public Builder speedFeet(int value) { this.speedFeet = value; return this; }
        public Builder initiativeModifier(int value) { this.initiativeModifier = value; return this; }
        public Builder initiativeScore(int value) { this.initiativeScore = value; return this; }
        public Builder constitutionSaveBonus(int value) { this.constitutionSaveBonus = value; return this; }
        public Builder resistances(Set<DamageType> value) { this.resistances = value; return this; }
        public Builder vulnerabilities(Set<DamageType> value) { this.vulnerabilities = value; return this; }
        public Builder damageImmunities(Set<DamageType> value) { this.damageImmunities = value; return this; }
        public Builder conditionImmunities(Set<ConditionType> value) { this.conditionImmunities = value; return this; }
        public Builder abilities(List<AbilityDefinition> value) { this.abilities = value; return this; }
        public Builder savingThrowBonuses(Map<SaveAbility, Integer> value) { this.savingThrowBonuses = value; return this; }
        public Builder spellSaveDc(int value) { this.spellSaveDc = value; return this; }
        public Builder attacksPerAction(int value) { this.attacksPerAction = value; return this; }

        public ActorDefinition build() {
            int resolvedCurrentHitPoints = currentHitPoints == null ? maxHitPoints : currentHitPoints;
            int resolvedInitiativeScore;
            try {
                resolvedInitiativeScore = initiativeScore == null
                        ? Math.addExact(10, initiativeModifier)
                        : initiativeScore;
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("initiative score exceeds the supported range", exception);
            }
            return new ActorDefinition(id, definitionVersion, rulesetVersion, name, armorClass, maxHitPoints,
                    resolvedCurrentHitPoints, temporaryHitPoints, speedFeet, initiativeModifier,
                    resolvedInitiativeScore, constitutionSaveBonus, resistances, vulnerabilities,
                    damageImmunities, conditionImmunities, abilities, savingThrowBonuses, spellSaveDc,
                    attacksPerAction);
        }
    }
}
