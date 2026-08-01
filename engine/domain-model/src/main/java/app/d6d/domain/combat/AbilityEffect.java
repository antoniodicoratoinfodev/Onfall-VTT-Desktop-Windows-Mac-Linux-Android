package app.d6d.domain.combat;

/**
 * Effetto immediato che il motore può applicare senza un bersaglio.
 *
 * <p>La risoluzione resta esplicita nei dati della capacità: il motore non deduce
 * mai una regola dal nome o dal testo localizzato.</p>
 */
public enum AbilityEffect {
    NONE,
    /** Concede un'altra azione nel turno, ma quell'azione non può essere Magia. */
    GRANT_NON_MAGIC_ACTION
}
