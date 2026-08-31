package app.d6d.rules.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Politica, dichiarata dal regolamento, che separa durata, proprietario e
 * sincronizzazione di uno stato mutabile.
 *
 * <p>Lo scope identifica l'istanza concreta (per esempio l'attore «arya»); la
 * policy stabilisce invece quanto vive il dato, quale archivio ne e' autorevole
 * e se possa essere riportato altrove. Tenere i tre assi separati evita che una
 * risorsa «per incontro» venga automaticamente scambiata per un campo permanente
 * della scheda.</p>
 */
public record StatePersistencePolicy(
        Lifetime lifetime,
        Owner owner,
        SyncPolicy syncPolicy,
        String resetEvent) {

    public enum Lifetime {
        ACTION,
        TURN,
        SCENE,
        ENCOUNTER,
        SESSION,
        CAMPAIGN,
        PERMANENT
    }

    public enum Owner {
        SCOPE,
        ACTOR,
        PARTY,
        SESSION,
        CAMPAIGN,
        GM
    }

    public enum SyncPolicy {
        LOCAL_ONLY,
        PROPOSE,
        AUTO_IF_COMPATIBLE,
        NEVER
    }

    public StatePersistencePolicy {
        lifetime = Objects.requireNonNull(lifetime, "lifetime");
        owner = Objects.requireNonNull(owner, "owner");
        syncPolicy = Objects.requireNonNull(syncPolicy, "syncPolicy");
        resetEvent = normalizeEvent(resetEvent);
    }

    /** Default conservativo: nessuna scadenza e nessuna scrittura fuori dallo scope. */
    public static StatePersistencePolicy persistentLocal() {
        return new StatePersistencePolicy(
                Lifetime.PERMANENT,
                Owner.SCOPE,
                SyncPolicy.LOCAL_ONLY,
                "");
    }

    /** Evento canonico usato quando il pack non ne dichiara uno personalizzato. */
    public String effectiveResetEvent() {
        if (!resetEvent.isEmpty()) return resetEvent;
        return switch (lifetime) {
            case ACTION -> "ACTION_ENDED";
            case TURN -> "TURN_ENDED";
            case SCENE -> "SCENE_ENDED";
            case ENCOUNTER -> "ENCOUNTER_ENDED";
            case SESSION -> "SESSION_ENDED";
            case CAMPAIGN -> "CAMPAIGN_ENDED";
            case PERMANENT -> "";
        };
    }

    public boolean expiresOn(String event) {
        String boundary = effectiveResetEvent();
        return !boundary.isEmpty() && boundary.equals(normalizeEvent(event));
    }

    public boolean canSynchronizeAutomatically() {
        return syncPolicy == SyncPolicy.AUTO_IF_COMPATIBLE;
    }

    public boolean canProposeSynchronization() {
        return syncPolicy == SyncPolicy.PROPOSE || syncPolicy == SyncPolicy.AUTO_IF_COMPATIBLE;
    }

    private static String normalizeEvent(String event) {
        if (event == null || event.isBlank()) return "";
        return event.trim().toUpperCase(Locale.ROOT);
    }
}
