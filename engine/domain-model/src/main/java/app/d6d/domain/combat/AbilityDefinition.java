package app.d6d.domain.combat;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, versioned ability data interpreted by the engine.
 *
 * <p>An ability with {@code areaRadiusFeet > 0} is an area effect (a sphere of that
 * radius centred on a chosen point). When it also carries a {@code saveAbility} the
 * engine resolves it as a saving throw: each creature in the area rolls that save
 * against the caster's spell save DC and, if {@code halfOnSave}, takes half damage
 * on a success instead of none.</p>
 *
 * <p>A {@code passive} ability is a standing trait — weapon mastery, spellcasting,
 * a feat — that is never activated on a turn. The engine refuses to resolve it and
 * the interface lists it apart from the abilities the player can spend a turn on.</p>
 */
public record AbilityDefinition(
        String id,
        String version,
        String source,
        String rulesetVersion,
        String name,
        ActivationCost activationCost,
        ResolutionMethod resolutionMethod,
        int attackBonus,
        int rangeFeet,
        int maxTargets,
        List<DamageFormula> damage,
        AutomationStatus automationStatus,
        String rulesText,
        int areaRadiusFeet,
        SaveAbility saveAbility,
        boolean halfOnSave,
        boolean passive,
        SaveAbility attackAbility,
        boolean spellOrCantrip,
        AbilityEffect effect,
        String resourceId,
        int resourceCost) {

    public AbilityDefinition {
        id = requireText(id, "id");
        version = requireText(version, "version");
        source = requireText(source, "source");
        rulesetVersion = requireText(rulesetVersion, "rulesetVersion");
        name = requireText(name, "name");
        Objects.requireNonNull(activationCost, "activationCost");
        Objects.requireNonNull(resolutionMethod, "resolutionMethod");
        if (rangeFeet < 0) {
            throw new IllegalArgumentException("rangeFeet cannot be negative");
        }
        if (maxTargets <= 0) {
            throw new IllegalArgumentException("maxTargets must be positive");
        }
        if (areaRadiusFeet < 0) {
            throw new IllegalArgumentException("areaRadiusFeet cannot be negative");
        }
        damage = List.copyOf(Objects.requireNonNull(damage, "damage"));
        Objects.requireNonNull(automationStatus, "automationStatus");
        effect = effect == null ? AbilityEffect.NONE : effect;
        resourceId = resourceId == null ? "" : resourceId;
        if (resourceCost < 0) {
            throw new IllegalArgumentException("resourceCost cannot be negative");
        }
        if (resourceCost > 0 && resourceId.isBlank()) {
            throw new IllegalArgumentException("A resource cost needs a resource id");
        }
        rulesText = rulesText == null ? "" : rulesText;
        if (resolutionMethod == ResolutionMethod.ATTACK_ROLL && damage.isEmpty()) {
            throw new IllegalArgumentException("An attack needs at least one damage component");
        }
    }

    /** Backward-compatible constructor: a plain single-target ability, no area or save. */
    public AbilityDefinition(
            String id, String version, String source, String rulesetVersion, String name,
            ActivationCost activationCost, ResolutionMethod resolutionMethod, int attackBonus, int rangeFeet,
            int maxTargets, List<DamageFormula> damage, AutomationStatus automationStatus, String rulesText) {
        this(id, version, source, rulesetVersion, name, activationCost, resolutionMethod, attackBonus, rangeFeet,
                maxTargets, damage, automationStatus, rulesText, 0, null, false, false, null, false,
                AbilityEffect.NONE, "", 0);
    }

    /** Backward-compatible constructor: an ability the player activates, never a passive trait. */
    public AbilityDefinition(
            String id, String version, String source, String rulesetVersion, String name,
            ActivationCost activationCost, ResolutionMethod resolutionMethod, int attackBonus, int rangeFeet,
            int maxTargets, List<DamageFormula> damage, AutomationStatus automationStatus, String rulesText,
            int areaRadiusFeet, SaveAbility saveAbility, boolean halfOnSave) {
        this(id, version, source, rulesetVersion, name, activationCost, resolutionMethod, attackBonus, rangeFeet,
                maxTargets, damage, automationStatus, rulesText, areaRadiusFeet, saveAbility, halfOnSave, false,
                null, false, AbilityEffect.NONE, "", 0);
    }

    /**
     * Backward-compatible constructor: no recorded attack ability and no spell classification.
     */
    public AbilityDefinition(
            String id, String version, String source, String rulesetVersion, String name,
            ActivationCost activationCost, ResolutionMethod resolutionMethod, int attackBonus, int rangeFeet,
            int maxTargets, List<DamageFormula> damage, AutomationStatus automationStatus, String rulesText,
            int areaRadiusFeet, SaveAbility saveAbility, boolean halfOnSave, boolean passive) {
        this(id, version, source, rulesetVersion, name, activationCost, resolutionMethod, attackBonus, rangeFeet,
                maxTargets, damage, automationStatus, rulesText, areaRadiusFeet, saveAbility, halfOnSave, passive,
                null, false, AbilityEffect.NONE, "", 0);
    }

    /** True when the ability affects a spherical area rather than a single target. */
    public boolean isArea() {
        return areaRadiusFeet > 0;
    }

    /** True when the ability is resolved with a saving throw against the caster's DC. */
    public boolean hasSavingThrow() {
        return saveAbility != null;
    }

    public static Builder builder(String id, String name) {
        return new Builder(id, name);
    }

    public static AbilityDefinition attack(
            String id, String name, ActivationCost cost, int attackBonus, DamageFormula... damage) {
        return builder(id, name)
                .activationCost(cost)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackBonus(attackBonus)
                .damage(List.of(damage))
                .build();
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
        private String version = "1";
        private String source = "user";
        private String rulesetVersion = "srd-5.2.1";
        private ActivationCost activationCost = ActivationCost.ACTION;
        private ResolutionMethod resolutionMethod = ResolutionMethod.AUTOMATIC;
        private int attackBonus;
        private int rangeFeet = 5;
        private int maxTargets = 1;
        private List<DamageFormula> damage = List.of();
        private AutomationStatus automationStatus = AutomationStatus.AUTOMATED;
        private String rulesText = "";
        private int areaRadiusFeet;
        private SaveAbility saveAbility;
        private boolean halfOnSave;
        private boolean passive;
        private SaveAbility attackAbility;
        private boolean spellOrCantrip;
        private AbilityEffect effect = AbilityEffect.NONE;
        private String resourceId = "";
        private int resourceCost;

        private Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder version(String value) { this.version = value; return this; }
        public Builder source(String value) { this.source = value; return this; }
        public Builder rulesetVersion(String value) { this.rulesetVersion = value; return this; }
        public Builder activationCost(ActivationCost value) { this.activationCost = value; return this; }
        public Builder resolutionMethod(ResolutionMethod value) { this.resolutionMethod = value; return this; }
        public Builder attackBonus(int value) { this.attackBonus = value; return this; }
        public Builder rangeFeet(int value) { this.rangeFeet = value; return this; }
        public Builder maxTargets(int value) { this.maxTargets = value; return this; }
        public Builder damage(List<DamageFormula> value) { this.damage = value; return this; }
        public Builder automationStatus(AutomationStatus value) { this.automationStatus = value; return this; }
        public Builder rulesText(String value) { this.rulesText = value; return this; }
        public Builder areaRadiusFeet(int value) { this.areaRadiusFeet = value; return this; }
        public Builder saveAbility(SaveAbility value) { this.saveAbility = value; return this; }
        public Builder halfOnSave(boolean value) { this.halfOnSave = value; return this; }
        public Builder passive(boolean value) { this.passive = value; return this; }
        public Builder attackAbility(SaveAbility value) { this.attackAbility = value; return this; }
        public Builder spellOrCantrip(boolean value) { this.spellOrCantrip = value; return this; }
        public Builder effect(AbilityEffect value) { this.effect = value; return this; }
        public Builder resource(String id, int cost) {
            this.resourceId = id;
            this.resourceCost = cost;
            return this;
        }

        public AbilityDefinition build() {
            return new AbilityDefinition(id, version, source, rulesetVersion, name, activationCost,
                    resolutionMethod, attackBonus, rangeFeet, maxTargets, damage, automationStatus, rulesText,
                    areaRadiusFeet, saveAbility, halfOnSave, passive, attackAbility, spellOrCantrip,
                    effect, resourceId, resourceCost);
        }
    }
}
