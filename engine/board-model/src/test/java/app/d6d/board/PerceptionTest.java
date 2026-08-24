package app.d6d.board;

import app.d6d.domain.space.GridPosition;
import app.d6d.domain.space.TokenPlacement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chi si accorge di chi.
 *
 * <p>È la stessa vista della nebbia, chiesta su una coppia sola: se le due
 * risposte divergessero, una creatura potrebbe restare immobile mentre i
 * personaggi la guardano, o svegliarsi per qualcuno che il muro nasconde.</p>
 */
class PerceptionTest {

    private static final int COLUMNS = 21;
    private static final int ROWS = 21;

    private WallMask walls(int... columnRowPairs) {
        WallMask mask = WallMask.empty(COLUMNS, ROWS);
        for (int index = 0; index < columnRowPairs.length; index += 2) {
            mask = mask.withCell(columnRowPairs[index], columnRowPairs[index + 1], true);
        }
        return mask;
    }

    private TokenPlacement at(int column, int row) {
        return TokenPlacement.single("token", new GridPosition(column, row));
    }

    @Test
    void ilRaggioSiMisuraInChebyshevComeQuelloDellaNebbia() {
        WallMask empty = walls();
        TokenPlacement eye = at(10, 10);

        assertTrue(Perception.sees(empty, eye, 3, at(13, 10)), "bordo ortogonale");
        // La diagonale piena vale un passo: resta dentro il quadrato del raggio.
        assertTrue(Perception.sees(empty, eye, 3, at(13, 13)), "angolo del quadrato");
        assertFalse(Perception.sees(empty, eye, 3, at(14, 10)), "una casella oltre");
    }

    @Test
    void unMuroInterrompeLaLinea() {
        WallMask wall = walls(12, 10);

        assertFalse(Perception.sees(wall, at(10, 10), 6, at(14, 10)));
        // Il muro stesso si vede: è la parete davanti, non ciò che c'è dietro.
        assertTrue(Perception.sees(wall, at(10, 10), 6, at(12, 10)));
    }

    @Test
    void raggioZeroNonVedeNemmenoChiGliStaAddosso() {
        assertFalse(Perception.sees(walls(), at(10, 10), 0, at(10, 11)));
    }

    @Test
    void unaCreaturaGrandeGuardaDaTutteLeSueCaselleEdESuBastaUnAngolo() {
        WallMask wall = walls(11, 9);
        TokenPlacement large = new TokenPlacement("grande", new GridPosition(9, 9), 2);

        // Dalla riga alta il muro chiude; da quella bassa no, e una sola casella
        // che vede basta: la creatura sporge dall'angolo.
        assertFalse(VisionField.clearLine(wall, 10, 9, 13, 10));
        assertTrue(Perception.sees(wall, large, 4, at(13, 10)));
        // Lo stesso al contrario: si vede la parte scoperta, non il centro.
        assertTrue(Perception.sees(wall, at(13, 10), 4, large));
    }

    @Test
    void laVistaNonEsimmetricaQuandoIRaggiSonoDiversi() {
        WallMask empty = walls();
        TokenPlacement guard = at(10, 10);
        TokenPlacement scout = at(10, 18);

        // Otto caselle: la guardia con raggio corto non lo vede, lui sì.
        assertFalse(Perception.sees(empty, guard, 4, scout));
        assertTrue(Perception.sees(empty, scout, 12, guard));
    }
}
