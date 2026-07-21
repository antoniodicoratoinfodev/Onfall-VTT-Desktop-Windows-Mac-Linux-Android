package app.d6d.domain.combat;

/** A conventional NdS+M expression. The modifier is never doubled by a critical hit. */
public record DiceExpression(int count, int sides, int modifier) {
    private static final int MAX_DICE = 10_000;
    private static final int MAX_SIDES = 1_000_000;

    public DiceExpression {
        if (count <= 0) {
            throw new IllegalArgumentException("Dice count must be positive");
        }
        if (count > MAX_DICE) {
            throw new IllegalArgumentException("Dice count exceeds the supported limit of " + MAX_DICE);
        }
        if (sides <= 1) {
            throw new IllegalArgumentException("A die needs at least two sides");
        }
        if (sides > MAX_SIDES) {
            throw new IllegalArgumentException("Die sides exceed the supported limit of " + MAX_SIDES);
        }
    }

    public static DiceExpression of(int count, int sides) {
        return new DiceExpression(count, sides, 0);
    }

    public String notation() {
        return count + "d" + sides + (modifier == 0 ? "" : modifier > 0 ? "+" + modifier : String.valueOf(modifier));
    }
}
