package app.d6d.domain.combat;

/** Riserva limitata fotografata nell'incontro e consumata in modo annullabile. */
public record CombatResourceState(String id, String name, int maximum, int spent) {
    public CombatResourceState {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Resource id cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Resource name cannot be blank");
        }
        if (maximum < 0 || spent < 0 || spent > maximum) {
            throw new IllegalArgumentException("Invalid resource state");
        }
    }

    public int remaining() {
        return maximum - spent;
    }

    public CombatResourceState spend(int amount) {
        if (amount < 0 || amount > remaining()) {
            throw new IllegalArgumentException("Resource cost exceeds the remaining uses");
        }
        return new CombatResourceState(id, name, maximum, spent + amount);
    }
}
