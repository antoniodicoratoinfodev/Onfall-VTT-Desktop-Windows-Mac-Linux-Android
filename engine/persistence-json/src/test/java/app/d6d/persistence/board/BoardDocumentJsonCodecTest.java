package app.d6d.persistence.board;

import app.d6d.board.AreaTemplate;
import app.d6d.board.BoardDocument;
import app.d6d.board.BoardLayers;
import app.d6d.board.FogMask;
import app.d6d.board.GridPoint;
import app.d6d.board.InkStroke;
import app.d6d.board.Label;
import app.d6d.board.Measurement;
import app.d6d.board.SceneToken;
import app.d6d.board.StampKind;
import app.d6d.board.StaticStamp;
import app.d6d.board.TemplateShape;
import app.d6d.board.TokenCategory;
import app.d6d.board.TokenLootCategory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardDocumentJsonCodecTest {
    private final BoardDocumentJsonCodec codec = new BoardDocumentJsonCodec();

    @Test
    void roundTripDiOgniTipoOrdineELivelli() {
        FogMask fog = FogMask.empty(20, 15).withCell(1, 1, true).withCell(2, 1, true);
        BoardDocument source = new BoardDocument(
                1,
                List.of(
                        new InkStroke("ink", List.of(new GridPoint(1, 1), new GridPoint(2, 2)), 1, .1),
                        new Measurement("measure", List.of(new GridPoint(2.5, 2.5), new GridPoint(8.5, 7.5)), 2),
                        new AreaTemplate("area", TemplateShape.CONE, new GridPoint(4.5, 4.5),
                                new GridPoint(8.5, 4.5), 20, 5, 15, 3),
                        new Label("label", new GridPoint(6, 3), "Ingresso", 4, 16, -5),
                        new StaticStamp("stamp", new GridPoint(7, 7), StampKind.TREASURE, 1.5, 30, 5),
                        new SceneToken("token", "Mimic", TokenCategory.TRAP, new GridPoint(9.5, 8.5),
                                2, 45, 6, "scene-token-image-1", true, false,
                                true, TokenLootCategory.MISC, 2, "Denti di mimic", "Non rivelare"),
                        new SceneToken("token-fallback", "Carro", TokenCategory.VEHICLE, new GridPoint(3.5, 9.5),
                                3, 0, 7, "", false, true, "")),
                new BoardLayers(true, false, false, true, true),
                fog);

        assertEquals(source, codec.decode(codec.encode(source)));
    }

    @Test
    void unDocumentoPrecedenteSenzaVisibilitaPedineUsaIlDefault() {
        Map<String, Object> encoded = codec.encode(BoardDocument.empty());
        @SuppressWarnings("unchecked")
        Map<String, Object> layers = (Map<String, Object>) encoded.get("layers");
        layers.remove("sceneTokensVisible");

        assertEquals(true, codec.decode(encoded).layers().sceneTokensVisible());
    }

    @Test
    void unaPedinaPrecedenteSenzaLootRestaCompatibileENonRaccoglibile() {
        SceneToken legacy = new SceneToken(
                "legacy", "Cassa", TokenCategory.OBJECT, new GridPoint(1.5, 1.5),
                1, 0, 7, "", true, true, "Nota privata");
        Map<String, Object> encoded = codec.encode(BoardDocument.empty().withObjects(List.of(legacy)));
        @SuppressWarnings("unchecked")
        Map<String, Object> token = (Map<String, Object>) ((List<?>) encoded.get("objects")).get(0);
        token.remove("lootable");
        token.remove("lootCategory");
        token.remove("lootQuantity");
        token.remove("lootDescription");

        SceneToken decoded = (SceneToken) codec.decode(encoded).objects().get(0);

        assertEquals(false, decoded.lootable());
        assertEquals(TokenLootCategory.MISC, decoded.lootCategory());
        assertEquals(1, decoded.lootQuantity());
        assertEquals("", decoded.lootDescription());
        assertEquals("Nota privata", decoded.notes());
    }

    @Test
    void bitsetPeggioreRestaLimitatoEFaRoundTrip() {
        List<Long> words = new java.util.ArrayList<>(java.util.Collections.nCopies(2_500, 0L));
        for (int row = 0; row < 400; row++) {
            for (int column = row & 1; column < 400; column += 2) {
                int index = row * 400 + column;
                int word = index >>> 6;
                words.set(word, words.get(word) | (1L << (index & 63)));
            }
        }
        FogMask checkerboard = new FogMask(400, 400, words);
        BoardDocument source = BoardDocument.empty().withFog(checkerboard);
        Map<String, Object> encoded = codec.encode(source);

        assertEquals(source, codec.decode(encoded));
        Map<?, ?> fog = (Map<?, ?>) encoded.get("fog");
        assertEquals("bitset", fog.get("encoding"));
        assertEquals(2_500, ((List<?>) fog.get("data")).size());
    }

    @Test
    void tipiVersioniECoordinateInvalidiVengonoRifiutati() {
        Map<String, Object> unknown = new LinkedHashMap<>(codec.encode(BoardDocument.empty()));
        unknown.put("objects", List.of(Map.of("id", "x", "type", "particle", "colorArgb", 0)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(unknown));

        Map<String, Object> invalidPoint = new LinkedHashMap<>(codec.encode(BoardDocument.empty()));
        invalidPoint.put("objects", List.of(Map.of(
                "id", "x", "type", "label", "colorArgb", 0,
                "position", List.of(Double.NaN, 0), "text", "x", "textSizeSp", 14, "rotationDegrees", 0)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(invalidPoint));

        Map<String, Object> invalidCategory = new LinkedHashMap<>(codec.encode(BoardDocument.empty()));
        invalidCategory.put("objects", List.of(Map.ofEntries(
                Map.entry("id", "x"), Map.entry("type", "sceneToken"), Map.entry("colorArgb", 0),
                Map.entry("name", "Segreto"), Map.entry("category", "ALIEN"),
                Map.entry("position", List.of(1, 1)), Map.entry("sizeSquares", 1),
                Map.entry("rotationDegrees", 0), Map.entry("imageAssetId", ""),
                Map.entry("showLabel", false), Map.entry("visibleToPlayers", false), Map.entry("notes", ""))));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(invalidCategory));

        Map<String, Object> invalidFog = new LinkedHashMap<>(codec.encode(BoardDocument.empty()));
        invalidFog.put("fog", Map.of(
                "columns", Integer.MAX_VALUE, "rows", Integer.MAX_VALUE,
                "encoding", "runs", "data", List.of()));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(invalidFog));
    }
}
