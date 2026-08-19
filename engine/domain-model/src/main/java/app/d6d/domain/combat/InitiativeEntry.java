package app.d6d.domain.combat;

public record InitiativeEntry(String combatantId, int score, int rosterOrder) {
    public InitiativeEntry {
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId cannot be blank");
        }
        if (rosterOrder < 0) {
            throw new IllegalArgumentException("rosterOrder cannot be negative");
        }
    }
}
