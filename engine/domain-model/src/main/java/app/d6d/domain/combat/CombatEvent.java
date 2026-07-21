package app.d6d.domain.combat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Sequence, rather than wall-clock time, makes an audit trail stable and easy to compare. */
public record CombatEvent(
        long sequence,
        long revision,
        EventType type,
        int round,
        String actorId,
        String targetId,
        Map<String, String> details) {

    public CombatEvent {
        if (sequence < 0 || revision < 0 || round < 0) {
            throw new IllegalArgumentException("Negative event metadata");
        }
        Objects.requireNonNull(type, "type");
        actorId = actorId == null ? "" : actorId;
        targetId = targetId == null ? "" : targetId;
        details = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(details, "details")));
    }
}
