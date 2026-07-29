package app.d6d.persistence.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.d6d.domain.campaign.ActorKind;
import app.d6d.domain.campaign.ActorTemplate;
import app.d6d.domain.catalog.ActorCatalogEntry;
import app.d6d.domain.combat.AbilityDefinition;
import app.d6d.domain.combat.ActivationCost;
import app.d6d.domain.combat.ActorDefinition;
import app.d6d.domain.combat.AutomationStatus;
import app.d6d.domain.combat.ConditionType;
import app.d6d.domain.combat.DamageFormula;
import app.d6d.domain.combat.DamageType;
import app.d6d.domain.combat.ResolutionMethod;
import app.d6d.domain.combat.SaveAbility;
import app.d6d.persistence.json.Json;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActorCatalogJsonCodecTest {
    @Test
    void roundTripsFractionalCrMultipleDamageComponentsAndEveryDefense() {
        AbilityDefinition bite = new AbilityDefinition(
                "wolf-bite",
                "3",
                "user:test-pack",
                "srd-5.2.1",
                "Bite",
                ActivationCost.ACTION,
                ResolutionMethod.ATTACK_ROLL,
                5,
                10,
                2,
                List.of(
                        DamageFormula.dice(DamageType.PIERCING, 2, 6, 3),
                        DamageFormula.fixed(DamageType.COLD, 4)),
                AutomationStatus.ASSISTED,
                "On a hit, the target may fall prone.");
        AbilityDefinition howl = new AbilityDefinition(
                "wolf-howl",
                "1",
                "user:test-pack",
                "srd-5.2.1",
                "Howl",
                ActivationCost.BONUS_ACTION,
                ResolutionMethod.MANUAL,
                0,
                60,
                4,
                List.of(),
                AutomationStatus.MANUAL_REQUIRED,
                "");
        ActorDefinition definition = new ActorDefinition(
                "winter-wolf",
                "7",
                "srd-5.2.1",
                "Winter Wolf",
                15,
                75,
                41,
                6,
                50,
                3,
                13,
                4,
                Set.of(DamageType.COLD, DamageType.BLUDGEONING),
                Set.of(DamageType.FIRE),
                Set.of(DamageType.POISON, DamageType.PSYCHIC),
                Set.of(ConditionType.CHARMED, ConditionType.POISONED),
                List.of(bite, howl));
        ActorTemplate template = new ActorTemplate(
                "winter-wolf",
                "Winter Wolf",
                ActorKind.CREATURE,
                0,
                Map.of("habitat", "tundra", "note", "custom"));
        ActorCatalogEntry creature = ActorCatalogEntry.creature(
                template,
                definition,
                new BigDecimal("0.125"),
                25);

        Map<String, Object> encoded = ActorCatalogJsonCodec.encode(List.of(creature));
        String json = Json.encode(encoded);
        List<ActorCatalogEntry> decoded = ActorCatalogJsonCodec.decode(Json.parseObject(json));

        assertEquals(1, encoded.get("schemaVersion"));
        assertTrue(json.contains("\"challengeRating\":0.125"));
        assertEquals(List.of(creature), decoded);
    }

    @Test
    void roundTripsEmptyCatalogAndEmptyOptionalCollections() {
        assertEquals(List.of(), ActorCatalogJsonCodec.decode(
                ActorCatalogJsonCodec.encode(List.of())));

        ActorTemplate template = new ActorTemplate(
                "aria",
                "Aria",
                ActorKind.PLAYER_CHARACTER,
                1,
                Map.of());
        ActorDefinition definition = new ActorDefinition(
                "aria",
                "1",
                "srd-5.2.1",
                "Aria",
                12,
                9,
                9,
                0,
                30,
                1,
                11,
                0,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                List.of());
        ActorCatalogEntry character = ActorCatalogEntry.character(template, definition, true);

        assertEquals(
                List.of(character),
                ActorCatalogJsonCodec.fromMap(ActorCatalogJsonCodec.toMap(List.of(character))));
    }

    @Test
    void persistsPassiveTraitsAndDefaultsLegacyCatalogsToActiveAbilities() {
        AbilityDefinition mastery = AbilityDefinition.builder("mastery", "Weapon mastery")
                .activationCost(ActivationCost.NONE)
                .resolutionMethod(ResolutionMethod.MANUAL)
                .automationStatus(AutomationStatus.MANUAL_REQUIRED)
                .passive(true)
                .build();
        ActorTemplate template = new ActorTemplate(
                "fighter",
                "Fighter",
                ActorKind.PLAYER_CHARACTER,
                5,
                Map.of());
        ActorDefinition definition = ActorDefinition.builder("fighter", "Fighter")
                .maxHitPoints(40)
                .abilities(List.of(mastery))
                .build();
        ActorCatalogEntry character = ActorCatalogEntry.character(template, definition, true);

        Map<String, Object> document = mutableDocument(character);
        List<ActorCatalogEntry> decoded = ActorCatalogJsonCodec.decode(document);
        assertTrue(decoded.get(0).combatDefinition().abilities().get(0).passive());

        Map<String, Object> encodedEntry = object(array(document.get("entries")).get(0));
        Map<String, Object> encodedDefinition = object(encodedEntry.get("combatDefinition"));
        Map<String, Object> encodedAbility = object(array(encodedDefinition.get("abilities")).get(0));
        encodedAbility.remove("passive");
        List<ActorCatalogEntry> legacy = ActorCatalogJsonCodec.decode(document);
        assertFalse(legacy.get(0).combatDefinition().abilities().get(0).passive());
    }

    @Test
    void persistsAbilityClassificationAndDefaultsLegacyCatalogsToUnknownNonSpells() {
        AbilityDefinition spellAttack = AbilityDefinition.builder("spell-ray", "Spell ray")
                .resolutionMethod(ResolutionMethod.ATTACK_ROLL)
                .attackAbility(SaveAbility.CHARISMA)
                .spellOrCantrip(true)
                .damage(List.of(DamageFormula.dice(DamageType.FORCE, 1, 10, 0)))
                .build();
        ActorTemplate template = new ActorTemplate(
                "caster",
                "Caster",
                ActorKind.PLAYER_CHARACTER,
                1,
                Map.of());
        ActorDefinition definition = ActorDefinition.builder("caster", "Caster")
                .maxHitPoints(8)
                .abilities(List.of(spellAttack))
                .build();
        ActorCatalogEntry character = ActorCatalogEntry.character(template, definition, true);

        Map<String, Object> document = mutableDocument(character);
        AbilityDefinition decoded = ActorCatalogJsonCodec.decode(document)
                .get(0).combatDefinition().abilities().get(0);
        assertEquals(SaveAbility.CHARISMA, decoded.attackAbility());
        assertTrue(decoded.spellOrCantrip());

        Map<String, Object> encodedEntry = object(array(document.get("entries")).get(0));
        Map<String, Object> encodedDefinition = object(encodedEntry.get("combatDefinition"));
        Map<String, Object> encodedAbility = object(array(encodedDefinition.get("abilities")).get(0));
        encodedAbility.remove("attackAbility");
        encodedAbility.remove("spellOrCantrip");
        AbilityDefinition legacy = ActorCatalogJsonCodec.decode(document)
                .get(0).combatDefinition().abilities().get(0);
        assertEquals(null, legacy.attackAbility());
        assertFalse(legacy.spellOrCantrip());
    }

    @Test
    void persistsExtraAttackAndDefaultsLegacyCatalogsToOneAttack() {
        ActorTemplate template = new ActorTemplate(
                "fighter",
                "Fighter",
                ActorKind.PLAYER_CHARACTER,
                5,
                Map.of());
        ActorDefinition definition = ActorDefinition.builder("fighter", "Fighter")
                .maxHitPoints(40)
                .attacksPerAction(2)
                .strengthDexterityD20Disadvantage(true)
                .build();
        ActorCatalogEntry character = ActorCatalogEntry.character(template, definition, true);

        Map<String, Object> document = mutableDocument(character);
        List<ActorCatalogEntry> decoded = ActorCatalogJsonCodec.decode(document);
        assertEquals(2, decoded.get(0).combatDefinition().attacksPerAction());
        assertTrue(decoded.get(0).combatDefinition().strengthDexterityD20Disadvantage());

        Map<String, Object> encodedEntry = object(array(document.get("entries")).get(0));
        Map<String, Object> encodedDefinition = object(encodedEntry.get("combatDefinition"));
        encodedDefinition.remove("attacksPerAction");
        encodedDefinition.remove("strengthDexterityD20Disadvantage");
        List<ActorCatalogEntry> legacy = ActorCatalogJsonCodec.decode(document);
        assertEquals(1, legacy.get(0).combatDefinition().attacksPerAction());
        assertFalse(legacy.get(0).combatDefinition().strengthDexterityD20Disadvantage());
    }

    @Test
    void rejectsUnknownEnumsWithTheExactJsonPathAndAcceptedType() {
        Map<String, Object> document = mutableDocument(singleSimpleCreature());
        Map<String, Object> entry = object(array(document.get("entries")).get(0));
        Map<String, Object> definition = object(entry.get("combatDefinition"));
        definition.put("resistances", List.of("MAGIC"));

        ActorCatalogJsonCodec.CatalogFormatException exception = assertThrows(
                ActorCatalogJsonCodec.CatalogFormatException.class,
                () -> ActorCatalogJsonCodec.decode(document));

        assertTrue(exception.getMessage().contains("$.entries[0].combatDefinition.resistances[0]"));
        assertTrue(exception.getMessage().contains("unknown DamageType value 'MAGIC'"));
    }

    @Test
    void rejectsMalformedFieldsAndUnsupportedSchemaClearly() {
        Map<String, Object> missingEntries = new LinkedHashMap<>();
        missingEntries.put("schemaVersion", 1);
        ActorCatalogJsonCodec.CatalogFormatException missing = assertThrows(
                ActorCatalogJsonCodec.CatalogFormatException.class,
                () -> ActorCatalogJsonCodec.decode(missingEntries));
        assertTrue(missing.getMessage().contains("missing required member 'entries'"));

        Map<String, Object> future = new LinkedHashMap<>();
        future.put("schemaVersion", 2);
        future.put("entries", List.of());
        ActorCatalogJsonCodec.CatalogFormatException version = assertThrows(
                ActorCatalogJsonCodec.CatalogFormatException.class,
                () -> ActorCatalogJsonCodec.decode(future));
        assertTrue(version.getMessage().contains("unsupported schema version 2"));
    }

    @Test
    void rejectsDuplicateStableIdsAndMissingDamageAlternativeMembers() {
        ActorCatalogEntry entry = singleSimpleCreature();
        assertThrows(IllegalArgumentException.class,
                () -> ActorCatalogJsonCodec.encode(List.of(entry, entry)));

        Map<String, Object> duplicateDocument = mutableDocument(entry);
        array(duplicateDocument.get("entries")).add(array(duplicateDocument.get("entries")).get(0));
        assertThrows(ActorCatalogJsonCodec.CatalogFormatException.class,
                () -> ActorCatalogJsonCodec.decode(duplicateDocument));

        Map<String, Object> missingAlternative = mutableDocument(entry);
        Map<String, Object> encodedEntry = object(array(missingAlternative.get("entries")).get(0));
        Map<String, Object> definition = object(encodedEntry.get("combatDefinition"));
        Map<String, Object> ability = object(array(definition.get("abilities")).get(0));
        Map<String, Object> damage = object(array(ability.get("damage")).get(0));
        damage.remove("fixedAmount");
        assertThrows(ActorCatalogJsonCodec.CatalogFormatException.class,
                () -> ActorCatalogJsonCodec.decode(missingAlternative));
    }

    static ActorCatalogEntry singleSimpleCreature() {
        ActorTemplate template = new ActorTemplate(
                "rat",
                "Rat",
                ActorKind.CREATURE,
                0,
                Map.of());
        ActorDefinition definition = ActorDefinition.builder("rat", "Rat")
                .armorClass(10)
                .maxHitPoints(1)
                .abilities(List.of(AbilityDefinition.attack(
                        "rat-bite",
                        "Bite",
                        ActivationCost.ACTION,
                        0,
                        DamageFormula.dice(DamageType.PIERCING, 1, 4, -1))))
                .build();
        return ActorCatalogEntry.creature(template, definition, new BigDecimal("0.25"), 10);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableDocument(ActorCatalogEntry entry) {
        Map<String, Object> document = ActorCatalogJsonCodec.encode(List.of(entry));
        return (Map<String, Object>) Json.parse(Json.encode(document));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value) {
        return (List<Object>) value;
    }
}
