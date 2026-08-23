package app.d6d.domain.space;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridLineTraversalTest {

    @Test
    void laDestinazioneBloccataRestaRaggiungibileMaIlMuroDietroLaInterrompe() {
        Set<GridPosition> blocked = Set.of(new GridPosition(2, 0));

        assertTrue(clear(0, 0, 2, 0, blocked));
        assertFalse(clear(0, 0, 3, 0, blocked));
    }

    @Test
    void unAngoloChiusoInterrompeLaDiagonale() {
        Set<GridPosition> blocked = Set.of(new GridPosition(1, 0), new GridPosition(0, 1));

        assertFalse(clear(0, 0, 1, 1, blocked));
        assertTrue(clear(1, 1, 0, 0, Set.of()));
    }

    private boolean clear(
            int sourceColumn,
            int sourceRow,
            int targetColumn,
            int targetRow,
            Set<GridPosition> blocked) {
        return GridLineTraversal.clear(
                sourceColumn, sourceRow, targetColumn, targetRow,
                (column, row) -> blocked.contains(new GridPosition(column, row)));
    }
}
