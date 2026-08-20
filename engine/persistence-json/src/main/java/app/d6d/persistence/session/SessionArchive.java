package app.d6d.persistence.session;

import app.d6d.board.BoardDocument;
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
        Map<String, String> presentation,
        BoardDocument board) {

    /** Compatibilità sorgente per chiamanti che non hanno ancora un Lucido. */
    public SessionArchive(SessionSummary summary, CombatSession session, Map<String, String> presentation) {
        this(summary, session, presentation, BoardDocument.empty());
    }

    public SessionArchive {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(session, "session");
        presentation = Map.copyOf(Objects.requireNonNull(presentation, "presentation"));
        board = Objects.requireNonNull(board, "board");
    }
}
