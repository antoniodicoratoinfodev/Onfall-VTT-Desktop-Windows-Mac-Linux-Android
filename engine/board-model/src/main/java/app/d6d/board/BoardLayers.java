package app.d6d.board;

/** Visibilità e protezione delle sole parti possedute dal Lucido. */
public record BoardLayers(
        boolean backgroundVisible,
        boolean floorsVisible,
        boolean annotationsVisible,
        boolean stampsVisible,
        boolean sceneTokensVisible,
        boolean wallsVisible,
        boolean fogVisible,
        boolean locked) {
    /** Compatibilità sorgente con il Lucido precedente al layer Pavimento. */
    public BoardLayers(
            boolean backgroundVisible,
            boolean annotationsVisible,
            boolean stampsVisible,
            boolean sceneTokensVisible,
            boolean wallsVisible,
            boolean fogVisible,
            boolean locked) {
        this(backgroundVisible, true, annotationsVisible, stampsVisible, sceneTokensVisible, wallsVisible, fogVisible, locked);
    }

    /** Compatibilità sorgente con il Lucido precedente a sfondo e muri attivabili. */
    public BoardLayers(
            boolean annotationsVisible,
            boolean stampsVisible,
            boolean sceneTokensVisible,
            boolean fogVisible,
            boolean locked) {
        this(true, true, annotationsVisible, stampsVisible, sceneTokensVisible, true, fogVisible, locked);
    }

    /** Compatibilità sorgente con il Lucido precedente alle pedine di scena. */
    public BoardLayers(
            boolean annotationsVisible,
            boolean stampsVisible,
            boolean fogVisible,
            boolean locked) {
        this(true, true, annotationsVisible, stampsVisible, true, true, fogVisible, locked);
    }

    public static BoardLayers defaults() {
        return new BoardLayers(true, true, true, true, true, true, true, false);
    }
}
