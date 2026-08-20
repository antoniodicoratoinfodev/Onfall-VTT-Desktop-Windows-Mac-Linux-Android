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
        FogMask fog,
        WallMask walls,
        FloorMask floors) {
    public BoardDocument {
        if (schemaVersion != BoardLimits.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported board schema: " + schemaVersion);
        }
        objects = List.copyOf(Objects.requireNonNull(objects, "objects"));
        layers = Objects.requireNonNull(layers, "layers");
        fog = Objects.requireNonNull(fog, "fog");
        walls = Objects.requireNonNull(walls, "walls");
        floors = Objects.requireNonNull(floors, "floors");
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

    /** Compatibilità sorgente con i documenti precedenti all'introduzione dei muri. */
    public BoardDocument(int schemaVersion, List<BoardObject> objects, BoardLayers layers, FogMask fog) {
        this(
                schemaVersion, objects, layers, fog,
                WallMask.empty(fog.columns(), fog.rows()),
                FloorMask.empty(fog.columns(), fog.rows()));
    }

    /** Compatibilità sorgente con i documenti precedenti all'introduzione del pavimento. */
    public BoardDocument(
            int schemaVersion, List<BoardObject> objects, BoardLayers layers, FogMask fog, WallMask walls) {
        this(schemaVersion, objects, layers, fog, walls, FloorMask.empty(walls.columns(), walls.rows()));
    }

    public static BoardDocument empty() {
        return new BoardDocument(
                BoardLimits.SCHEMA_VERSION, List.of(), BoardLayers.defaults(),
                FogMask.empty(0, 0), WallMask.empty(0, 0), FloorMask.empty(0, 0));
    }

    public BoardDocument withObjects(List<BoardObject> value) {
        return new BoardDocument(schemaVersion, value, layers, fog, walls, floors);
    }

    public BoardDocument withLayers(BoardLayers value) {
        return new BoardDocument(schemaVersion, objects, value, fog, walls, floors);
    }

    public BoardDocument withFog(FogMask value) {
        return new BoardDocument(schemaVersion, objects, layers, value, walls, floors);
    }

    public BoardDocument withWalls(WallMask value) {
        return new BoardDocument(schemaVersion, objects, layers, fog, value, floors);
    }

    public BoardDocument withFloors(FloorMask value) {
        return new BoardDocument(schemaVersion, objects, layers, fog, walls, value);
    }
}
