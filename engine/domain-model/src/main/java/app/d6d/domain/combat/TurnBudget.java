package app.d6d.domain.combat;

/** Immutable accounting for the independent resources available during a turn. */
public record TurnBudget(
        int movementAllowanceFeet,
        int movementSpentFeet,
        boolean actionAvailable,
        boolean bonusActionAvailable,
        boolean reactionAvailable,
        boolean objectInteractionAvailable,
        int attacksRemaining,
        boolean spellSlotSpentThisTurn) {

    public TurnBudget {
        if (movementAllowanceFeet < 0 || movementSpentFeet < 0 || movementSpentFeet > movementAllowanceFeet
                || attacksRemaining < 0) {
            throw new IllegalArgumentException("Invalid turn budget");
        }
    }

    public static TurnBudget fresh(int speedFeet) {
        return fresh(speedFeet, 1);
    }

    public static TurnBudget fresh(int speedFeet, int attacksPerAction) {
        if (attacksPerAction < 1) {
            throw new IllegalArgumentException("attacksPerAction must be at least 1");
        }
        return new TurnBudget(speedFeet, 0, true, true, true, true, attacksPerAction, false);
    }

    public int movementRemainingFeet() {
        return movementAllowanceFeet - movementSpentFeet;
    }

    public TurnBudget spendMovement(int feet) {
        if (feet < 0 || feet > movementRemainingFeet()) {
            throw new IllegalArgumentException("Movement exceeds the remaining budget");
        }
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet + feet, actionAvailable,
                bonusActionAvailable, reactionAvailable, objectInteractionAvailable, attacksRemaining,
                spellSlotSpentThisTurn);
    }

    public TurnBudget useAction() {
        if (!actionAvailable) throw new IllegalStateException("Action already spent");
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, false, bonusActionAvailable,
                reactionAvailable, objectInteractionAvailable, attacksRemaining, spellSlotSpentThisTurn);
    }

    public TurnBudget useBonusAction() {
        if (!bonusActionAvailable) throw new IllegalStateException("Bonus action already spent");
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, actionAvailable, false,
                reactionAvailable, objectInteractionAvailable, attacksRemaining, spellSlotSpentThisTurn);
    }

    public TurnBudget useReaction() {
        if (!reactionAvailable) throw new IllegalStateException("Reaction already spent");
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, actionAvailable, bonusActionAvailable,
                false, objectInteractionAvailable, attacksRemaining, spellSlotSpentThisTurn);
    }

    public TurnBudget useObjectInteraction() {
        if (!objectInteractionAvailable) throw new IllegalStateException("Object interaction already spent");
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, actionAvailable, bonusActionAvailable,
                reactionAvailable, false, attacksRemaining, spellSlotSpentThisTurn);
    }

    public TurnBudget useAttack() {
        if (attacksRemaining == 0) throw new IllegalStateException("No attacks remain in the Attack action");
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, actionAvailable, bonusActionAvailable,
                reactionAvailable, objectInteractionAvailable, attacksRemaining - 1, spellSlotSpentThisTurn);
    }

    public TurnBudget markSpellSlotSpent() {
        if (spellSlotSpentThisTurn) throw new IllegalStateException("A spell slot was already spent this turn");
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, actionAvailable, bonusActionAvailable,
                reactionAvailable, objectInteractionAvailable, attacksRemaining, true);
    }
}
