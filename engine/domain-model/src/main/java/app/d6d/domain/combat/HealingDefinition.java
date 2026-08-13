package app.d6d.domain.combat;

import java.util.Objects;

/** A healing amount and its allowed friendly target; exactly one amount form is populated. */
public record HealingDefinition(
        HealingTarget target,
        DiceExpression dice,
        Integer fixedAmount,
        HealingSlotScaling slotScaling) {

    public HealingDefinition {
        Objects.requireNonNull(target, "target");
        if ((dice == null) == (fixedAmount == null)) {
            throw new IllegalArgumentException("Specify either healing dice or a fixed healing amount");
        }
        if (fixedAmount != null && fixedAmount <= 0) {
            throw new IllegalArgumentException("Fixed healing must be positive");
        }
        if (slotScaling != null && dice == null) {
            throw new IllegalArgumentException("Spell-slot scaling requires healing dice");
        }
        if (slotScaling != null) {
            try {
                int additionalLevels = 9 - slotScaling.baseSlotLevel();
                int additionalDice = Math.multiplyExact(
                        additionalLevels,
                        slotScaling.additionalDicePerSlotLevel());
                int maximumDice = Math.addExact(dice.count(), additionalDice);
                // DiceExpression owns the global dice-count limit. Constructing the
                // ninth-level formula here makes every legal slot total at runtime,
                // so planning can never discover an overflow halfway through a group.
                new DiceExpression(maximumDice, dice.sides(), dice.modifier());
            } catch (ArithmeticException failure) {
                throw new IllegalArgumentException("Spell-slot healing scaling is too large", failure);
            }
        }
    }

    /** Backward-compatible constructor matching the pre-scaling record shape. */
    public HealingDefinition(HealingTarget target, DiceExpression dice, Integer fixedAmount) {
        this(target, dice, fixedAmount, null);
    }

    public static HealingDefinition dice(HealingTarget target, DiceExpression dice) {
        return new HealingDefinition(target, Objects.requireNonNull(dice, "dice"), null, null);
    }

    public static HealingDefinition dice(
            HealingTarget target,
            DiceExpression dice,
            HealingSlotScaling slotScaling) {
        return new HealingDefinition(
                target,
                Objects.requireNonNull(dice, "dice"),
                null,
                Objects.requireNonNull(slotScaling, "slotScaling"));
    }

    public static HealingDefinition fixed(HealingTarget target, int amount) {
        return new HealingDefinition(target, null, amount, null);
    }

    public boolean usesDice() {
        return dice != null;
    }

    public boolean scalesWithSlot() {
        return slotScaling != null;
    }

    /** Resolves this formula for a selected slot and removes the now-consumed scaling metadata. */
    public HealingDefinition resolveAtSlotLevel(int slotLevel) {
        if (slotScaling == null) {
            throw new IllegalStateException("Healing does not scale with spell-slot level");
        }
        if (slotLevel < slotScaling.baseSlotLevel() || slotLevel > 9) {
            throw new IllegalArgumentException(
                    "Spell-slot level must be between " + slotScaling.baseSlotLevel() + " and 9");
        }
        int additionalLevels = slotLevel - slotScaling.baseSlotLevel();
        int additionalDice = Math.multiplyExact(
                additionalLevels,
                slotScaling.additionalDicePerSlotLevel());
        int resolvedCount = Math.addExact(dice.count(), additionalDice);
        return dice(target, new DiceExpression(resolvedCount, dice.sides(), dice.modifier()));
    }
}
