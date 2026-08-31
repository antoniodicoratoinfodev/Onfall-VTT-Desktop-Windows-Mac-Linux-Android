package app.d6d.domain.space;

import app.d6d.rules.model.CompiledRuleset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Geometria e unita' dichiarate dal regolamento, separata dalla griglia SRD legacy.
 *
 * <p>Le coordinate HEX sono assiali (q,r). GRIDLESS usa coordinate continue;
 * THEATRE_OF_MIND e una diagonale MANUAL non fingono una distanza automatica.</p>
 */
public record BoardGeometry(
        CompiledRuleset.BoardTopology topology,
        CompiledRuleset.DiagonalRule diagonalRule,
        BigDecimal unitsPerCell,
        String canonicalUnit,
        boolean elevation,
        boolean occupancyRequired) {

    public record Coordinate(double first, double second, double elevation) {
        public Coordinate {
            if (!Double.isFinite(first) || !Double.isFinite(second) || !Double.isFinite(elevation)) {
                throw new IllegalArgumentException("Board coordinates must be finite");
            }
        }

        public Coordinate(double first, double second) {
            this(first, second, 0);
        }
    }

    public BoardGeometry {
        topology = Objects.requireNonNull(topology, "topology");
        diagonalRule = Objects.requireNonNull(diagonalRule, "diagonalRule");
        unitsPerCell = plain(Objects.requireNonNull(unitsPerCell, "unitsPerCell"));
        if (unitsPerCell.signum() <= 0) throw new IllegalArgumentException("unitsPerCell must be positive");
        canonicalUnit = canonicalUnit == null || canonicalUnit.isBlank() ? "unit" : canonicalUnit.trim();
    }

    public static BoardGeometry from(CompiledRuleset.MovementDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new BoardGeometry(definition.topology(), definition.diagonalRule(),
                definition.unitsPerCell(), definition.canonicalUnit(),
                definition.elevation(), definition.occupancyRequired());
    }

    /** Adattatore esplicito per le sessioni tattiche SRD esistenti. */
    public static BoardGeometry legacySquare(MapGrid grid) {
        Objects.requireNonNull(grid, "grid");
        return new BoardGeometry(CompiledRuleset.BoardTopology.SQUARE,
                CompiledRuleset.DiagonalRule.UNIFORM,
                BigDecimal.valueOf(grid.feetPerSquare()), "ft", false, true);
    }

    public Optional<BigDecimal> distance(Coordinate from, Coordinate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (topology == CompiledRuleset.BoardTopology.THEATRE_OF_MIND
                || diagonalRule == CompiledRuleset.DiagonalRule.MANUAL) {
            return Optional.empty();
        }
        double dx = Math.abs(to.first() - from.first());
        double dy = Math.abs(to.second() - from.second());
        double horizontal = switch (topology) {
            case SQUARE -> squareDistance(dx, dy);
            case HEX_POINTY, HEX_FLAT -> (dx + dy + Math.abs((to.first() - from.first())
                    + (to.second() - from.second()))) / 2d;
            case GRIDLESS -> Math.hypot(dx, dy);
            case THEATRE_OF_MIND -> throw new IllegalStateException("handled above");
        };
        double cells = elevation
                ? Math.hypot(horizontal, Math.abs(to.elevation() - from.elevation()))
                : horizontal;
        return Optional.of(plain(BigDecimal.valueOf(cells).multiply(unitsPerCell)
                .setScale(6, RoundingMode.HALF_UP)));
    }

    private double squareDistance(double dx, double dy) {
        double diagonal = Math.min(dx, dy);
        double straight = Math.max(dx, dy) - diagonal;
        return switch (diagonalRule) {
            case UNIFORM -> Math.max(dx, dy);
            case FIVE_TEN_FIVE -> straight + diagonal + Math.floor(diagonal / 2d);
            case EUCLIDEAN -> Math.hypot(dx, dy);
            case MANUAL -> throw new IllegalStateException("handled before distance calculation");
        };
    }

    /** Celle adiacenti soltanto per geometrie discrete. */
    public List<Coordinate> neighbours(Coordinate origin) {
        Objects.requireNonNull(origin, "origin");
        int[][] offsets = switch (topology) {
            case SQUARE -> new int[][] {{-1,-1},{0,-1},{1,-1},{-1,0},{1,0},{-1,1},{0,1},{1,1}};
            case HEX_POINTY, HEX_FLAT -> new int[][] {{1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1}};
            case GRIDLESS, THEATRE_OF_MIND -> new int[0][0];
        };
        ArrayList<Coordinate> result = new ArrayList<>(offsets.length);
        for (int[] offset : offsets) {
            result.add(new Coordinate(origin.first() + offset[0], origin.second() + offset[1], origin.elevation()));
        }
        return List.copyOf(result);
    }

    /**
     * Ingombro discreto: lato N sui quadrati, raggio N-1 sugli esagoni.
     * Le geometrie continue non impongono celle e conservano soltanto l'origine.
     */
    public List<Coordinate> footprint(Coordinate origin, int size) {
        Objects.requireNonNull(origin, "origin");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        LinkedHashSet<Coordinate> result = new LinkedHashSet<>();
        if (topology == CompiledRuleset.BoardTopology.SQUARE) {
            for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
                result.add(new Coordinate(origin.first() + x, origin.second() + y, origin.elevation()));
            }
        } else if (topology == CompiledRuleset.BoardTopology.HEX_POINTY
                || topology == CompiledRuleset.BoardTopology.HEX_FLAT) {
            int radius = size - 1;
            for (int q = -radius; q <= radius; q++) {
                int minR = Math.max(-radius, -q - radius);
                int maxR = Math.min(radius, -q + radius);
                for (int r = minR; r <= maxR; r++) {
                    result.add(new Coordinate(origin.first() + q, origin.second() + r, origin.elevation()));
                }
            }
        } else {
            result.add(origin);
        }
        return List.copyOf(result);
    }

    private static BigDecimal plain(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }
}
