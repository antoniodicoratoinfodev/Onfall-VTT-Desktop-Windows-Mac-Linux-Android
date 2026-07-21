package app.d6d.domain.combat;

import java.util.Objects;

/** Associates a reusable actor definition with its stable id inside one encounter. */
public record CombatantSetup(String instanceId, ActorDefinition actor) {
    public CombatantSetup {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId cannot be blank");
        }
        Objects.requireNonNull(actor, "actor");
    }
}
