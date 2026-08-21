package app.d6d.board;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Regole della vista dinamica.
 *
 * <p>Il raggio è in piedi perché il motore conta in piedi: la conversione in metri
 * è una scelta di lingua e vive nell'interfaccia. Vale per tutti tranne chi compare
 * fra le eccezioni — la scurovisione di un nano, un famiglio che vede poco — perché
 * le schede non portano un campo di vista da cui dedurlo.</p>
 *
 * <p>Un raggio pari a zero non è "cieco per errore" ma "non vede nulla": è il modo
 * di togliere la vista a un combattente senza inventare un valore negativo.</p>
 */
public record VisionSettings(VisionMode mode, int radiusFeet, Map<String, Integer> radiusOverridesFeet) {

    public VisionSettings {
        mode = Objects.requireNonNull(mode, "mode");
        if (radiusFeet < 0 || radiusFeet > BoardLimits.MAX_VISION_RADIUS_FEET) {
            throw new IllegalArgumentException("Vision radius is outside the supported range");
        }
        Objects.requireNonNull(radiusOverridesFeet, "radiusOverridesFeet");
        if (radiusOverridesFeet.size() > BoardLimits.MAX_VISION_OVERRIDES) {
            throw new IllegalArgumentException("Too many per-combatant vision overrides");
        }
        // TreeMap: l'ordine delle eccezioni finisce nel JSON, e un ordine stabile
        // evita che due salvataggi identici producano file diversi.
        Map<String, Integer> copy = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : radiusOverridesFeet.entrySet()) {
            String id = entry.getKey();
            if (id == null || id.isBlank() || id.length() > BoardLimits.MAX_ID_LENGTH) {
                throw new IllegalArgumentException("Invalid combatant id in vision overrides");
            }
            Integer feet = entry.getValue();
            if (feet == null || feet < 0 || feet > BoardLimits.MAX_VISION_RADIUS_FEET) {
                throw new IllegalArgumentException("Vision override is outside the supported range");
            }
            copy.put(id, feet);
        }
        radiusOverridesFeet = Map.copyOf(copy);
    }

    public static VisionSettings defaults() {
        return new VisionSettings(VisionMode.MANUAL, BoardLimits.DEFAULT_VISION_RADIUS_FEET, Map.of());
    }

    public boolean dynamic() {
        return mode == VisionMode.DYNAMIC;
    }

    /** Raggio di questo combattente: la sua eccezione, altrimenti quello di mappa. */
    public int radiusFeetFor(String combatantId) {
        if (combatantId == null) return radiusFeet;
        Integer override = radiusOverridesFeet.get(combatantId);
        return override != null ? override : radiusFeet;
    }

    public boolean hasOverride(String combatantId) {
        return combatantId != null && radiusOverridesFeet.containsKey(combatantId);
    }

    public VisionSettings withMode(VisionMode value) {
        return new VisionSettings(value, radiusFeet, radiusOverridesFeet);
    }

    public VisionSettings withRadiusFeet(int value) {
        return new VisionSettings(mode, value, radiusOverridesFeet);
    }

    /** Fissa l'eccezione di un combattente; {@code null} la toglie e riporta al raggio di mappa. */
    public VisionSettings withOverride(String combatantId, Integer feet) {
        Objects.requireNonNull(combatantId, "combatantId");
        Map<String, Integer> next = new LinkedHashMap<>(radiusOverridesFeet);
        if (feet == null) {
            if (next.remove(combatantId) == null) return this;
        } else {
            Integer previous = next.put(combatantId, feet);
            if (previous != null && previous.equals(feet)) return this;
        }
        return new VisionSettings(mode, radiusFeet, next);
    }
}
