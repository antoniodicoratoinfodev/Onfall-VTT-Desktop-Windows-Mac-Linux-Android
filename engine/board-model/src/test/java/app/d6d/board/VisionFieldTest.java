package app.d6d.board;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Il campo visivo della nebbia dinamica.
 *
 * <p>Sbagliarlo si vede subito al tavolo in due modi opposti e ugualmente gravi:
 * mostrare ai giocatori una stanza che il personaggio non può vedere, oppure
 * nascondergli un nemico che il motore gli lascia comunque colpire.</p>
 */
class VisionFieldTest {

    private static final int COLUMNS = 21;
    private static final int ROWS = 21;

    private WallMask walls(int... columnRowPairs) {
        WallMask mask = WallMask.empty(COLUMNS, ROWS);
        for (int index = 0; index < columnRowPairs.length; index += 2) {
            mask = mask.withCell(columnRowPairs[index], columnRowPairs[index + 1], true);
        }
        return mask;
    }

    private boolean visible(boolean[] field, int column, int row) {
        return field[row * COLUMNS + column];
    }

    @Test
    void senzaMuriSiVedeIlQuadratoDiChebyshevEnonUnCerchio() {
        boolean[] field = VisionField.visibleFrom(walls(), COLUMNS, ROWS, 10, 10, 3);

        assertTrue(visible(field, 10, 10), "la propria casella");
        assertTrue(visible(field, 13, 10), "bordo ortogonale del raggio");
        // La diagonale piena resta dentro: in Chebyshev vale quanto un passo dritto.
        assertTrue(visible(field, 13, 13), "angolo del quadrato");
        assertFalse(visible(field, 14, 10), "una casella oltre il raggio");
        assertFalse(visible(field, 14, 14), "oltre l'angolo");
    }

    @Test
    void raggioZeroSignificaCiecoENonScriveNemmenoLaPropriaCasella() {
        boolean[] field = VisionField.visibleFrom(walls(), COLUMNS, ROWS, 4, 4, 0);

        assertFalse(visible(field, 4, 4));
        assertFalse(visible(field, 5, 4));
        assertFalse(visible(field, 4, 5));
    }

    @Test
    void unMuroSiVedeMaNasconde_cioCheGliStaDietro() {
        // Muro verticale a colonna 12, l'occhio a 10,10.
        boolean[] field = VisionField.visibleFrom(walls(12, 9, 12, 10, 12, 11), COLUMNS, ROWS, 10, 10, 6);

        assertTrue(visible(field, 11, 10), "la casella prima del muro");
        assertTrue(visible(field, 12, 10), "la parete stessa: la si guarda in faccia");
        assertFalse(visible(field, 13, 10), "subito dietro la parete");
        assertFalse(visible(field, 14, 10), "e più in là");
        assertTrue(visible(field, 10, 14), "una direzione libera resta libera");
    }

    @Test
    void laDiagonalePassaSoloConEntrambiGliSpigoliLiberi() {
        // La regola è quella del motore, ed è volutamente severa: **una sola**
        // delle due caselle ortogonali basta a chiudere il passo diagonale. Serve
        // a non far sbirciare oltre lo spigolo di un muro, dove una freccia non
        // passerebbe.
        boolean[] oneCorner = VisionField.visibleFrom(walls(11, 10), COLUMNS, ROWS, 10, 10, 5);
        assertFalse(visible(oneCorner, 11, 11), "un solo spigolo chiude già la diagonale");

        boolean[] bothCorners = VisionField.visibleFrom(walls(11, 10, 10, 11), COLUMNS, ROWS, 10, 10, 5);
        assertFalse(visible(bothCorners, 11, 11), "a maggior ragione con entrambi");

        boolean[] open = VisionField.visibleFrom(walls(), COLUMNS, ROWS, 10, 10, 5);
        assertTrue(visible(open, 11, 11), "senza spigoli la diagonale passa");

        // Lo spigolo chiude la diagonale ma non la strada dritta che lo aggira.
        assertTrue(visible(oneCorner, 10, 11), "sotto");
        assertTrue(visible(oneCorner, 11, 12), "e oltre, passando da sotto");
    }

