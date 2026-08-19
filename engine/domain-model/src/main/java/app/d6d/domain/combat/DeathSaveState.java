package app.d6d.domain.combat;

/**
 * Tiri salvezza contro morte di una creatura a 0 punti ferita.
 *
 * <p>Tre successi rendono Stable, tre fallimenti causano la morte. Recuperare
 * punti ferita o diventare Stable azzera sia i successi sia i fallimenti: per
 * questo lo stato e' un valore unico e non tre contatori sparsi.</p>
 */
public record DeathSaveState(int successes, int failures, boolean stable) {

    public static final int REQUIRED = 3;

    public DeathSaveState {
        if (successes < 0 || successes > REQUIRED) {
            throw new IllegalArgumentException("Death save successes must be between 0 and " + REQUIRED);
        }
        if (failures < 0 || failures > REQUIRED) {
            throw new IllegalArgumentException("Death save failures must be between 0 and " + REQUIRED);
        }
    }

    /** Stato di partenza: nessun tiro effettuato. */
    public static DeathSaveState none() {
        return new DeathSaveState(0, 0, false);
    }

    /** Una creatura Stable resta priva di sensi ma non tira piu' contro morte. */
    public static DeathSaveState stabilized() {
        return new DeathSaveState(0, 0, true);
    }

    public boolean dead() {
        return failures >= REQUIRED;
    }

    public boolean succeededEnough() {
        return successes >= REQUIRED;
    }

    /** Un fallimento; il 1 naturale ne vale due. */
    public DeathSaveState withFailures(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Failure amount cannot be negative");
        }
        return new DeathSaveState(successes, Math.min(REQUIRED, failures + amount), false);
    }

    public DeathSaveState withSuccess() {
        int updated = Math.min(REQUIRED, successes + 1);
        // Il terzo successo rende Stable e azzera i contatori.
        return updated >= REQUIRED ? stabilized() : new DeathSaveState(updated, failures, false);
    }
}
