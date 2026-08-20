package app.d6d.board;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardDocumentTest {

    @Test
    void ilDocumentoCopiaLeListeEImpedisceIdDuplicati() {
        List<BoardObject> source = new ArrayList<>();
        source.add(new Label("uno", new GridPoint(2.5, 3.5), "Sala", 0xffccaa44, 14, 0));
        BoardDocument document = new BoardDocument(1, source, BoardLayers.defaults(), FogMask.empty(20, 15));
        source.clear();

        assertEquals(1, document.objects().size());
        assertThrows(UnsupportedOperationException.class, () -> document.objects().clear());
        assertThrows(IllegalArgumentException.class, () -> new BoardDocument(
                1,
                List.of(document.objects().get(0), new StaticStamp("uno", new GridPoint(1, 1), StampKind.DOOR, 1, 0, 0)),
                BoardLayers.defaults(),
                FogMask.empty(20, 15)));
    }

    @Test
    void coordinateETestiNonFidatiSonoLimitati() {
        assertThrows(IllegalArgumentException.class, () -> new GridPoint(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new GridPoint(BoardLimits.MAX_WORLD_COORDINATE + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Label(
                "testo", new GridPoint(0, 0), "x".repeat(BoardLimits.MAX_LABEL_LENGTH + 1), 0, 14, 0));
        assertThrows(IllegalArgumentException.class, () -> new SceneToken(
                "token", "x".repeat(BoardLimits.MAX_TOKEN_NAME_LENGTH + 1), TokenCategory.OTHER,
                new GridPoint(0, 0), 1, 0, 0, "", true, true, ""));
        assertThrows(IllegalArgumentException.class, () -> new SceneToken(
                "token", "Trappola", TokenCategory.TRAP, new GridPoint(0, 0),
                BoardLimits.MIN_TOKEN_SIZE_SQUARES / 2, 0, 0, "", true, false, ""));
        assertThrows(IllegalArgumentException.class, () -> new SceneToken(
                "token", "Trappola", TokenCategory.TRAP, new GridPoint(0, 0), 1, 0, 0, "", true,
                false, "x".repeat(BoardLimits.MAX_TOKEN_NOTES_LENGTH + 1)));
    }

    @Test
    void laPedinaDiScenaSiSpostaSenzaPerdereMetadati() {
        SceneToken source = new SceneToken(
                "token", "Carro", TokenCategory.VEHICLE, new GridPoint(2.5, 3.5),
                2, 15, 0xffcc8844, "scene-image", true, true,
                true, TokenLootCategory.MISC, 3, "Tre casse di provviste", "Copertura illustrativa");

        SceneToken moved = source.translated(3, -1);

        assertEquals(new GridPoint(5.5, 2.5), moved.position());
        assertEquals(source.name(), moved.name());
        assertEquals(source.category(), moved.category());
        assertEquals(source.imageAssetId(), moved.imageAssetId());
        assertEquals(source.lootable(), moved.lootable());
        assertEquals(source.lootCategory(), moved.lootCategory());
        assertEquals(source.lootQuantity(), moved.lootQuantity());
        assertEquals(source.lootDescription(), moved.lootDescription());
        assertEquals(source.notes(), moved.notes());
    }

    @Test
    void iMetadatiLootNonFidatiSonoLimitati() {
        assertThrows(IllegalArgumentException.class, () -> new SceneToken(
                "token", "Fiale", TokenCategory.LOOT, new GridPoint(0, 0), 1, 0, 0, "", true, true,
                true, TokenLootCategory.POTION, 0, "", ""));
        assertThrows(IllegalArgumentException.class, () -> new SceneToken(
                "token", "Fiale", TokenCategory.LOOT, new GridPoint(0, 0), 1, 0, 0, "", true, true,
                true, TokenLootCategory.POTION, 1,
                "x".repeat(BoardLimits.MAX_TOKEN_LOOT_DESCRIPTION_LENGTH + 1), ""));
    }

    @Test
    void laNebbiaCompattaConservaLeCaselleDopoUnResize() {
        FogMask mask = FogMask.empty(4, 3)
                .withCell(1, 1, true)
                .withCell(3, 2, true);

        FogMask smaller = mask.resized(3, 2);
        FogMask restored = smaller.resized(4, 3);

        assertTrue(smaller.covered(1, 1));
        assertFalse(smaller.covered(3, 2));
        assertTrue(restored.covered(1, 1));
        assertFalse(restored.covered(3, 2));
        assertTrue(mask.covered(3, 2), "la fotografia originale resta immutabile");
    }

    @Test
    void gliOggettiFuoriDallaGrigliaRestanoNelDocumento() {
        InkStroke stroke = new InkStroke(
                "fuori", List.of(new GridPoint(20.5, 4.5), new GridPoint(25.5, 4.5)), 0xffffffff, 0.1);
        BoardDocument document = BoardDocument.empty().withObjects(List.of(stroke));

        assertEquals(stroke, document.objects().get(0));
        assertFalse(stroke.bounds(5).intersects(new BoardBounds(0, 0, 10, 10)));
    }
}
