package app.d6d.engine.ai;

import java.util.List;
import java.util.Objects;

/**
 * Resoconto di un singolo comando della CPU.
 *
 * <p>{@code amount} sono piedi per un movimento, danni effettivi per un attacco
 * e punti ferita realmente recuperati per una cura. Gli identificatori non
 * applicabili sono stringhe vuote, cosi' il record resta semplice da consumare
 * sia da Kotlin sia da Java. Per gli effetti ad area {@link #targets()} conserva
 * inoltre gli esiti dei singoli bersagli senza trasformare un solo comando in
 * piu' report (e quindi in piu' checkpoint). Per un attacco singolo {@link #hit()}
 * distingue un vero mancato da un colpo che infligge zero danni.
 * {@code consumedResourceId} e {@code slotLevel} rendono esplicito l'eventuale
 * slot scelto dinamicamente per una cura.</p>
 *
 * <p>{@link #reason()} dice perche' il comando e' stato scelto: e' vocabolario del
 * motore, non una frase pronta. Le parole mostrate al tavolo restano
 * dell'interfaccia. {@code detail} non e' testo di presentazione: conserva il
 * messaggio della regola violata quando un comando viene scartato, e resta vuoto
 * in tutti gli altri casi.</p>
 */
public record EnemyCpuActionReport(
        EnemyCpuActionType type,
        String actorId,
        String targetId,
        String abilityId,
        int amount,
        EnemyCpuReason reason,
        String detail,
        List<EnemyCpuTargetReport> targets,
        boolean hit,
        String consumedResourceId,
        int slotLevel) {

    public EnemyCpuActionReport {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        actorId = actorId == null ? "" : actorId;
        targetId = targetId == null ? "" : targetId;
        abilityId = abilityId == null ? "" : abilityId;
        if (amount < 0) throw new IllegalArgumentException("amount cannot be negative");
        detail = detail == null ? "" : detail;
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        consumedResourceId = consumedResourceId == null ? "" : consumedResourceId;
        if (slotLevel < 0 || slotLevel > 9) {
            throw new IllegalArgumentException("slotLevel must be between zero and nine");
        }
        if (slotLevel > 0 && consumedResourceId.isBlank()) {
            throw new IllegalArgumentException("A reported spell slot needs its resource id");
        }
    }

    /** Comando riuscito senza diagnostica da riportare. */
    public EnemyCpuActionReport(
            EnemyCpuActionType type,
            String actorId,
            String targetId,
            String abilityId,
            int amount,
            EnemyCpuReason reason) {
        this(type, actorId, targetId, abilityId, amount, reason, "", List.of(), false, "", 0);
    }

    /** Comando scartato o interrotto: {@code detail} porta il messaggio della regola. */
    public EnemyCpuActionReport(
            EnemyCpuActionType type,
            String actorId,
            String targetId,
            String abilityId,
            EnemyCpuReason reason,
            String detail) {
        this(type, actorId, targetId, abilityId, 0, reason, detail, List.of(), false, "", 0);
    }

    /** Comando multi-bersaglio, come un'area. */
    public EnemyCpuActionReport(
            EnemyCpuActionType type,
            String actorId,
            String targetId,
            String abilityId,
            int amount,
            EnemyCpuReason reason,
            List<EnemyCpuTargetReport> targets) {
        this(type, actorId, targetId, abilityId, amount, reason, "", targets, false, "", 0);
    }

    /** Attacco singolo con esito esplicito. */
    public EnemyCpuActionReport(
            EnemyCpuActionType type,
            String actorId,
            String targetId,
            String abilityId,
            int amount,
            EnemyCpuReason reason,
            boolean hit) {
        this(type, actorId, targetId, abilityId, amount, reason, "", List.of(), hit, "", 0);
    }
}