    @Test
    void laVistaDelGruppoEunioneDiQuelleDeiSuoiMembri() {
        WallMask barrier = walls(12, 9, 12, 10, 12, 11, 12, 12, 12, 13);
        boolean[] field = VisionField.blank(COLUMNS, ROWS);

        VisionField.addVisibleFrom(field, barrier, COLUMNS, ROWS, 10, 10, 6);
        assertFalse(visible(field, 14, 11), "da solo, il primo non vede oltre il muro");

        // Un secondo occhio già oltre la barriera apre ciò che il primo non vede.
        VisionField.addVisibleFrom(field, barrier, COLUMNS, ROWS, 15, 11, 3);
        assertTrue(visible(field, 14, 11), "l'unione somma, non interseca");
        assertTrue(visible(field, 10, 10), "e non toglie nulla al primo");
    }

    @Test
    void oltreIlBordoDellaMappaNonSiRompeNulla() {
        boolean[] field = VisionField.blank(COLUMNS, ROWS);
        VisionField.addVisibleFrom(field, walls(), COLUMNS, ROWS, 0, 0, 4);
        assertTrue(visible(field, 0, 0));
        assertTrue(visible(field, 4, 4));

        // Un'origine fuori griglia non è un errore: semplicemente non vede.
        boolean[] outside = VisionField.blank(COLUMNS, ROWS);
        VisionField.addVisibleFrom(outside, walls(), COLUMNS, ROWS, -1, 5, 4);
        for (boolean cell : outside) assertFalse(cell);
    }

    @Test
    void ilCampoDeveCombaciareConLaGriglia() {
        assertThrows(IllegalArgumentException.class, () -> VisionField.addVisibleFrom(
                new boolean[4], walls(), COLUMNS, ROWS, 1, 1, 2));
    }

    @Test
    void ilRaggioInCaselleArrotondaPerDifetto() {
        assertEquals(12, VisionField.radiusSquares(60, 5));
        assertEquals(3, VisionField.radiusSquares(35, 10));
        assertEquals(0, VisionField.radiusSquares(0, 5));
        assertEquals(0, VisionField.radiusSquares(60, 0));
    }

    @Test
    void laMemoriaAccumulaSenzaCopiareQuandoNonCambiaNulla() {
        ExploredMask explored = ExploredMask.empty(COLUMNS, ROWS);
        boolean[] field = VisionField.visibleFrom(walls(), COLUMNS, ROWS, 10, 10, 2);

        ExploredMask after = explored.withVisible(field);
        assertTrue(after.seen(10, 10));
        assertTrue(after.seen(12, 12));
        assertFalse(after.seen(0, 0));

        // Ripassare sullo stesso campo non produce un documento nuovo: il Lucido
        // non deve credere di avere modifiche da salvare a ogni cambio di turno.
        assertSame(after, after.withVisible(field));
    }

    @Test
    void laMemoriaRidimensionataConservaSoloLIntersezione() {
        ExploredMask explored = ExploredMask.empty(COLUMNS, ROWS)
                .withCell(3, 3, true)
                .withCell(18, 18, true);

        ExploredMask smaller = explored.resized(10, 10);
        assertTrue(smaller.seen(3, 3));
        assertFalse(smaller.seen(18, 18));
        assertEquals(10, smaller.columns());
    }

    @Test
    void ilRaggioPersonaleVinceSuQuelloDiMappa() {
        VisionSettings settings = VisionSettings.defaults()
                .withMode(VisionMode.DYNAMIC)
                .withRadiusFeet(60)
                .withOverride("nano", 120);

        assertEquals(120, settings.radiusFeetFor("nano"));
        assertEquals(60, settings.radiusFeetFor("elfo"));
        assertEquals(60, settings.radiusFeetFor(null));
        assertTrue(settings.dynamic());

        assertEquals(60, settings.withOverride("nano", null).radiusFeetFor("nano"));
        assertSame(settings, settings.withOverride("nano", 120), "nessuna modifica, nessuna copia");
    }

    @Test
    void iValoriFuoriScalaSonoRifiutati() {
        assertThrows(IllegalArgumentException.class, () -> VisionSettings.defaults().withRadiusFeet(-1));
        assertThrows(IllegalArgumentException.class, () ->
                VisionSettings.defaults().withRadiusFeet(BoardLimits.MAX_VISION_RADIUS_FEET + 1));
        assertThrows(IllegalArgumentException.class, () ->
                new VisionSettings(VisionMode.DYNAMIC, 60, Map.of("", 10)));
        assertThrows(IllegalArgumentException.class, () ->
                new VisionSettings(VisionMode.DYNAMIC, 60, Map.of("id", -5)));
    }
}
