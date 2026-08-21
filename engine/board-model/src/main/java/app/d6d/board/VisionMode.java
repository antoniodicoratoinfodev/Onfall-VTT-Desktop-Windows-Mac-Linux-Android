package app.d6d.board;

/** Da cosa dipende ciò che la nebbia copre. */
public enum VisionMode {
    /** Il master dipinge la nebbia a mano: è il comportamento storico del Lucido. */
    MANUAL,

    /**
     * La nebbia segue la vista di chi ha il turno.
     *
     * <p>Il documento non conserva il risultato: le caselle visibili si ricalcolano
     * da muri, posizione e raggio ogni volta che una delle tre cambia. Persiste
     * invece la memoria di ciò che è già stato visto, in {@link ExploredMask}.</p>
     */
    DYNAMIC
}
