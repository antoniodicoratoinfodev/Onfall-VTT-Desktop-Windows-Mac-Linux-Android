package app.d6d.engine.ai;

/**
 * Come si e' concluso un turno di gruppo.
 *
 * <p>Sostituisce il messaggio gia' scritto che il motore restituiva: chi mostra
 * l'esito al tavolo sceglie le parole, il motore si limita a dire quale dei casi
 * si e' verificato.</p>
 */
public enum EnemyCpuOutcome {

    /** Il gruppo nemico ha giocato il proprio turno fino in fondo. */
    COMPLETED,

    /** Gruppo misto: i nemici hanno agito, il turno resta al giocatore. */
    WAITING_FOR_PLAYER,

    /** Uno o piu' attori si sono fermati al limite di sicurezza delle decisioni. */
    DECISION_LIMIT_REACHED,

    /** Un attore e' stato arrestato dal controllo di revisione. */
    REVISION_GUARD_TRIGGERED,

    /** I comandi gia' prodotti sono stati annullati esplicitamente dal chiamante. */
    ROLLED_BACK,

    /** L'incontro non era attivo. */
    ENCOUNTER_NOT_ACTIVE,

    /** Sessione senza schieramenti dichiarati: la CPU non controlla nessuno. */
    FACTIONS_NOT_CONFIGURED,

    /** Nessun combattente poteva agire nel turno corrente. */
    NO_ACTOR_AVAILABLE,

    /** Il gruppo nemico non aveva attori vivi ed e' stato saltato. */
    LIFELESS_GROUP,

    /** Lo schieramento avversario ha sconfitto il gruppo. */
    ENEMY_VICTORY,
}
