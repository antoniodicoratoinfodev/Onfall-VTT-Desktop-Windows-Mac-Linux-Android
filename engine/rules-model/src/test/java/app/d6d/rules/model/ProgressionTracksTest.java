package app.d6d.rules.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionTracksTest {

    @Test
    void exposesMultipleNamedTracksForThreePointFiveStyleAdvancement() {
        CompiledRuleset rules = compile(
                table("table:xp", "0=1;1000=2;3000=3;6000=4", "FLOOR"),
                table("table:bab:full", "1=1;2=2;3=3;4=4", "EXACT"),
                table("table:save:good", "1=2;2=3;3=3;4=4", "EXACT"),
                table("table:save:poor", "1=0;2=0;3=1;4=1", "EXACT"),
                table("table:skill-points", "1=16;2=4;3=4;4=4", "EXACT"),
                table("table:caster-level", "1=1;2=2;3=3;4=4", "EXACT"),
                progression("progression:character", Map.of(
                        "minimumLevel", "1",
                        "maximumLevel", "4",
                        "experienceTableRef", "table:xp")),
                progression("progression:fighter", Map.of(
                        "minimumLevel", "1",
                        "maximumLevel", "4",
                        "track.baseAttack", "table:bab:full",
                        "track.save.fortitude", "table:save:good",
                        "track.reflex", "table:save:poor",
                        "track.will", "table:save:poor",
                        "track.skillPointsGainedAtLevel", "table:skill-points")),
                progression("progression:wizard", Map.of(
                        "minimumLevel", "1",
                        "maximumLevel", "4",
                        "track.baseAttack", "table:save:poor",
                        "track.casterLevel", "table:caster-level")));

        assertEquals(3, rules.progressions().size());
        assertEquals(Map.of(
                        "baseAttack", "table:bab:full",
                        "save.fortitude", "table:save:good",
                        "reflex", "table:save:poor",
                        "will", "table:save:poor",
                        "skillPointsGainedAtLevel", "table:skill-points"),
                rules.progression("progression:fighter").trackTableRefs());
        assertEquals(new BigDecimal("3"),
                rules.progressionTrackValue("progression:fighter", "baseAttack", 3));
        assertEquals(new BigDecimal("1"),
                rules.progressionTrackValue("progression:fighter", "reflex", 4));
        assertEquals(new BigDecimal("4"),
                rules.progressionTrackValue("progression:wizard", "casterLevel", 4));
        assertThrows(IllegalArgumentException.class,
                () -> rules.progressionTrackValue("progression:wizard", "casterLevel", 99));
        assertEquals(3, rules.levelForExperience(new BigDecimal("4500")));
        assertEquals(3, rules.levelForExperience(
                "progression:character", new BigDecimal("4500")));
        assertEquals(new BigDecimal("1000"),
                rules.experienceForLevel("progression:character", 2));
    }

    @Test
    void multipleExperienceCurvesNeedOneExplicitDefaultButRemainAddressableById() {
        RuleEntity standardTable = table("table:xp:standard", "0=1;100=2", "FLOOR");
        RuleEntity fastTable = table("table:xp:fast", "0=1;50=2", "FLOOR");
        RuleEntity standard = progression("progression:standard", Map.of(
                "experienceTableRef", "table:xp:standard",
                "maximumLevel", "2",
                "defaultExperience", "true"));
        RuleEntity fast = progression("progression:fast", Map.of(
                "experienceTableRef", "table:xp:fast",
                "maximumLevel", "2"));
        CompiledRuleset rules = compile(standardTable, fastTable, standard, fast);

        assertEquals("progression:standard", rules.experienceProgression().id());
        assertEquals("progression:standard", rules.defaultExperienceProgressionId());
        assertEquals(1, rules.levelForExperience(new BigDecimal("75")));
        assertEquals(2, rules.levelForExperience(
                "progression:fast", new BigDecimal("75")));

        IllegalArgumentException missingDefault = assertThrows(IllegalArgumentException.class,
                () -> compile(standardTable, fastTable,
                        progression("progression:standard", Map.of(
                                "experienceTableRef", "table:xp:standard", "maximumLevel", "2")),
                        fast));
        assertTrue(missingDefault.getMessage().contains("defaultExperience"));

        IllegalArgumentException duplicateDefault = assertThrows(IllegalArgumentException.class,
                () -> compile(standardTable, fastTable, standard,
                        progression("progression:fast", Map.of(
                                "experienceTableRef", "table:xp:fast", "maximumLevel", "2",
                                "defaultExperience", "true"))));
        assertTrue(duplicateDefault.getMessage().contains("defaultExperience"));
    }

    @Test
    void progressionTracksMustReferenceTablesAndDefaultExperienceNeedsACurve() {
        IllegalArgumentException missingTable = assertThrows(IllegalArgumentException.class,
                () -> compile(progression("progression:broken", Map.of(
                        "track.baseAttack", "table:missing"))));
        assertTrue(missingTable.getMessage().contains("track.baseAttack"));

        IllegalArgumentException defaultWithoutCurve = assertThrows(IllegalArgumentException.class,
                () -> compile(progression("progression:broken-default", Map.of(
                        "defaultExperience", "true"))));
        assertTrue(defaultWithoutCurve.getMessage().contains("experienceTableRef"));

        IllegalArgumentException emptyTrackId = assertThrows(IllegalArgumentException.class,
                () -> compile(progression("progression:empty-track", Map.of(
                        "track.", "table:any"))));
        assertTrue(emptyTrackId.getMessage().contains("track id"));

        RuleEntity textTable = entity("table:text", RuleKind.TABLE, Map.of(
                "rows", "1=LOW;2=HIGH", "lookup", "EXACT", "valueType", "TEXT"));
        IllegalArgumentException nonNumeric = assertThrows(IllegalArgumentException.class,
                () -> compile(textTable, progression("progression:text-track", Map.of(
                        "track.rank", "table:text", "maximumLevel", "2"))));
        assertTrue(nonNumeric.getMessage().contains("numeric table"));

        RuleEntity startsAtTwo = table("table:starts-at-two", "2=2", "EXACT");
        IllegalArgumentException uncoveredMinimum = assertThrows(IllegalArgumentException.class,
                () -> compile(startsAtTwo, progression("progression:uncovered", Map.of(
                        "track.rank", "table:starts-at-two", "maximumLevel", "2"))));
        assertTrue(uncoveredMinimum.getMessage().contains("level range"));

        RuleEntity exactWithGap = table("table:gap", "1=1;3=3", "EXACT");
        IllegalArgumentException uncoveredMiddle = assertThrows(IllegalArgumentException.class,
                () -> compile(exactWithGap, progression("progression:gap", Map.of(
                        "track.rank", "table:gap", "maximumLevel", "3"))));
        assertTrue(uncoveredMiddle.getMessage().contains("every integer level"));

        RuleEntity ceilingWithoutMaximum = table("table:ceiling-short", "1=1;2=2", "CEILING");
        IllegalArgumentException uncoveredMaximum = assertThrows(IllegalArgumentException.class,
                () -> compile(ceilingWithoutMaximum, progression("progression:ceiling-short", Map.of(
                        "track.rank", "table:ceiling-short", "maximumLevel", "3"))));
        assertTrue(uncoveredMaximum.getMessage().contains("level range"));

        IllegalArgumentException normalizedDuplicate = assertThrows(IllegalArgumentException.class,
                () -> compile(progression("progression:duplicate-track", Map.of(
                        "track.rank", "table:first", "track. rank", "table:second"))));
        assertTrue(normalizedDuplicate.getMessage().contains("duplicates normalized track"));
    }

    @Test
    void maximumLevelIsCanonicalAndClassLinksMustTargetAProgression() {
        RuleEntity rank = table("table:rank", "1=1;2=2;3=3", "EXACT");
        RuleEntity progression = progression("progression:class", Map.of(
                "minimumLevel", "1",
                "maximumLevel", "3",
                "maximumCharacterLevel", "1",
                "track.rank", "table:rank"));
        RuleEntity linkedClass = entity("class:linked", RuleKind.CLASS, Map.of(
                "progressionEntityRef", "progression:class"));
        CompiledRuleset compiled = compile(rank, progression, linkedClass);

        assertEquals(3, compiled.progression("progression:class").maximumLevel());
        assertEquals(new BigDecimal("3"),
                compiled.progressionTrackValue("progression:class", "rank", 3));

        IllegalArgumentException wrongKind = assertThrows(IllegalArgumentException.class,
                () -> compile(rank, entity("class:broken", RuleKind.CLASS, Map.of(
                        "progressionEntityRef", "table:rank"))));
        assertTrue(wrongKind.getMessage().contains("must reference a progression"));
    }

    @Test
    void consumersCanAggregateClassTracksWithoutAnImplicitMulticlassPolicy() {
        CompiledRuleset compiled = compile(
                table("table:bab:full-six", "1=1;2=2;3=3;4=4;5=5;6=6", "EXACT"),
                table("table:bab:half-three", "1=0;2=1;3=1", "EXACT"),
                progression("progression:fighter", Map.of(
                        "maximumLevel", "6", "track.baseAttack", "table:bab:full-six")),
                progression("progression:wizard", Map.of(
                        "maximumLevel", "3", "track.baseAttack", "table:bab:half-three")));

        BigDecimal multiclassBab = compiled.progressionTrackValue(
                "progression:fighter", "baseAttack", 6).add(compiled.progressionTrackValue(
                "progression:wizard", "baseAttack", 3));
        assertEquals(new BigDecimal("7"), multiclassBab);
    }

    private static RuleEntity table(String id, String rows, String lookup) {
        return entity(id, RuleKind.TABLE, Map.of(
                "rows", rows, "lookup", lookup, "valueType", "NUMBER"));
    }

    private static RuleEntity progression(String id, Map<String, String> attributes) {
        return entity(id, RuleKind.PROGRESSION, attributes);
    }

    private static CompiledRuleset compile(RuleEntity... entities) {
        return RulesetRevision.create(
                "progression-test", "revision:progression-test", "1", "Progression test", "",
                RulesetOrigin.HOMEBREW, "", RulesetRuntimeConfig.genericManual(),
                List.of(entities), "now").compile();
    }

    private static RuleEntity entity(String id, RuleKind kind, Map<String, String> attributes) {
        return new RuleEntity(
                id, kind, RulesetOrigin.HOMEBREW,
                LocalizedRuleText.single("en", id), LocalizedRuleText.single("en", id),
                "", true, RuleAutomationLevel.FULL, attributes, List.of(), "test", "CC0", 0);
    }
}
