package app.d6d.domain.space;

import app.d6d.rules.model.CompiledRuleset;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardGeometryTest {

    @Test
    void squareSupportsSrdUniformThreePointFiveAlternatingAndEuclideanDistance() {
        BoardGeometry.Coordinate start = new BoardGeometry.Coordinate(0, 0);
        BoardGeometry.Coordinate end = new BoardGeometry.Coordinate(4, 3);

        assertEquals(new BigDecimal("20"), square(CompiledRuleset.DiagonalRule.UNIFORM).distance(start, end).orElseThrow());
        assertEquals(new BigDecimal("25"), square(CompiledRuleset.DiagonalRule.FIVE_TEN_FIVE).distance(start, end).orElseThrow());
        assertEquals(new BigDecimal("25"), square(CompiledRuleset.DiagonalRule.EUCLIDEAN).distance(start, end).orElseThrow());
    }

    @Test
    void hexGridlessTheatreAndFootprintsHaveExplicitSemantics() {
        BoardGeometry hex = new BoardGeometry(CompiledRuleset.BoardTopology.HEX_POINTY,
                CompiledRuleset.DiagonalRule.UNIFORM, BigDecimal.ONE, "hex", false, true);
        BoardGeometry gridless = new BoardGeometry(CompiledRuleset.BoardTopology.GRIDLESS,
                CompiledRuleset.DiagonalRule.EUCLIDEAN, new BigDecimal("2"), "m", true, false);
        BoardGeometry theatre = new BoardGeometry(CompiledRuleset.BoardTopology.THEATRE_OF_MIND,
                CompiledRuleset.DiagonalRule.MANUAL, BigDecimal.ONE, "zone", false, false);

        assertEquals(new BigDecimal("2"), hex.distance(
                new BoardGeometry.Coordinate(0, 0), new BoardGeometry.Coordinate(2, -1)).orElseThrow());
        assertEquals(6, hex.neighbours(new BoardGeometry.Coordinate(0, 0)).size());
        assertEquals(7, hex.footprint(new BoardGeometry.Coordinate(0, 0), 2).size());
        assertEquals(new BigDecimal("10"), gridless.distance(
                new BoardGeometry.Coordinate(0, 0, 0), new BoardGeometry.Coordinate(3, 4, 0)).orElseThrow());
        assertTrue(theatre.distance(new BoardGeometry.Coordinate(0, 0), new BoardGeometry.Coordinate(1, 1)).isEmpty());
    }

    private static BoardGeometry square(CompiledRuleset.DiagonalRule diagonal) {
        return new BoardGeometry(CompiledRuleset.BoardTopology.SQUARE, diagonal,
                new BigDecimal("5"), "ft", false, true);
    }
}
