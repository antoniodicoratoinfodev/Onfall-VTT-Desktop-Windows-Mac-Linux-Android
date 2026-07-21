package app.d6d.engine.encounter;

import app.d6d.domain.campaign.EncounterDefinition;
import app.d6d.domain.campaign.EncounterEnemyGroup;
import app.d6d.domain.campaign.EncounterPartyMember;
import app.d6d.domain.rules.EncounterDifficulty;
import app.d6d.domain.rules.XpBudget;
import app.d6d.domain.rules.XpBudgetTable2024;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies the 2024 direct-sum encounter budget rules and qualitative warnings. */
public final class EncounterBuilder {

    public EncounterEvaluation evaluate(EncounterDefinition encounter) {
        Objects.requireNonNull(encounter, "encounter");
        return evaluate(encounter, encounter.targetDifficulty());
    }

    public EncounterEvaluation evaluate(
            EncounterDefinition encounter, EncounterDifficulty targetDifficulty) {
        Objects.requireNonNull(encounter, "encounter");
        Objects.requireNonNull(targetDifficulty, "targetDifficulty");

        List<EncounterPartyMember> presentParty = encounter.party().stream()
                .filter(EncounterPartyMember::present)
                .collect(Collectors.toList());
        XpBudget partyBudget = calculatePartyBudget(presentParty);
        long encounterXp = calculateEncounterCost(encounter.enemies());
        long targetXp = partyBudget.forDifficulty(targetDifficulty);
        long delta = Math.subtractExact(encounterXp, targetXp);
        boolean overBudget = encounterXp > targetXp;

        long enemyCount = enemyCount(encounter.enemies());
        int distinctStatBlocks = distinctStatBlockCount(encounter.enemies());
        List<EncounterWarning> warnings = warnings(
                encounter,
                presentParty,
                encounterXp,
                targetXp,
                enemyCount,
                distinctStatBlocks);

        return new EncounterEvaluation(
                partyBudget,
                encounterXp,
                targetDifficulty,
                targetXp,
                delta,
                rating(encounterXp, partyBudget),
                overBudget,
                overBudget && encounter.allowOverBudget(),
                presentParty.size(),
                enemyCount,
                distinctStatBlocks,
                warnings);
    }

    /** Sums the row for every present character; it does not average party levels. */
    public XpBudget calculatePartyBudget(List<EncounterPartyMember> party) {
        Objects.requireNonNull(party, "party");
        XpBudget result = XpBudget.zero();
        for (EncounterPartyMember member : party) {
            Objects.requireNonNull(member, "party contains null");
            if (member.present()) {
                result = result.plus(XpBudgetTable2024.forLevel(member.level()));
            }
        }
        return result;
    }

    /** Direct 2024 XP sum. No enemy-count multiplier is applied. */
    public long calculateEncounterCost(List<EncounterEnemyGroup> enemies) {
        Objects.requireNonNull(enemies, "enemies");
        long result = 0;
        for (EncounterEnemyGroup enemy : enemies) {
            Objects.requireNonNull(enemy, "enemies contains null");
            result = Math.addExact(result, enemy.effectiveXp());
        }
        return result;
    }

    private static EncounterRating rating(long cost, XpBudget budget) {
        if (cost <= budget.low()) {
            return EncounterRating.LOW;
        }
        if (cost <= budget.moderate()) {
            return EncounterRating.MODERATE;
        }
        if (cost <= budget.high()) {
            return EncounterRating.HIGH;
        }
        return EncounterRating.ABOVE_HIGH;
    }

    private static long enemyCount(List<EncounterEnemyGroup> enemies) {
        long result = 0;
        for (EncounterEnemyGroup enemy : enemies) {
            result = Math.addExact(result, enemy.quantity());
        }
        return result;
    }

    private static int distinctStatBlockCount(List<EncounterEnemyGroup> enemies) {
        Set<String> ids = new HashSet<>();
        enemies.forEach(enemy -> ids.add(enemy.statBlockId()));
        return ids.size();
    }

    private static List<EncounterWarning> warnings(
            EncounterDefinition encounter,
            List<EncounterPartyMember> presentParty,
            long encounterXp,
            long targetXp,
            long enemyCount,
            int distinctStatBlocks) {
        List<EncounterWarning> result = new ArrayList<>();

        if (!presentParty.isEmpty() && enemyCount > (long) presentParty.size() * 2) {
            int minimumLevel = presentParty.stream()
                    .mapToInt(EncounterPartyMember::level)
                    .min()
                    .orElseThrow();
            WarningSeverity severity = minimumLevel <= 2
                    ? WarningSeverity.DANGER
                    : WarningSeverity.WARNING;
            result.add(new EncounterWarning(
                    EncounterWarningCode.ENEMIES_OUTNUMBER_PARTY,
                    severity,
                    "There are more than two enemies per present character; "
                            + "prefer fragile enemies or reduce their number."));
        }

        if (distinctStatBlocks > 3) {
            result.add(new EncounterWarning(
                    EncounterWarningCode.TOO_MANY_STAT_BLOCKS,
                    WarningSeverity.WARNING,
                    "More than three different stat blocks increase the game master's workload."));
        }

        if (!presentParty.isEmpty()) {
            int partyLevel = presentParty.stream()
                    .mapToInt(EncounterPartyMember::level)
                    .max()
                    .orElseThrow();
            List<EncounterEnemyGroup> abovePartyLevel = encounter.enemies().stream()
                    .filter(enemy -> enemy.challengeRating() > partyLevel)
                    .collect(Collectors.toList());
            if (!abovePartyLevel.isEmpty()) {
                String names = abovePartyLevel.stream()
                        .map(EncounterEnemyGroup::displayName)
                        .distinct()
                        .sorted()
                        .collect(Collectors.joining(", "));
                result.add(new EncounterWarning(
                        EncounterWarningCode.CHALLENGE_RATING_ABOVE_PARTY_LEVEL,
                        WarningSeverity.DANGER,
                        "Challenge Rating exceeds the highest present character level: " + names + "."));
            }
        }

        List<EncounterEnemyGroup> lairEnemies = encounter.enemies().stream()
                .filter(EncounterEnemyGroup::usesAlternateLairXp)
                .collect(Collectors.toList());
        if (!lairEnemies.isEmpty()) {
            String names = lairEnemies.stream()
                    .map(EncounterEnemyGroup::displayName)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));
            result.add(new EncounterWarning(
                    EncounterWarningCode.LAIR_XP_APPLIED,
                    WarningSeverity.INFO,
                    "Alternative lair XP is active for: " + names + "."));
        }

        long zeroXpEnemies = encounter.enemies().stream()
                .filter(enemy -> enemy.effectiveXpPerCreature() == 0)
                .mapToLong(EncounterEnemyGroup::quantity)
                .sum();
        if (zeroXpEnemies > 0) {
            result.add(new EncounterWarning(
                    EncounterWarningCode.ZERO_XP_ENEMIES,
                    WarningSeverity.WARNING,
                    zeroXpEnemies + " enemies contribute 0 XP; consider using a swarm."));
        }

        if (encounterXp > targetXp) {
            String overrideText = encounter.allowOverBudget()
                    ? " The explicit override permits it."
                    : " An explicit override is required.";
            result.add(new EncounterWarning(
                    EncounterWarningCode.OVER_TARGET_BUDGET,
                    WarningSeverity.DANGER,
                    "The encounter exceeds the selected budget by "
                            + (encounterXp - targetXp) + " XP." + overrideText));
        }

        return List.copyOf(result);
    }
}
