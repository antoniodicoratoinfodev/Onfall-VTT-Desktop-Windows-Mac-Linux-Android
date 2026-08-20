package app.d6d.board;

import java.util.Objects;

/**
 * Pedina visiva del Lucido, intenzionalmente priva di statistiche di combattimento.
 *
 * <p>{@code imageAssetId} è una chiave opaca dell'archivio immagini locale, non un
 * percorso. Se è vuota o non si risolve, la UI disegna il medaglione di categoria.</p>
 */
public record SceneToken(
        String id,
        String name,
        TokenCategory category,
        GridPoint position,
        double sizeSquares,
        double rotationDegrees,
        int colorArgb,
        String imageAssetId,
        boolean showLabel,
        boolean visibleToPlayers,
        boolean lootable,
        TokenLootCategory lootCategory,
        int lootQuantity,
        String lootDescription,
        String notes) implements BoardObject {

    public SceneToken {
        id = BoardValidation.id(id);
        name = requireText(name, BoardLimits.MAX_TOKEN_NAME_LENGTH, "name");
        category = Objects.requireNonNull(category, "category");
        position = Objects.requireNonNull(position, "position");
        BoardValidation.positive(sizeSquares, BoardLimits.MAX_TOKEN_SIZE_SQUARES, "sizeSquares");
        if (sizeSquares < BoardLimits.MIN_TOKEN_SIZE_SQUARES) {
            throw new IllegalArgumentException("sizeSquares is outside the supported range");
        }
        BoardValidation.finite(rotationDegrees, "rotationDegrees");
        imageAssetId = Objects.requireNonNull(imageAssetId, "imageAssetId").trim();
        if (!imageAssetId.isEmpty() && imageAssetId.length() > BoardLimits.MAX_ID_LENGTH) {
            throw new IllegalArgumentException("imageAssetId is too long");
        }
        lootCategory = Objects.requireNonNull(lootCategory, "lootCategory");
        if (lootQuantity < 1 || lootQuantity > BoardLimits.MAX_TOKEN_LOOT_QUANTITY) {
            throw new IllegalArgumentException("lootQuantity is outside the supported range");
        }
        lootDescription = Objects.requireNonNull(lootDescription, "lootDescription").trim();
        if (lootDescription.length() > BoardLimits.MAX_TOKEN_LOOT_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Token loot description is too long");
        }
        notes = Objects.requireNonNull(notes, "notes").trim();
        if (notes.length() > BoardLimits.MAX_TOKEN_NOTES_LENGTH) {
            throw new IllegalArgumentException("Token notes are too long");
        }
    }

    /** Costruttore compatibile con le pedine create prima del loot strutturato. */
    public SceneToken(
            String id,
            String name,
            TokenCategory category,
            GridPoint position,
            double sizeSquares,
            double rotationDegrees,
            int colorArgb,
            String imageAssetId,
            boolean showLabel,
            boolean visibleToPlayers,
            String notes) {
        this(id, name, category, position, sizeSquares, rotationDegrees, colorArgb,
                imageAssetId, showLabel, visibleToPlayers, false, TokenLootCategory.MISC,
                1, "", notes);
    }

    @Override
    public BoardBounds bounds(int feetPerSquare) {
        return BoardBounds.around(position).expanded(sizeSquares / 2.0);
    }

    @Override
    public SceneToken translated(double dx, double dy) {
        return new SceneToken(id, name, category, position.translated(dx, dy), sizeSquares,
                rotationDegrees, colorArgb, imageAssetId, showLabel, visibleToPlayers,
                lootable, lootCategory, lootQuantity, lootDescription, notes);
    }

    private static String requireText(String value, int maximum, String field) {
        String result = Objects.requireNonNull(value, field).trim();
        if (result.isEmpty() || result.length() > maximum) {
            throw new IllegalArgumentException(field + " is empty or too long");
        }
        return result;
    }
}
