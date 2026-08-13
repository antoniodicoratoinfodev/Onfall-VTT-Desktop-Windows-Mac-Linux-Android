package app.d6d.domain.space;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Geometria della griglia: distanze, ingombri e occupazione. */
class BattleMapTest {

    private static final MapGrid GRID = MapGrid.standard(20, 15);

    @Test
    void unaDiagonaleContaComeUnaCasella() {
        // Regola su griglia 2024: si contano le caselle, la diagonale vale uno.
        assertEquals(1, new GridPosition(0, 0).squaresTo(new GridPosition(1, 1)));
        assertEquals(3, new GridPosition(0, 0).squaresTo(new GridPosition(3, 3)));
        assertEquals(4, new GridPosition(0, 0).squaresTo(new GridPosition(4, 2)));
    }

    @Test
    void unaCasellaValeCinquePiediDiPredefinito() {
        assertEquals(MapGrid.STANDARD_FEET_PER_SQUARE, GRID.feetPerSquare());
        assertEquals(30, GRID.feetFor(6));
        assertEquals(6, GRID.squaresFor(30));
    }

    @Test
    void unaDistanzaNonMultiplaSiArrotondaPerEccesso() {
        // Una gittata di 12 piedi non si copre con due caselle da cinque.
        assertEquals(3, GRID.squaresFor(12));
    }

    @Test
    void laScalaEConfigurabilePerMappeGrandi() {
        MapGrid campale = new MapGrid(60, 40, 20);

        assertEquals(200, campale.feetFor(10));
        assertEquals(5, campale.squaresFor(100));
    }

    @Test
    void unaCreaturaGrandeOccupaQuattroCaselle() {
        TokenPlacement grande = new TokenPlacement("orco", new GridPosition(2, 2), 2);

        assertEquals(4, grande.occupiedSquares().size());
        assertTrue(grande.occupies(new GridPosition(3, 3)));
        assertFalse(grande.occupies(new GridPosition(4, 2)));
    }

    @Test
    void laDistanzaSiMisuraFraIBordiNonDallOrigine() {
        // Creatura Grande in 2,2-3,3; il bersaglio e' adiacente al suo bordo destro.
        TokenPlacement grande = new TokenPlacement("orco", new GridPosition(2, 2), 2);
        TokenPlacement piccolo = TokenPlacement.single("ladro", new GridPosition(4, 3));

        // Adiacente significa una casella: cinque piedi, non di piu'.
        assertEquals(1, grande.squaresTo(piccolo));
    }

    @Test
    void unaCreaturaAdiacenteEEntroCinquePiedi() {
        BattleMap map = BattleMap.none()
                .withGrid(GRID)
                .withPlacement(TokenPlacement.single("a", new GridPosition(5, 5)))
                .withPlacement(TokenPlacement.single("b", new GridPosition(6, 6)));

        assertEquals(5, map.distanceFeet("a", "b").orElseThrow());
    }

    @Test
    void senzaPosizionamentoNonSiDichiaraUnaDistanza() {
        BattleMap map = BattleMap.none()
                .withGrid(GRID)
                .withPlacement(TokenPlacement.single("a", new GridPosition(1, 1)));

        // Il documento vieta di dichiarare distanze esatte senza coordinate complete.
        assertTrue(map.distanceFeet("a", "assente").isEmpty());
    }

    @Test
    void dueSegnapostiNonPossonoSovrapporsi() {
        BattleMap map = BattleMap.none()
                .withGrid(GRID)
                .withPlacement(TokenPlacement.single("a", new GridPosition(5, 5)));

        assertFalse(map.isFree(TokenPlacement.single("b", new GridPosition(5, 5))));
        assertTrue(map.isFree(TokenPlacement.single("b", new GridPosition(6, 5))));
        // Spostare se stessi sulla propria casella resta lecito.
        assertTrue(map.isFree(TokenPlacement.single("a", new GridPosition(5, 5))));
    }

