package app.d6d.engine.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.d6d.domain.campaign.EncounterDefinition;
import app.d6d.domain.campaign.EncounterEnemyGroup;
import app.d6d.domain.campaign.EncounterPartyMember;
import app.d6d.domain.campaign.XpContext;
import app.d6d.domain.rules.EncounterDifficulty;
import app.d6d.domain.rules.RulesetVersionManifest;
import app.d6d.domain.rules.XpBudget;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EncounterBuilderTest {

    private static final RulesetVersionManifest MANIFEST = new RulesetVersionManifest(
            "srd-5.2.1", "5.2.1", "2025-09", List.of());
    private final EncounterBuilder builder = new EncounterBuilder();

    @Test
    void mixedLevelPartyUsesEveryIndividualBudgetAndExcludesAbsentCharacters() {
        EncounterDefinition encounter = encounter(
                List.of(
                        member("one", 1),
                        member("five", 5),
                        member("twenty", 20),
                        new EncounterPartyMember("absent", "Absent", 20, false)),
                List.of(),
                EncounterDifficulty.HIGH);

        EncounterEvaluation result = builder.evaluate(encounter);

        assertEquals(new XpBudget(6_950, 14_025, 23_200), result.partyBudget());
        assertEquals(3, result.presentCharacterCount());
    }

    @Test
    void monsterCostIsADirectSumWithoutTheLegacyMultiplier() {
        List<EncounterEnemyGroup> enemies = List.of(
                EncounterEnemyGroup.base("goblin", "Goblin", 0.25, 50, 3),
                EncounterEnemyGroup.base("wolf", "Wolf", 0.25, 100, 2));

        assertEquals(350, builder.calculateEncounterCost(enemies));
    }

    @Test
    void selectsBaseOrLairXpFromTheActiveContext() {
        EncounterEnemyGroup base = new EncounterEnemyGroup(
                "dragon-base", "Dragon", 10, 5_900, 7_200L, 1, XpContext.BASE);
        EncounterEnemyGroup lair = new EncounterEnemyGroup(
                "dragon-lair", "Dragon", 10, 5_900, 7_200L, 1, XpContext.LAIR);
        EncounterDefinition encounter = encounter(
                List.of(member("hero", 20)),
                List.of(base, lair),
                EncounterDifficulty.HIGH);

        EncounterEvaluation result = builder.evaluate(encounter);

        assertEquals(13_100, result.encounterCostXp());
        assertTrue(result.hasWarning(EncounterWarningCode.LAIR_XP_APPLIED));
    }

    @Test
    void warnsWhenEnemiesExceedTwoPerCharacterAndIsStricterAtLevelTwo() {
        EncounterDefinition encounter = encounter(
                List.of(member("hero", 2)),
                List.of(EncounterEnemyGroup.base("rat", "Rat", 0, 5, 3)),
                EncounterDifficulty.HIGH);

        EncounterEvaluation result = builder.evaluate(encounter);
        EncounterWarning warning = warning(result, EncounterWarningCode.ENEMIES_OUTNUMBER_PARTY);

        assertEquals(WarningSeverity.DANGER, warning.severity());
        assertEquals(3, result.enemyCount());
    }

    @Test
    void warnsAboveThreeDistinctStatBlocks() {
        EncounterDefinition encounter = encounter(
                List.of(member("hero", 10)),
                List.of(
                        enemy("a", 1, 10),
                        enemy("b", 1, 10),
                        enemy("c", 1, 10),
                        enemy("d", 1, 10)),
                EncounterDifficulty.LOW);

        EncounterEvaluation result = builder.evaluate(encounter);

        assertEquals(4, result.distinctStatBlockCount());
        assertTrue(result.hasWarning(EncounterWarningCode.TOO_MANY_STAT_BLOCKS));
    }

    @Test
    void warnsWhenChallengeRatingExceedsPartyLevel() {
        EncounterDefinition encounter = encounter(
                List.of(member("hero", 3)),
                List.of(enemy("ogre-mage", 4, 100)),
                EncounterDifficulty.HIGH);

        assertTrue(builder.evaluate(encounter)
                .hasWarning(EncounterWarningCode.CHALLENGE_RATING_ABOVE_PARTY_LEVEL));
    }

    @Test
    void warnsAboutZeroXpCreatures() {
        EncounterDefinition encounter = encounter(
                List.of(member("hero", 3)),
                List.of(enemy("harmless", 0, 0)),
                EncounterDifficulty.LOW);

        assertTrue(builder.evaluate(encounter)
                .hasWarning(EncounterWarningCode.ZERO_XP_ENEMIES));
    }

    @Test
    void reportsBudgetDeltaAndExplicitOverrideSeparately() {
        EncounterDefinition encounter = new EncounterDefinition(
                "encounter",
                "Encounter",
                MANIFEST,
                List.of(member("hero", 1)),
                List.of(enemy("boss", 1, 150)),
                EncounterDifficulty.HIGH,
                true,
                Map.of());

        EncounterEvaluation result = builder.evaluate(encounter);

        assertEquals(50, result.xpDeltaFromTarget());
        assertEquals(EncounterRating.ABOVE_HIGH, result.officialRating());
        assertTrue(result.overTargetBudget());
        assertTrue(result.overBudgetAllowed());
        assertTrue(result.hasWarning(EncounterWarningCode.OVER_TARGET_BUDGET));
    }

    @Test
    void exactlyThreeStatBlocksAndTwoEnemiesPerCharacterDoNotWarn() {
        EncounterDefinition encounter = encounter(
                List.of(member("one", 10), member("two", 10)),
                List.of(
                        enemy("a", 1, 10),
                        enemy("b", 1, 10),
                        new EncounterEnemyGroup("c", "c", 1, 10, null, 2, XpContext.BASE)),
                EncounterDifficulty.LOW);

        EncounterEvaluation result = builder.evaluate(encounter);

        assertFalse(result.hasWarning(EncounterWarningCode.TOO_MANY_STAT_BLOCKS));
        assertFalse(result.hasWarning(EncounterWarningCode.ENEMIES_OUTNUMBER_PARTY));
    }

    private static EncounterDefinition encounter(
            List<EncounterPartyMember> party,
            List<EncounterEnemyGroup> enemies,
            EncounterDifficulty difficulty) {
        return new EncounterDefinition(
                "encounter", "Encounter", MANIFEST, party, enemies, difficulty);
    }

    private static EncounterPartyMember member(String id, int level) {
        return new EncounterPartyMember(id, id, level);
    }

    private static EncounterEnemyGroup enemy(String id, double cr, long xp) {
        return EncounterEnemyGroup.base(id, id, cr, xp, 1);
    }

    private static EncounterWarning warning(
            EncounterEvaluation evaluation, EncounterWarningCode code) {
        return evaluation.warnings().stream()
                .filter(value -> value.code() == code)
                .findFirst()
                .orElseThrow();
    }
}
