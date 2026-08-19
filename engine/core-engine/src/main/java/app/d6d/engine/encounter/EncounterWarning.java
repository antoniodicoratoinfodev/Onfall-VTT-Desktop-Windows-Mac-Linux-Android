package app.d6d.engine.encounter;

import java.util.Objects;

public record EncounterWarning(
        EncounterWarningCode code,
        WarningSeverity severity,
        String message) {

    public EncounterWarning {
        code = Objects.requireNonNull(code, "code");
        severity = Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        message = message.trim();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
