package app.d6d.domain.campaign;

import java.util.Objects;

/** A quantity of enemies sharing one stat block and XP context. */
public record EncounterEnemyGroup(
        String statBlockId,
        String displayName,
        double challengeRating,
        long baseXp,
        Long lairXp,
        int quantity,
        XpContext xpContext) {

    public EncounterEnemyGroup {
        statBlockId = CampaignValues.requireText(statBlockId, "statBlockId");
        displayName = CampaignValues.requireText(displayName, "displayName");
        if (!Double.isFinite(challengeRating) || challengeRating < 0) {
            throw new IllegalArgumentException("challengeRating must be finite and non-negative");
        }
        if (baseXp < 0) {
            throw new IllegalArgumentException("baseXp must not be negative");
        }
        if (lairXp != null && lairXp < 0) {
            throw new IllegalArgumentException("lairXp must not be negative");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        xpContext = Objects.requireNonNull(xpContext, "xpContext");
    }

    public static EncounterEnemyGroup base(
            String statBlockId,
            String displayName,
            double challengeRating,
            long baseXp,
            int quantity) {
        return new EncounterEnemyGroup(
                statBlockId, displayName, challengeRating, baseXp, null, quantity, XpContext.BASE);
    }

    public static EncounterEnemyGroup lair(
            String statBlockId,
            String displayName,
            double challengeRating,
            long baseXp,
            long lairXp,
            int quantity) {
        return new EncounterEnemyGroup(
                statBlockId, displayName, challengeRating, baseXp, lairXp, quantity, XpContext.LAIR);
    }

    /** Uses alternate XP only when both a lair context and a lair value are present. */
    public long effectiveXpPerCreature() {
        return xpContext == XpContext.LAIR && lairXp != null ? lairXp : baseXp;
    }

    public long effectiveXp() {
        return Math.multiplyExact(effectiveXpPerCreature(), quantity);
    }

    public boolean usesAlternateLairXp() {
        return xpContext == XpContext.LAIR && lairXp != null;
    }
}
