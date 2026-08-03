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
        boolean spellSlotSpentThisTurn,
        boolean additionalActionAvailable,
        boolean additionalActionMagicRestricted,
        boolean actionSurgeUsedThisTurn,
        boolean attackActionInProgress) {

    public TurnBudget {
        if (movementAllowanceFeet < 0 || movementSpentFeet < 0 || movementSpentFeet > movementAllowanceFeet
                || attacksRemaining < 0) {
            throw new IllegalArgumentException("Invalid turn budget");
        }
        if (additionalActionMagicRestricted && !additionalActionAvailable) {
            throw new IllegalArgumentException("Only an available additional action can be restricted");
        }
    }

    /** Compatibilità con i salvataggi e i chiamanti precedenti alle azioni aggiuntive. */
    public TurnBudget(
            int movementAllowanceFeet,
            int movementSpentFeet,
            boolean actionAvailable,
            boolean bonusActionAvailable,
            boolean reactionAvailable,
            boolean objectInteractionAvailable,
            int attacksRemaining,
            boolean spellSlotSpentThisTurn) {
        this(movementAllowanceFeet, movementSpentFeet, actionAvailable, bonusActionAvailable,
                reactionAvailable, objectInteractionAvailable, attacksRemaining, spellSlotSpentThisTurn,
                false, false, false, false);
    }

    public static TurnBudget fresh(int speedFeet) {
        return fresh(speedFeet, 1);
    }

    public static TurnBudget fresh(int speedFeet, int attacksPerAction) {
        if (attacksPerAction < 1) {
            throw new IllegalArgumentException("attacksPerAction must be at least 1");
        }
        return new TurnBudget(speedFeet, 0, true, true, true, true, attacksPerAction, false,
                false, false, false, false);
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
                spellSlotSpentThisTurn, additionalActionAvailable, additionalActionMagicRestricted,
                actionSurgeUsedThisTurn, attackActionInProgress);
    }

    public TurnBudget useAction() {
        return useAction(false);
    }

    /**
     * Consuma un'azione valida per il tipo richiesto.
     *
     * <p>Per un uso non magico viene consumata prima l'azione aggiuntiva limitata:
     * così l'azione ordinaria resta disponibile per un'eventuale azione di Magia.</p>
     */
    public TurnBudget useAction(boolean magicAction) {
        boolean useAdditional = !magicAction && additionalActionAvailable && additionalActionMagicRestricted;
        if (!useAdditional && !actionAvailable) {
            if (magicAction || !additionalActionAvailable) {
                throw new IllegalStateException("Action already spent");
            }
            useAdditional = true;
        }
        return new TurnBudget(
                movementAllowanceFeet,
                movementSpentFeet,
                useAdditional ? actionAvailable : false,
                bonusActionAvailable,
                reactionAvailable,
                objectInteractionAvailable,
                attacksRemaining,
                spellSlotSpentThisTurn,
                useAdditional ? false : additionalActionAvailable,
                useAdditional ? false : additionalActionMagicRestricted,
                actionSurgeUsedThisTurn,
                attackActionInProgress);
    }

    public boolean canUseAction(boolean magicAction) {
        return actionAvailable || (!magicAction && additionalActionAvailable);
    }

    /** Concede l'azione di Azione impetuosa; il limite di una volta per turno vive qui. */
    public TurnBudget grantNonMagicAction() {
        if (actionSurgeUsedThisTurn) {
            throw new IllegalStateException("Action Surge was already used this turn");
        }
        if (additionalActionAvailable) {
            throw new IllegalStateException("An additional action is already available");
        }
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, actionAvailable, bonusActionAvailable,
                reactionAvailable, objectInteractionAvailable, attacksRemaining, spellSlotSpentThisTurn,
                true, true, true, attackActionInProgress);
    }

    public TurnBudget useBonusAction() {
        if (!bonusActionAvailable) throw new IllegalStateException("Bonus action already spent");
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, actionAvailable, false,
                reactionAvailable, objectInteractionAvailable, attacksRemaining, spellSlotSpentThisTurn,
                additionalActionAvailable, additionalActionMagicRestricted, actionSurgeUsedThisTurn,
                attackActionInProgress);
    }

    public TurnBudget useReaction() {
        if (!reactionAvailable) throw new IllegalStateException("Reaction already spent");
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, actionAvailable, bonusActionAvailable,
                false, objectInteractionAvailable, attacksRemaining, spellSlotSpentThisTurn,
                additionalActionAvailable, additionalActionMagicRestricted, actionSurgeUsedThisTurn,
                attackActionInProgress);
    }

    public TurnBudget useObjectInteraction() {
        if (!objectInteractionAvailable) throw new IllegalStateException("Object interaction already spent");
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, actionAvailable, bonusActionAvailable,
                reactionAvailable, false, attacksRemaining, spellSlotSpentThisTurn,
                additionalActionAvailable, additionalActionMagicRestricted, actionSurgeUsedThisTurn,
                attackActionInProgress);
    }

    public TurnBudget useAttack() {
        if (attacksRemaining == 0) throw new IllegalStateException("No attacks remain in the Attack action");
        int remaining = attacksRemaining - 1;
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, actionAvailable, bonusActionAvailable,
                reactionAvailable, objectInteractionAvailable, remaining, spellSlotSpentThisTurn,
                additionalActionAvailable, additionalActionMagicRestricted, actionSurgeUsedThisTurn,
                attackActionInProgress && remaining > 0);
    }

    /** Avvia una nuova azione di Attacco e ne consuma immediatamente il primo attacco. */
    public TurnBudget startAttackAction(int attacksPerAction) {
        if (attacksPerAction < 1) {
            throw new IllegalArgumentException("attacksPerAction must be at least 1");
        }
        if (attackActionInProgress) {
            throw new IllegalStateException("An Attack action is already in progress");
        }
        TurnBudget paid = useAction(false);
        int remaining = attacksPerAction - 1;
        return new TurnBudget(paid.movementAllowanceFeet, paid.movementSpentFeet, paid.actionAvailable,
                paid.bonusActionAvailable, paid.reactionAvailable, paid.objectInteractionAvailable, remaining,
                paid.spellSlotSpentThisTurn, paid.additionalActionAvailable,
                paid.additionalActionMagicRestricted, paid.actionSurgeUsedThisTurn, remaining > 0);
    }

    public TurnBudget markSpellSlotSpent() {
        if (spellSlotSpentThisTurn) throw new IllegalStateException("A spell slot was already spent this turn");
        return new TurnBudget(movementAllowanceFeet, movementSpentFeet, actionAvailable, bonusActionAvailable,
                reactionAvailable, objectInteractionAvailable, attacksRemaining, true,
                additionalActionAvailable, additionalActionMagicRestricted, actionSurgeUsedThisTurn,
                attackActionInProgress);
    }
}
