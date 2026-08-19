package app.d6d.engine;

/** A rejected command. The session and its audit trail remain unchanged. */
public final class CombatRuleException extends IllegalStateException {
    public CombatRuleException(String message) {
        super(message);
    }
}
