package app.d6d.persistence.board;

import app.d6d.board.AreaTemplate;
import app.d6d.board.BoardDocument;
import app.d6d.board.BoardLayers;
import app.d6d.board.BoardLimits;
import app.d6d.board.BoardObject;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Codec JSON strutturato e rigoroso per il Lucido. */
public final class BoardDocumentJsonCodec {

    public Map<String, Object> encode(BoardDocument board) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", board.schemaVersion());
        value.put("layers", encodeLayers(board.layers()));
        value.put("objects", board.objects().stream().map(this::encodeObject).toList());
        value.put("fog", encodeFog(board.fog()));
        return value;
    }

    public BoardDocument decode(Map<String, ?> value) {
        int version = integer(value.get("schemaVersion"), "$.board.schemaVersion");
        if (version != BoardLimits.SCHEMA_VERSION) {
            throw invalid("$.board.schemaVersion", "unsupported version " + version);
        }
        BoardLayers layers = value.get("layers") == null
                ? BoardLayers.defaults()
                : decodeLayers(object(value.get("layers"), "$.board.layers"));
        List<?> rawObjects = list(value.get("objects"), "$.board.objects");
        if (rawObjects.size() > BoardLimits.MAX_OBJECTS) {
            throw invalid("$.board.objects", "too many objects");
        }
        List<BoardObject> objects = new ArrayList<>(rawObjects.size());
        for (int index = 0; index < rawObjects.size(); index++) {
            objects.add(decodeObject(object(rawObjects.get(index), "$.board.objects[" + index + "]"), index));
        }
        FogMask fog = value.get("fog") == null
                ? FogMask.empty(0, 0)
                : decodeFog(object(value.get("fog"), "$.board.fog"));
        return new BoardDocument(version, objects, layers, fog);
    }

    private Map<String, Object> encodeLayers(BoardLayers layers) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("annotationsVisible", layers.annotationsVisible());
        value.put("stampsVisible", layers.stampsVisible());
        value.put("sceneTokensVisible", layers.sceneTokensVisible());
        value.put("fogVisible", layers.fogVisible());
        value.put("locked", layers.locked());
        return value;
    }

    private BoardLayers decodeLayers(Map<String, ?> value) {
        return new BoardLayers(
                bool(value.get("annotationsVisible"), "$.board.layers.annotationsVisible", true),
                bool(value.get("stampsVisible"), "$.board.layers.stampsVisible", true),
                bool(value.get("sceneTokensVisible"), "$.board.layers.sceneTokensVisible", true),
                bool(value.get("fogVisible"), "$.board.layers.fogVisible", true),
                bool(value.get("locked"), "$.board.layers.locked", false));
    }

    private Map<String, Object> encodeObject(BoardObject object) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", object.id());
        if (object instanceof InkStroke stroke) {
            value.put("type", "ink");
            value.put("points", encodePoints(stroke.points()));
            value.put("colorArgb", stroke.colorArgb());
            value.put("widthSquares", stroke.widthSquares());
        } else if (object instanceof Measurement measurement) {
            value.put("type", "measurement");
            value.put("points", encodePoints(measurement.points()));
            value.put("colorArgb", measurement.colorArgb());
        } else if (object instanceof AreaTemplate template) {
            value.put("type", "template");
            value.put("shape", template.shape().name());
            value.put("anchor", encodePoint(template.anchor()));
            value.put("end", encodePoint(template.end()));
            value.put("sizeFeet", template.sizeFeet());
            value.put("widthFeet", template.widthFeet());
            value.put("rotationDegrees", template.rotationDegrees());
            value.put("colorArgb", template.colorArgb());
        } else if (object instanceof Label label) {
            value.put("type", "label");
            value.put("position", encodePoint(label.position()));
            value.put("text", label.text());
            value.put("colorArgb", label.colorArgb());
            value.put("textSizeSp", label.textSizeSp());
            value.put("rotationDegrees", label.rotationDegrees());
        } else if (object instanceof StaticStamp stamp) {
            value.put("type", "stamp");
            value.put("position", encodePoint(stamp.position()));
            value.put("kind", stamp.kind().name());
            value.put("sizeSquares", stamp.sizeSquares());
            value.put("rotationDegrees", stamp.rotationDegrees());
            value.put("colorArgb", stamp.colorArgb());
        } else if (object instanceof SceneToken token) {
            value.put("type", "sceneToken");
            value.put("name", token.name());
            value.put("category", token.category().name());
            value.put("position", encodePoint(token.position()));
            value.put("sizeSquares", token.sizeSquares());
            value.put("rotationDegrees", token.rotationDegrees());
            value.put("colorArgb", token.colorArgb());
            value.put("imageAssetId", token.imageAssetId());
            value.put("showLabel", token.showLabel());
            value.put("visibleToPlayers", token.visibleToPlayers());
            value.put("lootable", token.lootable());
            value.put("lootCategory", token.lootCategory().name());
            value.put("lootQuantity", token.lootQuantity());
            value.put("lootDescription", token.lootDescription());
            value.put("notes", token.notes());
        } else {
            throw new IllegalArgumentException("Unsupported board object: " + object.getClass().getName());
        }
        return value;
    }

    private BoardObject decodeObject(Map<String, ?> value, int index) {
        String path = "$.board.objects[" + index + "]";
        String id = string(value.get("id"), path + ".id");
        int color = integer(value.get("colorArgb"), path + ".colorArgb");
        return switch (string(value.get("type"), path + ".type")) {
            case "ink" -> new InkStroke(
                    id,
                    points(value.get("points"), path + ".points"),
                    color,
                    decimal(value.get("widthSquares"), path + ".widthSquares"));
            case "measurement" -> new Measurement(
                    id,
                    points(value.get("points"), path + ".points"),
                    color);
            case "template" -> new AreaTemplate(
                    id,
                    enumValue(TemplateShape.class, value.get("shape"), path + ".shape"),
                    point(value.get("anchor"), path + ".anchor"),
                    point(value.get("end"), path + ".end"),
                    decimal(value.get("sizeFeet"), path + ".sizeFeet"),
                    decimal(value.get("widthFeet"), path + ".widthFeet"),
                    decimal(value.get("rotationDegrees"), path + ".rotationDegrees"),
                    color);
            case "label" -> new Label(
                    id,
                    point(value.get("position"), path + ".position"),
                    string(value.get("text"), path + ".text"),
                    color,
                    decimal(value.get("textSizeSp"), path + ".textSizeSp"),
                    decimal(value.get("rotationDegrees"), path + ".rotationDegrees"));
            case "stamp" -> new StaticStamp(
                    id,
                    point(value.get("position"), path + ".position"),
                    enumValue(StampKind.class, value.get("kind"), path + ".kind"),
                    decimal(value.get("sizeSquares"), path + ".sizeSquares"),
                    decimal(value.get("rotationDegrees"), path + ".rotationDegrees"),
                    color);
            case "sceneToken" -> new SceneToken(
                    id,
                    string(value.get("name"), path + ".name"),
                    enumValue(TokenCategory.class, value.get("category"), path + ".category"),
                    point(value.get("position"), path + ".position"),
                    decimal(value.get("sizeSquares"), path + ".sizeSquares"),
                    decimal(value.get("rotationDegrees"), path + ".rotationDegrees"),
                    color,
                    string(value.get("imageAssetId"), path + ".imageAssetId"),
                    bool(value.get("showLabel"), path + ".showLabel", true),
                    bool(value.get("visibleToPlayers"), path + ".visibleToPlayers", true),
                    bool(value.get("lootable"), path + ".lootable", false),
                    enumValue(
                            TokenLootCategory.class,
                            value.get("lootCategory"),
                            path + ".lootCategory",
                            TokenLootCategory.MISC),
                    integer(value.get("lootQuantity"), path + ".lootQuantity", 1),
                    string(value.get("lootDescription"), path + ".lootDescription", ""),
                    string(value.get("notes"), path + ".notes"));
            default -> throw invalid(path + ".type", "unknown board object type");
        };
    }

    private List<?> encodePoints(List<GridPoint> points) {
        return points.stream().map(this::encodePoint).toList();
    }

    private List<Object> encodePoint(GridPoint point) {
        return List.of(point.x(), point.y());
    }

    private GridPoint point(Object value, String path) {
        List<?> pair = list(value, path);
        if (pair.size() != 2) throw invalid(path, "point must contain exactly two coordinates");
        return new GridPoint(decimal(pair.get(0), path + "[0]"), decimal(pair.get(1), path + "[1]"));
    }

    private List<GridPoint> points(Object value, String path) {
        List<?> raw = list(value, path);
        if (raw.size() > BoardLimits.MAX_POINTS_PER_PATH) throw invalid(path, "too many points");
        List<GridPoint> result = new ArrayList<>(raw.size());
        for (int index = 0; index < raw.size(); index++) result.add(point(raw.get(index), path + "[" + index + "]"));
        return result;
    }

    private Map<String, Object> encodeFog(FogMask fog) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("columns", fog.columns());
        value.put("rows", fog.rows());
        List<List<Integer>> runs = fogRuns(fog);
        if (runs.size() * 2 < fog.words().size()) {
            value.put("encoding", "runs");
            value.put("data", runs);
        } else {
            value.put("encoding", "bitset");
            value.put("data", fog.words());
        }
        return value;
    }

    private FogMask decodeFog(Map<String, ?> value) {
        int columns = integer(value.get("columns"), "$.board.fog.columns");
        int rows = integer(value.get("rows"), "$.board.fog.rows");
        if (columns < 0 || rows < 0
                || columns > BoardLimits.MAX_FOG_DIMENSION || rows > BoardLimits.MAX_FOG_DIMENSION) {
            throw invalid("$.board.fog", "dimensions are outside the supported map limits");
        }
        String encoding = string(value.get("encoding"), "$.board.fog.encoding");
        if (encoding.equals("bitset")) {
            List<?> raw = list(value.get("data"), "$.board.fog.data");
            List<Long> words = new ArrayList<>(raw.size());
            for (int index = 0; index < raw.size(); index++) {
                words.add(longInteger(raw.get(index), "$.board.fog.data[" + index + "]"));
            }
            return new FogMask(columns, rows, words);
        }
        if (!encoding.equals("runs")) throw invalid("$.board.fog.encoding", "unknown encoding");
        int cells = Math.multiplyExact(columns, rows);
        List<Long> words = new ArrayList<>(java.util.Collections.nCopies((cells + 63) >>> 6, 0L));
        int previousEnd = 0;
        List<?> rawRuns = list(value.get("data"), "$.board.fog.data");
        if (rawRuns.size() > cells) throw invalid("$.board.fog.data", "too many runs");
        for (int index = 0; index < rawRuns.size(); index++) {
            List<?> pair = list(rawRuns.get(index), "$.board.fog.data[" + index + "]");
            if (pair.size() != 2) throw invalid("$.board.fog.data[" + index + "]", "run must have start and length");
            int start = integer(pair.get(0), "$.board.fog.data[" + index + "][0]");
            int length = integer(pair.get(1), "$.board.fog.data[" + index + "][1]");
            if (start < previousEnd || length <= 0 || start > cells - length) {
                throw invalid("$.board.fog.data[" + index + "]", "invalid or overlapping run");
            }
            for (int cell = start; cell < start + length; cell++) {
                int word = cell >>> 6;
                words.set(word, words.get(word) | (1L << (cell & 63)));
            }
            previousEnd = start + length;
        }
        return new FogMask(columns, rows, words);
    }

    private List<List<Integer>> fogRuns(FogMask fog) {
        List<List<Integer>> result = new ArrayList<>();
        int cells = fog.columns() * fog.rows();
        int index = 0;
        while (index < cells) {
            if (!fog.covered(index % fog.columns(), index / fog.columns())) {
                index++;
                continue;
            }
            int start = index++;
            while (index < cells && fog.covered(index % fog.columns(), index / fog.columns())) index++;
            result.add(List.of(start, index - start));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> object(Object value, String path) {
        if (!(value instanceof Map<?, ?> map)) throw invalid(path, "expected an object");
        for (Object key : map.keySet()) if (!(key instanceof String)) throw invalid(path, "object key is not text");
        return (Map<String, ?>) map;
    }

    private List<?> list(Object value, String path) {
        if (!(value instanceof List<?> list)) throw invalid(path, "expected a list");
        return list;
    }

    private String string(Object value, String path) {
        if (!(value instanceof String text)) throw invalid(path, "expected text");
        return text;
    }

    private String string(Object value, String path, String fallback) {
        return value == null ? fallback : string(value, path);
    }

    private boolean bool(Object value, String path, boolean fallback) {
        if (value == null) return fallback;
        if (!(value instanceof Boolean bool)) throw invalid(path, "expected a boolean");
        return bool;
    }

    private int integer(Object value, String path) {
        long number = longInteger(value, path);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw invalid(path, "integer is out of range");
        return (int) number;
    }

    private int integer(Object value, String path, int fallback) {
        return value == null ? fallback : integer(value, path);
    }

    private long longInteger(Object value, String path) {
        if (!(value instanceof Number number)) throw invalid(path, "expected an integer");
        if (number instanceof Float || number instanceof Double || number instanceof java.math.BigDecimal) {
            double decimal = number.doubleValue();
            if (!Double.isFinite(decimal) || decimal != Math.rint(decimal)) throw invalid(path, "expected an integer");
        }
        try {
            return new java.math.BigDecimal(number.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw invalid(path, "integer is out of range");
        }
    }

    private double decimal(Object value, String path) {
        if (!(value instanceof Number number)) throw invalid(path, "expected a number");
        double result = number.doubleValue();
        if (!Double.isFinite(result)) throw invalid(path, "number must be finite");
        return result;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, Object value, String path) {
        try {
            return Enum.valueOf(type, string(value, path));
        } catch (IllegalArgumentException failure) {
            throw invalid(path, "unknown value");
        }
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, Object value, String path, E fallback) {
        return value == null ? fallback : enumValue(type, value, path);
    }

    private IllegalArgumentException invalid(String path, String message) {
        return new IllegalArgumentException("Invalid board at " + path + ": " + message);
    }
}
