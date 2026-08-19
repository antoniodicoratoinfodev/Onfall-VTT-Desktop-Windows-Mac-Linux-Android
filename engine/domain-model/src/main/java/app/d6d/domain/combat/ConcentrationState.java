package app.d6d.domain.combat;

/** One active concentration per owner. Dependent effects refer back through owner id. */
public record ConcentrationState(String abilityId, int startedRound) {
    public ConcentrationState {
        if (abilityId == null || abilityId.isBlank()) {
            throw new IllegalArgumentException("abilityId cannot be blank");
        }
        if (startedRound < 0) {
            throw new IllegalArgumentException("startedRound cannot be negative");
        }
    }
}
