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
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.engine.CombatSession;
import app.d6d.persistence.json.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
                .abilities(List.of(blade, focus))
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
}
