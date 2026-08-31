package app.d6d.rules.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Prime primitive runtime estratte dal motore, versionate insieme alla revisione. */
public record RulesetRuntimeConfig(
        String semanticsVersion,
        int criticalHitMinimumNatural,
        boolean naturalOneAlwaysMisses,
        int maximumExhaustion,
        int exhaustionD20PenaltyPerLevel,
        int exhaustionSpeedPenaltyFeetPerLevel,
        int proficiencyBonusBase,
        int proficiencyLevelsPerIncrease,
        int proficiencyBonusMaximum) {

    public static final String CURRENT_SEMANTICS = "1";

    public RulesetRuntimeConfig {
        if (semanticsVersion == null || semanticsVersion.isBlank()) {
            throw new IllegalArgumentException("semanticsVersion cannot be blank");
        }
        semanticsVersion = semanticsVersion.trim();
        if (criticalHitMinimumNatural < 2 || criticalHitMinimumNatural > 20) {
            throw new IllegalArgumentException("Critical threshold must be between 2 and 20");
        }
        if (maximumExhaustion < 1 || maximumExhaustion > 20) {
            throw new IllegalArgumentException("Maximum Exhaustion must be between 1 and 20");
        }
        if (exhaustionD20PenaltyPerLevel < 0 || exhaustionD20PenaltyPerLevel > 20) {
            throw new IllegalArgumentException("Exhaustion D20 penalty must be between 0 and 20");
        }
        if (exhaustionSpeedPenaltyFeetPerLevel < 0 || exhaustionSpeedPenaltyFeetPerLevel > 100) {
            throw new IllegalArgumentException("Exhaustion speed penalty must be between 0 and 100");
        }
        if (proficiencyBonusBase < -20 || proficiencyBonusBase > 20) {
            throw new IllegalArgumentException("Proficiency base must be between -20 and 20");
        }
        if (proficiencyLevelsPerIncrease < 1 || proficiencyLevelsPerIncrease > 20) {
            throw new IllegalArgumentException("Proficiency interval must be between 1 and 20");
        }
        if (proficiencyBonusMaximum < proficiencyBonusBase || proficiencyBonusMaximum > 50) {
            throw new IllegalArgumentException("Proficiency maximum is invalid");
        }
    }

    public static RulesetRuntimeConfig standardSrd521() {
        return new RulesetRuntimeConfig(CURRENT_SEMANTICS, 20, true, 6, 2, 5, 2, 4, 6);
    }

    /**
     * Valori di compatibilità interni per un regolamento che usa soltanto le
     * primitive generiche. Non dichiarano alcuna meccanica: critico,
     * Sfinimento e competenza diventano regole soltanto quando esistono le
     * rispettive entità nel regolamento.
     */
    public static RulesetRuntimeConfig genericManual() {
        return new RulesetRuntimeConfig(CURRENT_SEMANTICS, 20, false, 1, 0, 0, 0, 1, 0);
    }

    public int proficiencyBonus(int level) {
        int normalizedLevel = Math.max(1, level);
        return Math.min(proficiencyBonusMaximum,
                proficiencyBonusBase + (normalizedLevel - 1) / proficiencyLevelsPerIncrease);
    }

    public RulesetRuntimeConfig withCriticalHitMinimumNatural(int value) {
        return new RulesetRuntimeConfig(semanticsVersion, value, naturalOneAlwaysMisses,
                maximumExhaustion, exhaustionD20PenaltyPerLevel, exhaustionSpeedPenaltyFeetPerLevel,
                proficiencyBonusBase, proficiencyLevelsPerIncrease, proficiencyBonusMaximum);
    }

    public RulesetRuntimeConfig withNaturalOneAlwaysMisses(boolean value) {
        return new RulesetRuntimeConfig(semanticsVersion, criticalHitMinimumNatural, value,
                maximumExhaustion, exhaustionD20PenaltyPerLevel, exhaustionSpeedPenaltyFeetPerLevel,
                proficiencyBonusBase, proficiencyLevelsPerIncrease, proficiencyBonusMaximum);
    }

    public RulesetRuntimeConfig withMaximumExhaustion(int value) {
        return new RulesetRuntimeConfig(semanticsVersion, criticalHitMinimumNatural, naturalOneAlwaysMisses,
                value, exhaustionD20PenaltyPerLevel, exhaustionSpeedPenaltyFeetPerLevel,
                proficiencyBonusBase, proficiencyLevelsPerIncrease, proficiencyBonusMaximum);
    }

    public RulesetRuntimeConfig withExhaustionD20PenaltyPerLevel(int value) {
        return new RulesetRuntimeConfig(semanticsVersion, criticalHitMinimumNatural, naturalOneAlwaysMisses,
                maximumExhaustion, value, exhaustionSpeedPenaltyFeetPerLevel,
                proficiencyBonusBase, proficiencyLevelsPerIncrease, proficiencyBonusMaximum);
    }

    public RulesetRuntimeConfig withExhaustionSpeedPenaltyFeetPerLevel(int value) {
        return new RulesetRuntimeConfig(semanticsVersion, criticalHitMinimumNatural, naturalOneAlwaysMisses,
                maximumExhaustion, exhaustionD20PenaltyPerLevel, value,
                proficiencyBonusBase, proficiencyLevelsPerIncrease, proficiencyBonusMaximum);
    }

    public RulesetRuntimeConfig withProficiency(int base, int levelsPerIncrease, int maximum) {
        return new RulesetRuntimeConfig(semanticsVersion, criticalHitMinimumNatural, naturalOneAlwaysMisses,
                maximumExhaustion, exhaustionD20PenaltyPerLevel, exhaustionSpeedPenaltyFeetPerLevel,
                base, levelsPerIncrease, maximum);
    }

    public Map<String, String> attributesFor(String entityId) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (CoreRuleIds.CRITICAL_HIT.equals(entityId)) {
            result.put("criticalHitMinimumNatural", Integer.toString(criticalHitMinimumNatural));
            result.put("naturalOneAlwaysMisses", Boolean.toString(naturalOneAlwaysMisses));
        } else if (CoreRuleIds.EXHAUSTION.equals(entityId)) {
            result.put("maximumExhaustion", Integer.toString(maximumExhaustion));
            result.put("d20PenaltyPerLevel", Integer.toString(exhaustionD20PenaltyPerLevel));
            result.put("speedPenaltyFeetPerLevel", Integer.toString(exhaustionSpeedPenaltyFeetPerLevel));
        } else if (CoreRuleIds.PROFICIENCY.equals(entityId)) {
            result.put("base", Integer.toString(proficiencyBonusBase));
            result.put("levelsPerIncrease", Integer.toString(proficiencyLevelsPerIncrease));
            result.put("maximum", Integer.toString(proficiencyBonusMaximum));
        }
        return Map.copyOf(result);
    }
}
