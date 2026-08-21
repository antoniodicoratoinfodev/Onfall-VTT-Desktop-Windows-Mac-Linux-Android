package app.d6d.persistence.board;

import app.d6d.board.AreaTemplate;
import app.d6d.board.BoardDocument;
import app.d6d.board.BoardLayers;
import app.d6d.board.ExploredMask;
import app.d6d.board.FogMask;
import app.d6d.board.FloorMask;
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
import app.d6d.board.VisionMode;
import app.d6d.board.VisionSettings;
import app.d6d.board.WallMask;
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
                new BoardLayers(false, false, true, false, false, false, true, true),
                fog,
                WallMask.empty(20, 15).withCell(4, 6, true).withCell(5, 6, true),
                FloorMask.empty(20, 15).withCell(8, 7, true).withCell(9, 7, true));

        assertEquals(source, codec.decode(codec.encode(source)));
    }

    @Test
    void unDocumentoPrecedenteSenzaVisibilitaPedineUsaIlDefault() {
        Map<String, Object> encoded = codec.encode(BoardDocument.empty());
        @SuppressWarnings("unchecked")
        Map<String, Object> layers = (Map<String, Object>) encoded.get("layers");
        layers.remove("sceneTokensVisible");
        layers.remove("backgroundVisible");
        layers.remove("floorsVisible");
        layers.remove("wallsVisible");
        encoded.remove("walls");
        encoded.remove("floors");

        BoardDocument decoded = codec.decode(encoded);
        assertEquals(true, decoded.layers().sceneTokensVisible());
        assertEquals(true, decoded.layers().backgroundVisible());
        assertEquals(true, decoded.layers().floorsVisible());
        assertEquals(true, decoded.layers().wallsVisible());
        assertEquals(WallMask.empty(0, 0), decoded.walls());
        assertEquals(FloorMask.empty(0, 0), decoded.floors());
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
    void vistaDinamicaEmemoriaDellEsploratoFannoRoundTrip() {
        VisionSettings vision = new VisionSettings(
                VisionMode.DYNAMIC, 60, Map.of("pg-nano", 120, "pg-elfo", 0));
        ExploredMask explored = ExploredMask.empty(20, 15)
                .withCell(0, 0, true)
                .withCell(1, 0, true)
                .withCell(9, 9, true);
        BoardDocument source = new BoardDocument(
                1, List.of(), BoardLayers.defaults(), FogMask.empty(20, 15),
                WallMask.empty(20, 15), FloorMask.empty(20, 15), vision, explored);

        BoardDocument restored = codec.decode(codec.encode(source));

        assertEquals(VisionMode.DYNAMIC, restored.vision().mode());
        assertEquals(60, restored.vision().radiusFeet());
        assertEquals(120, restored.vision().radiusFeetFor("pg-nano"));
        assertEquals(0, restored.vision().radiusFeetFor("pg-elfo"), "zero e' cieco, non assente");
        assertEquals(60, restored.vision().radiusFeetFor("chiunque-altro"));
        assertEquals(explored, restored.explored());
        assertEquals(source, restored);
    }

    @Test
    void unLucidoSalvatoPrimaDellaVistaDinamicaSiRiapreDipintoAmano() {
        BoardDocument previous = new BoardDocument(
                1, List.of(), BoardLayers.defaults(), FogMask.empty(12, 8).withCell(3, 3, true));
        Map<String, Object> encoded = new LinkedHashMap<>(codec.encode(previous));
        // Il file di allora non aveva queste due chiavi: si legge come se non le avesse.
        encoded.remove("vision");
        encoded.remove("explored");

        BoardDocument restored = codec.decode(encoded);

        assertEquals(VisionMode.MANUAL, restored.vision().mode(), "la nebbia resta quella dipinta");
        assertEquals(60, restored.vision().radiusFeet());
        assertEquals(ExploredMask.empty(12, 8), restored.explored());
        assertEquals(previous.fog(), restored.fog(), "e la nebbia salvata non si perde");
    }

    @Test
    void unaVistaFuoriScalaVieneRifiutata() {
        BoardDocument source = new BoardDocument(
                1, List.of(), BoardLayers.defaults(), FogMask.empty(8, 8));
        Map<String, Object> encoded = new LinkedHashMap<>(codec.encode(source));

        Map<String, Object> badMode = new LinkedHashMap<>();
        badMode.put("mode", "TELEPATHY");
        encoded.put("vision", badMode);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));

        Map<String, Object> badRadius = new LinkedHashMap<>();
        badRadius.put("mode", "DYNAMIC");
        badRadius.put("radiusFeet", -10);
        encoded.put("vision", badRadius);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));

        Map<String, Object> badOverride = new LinkedHashMap<>();
        badOverride.put("mode", "DYNAMIC");
        badOverride.put("radiusFeet", 60);
        badOverride.put("radiusOverridesFeet", Map.of("pg", 999_999));
        encoded.put("vision", badOverride);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));
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
