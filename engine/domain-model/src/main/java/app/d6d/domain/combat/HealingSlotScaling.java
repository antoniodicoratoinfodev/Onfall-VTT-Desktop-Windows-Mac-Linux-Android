package app.d6d.domain.combat;

/** Additional healing dice granted for every spell-slot level above the base level. */
public record HealingSlotScaling(int baseSlotLevel, int additionalDicePerSlotLevel) {
    public HealingSlotScaling {
        if (baseSlotLevel < 1 || baseSlotLevel > 9) {
            throw new IllegalArgumentException("Base spell-slot level must be between 1 and 9");
        }
        if (additionalDicePerSlotLevel <= 0) {
            throw new IllegalArgumentException("Additional healing dice per slot level must be positive");
        }
    }
}
