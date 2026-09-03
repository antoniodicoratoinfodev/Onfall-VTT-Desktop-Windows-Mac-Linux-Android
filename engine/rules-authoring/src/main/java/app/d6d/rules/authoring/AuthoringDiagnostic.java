package app.d6d.rules.authoring;

import java.util.Objects;

/** Diagnostica strutturata: la UI decide lingua, posizione e azione proposta. */
public record AuthoringDiagnostic(
        String code,
        DiagnosticSeverity severity,
        String field,
        String detail
) {
    public AuthoringDiagnostic {
        code = requireText(code, "code");
        severity = Objects.requireNonNull(severity, "severity");
        field = Objects.requireNonNullElse(field, "").trim();
        detail = Objects.requireNonNullElse(detail, "").trim();
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
