package app.d6d.persistence.combat;

import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AttackRequest;
import app.d6d.domain.combat.AttackResult;
import app.d6d.domain.combat.CombatEvent;
import app.d6d.domain.combat.CombatState;
import app.d6d.domain.combat.ConditionDuration;
import app.d6d.domain.combat.ConditionInstance;
import app.d6d.domain.combat.ConditionType;
import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.DiceExpression;
import app.d6d.domain.combat.HealingDefinition;
import app.d6d.domain.combat.HealingSlotScaling;
import app.d6d.domain.combat.HealingTarget;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.domain.combat.SpellSlotResourceId;
import app.d6d.engine.CombatSession;
import app.d6d.persistence.json.Json;
import app.d6d.rules.model.LocalizedRuleText;
import app.d6d.rules.model.RuleAutomationLevel;
import app.d6d.rules.model.RuleEntity;
import app.d6d.rules.model.RuleKind;
import app.d6d.rules.model.RuleScope;
import app.d6d.rules.model.RuleValue;
import app.d6d.rules.model.RulesetBinding;
import app.d6d.rules.model.RulesetOrigin;
import app.d6d.rules.model.RulesetRevision;
import app.d6d.rules.model.RulesetRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatSessionPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void codecRoundTripsActiveSessionAndPreservesFutureRandomRolls() {
        CombatSession original = populatedActiveSession(424242L);
        CombatState savedState = original.currentState();
        List<CombatEvent> savedAudit = original.auditTrail();
        CombatSessionJsonCodec codec = new CombatSessionJsonCodec();

        Map<String, Object> encoded = codec.encode(original);
        assertEquals(CombatSessionJsonCodec.SCHEMA_VERSION, encoded.get("schemaVersion"));
        CombatSession restored = codec.decode(Json.parseObject(Json.encode(encoded)));

        assertEquals(savedState, restored.currentState());
        assertEquals(2, restored.currentState().combatant("hero").snapshot().attacksPerAction());
        assertTrue(restored.currentState().combatant("hero").snapshot().strengthDexterityD20Disadvantage());
        AbilityDefinition restoredBlade =
                restored.currentState().combatant("hero").snapshot().ability("blade");
        assertEquals(SaveAbility.DEXTERITY, restoredBlade.attackAbility());
        assertFalse(restoredBlade.spellOrCantrip());
        assertTrue(restored.currentState().combatant("hero").snapshot()
                .ability("focus").spellOrCantrip());
        assertEquals(
                HealingDefinition.dice(
                        HealingTarget.SELF_OR_ALLY,
                        new DiceExpression(2, 8, 3),
                        new HealingSlotScaling(1, 2)),
                restored.currentState().combatant("hero").snapshot()
                        .ability("mending-light").healing());
        assertEquals(
                HealingDefinition.fixed(HealingTarget.SELF, 8),
                restored.currentState().combatant("hero").snapshot()
                        .ability("second-wind").healing());
        assertEquals(Set.of("hero"), restored.currentState().partyCombatantIds());
        assertEquals(savedAudit, restored.auditTrail());
        assertFalse(restored.canUndo());

        AttackResult originalNext = original.attack(AttackRequest.digital(
                "goblin", "hero", "blade", D20Mode.ADVANTAGE));
        AttackResult restoredNext = restored.attack(AttackRequest.digital(
                "goblin", "hero", "blade", D20Mode.ADVANTAGE));
        assertEquals(originalNext, restoredNext);
        assertEquals(original.currentState().randomState(), restored.currentState().randomState());
    }

    @Test
    void storeSavesAndLoadsUsingItsDefaultFile() throws Exception {
        CombatSession original = populatedActiveSession(77L);
        CombatSessionStore store = new CombatSessionStore(temporaryDirectory);

        assertFalse(store.exists());
        store.save(original);

        assertTrue(store.exists());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("active-combat.json")));
        CombatSession loaded = store.load();
        assertEquals(original.currentState(), loaded.currentState());
        assertEquals(original.auditTrail(), loaded.auditTrail());
    }

    @Test
    void corruptOrIncompatibleImportNeverOverwritesCurrentSession() throws Exception {
        CombatSession original = populatedActiveSession(101L);
        CombatSessionStore store = new CombatSessionStore(temporaryDirectory, "current", 2);
        store.save(original);
        Path currentFile = temporaryDirectory.resolve("current.json");
        byte[] before = Files.readAllBytes(currentFile);

        Path malformed = temporaryDirectory.resolve("malformed.json");
        Files.writeString(malformed, "{\"schemaVersion\":1", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> store.importFrom(malformed));
        assertArrayEquals(before, Files.readAllBytes(currentFile));

        Path incompatible = temporaryDirectory.resolve("incompatible.json");
        Files.writeString(incompatible,
                "{\"schemaVersion\":999,\"currentState\":{},\"auditTrail\":[]}",
                StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> store.importFrom(incompatible));
        assertArrayEquals(before, Files.readAllBytes(currentFile));
        assertEquals(original.currentState(), store.load().currentState());
    }

    @Test
    void exportThenImportTransfersTheCompleteSession() throws Exception {
        CombatSession original = populatedActiveSession(9090L);
        Path sourceDirectory = temporaryDirectory.resolve("source");
        Path targetDirectory = temporaryDirectory.resolve("target");
        Path exportedFile = temporaryDirectory.resolve("portable/session.json");
        CombatSessionStore source = new CombatSessionStore(sourceDirectory, "combat", 3);
        CombatSessionStore target = new CombatSessionStore(targetDirectory, "combat", 3);

        source.save(original);
        source.exportTo(exportedFile);
        CombatSession imported = target.importFrom(exportedFile);

        assertTrue(target.exists());
        assertEquals(original.currentState(), imported.currentState());
        assertEquals(original.auditTrail(), imported.auditTrail());
        assertEquals(original.currentState(), target.load().currentState());
        assertEquals(original.auditTrail(), target.load().auditTrail());
    }

    @Test
    void modularRulesetBindingRuntimeAndAuditSurviveRoundTrip() {
        CombatSession original = populatedActiveSession(9191L);
        RulesetBinding binding = new RulesetBinding(
                "campaign:rules", "revision:2", "canonical:2", "runtime:2", "1",
                "Campaign rules", false);
        RulesetRuntimeConfig runtime = new RulesetRuntimeConfig(
                "1", 18, false, 9, 3, 10, 1, 3, 8);
        original.changeRuleset(binding, runtime);

        CombatSession restored = new CombatSessionJsonCodec().decode(
                Json.parseObject(Json.encode(new CombatSessionJsonCodec().encode(original))));

        assertEquals(binding, restored.currentState().rulesetBinding());
        assertEquals(runtime, restored.currentState().rulesetRuntime());
        assertEquals(9, restored.currentState().combatant("hero").maximumExhaustion());
        CombatEvent changed = restored.auditTrail().stream()
                .filter(event -> event.type() == app.d6d.domain.combat.EventType.RULESET_CHANGED)
                .findFirst().orElseThrow();
        assertEquals("canonical:2", changed.details().get("afterHash"));
        assertEquals("runtime:2", changed.details().get("afterRuntimeHash"));
    }

    @Test
    void executableRuleSnapshotStateAndFutureRandomnessSurviveRoundTrip() {
        RulesetRevision revision = portableRuleset();
        CombatSession original = populatedActiveSession(818181L);
        original.changeRuleset(revision);
        original.resume();
        original.setGenericRuleActive("portable:value:scene", true);
        original.executeRuleAction("portable:action:strain");
        original.rollRuleRandomizer("portable:randomizer:risk");
        RuleScope heroRules = RuleScope.actor("hero");
        RuleScope locationRules = RuleScope.scene("scene:archive");
        original.setGenericResource(
                heroRules, "portable:resource:stress", new BigDecimal("1"), new BigDecimal("8"));
        original.setGenericRuleValue(locationRules, "portable:value:scene", RuleValue.text("DANGER"));

        CombatSessionJsonCodec codec = new CombatSessionJsonCodec();
        CombatSession restored = codec.decode(Json.parseObject(Json.encode(codec.encode(original))));

        assertEquals(original.currentState().ruleSession(), restored.currentState().ruleSession());
        assertEquals(revision.entities(), restored.currentState().ruleSession().entities());
        assertEquals(new BigDecimal("4"), restored.currentState().ruleSession().state()
                .resources().get("portable:resource:stress").current());
        assertEquals(1, restored.currentState().ruleSession().state()
                .conditionStacks().get("portable:condition:marked"));
        assertEquals(RuleValue.text("DANGER"), restored.genericTypedRuleValue("portable:value:scene"));
        assertTrue(restored.genericRuleActive("portable:value:scene"));
        assertEquals(new BigDecimal("1"), restored.genericRuleState(heroRules).resources()
                .get("portable:resource:stress").current());
        assertEquals(RuleValue.text("DANGER"),
                restored.genericTypedRuleValue(locationRules, "portable:value:scene"));
        assertEquals(4, CombatSessionJsonCodec.SCHEMA_VERSION);
        assertEquals(original.auditTrail(), restored.auditTrail());

        var originalNext = original.rollRuleRandomizer("portable:randomizer:risk");
        var restoredNext = restored.rollRuleRandomizer("portable:randomizer:risk");
        assertEquals(originalNext, restoredNext);
        assertEquals(original.currentState().randomState(), restored.currentState().randomState());
    }

    @Test
    void schemaTwoWithoutEmbeddedRulesRemainsReadableAsLegacySession() {
        CombatSessionJsonCodec codec = new CombatSessionJsonCodec();
        Map<String, Object> encoded = codec.encode(populatedActiveSession(7373L));
        encoded.put("schemaVersion", 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) encoded.get("currentState");
        state.remove("ruleSession");

        CombatState restored = codec.decode(Json.parseObject(Json.encode(encoded))).currentState();

        assertFalse(restored.ruleSession().executable());
        assertEquals("rules-7", restored.rulesetVersion());
    }

    @Test
    void schemaThreeEmbeddedStateWithoutScopesRemainsReadable() {
        CombatSessionJsonCodec codec = new CombatSessionJsonCodec();
        CombatSession original = populatedActiveSession(7474L);
        original.changeRuleset(portableRuleset());
        original.setGenericRuleValue("portable:value:scene", RuleValue.text("DANGER"));
        Map<String, Object> encoded = codec.encode(original);
        encoded.put("schemaVersion", 3);
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) encoded.get("currentState");
        @SuppressWarnings("unchecked")
        Map<String, Object> ruleSession = (Map<String, Object>) state.get("ruleSession");
        ruleSession.remove("scopedStates");

        CombatSession restored = codec.decode(Json.parseObject(Json.encode(encoded)));

        assertTrue(restored.currentState().ruleSession().scopedStates().isEmpty());
        assertEquals(RuleValue.text("DANGER"),
                restored.genericTypedRuleValue("portable:value:scene"));
    }

    @Test
    void schemaOneSessionMigratesConservativelyToLegacySrdBinding() {
        CombatSessionJsonCodec codec = new CombatSessionJsonCodec();
        Map<String, Object> encoded = codec.encode(populatedActiveSession(9292L));
        encoded.put("schemaVersion", 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) encoded.get("currentState");
        state.remove("rulesetBinding");
        state.remove("rulesetRuntime");

        CombatState restored = codec.decode(Json.parseObject(Json.encode(encoded))).currentState();

        assertTrue(restored.rulesetBinding().legacy());
        assertEquals(RulesetRuntimeConfig.standardSrd521(), restored.rulesetRuntime());
        assertEquals(6, restored.combatant("hero").maximumExhaustion());
    }

    @Test
    void legacySessionWithoutAttacksPerActionDefaultsSnapshotsToOne() {
        CombatSessionJsonCodec codec = new CombatSessionJsonCodec();
        Map<String, Object> encoded = codec.encode(populatedActiveSession(505L));
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) encoded.get("currentState");
        @SuppressWarnings("unchecked")
        Map<String, Object> combatants = (Map<String, Object>) state.get("combatants");
        for (Object rawCombatant : combatants.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> combatant = (Map<String, Object>) rawCombatant;
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshot = (Map<String, Object>) combatant.get("snapshot");
            snapshot.remove("attacksPerAction");
            snapshot.remove("strengthDexterityD20Disadvantage");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> abilities = (List<Map<String, Object>>) snapshot.get("abilities");
            for (Map<String, Object> ability : abilities) {
                ability.remove("attackAbility");
                ability.remove("spellOrCantrip");
                ability.remove("healing");
            }
        }

        CombatSession restored = codec.decode(Json.parseObject(Json.encode(encoded)));

        assertEquals(1, restored.currentState().combatant("hero").snapshot().attacksPerAction());
        assertEquals(1, restored.currentState().combatant("goblin").snapshot().attacksPerAction());
        assertFalse(restored.currentState().combatant("hero").snapshot().strengthDexterityD20Disadvantage());
        assertFalse(restored.currentState().combatant("goblin").snapshot().strengthDexterityD20Disadvantage());
        AbilityDefinition legacyBlade =
                restored.currentState().combatant("hero").snapshot().ability("blade");
        assertEquals(null, legacyBlade.attackAbility());
        assertFalse(legacyBlade.spellOrCantrip());
        assertFalse(restored.currentState().combatant("hero").snapshot()
                .ability("focus").spellOrCantrip());
        assertEquals(null, restored.currentState().combatant("hero").snapshot()
                        .ability("mending-light").healing());
    }

    @Test
    void legacyHealingWithoutSlotScalingKeepsItsBaseFormula() {
        CombatSessionJsonCodec codec = new CombatSessionJsonCodec();
        Map<String, Object> encoded = codec.encode(populatedActiveSession(606L));
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) encoded.get("currentState");
        @SuppressWarnings("unchecked")
        Map<String, Object> combatants = (Map<String, Object>) state.get("combatants");
        @SuppressWarnings("unchecked")
        Map<String, Object> hero = (Map<String, Object>) combatants.get("hero");
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) hero.get("snapshot");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> abilities = (List<Map<String, Object>>) snapshot.get("abilities");
        Map<String, Object> encodedAbility = abilities.stream()
                .filter(ability -> "mending-light".equals(ability.get("id")))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> encodedHealing = (Map<String, Object>) encodedAbility.get("healing");
        encodedHealing.remove("slotScaling");

        HealingDefinition restored = codec.decode(Json.parseObject(Json.encode(encoded)))
                .currentState().combatant("hero").snapshot().ability("mending-light").healing();

        assertEquals(
                HealingDefinition.dice(
                        HealingTarget.SELF_OR_ALLY, new DiceExpression(2, 8, 3)),
                restored);
    }

    private static CombatSession populatedActiveSession(long seed) {
        AbilityDefinition blade = AbilityDefinition.builder("blade", "Runic blade")
                .version("3")
                .source("test-pack")
                .rulesetVersion("rules-7")
                .activationCost(ActivationCost.ACTION)
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackAbility(SaveAbility.DEXTERITY)
                .attackBonus(8)
                .rangeFeet(10)
                .maxTargets(1)
                .damage(List.of(
                        DamageFormula.dice(DamageType.SLASHING, 2, 6, 3),
                        DamageFormula.fixed(DamageType.FORCE, 2)))
                .rulesText("A versioned test ability.")
                .build();
        AbilityDefinition focus = AbilityDefinition.builder("focus", "Arcane focus")
                .activationCost(ActivationCost.NONE)
                .resolutionMethod(ResolutionMethod.AUTOMATIC)
                .spellOrCantrip(true)
                .build();
        AbilityDefinition mendingLight = AbilityDefinition.builder("mending-light", "Mending light")
                .activationCost(ActivationCost.BONUS_ACTION)
                .rangeFeet(30)
                .spellOrCantrip(true)
                .resource(SpellSlotResourceId.standard(1).id(), 1)
                .healing(HealingDefinition.dice(
                        HealingTarget.SELF_OR_ALLY,
                        new DiceExpression(2, 8, 3),
                        new HealingSlotScaling(1, 2)))
                .build();
        // Una cura a importo fisso e senza slot: l'altra forma che il codec deve
        // riportare identica, con "dice" nullo nel documento salvato.
        AbilityDefinition secondWind = AbilityDefinition.builder("second-wind", "Second wind")
                .activationCost(ActivationCost.BONUS_ACTION)
                .healing(HealingDefinition.fixed(HealingTarget.SELF, 8))
                .build();
        ActorDefinition hero = ActorDefinition.builder("hero-definition", "Hero")
                .definitionVersion("5")
                .rulesetVersion("rules-7")
                .armorClass(17)
                .maxHitPoints(50)
                .currentHitPoints(43)
                .temporaryHitPoints(4)
                .speedFeet(35)
                .initiativeModifier(4)
                .initiativeScore(14)
                .constitutionSaveBonus(6)
                .attacksPerAction(2)
                .strengthDexterityD20Disadvantage(true)
                .resistances(Set.of(DamageType.FIRE, DamageType.COLD))
                .vulnerabilities(Set.of(DamageType.PSYCHIC))
                .damageImmunities(Set.of(DamageType.POISON))
                .conditionImmunities(Set.of(ConditionType.POISONED))
                .abilities(List.of(blade, focus, mendingLight, secondWind))
                .build();
        ActorDefinition goblin = ActorDefinition.builder("goblin-definition", "Goblin")
                .armorClass(13)
                .maxHitPoints(45)
                .initiativeModifier(2)
                .constitutionSaveBonus(1)
                .abilities(List.of(blade))
                .build();

        CombatSession session = CombatSession.create("persistent-encounter", seed, "rules-7", "pack-11");
        session.addCombatant("hero", hero);
        session.addCombatant("goblin", goblin);
        session.setInitiative("hero", 22);
        session.setInitiative("goblin", 12);
        session.setPartyCombatants(List.of("hero"));
        session.markReady();
        session.start();
        session.attack(AttackRequest.digital("hero", "goblin", "blade", D20Mode.NORMAL));
        session.beginConcentration("hero", "focus");
        session.applyCondition("goblin", new ConditionInstance(
                "focus-restraint",
                ConditionType.RESTRAINED,
                "hero",
                "focus",
                session.currentState().round(),
                ConditionDuration.concentration(),
                "hero",
                "Persists while the hero concentrates."));
        session.spendMovement("hero", 15);
        session.endTurn();
        return session;
    }

    private static RulesetRevision portableRuleset() {
        List<RuleEntity> entities = List.of(
                portableEntity("portable:stat:focus", RuleKind.STAT,
                        Map.of("defaultFormula", "3")),
                portableEntity("portable:turn", RuleKind.ACTION_ECONOMY,
                        Map.of("budgets", "tempo=2")),
                portableEntity("portable:resource:stress", RuleKind.RESOURCE, Map.of(
                        "maximumFormula", "5", "initialFormula", "5", "recoveryEvent", "REST")),
                portableEntity("portable:value:scene", RuleKind.VALUE, Map.of(
                        "valueType", "TEXT", "defaultValue", "SAFE",
                        "allowedValues", "SAFE,DANGER", "mutable", "true")),
                portableEntity("portable:condition:marked", RuleKind.CONDITION, Map.of(
                        "conditionId", "portable:marked", "maximumStacks", "4")),
                portableEntity("portable:damage:aether", RuleKind.DAMAGE_TYPE,
                        Map.of("damageTypeId", "portable:aether")),
                portableEntity("portable:effect:mark", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "portable:condition:marked",
                        "targetRef", "portable:condition:marked", "application", "ADD_CONDITION",
                        "operation", "ADD", "valueFormula", "1")),
                portableEntity("portable:effect:danger", RuleKind.MODIFIER, Map.of(
                        "ownerRef", "portable:value:scene",
                        "targetRef", "portable:value:scene", "application", "SET_VALUE",
                        "valueType", "TEXT", "valueLiteral", "DANGER")),
                portableEntity("portable:action:strain", RuleKind.ACTION, Map.of(
                        "costs", "turn:tempo=1;resource:portable:resource:stress=1",
                        "effectRefs", "portable:effect:mark,portable:effect:danger")),
                portableEntity("portable:randomizer:risk", RuleKind.RANDOMIZER, Map.of(
                        "mode", "DICE_POOL", "countFormula", "${portable:stat:focus}",
                        "sidesFormula", "6", "keep", "SUCCESSES", "successThresholdFormula", "5")));
        return RulesetRevision.create(
                "portable:test", "portable:revision:1", "1.0.0", "Portable test",
                "Portable executable rules", RulesetOrigin.HOMEBREW, "",
                RulesetRuntimeConfig.standardSrd521(), entities, "2026-08-30T00:00:00Z");
    }

    private static RuleEntity portableEntity(String id, RuleKind kind, Map<String, String> attributes) {
        return new RuleEntity(
                id, kind, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.bilingual(id, id), LocalizedRuleText.bilingual("Test", "Test"), "",
                true, RuleAutomationLevel.FULL, attributes, List.of("portable"), "Test", "", 0);
    }
}
