package app.d6d.rules.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollResolutionTest {

    @Test
    void fiveEStyleAttackSeparatesRandomizerTotalTargetAndNaturalRules() {
        CompiledRuleset rules = compile(
                d20("randomizer:d20", 1, "SUM"),
                stat("test:attack", "5"),
                stat("test:defense", "18"),
                roll("roll:attack", Map.of(
                        "randomizerRef", "randomizer:d20",
                        "totalFormula", "${roll} + ${test:attack}",
                        "targetFormula", "${test:defense}",
                        "comparison", "MEET_OR_EXCEED",
                        "naturalSuccessMinimum", "20",
                        "naturalFailureMaximum", "1",
                        "threatMinimumNatural", "20",
                        "criticalMultiplier", "2")));
        RuleRuntimeState source = rules.initialState(Map.of(), Set.of());
        RuleRuntimeState target = rules.initialState(Map.of(), Set.of());

        CompiledRuleset.RollResolution failure = rules.resolveRoll(
                "roll:attack", source, target, visibleRolls(12));
        CompiledRuleset.RollResolution critical = rules.resolveRoll(
                "roll:attack", source, target, visibleRolls(20));
        RuleRuntimeState enormousBonus = rules.setNumericValue(
                "test:attack", new BigDecimal("100"), source);
        CompiledRuleset.RollResolution naturalOne = rules.resolveRoll(
                "roll:attack", enormousBonus, target, visibleRolls(1));

        assertEquals(new BigDecimal("17"), failure.total());
        assertEquals(new BigDecimal("18"), failure.target());
        assertEquals(new BigDecimal("-1"), failure.margin());
        assertFalse(failure.success());
        assertEquals(RuleValue.text("FAILURE"), failure.outcome());

        assertTrue(critical.automaticSuccess());
        assertTrue(critical.success());
        assertTrue(critical.threat());
        assertTrue(critical.critical());
        assertEquals(2, critical.criticalMultiplier());
        assertNull(critical.confirmation());
        assertEquals(RuleValue.text("CRITICAL_SUCCESS"), critical.outcome());

        assertTrue(naturalOne.automaticFailure());
        assertFalse(naturalOne.success());
        assertEquals(new BigDecimal("101"), naturalOne.total());
        assertEquals(RuleValue.text("AUTOMATIC_FAILURE"), naturalOne.outcome());
    }

    @Test
    void advantageIsAReplaceableRandomizerPolicyNotHardcodedInTheCheck() {
        CompiledRuleset rules = compile(
                d20("randomizer:advantage", 2, "HIGHEST"),
                stat("test:attack", "5"), stat("test:defense", "18"),
                roll("roll:advantage", Map.of(
                        "randomizerRef", "randomizer:advantage",
                        "totalFormula", "${roll} + ${test:attack}",
                        "targetFormula", "${test:defense}")));
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of());

        CompiledRuleset.RollResolution result = rules.resolveRoll(
                "roll:advantage", state, visibleRolls(5, 15));

        assertEquals(List.of(5, 15), result.primary().draws());
        assertEquals(new BigDecimal("15"), result.primary().value());
        assertEquals(new BigDecimal("20"), result.total());
        assertTrue(result.success());
    }

    @Test
    void threePointFiveThreatUsesASecondDeterministicConfirmationRoll() {
        CompiledRuleset rules = compile(
                d20("randomizer:d20", 1, "SUM"),
                stat("test:bab-attack", "8"), stat("test:touch-defense", "20"),
                roll("roll:three-five-attack", Map.of(
                        "randomizerRef", "randomizer:d20",
                        "totalFormula", "${roll} + ${test:bab-attack}",
                        "targetFormula", "${test:touch-defense}",
                        "naturalSuccessMinimum", "20",
                        "naturalFailureMaximum", "1",
                        "threatMinimumNatural", "19",
                        "confirmationRequired", "true",
                        "criticalMultiplier", "3")));
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of());

        CompiledRuleset.RollResolution confirmed = rules.resolveRoll(
                "roll:three-five-attack", state, visibleRolls(19, 15));
        CompiledRuleset.RollResolution unconfirmed = rules.resolveRoll(
                "roll:three-five-attack", state, visibleRolls(19, 5));

        assertTrue(confirmed.success());
        assertTrue(confirmed.threat());
        assertTrue(confirmed.critical());
        assertEquals(3, confirmed.criticalMultiplier());
        assertEquals(List.of(15), confirmed.confirmation().draws());
        assertEquals(new BigDecimal("23"), confirmed.confirmationTotal());

        assertTrue(unconfirmed.success());
        assertTrue(unconfirmed.threat());
        assertFalse(unconfirmed.critical());
        assertEquals(1, unconfirmed.criticalMultiplier());
        assertNotNull(unconfirmed.confirmation());
        assertEquals(new BigDecimal("13"), unconfirmed.confirmationTotal());
    }

    @Test
    void opposedChecksUseTheOpponentTotalAndTheDeclaredTieComparison() {
        CompiledRuleset rules = compile(
                randomizer("randomizer:d6", 1, 6, "SUM"),
                stat("test:power", "2"), stat("test:resistance", "1"),
                roll("roll:defend", Map.of(
                        "randomizerRef", "randomizer:d6",
                        "totalFormula", "${roll} + ${test:resistance}")),
                roll("roll:contest", Map.of(
                        "randomizerRef", "randomizer:d6",
                        "totalFormula", "${roll} + ${test:power}",
                        "opposedRollRef", "roll:defend",
                        "comparison", "EXCEED")));
        RuleRuntimeState state = rules.initialState(Map.of(), Set.of());

        CompiledRuleset.RollResolution result = rules.resolveRoll(
                "roll:contest", state, state, visibleRolls(4, 5));

        assertEquals(new BigDecimal("6"), result.total());
        assertEquals(new BigDecimal("6"), result.opposedTotal());
        assertEquals(result.opposedTotal(), result.target());
        assertEquals(List.of(5), result.opposed().draws());
        assertEquals(BigDecimal.ZERO, result.margin());
        assertFalse(result.success());
    }

    @Test
    void legacyRollEntitiesRemainRandomizersWhileBrokenResolutionLinksFailPublication() {
        RuleEntity legacy = entity("roll:legacy", RuleKind.ROLL,
                Map.of("mode", "DICE", "countFormula", "1", "sidesFormula", "20"));
        CompiledRuleset compatible = compile(legacy);
        assertTrue(compatible.randomizers().containsKey("roll:legacy"));
        assertTrue(compatible.rolls().isEmpty());

        IllegalArgumentException missingRandomizer = assertThrows(IllegalArgumentException.class, () -> compile(
                roll("roll:broken", Map.of("randomizerRef", "randomizer:absent"))));
        assertTrue(missingRandomizer.getMessage().contains("randomizerRef"));

        IllegalArgumentException overlappingNaturalRanges = assertThrows(IllegalArgumentException.class, () -> compile(
                d20("randomizer:d20", 1, "SUM"),
                roll("roll:overlap", Map.of(
                        "randomizerRef", "randomizer:d20",
                        "naturalFailureMaximum", "10",
                        "naturalSuccessMinimum", "10"))));
        assertTrue(overlappingNaturalRanges.getMessage().contains("overlap"));
    }

    @Test
    void everyResolutionOnlyAttributeRequiresAnExplicitRandomizerReference() {
        Map<String, String> resolutionOnlyAttributes = Map.of(
                "naturalSuccessMinimum", "20",
                "naturalFailureMaximum", "1",
                "confirmationRequired", "true",
                "criticalMultiplier", "2",
                "outcomeTableRef", "table:outcomes");

        resolutionOnlyAttributes.forEach((attribute, value) -> {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> compile(roll("roll:missing-randomizer:" + attribute, Map.of(attribute, value))),
                    attribute);
            assertTrue(failure.getMessage().contains("randomizerRef"), attribute);
        });
    }

    @Test
    void naturalOverridesDoNotReuseAnOutcomeBandWithTheOppositeResult() {
        CompiledRuleset rules = compile(
                d20("randomizer:d20", 1, "SUM"),
                stat("test:attack", "0"),
                stat("test:defense", "18"),
                entity("table:outcomes", RuleKind.TABLE, Map.of(
                        "valueType", "TEXT",
                        "lookup", "FLOOR",
                        "rows", "-1000=NEGATIVE_BAND;0=POSITIVE_BAND")),
                roll("roll:with-outcomes", Map.of(
                        "randomizerRef", "randomizer:d20",
                        "totalFormula", "${roll} + ${test:attack}",
                        "targetFormula", "${test:defense}",
                        "naturalSuccessMinimum", "20",
                        "naturalFailureMaximum", "1",
                        "outcomeTableRef", "table:outcomes")));
        RuleRuntimeState base = rules.initialState(Map.of(), Set.of());
        RuleRuntimeState enormousBonus = rules.setNumericValue(
                "test:attack", new BigDecimal("100"), base);
        RuleRuntimeState enormousPenalty = rules.setNumericValue(
                "test:attack", new BigDecimal("-100"), base);

        CompiledRuleset.RollResolution ordinarySuccess = rules.resolveRoll(
                "roll:with-outcomes", base, visibleRolls(18));
        CompiledRuleset.RollResolution naturalOne = rules.resolveRoll(
                "roll:with-outcomes", enormousBonus, visibleRolls(1));
        CompiledRuleset.RollResolution naturalTwenty = rules.resolveRoll(
                "roll:with-outcomes", enormousPenalty, visibleRolls(20));

        assertTrue(ordinarySuccess.success());
        assertEquals(RuleValue.text("POSITIVE_BAND"), ordinarySuccess.outcome());
        assertFalse(naturalOne.success());
        assertEquals(RuleValue.text("AUTOMATIC_FAILURE"), naturalOne.outcome());
        assertTrue(naturalTwenty.success());
        assertEquals(RuleValue.text("AUTOMATIC_SUCCESS"), naturalTwenty.outcome());
    }

    @Test
    void rollLocalValuesAreAcceptedOnlyInsideRollTotalFormulas() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> compile(
                stat("test:invalid", "${roll}")));

        assertTrue(failure.getMessage().contains("defaultFormula"));
        assertTrue(failure.getMessage().contains("roll"));
    }

    private static CompiledRuleset.RandomSource visibleRolls(int... values) {
        AtomicInteger index = new AtomicInteger();
        return bound -> {
            int visible = values[index.getAndIncrement()];
            if (visible < 1 || visible > bound) throw new AssertionError("Roll is outside die bounds");
            return visible - 1;
        };
    }

    private static RuleEntity d20(String id, int count, String keep) {
        return randomizer(id, count, 20, keep);
    }

    private static RuleEntity randomizer(String id, int count, int sides, String keep) {
        return entity(id, RuleKind.RANDOMIZER, Map.of(
                "mode", "DICE", "countFormula", Integer.toString(count),
                "sidesFormula", Integer.toString(sides), "keep", keep));
    }

    private static RuleEntity roll(String id, Map<String, String> attributes) {
        return entity(id, RuleKind.ROLL, attributes);
    }

    private static RuleEntity stat(String id, String formula) {
        return entity(id, RuleKind.STAT, Map.of("defaultFormula", formula));
    }

    private static CompiledRuleset compile(RuleEntity... entities) {
        return RulesetRevision.create(
                "roll-test", "revision:roll-test", "1", "Roll test", "",
                RulesetOrigin.HOMEBREW, "", RulesetRuntimeConfig.standardSrd521(),
                List.of(entities), "now").compile();
    }

    private static RuleEntity entity(String id, RuleKind kind, Map<String, String> attributes) {
        return new RuleEntity(
                id, kind, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.single("en", id), LocalizedRuleText.single("en", "Test"),
                "", true, RuleAutomationLevel.FULL, attributes, List.of("test"), "test", "", 0);
    }
}
