package app.d6d.board;

/** Limiti pubblici applicati tanto dal modello quanto dai codec. */
public final class BoardLimits {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_OBJECTS = 5_000;
    public static final int MAX_POINTS_PER_PATH = 10_000;
    public static final int MAX_TOTAL_POINTS = 100_000;
    public static final int MAX_LABEL_LENGTH = 500;
    public static final int MAX_TOKEN_NAME_LENGTH = 80;
    public static final int MAX_TOKEN_NOTES_LENGTH = 500;
    public static final int MAX_TOKEN_LOOT_DESCRIPTION_LENGTH = 500;
    public static final int MAX_TOKEN_LOOT_QUANTITY = 9_999;
    public static final int MAX_ID_LENGTH = 120;
    public static final int MAX_FOG_DIMENSION = 400;

    /** Raggio massimo della vista dinamica: oltre, il campo copre comunque la mappa intera. */
    public static final int MAX_VISION_RADIUS_FEET = 5_000;
    /** Un'eccezione per combattente; il tetto tiene il documento entro una misura sana. */
    public static final int MAX_VISION_OVERRIDES = 500;
    /** Sessanta piedi: la scurovisione più comune del regolamento, 18 m in italiano. */
    public static final int DEFAULT_VISION_RADIUS_FEET = 60;
    public static final double MAX_WORLD_COORDINATE = 4_096.0;
    public static final double MIN_TOKEN_SIZE_SQUARES = 0.25;
    public static final double MAX_TOKEN_SIZE_SQUARES = 20.0;

    private BoardLimits() {
    }
}
