package app.d6d.engine.ai;

import app.d6d.domain.space.GridPosition;

/** Una decisione pura: descrive il prossimo comando senza modificare la sessione. */
public sealed interface EnemyCpuDecision permits
        EnemyCpuDecision.Move,
        EnemyCpuDecision.Attack,
        EnemyCpuDecision.AreaAttack,
        EnemyCpuDecision.Heal,
        EnemyCpuDecision.Activate,
        EnemyCpuDecision.Done {

    String actorId();

    /** Motivazione della scelta, in vocabolario: le parole per il tavolo le mette l'interfaccia. */
    EnemyCpuReason reason();

    record Move(String actorId, GridPosition destination, EnemyCpuReason reason)
            implements EnemyCpuDecision { }

    record Attack(String actorId, String targetId, String abilityId, EnemyCpuReason reason)
            implements EnemyCpuDecision { }

    record AreaAttack(String actorId, GridPosition center, String abilityId, EnemyCpuReason reason)
            implements EnemyCpuDecision { }

    /**
     * Cura con l'eventuale risorsa scelta esplicitamente.
     *
     * <p>{@code resourceId} resta vuoto per le cure senza consumo; {@code slotLevel}
     * vale zero per le risorse che non sono slot incantesimo.</p>
     */
    record Heal(
            String actorId,
            String targetId,
            String abilityId,
            String resourceId,
            int slotLevel,
            EnemyCpuReason reason) implements EnemyCpuDecision {

        public Heal {
            resourceId = resourceId == null ? "" : resourceId;
            if (slotLevel < 0 || slotLevel > 9) {
                throw new IllegalArgumentException("slotLevel must be between zero and nine");
            }
            if (slotLevel > 0 && resourceId.isBlank()) {
                throw new IllegalArgumentException("A healing slot needs its resource id");
            }
        }

        /** Compatibilita' sorgente per una cura che usa la risorsa base dell'abilita'. */
        public Heal(String actorId, String targetId, String abilityId, EnemyCpuReason reason) {
            this(actorId, targetId, abilityId, "", 0, reason);
        }
    }

    record Activate(String actorId, String abilityId, EnemyCpuReason reason)
            implements EnemyCpuDecision { }

    /**
     * Nessun comando da eseguire.
     *
     * <p>{@code detail} non e' testo di presentazione: quando la pianificazione si
     * ferma per un contenuto non valido conserva il messaggio della regola violata,
     * utile in diagnostica e nel registro.</p>
     */
    record Done(String actorId, EnemyCpuReason reason, String detail) implements EnemyCpuDecision {

        public Done {
            detail = detail == null ? "" : detail;
        }

        public Done(String actorId, EnemyCpuReason reason) {
            this(actorId, reason, "");
        }
    }
}
