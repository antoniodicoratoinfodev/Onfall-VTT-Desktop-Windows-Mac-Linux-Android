package app.d6d.domain.combat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable encounter-local copy; future edits to the library actor cannot affect it. */
public record CombatantSnapshot(
        String instanceId,
        String definitionId,
        String definitionVersion,
        String rulesetVersion,
        String name,
        int armorClass,
        int maxHitPoints,
        int initialHitPoints,
        int initialTemporaryHitPoints,
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
        int attacksPerAction,
        boolean strengthDexterityD20Disadvantage,
        List<CombatResourceState> resources) {

    public CombatantSnapshot {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId cannot be blank");
        }
        if (definitionId == null || definitionId.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Definition id and name are required");
        }
        if (definitionVersion == null || definitionVersion.isBlank()
                || rulesetVersion == null || rulesetVersion.isBlank()) {
            throw new IllegalArgumentException("Definition and ruleset versions are required");
        }
        if (armorClass < 0 || maxHitPoints <= 0 || initialHitPoints < 0 || initialHitPoints > maxHitPoints
                || initialTemporaryHitPoints < 0 || speedFeet < 0) {
            throw new IllegalArgumentException("Invalid snapshot statistics");
        }
        resistances = Set.copyOf(Objects.requireNonNull(resistances, "resistances"));
        vulnerabilities = Set.copyOf(Objects.requireNonNull(vulnerabilities, "vulnerabilities"));
        damageImmunities = Set.copyOf(Objects.requireNonNull(damageImmunities, "damageImmunities"));
        conditionImmunities = Set.copyOf(Objects.requireNonNull(conditionImmunities, "conditionImmunities"));
        abilities = List.copyOf(Objects.requireNonNull(abilities, "abilities"));
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        savingThrowBonuses = Map.copyOf(Objects.requireNonNull(savingThrowBonuses, "savingThrowBonuses"));
        if (spellSaveDc < 0) {
            throw new IllegalArgumentException("spellSaveDc cannot be negative");
        }
        if (attacksPerAction < 1) {
            throw new IllegalArgumentException("attacksPerAction must be at least 1");
        }
        if (resources.stream().map(CombatResourceState::id).distinct().count() != resources.size()) {
            throw new IllegalArgumentException("Resource ids must be unique inside a combatant snapshot");
        }
    }

    /** Backward-compatible full constructor: no encounter resources. */
    public CombatantSnapshot(
            String instanceId, String definitionId, String definitionVersion, String rulesetVersion, String name,
            int armorClass, int maxHitPoints, int initialHitPoints, int initialTemporaryHitPoints, int speedFeet,
            int initiativeModifier, int initiativeScore, int constitutionSaveBonus, Set<DamageType> resistances,
            Set<DamageType> vulnerabilities, Set<DamageType> damageImmunities, Set<ConditionType> conditionImmunities,
            List<AbilityDefinition> abilities, Map<SaveAbility, Integer> savingThrowBonuses, int spellSaveDc,
            int attacksPerAction, boolean strengthDexterityD20Disadvantage) {
        this(instanceId, definitionId, definitionVersion, rulesetVersion, name, armorClass, maxHitPoints,
                initialHitPoints, initialTemporaryHitPoints, speedFeet, initiativeModifier, initiativeScore,
                constitutionSaveBonus, resistances, vulnerabilities, damageImmunities, conditionImmunities,
                abilities, savingThrowBonuses, spellSaveDc, attacksPerAction, strengthDexterityD20Disadvantage,
                List.of());
    }

    /** Backward-compatible constructor: no per-ability save bonuses and not a spellcaster. */
    public CombatantSnapshot(
            String instanceId, String definitionId, String definitionVersion, String rulesetVersion, String name,
            int armorClass, int maxHitPoints, int initialHitPoints, int initialTemporaryHitPoints, int speedFeet,
            int initiativeModifier, int initiativeScore, int constitutionSaveBonus, Set<DamageType> resistances,
            Set<DamageType> vulnerabilities, Set<DamageType> damageImmunities, Set<ConditionType> conditionImmunities,
            List<AbilityDefinition> abilities) {
        this(instanceId, definitionId, definitionVersion, rulesetVersion, name, armorClass, maxHitPoints,
                initialHitPoints, initialTemporaryHitPoints, speedFeet, initiativeModifier, initiativeScore,
                constitutionSaveBonus, resistances, vulnerabilities, damageImmunities, conditionImmunities,
                abilities, Map.of(), 0, 1, false, List.of());
    }

    /** Backward-compatible constructor: one attack for each Attack action. */
    public CombatantSnapshot(
            String instanceId, String definitionId, String definitionVersion, String rulesetVersion, String name,
            int armorClass, int maxHitPoints, int initialHitPoints, int initialTemporaryHitPoints, int speedFeet,
            int initiativeModifier, int initiativeScore, int constitutionSaveBonus, Set<DamageType> resistances,
            Set<DamageType> vulnerabilities, Set<DamageType> damageImmunities, Set<ConditionType> conditionImmunities,
            List<AbilityDefinition> abilities, Map<SaveAbility, Integer> savingThrowBonuses, int spellSaveDc) {
        this(instanceId, definitionId, definitionVersion, rulesetVersion, name, armorClass, maxHitPoints,
                initialHitPoints, initialTemporaryHitPoints, speedFeet, initiativeModifier, initiativeScore,
                constitutionSaveBonus, resistances, vulnerabilities, damageImmunities, conditionImmunities,
                abilities, savingThrowBonuses, spellSaveDc, 1, false, List.of());
    }

    /** Backward-compatible constructor: no imposed disadvantage on Strength/Dexterity d20 tests. */
    public CombatantSnapshot(
            String instanceId, String definitionId, String definitionVersion, String rulesetVersion, String name,
            int armorClass, int maxHitPoints, int initialHitPoints, int initialTemporaryHitPoints, int speedFeet,
            int initiativeModifier, int initiativeScore, int constitutionSaveBonus, Set<DamageType> resistances,
            Set<DamageType> vulnerabilities, Set<DamageType> damageImmunities, Set<ConditionType> conditionImmunities,
            List<AbilityDefinition> abilities, Map<SaveAbility, Integer> savingThrowBonuses, int spellSaveDc,
            int attacksPerAction) {
        this(instanceId, definitionId, definitionVersion, rulesetVersion, name, armorClass, maxHitPoints,
                initialHitPoints, initialTemporaryHitPoints, speedFeet, initiativeModifier, initiativeScore,
                constitutionSaveBonus, resistances, vulnerabilities, damageImmunities, conditionImmunities,
                abilities, savingThrowBonuses, spellSaveDc, attacksPerAction, false, List.of());
    }

    public static CombatantSnapshot from(String instanceId, ActorDefinition actor) {
        Objects.requireNonNull(actor, "actor");
        return new CombatantSnapshot(instanceId, actor.id(), actor.definitionVersion(), actor.rulesetVersion(),
                actor.name(), actor.armorClass(), actor.maxHitPoints(), actor.currentHitPoints(),
                actor.temporaryHitPoints(), actor.speedFeet(), actor.initiativeModifier(), actor.initiativeScore(),
                actor.constitutionSaveBonus(), actor.resistances(), actor.vulnerabilities(), actor.damageImmunities(),
                actor.conditionImmunities(), actor.abilities(), actor.savingThrowBonuses(), actor.spellSaveDc(),
                actor.attacksPerAction(), actor.strengthDexterityD20Disadvantage(), actor.resources());
    }

    /**
     * Crea la fotografia temporanea di Forma Selvatica.
     *
     * <p>I PF e le risorse restano quelli del druido; CA, movimento, attacchi e
     * statistiche fisiche arrivano dalla bestia. I privilegi di classe e le altre
     * forme conosciute restano nello snapshot, mentre armi e incantesimi non sono
     * utilizzabili finche' dura la trasformazione.</p>
     */
    public static CombatantSnapshot wildShape(
            String instanceId,
            ActorDefinition druid,
            ActorDefinition beast) {
        Objects.requireNonNull(druid, "druid");
        Objects.requireNonNull(beast, "beast");
        List<AbilityDefinition> retained = druid.abilities().stream()
                .filter(ability -> ability.passive()
                        || ability.id().startsWith("srd521-it:feature:")
                        || ability.id().startsWith("srd521-it:feat:")
                        || ability.id().startsWith("srd521-it:beast:"))
                .filter(ability -> !ability.spellOrCantrip())
                .toList();
        Map<String, AbilityDefinition> abilityMap = java.util.stream.Stream
                .concat(beast.abilities().stream(), retained.stream())
                .collect(java.util.stream.Collectors.toMap(
                        AbilityDefinition::id,
                        ability -> ability,
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new));
        List<AbilityDefinition> abilities = List.copyOf(abilityMap.values());
        Map<SaveAbility, Integer> saves = new java.util.EnumMap<>(SaveAbility.class);
        druid.savingThrowBonuses().forEach((ability, bonus) -> saves.merge(ability, bonus, Math::max));
        beast.savingThrowBonuses().forEach((ability, bonus) -> saves.merge(ability, bonus, Math::max));
        Set<DamageType> resistances = new java.util.HashSet<>(druid.resistances());
        resistances.addAll(beast.resistances());
        Set<DamageType> immunities = new java.util.HashSet<>(druid.damageImmunities());
        immunities.addAll(beast.damageImmunities());
        Set<ConditionType> conditionImmunities = new java.util.HashSet<>(druid.conditionImmunities());
        conditionImmunities.addAll(beast.conditionImmunities());
        return new CombatantSnapshot(
                instanceId,
                druid.id(),
                druid.definitionVersion() + "+forma-selvatica." + substringAfterLast(beast.id(), ':'),
                druid.rulesetVersion(),
                druid.name() + " · " + beast.name(),
                beast.armorClass(),
                druid.maxHitPoints(),
                druid.currentHitPoints(),
                druid.temporaryHitPoints(),
                beast.speedFeet(),
                beast.initiativeModifier(),
                beast.initiativeScore(),
                Math.max(druid.constitutionSaveBonus(), beast.constitutionSaveBonus()),
                resistances,
                beast.vulnerabilities(),
                immunities,
                conditionImmunities,
                abilities,
                saves,
                druid.spellSaveDc(),
                beast.attacksPerAction(),
                beast.strengthDexterityD20Disadvantage(),
                druid.resources());
    }

    /**
     * The same combatant under the labels of another language.
     *
     * <p>Names only. Statistics, resources, resistances and the whole shape of
     * the fight are untouched, and so is {@code definitionVersion}: this is not
     * a table correction and must not be mistaken for one when the sheet and
     * the encounter are compared.</p>
     *
     * <p>{@code abilityNames} and {@code abilityRulesTexts} are read by ability
     * id and may cover any subset: an ability the caller has no translation for
     * keeps the wording it already had, which is what should happen to anything
     * the table wrote itself.</p>
     */
    public CombatantSnapshot relabelled(
            String newName,
            Map<String, String> abilityNames,
            Map<String, String> abilityRulesTexts) {
        Objects.requireNonNull(abilityNames, "abilityNames");
        Objects.requireNonNull(abilityRulesTexts, "abilityRulesTexts");
        String label = newName == null || newName.isBlank() ? name : newName;
        List<AbilityDefinition> renamed = abilities.stream()
                .map(ability -> ability.withLabels(
                        abilityNames.get(ability.id()),
                        abilityRulesTexts.get(ability.id())))
                .toList();
        if (label.equals(name) && renamed.equals(abilities)) {
            return this;
        }
        return new CombatantSnapshot(
                instanceId,
                definitionId,
                definitionVersion,
                rulesetVersion,
                label,
                armorClass,
                maxHitPoints,
                initialHitPoints,
                initialTemporaryHitPoints,
                speedFeet,
                initiativeModifier,
                initiativeScore,
                constitutionSaveBonus,
                resistances,
                vulnerabilities,
                damageImmunities,
                conditionImmunities,
                renamed,
                savingThrowBonuses,
                spellSaveDc,
                attacksPerAction,
                strengthDexterityD20Disadvantage,
                resources);
    }

    private static String substringAfterLast(String value, char separator) {
        int index = value.lastIndexOf(separator);
        return index < 0 ? value : value.substring(index + 1);
    }

    public AbilityDefinition ability(String abilityId) {
        return abilities.stream().filter(ability -> ability.id().equals(abilityId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ability: " + abilityId));
    }

    /** Saving-throw bonus for an ability, or 0 when the snapshot has no recorded save. */
    public int saveBonus(SaveAbility ability) {
        return savingThrowBonuses.getOrDefault(ability, 0);
    }

    /** Marca le revisioni nate da una correzione decisa al tavolo. */
    public static final String TABLE_REVISION_MARKER = "+tavolo.";

    /**
     * Vero quando questa fotografia e' stata corretta durante il gioco e quindi non
     * corrisponde piu' alla definizione da cui era stata tratta.
     */
    public boolean tableEdited() {
        return definitionVersion.contains(TABLE_REVISION_MARKER);
    }

    /**
     * Copia con le statistiche corrette al tavolo.
     *
     * <p>Fa due cose che un chiamante dimenticherebbe. Primo, riporta i punti ferita
     * iniziali entro il nuovo massimo: abbassare il tetto non deve rendere invalida
     * la fotografia. Secondo, incrementa la revisione della definizione, perche' il
     * documento vuole che una modifica produca una nuova revisione anziche' cambiare
     * in silenzio un contenuto gia' versionato: cosi' una fotografia corretta al
     * tavolo si distingue sempre dalla definizione di catalogo da cui proviene.</p>
     */
    public CombatantSnapshot withStats(
            String newName,
            int newArmorClass,
            int newMaxHitPoints,
            int newSpeedFeet,
            int newInitiativeModifier,
            int newInitiativeScore,
            int newConstitutionSaveBonus) {
        return new CombatantSnapshot(
                instanceId,
                definitionId,
                nextTableRevision(definitionVersion),
                rulesetVersion,
                newName == null || newName.isBlank() ? name : newName,
                newArmorClass,
                newMaxHitPoints,
                Math.min(initialHitPoints, newMaxHitPoints),
                initialTemporaryHitPoints,
                newSpeedFeet,
                newInitiativeModifier,
                newInitiativeScore,
                newConstitutionSaveBonus,
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
    }

    /**
     * Revisione successiva di una definizione corretta al tavolo.
     *
     * <p>"1.0.0" diventa "1.0.0+tavolo.1", poi "+tavolo.2" e cosi' via. Un suffisso
     * illeggibile non fa fallire la correzione: si riparte da uno.</p>
     */
    public static String nextTableRevision(String version) {
        int marker = version.indexOf(TABLE_REVISION_MARKER);
        if (marker < 0) {
            return version + TABLE_REVISION_MARKER + 1;
        }
        String base = version.substring(0, marker);
        String counter = version.substring(marker + TABLE_REVISION_MARKER.length());
        try {
            return base + TABLE_REVISION_MARKER + (Integer.parseInt(counter) + 1);
        } catch (NumberFormatException notANumber) {
            return base + TABLE_REVISION_MARKER + 1;
        }
    }
}
