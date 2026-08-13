package app.d6d.engine.ai;

/** Esito di un singolo bersaglio compreso in un comando CPU ad area. */
public record EnemyCpuTargetReport(String targetId, int amount, boolean saved) {

    public EnemyCpuTargetReport {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId cannot be blank");
        }
        if (amount < 0) throw new IllegalArgumentException("amount cannot be negative");
    }
}
