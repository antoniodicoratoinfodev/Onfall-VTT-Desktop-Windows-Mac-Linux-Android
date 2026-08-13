package app.d6d.engine.ai;

/**
 * Perche' la CPU ha fatto (o non ha fatto) una cosa.
 *
 * <p>E' vocabolario, non testo: il motore dichiara la motivazione, la scrittura
 * per il tavolo appartiene all'interfaccia, che la conosce nella lingua giusta e
 * puo' cambiarla senza toccare le regole.</p>
 */
public enum EnemyCpuReason {

    /** Attacco sul bersaglio prioritario condiviso dal gruppo. */
    FOCUS_FIRE,

    /** Miglior attacco disponibile su un bersaglio qualsiasi. */
    BEST_ATTACK,

    /** Area scelta pesando bersagli colpiti ed eventuale fuoco amico. */
    AREA_COVERAGE,

    /** Cura preventiva sul membro piu' esposto della squadra. */
    PROTECT_ALLY,

    /** Cura che rimette in piedi un alleato a zero punti ferita. */
    RAISE_ALLY,

    /** Risorsa convertita in pressione offensiva aggiuntiva. */
    EXTRA_PRESSURE,

    /** Spostamento che riduce la distanza dal bersaglio. */
    CLOSE_DISTANCE,

    /** Spostamento su una casella libera attorno al bersaglio prioritario. */
    SURROUND_TARGET,

    /** Il gruppo nemico ha concluso il proprio turno. */
    GROUP_TURN_COMPLETED,

    /** Turno chiuso dopo aver fermato uno o piu' attori al limite di decisioni. */
    GROUP_TURN_LIMITED,

    /** Gruppo nemico senza attori vivi: il turno viene consegnato al motore e saltato. */
    LIFELESS_GROUP_SKIPPED,

    /** Pianificazione interrotta da un contenuto non valido. */
    PLANNING_FAILED,

    /** Comando rifiutato dal motore: la decisione viene scartata e si ripianifica. */
    COMMAND_REJECTED,

    /** Comando andato a vuoto: nessun avanzamento di revisione, attore fermato. */
    NO_PROGRESS,

    /** Attore fermato al limite di sicurezza delle decisioni. */
    DECISION_LIMIT_REACHED,

    /** Nessun comando automatico utile e legale. */
    NOTHING_USEFUL,

    /** L'incontro non e' attivo. */
    ENCOUNTER_NOT_ACTIVE,

    /** La sessione non dichiara gli schieramenti: la CPU non controlla nessuno. */
    FACTIONS_NOT_CONFIGURED,

    /** Il combattente non appartiene (o non appartiene piu') al turno corrente. */
    NOT_IN_CURRENT_TURN,

    /** Membro del gruppo: la CPU nemica non lo controlla mai. */
    NOT_CONTROLLED,

    /** Sconfitto, morto o incapacitato: non puo' agire. */
    ACTOR_CANNOT_ACT,

    /** Non restano avversari in piedi. */
    NO_OPPONENTS_LEFT,

    /** Lo schieramento avversario ha sconfitto il gruppo. */
    ENEMY_VICTORY,
}
