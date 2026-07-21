package app.d6d.domain.space;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mappa tattica dell'incontro: griglia, segnaposti e sfondo.
 *
 * <p>La mappa e' facoltativa. Quando non e' configurata l'incontro si gioca in
 * modalita' astratta esattamente come prima che la mappa esistesse, e il documento
 * chiede che in quel caso l'applicazione non si dichiari simulazione tattica
 * esatta.</p>
 *
 * <p>{@code backgroundImage} e' il nome di un file nell'archivio locale delle
 * immagini, mai un percorso assoluto: le immagini dell'utente restano locali e
 * fuori dagli export condivisibili.</p>
 */
public record BattleMap(MapGrid grid, Map<String, TokenPlacement> placements, String backgroundImage) {

    public BattleMap {
        Objects.requireNonNull(grid, "grid");
        placements = Map.copyOf(Objects.requireNonNull(placements, "placements"));
        backgroundImage = backgroundImage == null ? "" : backgroundImage;
        for (Map.Entry<String, TokenPlacement> entry : placements.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().combatantId())) {
                throw new IllegalArgumentException("Placement key must match its combatant id");
            }
            if (!grid.contains(entry.getValue().origin())) {
                throw new IllegalArgumentException(
                        "Placement outside the grid: " + entry.getValue().combatantId());
            }
        }
    }

    /** Nessuna mappa: l'incontro resta astratto. */
    public static BattleMap none() {
        return new BattleMap(MapGrid.NONE, Map.of(), "");
    }

    public boolean configured() {
        return grid.configured();
    }

    public Optional<TokenPlacement> placementOf(String combatantId) {
        return Optional.ofNullable(placements.get(combatantId));
    }

    public boolean isPlaced(String combatantId) {
        return placements.containsKey(combatantId);
    }

    /**
     * Distanza in piedi fra due combattenti posizionati.
     *
     * <p>Vuota quando anche uno solo dei due non e' sulla mappa: senza coordinate
     * complete non si puo' dichiarare una distanza esatta.</p>
     */
    public Optional<Integer> distanceFeet(String first, String second) {
        TokenPlacement a = placements.get(first);
        TokenPlacement b = placements.get(second);
        if (a == null || b == null) return Optional.empty();
        return Optional.of(grid.feetFor(a.squaresTo(b)));
    }

    /** Il segnaposto entra interamente nella griglia. */
    public boolean fitsInsideGrid(TokenPlacement placement) {
        return placement.occupiedSquares().stream().allMatch(grid::contains);
    }

    /**
     * Verifica che lo spazio sia libero, ignorando il combattente indicato.
     *
     * <p>Ignorare se stessi serve quando un segnaposto si sposta di poco e la nuova
     * posizione si sovrappone alla vecchia.</p>
     */
    public boolean isFree(TokenPlacement candidate) {
        return placements.values().stream()
                .filter(existing -> !existing.combatantId().equals(candidate.combatantId()))
                .noneMatch(existing -> existing.overlaps(candidate));
    }

    /** Combattente che occupa la casella, se c'e'. */
    public Optional<String> occupantAt(GridPosition position) {
        return placements.values().stream()
                .filter(placement -> placement.occupies(position))
                .map(TokenPlacement::combatantId)
                .findFirst();
    }

    public BattleMap withGrid(MapGrid updated) {
        Objects.requireNonNull(updated, "updated");
        // Restringere la griglia non deve lasciare segnaposti fuori dal bordo:
        // vengono rimossi e andranno riposizionati esplicitamente.
        Map<String, TokenPlacement> retained = new LinkedHashMap<>();
        placements.forEach((id, placement) -> {
            if (placement.occupiedSquares().stream().allMatch(updated::contains)) {
                retained.put(id, placement);
            }
        });
        return new BattleMap(updated, retained, backgroundImage);
    }

    public BattleMap withPlacement(TokenPlacement placement) {
        Map<String, TokenPlacement> updated = new LinkedHashMap<>(placements);
        updated.put(placement.combatantId(), placement);
        return new BattleMap(grid, updated, backgroundImage);
    }

    public BattleMap without(String combatantId) {
        Map<String, TokenPlacement> updated = new LinkedHashMap<>(placements);
        updated.remove(combatantId);
        return new BattleMap(grid, updated, backgroundImage);
    }

    public BattleMap withBackground(String imageName) {
        return new BattleMap(grid, placements, imageName);
    }

    /** Segnaposti in ordine stabile, per disegnarli sempre nella stessa sequenza. */
    public List<TokenPlacement> orderedPlacements() {
        return List.copyOf(placements.values());
    }
}
