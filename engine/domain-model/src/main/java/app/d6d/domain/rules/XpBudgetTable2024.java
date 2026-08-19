package app.d6d.domain.rules;

import java.util.Collection;
import java.util.Objects;

/** Official per-character encounter budgets for the revised 2024 rules. */
public final class XpBudgetTable2024 {

    private static final XpBudget[] BY_LEVEL = {
        null,
        new XpBudget(50, 75, 100),
        new XpBudget(100, 150, 200),
        new XpBudget(150, 225, 400),
        new XpBudget(250, 375, 500),
        new XpBudget(500, 750, 1_100),
        new XpBudget(600, 1_000, 1_400),
        new XpBudget(750, 1_300, 1_700),
        new XpBudget(1_000, 1_700, 2_100),
        new XpBudget(1_300, 2_000, 2_600),
        new XpBudget(1_600, 2_300, 3_100),
        new XpBudget(1_900, 2_900, 4_100),
        new XpBudget(2_200, 3_700, 4_700),
        new XpBudget(2_600, 4_200, 5_400),
        new XpBudget(2_900, 4_900, 6_200),
        new XpBudget(3_300, 5_400, 7_800),
        new XpBudget(3_800, 6_100, 9_800),
        new XpBudget(4_500, 7_200, 11_700),
        new XpBudget(5_000, 8_700, 14_200),
        new XpBudget(5_500, 10_700, 17_200),
        new XpBudget(6_400, 13_200, 22_000)
    };

    private XpBudgetTable2024() {
    }

    public static XpBudget forLevel(int level) {
        if (level < 1 || level > 20) {
            throw new IllegalArgumentException("character level must be between 1 and 20");
        }
        return BY_LEVEL[level];
    }

    public static XpBudget budgetForLevel(int level) {
        return forLevel(level);
    }

    /** Adds each character's row independently, so mixed-level parties remain accurate. */
    public static XpBudget forPartyLevels(Collection<Integer> levels) {
        Objects.requireNonNull(levels, "levels");
        XpBudget result = XpBudget.zero();
        for (Integer level : levels) {
            result = result.plus(forLevel(Objects.requireNonNull(level, "levels contains null")));
        }
        return result;
    }

    public static XpBudget budgetForParty(Collection<Integer> levels) {
        return forPartyLevels(levels);
    }
}
