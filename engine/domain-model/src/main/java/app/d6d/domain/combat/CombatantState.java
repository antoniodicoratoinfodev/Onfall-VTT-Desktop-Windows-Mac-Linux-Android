package app.d6d.domain.combat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CombatantState(
        CombatantSnapshot snapshot,
        int currentHitPoints,
        int temporaryHitPoints,
        List<ConditionInstance> conditions,
        ConcentrationState concentration,
        DeathSaveState deathSaves,
        int exhaustionLevel,
        List<CombatResourceState> resources) {

    /** Exhaustion arriva a sei: al sesto livello la creatura muore. */
    public static final int MAX_EXHAUSTION = 6;

    public CombatantState {
        Objects.requireNonNull(snapshot, "snapshot");
        if (currentHitPoints < 0 || currentHitPoints > snapshot.maxHitPoints() || temporaryHitPoints < 0) {
            throw new IllegalArgumentException("Invalid hit point state");
        }
        conditions = List.copyOf(Objects.requireNonNull(conditions, "conditions"));
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        deathSaves = deathSaves == null ? DeathSaveState.none() : deathSaves;
        if (exhaustionLevel < 0 || exhaustionLevel > MAX_EXHAUSTION) {
            throw new IllegalArgumentException("Exhaustion must be between 0 and " + MAX_EXHAUSTION);
        }
    }

    /** Backward-compatible full constructor: uses the resources captured by the snapshot. */
    public CombatantState(
            CombatantSnapshot snapshot,
            int currentHitPoints,
            int temporaryHitPoints,
            List<ConditionInstance> conditions,
            ConcentrationState concentration,
            DeathSaveState deathSaves,
            int exhaustionLevel) {
        this(snapshot, currentHitPoints, temporaryHitPoints, conditions, concentration, deathSaves,
                exhaustionLevel, snapshot.resources());
    }

    /**
     * Costruttore di compatibilita' per chi non gestisce morte ed Exhaustion.
     *
     * <p>Mantiene compilanti i chiamanti scritti prima che questi stati esistessero,
     * senza obbligarli a dichiarare valori che per loro sono sempre iniziali.</p>
     */
    public CombatantState(
            CombatantSnapshot snapshot,
            int currentHitPoints,
            int temporaryHitPoints,
            List<ConditionInstance> conditions,
            ConcentrationState concentration) {
        this(snapshot, currentHitPoints, temporaryHitPoints, conditions, concentration, DeathSaveState.none(), 0,
                snapshot.resources());
    }

    /** A 0 punti ferita. Non implica la morte: i tiri contro morte decidono l'esito. */
    public boolean defeated() {
        return currentHitPoints == 0;
    }

    public boolean bloodied() {
        return currentHitPoints * 2 <= snapshot.maxHitPoints();
    }

    public boolean dead() {
        return deathSaves.dead() || exhaustionLevel >= MAX_EXHAUSTION;
    }

    /** Priva di sensi a 0 PF ma non ancora morta. */
    public boolean unconscious() {
        return currentHitPoints == 0 && !dead();
    }

    public boolean stable() {
        return deathSaves.stable();
    }

    /** Penalita' di Exhaustion a tutti i D20 Test: −2 per livello. */
    public int exhaustionD20Penalty() {
        return -2 * exhaustionLevel;
    }

    /** Riduzione di velocita' da Exhaustion: −5 piedi per livello, mai sotto zero. */
    public int effectiveSpeedFeet() {
        return Math.max(0, snapshot.speedFeet() - 5 * exhaustionLevel);
    }

    public Optional<ConcentrationState> activeConcentration() {
        return Optional.ofNullable(concentration);
    }

    public Optional<CombatResourceState> resource(String resourceId) {
        return resources.stream().filter(resource -> resource.id().equals(resourceId)).findFirst();
    }
}
