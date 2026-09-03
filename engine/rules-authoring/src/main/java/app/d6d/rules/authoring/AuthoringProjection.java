package app.d6d.rules.authoring;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Risultato lossless di una proiezione runtime -> modello di authoring. */
public record AuthoringProjection<T>(
        Optional<T> value,
        ProjectionStatus status,
        List<AuthoringDiagnostic> diagnostics
) {
    public AuthoringProjection {
        value = Objects.requireNonNull(value, "value");
        status = Objects.requireNonNull(status, "status");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public static <T> AuthoringProjection<T> exact(T value) {
        return new AuthoringProjection<>(Optional.of(value), ProjectionStatus.EXACT, List.of());
    }
}