    @Test
    void unaCreaturaGrandeCollideConTuttoIlProprioIngombro() {
        BattleMap map = BattleMap.none()
                .withGrid(GRID)
                .withPlacement(new TokenPlacement("orco", new GridPosition(2, 2), 2));

        assertFalse(map.isFree(TokenPlacement.single("ladro", new GridPosition(3, 3))));
        assertTrue(map.isFree(TokenPlacement.single("ladro", new GridPosition(4, 4))));
    }

    @Test
    void unSegnapostoNonPuoUscireDallaGriglia() {
        BattleMap map = BattleMap.none().withGrid(MapGrid.standard(5, 5));

        assertFalse(map.fitsInsideGrid(new TokenPlacement("enorme", new GridPosition(4, 4), 3)));
        assertTrue(map.fitsInsideGrid(new TokenPlacement("enorme", new GridPosition(1, 1), 3)));
    }

    @Test
    void restringereLaGrigliaRimuoveISegnapostiRimastiFuori() {
        BattleMap map = BattleMap.none()
                .withGrid(MapGrid.standard(20, 20))
                .withPlacement(TokenPlacement.single("vicino", new GridPosition(1, 1)))
                .withPlacement(TokenPlacement.single("lontano", new GridPosition(18, 18)));

        BattleMap ridotta = map.withGrid(MapGrid.standard(10, 10));

        assertTrue(ridotta.isPlaced("vicino"));
        assertFalse(ridotta.isPlaced("lontano"));
    }

    @Test
    void siRisaleAlCombattenteDaUnaCasella() {
        BattleMap map = BattleMap.none()
                .withGrid(GRID)
                .withPlacement(new TokenPlacement("orco", new GridPosition(3, 3), 2));

        assertEquals("orco", map.occupantAt(new GridPosition(4, 4)).orElseThrow());
        assertTrue(map.occupantAt(new GridPosition(9, 9)).isEmpty());
    }

    @Test
    void unaMappaNonConfigurataRestaAstratta() {
        BattleMap map = BattleMap.none();

        assertFalse(map.configured());
        assertFalse(map.grid().configured());
    }

    @Test
    void iSegnapostiHannoUnOrdineCanonicoIndipendenteDallaMappaSorgente() {
        Map<String, TokenPlacement> first = new HashMap<>();
        first.put("zeta", TokenPlacement.single("zeta", new GridPosition(1, 1)));
        first.put("alfa", TokenPlacement.single("alfa", new GridPosition(2, 1)));
        first.put("medio", TokenPlacement.single("medio", new GridPosition(3, 1)));
        Map<String, TokenPlacement> second = new LinkedHashMap<>();
        second.put("medio", TokenPlacement.single("medio", new GridPosition(3, 1)));
        second.put("zeta", TokenPlacement.single("zeta", new GridPosition(1, 1)));
        second.put("alfa", TokenPlacement.single("alfa", new GridPosition(2, 1)));

        BattleMap fromHashMap = new BattleMap(GRID, first, "", MapBackground.UNSET);
        BattleMap fromDifferentOrder = new BattleMap(GRID, second, "", MapBackground.UNSET);
        List<String> expected = List.of("alfa", "medio", "zeta");

        assertEquals(expected, fromHashMap.orderedPlacements().stream()
                .map(TokenPlacement::combatantId).toList());
        assertEquals(expected, fromDifferentOrder.orderedPlacements().stream()
                .map(TokenPlacement::combatantId).toList());
    }

    @Test
    void unaGrigliaConUnSoloLatoVieneRifiutata() {
        assertThrows(IllegalArgumentException.class, () -> new MapGrid(10, 0, 5));
    }

    @Test
    void unaCasellaDeveCoprireUnaDistanzaPositiva() {
        assertThrows(IllegalArgumentException.class, () -> new MapGrid(10, 10, 0));
    }
}
