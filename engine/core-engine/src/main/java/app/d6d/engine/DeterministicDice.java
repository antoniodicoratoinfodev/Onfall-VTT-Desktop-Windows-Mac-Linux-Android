package app.d6d.engine;

import app.d6d.domain.combat.D20Mode;
import app.d6d.domain.combat.DiceExpression;
import app.d6d.domain.combat.DiceRollResult;

import java.util.ArrayList;
import java.util.List;

/** Small SplitMix64-backed generator whose exact state is safe to snapshot for undo/replay. */
public final class DeterministicDice {
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private final long seed;
    private long state;

    public DeterministicDice(long seed) {
        this(seed, seed);
    }

    private DeterministicDice(long seed, long state) {
        this.seed = seed;
        this.state = state;
    }

    public static DeterministicDice fromState(long seed, long state) {
        return new DeterministicDice(seed, state);
    }

    public long seed() {
        return seed;
    }

    public long state() {
        return state;
    }

    public void restore(long savedState) {
        state = savedState;
    }

    public int roll(int sides) {
        if (sides <= 1) throw new IllegalArgumentException("A die needs at least two sides");
        long value = nextLong();
        return 1 + (int) Long.remainderUnsigned(value, sides);
    }

    public List<Integer> rollD20(D20Mode mode) {
        if (mode == null) throw new NullPointerException("mode");
        if (mode == D20Mode.NORMAL) return List.of(roll(20));
        return List.of(roll(20), roll(20));
    }

    public DiceRollResult roll(DiceExpression expression, boolean critical) {
        if (expression == null) throw new NullPointerException("expression");
        int count = Math.multiplyExact(expression.count(), critical ? 2 : 1);
        List<Integer> results = new ArrayList<>(count);
        long total = expression.modifier();
        for (int index = 0; index < count; index++) {
            int value = roll(expression.sides());
            results.add(value);
            total = Math.addExact(total, value);
        }
        if (total < Integer.MIN_VALUE || total > Integer.MAX_VALUE) {
            throw new ArithmeticException("Dice total exceeds the supported range");
        }
        return new DiceRollResult(results, expression.modifier(), (int) total);
    }

    private long nextLong() {
        long z = state += GAMMA;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
