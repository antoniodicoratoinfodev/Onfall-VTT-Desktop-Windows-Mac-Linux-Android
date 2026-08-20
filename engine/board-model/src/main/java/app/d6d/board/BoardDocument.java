package app.d6d.board;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Documento versionato del Lucido, autonomo dal motore di combattimento. */
public record BoardDocument(
        int schemaVersion,
        List<BoardObject> objects,
        BoardLayers layers,
        FogMask fog) {
    public BoardDocument {
        if (schemaVersion != BoardLimits.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported board schema: " + schemaVersion);
        }
        objects = List.copyOf(Objects.requireNonNull(objects, "objects"));
        layers = Objects.requireNonNull(layers, "layers");
        fog = Objects.requireNonNull(fog, "fog");
        if (objects.size() > BoardLimits.MAX_OBJECTS) {
            throw new IllegalArgumentException("Board contains too many objects");
        }
        Set<String> ids = new HashSet<>();
        int points = 0;
        for (BoardObject object : objects) {
            if (!ids.add(object.id())) throw new IllegalArgumentException("Duplicate board object id: " + object.id());
            if (object instanceof InkStroke stroke) points = Math.addExact(points, stroke.points().size());
            if (object instanceof Measurement measurement) points = Math.addExact(points, measurement.points().size());
            if (points > BoardLimits.MAX_TOTAL_POINTS) {
                throw new IllegalArgumentException("Board contains too many path points");
            }
        }
    }

    public static BoardDocument empty() {
        return new BoardDocument(BoardLimits.SCHEMA_VERSION, List.of(), BoardLayers.defaults(), FogMask.empty(0, 0));
    }

    public BoardDocument withObjects(List<BoardObject> value) {
        return new BoardDocument(schemaVersion, value, layers, fog);
    }

    public BoardDocument withLayers(BoardLayers value) {
        return new BoardDocument(schemaVersion, objects, value, fog);
    }

    public BoardDocument withFog(FogMask value) {
        return new BoardDocument(schemaVersion, objects, layers, value);
    }
}
