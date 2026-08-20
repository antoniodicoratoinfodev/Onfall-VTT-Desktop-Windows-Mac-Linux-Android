package app.d6d.board;

/** Visibilità e protezione delle sole parti possedute dal Lucido. */
public record BoardLayers(
        boolean annotationsVisible,
        boolean stampsVisible,
        boolean sceneTokensVisible,
        boolean fogVisible,
        boolean locked) {
    /** Compatibilità sorgente con il Lucido precedente alle pedine di scena. */
    public BoardLayers(
            boolean annotationsVisible,
            boolean stampsVisible,
            boolean fogVisible,
            boolean locked) {
        this(annotationsVisible, stampsVisible, true, fogVisible, locked);
    }

    public static BoardLayers defaults() {
        return new BoardLayers(true, true, true, true, false);
    }
}
