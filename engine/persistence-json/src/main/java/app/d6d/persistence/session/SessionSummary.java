package app.d6d.persistence.session;

import java.util.Objects;

/**
 * Riga dell'elenco delle sessioni salvate.
 *
 * <p>{@code slug} e' il nome del file, {@code displayName} quello scelto
 * dall'utente: restano distinti cosi' una sessione puo' chiamarsi "Cripta dei
 * Predoni — sera 3" senza che il nome finisca tale e quale sul filesystem.</p>
 */
public record SessionSummary(
        String slug,
        String displayName,
        String savedAt,
        int round,
        int combatantCount,
        String status) {

    public SessionSummary {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(displayName, "displayName");
        savedAt = savedAt == null ? "" : savedAt;
        status = status == null ? "" : status;
        if (round < 0 || combatantCount < 0) {
            throw new IllegalArgumentException("Session counters cannot be negative");
        }
    }
}
