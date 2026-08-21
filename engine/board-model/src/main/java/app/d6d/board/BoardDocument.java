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
        FloorMask floors,
        VisionSettings vision,
        ExploredMask explored) {
    public BoardDocument {
        if (schemaVersion != BoardLimits.SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported board schema: " + schemaVersion);
        }
        objects = List.copyOf(Objects.requireNonNull(objects, "objects"));
        layers = Objects.requireNonNull(layers, "layers");
        fog = Objects.requireNonNull(fog, "fog");
        walls = Objects.requireNonNull(walls, "walls");
        floors = Objects.requireNonNull(floors, "floors");
        vision = Objects.requireNonNull(vision, "vision");
        explored = Objects.requireNonNull(explored, "explored");
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

    /** Compatibilità sorgente con i documenti precedenti alla vista dinamica. */
    public BoardDocument(
            int schemaVersion,
            List<BoardObject> objects,
            BoardLayers layers,
            FogMask fog,
            WallMask walls,
            FloorMask floors) {
        this(
                schemaVersion, objects, layers, fog, walls, floors,
                VisionSettings.defaults(),
                ExploredMask.empty(fog.columns(), fog.rows()));
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
                FogMask.empty(0, 0), WallMask.empty(0, 0), FloorMask.empty(0, 0),
                VisionSettings.defaults(), ExploredMask.empty(0, 0));
    }

    public BoardDocument withObjects(List<BoardObject> value) {
        return new BoardDocument(schemaVersion, value, layers, fog, walls, floors, vision, explored);
    }

    public BoardDocument withLayers(BoardLayers value) {
        return new BoardDocument(schemaVersion, objects, value, fog, walls, floors, vision, explored);
    }

    public BoardDocument withFog(FogMask value) {
        return new BoardDocument(schemaVersion, objects, layers, value, walls, floors, vision, explored);
    }

    public BoardDocument withWalls(WallMask value) {
        return new BoardDocument(schemaVersion, objects, layers, fog, value, floors, vision, explored);
    }

    public BoardDocument withFloors(FloorMask value) {
        return new BoardDocument(schemaVersion, objects, layers, fog, walls, value, vision, explored);
    }

    public BoardDocument withVision(VisionSettings value) {
        return new BoardDocument(schemaVersion, objects, layers, fog, walls, floors, value, explored);
    }

    public BoardDocument withExplored(ExploredMask value) {
        return new BoardDocument(schemaVersion, objects, layers, fog, walls, floors, vision, value);
    }
}
