package app.d6d.persistence.session;

import app.d6d.engine.CombatSession;

import java.util.Map;
import java.util.Objects;

/**
 * Sessione ricaricata dal disco.
 *
 * <p>Oltre al combattimento porta lo stato di presentazione che non appartiene al
 * motore — bersaglio selezionato, modo di tiro, ingombri dei segnaposti non ancora
 * collocati — cosi' riaprire una sessione restituisce il tavolo com'era, non solo
 * le regole com'erano.</p>
 */
public record SessionArchive(
        SessionSummary summary,
        CombatSession session,
        Map<String, String> presentation) {

    public SessionArchive {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(session, "session");
        presentation = Map.copyOf(Objects.requireNonNull(presentation, "presentation"));
    }
}
